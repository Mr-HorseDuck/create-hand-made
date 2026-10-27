package com.alben.createhandmade.item;

import com.alben.createhandmade.ModDataComponents;
import com.alben.createhandmade.ServerConfig;
import com.alben.createhandmade.recipe.HandMadeCrushingRecipe;
import com.alben.createhandmade.recipe.HandMadeRecipePool;
import com.alben.createhandmade.recipe.HandMadeTool;
import com.alben.createhandmade.util.CycleSoundWindow;
import com.alben.createhandmade.util.StickyUseSession;
import com.simibubi.create.content.kinetics.millstone.MillingRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import com.simibubi.create.foundation.item.CustomUseEffectsItem;
import com.simibubi.create.foundation.item.render.SimpleCustomRenderer;
import net.createmod.catnip.data.TriState;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Consumer;

public class MortarItem extends Item implements CustomUseEffectsItem {

    // ================= 时间参数（研钵 / 碾钵各一套，键见 ServerConfig） =================
    // 两支工具互相不继承（CrusherMortarItem 是独立实现），所以把"读配置 / 解析时长"的逻辑
    // 集中放在这里，碾钵直接复用（它本来就在用 MortarItem.slamTick）。

    /** 加工时长上限（tick）：防止"极端系数 × 长配方"把一次加工拖到几分钟。 */
    public static final int MAX_PROCESSING_TICKS = 1200;

    /** 起手时长（tick）。{@code crusherMortar = true} 时读碾钵那一套键。 */
    public static int windupTicks(boolean crusherMortar) {
        return (crusherMortar ? ServerConfig.INSTANCE.crusherMortarWindupTicks
                : ServerConfig.INSTANCE.mortarWindupTicks).get();
    }

    /** 捶捣周期（tick），至少 1（避免除零）。 */
    public static int poundCycleTicks(boolean crusherMortar) {
        return Math.max(1, (crusherMortar ? ServerConfig.INSTANCE.crusherMortarPoundCycleTicks
                : ServerConfig.INSTANCE.mortarPoundCycleTicks).get());
    }

    /**
     * 解析「单物品加工时长」= 配方 {@code processing_time} × 系数，<b>不含起手</b>（tick）。
     *
     * <p>规则（<b>B1</b>）：</p>
     * <ul>
     *   <li>配方不是 {@link ProcessingRecipe}（理论上不会发生）、或没写 {@code processing_time}
     *       （Create 的解析默认值是 <b>0</b>，不是 100）→ 用该工具配置里的 {@code *_default_duration} 兜底；</li>
     *   <li><b>系数作用于最终 base（含兜底值）</b>：配方没写时长时，结果 = {@code default_duration × 系数}；</li>
     *   <li>四舍五入后夹到 {@code [1, MAX_PROCESSING_TICKS]}。</li>
     * </ul>
     *
     * <p><b>只在 {@code use()} 里调用一次</b>，结果存进 {@link MortarContents#durationTicks()}：
     * {@code getUseDuration} 每帧都会被调用（渲染器每帧问 {@code getTicksUsingItem()}），
     * 不能在那里遍历配方池。</p>
     */
    public static int resolveDuration(@Nullable RecipeHolder<?> recipe, boolean crusherMortar) {
        int base = recipe != null && recipe.value() instanceof ProcessingRecipe<?, ?> processing
                ? processing.getProcessingDuration()
                : 0;
        if (base <= 0) {
            base = Math.max(1, (crusherMortar ? ServerConfig.INSTANCE.crusherMortarDefaultDuration
                    : ServerConfig.INSTANCE.mortarDefaultDuration).get());
        }
        double multiplier = (crusherMortar ? ServerConfig.INSTANCE.crusherMortarDurationMultiplier
                : ServerConfig.INSTANCE.mortarDurationMultiplier).get();
        int processing = (int) Math.round(base * multiplier);
        return Math.max(1, Math.min(processing, MAX_PROCESSING_TICKS));
    }

