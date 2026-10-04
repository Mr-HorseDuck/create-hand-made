package com.alben.createhandmade.compat.tlm.behavior;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

/**
 * 女仆工具切换辅助。
 *
 * ★ 通过反射访问女仆背包，避免编译期依赖 TLM 的具体类路径。
 *   若 TLM 版本的方法名不同，功能会静默失效，但不影响编译和其他功能。
 */
public final class MaidToolHelper {

    private MaidToolHelper() {}

    /**
     * 遍历女仆背包，找到指定类型的工具并与主手交换。
     *
     * @return 找到并交换成功返回 true
     */
    public static boolean tryEquipFromInventory(EntityMaid maid, Class<?> toolClass) {
        IItemHandler inv = getMaidInventory(maid);
        if (inv == null) return false;

        // ★ Forge 的写方法只在 IItemHandlerModifiable 上
        if (!(inv instanceof IItemHandlerModifiable modifiable)) return false;

        ItemStack mainHand = maid.getMainHandItem();

        for (int i = 0; i < modifiable.getSlots(); i++) {
            ItemStack slot = modifiable.getStackInSlot(i);
            if (slot.isEmpty()) continue;
            if (!toolClass.isInstance(slot.getItem())) continue;

            // 交换
            ItemStack found = slot.copy();
            modifiable.setStackInSlot(i, mainHand.copy());
            maid.setItemInHand(InteractionHand.MAIN_HAND, found);
            return true;
        }
        return false;
    }

    /**
     * 通过反射获取女仆背包（IItemHandler）。
     */
    private static IItemHandler getMaidInventory(EntityMaid maid) {
        String[] methodNames = {"getMaidInv", "getMaidInventory", "getInventory"};

        for (String name : methodNames) {
            try {
                Object result = maid.getClass().getMethod(name).invoke(maid);
                if (result instanceof IItemHandler handler) {
                    return handler;
                }
            } catch (NoSuchMethodException ignored) {
                // 继续尝试下一个方法名
            } catch (Exception ignored) {
                // 反射调用失败，继续
            }
        }
        return null;
    }
}