package com.xinantown.sfprice;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * SfPricingTool — Slimefun 成本推导脚本（需求 v1.4 §Slimefun 物品定价）。
 *
 * <p>软依赖 Slimefun：Slimefun 未加载时插件照常启动（命令提示不可用），
 * 加载时可用 {@code /sfprice scan} 扫描全部启用物品的配方，按
 * {@code base 价 = Σ(配方材料 base 价) × 加工系数} 计算，导出 slimefun-prices.yml。
 */
public final class SfPricingPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        getCommand("sfprice").setExecutor(new SfPriceCommand(this));
        getLogger().info("SfPricingTool 已启用（Slimefun 成本推导工具）");
    }

    @Override
    public void onDisable() {
        getLogger().info("SfPricingTool 已禁用");
    }
}