    /**
     * 取这次使用的<b>加工时长</b>（tick，不含起手）。
     *
     * <p>优先用组件里存的（{@code use()} 时按配方算好、<b>已经乘过系数</b>的最终值，不再重复乘）；
     * 若为 0（R8 之前存下的旧数据 —— 旧组件没有 {@code duration_ticks} 字段，反序列化后是 0），
     * <b>复用 {@link #resolveDuration} 的兜底链路</b>（{@code recipe = null} ⇒ 走
     * {@code default_duration × 系数}），避免旧存档里的"已装料工具"瞬间加工完，
     * 同时与"新数据没写配方时长"的情况保持同一套语义。</p>
     */
    public static int processingTicks(MortarContents contents, boolean crusherMortar) {
        int stored = contents.durationTicks();
        if (stored > 0) return Math.min(stored, MAX_PROCESSING_TICKS);
        return resolveDuration(null, crusherMortar);
    }

    /**
     * 捶捣曲线里「砸到底」所在的周期相位（0~1）。
     *
     * <p><b>这是音效与动画唯一的同步锚点</b>：客户端 {@code MortarItemRenderer.poundCurve}
     * 在 {@code phase == SLAM_PHASE} 时杵正好落到最低点，服务端 {@link #onUseTick} 在
     * {@link #slamTick(int)} 这个 tick 播音效/粒子 —— 两边共用同一个常量，改这里两端一起变。</p>
     *
     * <p>捶捣周期从「起手结束」那一刻开始计时（b2.6 起用<b>会话加工 tick</b>
     * {@code = 会话 tick - 起手}；会话首次使用时与旧的 {@code usedTicks - windupTicks} 等价），
     * 所以第一声研磨音正好落在第一次「砸到底」上，且续用时不重播相位。</p>
     */
    public static final float SLAM_PHASE = 0.60f;

    /**
     * 一个捶捣周期里「砸到底」发生在第几个 tick（即 {@code poundTicks % cycle} 要等于的值）。
     * 默认周期 15 tick 时为第 9 tick。周期下限是 2（配置已限制），所以结果恒在 [0, cycle-1]。
     */
    public static int slamTick(int cycleTicks) {
        return Math.max(0, Math.min(cycleTicks - 1, Math.round(SLAM_PHASE * cycleTicks)));
    }

    public MortarItem(Properties properties) {
        super(properties);
    }

    @Override
    public int getEnchantmentValue(ItemStack stack) {
        return 15;
    }

    // ================= 右键：副手取物，开始研磨 =================

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        MortarContents loaded = stack.get(ModDataComponents.MORTAR_CONTENTS.get());
        if (loaded != null) {
            // ★ b2.6：必须在 startUsingItem 之前定下"本次使用的总时长"
            //   （vanilla 的 startUsingItem 会立刻调用 getUseDuration()）：
            //   会话首次 = 起手 + 加工，同一次长按的续用 = 只有加工。
            StickyUseSession.beginUse(player, stack, hand, StickyUseSession.DEFAULT_STICKY_TICKS,
                    windupTicks(false), processingTicks(loaded, false));
            player.startUsingItem(hand);
            return InteractionResultHolder.consume(stack);
        }

        InteractionHand otherHand = (hand == InteractionHand.MAIN_HAND)
                ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack input = player.getItemInHand(otherHand);
        if (input.isEmpty()) {
            return InteractionResultHolder.fail(stack);
        }

        SingleRecipeInput recipeInput = new SingleRecipeInput(input);
        RecipeHolder<?> recipe = findMillingRecipe(level, recipeInput);
        if (recipe == null) {
            return InteractionResultHolder.fail(stack);
        }

        // 加工时长在这里定死（不含起手）：getUseDuration 每帧都被调用，不能在那里查配方。
        int duration = resolveDuration(recipe, false);

