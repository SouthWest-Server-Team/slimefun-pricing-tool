package com.xinantown.sfprice;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import io.github.thebusybiscuit.slimefun4.implementation.Slimefun;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * 核心扫描器：遍历 Slimefun 启用物品，递归计算 base 价并导出 YAML。
 *
 * <p>公式：{@code base = Σ(每格材料数量 × 材料单价) × 加工系数(addon)}。
 * 材料单价解析：Slimefun 物品（PDC 标记）→ 递归其 base；原版材料 → MaterialPriceTable；
 * 原版材料未收录 → 记缺价（missingMaterials），按 0 计入并继续（报告会标出）。
 *
 * <p>依赖注入设计（无服务器可测）：{@code multiplierResolver}（addon→系数）、
 * {@code itemLookup}（id→物品视图）、{@code sfIdResolver}（ItemStack→Slimefun id，
 * 默认读 PDC；测试注入 fake 映射）。三者均可替换。
 */
public final class PriceScanner {

    /** 无配方/不按配方定价的 RecipeType key（白名单手动定价，需求 v1.4 例外条款）。 */
    private static final Set<String> NO_RECIPE_TYPES = Set.of(
            "mob_drop",
            "barter_drop",
            "geo_miner",
            "null",
            "interact"
    );

    private final MaterialPriceTable materials = new MaterialPriceTable();
    private final Function<String, Double> multiplier;
    private final Function<String, SlimefunItemView> itemLookup;
    private final Function<ItemStack, String> sfIdResolver;
    private final java.util.function.Predicate<ItemStack> airCheck;
    private final Map<String, Double> priced = new LinkedHashMap<>();
    private final Set<String> noRecipe = new LinkedHashSet<>();
    private final Map<String, Integer> missing = new LinkedHashMap<>();
    private final Set<String> cycles = new LinkedHashSet<>();
    private final Set<String> computing = new LinkedHashSet<>(); // 防循环递归栈

    /**
     * 全参构造。{@code airCheck} 默认 {@code s -> s.getType().isAir()}；
     * 纯 JVM 测试注入恒 false（Paper 的 isAir() 懒加载 RegistryAccess，无服务器必炸）。
     */
    public PriceScanner(Function<String, Double> multiplier,
                        Function<String, SlimefunItemView> itemLookup,
                        Function<ItemStack, String> sfIdResolver) {
        this(multiplier, itemLookup, sfIdResolver, s -> s.getType().isAir());
    }

    PriceScanner(Function<String, Double> multiplier,
                 Function<String, SlimefunItemView> itemLookup,
                 Function<ItemStack, String> sfIdResolver,
                 java.util.function.Predicate<ItemStack> airCheck) {
        this.multiplier = multiplier;
        this.itemLookup = itemLookup;
        this.sfIdResolver = sfIdResolver;
        this.airCheck = airCheck;
    }

    /** 全量扫描（仅当 Slimefun 加载时调用）。 */
    public static ScanResult scan(String onlyId) {
        List<SlimefunItem> items;
        try {
            items = Slimefun.getRegistry().getEnabledSlimefunItems();
        } catch (Throwable t) {
            return ScanResult.fail("Slimefun registry 不可用: " + t.getMessage());
        }
        PriceScanner scanner = new PriceScanner(AddonMultiplier.resolver(),
                id -> {
                    SlimefunItem item = SlimefunItem.getById(id);
                    return item == null ? null : SlimefunItemView.adapt(item);
                },
                PriceScanner::pdcSfId);
        for (SlimefunItem item : items) {
            String id = item.getId();
            if (onlyId != null && !onlyId.equalsIgnoreCase(id)) continue;
            scanner.process(SlimefunItemView.adapt(item));
        }
        String path = scanner.export();
        return ScanResult.ok(scanner.priced, scanner.noRecipe, scanner.missing, scanner.cycles, path);
    }

    /** 处理单个物品：无配方 → noRecipe；有配方 → 递归算 base。 */
    void process(SlimefunItemView item) {
        String id = item.id();
        if (priced.containsKey(id) || noRecipe.contains(id)) return;
        if (isNoRecipeType(item.recipeTypeKey())) {
            noRecipe.add(id);
            return;
        }
        computing.add(id);
        double cost = compute(item);
        computing.remove(id);
        priced.put(id, cost);
    }

