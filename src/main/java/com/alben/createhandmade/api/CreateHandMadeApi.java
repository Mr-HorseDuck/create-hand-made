package com.alben.createhandmade.api;

import com.alben.createhandmade.bellows.BellowsMediaRegistry;
import com.alben.createhandmade.item.InfusionGunItem;
import com.alben.createhandmade.item.contents.InfusionGunContents;
import com.alben.createhandmade.item.contents.MortarContents;
import com.alben.createhandmade.recipe.HandMadeRecipePool;
import com.alben.createhandmade.recipe.ToolType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Create: Hand Made 对外 API 的稳定入口。
 *
 * <p>跨模组请优先绑定本类：这里的签名视为稳定，内部转发到具体工具类。
 * 需要新能力时在本类新增方法，不破坏已有调用方。</p>
 *
 * <p>{@link #API_VERSION} 破坏性变更时递增；新增方法不算破坏性变更。</p>
 */
public final class CreateHandMadeApi {

    public static final int API_VERSION = 1;

    private CreateHandMadeApi() {}

    // ==================== 配方查询 ====================

    /**
     * 某工具在给定世界里可用的全部基础配方（L1 + L3，顺序即游戏内的查询优先顺序）。
     *
     * <p>转发到 {@link HandMadeRecipePool#getBaseRecipes}，和 1.21 语义一致：
     * L1 按工具对应的 Create RecipeType 全量拉取，L3 按 tool 字段过滤，
     * 不调 {@code matches()}，不依赖输入物品。</p>
     */
    public static List<Recipe<?>> recipesFor(ToolType tool, Level level) {
        return HandMadeRecipePool.getBaseRecipes(tool, level);
    }

    /** 该配方是否被数据包 / KubeJS 的过滤器对本工具禁用。 */
    public static boolean isRecipeDisabled(ToolType tool, ResourceLocation recipeId) {
        return HandMadeRecipePool.isRecipeDisabled(tool, recipeId);
    }

    // ==================== 风箱介质 ====================

    /** 该物品能充当哪些风箱处理类型。 */
    public static List<ResourceLocation> bellowsMediaTypes(ItemStack stack) {
        return BellowsMediaRegistry.resolveAll(stack);
    }

    /** 该物品是否能作为风箱介质。 */
    public static boolean isBellowsMedia(ItemStack stack) {
        return BellowsMediaRegistry.isMedia(stack);
    }

    /** 某风箱处理类型对应的介质物品列表。 */
    public static List<ItemStack> bellowsMediaItems(ResourceLocation typeId) {
        return BellowsMediaRegistry.getMediaFor(typeId);
    }

    /** 某风箱处理类型是否被配置禁用。 */
    public static boolean isBellowsTypeDisabled(ResourceLocation typeId) {
        return BellowsMediaRegistry.isTypeDisabled(typeId);
    }

    // ==================== 灌注枪 ====================

    /** 灌注枪容量（mB）。 */
    public static int infusionGunCapacity() {
        return InfusionGunContents.CAPACITY;
    }

    /** 读灌注枪内的流体（永不返回 null；空枪返回 FluidStack.EMPTY）。 */
    public static InfusionGunContents infusionGunContents(ItemStack gun) {
        return InfusionGunItem.getContents(gun);
    }

    /** 写灌注枪内的流体；contents 为空时移除组件。 */
    public static void setInfusionGunContents(ItemStack gun, InfusionGunContents contents) {
        InfusionGunItem.setContents(gun, contents);
    }

    /** 读取世界里某个位置的流体源（只读，不消耗、不改变世界）。 */
    public static FluidStack fluidSourceAt(Level level, BlockPos pos) {
        return InfusionGunItem.getFluidFromSource(level, pos);
    }

    // ==================== 研钵 / 碾钵 ====================

    /** 读研钵内的内容物；空碗返回 null。 */
    @Nullable
    public static MortarContents mortarContents(ItemStack stack) {
        return MortarContents.fromStack(stack);
    }

    /** 写研钵内的内容物；传 null 表示清空。 */
    public static void setMortarContents(ItemStack stack, @Nullable MortarContents contents) {
        if (contents == null) {
            MortarContents.clearFromStack(stack);
        } else {
            contents.writeToStack(stack);
        }
    }

    /** 按当前配置计算这次加工需要多少 tick（不含起手）。 */
    public static int mortarProcessingTicks(boolean crusherMortar) {
        return crusherMortar
                ? com.alben.createhandmade.Config.INSTANCE.crusherMortarProcessingTicks.get()
                : com.alben.createhandmade.Config.INSTANCE.mortarProcessingTicks.get();
    }
}