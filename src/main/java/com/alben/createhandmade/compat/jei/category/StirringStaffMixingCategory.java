package com.alben.createhandmade.compat.jei.category;

import com.alben.createhandmade.compat.jei.JeiRecipeSource;
import com.alben.createhandmade.compat.jei.ModJeiTypes;
import com.alben.createhandmade.item.ModItems;
import com.alben.createhandmade.recipe.HandMadeTool;
import com.alben.createhandmade.recipe.HandMadeToolRecipe;
import com.zurrtum.create.AllRecipeTypes;
import com.zurrtum.create.client.compat.jei.category.BasinCategory;
import com.zurrtum.create.client.compat.jei.renderer.TwoIconRenderer;
import com.zurrtum.create.content.kinetics.mixer.PotionRecipe;
import com.zurrtum.create.content.processing.basin.BasinRecipe;
import com.zurrtum.create.content.processing.recipe.ProcessingOutput;
import com.zurrtum.create.infrastructure.fluids.FluidStack;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.types.IRecipeType;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.ArrayList;
import java.util.List;

/**
 * 搅拌杖 · 混合（#9）与搅拌杖 · 自动酿造（#11）两个类别。
 *
 * <p>上游（NeoForge 1.21.1）把 {@code standard / autoShapeless / autoBrewing} 做成同一个类的三种
 * {@code MixingType}，共用 {@code BasinCategory} 的通用布局；26.1 的 Create Fly 把「酿造」
 * 拆成了独立的 {@code PotionCategory}（布局差别很大），所以这里也拆成两个类：
 * 外层 = 混合（照抄 {@code MixingCategory}），内嵌 {@link Brewing} = 酿造（照抄
 * {@code PotionCategory}）。</p>
 *
 * <p><b>两者共用 {@code BasinCategory<BasinRecipe>}：</b>L1 的 {@code MixingRecipe}、
 * {@code PotionRecipe} 与 L3 的 {@code HandMadeToolRecipe} 都 implements
 * {@link BasinRecipe}，而 {@link ModJeiTypes#STIRRING_STAFF_MIXING} 与
 * {@link ModJeiTypes#STIRRING_STAFF_AUTO_BREWING} 绑的都是
 * {@code RecipeHolder<BasinRecipe>} —— 一个基类覆盖两个类别，不用改类型常量。</p>
 */
public class StirringStaffMixingCategory extends BasinCategory<BasinRecipe> {

    private static final String TITLE_KEY = "jei.create_hand_made.stirring_staff_mixing";

    /** 产物区基准 y：产物总数 ≤ 4 时 51，更多时下移 9（Create 的原算法）。 */
    private static final int RESULT_Y_SHORT = 51;
    private static final int RESULT_Y_TALL = 60;
    /** 产物总数超过这个值就换到「高布局」。 */
    private static final int RESULT_COUNT_TALL = 4;
    /** 装饰：x 抄 Create 的 {@code MixingBasinRenderState} 原点 91；y 由 −5 改为 8。 */
    private static final int DECOR_X = 91;
    private static final int DECOR_Y = 8;

    /**
     * 源：{@code AllRecipeTypes.MIXING} + L3 {@code HandMadeToolRecipe}
     * （{@code tool = stirring_staff} 那一支）。
     *
     * <p>与 {@code HandMadeRecipePool.collectStirringStaff} 一一对应（那里还额外 accept
     * {@code HandMadeRecipeRegistry.appendExclusive} 的数据包追加表 —— 后者客户端拿不到，
     * 见 PLAN 的风险项）。</p>
     */
    public static List<RecipeHolder<BasinRecipe>> getRecipes() {
        List<RecipeHolder<BasinRecipe>> list = new ArrayList<>();
        JeiRecipeSource.appendBasin(list, AllRecipeTypes.MIXING, HandMadeTool.STIRRING_STAFF, null);
        JeiRecipeSource.appendBasin(list, JeiRecipeSource.l3Tool(), HandMadeTool.STIRRING_STAFF,
                recipe -> recipe instanceof HandMadeToolRecipe handMade
                        && handMade.tool() == HandMadeTool.STIRRING_STAFF);
        return list;
    }

