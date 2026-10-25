package com.alben.createhandmade.item;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;

/**
 * 研钵 / 碾钵「碗里的东西」：原料 + 本次加工时长（不含起手）。
 */
public record MortarContents(ItemStack stack, int durationTicks) {

    private static final Codec<MortarContents> NEW_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ItemStack.CODEC.fieldOf("stack").forGetter(MortarContents::stack),
            Codec.INT.optionalFieldOf("duration_ticks", 0).forGetter(MortarContents::durationTicks)
    ).apply(instance, MortarContents::new));

    public static final Codec<MortarContents> CODEC = Codec.either(NEW_CODEC, ItemStack.CODEC)
            .xmap(either -> either.map(contents -> contents, stack -> new MortarContents(stack, 0)),
                    Either::left);

    public static final StreamCodec<RegistryFriendlyByteBuf, MortarContents> STREAM_CODEC = StreamCodec.composite(
            ItemStack.STREAM_CODEC, MortarContents::stack,
            ByteBufCodecs.VAR_INT, MortarContents::durationTicks,
            MortarContents::new);
}
