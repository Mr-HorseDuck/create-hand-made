package com.alben.createhandmade.recipe.type;

import com.alben.createhandmade.recipe.HandMadeRecipeTypes;
import com.alben.createhandmade.recipe.HandMadeToolRecipeLike;
import com.alben.createhandmade.recipe.ToolType;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.content.fluids.transfer.FillingRecipe;
import com.simibubi.create.content.kinetics.crusher.AbstractCrushingRecipe;
import com.simibubi.create.content.kinetics.deployer.ItemApplicationRecipe;
import com.simibubi.create.content.kinetics.press.PressingRecipe;
import com.simibubi.create.content.kinetics.saw.CuttingRecipe;
import com.simibubi.create.content.processing.basin.BasinRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeBuilder.ProcessingRecipeParams;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleItemRecipe;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.wrapper.RecipeWrapper;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * 本模组 L3 独占配方的全部实现类 + {@code fan_type} 枚举，都放在这一个文件里。
 *
 * <p>它们都只服务 {@code create_hand_made:tool_recipe} / {@code stonecutting_recipe}
 * / {@code bellows_recipe} 三种自建类型，各自只有几十行，拆成 9 个文件没有收益。</p>
 *
 * <p>共同点：都是"继承 Create 的某个配方家族父类，只把 getType/getSerializer 改到自己的类型"，
 * 从而让手搓工具读到的配方与 Create 机器完全隔离。</p>
 */
public final class HandMadeRecipes {

    private HandMadeRecipes() {}

    /**
     * 风箱独占配方（{@code create_hand_made:bellows_recipe}）的 {@code fan_type} 取值。
     *
     * <p>四种鼓风与 Create 的四个 {@code FanProcessingType} 一一对应，但这里<b>刻意不引用</b>
     * Create 的类型对象（{@code AllFanProcessingTypes.BlastingType} 等）：配方层只需要
     * "是哪一种鼓风"这一条信息，用本模组自己的枚举可以让 {@code recipe} 包完全不依赖
     * Create 的风扇实现类。</p>
     *
     * <p>常量名与 Create 的 4 个类型 id 的后缀一致：{@code create:blasting} → {@link #BLASTING}。</p>
     */
    public enum FanType {

        /** 鼓风熔炼（Create {@code create:blasting}）。 */
        BLASTING,

        /** 鼓风烟熏（Create {@code create:smoking}）。 */
        SMOKING,

        /** 鼓风缠魂（Create {@code create:haunting}）。 */
        HAUNTING,

        /** 鼓风洗涤（Create {@code create:splashing}）。 */
        SPLASHING;

        /** JSON / KubeJS 里写的字符串形式（{@code blasting} / {@code smoking} / …）。 */
        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** 解析 {@code fan_type} 字符串；未知取值返回 null（由调用方负责报错）。 */
        @Nullable
        public static FanType byId(String id) {
            for (FanType type : values()) {
                if (type.id().equals(id)) {
                    return type;
                }
            }
            return null;
        }

