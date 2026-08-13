package com.xinantown.sfprice;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * 一次扫描的结果。priced = id → base 价；noRecipe = 无配方物品 id（白名单手动定价）；
 * missingMaterials = 缺原版材料价的材料 key → 出现次数；cycles = 配方循环依赖描述。
 */
public record ScanResult(
        Map<String, Double> priced,
        Set<String> noRecipe,
        Map<String, Integer> missingMaterials,
        Set<String> cycles,
        String outputPath,
        String error) {

    public int pricedCount() {
        return priced.size();
    }

    public int noRecipeCount() {
        return noRecipe.size();
    }

    public static ScanResult ok(Map<String, Double> priced, Set<String> noRecipe,
                                Map<String, Integer> missing, Set<String> cycles, String path) {
        return new ScanResult(new LinkedHashMap<>(priced), new LinkedHashSet<>(noRecipe),
                new LinkedHashMap<>(missing), new LinkedHashSet<>(cycles), path, null);
    }

    public static ScanResult fail(String error) {
        return new ScanResult(Map.of(), Set.of(), Map.of(), Set.of(), null, error);
    }
}
