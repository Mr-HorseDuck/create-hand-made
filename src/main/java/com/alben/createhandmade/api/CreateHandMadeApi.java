package com.alben.createhandmade.api;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import com.alben.createhandmade.ModDataComponents;
import com.alben.createhandmade.bellows.BellowsMediaRegistry;
import com.alben.createhandmade.item.InfusionGunContents;
import com.alben.createhandmade.item.InfusionGunItem;
import com.alben.createhandmade.item.MortarContents;
import com.alben.createhandmade.item.MortarItem;
import com.alben.createhandmade.recipe.HandMadeRecipeFilters;
import com.alben.createhandmade.recipe.HandMadeRecipePool;
import com.alben.createhandmade.recipe.HandMadeTool;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Create: Hand Made 对外 API 的<b>稳定入口</b>。
 *
 * <h3>为什么需要这一层</h3>
 * <p>各工具类（{@code BellowsItem} / {@code InfusionGunItem} / {@code MortarItem} / …）里的查询与执行方法
 * 已经是 {@code public static}，可以直接调用；但那些方法的名字、返回类型（{@code RecipeHolder<?>}、
 * {@code StandardProcessingRecipe<?>} 等 Create 内部类型）会随内部重构而变。
 * <b>跨模组请优先绑定本类</b>：这里的签名视为稳定，内部再转发到具体工具类。
 * 需要新的查询能力时，在本类新增方法即可，不会破坏已有调用方。</p>
 *
 * <h3>稳定性承诺</h3>
 * <ul>
 *   <li>{@link #API_VERSION} 每次破坏性变更递增；新增方法不算破坏性变更。</li>
 *   <li>本类只提供<b>查询/读写组件</b>级别的能力，不提供"替玩家执行一次加工"这类写操作；
 *       写操作的入口在各工具类上，并带有副作用（见下）。</li>
 * </ul>
 *
 * <h3>写操作的副作用（直接调用工具类 public 方法时必读）</h3>
 * <table>
 *   <tr><th>方法</th><th>副作用</th></tr>
 *   <tr><td>{@code InfusionGunItem#finishInject}</td>
 *       <td>构造注液后的产出并<b>替换</b>传送物品；不检查幂等，重复调用会多出产出</td></tr>
 *   <tr><td>{@code PointerItem#executeDepotProcess}</td>
 *       <td>直接改置物台状态并产出；<b>只在服务端调用</b></td></tr>
 *   <tr><td>{@code BellowsItem#applyToTarget}</td>
 *       <td>对目标应用鼓风效果，会跨模组触发上游效果并可能转化物品</td></tr>
 *   <tr><td>{@code PressHammerItem#tryPressBasin} / {@code tryPressTransported}</td>
 *       <td>消耗输入并产出，播放粒子/音效；<b>只在服务端调用</b></td></tr>
 *   <tr><td>{@code HandSawItem#executeCut} / {@code stripBlock}</td>
 *       <td>破坏/改变世界方块；<b>只在服务端调用</b>，且需自行负责耐久与玩家行为约束</td></tr>
 * </table>
 * <p>本模组自身的调用点都在 {@code if (!level.isClientSide)} 之内；开放后请在调用方自行保证这一前提。</p>
 */
public final class CreateHandMadeApi {

    /** API 版本：破坏性变更时递增。 */
    public static final int API_VERSION = 1;

    private CreateHandMadeApi() {
    }

    // ==================== 配方查询 ====================

    /** 某工具在给定世界里可用的全部基础配方（顺序即游戏内的查询优先顺序）。 */
    public static List<RecipeHolder<?>> recipesFor(HandMadeTool tool, Level level) {
        return HandMadeRecipePool.getBaseRecipes(tool, level);
    }

    /** 该配方是否被数据包 / KubeJS 的过滤器对本工具禁用。 */
    public static boolean isRecipeDisabled(HandMadeTool tool, ResourceLocation recipeId) {
        return HandMadeRecipeFilters.isDisabled(tool, recipeId);
    }

    // ==================== 风箱介质 ====================

    /** 该物品能充当哪些风箱处理类型（id 列表，可能为空）。 */
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

    /** 读灌注枪内的流体（永不返回 {@code null}；空枪返回 {@link FluidStack#EMPTY}）。 */
    public static InfusionGunContents infusionGunContents(ItemStack gun) {
        return InfusionGunItem.getContents(gun);
    }

    /** 写灌注枪内的流体；{@code contents} 为空时会移除组件（纯空枪不会留下空组件）。 */
    public static void setInfusionGunContents(ItemStack gun, InfusionGunContents contents) {
        InfusionGunItem.setContents(gun, contents);
    }

    /** 读取世界里某个位置的流体源（<b>只读</b>，不消耗、不改变世界）。 */
    public static FluidStack fluidSourceAt(Level level, BlockPos pos) {
        return InfusionGunItem.getFluidFromSource(level, pos);
    }

    // ==================== 研钵 / 碾钵 ====================

    /** 读研钵内的内容物；空碗返回 {@code null}。 */
    @Nullable
    public static MortarContents mortarContents(ItemStack stack) {
        return stack.get(ModDataComponents.MORTAR_CONTENTS.get());
    }

    /** 写研钵内的内容物；传 {@code null} 表示清空。 */
    public static void setMortarContents(ItemStack stack, @Nullable MortarContents contents) {
        if (contents == null)
            stack.remove(ModDataComponents.MORTAR_CONTENTS.get());
        else
            stack.set(ModDataComponents.MORTAR_CONTENTS.get(), contents);
    }

    /** 按当前配置计算这次加工需要多少 tick（不含起手）。 */
    public static int mortarProcessingTicks(MortarContents contents, boolean crusherMortar) {
        return MortarItem.processingTicks(contents, crusherMortar);
    }
}
