package com.colin101.maidfactory;

import com.alben.createhandmade.compat.MaidApiHooks;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 好感度加成：冷却缩短 + 耐久有概率不消耗。
 *
 * <p>等级对应（TLM getFavorabilityManager().getLevel()）：
 * <ul>
 *   <li>level 0 → 5% 免耐久，冷却 ×0.925</li>
 *   <li>level 1 → 10% 免耐久，冷却 ×0.85</li>
 *   <li>level 2 / 3 → 15% 免耐久，冷却 ×0.775（level 3 封顶）</li>
 * </ul>
 */
public final class MaidFavorabilityHelper {

    private MaidFavorabilityHelper() {}

    private static float durabilitySkipChance(int level) {
        return switch (level) {
            case 0 -> 0.05f;
            case 1 -> 0.10f;
            case 2, 3 -> 0.15f;
            default -> 0f;
        };
    }

    private static float cooldownReduction(int level) {
        return switch (level) {
            case 0 -> 0.075f;
            case 1 -> 0.15f;
            case 2, 3 -> 0.225f;
            default -> 0f;
        };
    }

    /** 按好感度缩短行为冷却，最短 1 tick。 */
    public static int adjustCooldown(int baseCooldown, EntityMaid maid) {
        if (baseCooldown <= 1) return baseCooldown;
        float reduction = cooldownReduction(maid.getFavorabilityManager().getLevel());
        if (reduction <= 0f) return baseCooldown;
        int adjusted = Math.round(baseCooldown * (1f - reduction));
        return Math.max(1, adjusted);
    }

    /**
     * 读配置里的基础冷却并按好感度缩短。
     *
     * <p>所有女仆行为的冷却赋值统一走这里 —— 以前 {@link #adjustCooldown} 写好了却没人调用，
     * 结果"好感度缩短冷却"这个特性实际上没生效（只有免耐久生效）。</p>
     */
    public static int cooldownFor(ForgeConfigSpec.IntValue base, EntityMaid maid) {
        return adjustCooldown(base.get(), maid);
    }

    /**
     * 给主 sourceSet 的对外 API 注册"女仆规则扣耐久"钩子。
     *
     * <p>由 {@code CreateHandMade} 在 mod 构造阶段反射调用（api 包不能直接引用 EntityMaid），
     * 这样 {@code CreateHandMadeApi.damageTool} 对女仆也会走好感度免耐久。</p>
     */
    public static void registerApiHook() {
        MaidApiHooks.registerToolDamage((tool, entity, amount) -> {
            if (!(entity instanceof EntityMaid maid)) return false;
            damageToolWithLuck(tool, maid, amount);
            return true;
        });
    }

    /** 按好感度概率跳过耐久消耗；未跳过时正常 hurtAndBreak。 */
    public static void damageToolWithLuck(ItemStack tool, EntityMaid maid, int amount) {
        float skip = durabilitySkipChance(maid.getFavorabilityManager().getLevel());
        if (skip > 0f && ((Entity) maid).level().random.nextFloat() < skip) {
            return;
        }
        tool.hurtAndBreak(amount, maid, e -> {});
    }
}