package com.alben.createhandmade.compat.tlm.behavior;

import com.alben.createhandmade.CreateHandMade;
import com.alben.createhandmade.enchantment.BatchCraftingHelper;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 批量加工附魔的女仆拦截器。
 *
 * 女仆不会用带 Batch Crafting 的工具——检测到就吐槽并拒绝。
 * 冷却 60 秒，避免刷屏。
 */
public final class MaidBatchCraftingBlocker {

    /** 吐槽冷却（tick）。60 秒 = 1200 tick。 */
    private static final long REFUSAL_COOLDOWN_TICKS = 1200;

    /** 玩家提示范围（格）。 */
    private static final double MESSAGE_RANGE_SQR = 32.0 * 32.0;

    /** 超过这个数量就清理过期条目，防止长期运行累积。 */
    private static final int CLEANUP_THRESHOLD = 256;

    /** 上次吐槽时间：maid UUID → gameTime。 */
    private static final Map<UUID, Long> LAST_REFUSAL = new ConcurrentHashMap<>();

    private static final String[] REFUSAL_KEYS = {
            "message.create_hand_made.maid.refuse_batch.0",
            "message.create_hand_made.maid.refuse_batch.1",
            "message.create_hand_made.maid.refuse_batch.2"
    };

    private MaidBatchCraftingBlocker() {}

    /**
     * 检测主手工具是否带批量加工附魔；有则吐槽并返回 true（调用方应 return false）。
     *
     * @param maid 女仆
     * @param tool 女仆主手物品
     * @return true = 应拒绝使用
     */
    public static boolean checkAndRefuse(EntityMaid maid, ItemStack tool) {
        if (tool.isEmpty()) return false;
        if (BatchCraftingHelper.getBatchSize(tool) <= 1) return false;

        // TLM 1.5.3 移除了 EntityMaid 对 Entity 方法的覆写。
        // 直接调 maid.level() / maid.getUUID() 编译能过，运行抛 NoSuchMethodError。
        // 所有 Entity 基类方法必须强转。
        Entity entity = (Entity) maid;

        long now = entity.level().getGameTime();
        UUID id = entity.getUUID();

        Long last = LAST_REFUSAL.get(id);
        if (last != null && now - last < REFUSAL_COOLDOWN_TICKS) {
            return true;   // 冷却中，静默拒绝
        }
        LAST_REFUSAL.put(id, now);
        cleanupIfNeeded(now);

        // 随机选一句
        int idx = entity.level().random.nextInt(REFUSAL_KEYS.length);
        Component line = Component.translatable(REFUSAL_KEYS[idx]);
        Component msg = Component.literal("<" + entity.getName().getString() + "> ")
                .append(line);

        if (entity.level() instanceof ServerLevel sl) {
            for (ServerPlayer p : sl.players()) {
                if (p.distanceToSqr(entity.position()) <= MESSAGE_RANGE_SQR) {
                    p.displayClientMessage(msg, false);
                }
            }
        }

        CreateHandMade.debug("Maid {} refused batch crafting tool", entity.getId());
        return true;
    }

    /**
     * 女仆卸载/死亡时调用，避免静态 Map 无限增长。
     * 没有现成事件的话可以不调，cleanupIfNeeded 会兜底。
     */
    public static void forget(UUID maidId) {
        if (maidId != null) LAST_REFUSAL.remove(maidId);
    }

    private static void cleanupIfNeeded(long now) {
        if (LAST_REFUSAL.size() < CLEANUP_THRESHOLD) return;
        Iterator<Map.Entry<UUID, Long>> it = LAST_REFUSAL.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> entry = it.next();
            if (now - entry.getValue() >= REFUSAL_COOLDOWN_TICKS) {
                it.remove();
            }
        }
    }
}