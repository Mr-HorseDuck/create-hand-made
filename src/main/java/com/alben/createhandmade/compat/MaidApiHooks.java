package com.alben.createhandmade.compat;

import com.alben.createhandmade.CreateHandMade;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * 主 sourceSet ↔ TLM 兼容层之间的能力钩子。
 *
 * <p>为什么需要这一层：{@code api} 包在主 sourceSet，而 TLM 只挂了
 * {@code tlmCompatCompileOnly}，主代码没有资格直接引用 {@code EntityMaid} /
 * {@code MaidFavorabilityHelper}。兼容层在初始化时把一个实现注册进来，
 * 主代码就只依赖这里的接口，编译期不需要 TLM。</p>
 *
 * <p>未装 TLM / tlmCompat 未编译时钩子为空，调用方走裸逻辑。</p>
 */
public final class MaidApiHooks {

    /** 女仆工具耐久扣减。返回 true 表示这次已经由兼容层处理（可能因好感度免掉）。 */
    @FunctionalInterface
    public interface MaidToolDamage {
        boolean damage(ItemStack tool, LivingEntity entity, int amount);
    }

    private static volatile MaidToolDamage toolDamage;

    private MaidApiHooks() {
    }

    /** 由 tlmCompat 在 mod 构造阶段注册（CreateHandMade 里通过反射调用）。 */
    public static void registerToolDamage(MaidToolDamage hook) {
        toolDamage = hook;
    }

    /**
     * 按女仆规则扣工具耐久。
     *
     * @return true 表示兼容层已处理；false 表示调用方应自行扣耐久
     */
    public static boolean damageToolWithMaidRules(ItemStack tool, LivingEntity entity, int amount) {
        MaidToolDamage hook = toolDamage;
        if (hook == null) return false;
        try {
            return hook.damage(tool, entity, amount);
        } catch (Throwable t) {
            CreateHandMade.LOGGER.warn("[HandMade] maid tool damage hook failed, falling back", t);
            return false;
        }
    }
}