    /** 递归成本：Σ(材料数量 × 单价) × 加工系数。缺材料按 0 计并记缺价。 */
    private double compute(SlimefunItemView item) {
        ItemStack[] recipe = item.recipe();
        if (recipe == null || recipe.length == 0) return 0.0;
        ItemStack output = item.recipeOutput();
        double sum = 0.0;
        for (int i = 0; i < recipe.length; i++) {
            ItemStack slot = recipe[i];
            if (slot == null || airCheck.test(slot)) continue;
            if (isOutputSlot(slot, output)) continue; // Slimefun 9 格配方 index 8 = 输出，不算材料
            double unit = materialPrice(slot);
            if (unit < 0) {
                recordMissing(slot);
                continue; // 缺价材料按 0 计入
            }
            sum += unit * slot.getAmount();
        }
        double mult = multiplier.apply(item.addonName());
        return round2(sum * mult);
    }

    /** 输出格判定：与 getRecipeOutput() 同类型且数量一致（index 8 约定，防御性按内容比对）。 */
    private static boolean isOutputSlot(ItemStack slot, ItemStack output) {
        if (output == null || slot == null) return false;
        return slot.getType() == output.getType() && slot.getAmount() == output.getAmount();
    }

    /** 材料单价：Slimefun 物品 → 递归；原版 → 价表；缺价返回 -1。 */
    private double materialPrice(ItemStack slot) {
        // 1) Slimefun 物品（PDC 标记）→ 递归 base
        String sfId = sfIdResolver.apply(slot);
        if (sfId != null) {
            SlimefunItemView view = itemLookup.apply(sfId);
            if (view != null) {
                String id = view.id();
                if (computing.contains(id)) {
                    cycles.add(id + " (配方循环)");
                    return 0.0; // 循环依赖按 0 计，报告标出
                }
                if (!priced.containsKey(id)) process(view);
                Double v = priced.get(id);
                return v == null ? 0.0 : v;
            }
        }
        // 2) 原版材料 → 价表
        Double v = materials.priceOf(slot);
        return v == null ? -1.0 : v;
    }

    /** 从 ItemStack 读 Slimefun ID（PDC 键 minecraft:slimefun_item，Slimefun 标准标记）。 */
    static String pdcSfId(ItemStack slot) {
        try {
            var meta = slot.getItemMeta();
            if (meta == null) return null;
            var pdc = meta.getPersistentDataContainer();
            NamespacedKey key = NamespacedKey.minecraft("slimefun_item");
            if (!pdc.has(key)) return null;
            return pdc.get(key, PersistentDataType.STRING);
        } catch (Throwable t) {
            return null;
        }
    }

    private boolean isNoRecipeType(String typeKey) {
        return typeKey == null || NO_RECIPE_TYPES.contains(typeKey);
    }

    // ===== 包级测试访问器（生产逻辑不变） =====
    Map<String, Double> pricedView() {
        return priced;
    }

    Set<String> noRecipeView() {
        return noRecipe;
    }

    Map<String, Integer> missingView() {
        return missing;
    }

    Set<String> cyclesView() {
        return cycles;
    }

    private void recordMissing(ItemStack slot) {
        String key = slot.getType().name();
        missing.merge(key, 1, Integer::sum);
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    /** 导出 slimefun-prices.yml 到插件数据目录。 */
    private String export() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("generated-by", "SfPricingTool 1.0.0");
        root.put("formula", "base = Σ(材料数量×单价) × 加工系数(addon)");

        Map<String, Double> pricedOut = new LinkedHashMap<>();
        for (Map.Entry<String, Double> e : priced.entrySet()) {
            if (e.getValue() > 0) pricedOut.put(e.getKey(), e.getValue());
        }
        root.put("prices", pricedOut);

        List<String> noRecipeList = new ArrayList<>(noRecipe);
        java.util.Collections.sort(noRecipeList);
        root.put("no-recipe-manual-whitelist", noRecipeList);

        Map<String, Integer> missingOut = new LinkedHashMap<>(missing);
        root.put("missing-material-prices", missingOut);

        List<String> cycleList = new ArrayList<>(cycles);
        root.put("recipe-cycles", cycleList);

        DumperOptions opts = new DumperOptions();
        opts.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        opts.setPrettyFlow(true);
        Yaml yaml = new Yaml(opts);
        String path = "plugins/SfPricingTool/slimefun-prices.yml";
        File f = new File(path);
        f.getParentFile().mkdirs();
        try (FileWriter w = new FileWriter(f)) {
            yaml.dump(root, w);
        } catch (IOException e) {
            return "写入失败: " + e.getMessage();
        }
        return f.getAbsolutePath();
    }
}