        ItemStack toGrind = input.copyWithCount(1);
        if (!level.isClientSide) {
            input.shrink(1);
            player.setItemInHand(otherHand, input);
        }
        stack.set(ModDataComponents.MORTAR_CONTENTS.get(), new MortarContents(toGrind, duration));

        // ★ b2.6：装料这一次也算"会话首次使用"（紧接上一次松手 5t 内的换料视为续磨，不再重播起手）
        StickyUseSession.beginUse(player, stack, hand, StickyUseSession.DEFAULT_STICKY_TICKS,
                windupTicks(false), duration);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    // ================= 使用时长：会话首次 = 起手 + 加工，同一次长按的续用 = 只有加工 =================

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        MortarContents contents = stack.get(ModDataComponents.MORTAR_CONTENTS.get());
        if (contents == null || contents.stack().isEmpty()) return 0;
        int processing = processingTicks(contents, false);
        int firstUse = windupTicks(false) + processing;
        // ★ b2.6：总使用时长由 use() 里的 StickyUseSession.beginUse 定下、在这里读回 ——
        //   会话首次 = 起手 + 加工，同一次长按的续用 = 只有加工（起手一个会话只付一次）。
        //   本方法每帧都会被调用（渲染器问 getTicksUsingItem()），所以一次使用内必须恒定：
        //   只能读回"定值"，不能在这里重新判断要不要起手。
        //   读不到（旧存档 / 极端路径）时退回"首次使用"语义 = 起手 + 加工。
        if (entity instanceof Player player) {
            return StickyUseSession.currentUseDuration(player, stack, firstUse);
        }
        return firstUse;
    }

    // ================= 完成：产出研磨结果 =================

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!(entity instanceof Player player)) return stack;

        MortarContents contents = stack.get(ModDataComponents.MORTAR_CONTENTS.get());
        if (contents == null || contents.stack().isEmpty()) return stack;

        // ★ 客户端提前返回：产出、数据组件清除、耐久消耗全部交给服务端权威执行
        if (level.isClientSide) return stack;

        // ★ b2.7c：完成音"保底"要用的两个量必须在这里先取 ——
        //   getUseDuration 依赖数据组件里的加工时长，而下面会把它清掉。
        //   phase = 本件最后一个 tick 的会话加工相位；duration = 本次使用总时长（b2.6 起是定值）。
        int windup = windupTicks(false);
        int cycle = poundCycleTicks(false);
        int phase = StickyUseSession.processingElapsedTicks(player, stack, entity.getUsedItemHand(),
                StickyUseSession.DEFAULT_STICKY_TICKS, windup);
        int duration = getUseDuration(stack, entity);

        SingleRecipeInput recipeInput = new SingleRecipeInput(contents.stack());
        RecipeHolder<?> recipe = findMillingRecipe(level, recipeInput);
        // 候选集由配方池保证：Create 的 MillingRecipe 与 L3 独占配方（HandMadeCrushingRecipe）
        // 都是 ProcessingRecipe，rollResults 对两者通用，所以这里用共同父类消费。
        if (recipe != null && recipe.value() instanceof ProcessingRecipe<?, ?> processing) {
            List<ItemStack> results = processing.rollResults(level.random);
            for (ItemStack result : results) {
                if (!result.isEmpty()) {
                    player.getInventory().placeItemBackInInventory(result);
                }
            }
        }

        stack.remove(ModDataComponents.MORTAR_CONTENTS.get());
        stack.hurtAndBreak(1, entity, LivingEntity.getSlotForHand(entity.getUsedItemHand()));

        // ★ b2.7c 完成音"保底"（取代 b2.7 的"无条件补"）：
        //   本件窗口 = [phase - (duration - 1), phase]（相位在一次使用内每 tick +1）；
        //   其中最后一个 tick（相位 == phase）已被 onUseTick 让位给这一声 ⇒ 只统计到 phase - 1。
        //   本件一次捶捣音都没真的响过（processing 很短、或相位正好错开 slamTick）才补，
        //   从而"每件至少一声"且不会与周期音同 tick 双响。
        boolean cycleSoundPlayed = phase >= 0 && CycleSoundWindow.hasCycleTick(
                phase - (duration - 1), phase - 1, cycle, slamTick(cycle));
        if (!cycleSoundPlayed) {
            level.playSound(null, player.blockPosition(), SoundEvents.GRINDSTONE_USE,
                    SoundSource.PLAYERS, 1f, 2.0f + (level.random.nextFloat() - 0.5f) * 0.2f);
        }
        return stack;
    }

    // ================= 中途松手：退还物品 =================

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!(entity instanceof Player player)) return;

        // ★ 客户端提前返回：退还、组件清除交给服务端权威执行
        if (level.isClientSide) return;

        MortarContents contents = stack.get(ModDataComponents.MORTAR_CONTENTS.get());
        if (contents == null) return;

        ItemStack input = contents.stack();
        if (!input.isEmpty()) {
            player.getInventory().placeItemBackInInventory(input);
        }
        stack.remove(ModDataComponents.MORTAR_CONTENTS.get());
    }

    // ================= 动画 / 音效屏蔽 =================

    /**
     * 使用姿势交给 {@link UseAnim#NONE}：只保留手臂的基础持握姿态，
     * 不再叠加 EAT/DRINK 那套「往嘴边抬」的位移与旋转。
     *
     * <p>研磨的视觉效果全部由 {@code MortarItemRenderer} 用模型位移实现（两阶段动画），
     * 因此这里必须是 NONE，否则手臂会同时做出啃食动作、与研钵动画打架。</p>
     *
     * <p>副作用（已在 T9 核实、可接受）：{@code UseAnim} 不参与第三人称手臂姿态
     * （{@code PlayerRenderer.getArmPose} 对 EAT/NONE 一律返回 {@code ArmPose.ITEM}），
     * 所以这个改动只影响第一人称。</p>
     */
    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public net.minecraft.sounds.SoundEvent getEatingSound() {
        return SoundEvents.EMPTY;
    }

    // ================= 每 tick：音效 + 粒子 =================

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingTicks) {
        MortarContents contents = stack.get(ModDataComponents.MORTAR_CONTENTS.get());
        if (contents == null || contents.stack().isEmpty()) return;
        ItemStack input = contents.stack();
        if (!(entity instanceof Player player)) return;

        // 起手门槛 / 捶捣周期读 SERVER 配置：服务端与客户端（连接时同步）拿到同一个值，
        // 所以"服务端开始出声/出粒子"的时刻与客户端视觉起手严格同步。
        // 注意：SERVER 配置在世界加载后才存在，本方法只在世界内被调用，安全。
        int windup = windupTicks(false);
        int cycle = poundCycleTicks(false);

        // ★ b2.6：相位改用「会话加工 tick」，不再用"本次使用"的 tick。
        //   会话首次使用时两者完全等价（第一声仍落在第一次「砸到底」上）；
        //   续用时相位继续累加 ⇒ 不再"每磨完一件就静止一个起手时长"。
        //   客户端渲染器（MortarItemRenderer）用同一个相位，所以音画仍严格对齐。
        int poundTicks = StickyUseSession.processingElapsedTicks(player, stack, entity.getUsedItemHand(),
                StickyUseSession.DEFAULT_STICKY_TICKS, windup);
        if (poundTicks < 0) return;   // 还在起手期内（或不在会话里）
        if (poundTicks % cycle != slamTick(cycle)) return;

        // ★ 服务端：音效 + 粒子广播给附近玩家
        if (!level.isClientSide) {
            // ★ b2.7c 让位：本次使用的最后一个 tick（remainingTicks == 1）就是产出 tick ——
            //   finishUsingItem 会在同一 tick 播"完成音"，所以那一 tick 的周期音让位（粒子照旧）。
            if (remainingTicks > 1) {
                level.playSound(null, entity.blockPosition(), SoundEvents.GRINDSTONE_USE,
                        SoundSource.PLAYERS, 1f, 2.0f + (level.random.nextFloat() - 0.5f) * 0.2f);
            }

            if (level instanceof ServerLevel serverLevel) {
                Vec3 eye = entity.getEyePosition(1f);
                Vec3 look = entity.getLookAngle();
                Vec3 spawnPos = eye.add(look.scale(0.5)).add(0, -0.3, 0);

                for (int i = 0; i < 2; i++) {
                    double angle = level.random.nextDouble() * Math.PI * 2;
                    double horizSpeed = 0.05 + level.random.nextDouble() * 0.1;
                    double vx = Math.cos(angle) * horizSpeed;
                    double vz = Math.sin(angle) * horizSpeed;
                    double vy = 0.15 + level.random.nextDouble() * 0.15;

                    serverLevel.sendParticles(
                            new ItemParticleOption(ParticleTypes.ITEM, input),
                            spawnPos.x, spawnPos.y, spawnPos.z,
                            1, vx, vy, vz, 0.0
                    );
                }
            }
        }
    }

    // ================= 配方查找：走统一配方池 =================

    /**
     * 按统一配方池查找研磨配方。
     *
     * <p>重构前这里直接调 {@code AllRecipeTypes.MILLING.find(recipeInput, level)}，
     * 它的实现是 {@code level.getRecipeManager().getRecipeFor(...)}，
     * 语义等于「按配方管理器顺序找到第一条 {@code matches} 的 MILLING 配方」。</p>
     *
     * <p>现在改为遍历 {@link HandMadeRecipePool#getBaseRecipes}
     * （{@link HandMadeTool#MORTAR}）并做同样的 {@code matches} 判定、取第一条，
     * 数据来源与顺序都和改造前一致。匹配判定留在本类，配方池只负责收集候选。</p>
     *
     * @return 第一个匹配的研磨配方；没有则返回 null
     */
    @Nullable
    public static RecipeHolder<?> findMillingRecipe(Level level, SingleRecipeInput recipeInput) {
        // 用 instanceof 模式匹配取出确切的配方类型再调 matches：
        // holder.value() 的静态类型是 Recipe<?>，其 matches 的参数是通配符捕获，
        // 无法直接接受 SingleRecipeInput。instanceof 能拿到确切类型，
        // 因此既不需要 unchecked 强转，也不会在将来类型变化时静默出错。
        for (RecipeHolder<?> holder : HandMadeRecipePool.getBaseRecipes(HandMadeTool.MORTAR, level)) {
            // L3 独占配方（碾磨家族）：只认归属研钵的那些（碾钵写下的不归研钵）
            if (holder.value() instanceof HandMadeCrushingRecipe exclusive) {
                if (exclusive.getTool() != HandMadeTool.MORTAR) continue;
                if (!exclusive.matches(recipeInput, level)) continue;
                return holder;
            }
            if (!(holder.value() instanceof MillingRecipe millingRecipe)) continue;
            if (!millingRecipe.matches(recipeInput, level)) continue;
            return holder;
        }
        return null;
    }

    // ================= 渲染器 =================

    @Override
    @OnlyIn(Dist.CLIENT)
    @SuppressWarnings("removal")
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(SimpleCustomRenderer.create(this, new MortarItemRenderer()));
    }

    // ================= 屏蔽原版进食效果 =================

    @Override
    public TriState shouldTriggerUseEffects(ItemStack stack, LivingEntity entity) {
        return TriState.TRUE;
    }

    @Override
    public boolean triggerUseEffects(ItemStack stack, LivingEntity entity, int count, RandomSource random) {
        return true;
    }
}