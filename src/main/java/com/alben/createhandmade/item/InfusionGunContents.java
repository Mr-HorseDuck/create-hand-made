package com.alben.createhandmade.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * 注液枪里"装了哪 种液体、多少量"的状态。
 *
 * <h3>为什么序列化格式是显式的 (fluid id + amount) 而不是 {@code FluidStack.OPTIONAL_CODEC}</h3>
 * <p>{@code FluidStack.OPTIONAL_CODEC} 在"空 / 非空"和流体组件上做可选包装，往返序列化
 * （存档、网络包、{@code copy()}）时容易产生"看起来一样、组件却不相等"的栈 —— 那会让物流的
 * 严格比较（{@code ItemStack.isSameItemSameComponents}）判断失败，表现为"带液体的枪能显示却打不了包"。</p>
 *
 * <p>这里仿照 Mekanism 的做法（{@code ATTACHED_FLUIDS} 只存类型 + 数量这类原始数据）：
 * 组件值只有 {@code fluid} 与 {@code amount} 两个稳定字段，且<b>空液体时不写组件</b>
 * （见 {@code InfusionGunItem.setContents}）。这样同一把枪在任何路径下都是同一个栈。</p>
 */
public record InfusionGunContents(FluidStack fluid) {

    public static final int CAPACITY = 1000;

    public static final Codec<InfusionGunContents> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceLocation.CODEC
                    .fieldOf("fluid")
                    .forGetter(contents -> BuiltInRegistries.FLUID.getKey(contents.fluid().getFluid())),
            Codec.INT.optionalFieldOf("amount", 0).forGetter(InfusionGunContents::amount)
    ).apply(i, InfusionGunContents::fromIdAndAmount));

    public static final StreamCodec<RegistryFriendlyByteBuf, InfusionGunContents> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.registry(Registries.FLUID), contents -> contents.fluid().getFluid(),
                    ByteBufCodecs.VAR_INT, InfusionGunContents::amount,
                    InfusionGunContents::fromFluidAndAmount);

    private static InfusionGunContents fromIdAndAmount(ResourceLocation id, int amount) {
        Fluid fluid = BuiltInRegistries.FLUID.get(id);
        return fromFluidAndAmount(fluid, amount);
    }

    private static InfusionGunContents fromFluidAndAmount(Fluid fluid, int amount) {
        if (fluid == null || fluid == Fluids.EMPTY || amount <= 0) {
            return new InfusionGunContents(FluidStack.EMPTY);
        }
        return new InfusionGunContents(new FluidStack(fluid, Math.min(amount, CAPACITY)));
    }

    public boolean isEmpty() {
        return fluid.isEmpty();
    }

    public int amount() {
        return fluid.getAmount();
    }

    public int remaining() {
        return CAPACITY - fluid.getAmount();
    }

    /**
     * 累加注入：把 added 加入到现有液体中（同种 / 现有为空时）。
     */
    public InfusionGunContents withFill(FluidStack added) {
        if (added.isEmpty()) return this;

        if (!fluid.isEmpty() && !FluidStack.isSameFluidSameComponents(fluid, added)) {
            return this;
        }

        int current = fluid.isEmpty() ? 0 : fluid.getAmount();
        int space = CAPACITY - current;
        if (space <= 0) return this;

        int toAdd = Math.min(space, added.getAmount());
        if (toAdd <= 0) return this;

        FluidStack base = fluid.isEmpty() ? added : fluid;
        return new InfusionGunContents(base.copyWithAmount(current + toAdd));
    }

    /**
     * 消耗指定量。
     */
    public InfusionGunContents withDrain(int amount) {
        if (amount <= 0 || fluid.isEmpty()) return this;
        int actual = Math.min(fluid.getAmount(), amount);
        int remainingAmount = fluid.getAmount() - actual;
        if (remainingAmount <= 0) return new InfusionGunContents(FluidStack.EMPTY);
        return new InfusionGunContents(fluid.copyWithAmount(remainingAmount));
    }
}
