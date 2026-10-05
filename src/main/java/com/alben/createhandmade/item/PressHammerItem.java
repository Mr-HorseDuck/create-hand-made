package com.alben.createhandmade.item;

import com.alben.createhandmade.network.PressParticlesPacket;
import com.alben.createhandmade.network.PressParticlesPacket.ParticleStyle;
import com.alben.createhandmade.recipe.HandMadePressingRecipe;
import com.alben.createhandmade.recipe.HandMadeRecipePool;
import com.alben.createhandmade.recipe.HandMadeTool;
import com.simibubi.create.AllRecipeTypes;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.TransportedResult;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.kinetics.press.PressingRecipe;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import com.simibubi.create.content.processing.basin.BasinBlock;
import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.basin.BasinRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.recipe.RecipeApplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.living.LivingKnockBackEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@EventBusSubscriber
public class PressHammerItem extends Item {

    private static final int CHARGE_TICKS = 15;
    private static final int CHARGE_WINDOW_TICKS = 20;

    private static final Map<UUID, Long> CHARGE_SWING = new HashMap<>();
    private static final Map<UUID, Long> CHARGE_TARGET = new HashMap<>();
    private static final Map<UUID, Long> CHARGED_ATTACK = new HashMap<>();

    @Override
    public int getEnchantmentValue(ItemStack stack) {
        return 15;
    }

    public PressHammerItem(Properties properties) {
        super(properties);
    }

    // ================= 拦截右键 =================

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (!(player.getMainHandItem().getItem() instanceof PressHammerItem)) return;

        Level level = event.getLevel();
        BlockPos pos = event.getPos();

        boolean shouldIntercept = false;

        if (level.getBlockEntity(pos) instanceof DepotBlockEntity depot) {
            shouldIntercept = !depot.getHeldItem().isEmpty();
        } else if (BlockEntityBehaviour.get(level, pos, TransportedItemStackHandlerBehaviour.TYPE) != null) {
            shouldIntercept = true;
        } else if (level.getBlockState(pos).getBlock() instanceof BasinBlock
                && level.getBlockEntity(pos) instanceof BasinBlockEntity basin) {
            shouldIntercept = !basin.isEmpty();
        }