        /** 错误信息里列出的合法取值；从枚举推导，新增常量时不会漏改文案。 */
        public static String validIds() {
            StringBuilder sb = new StringBuilder();
            for (FanType type : values()) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(type.id());
            }
            return sb.toString();
        }
    }

    /**
     * 应用家族的 L3 独占配方，服务指杆（ToolType.POINTER）。
     *
     * <p>继承 ItemApplicationRecipe —— 2 槽 matches、keepHeldItem、
     * shouldKeepHeldItem 全部沿用父类。构造必须传一个 AllRecipeTypes 枚举
     * （父类唯一 public 构造要求），传 ITEM_APPLICATION；getType() 覆写为独立 type。</p>
     */
    public static class Application extends ItemApplicationRecipe
            implements HandMadeToolRecipeLike {

        @Nullable
        private ToolType tool;

        public Application(ProcessingRecipeParams params) {
            super(AllRecipeTypes.ITEM_APPLICATION, params);
        }

        @Override
        public RecipeType<?> getType() {
            return HandMadeRecipeTypes.TOOL_RECIPE.get();
        }

        @Override
        public RecipeSerializer<?> getSerializer() {
            return HandMadeRecipeTypes.TOOL_SERIALIZER.get();
        }

        @Nullable @Override public ToolType getTool() { return tool; }
        @Override public void setTool(@Nullable ToolType tool) { this.tool = tool; }
    }

    /**
     * 风箱的 L3 独占配方类，服务 {@code create_hand_made:bellows_recipe} 类型。
     *
     * <p>fan_type 用 ResourceLocation，支持任意第三方鼓风类型，
     * 必须是带命名空间的完整 id（如 {@code create:blasting}、{@code dndesires:sanding}）。</p>
     */
    public static class Bellows extends ProcessingRecipe<RecipeWrapper> {

        @Nullable
        private ResourceLocation fanType;

        public Bellows(ProcessingRecipeParams params) {
            super(HandMadeRecipeTypes.BELLOWS_RECIPE_INFO, params);
        }

        @Override
        public boolean matches(RecipeWrapper inv, Level level) {
            if (ingredients.isEmpty() || inv.getContainerSize() < 1) return false;
            if (inv.isEmpty()) return false;
            return ingredients.get(0).test(inv.getItem(0));
        }

        @Override protected int getMaxInputCount() { return 1; }
        @Override protected int getMaxOutputCount() { return 12; }
        @Override protected boolean canSpecifyDuration() { return false; }
        @Override protected int getMaxFluidInputCount() { return 0; }

        @Nullable public ResourceLocation getFanType() { return fanType; }
        public void setFanType(@Nullable ResourceLocation fanType) { this.fanType = fanType; }
    }

    /**
     * 碾磨家族的 L3 独占配方类，由研钵（ToolType.MORTAR）与
     * 碾钵（ToolType.CRUSHER_MORTAR）共用。
     *
     * <p>继承 AbstractCrushingRecipe，因为它提供了接受 IRecipeTypeInfo 的 public 构造，
     * 可以把本模组的 TOOL_RECIPE_INFO 传进去。代价：它不是 MillingRecipe 也不是
     * CrushingRecipe，所以工具侧与 JEI 侧的 instanceof 判定需要放宽。</p>
     */
    public static class Crushing extends AbstractCrushingRecipe
            implements HandMadeToolRecipeLike {

        /** 上限取「研磨 4」与「粉碎 7」的并集，取最大值不误拒碾钵的多产物配方。 */
        private static final int MAX_OUTPUT_COUNT = 7;

        @Nullable
        private ToolType tool;

        public Crushing(ProcessingRecipeParams params) {
            super(HandMadeRecipeTypes.TOOL_RECIPE_INFO, params);
        }

        @Override
        public boolean matches(RecipeWrapper inv, Level level) {
            if (inv.isEmpty()) return false;
            return !ingredients.isEmpty() && ingredients.get(0).test(inv.getItem(0));
        }

        @Override
        protected int getMaxOutputCount() {
            return MAX_OUTPUT_COUNT;
        }

        @Nullable @Override
        public ToolType getTool() { return tool; }

        @Override
        public void setTool(@Nullable ToolType tool) { this.tool = tool; }
    }

    /**
     * 切削家族的 L3 独占配方，服务手锯（ToolType.HAND_SAW）。
     * matches / 上限 / canSpecifyDuration 全部沿用父类 CuttingRecipe。
     */
    public static class Cutting extends CuttingRecipe
            implements HandMadeToolRecipeLike {

        @Nullable
        private ToolType tool;

        public Cutting(ProcessingRecipeParams params) {
            super(params);
        }

        @Override
        public RecipeType<?> getType() {
            return HandMadeRecipeTypes.TOOL_RECIPE.get();
        }

        @Override
        public RecipeSerializer<?> getSerializer() {
            return HandMadeRecipeTypes.TOOL_SERIALIZER.get();
        }

        @Nullable @Override public ToolType getTool() { return tool; }
        @Override public void setTool(@Nullable ToolType tool) { this.tool = tool; }
    }

    /**
     * 注液家族的 L3 独占配方，服务灌注枪（ToolType.INFUSION_GUN）。
     * matches / 上限 / getRequiredFluid 全部沿用父类 FillingRecipe。
     */
    public static class Filling extends FillingRecipe
            implements HandMadeToolRecipeLike {

        @Nullable
        private ToolType tool;

        public Filling(ProcessingRecipeParams params) {
            super(params);
        }

        @Override
        public RecipeType<?> getType() {
            return HandMadeRecipeTypes.TOOL_RECIPE.get();
        }

        @Override
        public RecipeSerializer<?> getSerializer() {
            return HandMadeRecipeTypes.TOOL_SERIALIZER.get();
        }

        @Nullable @Override public ToolType getTool() { return tool; }
        @Override public void setTool(@Nullable ToolType tool) { this.tool = tool; }
    }

    /**
     * 冲压家族的 L3 独占配方，服务冲压锤 · 置物台（ToolType.PRESS_HAMMER_DEPOT）。
     * matches / 上限 / canSpecifyDuration 全部沿用父类 PressingRecipe。
     */
    public static class Pressing extends PressingRecipe
            implements HandMadeToolRecipeLike {

        @Nullable
        private ToolType tool;

        public Pressing(ProcessingRecipeParams params) {
            super(params);
        }

        @Override
        public RecipeType<?> getType() {
            return HandMadeRecipeTypes.TOOL_RECIPE.get();
        }

        @Override
        public RecipeSerializer<?> getSerializer() {
            return HandMadeRecipeTypes.TOOL_SERIALIZER.get();
        }

        @Nullable @Override public ToolType getTool() { return tool; }
        @Override public void setTool(@Nullable ToolType tool) { this.tool = tool; }
    }

    /** 手锯的切石配方（{@code create_hand_made:stonecutting_recipe}）。 */
    public static class Stonecutting extends SingleItemRecipe {

        /** 1.20.1 的 SingleItemRecipe 构造需要 ResourceLocation 作为第三个参数。 */
        public Stonecutting(ResourceLocation id, String group,
                            Ingredient ingredient, ItemStack result) {
            super(HandMadeRecipeTypes.STONECUTTING_RECIPE.get(),
                    HandMadeRecipeTypes.STONECUTTING_SERIALIZER.get(),
                    id, group, ingredient, result);
        }

        @Override
        public boolean matches(Container container, Level level) {
            return !container.isEmpty() && this.ingredient.test(container.getItem(0));
        }
    }

    /**
     * 工作盆家族的 L3 独占配方类，服务冲压锤（ToolType.PRESS_HAMMER_BASIN）
     * 和搅拌杖（ToolType.STIRRING_STAFF）。
     *
     * <p>继承 BasinRecipe（而非 ProcessingRecipe），因为它提供
     * protected BasinRecipe(IRecipeTypeInfo, ProcessingRecipeParams) 构造。
     * 白拿的好处：工具侧的 BasinRecipe.match(basin, recipe) / apply(...) 直接可用，
     * JEI 现有类别的 instanceof BasinRecipe 判定也能识别它。</p>
     *
     * <p>注意：BasinRecipe.matches(Container, Level) 恒返回 false（Create 的原设计），
     * 匹配走静态 BasinRecipe.match，所以本类不需要覆写 matches。</p>
     */
    public static class Tool extends BasinRecipe
            implements HandMadeToolRecipeLike {

        @Nullable
        private ToolType tool;

        public Tool(ProcessingRecipeParams params) {
            super(HandMadeRecipeTypes.TOOL_RECIPE_INFO, params);
        }

        @Nullable @Override
        public ToolType getTool() { return tool; }

        @Override
        public void setTool(@Nullable ToolType tool) { this.tool = tool; }
    }
}
