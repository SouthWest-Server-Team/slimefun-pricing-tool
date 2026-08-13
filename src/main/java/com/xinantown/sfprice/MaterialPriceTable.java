package com.xinantown.sfprice;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 原版材料 base 价表（需求 v1.4 §基础物价表 + 三锚点推导；2026-08-13 金山定稿）。
 *
 * <p>Slimefun 配方中出现的原版材料，优先查本表；查不到则记入"缺价"清单（ScanResult.missingMaterials）。
 * 物品识别用 {@link ItemStack#getType()}（Material），忽略 NBT/数量（数量单独乘）。
 */
public final class MaterialPriceTable {

    /** Material 名称（大写枚举名）→ base 价（银）。 */
    private final Map<String, Double> prices = new LinkedHashMap<>();

    public MaterialPriceTable() {
        // ---- 三锚点（需求 v1.4 §基础物价表，2026-08-13 定）----
        put(Material.COBBLESTONE, 0.1);          // 圆石 低端锚点
        put(Material.WHEAT, 0.5);                // 小麦 农业锚点
        put(Material.IRON_INGOT, 5.0);           // 铁锭 加工品锚点

        // ---- 农产品 ----
        put(Material.WHEAT_SEEDS, 0.3);
        put(Material.CARROT, 0.5);
        put(Material.POTATO, 0.5);
        put(Material.PUMPKIN, 2.0);
        put(Material.MELON, 1.0);
        put(Material.SUGAR_CANE, 0.3);
        put(Material.BREAD, 1.5);

        // ---- 木材 ----
        put(Material.OAK_LOG, 1.0);
        put(Material.SPRUCE_LOG, 1.0);
        put(Material.BIRCH_LOG, 1.0);
        put(Material.DARK_OAK_LOG, 1.2);
        put(Material.OAK_PLANKS, 0.25);
        put(Material.STICK, 0.1);

        // ---- 矿物 ----
        put(Material.COAL, 1.0);
        put(Material.REDSTONE, 2.0);
        put(Material.LAPIS_LAZULI, 3.0);
        put(Material.GOLD_INGOT, 15.0);
        put(Material.DIAMOND, 50.0);

        // ---- 装备（1.5 倍加工增值，NPC 卖出价）----
        put(Material.IRON_SWORD, 15.0);
        put(Material.IRON_PICKAXE, 15.0);
        put(Material.IRON_AXE, 15.0);
        put(Material.IRON_CHESTPLATE, 60.0);
        put(Material.IRON_BOOTS, 30.0);
        put(Material.GOLDEN_SWORD, 45.0);
    }

    private void put(Material m, double price) {
        prices.put(m.name(), price);
    }

    /** 查询原版材料 base 价；未收录 → null（调用方记缺价）。 */
    public Double priceOf(ItemStack item) {
        if (item == null) return null;
        return prices.get(item.getType().name());
    }

    /** 已收录的全部材料名（用于报告/补全参考）。 */
    public Map<String, Double> all() {
        return new LinkedHashMap<>(prices);
    }
}
