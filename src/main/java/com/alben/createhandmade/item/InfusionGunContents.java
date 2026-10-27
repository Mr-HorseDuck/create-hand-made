package com.alben.createhandmade.item;

import com.mojang.serialization.Codec;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.fluids.FluidStack;

public record InfusionGunContents(FluidStack fluid) {

    public static final int CAPACITY = 1000;

    public static final Codec<InfusionGunContents> CODEC =
            FluidStack.OPTIONAL_CODEC.xmap(InfusionGunContents::new, InfusionGunContents::fluid);

    public static final StreamCodec<RegistryFriendlyByteBuf, InfusionGunContents> STREAM_CODEC =
            FluidStack.OPTIONAL_STREAM_CODEC.map(InfusionGunContents::new, InfusionGunContents::fluid);

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