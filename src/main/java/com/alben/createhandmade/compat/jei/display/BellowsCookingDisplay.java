package com.alben.createhandmade.compat.jei.display;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * 风箱熔炼 / 烟熏的归一化显示配方（#13 / #14）：单输入 + 单产物。
 *
 * <p><b>为什么需要一个新的显示类型（而不是直接用原版烹饪配方）：</b></p>
 * <ul>
 *   <li>这两个类别的数据源有两侧：原版 {@code RecipeType.SMELTING / BLASTING / SMOKING}
 *       （类型是 {@code AbstractCookingRecipe} / {@code SingleItemRecipe}）与本模组 L3
 *       {@code create_hand_made:bellows_recipe}（{@code HandMadeBellowsRecipe}，它不是烹饪配方，
 *       只 implements {@code CreateSingleStackRollableRecipe}）；</li>
 *   <li>JEI 的 {@code IRecipeType<T>} 是<b>不变型</b>，一个类别只能有一个显示类型 ——
 *       两侧必须归一化到同一个 T 上，否则 L3 配方会被编译器挡在类别之外；</li>
 *   <li>两侧共同的语义只有「一个原料 → 一个产物」这一条（烹饪配方天然单产物，
 *       L3 是多产物但按烹饪语义只取第一个），所以归一成一个纯数据 record 是最小代价。</li>
 * </ul>
 *
 * <p><b>形态照抄 Create 自己的 {@code com.zurrtum.create.client.compat.jei.display.BlockCuttingDisplay}</b>
 * （{@code javap -p} 实测：{@code final class ... extends Record}，分量
 * {@code (Identifier id, Ingredient input, List<List<ItemStackTemplate>> outputs)}）：
 * 同样是「纯显示 record + 带一个 id」。</p>
 *
 * <p><b>为什么没有 {@code RecipeHolder} 包装</b>（与其它 15 个类型不同）：
 * {@code RecipeHolder<T>} 的 {@code T} 有 {@code extends Recipe<?>} 上界（切片 1 实测），
 * 而本 record 只是显示数据、不是配方，装不进 holder。所以
 * {@code ModJeiTypes.BELLOWS_BLASTING / BELLOWS_SMOKING} 直接绑
 * {@code IRecipeType<BellowsCookingDisplay>}（与 Create 的 {@code BLOCK_CUTTING} 同类形态），
 * <b>UID 一个字符未动</b>。</p>
 *
 * @param id     配方 id（取原生配方 holder 的 id：JEI 的收藏 / 书签 / 历史据此稳定）
 * @param input  输入原料
 * @param result 产物（26.1 配方层用 {@link ItemStackTemplate}，槽位可直接 {@code add(ItemStackTemplate)}）
 */
public record BellowsCookingDisplay(Identifier id, Ingredient input, ItemStackTemplate result) {
}
