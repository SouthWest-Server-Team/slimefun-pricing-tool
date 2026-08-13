package com.xinantown.sfprice;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/** /sfprice 命令：scan（全量扫描） / scan &lt;id&gt;（单物品调试）。 */
public final class SfPriceCommand implements CommandExecutor, TabCompleter {

    private final SfPricingPlugin plugin;

    public SfPriceCommand(SfPricingPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("sfprice.admin")) {
            sender.sendMessage("§c你没有权限使用 /sfprice");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage("§e用法: /sfprice scan [id] — 扫描 Slimefun 物品成本（id 省略 = 全量）");
            return true;
        }
        if (!args[0].equalsIgnoreCase("scan")) {
            sender.sendMessage("§c未知子命令: " + args[0] + "（仅支持 scan）");
            return true;
        }
        if (plugin.getServer().getPluginManager().getPlugin("Slimefun") == null) {
            sender.sendMessage("§cSlimefun 未加载，无法扫描");
            return true;
        }
        String id = args.length >= 2 ? args[1] : null;
        // 扫描可能耗时（全量物品 × 递归配方），异步执行，完成后主线程发结果
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            ScanResult result = PriceScanner.scan(id);
            plugin.getServer().getScheduler().runTask(plugin, () -> report(sender, result));
        });
        sender.sendMessage("§e正在扫描" + (id == null ? "全部物品" : " " + id) + "…（完成会通知）");
        return true;
    }

    private void report(CommandSender sender, ScanResult r) {
        if (r.error() != null) {
            sender.sendMessage("§c扫描失败: " + r.error());
            return;
        }
        sender.sendMessage("§a扫描完成！");
        sender.sendMessage("§e  已定价物品: §f" + r.pricedCount() + " 个");
        sender.sendMessage("§e  无配方(白名单手动定价): §f" + r.noRecipeCount() + " 个");
        sender.sendMessage("§e  配方循环依赖: §f" + r.cycles().size() + " 处");
        sender.sendMessage("§e  缺原版材料价(需补价表): §f" + r.missingMaterials().size() + " 种");
        sender.sendMessage("§a  已导出: §f" + r.outputPath());
        if (!r.missingMaterials().isEmpty()) {
            sender.sendMessage("§c  ⚠ 缺价材料示例: §f" + String.join(", ", r.missingMaterials().keySet()));
        }
        if (!r.cycles().isEmpty()) {
            sender.sendMessage("§c  ⚠ 循环示例: §f" + String.join("; ", r.cycles()));
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) return List.of("scan");
        return List.of();
    }
}
