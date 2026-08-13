package com.xinantown.sfprice;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import io.github.thebusybiscuit.slimefun4.api.recipes.RecipeType;
import org.bukkit.inventory.ItemStack;

/**
 * SlimefunItem 的最小视图（依赖注入用）：id / 配方 / 配方类型 / 来源附属。
 *
 * <p>生产环境用 {@link #adapt(SlimefunItem)} 包装真实物品；测试用纯 record fake，
 * 从而在无服务器环境验证递归成本算法。getId() 在真实类上是 final 不可 override，
 * 故不直接继承 SlimefunItem。
 *
 * <p>{@code recipeTypeKey()} 返回 RecipeType 的 NamespacedKey key（字符串），
 * 避免在无服务器测试环境触发 {@code RecipeType} 类静态初始化
 * （其 clinit 会构造 Bukkit NamespacedKey，需 Plugin 实例，纯 JVM 抛
 * {@code IllegalArgumentException: Plugin cannot be null}）。
 */
public interface SlimefunItemView {

    String id();

    ItemStack[] recipe();

    String recipeTypeKey();

    String addonName();

    /** 配方输出物品（Slimefun 9 格配方 index 8 = 输出；成本计算必须排除输出格）。 */
    ItemStack recipeOutput();

    /** 包装真实 SlimefunItem。 */
    static SlimefunItemView adapt(SlimefunItem item) {
        return new SlimefunItemView() {
            @Override
            public String id() {
                return item.getId();
            }

            @Override
            public ItemStack[] recipe() {
                return item.getRecipe();
            }

            @Override
            public String recipeTypeKey() {
                var type = item.getRecipeType();
                return type == null ? null : type.getKey().getKey();
            }

            @Override
            public ItemStack recipeOutput() {
                return item.getRecipeOutput();
            }

            @Override
            public String addonName() {
                try {
                    var addon = item.getAddon();
                    if (addon == null) return null;
                    var plugin = addon.getJavaPlugin();
                    return plugin == null ? addon.getName() : plugin.getName();
                } catch (Throwable t) {
                    return null;
                }
            }
        };
    }
}