        if (shouldIntercept) {
            event.setUseBlock(TriState.FALSE);
        }
    }

    // ================= 蓄力 =================

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!(entity instanceof Player player)) return;

        int usedTicks = getUseDuration(stack, entity) - timeLeft;
        if (usedTicks < CHARGE_TICKS) return;
        if (level.isClientSide) return;

        long gameTime = level.getGameTime();
        CHARGE_SWING.put(player.getUUID(), gameTime);
        player.swing(player.getUsedItemHand(), true);

        // 生物
        double entityReach = player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
        Vec3 eye = player.getEyePosition(1);
        Vec3 look = player.getViewVector(1);

        EntityHitResult entityHit = findEntityHit(player, eye, look, entityReach);
        if (entityHit != null && entityHit.getEntity() instanceof LivingEntity target) {
            CHARGE_TARGET.put(target.getUUID(), gameTime);
            AllSoundEvents.MECHANICAL_PRESS_ACTIVATION.playOnServer(
                    level, target.blockPosition(), 1f, 0.8f);
            CHARGED_ATTACK.put(player.getUUID(), gameTime);
            player.attack(target);
            stack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
            return;
        }

        // 方块
        double blockReach = player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);
        Vec3 end = eye.add(look.scale(blockReach));
        BlockHitResult hit = level.clip(new ClipContext(eye, end,
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));

        if (hit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = hit.getBlockPos();
            List<ItemStack> particleItems = findTargetItems(level, pos);

            // 先压工作盆，未命中再压置物台/传送带（保持原来的短路顺序）
            if (!tryPressBasin(level, pos)) {
                tryPressTransported(level, pos);
            }

            if (!particleItems.isEmpty()) {
                broadcastParticles(level, pos, particleItems);
            }
            AllSoundEvents.MECHANICAL_PRESS_ACTIVATION.playOnServer(level, pos, 1f, 0.8f);

            // ★ 修复：蓄力挥击本身消耗耐久，无论是否真的压到东西。
            //   此前只有 success（真压到物品）才扣，导致对着地面/普通方块蓄力敲不扣耐久。
            stack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
        } else {
            // ★ 空挥：用原版重击挥空音效，而不是机械压床启动音
            Vec3 soundPos = eye.add(look.scale(1.0));
            level.playSound(null, BlockPos.containing(soundPos),
                    SoundEvents.PLAYER_ATTACK_STRONG,
                    SoundSource.PLAYERS, 0.6f, 0.9f);
            stack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
        }
    }

    @Nullable
    private static EntityHitResult findEntityHit(Player player, Vec3 eye, Vec3 look, double reach) {
        Vec3 end = eye.add(look.scale(reach));
        AABB box = player.getBoundingBox().expandTowards(end.subtract(eye)).inflate(1.0);
        return ProjectileUtil.getEntityHitResult(
                player, eye, end, box,
                e -> !e.isSpectator() && e.isPickable() && e instanceof LivingEntity,
                reach * reach);
    }

    // ================= 普通攻击扣耐久 =================

    @SubscribeEvent
    public static void onAttackEntity(AttackEntityEvent event) {
        Player player = event.getEntity();
        if (player.level().isClientSide) return;
        if (!(player.getMainHandItem().getItem() instanceof PressHammerItem)) return;

        Long t = CHARGED_ATTACK.remove(player.getUUID());
        if (t != null && player.level().getGameTime() - t <= 2) return;

        player.getMainHandItem().hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
    }

    @SubscribeEvent
    public static void onBreakBlock(BlockEvent.BreakEvent event) {
        // 保留原逻辑，TOOL 组件自动扣耐久
    }

    // ================= 蓄力暴击 =================

    @SubscribeEvent
    public static void onCriticalHit(CriticalHitEvent event) {
        Player player = event.getEntity();
        if (!(player.getMainHandItem().getItem() instanceof PressHammerItem)) return;

        Long swingTime = CHARGE_SWING.get(player.getUUID());
        if (swingTime == null) return;

        long elapsed = player.level().getGameTime() - swingTime;
        if (elapsed > CHARGE_WINDOW_TICKS) {
            CHARGE_SWING.remove(player.getUUID());
            return;
        }

        event.setCriticalHit(true);
        event.setDamageMultiplier(1.5F);
    }

    // ================= 蓄力额外击退 =================

    @SubscribeEvent
    public static void onKnockback(LivingKnockBackEvent event) {
        LivingEntity target = event.getEntity();
        Long targetTime = CHARGE_TARGET.get(target.getUUID());
        if (targetTime == null) return;

        long elapsed = target.level().getGameTime() - targetTime;
        if (elapsed > CHARGE_WINDOW_TICKS) {
            CHARGE_TARGET.remove(target.getUUID());
            return;
        }

        event.setStrength(event.getStrength() + 2.0F);
        CHARGE_TARGET.remove(target.getUUID());
    }

    // ================= 探测目标物品 =================

    private static List<ItemStack> findTargetItems(Level level, BlockPos pos) {
        List<ItemStack> result = new ArrayList<>();

        if (level.getBlockEntity(pos) instanceof DepotBlockEntity depot) {
            ItemStack s = depot.getHeldItem();
            if (!s.isEmpty()) result.add(s.copy());
            return result;
        }

        if (level.getBlockEntity(pos) instanceof BasinBlockEntity basin) {
            for (int i = 0; i < basin.inputInventory.getSlots(); i++) {
                ItemStack s = basin.inputInventory.getItem(i);
                if (!s.isEmpty()) result.add(s.copy());
            }
            return result;
        }

        TransportedItemStackHandlerBehaviour handler =
                BlockEntityBehaviour.get(level, pos, TransportedItemStackHandlerBehaviour.TYPE);
        if (handler != null) {
            handler.handleProcessingOnAllItems(item -> {
                if (!item.stack.isEmpty()) result.add(item.stack.copy());
                return TransportedResult.doNothing();
            });
        }

        return result;
    }

    // ================= 广播粒子 =================

    private static void broadcastParticles(Level level, BlockPos pos, List<ItemStack> stacks) {
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (stacks.isEmpty()) return;

        PressParticlesPacket packet = new PressParticlesPacket(pos, stacks, ParticleStyle.PRESS);
        double rangeSq = 64.0 * 64.0;

        for (ServerPlayer p : serverLevel.players()) {
            if (p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= rangeSq) {
                PacketDistributor.sendToPlayer(p, packet);
            }
        }
    }

    // ================= 工作盆 =================

    private static boolean tryPressBasin(Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof BasinBlockEntity basin)) return false;
        if (basin.isEmpty()) return false;

        // 候选集 = COMPACTING ∪ 可压缩工作台配方，由配方池提供并按配方管理器的
        // 全局顺序合并 —— 与改造前 RecipeFinder.get(level, predicate) 的遍历顺序一致，
        // 因此"两类同时匹配同一盆内容时谁先被选中"也和改造前一致。
        //
        // ★ try/catch 刻意保留（与改造前一致）：收集与匹配都会遍历全量配方
        //   并调用配方自身的 getIngredients() / matches，任何一条畸形配方抛异常
        //   都不应该中断冲压。
        try {
            List<RecipeHolder<?>> merged = HandMadeRecipePool.mergeInGlobalOrder(level,
                    HandMadeRecipePool.getBaseRecipes(HandMadeTool.PRESS_HAMMER_BASIN, level),
                    HandMadeRecipePool.getBaseRecipes(HandMadeTool.PRESS_HAMMER_AUTO_SQUARE, level));
            for (RecipeHolder<?> holder : merged) {
                if (BasinRecipe.match(basin, holder.value())) {
                    if (BasinRecipe.apply(basin, holder.value())) {
                        basin.notifyChangeOfContents();
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {
            // 防御畸形配方导致整个冲压中断
        }
        return false;
    }

    // ================= 置物台 / 传送带 =================

    @Nullable
    private static RecipeHolder<?> findPressingRecipe(Level level, ItemStack stack) {
        // ★ 序列组装不走配方池：它需要按「输入 + 中间物品」的组装进度解析，
        //   不是一条普通的 PRESSING 配方，保持原样。
        Optional<RecipeHolder<PressingRecipe>> sequenced =
                SequencedAssemblyRecipe.getRecipe(level, stack,
                        AllRecipeTypes.PRESSING.getType(), PressingRecipe.class);
        if (sequenced.isPresent()) return sequenced.get();

        // 候选集改由统一配方池提供；matches 与 automation 过滤留在本类。
        // 用 instanceof 取出确切的 PressingRecipe 再调 matches：holder.value() 的
        // 静态类型是 Recipe<?>，其 matches 参数是通配符捕获，无法直接接受 SingleRecipeInput。
        SingleRecipeInput recipeInput = new SingleRecipeInput(stack);
        for (RecipeHolder<?> holder : HandMadeRecipePool.getBaseRecipes(HandMadeTool.PRESS_HAMMER_DEPOT, level)) {
            // ★ automation 过滤保留在调用方：配方池与 JEI 都不过滤这些"仅手动"配方。
            //   L3 独占配方与 Create 配方走同一套规则，所以过滤对两者都生效。
            if (!AllRecipeTypes.CAN_BE_AUTOMATED.test(holder)) continue;

            // L3 独占配方（冲压家族）：只认归属置物台的那些
            if (holder.value() instanceof HandMadePressingRecipe exclusive) {
                if (exclusive.getTool() != HandMadeTool.PRESS_HAMMER_DEPOT) continue;
                if (!exclusive.matches(recipeInput, level)) continue;
                return holder;
            }

            if (!(holder.value() instanceof PressingRecipe pressing)) continue;
            if (!pressing.matches(recipeInput, level)) continue;
            return holder;
        }
        return null;
    }

    private static boolean tryPressTransported(Level level, BlockPos pos) {
        TransportedItemStackHandlerBehaviour handler =
                BlockEntityBehaviour.get(level, pos, TransportedItemStackHandlerBehaviour.TYPE);
        if (handler == null) return false;

        boolean[] success = {false};

        handler.handleProcessingOnAllItems(item -> {
            if (success[0]) return TransportedResult.doNothing();
            ItemStack stack = item.stack;
            if (stack.isEmpty()) return TransportedResult.doNothing();

            RecipeHolder<?> recipe = findPressingRecipe(level, stack);
            if (recipe == null) return TransportedResult.doNothing();

            // 应用配方
            List<ItemStack> results = RecipeApplier.applyRecipeOn(
                    level, stack.copyWithCount(1), recipe.value(), true);

            // ★ 配方已匹配 → 消耗 1 个原料（无论产出是否为空）
            success[0] = true;

            ItemStack left = stack.copy();
            left.shrink(1);

            List<TransportedItemStack> outputs = new ArrayList<>();
            for (ItemStack result : results) {
                if (result.isEmpty()) continue;
                TransportedItemStack copy = item.copy();
                copy.stack = result;
                outputs.add(copy);
            }

            if (outputs.isEmpty()) {
                // 无产出
                if (left.isEmpty()) return TransportedResult.removeItem();
                TransportedItemStack leftStack = item.copy();
                leftStack.stack = left;
                return TransportedResult.convertTo(leftStack);
            }

            if (left.isEmpty()) {
                return TransportedResult.convertTo(outputs);
            } else {
                TransportedItemStack leftStack = item.copy();
                leftStack.stack = left;
                return TransportedResult.convertToAndLeaveHeld(outputs, leftStack);
            }
        });

        return success[0];
    }
}