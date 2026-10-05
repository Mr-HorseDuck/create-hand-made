package com.alben.createhandmade.item;

import com.alben.createhandmade.network.PressParticlesPacket;
import com.alben.createhandmade.network.PressParticlesPacket.ParticleStyle;
import com.alben.createhandmade.network.StirringStatePacket;
import com.alben.createhandmade.recipe.HandMadeRecipePool;
import com.alben.createhandmade.recipe.HandMadeTool;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.fluids.potion.PotionMixingRecipes;
import com.simibubi.create.content.kinetics.mixer.MixingRecipe;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.basin.BasinRecipe;
import com.simibubi.create.foundation.item.CustomUseEffectsItem;
import com.simibubi.create.foundation.item.render.SimpleCustomRenderer;
import com.simibubi.create.infrastructure.config.AllConfigs;
import net.createmod.catnip.data.TriState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import com.alben.createhandmade.client.ClientStirringState;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

@EventBusSubscriber
public class StirringStaffItem extends Item implements CustomUseEffectsItem {

    private static final int STIR_DURATION = 60;   // 3 秒
    private static final int WINDUP_TICKS = 8;   // 起手 1 秒不处理

    public StirringStaffItem(Properties properties) {
        super(properties);
    }

    @Override
    public int getEnchantmentValue(ItemStack stack) {
        return 15;
    }

    // ================= 攻击消耗耐久 =================

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;

