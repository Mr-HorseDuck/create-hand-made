package com.alben.createhandmade.api;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 削工具耐久的统一实现：{@link CreateHandMadeApi#damageTool} 与模组内部工具共用一套。
 *
 * <p>为什么单独抽出来：以前 {@code damageTool} 直接转发 {@code InfusionGunItem.damageGun}，
 * 于是「扣耐久」这件事在 API 上只对灌注枪成立 —— 接入方拿自己的物品调用时行为不明，
 * 而灌注枪自己的 6 处调用点又绑在物品类上。现在两边都走这里，语义只有一份。</p>
 *
 * <p><b>只在服务端调用</b>：{@code hurtAndBreak} 会改物品栈并可能触发损坏广播。</p>
 *
 * @since 0.7.1
 */
public final class ToolDamage {

    private ToolDamage() {
    }

    /**
     * 扣 1 点耐久（耐久耗尽时按原版规则损坏）。
     *
     * <p>玩家场景用「当前使用的手」作为损坏广播的槽位，这样第三人称 / 旁观者看到的
     * 手部动作与原版一致；其它生物（含女仆）走空回调 —— 女仆的免耐久判定由
     * {@link CreateHandMadeApi#damageTool} 在更外层先做掉。</p>
     */
    public static void breakTool(ItemStack stack, LivingEntity entity) {
        if (stack == null || stack.isEmpty() || entity == null) return;
        if (entity instanceof Player player) {
            EquipmentSlot slot = player.getUsedItemHand() == InteractionHand.MAIN_HAND
                    ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
            stack.hurtAndBreak(1, player, e -> e.broadcastBreakEvent(slot));
        } else {
            stack.hurtAndBreak(1, entity, e -> {
            });
        }
    }
}
