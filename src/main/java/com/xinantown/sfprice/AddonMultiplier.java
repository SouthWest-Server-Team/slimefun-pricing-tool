package com.xinantown.sfprice;

import java.util.Map;
import java.util.function.Function;

/**
 * 加工系数分档（需求 v1.4：基础 Slimefun 1.2 / 中级 1.5 / 高级 2.0 / 魔法 2.0）。
 *
 * <p>按 Slimefun 附属插件名（{@code item.getAddon().getJavaPlugin().getName()}）映射；
 * 未知附属默认 1.2（基础档）。映射可被 {@code Function<String,Double>} 替换（测试用）。
 */
public final class AddonMultiplier {

    private AddonMultiplier() {
    }

    /** 附属插件名（Bukkit 插件名）→ 加工系数。 */
    public static final Map<String, Double> BY_ADDON = Map.of(
            "Slimefun", 1.2,             // 基础（本体）
            "FoxyMachines", 1.5,         // 中级（神秘科技）
            "InfinityExpansion", 2.0,    // 高级（无尽科技）
            "Networks", 1.2,             // 网络（归基础档，待金山确认）
            "SlimeGlue", 1.2             // 粘液胶（归基础档，待金山确认）
    );

    /** 默认基础系数（未知附属）。 */
    public static final double DEFAULT = 1.2;

    /** 按插件名取系数（未知 → DEFAULT）。 */
    public static double of(String addonName) {
        if (addonName == null) return DEFAULT;
        return BY_ADDON.getOrDefault(addonName, DEFAULT);
    }

    /** 供测试注入：返回一个按插件名查系的函数。 */
    public static Function<String, Double> resolver() {
        return AddonMultiplier::of;
    }
}
