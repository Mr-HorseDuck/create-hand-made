package com.alben.createhandmade.recipe;

import java.util.List;

import com.alben.createhandmade.CreateHandMade;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.content.processing.sequenced.SequencedRecipe;

import net.createmod.catnip.codecs.stream.CatnipStreamCodecBuilders;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import org.jetbrains.annotations.NotNull;

/**
 * {@link HandMadeSequencedAssemblyRecipe} 的 serializer。
 *
 * <p>字段与 Create 的 {@code SequencedAssemblyRecipeSerializer} 完全一致
 * （{@code ingredient} / {@code transitional_item} / {@code sequence} / {@code results} / {@code loops}），
 * 所以整合包作者可以直接把原版 {@code create:sequenced_assembly} 的 JSON 换个 {@code type} 用。</p>
 *
 * <p><b>必须重写而不是复用 Create 的 serializer：</b>它的 {@code CODEC}/{@code STREAM_CODEC}
 * 字段是 {@code private}，且类型固定为 {@code RecipeSerializer<SequencedAssemblyRecipe>}；
 * {@code RecipeSerializer<T>} 的 {@code codec()} 返回 {@code MapCodec<T>}，
 * 泛型不变，子类无法把它改成 {@code MapCodec<HandMadeSequencedAssemblyRecipe>}。</p>
 *
 * <p><b>与 Create 版的两处刻意差异：</b></p>
 * <ul>
 *   <li>解码后校验每一步必须是 {@link HandMadeAssemblyRecipe} —— 否则在<b>加载期</b>就报错，
 *       而不是等到玩家把过渡物品放上传送带才发现「这一步没人能做」；</li>
 *   <li>Create 的 {@code STREAM_CODEC} 有一处参数错位（{@code sequence}/{@code results} 互换），
 *       这里按正确顺序写。</li>
 * </ul>
 */
public class HandMadeSequencedAssemblyRecipeSerializer
        implements RecipeSerializer<HandMadeSequencedAssemblyRecipe> {

    private static final MapCodec<HandMadeSequencedAssemblyRecipe> CODEC = RecordCodecBuilder.mapCodec(
            i -> i.group(
                    Ingredient.CODEC.fieldOf("ingredient")
                            .forGetter(HandMadeSequencedAssemblyRecipe::getIngredient),
                    ProcessingOutput.CODEC.fieldOf("transitional_item")
                            .forGetter(HandMadeSequencedAssemblyRecipe::transitionalOutput),
                    SequencedRecipe.CODEC.listOf()
                            .fieldOf("sequence")
                            .forGetter(HandMadeSequencedAssemblyRecipe::getSequence),
                    ProcessingOutput.CODEC.listOf()
                            .fieldOf("results")
                            .forGetter(r -> r.resultPool),
                    ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("loops", 1)
                            .forGetter(HandMadeSequencedAssemblyRecipe::getLoops))
                    .apply(i, (ingredient, transitional, sequence, results, loops) -> build(
                            ingredient, transitional, sequence, results, loops, true)));

    public static final StreamCodec<RegistryFriendlyByteBuf, HandMadeSequencedAssemblyRecipe> STREAM_CODEC =
            StreamCodec.composite(
                    Ingredient.CONTENTS_STREAM_CODEC, HandMadeSequencedAssemblyRecipe::getIngredient,
                    ProcessingOutput.STREAM_CODEC, HandMadeSequencedAssemblyRecipe::transitionalOutput,
                    CatnipStreamCodecBuilders.list(SequencedRecipe.STREAM_CODEC),
                    HandMadeSequencedAssemblyRecipe::getSequence,
                    CatnipStreamCodecBuilders.list(ProcessingOutput.STREAM_CODEC), r -> r.resultPool,
                    ByteBufCodecs.VAR_INT, HandMadeSequencedAssemblyRecipe::getLoops,
                    (ingredient, transitional, sequence, results, loops) -> build(
                            ingredient, transitional, sequence, results, loops, false));

    private static HandMadeSequencedAssemblyRecipe build(Ingredient ingredient,
                                                        ProcessingOutput transitional,
                                                        List<SequencedRecipe<?>> sequence,
                                                        List<ProcessingOutput> results,
                                                        int loops,
                                                        boolean initSteps) {
        warnAboutBadSteps(sequence);

        HandMadeSequencedAssemblyRecipe recipe = new HandMadeSequencedAssemblyRecipe(
                HandMadeRecipeTypes.HAND_SEQUENCED_ASSEMBLY.getSerializer());
        recipe.applyDecoded(ingredient, sequence, transitional, results, loops, initSteps);
        return recipe;
    }

    /**
     * 步骤必须是本模组的手搓 L3 配方。
     *
     * <p>只要求「{@code ProcessingRecipe} + {@code IAssemblyRecipe}」是 Create 的口径
     * （{@code SequencedRecipe.CODEC} 已经保证了）；但那样允许作者写 {@code create:pressing}
     * 之类机器步骤，而本类型的桶只有手搓工具会查 —— 那种步骤会永远没人执行。所以这里收紧。</p>
     *
     * <p><b>为什么要 warn 而不是抛出：</b>配方解码是在数据包/服务端加载的<b>原子</b>阶段跑的，
     * codec 里抛异常会让整个 {@code RecipeManager} 加载失败（实测：专用服务器直接起不来）。
     * 而 {@code MapCodec} 只能返回「成功/失败」，没有"这条配方失败、其余继续"的通道
     * （Create 自己用的是 {@code DataResult.error}，代价是整条配方被丢弃）。
     * 因此这里降级为<b>明确的一行 WARN</b>：服务器照常启动，作者在日志里能一眼看到问题，
     * 而这条配方在游戏里不会有任何工具去执行它（工具按家族类匹配，机器步骤永远匹配不上）。</p>
     */
    private static void warnAboutBadSteps(List<SequencedRecipe<?>> sequence) {
        for (SequencedRecipe<?> step : sequence) {
            if (!(step.getAsAssemblyRecipe() instanceof HandMadeAssemblyRecipe)) {
                CreateHandMade.LOGGER.warn(
                        "[hand_sequenced_assembly] step '{}' is NOT a hand-crafted step; "
                                + "this recipe will never advance. Only create_hand_made:tool_recipe "
                                + "steps are supported here -- use create:sequenced_assembly for machine steps.",
                        step.getRecipe()
                                .getClass()
                                .getSimpleName());
            }
        }
    }

    @Override
    public @NotNull MapCodec<HandMadeSequencedAssemblyRecipe> codec() {
        return CODEC;
    }

    @Override
    public @NotNull StreamCodec<RegistryFriendlyByteBuf, HandMadeSequencedAssemblyRecipe> streamCodec() {
        return STREAM_CODEC;
    }
}