    @Override
    public IRecipeType<RecipeHolder<BasinRecipe>> getRecipeType() {
        return ModJeiTypes.STIRRING_STAFF_MIXING;
    }

    @Override
    public Component getTitle() {
        return Component.translatable(TITLE_KEY);
    }

    @Override
    public IDrawable getIcon() {
        // Create 是 (机械搅拌器, 工作盆)；这里换成 (搅拌杖, 炼药锅)。
        return new TwoIconRenderer(ModItems.STIRRING_STAFF, Items.CAULDRON);
    }

    // getHeight() 由 BasinCategory 提供（103），不覆写。

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<BasinRecipe> holder,
                          IFocusGroup focuses) {
        BasinRecipe recipe = holder.value();

        addIngredientSlots(builder, recipe);

        // 产物：物品与流体共用一套「两列 + 奇数居中」坐标，所以要把两者的总数先算出来，
        // 再分别调 addResultSlots / addFluidResultSlots（逐句照抄 MixingCategory）。
        List<ProcessingOutput> results = BasinSlots.results(recipe);
        List<FluidStack> fluidResults = BasinSlots.fluidResults(recipe);
        int itemCount = results.size();
        int count = itemCount + fluidResults.size();
        boolean odd = count % 2 != 0;
        int lastIndex = count - 1;
        int yBase = count <= RESULT_COUNT_TALL ? RESULT_Y_SHORT : RESULT_Y_TALL;

        addResultSlots(builder, results, 0, lastIndex, yBase, odd);
        addFluidResultSlots(builder, fluidResults, itemCount, lastIndex, yBase, odd);
        addHeatSlots(builder, recipe);
    }

    @Override
    public void draw(RecipeHolder<BasinRecipe> holder, IRecipeSlotsView slotsView,
                     GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
        BasinRecipe recipe = holder.value();
        int resultCount = BasinSlots.results(recipe).size() + BasinSlots.fluidResults(recipe).size();
        // 箭头 / 加热条 / 加热等级文字全在 BasinCategory.drawBackground 里。
        drawBackground(recipe, graphics, resultCount);
        // Create 在这里画搅拌器 + 工作盆 3D 模型（MixingBasinRenderState）；手工工具画自己的图标。
        graphics.item(new ItemStack(ModItems.STIRRING_STAFF), DECOR_X, DECOR_Y);
    }

    /**
     * 搅拌杖 · 自动酿造（#11）。
     *
     * <p><b>布局逐句照抄 26.1 的 {@code PotionCategory}</b>（{@code javap -p -c} 核对）——
     * 它是 26.1 Create 注册 {@code AUTOMATIC_BREWING} 用的那个类（不是上游想象的
     * {@code MixingCategory} 通用布局）：</p>
     * <ul>
     *   <li>输入物品槽 {@code (21, 51)} = {@code ingredient()}；</li>
     *   <li>输入流体槽 {@code (40, 51)} = {@code fluidIngredient()}（药水基底，如水瓶）；</li>
     *   <li>输出流体槽 {@code (142, 51)} = {@code result()}；</li>
     *   <li>热量：{@code PotionCategory} 硬编码「只需要烈焰燃烧器」——正好等于
     *       {@code addHeatSlots} 在 {@code heat() == HEATED} 时的行为
     *       （{@code !HEATED.testBlazeBurner(NONE)} → 加燃烧器槽 {@code (134, 81)}；
     *       {@code HEATED.testBlazeBurner(KINDLED)} → 不加烈焰蛋糕槽）。
     *       {@code PotionRecipe.heat()} 实测就返回 {@code HEATED}（{@code javap -c} 核对），
     *       所以这里直接复用 helper，不单独写槽位。</li>
     *   <li>{@code draw}：{@code drawBackground(recipe, graphics, 1)} 得到与
     *       {@code PotionCategory} 相同的 {@code JEI_DOWN_ARROW}(136, 32) + 加热条 + 文字。</li>
     * </ul>
     *
     * <p><b>为什么显示类型仍是 {@code BasinRecipe}：</b>{@code PotionRecipe}
     * {@code implements BasinRecipe}，而类型常量 {@code STIRRING_STAFF_AUTO_BREWING} 绑的是
     * {@code RecipeHolder<BasinRecipe>}（UID 不许动）。所以 {@code setRecipe} 里先
     * {@code instanceof PotionRecipe} 取具体 accessor；{@code POTION} 这个数据源里只会有
     * {@code PotionRecipe}，取不到时直接返回空布局（防御）。</p>
     */
    public static class Brewing extends BasinCategory<BasinRecipe> {

        private static final String TITLE_KEY = "jei.create_hand_made.stirring_staff_auto_brewing";

        /** 与 {@code PotionCategory} 一致的三处槽位坐标。 */
        private static final int INPUT_ITEM_X = 21;
        private static final int INPUT_FLUID_X = 40;
        private static final int OUTPUT_FLUID_X = 142;
        private static final int SLOT_Y = 51;
        /** 装饰：x 抄 {@code MixingBasinRenderState} 原点 91；y 由 −5 改为 8。 */
        private static final int DECOR_X = 91;
        private static final int DECOR_Y = 8;

        /**
         * 源：{@code AllRecipeTypes.POTION}（{@code PotionRecipe}）。
         *
         * <p>L2 过滤工具用 {@code stirring_staff_auto_brewing}（它是 {@code HandMadeTool}
         * 的独立常量，数据包可以单独关掉酿造这一族），与
         * {@code HandMadeRecipePool.collectStirringStaffAutoBrewing} 对应。
         * 服务端还有一个 {@code allowBrewingInMixer} 的 config 开关，客户端读不到
         * （{@code AllConfigs.server()} 是服务端对象），JEI 层不做这道门。</p>
         */
        public static List<RecipeHolder<BasinRecipe>> getRecipes() {
            List<RecipeHolder<BasinRecipe>> list = new ArrayList<>();
            JeiRecipeSource.appendBasin(list, AllRecipeTypes.POTION,
                    HandMadeTool.STIRRING_STAFF_AUTO_BREWING, null);
            return list;
        }

        @Override
        public IRecipeType<RecipeHolder<BasinRecipe>> getRecipeType() {
            return ModJeiTypes.STIRRING_STAFF_AUTO_BREWING;
        }

        @Override
        public Component getTitle() {
            return Component.translatable(TITLE_KEY);
        }

        @Override
        public IDrawable getIcon() {
            // 与 PotionCategory 同构：(机械搅拌器, 酿造台) → (搅拌杖, 酿造台)。
            return new TwoIconRenderer(ModItems.STIRRING_STAFF, Items.BREWING_STAND);
        }

        // getHeight() 同样由 BasinCategory 提供（103），与 PotionCategory 的 103 一致。

        @Override
        public void setRecipe(IRecipeLayoutBuilder builder, RecipeHolder<BasinRecipe> holder,
                              IFocusGroup focuses) {
            if (!(holder.value() instanceof PotionRecipe recipe)) {
                // POTION 类型里只会有 PotionRecipe；走到这里说明数据源之外的东西被塞进来了，
                // 宁可空布局也不要抛异常（注册期抛异常会让整个 JEI 插件失效）。
                return;
            }

            builder.addInputSlot(INPUT_ITEM_X, SLOT_Y)
                    .setBackground(SLOT, -1, -1)
                    .add(recipe.ingredient());

            addFluidSlot(builder, INPUT_FLUID_X, SLOT_Y, recipe.fluidIngredient())
                    .setBackground(SLOT, -1, -1);

            addFluidSlot(builder, OUTPUT_FLUID_X, SLOT_Y, recipe.result())
                    .setBackground(SLOT, -1, -1);

            // 药水恒为 HEATED ⇒ 只会得到 (134, 81) 的烈焰燃烧器槽，与 PotionCategory 完全一致。
            addHeatSlots(builder, recipe);
        }

        @Override
        public void draw(RecipeHolder<BasinRecipe> holder, IRecipeSlotsView slotsView,
                         GuiGraphicsExtractor graphics, double mouseX, double mouseY) {
            // 药水只有一个流体产物 ⇒ count = 1 ⇒ 箭头落在 (136, 32)，与 PotionCategory 相同。
            drawBackground(holder.value(), graphics, 1);
            graphics.item(new ItemStack(ModItems.STIRRING_STAFF), DECOR_X, DECOR_Y);
        }
    }
}
