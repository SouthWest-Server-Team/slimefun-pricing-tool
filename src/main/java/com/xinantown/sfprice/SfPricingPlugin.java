package com.xinantown.sfprice;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

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
        // 首次启动把内置 material-prices.yml 复制到数据目录（管理员可直接编辑调价）
        saveResource("material-prices.yml", false);
        getLogger().info("SfPricingTool 已启用（Slimefun 成本推导工具）");
    }

    @Override
    public void onDisable() {
        getLogger().info("SfPricingTool 已禁用");
    }

    /** 材料价表输入流：数据目录可编辑版优先，resources 内置版兜底。 */
    public InputStream getPriceTableInput() {
        File external = new File(getDataFolder(), "material-prices.yml");
        if (external.exists()) {
            try {
                return new FileInputStream(external);
            } catch (FileNotFoundException ignored) {
                // fall through
            }
        }
        return getResource("material-prices.yml");
    }
}
