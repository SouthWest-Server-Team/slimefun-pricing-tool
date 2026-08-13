package com.xinantown.sfprice;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * PriceScanner 算法单测（纯 JVM，无服务器）：
 * fake 物品视图 + Mockito mock ItemStack，验证核心公式、递归链、循环、缺价、无配方、系数分档。
 *
 * <p>注意：
 * ① 不使用 {@code RecipeType} 类常量（静态初始化需 Bukkit Plugin 实例）——一律用 key 字符串；
 * ② 不真实构造 {@code ItemStack}（构造器触发 {@code org.bukkit.Registry} 类初始化，
 *    纯 JVM 抛 NoClassDefFoundError）——全部 mock。
 */
class PriceScannerTest {

    /** 普通合成类型 key（非 no-recipe 类型）。 */
    private static final String CRAFT = "enhanced_crafting_table";

    /** fake 物品视图。 */
    private record FakeItem(String id, ItemStack[] recipe, String typeKey, String addon,
                            ItemStack output) implements SlimefunItemView {
        @Override
        public String addonName() {
            return addon;
        }

        @Override
        public String recipeTypeKey() {
            return typeKey;
        }

        @Override
        public ItemStack recipeOutput() {
            return output;
        }
    }

    /** 便捷构造：无输出（输出单独存的配方）。 */
    private static FakeItem item(String id, ItemStack[] recipe) {
        return new FakeItem(id, recipe, CRAFT, "Slimefun", null);
    }

    private static ItemStack[] recipe(ItemStack... slots) {
        ItemStack[] r = new ItemStack[9];
        for (int i = 0; i < slots.length && i < 9; i++) r[i] = slots[i];
        return r;
    }

    /** mock 原版材料（无 PDC）。 */
    private static ItemStack stack(Material m, int amount) {
        ItemStack s = mock(ItemStack.class);
        when(s.getType()).thenReturn(m);
        when(s.getAmount()).thenReturn(amount);
        // 无 PDC：getItemMeta 返回 null → PriceScanner.pdcSfId 返回 null → 走原版价表
        when(s.getItemMeta()).thenReturn(null);
        return s;
    }

    /** mock Slimefun 材料（带 PDC 标记 slimefun_item=sfId）。 */
    private static ItemStack sfStack(Material m, int amount, String sfId) {
        ItemStack s = mock(ItemStack.class);
        when(s.getType()).thenReturn(m);
        when(s.getAmount()).thenReturn(amount);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        when(pdc.has(any(NamespacedKey.class))).thenReturn(true);
        when(pdc.get(any(NamespacedKey.class), eq(PersistentDataType.STRING))).thenReturn(sfId);
        ItemMeta meta = mock(ItemMeta.class);
        when(meta.getPersistentDataContainer()).thenReturn(pdc);
        when(s.getItemMeta()).thenReturn(meta);
        return s;
    }

    private static Function<String, SlimefunItemView> lookup(Map<String, SlimefunItemView> map) {
        return id -> map.get(id);
    }

    /** sfIdResolver：读 mock PDC（与生产实现一致）。 */
    private static Function<ItemStack, String> pdcResolver() {
        return PriceScanner::pdcSfId;
    }

    private static Function<String, Double> fixedMultiplier() {
        return addon -> 1.2;
    }

    /** 组装 scanner（airCheck 恒 false：mock 的 getType() 不触发 Material.isAir 懒加载）。 */
    private static PriceScanner scanner(Map<String, SlimefunItemView> items) {
        return new PriceScanner(fixedMultiplier(), lookup(items), pdcResolver(), s -> false);
    }

    // ==================== 测试 ====================

    @Test
    void pureVanillaRecipe_usesMaterialPrices() {
        // 3 铁锭 → 5.0 × 3 = 15，× 1.2 = 18
        Map<String, SlimefunItemView> items = new LinkedHashMap<>();
        items.put("TEST_INGOT", item("TEST_INGOT",
                recipe(stack(Material.IRON_INGOT, 1), stack(Material.IRON_INGOT, 1), stack(Material.IRON_INGOT, 1))));
        PriceScanner s = scanner(items);
        s.process(items.get("TEST_INGOT"));
        assertEquals(18.0, s.pricedView().get("TEST_INGOT"), 1e-9);
        assertTrue(s.missingView().isEmpty(), "铁锭是价表内材料，不应缺价");
        assertTrue(s.noRecipeView().isEmpty());
    }

    @Test
    void outputSlot_excludedFromCost() {
        // 配方：2 PAPER 材料 + index 8 输出铁锭（与 recipeOutput 一致）→ 只算 2 PAPER
        // PAPER 价表未收录（缺价按 0）→ 期望 0？不——用价表内材料验证排除逻辑更清楚：
        // 2 铁锭材料 + 1 输出钻石 → 只算 2 铁锭 = 10×1.2 = 12
        ItemStack out = stack(Material.DIAMOND, 1);
        Map<String, SlimefunItemView> items = new LinkedHashMap<>();
        items.put("WITH_OUTPUT", new FakeItem("WITH_OUTPUT",
                recipe(stack(Material.IRON_INGOT, 1), stack(Material.IRON_INGOT, 1), out),
                CRAFT, "Slimefun", out));
        PriceScanner s = scanner(items);
        s.process(items.get("WITH_OUTPUT"));
        assertEquals(12.0, s.pricedView().get("WITH_OUTPUT"), 1e-9);
    }

