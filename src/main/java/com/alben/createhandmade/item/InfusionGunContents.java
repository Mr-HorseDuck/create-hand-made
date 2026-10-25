package com.alben.createhandmade.item;

import com.mojang.serialization.Codec;
import com.zurrtum.create.infrastructure.fluids.FluidStack;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public record InfusionGunContents(FluidStack fluid) {

    /**
     * Create Fly 的 {@code FluidStack} 走 Fabric Transfer API 的单位体系：
     * <b>1 mB = 81 droplets，1 桶 = 81000 droplets</b>（与 NeoForge 版 Create 的
     * “1 桶 = 1000 mB”不同）。本模组的配置与提示仍按 mB 表达，所有跨边界的
     * 数量都必须在这里换算，否则会出现「枪显示装满 1000，盆里只少了 1%」这种
     * 单位错位。
     */
    public static final int DROPLETS_PER_MB = 81;

    /** 枪内容量（mB）—— 与原 NeoForge 版一致：1 桶。 */
    public static final int CAPACITY_MB = 1000;

    /** 枪内容量（droplets）—— 内部实际比较用的量。 */
    public static final int CAPACITY = CAPACITY_MB * DROPLETS_PER_MB;

    public static final Codec<InfusionGunContents> CODEC =
            FluidStack.OPTIONAL_CODEC.xmap(InfusionGunContents::new, InfusionGunContents::fluid);

    public static final StreamCodec<RegistryFriendlyByteBuf, InfusionGunContents> STREAM_CODEC =
            FluidStack.OPTIONAL_PACKET_CODEC.map(InfusionGunContents::new, InfusionGunContents::fluid);

    public boolean isEmpty() {
        return fluid.isEmpty();
    }

    public int amount() {
        return fluid.getAmount();
    }

    public int remaining() {
        return CAPACITY - fluid.getAmount();
    }

    public InfusionGunContents withFill(FluidStack added) {
        if (added.isEmpty()) return this;

        if (!fluid.isEmpty() && !FluidStack.areFluidsAndComponentsEqual(fluid, added)) {
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

    public InfusionGunContents withDrain(int amount) {
        if (amount <= 0 || fluid.isEmpty()) return this;
        int actual = Math.min(fluid.getAmount(), amount);
        int remainingAmount = fluid.getAmount() - actual;
        if (remainingAmount <= 0) return new InfusionGunContents(FluidStack.EMPTY);
        return new InfusionGunContents(fluid.copyWithAmount(remainingAmount));
    }
}
