package com.alben.createhandmade.item;

import com.mojang.serialization.Codec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

public record InfusionGunContents(FluidStack fluid) {

    public static final int CAPACITY = 1000;

    /** ★ 1.20.1：NBT 存储 key（替代 DataComponentType） */
    private static final String NBT_KEY = "InfusionGunContents";
    private static final String FLUID_KEY = "Fluid";

    // ========================================================
    // ★ Forge 1.20.1 的 FluidStack 只有 CODEC（无 OPTIONAL_CODEC）
    // ========================================================
    public static final Codec<InfusionGunContents> CODEC =
            FluidStack.CODEC.xmap(InfusionGunContents::new, InfusionGunContents::fluid);

    // ========================================================
    // ★ 网络编解码：FriendlyByteBuf
    // ========================================================
    public static InfusionGunContents decode(FriendlyByteBuf buf) {
        return new InfusionGunContents(FluidStack.readFromPacket(buf));
    }

    public void encode(FriendlyByteBuf buf) {
        fluid.writeToPacket(buf);
    }

    // ========================================================
    // ★ NBT 存储
    // ========================================================

    /** 从 ItemStack 的 NBT 中读取 InfusionGunContents，不存在时返回 null */
    public static InfusionGunContents fromStack(ItemStack holder) {
        CompoundTag tag = holder.getTagElement(NBT_KEY);
        if (tag == null || !tag.contains(FLUID_KEY)) return null;

        CompoundTag fluidTag = tag.getCompound(FLUID_KEY);
        FluidStack fluid = FluidStack.loadFluidStackFromNBT(fluidTag);
        if (fluid == null) fluid = FluidStack.EMPTY;

        return new InfusionGunContents(fluid);
    }

    /** 将 InfusionGunContents 写入 ItemStack 的 NBT */
    public void writeToStack(ItemStack holder) {
        if (this.fluid.isEmpty()) {
            clearFromStack(holder);
            return;
        }

        CompoundTag fluidTag = new CompoundTag();
        this.fluid.writeToNBT(fluidTag);

        CompoundTag tag = new CompoundTag();
        tag.put(FLUID_KEY, fluidTag);

        holder.getOrCreateTag().put(NBT_KEY, tag);
    }

    /** 从 ItemStack 的 NBT 中清除 InfusionGunContents */
    public static void clearFromStack(ItemStack holder) {
        holder.removeTagKey(NBT_KEY);
    }

    // ========================================================
    // 业务逻辑
    // ========================================================

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
     * ★ 1.20.1：用 new FluidStack(fluid, amount, tag) 替代 copyWithAmount()
     */
    public InfusionGunContents withFill(FluidStack added) {
        if (added.isEmpty()) return this;

        if (!fluid.isEmpty() && !FluidStack.areFluidStackTagsEqual(fluid, added)) {
            return this;
        }

        int current = fluid.isEmpty() ? 0 : fluid.getAmount();
        int space = CAPACITY - current;
        if (space <= 0) return this;

        int toAdd = Math.min(space, added.getAmount());
        if (toAdd <= 0) return this;

        FluidStack base = fluid.isEmpty() ? added : fluid;
        // ★ 1.20.1：手动构造新 FluidStack
        FluidStack result = new FluidStack(base.getFluid(), current + toAdd, base.getTag());
        return new InfusionGunContents(result);
    }

    /**
     * 消耗指定量。
     * ★ 1.20.1：用 new FluidStack(fluid, amount, tag) 替代 copyWithAmount()
     */
    public InfusionGunContents withDrain(int amount) {
        if (amount <= 0 || fluid.isEmpty()) return this;
        int actual = Math.min(fluid.getAmount(), amount);
        int remainingAmount = fluid.getAmount() - actual;
        if (remainingAmount <= 0) return new InfusionGunContents(FluidStack.EMPTY);
        // ★ 1.20.1：手动构造新 FluidStack
        FluidStack result = new FluidStack(fluid.getFluid(), remainingAmount, fluid.getTag());
        return new InfusionGunContents(result);
    }
}