    @Test
    void slimefunChain_recursiveCost() {
        // A = 2 铁锭 (10) ×1.2 = 12；B = 2 × A(12×2=24) + 1 铁锭(5) = 29 ×1.2 = 34.8
        Map<String, SlimefunItemView> items = new LinkedHashMap<>();
        items.put("A", item("A",
                recipe(stack(Material.IRON_INGOT, 2))));
        items.put("B", item("B",
                recipe(sfStack(Material.PAPER, 1, "A"), sfStack(Material.PAPER, 1, "A"),
                        stack(Material.IRON_INGOT, 1))));
        PriceScanner s = scanner(items);
        s.process(items.get("B"));
        // A 先被递归计算
        assertEquals(12.0, s.pricedView().get("A"), 1e-9);
        assertEquals(34.8, s.pricedView().get("B"), 1e-9);
    }

    @Test
    void missingMaterial_recordedAndCounted() {
        // 配方含价表外材料（BEDROCK 未收录）→ 记缺价、该格按 0
        Map<String, SlimefunItemView> items = new LinkedHashMap<>();
        items.put("X", item("X",
                recipe(stack(Material.IRON_INGOT, 1), stack(Material.BEDROCK, 1))));
        PriceScanner s = scanner(items);
        s.process(items.get("X"));
        // 只有铁锭计入：5 × 1.2 = 6；基岩缺价
        assertEquals(6.0, s.pricedView().get("X"), 1e-9);
        assertEquals(1, s.missingView().get("BEDROCK"));
    }

    @Test
    void cycle_detectedNotInfinite() {
        // A 配方含 B、B 配方含 A（PDC 标记触发递归；computing 栈防死循环）
        Map<String, SlimefunItemView> items = new LinkedHashMap<>();
        items.put("A", item("A",
                recipe(sfStack(Material.PAPER, 1, "B"))));
        items.put("B", item("B",
                recipe(sfStack(Material.PAPER, 1, "A"))));
        PriceScanner s = scanner(items);
        s.process(items.get("A"));
        // 循环：A→B→A，computing 检测后 B 返回 0 → A = 0×1.2 = 0
        assertEquals(0.0, s.pricedView().get("A"), 1e-9);
        assertFalse(s.cyclesView().isEmpty(), "应记录循环依赖");
    }

    @Test
    void noRecipeType_markedManualWhitelist() {
        // mob_drop 类型 → noRecipe（白名单手动定价）
        Map<String, SlimefunItemView> items = new LinkedHashMap<>();
        items.put("DROPPED", new FakeItem("DROPPED", recipe(), "mob_drop", "Slimefun", null));
        PriceScanner s = scanner(items);
        s.process(items.get("DROPPED"));
        assertTrue(s.noRecipeView().contains("DROPPED"));
        assertFalse(s.pricedView().containsKey("DROPPED"));
    }

    @Test
    void addonMultiplier_tiers() {
        assertEquals(1.2, AddonMultiplier.of("Slimefun"), 1e-9);
        assertEquals(1.5, AddonMultiplier.of("FoxyMachines"), 1e-9);
        assertEquals(2.0, AddonMultiplier.of("InfinityExpansion"), 1e-9);
        assertEquals(1.2, AddonMultiplier.of("UnknownAddon"), 1e-9); // 未知默认基础
        assertEquals(1.2, AddonMultiplier.of(null), 1e-9);
    }

    @Test
    void materialPriceTable_coversThreeAnchors() {
        MaterialPriceTable t = new MaterialPriceTable();
        assertEquals(0.1, t.priceOf(stack(Material.COBBLESTONE, 1)), 1e-9);
        assertEquals(0.5, t.priceOf(stack(Material.WHEAT, 1)), 1e-9);
        assertEquals(5.0, t.priceOf(stack(Material.IRON_INGOT, 1)), 1e-9);
        assertNull(t.priceOf(stack(Material.BEDROCK, 1)), "价表外材料应返回 null（缺价）");
    }

    @Test
    void materialPriceTable_loadsExternalYaml() throws Exception {
        // 从 resources 加载 material-prices.yml（真实缺价清单覆盖验证：LEATHER/GLASS/PAPER 等）
        MaterialPriceTable t = new MaterialPriceTable();
        try (var in = getClass().getResourceAsStream("/material-prices.yml")) {
            assertNotNull(in, "material-prices.yml 应存在于 resources");
            t.loadExternal(in);
        }
        assertNotNull(t.priceOf(stack(Material.LEATHER, 1)), "LEATHER 应已定价（缺价清单第 1 项）");
        assertNotNull(t.priceOf(stack(Material.GLASS, 1)), "GLASS 应已定价");
        assertNotNull(t.priceOf(stack(Material.PAPER, 1)), "PAPER 应已定价");
        assertNotNull(t.priceOf(stack(Material.CHEST, 1)), "CHEST 应已定价");
        assertNotNull(t.priceOf(stack(Material.STRUCTURE_BLOCK, 1)), "STRUCTURE_BLOCK 应已定价（0 值占位）");
        // 内置锚点仍可用（外部覆盖不破坏）
        assertEquals(0.1, t.priceOf(stack(Material.COBBLESTONE, 1)), 1e-9);
    }
}
