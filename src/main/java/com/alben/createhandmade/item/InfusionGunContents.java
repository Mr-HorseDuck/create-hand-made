package com.alben.createhandmade.item;

import java.util.Objects;

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

    /**
     * 值相等：必须与 {@link #hashCode()} 一致。
     *
     * <p>NeoForge 的 {@link FluidStack} <b>没有覆写 equals/hashCode</b>（21.1.250 实测只有 toString），
     * 所以 record 自动生成的 equals 会退化成引用比较。后果是"内容完全相同的两把灌注枪"组件永不相等，
     * 进而让 {@code ItemStack#isSameItemSameComponents} 恒为 false，破坏 Create 物流/库存的一切精确匹配：
     * 仓储管理员里请求会因 {@code InventorySummary#getCountOf} 返回 0 而静默消失（待打包栏闪一下）。</p>
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!(obj instanceof InfusionGunContents other))
            return false;
        boolean thisEmpty = fluid.isEmpty();
        boolean otherEmpty = other.fluid.isEmpty();
        if (thisEmpty || otherEmpty)
            return thisEmpty && otherEmpty;
        return fluid.getAmount() == other.fluid.getAmount()
                && FluidStack.isSameFluidSameComponents(fluid, other.fluid);
    }

    @Override
    public int hashCode() {
        if (fluid.isEmpty())
            return 0;
        // FluidStack 本身没有 hashCode，用 注册项 + 数量 + 组件补丁 组合；两者都与 equals 的判断依据一致
        return Objects.hash(fluid.getFluid(), fluid.getAmount(), fluid.getComponents());
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