package com.alben.createhandmade.recipe.serializer;

import com.alben.createhandmade.recipe.HandMadeSequencedAssemblyRecipe;
import com.google.gson.JsonObject;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeSerializer;

/**
 * {@code create_hand_made:hand_sequenced_assembly} 的 serializer。
 *
 * <p>字段布局与 Create 的 {@code SequencedAssemblyRecipeSerializer} 完全一致
 * （{@code ingredient} / {@code transitionalItem} / {@code sequence} / {@code results} / {@code loops}），
 * 所以整合包作者只要把现成的原版序列组装配方的 {@code type} 换掉就能用。</p>
 *
 * <p><b>为什么不能继承 Create 的 serializer：</b>它的
 * {@code fromJson} / {@code toNetwork} / {@code fromNetwork} 都是 {@code final}，
 * 而它内部构造的是 {@code SequencedAssemblyRecipe} 本体（不是我们的子类）。所以这里自己写一份，
 * 真正解析/序列化字段的代码放在 {@link HandMadeSequencedAssemblyRecipe} 里
 * （只有子类能访问 {@code protected} 的 {@code ingredient / sequence / loops / transitionalItem}）。</p>
 */
public class HandMadeSequencedAssemblyRecipeSerializer
        implements RecipeSerializer<HandMadeSequencedAssemblyRecipe> {

    @Override
    public HandMadeSequencedAssemblyRecipe fromJson(ResourceLocation id, JsonObject json) {
        return HandMadeSequencedAssemblyRecipe.fromJson(id, json, this);
    }

    @Override
    public HandMadeSequencedAssemblyRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buffer) {
        return HandMadeSequencedAssemblyRecipe.fromNetwork(id, buffer, this);
    }

    @Override
    public void toNetwork(FriendlyByteBuf buffer, HandMadeSequencedAssemblyRecipe recipe) {
        recipe.writeFieldsTo(buffer);
    }
}