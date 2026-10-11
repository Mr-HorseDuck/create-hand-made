package com.alben.createhandmade.compat.tlm.behavior;

import com.simibubi.create.content.logistics.filter.FilterItem;
import com.simibubi.create.content.logistics.filter.FilterItemStack;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

import javax.annotation.Nullable;

public final class MaidFilterHelper {

    private MaidFilterHelper() {}

    /**
     * 从女仆背包中查找 Create 列表过滤器并封装成 FilterItemStack
     */
    @Nullable
    public static FilterItemStack findFilterStack(EntityMaid maid) {
        IItemHandler inv = getMaidInventory(maid);
        if (inv == null) return null;

        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack slot = inv.getStackInSlot(i);
            if (slot.isEmpty()) continue;

            if (slot.getItem() instanceof FilterItem) {
                return FilterItemStack.of(slot);
            }
        }
        return null;
    }

    @Nullable
    private static IItemHandler getMaidInventory(EntityMaid maid) {
        String[] methodNames = {"getMaidInv", "getMaidInventory", "getInventory"};
        for (String name : methodNames) {
            try {
                Object result = maid.getClass().getMethod(name).invoke(maid);
                if (result instanceof IItemHandler handler) {
                    return handler;
                }
            } catch (Exception ignored) {}
        }
        return null;
    }
}