        ItemStack mainHand = player.getMainHandItem();
        if (mainHand.getItem() instanceof StirringStaffItem) {
            mainHand.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
            return;
        }
        ItemStack offHand = player.getOffhandItem();
        if (offHand.getItem() instanceof StirringStaffItem) {
            offHand.hurtAndBreak(1, player, EquipmentSlot.OFFHAND);
        }
    }

    // ================= 右键拦截：把"工作盆的空手交互"让给搅拌杖 =================

    /**
     * 原版 {@code Minecraft#startUseItem} 在准星命中方块时，只要**当前手**的 useItemOn
     * 返回 SUCCESS/CONSUME（或 FAIL）就直接 return，副手根本不会被尝试。
     * 而 Create 的工作盆在**空手**时 useItemOn 无条件返回 SUCCESS（把盆里的东西掏回背包），
     * 于是"主手空着 + 搅拌杖在副手"永远走不到搅拌杖的 use()，只有潜行（跳过方块交互）才用得出。
     *
     * <p>这里按 {@link PlayerInteractEvent.RightClickBlock} 的既有做法（BellowsItem /
     * InfusionGunItem / PressHammerItem 同款）显式关掉方块交互，让 useItemOn 返回 PASS，
     * 手部循环才能轮到副手的 useItem。判定必须用 {@code event.getHand()}（正在使用的那只手），
     * 不能用 {@code getMainHandItem()}，否则副手场景不生效。</p>
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (!(player.getItemInHand(event.getHand()).getItem() instanceof StirringStaffItem)) return;

        if (!(event.getLevel().getBlockEntity(event.getPos()) instanceof BasinBlockEntity basin)) return;
        if (basin.isEmpty()) return;

        // TriState 全限定：本类已 import net.createmod.catnip.data.TriState（CustomUseEffectsItem 用）
        event.setUseBlock(net.neoforged.neoforge.common.util.TriState.FALSE);
    }

    // ================= 右键 =================

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE);
        if (hit.getType() != HitResult.Type.BLOCK) {
            return InteractionResultHolder.fail(stack);
        }

        BlockPos pos = hit.getBlockPos();
        if (!(level.getBlockEntity(pos) instanceof BasinBlockEntity basin)) {
            return InteractionResultHolder.fail(stack);
        }
        if (basin.isEmpty()) {
            return InteractionResultHolder.fail(stack);
        }

        // 客户端 + 服务端都做配方检测
        if (findMatchingRecipe(level, basin) == null) {
            return InteractionResultHolder.fail(stack);
        }

        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    // ================= 使用时长 =================

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return STIR_DURATION;
    }

    // ================= 完成 =================

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!(entity instanceof Player player)) return stack;
        if (level.isClientSide) return stack;

        BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE);
        if (hit.getType() != HitResult.Type.BLOCK) return stack;

        BlockPos pos = hit.getBlockPos();
        if (!(level.getBlockEntity(pos) instanceof BasinBlockEntity basin)) return stack;

        // 再检测一次（防中途玩家挪动盆内容）
        RecipeHolder<?> recipe = findMatchingRecipe(level, basin);
        if (recipe != null) {
            if (BasinRecipe.apply(basin, recipe.value())) {
                basin.notifyChangeOfContents();
                stack.hurtAndBreak(1, entity, LivingEntity.getSlotForHand(entity.getUsedItemHand()));
            }
        }
        return stack;
    }

    // ================= 每 tick：音效 + 粒子 =================

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingTicks) {
        if (!(entity instanceof Player player)) return;

        int usedTicks = getUseDuration(stack, entity) - remainingTicks;
        if (usedTicks < WINDUP_TICKS) return;

        BlockHitResult hit = getPlayerPOVHitResult(level, player, ClipContext.Fluid.NONE);
        if (hit.getType() != HitResult.Type.BLOCK) return;

        BlockPos pos = hit.getBlockPos();
        if (!(level.getBlockEntity(pos) instanceof BasinBlockEntity basin)) return;
        if (basin.isEmpty()) return;

        // ★ 服务端广播：音效 + 搅拌状态 + 粒子，全部同步给附近玩家
        if (!level.isClientSide && usedTicks % 4 == 0) {
            if (level instanceof ServerLevel serverLevel) {
                StirringStatePacket packet = new StirringStatePacket(pos);
                double rangeSq = 64.0 * 64.0;
                for (ServerPlayer p : serverLevel.players()) {
                    if (p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= rangeSq) {
                        PacketDistributor.sendToPlayer(p, packet);
                    }
                }

                AllSoundEvents.MIXING.playOnServer(level, pos, 3.0f, 2f);
                broadcastStirParticles(level, pos, basin);
            }
        }
    }

    private static void broadcastStirParticles(Level level, BlockPos pos, BasinBlockEntity basin) {
        // 收集盆内所有非空物品
        List<ItemStack> stacks = new ArrayList<>();
        for (int i = 0; i < basin.inputInventory.getSlots(); i++) {
            ItemStack s = basin.inputInventory.getItem(i);
            if (!s.isEmpty()) stacks.add(s.copy());
        }
        if (stacks.isEmpty()) return;
        if (!(level instanceof ServerLevel serverLevel)) return;

        PressParticlesPacket packet = new PressParticlesPacket(
                pos, stacks, ParticleStyle.STIR);
        double rangeSq = 64.0 * 64.0;

        for (ServerPlayer p : serverLevel.players()) {
            if (p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= rangeSq) {
                PacketDistributor.sendToPlayer(p, packet);
            }
        }
    }

    // ================= 配方查找：MIXING + 无序合成 + 自动酿造 =================

    @Nullable
    public static RecipeHolder<?> findMatchingRecipe(Level level, BasinBlockEntity basin) {
        if (basin.isEmpty()) return null;

        // 1. 普通配方：MIXING 类型 + 工作台无序合成
        //    两类候选都来自统一配方池，并按配方管理器的全局顺序合并 ——
        //    与改造前 RecipeFinder.get(level, predicate) 的遍历顺序一致，
        //    因此"两类同时匹配同一盆内容时谁先被选中"也和改造前一致。
        //    BasinRecipe.match 的第二个参数就是 Recipe<?>，可以直接传 holder.value()。
        //
        //    ★ try/catch 刻意保留（与改造前一致）：收集与匹配都会遍历全量配方
        //      并调用配方自身的 getIngredients() / matches，任何一条畸形配方抛异常
        //      都不应该让整个搅拌流程中断。
        try {
            List<RecipeHolder<?>> merged = HandMadeRecipePool.mergeInGlobalOrder(level,
                    HandMadeRecipePool.getBaseRecipes(HandMadeTool.STIRRING_STAFF, level),
                    HandMadeRecipePool.getBaseRecipes(HandMadeTool.STIRRING_STAFF_AUTO_SHAPELESS, level));
            for (RecipeHolder<?> holder : merged) {
                if (BasinRecipe.match(basin, holder.value())) {
                    return holder;
                }
            }
        } catch (Exception ignored) {
            // 防御畸形配方导致整个搅拌中断
        }

        // 2. 自动酿造
        //    ★ 这里刻意不走配方池：PotionMixingRecipes.sortRecipesByItem(level) 是按物品
        //      建好的索引，能用 get(Item) 直接命中，而 getBaseRecipes(STIRRING_STAFF_AUTO_BREWING)
        //      是全量列表，逐条匹配会显著变慢。配方池的那个 case 只服务于 JEI 展示。
        if (AllConfigs.server().recipes.allowBrewingInMixer.get()) {
            for (int i = 0; i < basin.inputInventory.getSlots(); i++) {
                ItemStack s = basin.inputInventory.getItem(i);
                if (s.isEmpty()) continue;

                List<MixingRecipe> list = PotionMixingRecipes.sortRecipesByItem(level).get(s.getItem());
                if (list == null) continue;

                for (MixingRecipe mixingRecipe : list) {
                    if (BasinRecipe.match(basin, mixingRecipe)) {
                        return new RecipeHolder<>(
                                ResourceLocation.fromNamespaceAndPath("create", "potion_mixing"),
                                mixingRecipe);
                    }
                }
            }
        }

        return null;
    }

    // ================= 动画 / 音效屏蔽 =================

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.EAT;
    }

    @Override
    public SoundEvent getEatingSound() {
        return SoundEvents.EMPTY;
    }

    @Override
    public TriState shouldTriggerUseEffects(ItemStack stack, LivingEntity entity) {
        return TriState.TRUE;
    }

    @Override
    public boolean triggerUseEffects(ItemStack stack, LivingEntity entity, int count, RandomSource random) {
        // true = 屏蔽原版进食粒子
        return true;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    @SuppressWarnings("removal")
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(SimpleCustomRenderer.create(this, new StirringStaffRenderer()));
    }
}