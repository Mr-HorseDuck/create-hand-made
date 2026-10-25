package com.alben.createhandmade.item;

import com.alben.createhandmade.ServerConfig;
import com.alben.createhandmade.network.PressParticlesPacket;
import com.alben.createhandmade.network.PressParticlesPacket.ParticleStyle;
import com.alben.createhandmade.recipe.BasinRecipeCompat;
import com.alben.createhandmade.recipe.HandMadePressingRecipe;
import com.alben.createhandmade.recipe.HandMadeRecipePool;
import com.alben.createhandmade.recipe.HandMadeTool;
import com.zurrtum.create.AllRecipeTypes;
import com.zurrtum.create.AllSoundEvents;
import com.zurrtum.create.api.behaviour.BlockEntityBehaviour;
import com.zurrtum.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.zurrtum.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.TransportedResult;
import com.zurrtum.create.content.kinetics.belt.transport.TransportedItemStack;
import com.zurrtum.create.content.kinetics.press.PressingRecipe;
import com.zurrtum.create.content.logistics.depot.DepotBlockEntity;
import com.zurrtum.create.content.processing.basin.BasinBlock;
import com.zurrtum.create.content.processing.basin.BasinBlockEntity;
import com.zurrtum.create.content.processing.basin.BasinInput;
import com.zurrtum.create.foundation.recipe.CreateSingleStackRollableRecipe;
import com.zurrtum.create.foundation.recipe.RecipeApplier;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class PressHammerItem extends Item {

    // 蓄力门槛 / 蓄力命中窗口已移到 ServerConfig（tools.press_hammer.*），运行时读 ——
    // 见 releaseUsing() 与电荷窗口判定。不能进静态字段：SERVER 配置只在进入世界后加载。

    public PressHammerItem(Properties properties) {
        super(properties);
    }

    // ================= 拦截右键 =================
    //
    // Fabric 用 UseBlockCallback 取代 NeoForge 的 PlayerInteractEvent.RightClickBlock。
    // 回调只能返回「消费 / 不消费」而不能单独禁止方块交互，因此命中时两端手动
    // player.startUsingItem(hand)，再返回 SUCCESS 把整个交互吃掉。

    public static InteractionResult interceptRightClick(Player player, Level level, InteractionHand hand,
                                                        BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (!(player.getMainHandItem().getItem() instanceof PressHammerItem)) {
            return InteractionResult.PASS;
        }

        BlockPos pos = hit.getBlockPos();
        boolean shouldIntercept = false;

        if (level.getBlockEntity(pos) instanceof DepotBlockEntity depot) {
            TransportedItemStack held = depot.getHeldItem();
            shouldIntercept = held != null && !held.stack.isEmpty();
        } else if (BlockEntityBehaviour.get(level, pos, TransportedItemStackHandlerBehaviour.TYPE) != null) {
            shouldIntercept = true;
        } else if (level.getBlockState(pos).getBlock() instanceof BasinBlock
                && level.getBlockEntity(pos) instanceof BasinBlockEntity basin) {
            shouldIntercept = !basin.isEmpty();
        }

        if (!shouldIntercept) {
            return InteractionResult.PASS;
        }

        player.startUsingItem(hand);
        return InteractionResult.SUCCESS;
    }

    // ================= 蓄力 =================

    /**
     * 蓄力期间的反馈：<b>蓄力完成的那一刻</b>播一声轻提示（本地播，个人反馈，不广播）。
     */
    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingTicks) {
        if (!(entity instanceof Player player)) {
            return;
        }

        int usedTicks = getUseDuration(stack, entity) - remainingTicks;
        int chargeTicks = ServerConfig.INSTANCE.pressHammerChargeTicks.get();
        if (usedTicks == chargeTicks && level.isClientSide()) {
            player.playSound(AllSoundEvents.CONFIRM.getMainEvent(), 1f, 1.0f);
        }
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResult.CONSUME;
    }

    @Override
    public ItemUseAnimation getUseAnimation(ItemStack stack) {
        return ItemUseAnimation.BOW;
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!(entity instanceof Player player)) {
            return false;
        }

        int usedTicks = getUseDuration(stack, entity) - timeLeft;
        // 蓄力门槛读 SERVER 配置（运行时读）
        if (usedTicks < ServerConfig.INSTANCE.pressHammerChargeTicks.get()) {
            return false;
        }
        if (level.isClientSide()) {
            return false;
        }

        player.swing(player.getUsedItemHand(), true);

        // 生物
        double entityReach = player.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
        Vec3 eye = player.getEyePosition(1);
        Vec3 look = player.getViewVector(1);

        EntityHitResult entityHit = findEntityHit(player, eye, look, entityReach);
        if (entityHit != null && entityHit.getEntity() instanceof LivingEntity target) {
            AllSoundEvents.MECHANICAL_PRESS_ACTIVATION.playOnServer(
                    level, target.blockPosition(), 1f, 0.8f);

            player.attack(target);
            // 蓄力暴击 + 额外击退：26.1 无 CriticalHitEvent / LivingKnockBackEvent，
            // 直接在命中分支里补伤害与击退。
            applyChargeBonus(player, target, level);
            return true;
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

            // 蓄力挥击本身消耗耐久，无论是否真的压到东西
            stack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
        } else {
            // 空挥：用原版重击挥空音效，而不是机械压床启动音
            Vec3 soundPos = eye.add(look.scale(1.0));
            level.playSound(null, BlockPos.containing(soundPos),
                    SoundEvents.PLAYER_ATTACK_STRONG,
                    SoundSource.PLAYERS, 0.6f, 0.9f);
            stack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
        }
        return true;
    }

    /** 蓄力命中：额外 0.5 倍伤害（等价原版暴击 1.5×）+ 额外击退。 */
    private static void applyChargeBonus(Player player, LivingEntity target, Level level) {
        if (level instanceof ServerLevel serverLevel) {
            float extra = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.5f;
            if (extra > 0) {
                // 同一 tick 内第二次 hurt 会被无敌帧吃掉，这里临时清零
                int prevInvulnerable = target.invulnerableTime;
                target.invulnerableTime = 0;
                target.hurtServer(serverLevel, player.damageSources().playerAttack(player), extra);
                target.invulnerableTime = prevInvulnerable;
            }
        }

        Vec3 away = target.position().subtract(player.position());
        target.knockback(2.4, away.x, away.z);
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

    // ================= 探测目标物品 =================

    private static List<ItemStack> findTargetItems(Level level, BlockPos pos) {
        List<ItemStack> result = new ArrayList<>();

        if (level.getBlockEntity(pos) instanceof DepotBlockEntity depot) {
            TransportedItemStack held = depot.getHeldItem();
            if (held != null && !held.stack.isEmpty()) {
                result.add(held.stack.copy());
            }
            return result;
        }

        if (level.getBlockEntity(pos) instanceof BasinBlockEntity basin) {
            for (int i = 0; i < basin.itemCapability.getContainerSize(); i++) {
                ItemStack s = basin.itemCapability.getItem(i);
                if (!s.isEmpty()) {
                    result.add(s.copy());
                }
            }
            return result;
        }

        TransportedItemStackHandlerBehaviour handler =
                BlockEntityBehaviour.get(level, pos, TransportedItemStackHandlerBehaviour.TYPE);
        if (handler != null) {
            handler.handleProcessingOnAllItems(item -> {
                if (!item.stack.isEmpty()) {
                    result.add(item.stack.copy());
                }
                return TransportedResult.doNothing();
            });
        }

        return result;
    }

    // ================= 广播粒子 =================

    private static void broadcastParticles(Level level, BlockPos pos, List<ItemStack> stacks) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (stacks.isEmpty()) {
            return;
        }

        PressParticlesPacket packet = new PressParticlesPacket(pos, stacks, ParticleStyle.PRESS);
        double rangeSq = 64.0 * 64.0;

        for (ServerPlayer p : serverLevel.players()) {
            if (p.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5) <= rangeSq) {
                ServerPlayNetworking.send(p, packet);
            }
        }
    }

    // ================= 工作盆 =================

    private static boolean tryPressBasin(Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof BasinBlockEntity basin)) {
            return false;
        }
        if (basin.isEmpty()) {
            return false;
        }

        // 候选集 = COMPACTING ∪ 可压缩工作台配方，由配方池提供并按配方管理器的
        // 全局顺序合并 —— 与改造前 RecipeFinder.get(level, predicate) 的遍历顺序一致。
        //
        // try/catch 刻意保留：收集与匹配都会遍历全量配方并调用配方自身的
        // getIngredients() / matches，任何一条畸形配方抛异常都不应该中断冲压。
        try {
            List<RecipeHolder<?>> merged = HandMadeRecipePool.mergeInGlobalOrder(level,
                    HandMadeRecipePool.getBaseRecipes(HandMadeTool.PRESS_HAMMER_BASIN, level),
                    HandMadeRecipePool.getBaseRecipes(HandMadeTool.PRESS_HAMMER_AUTO_SQUARE, level));
            BasinInput input = new BasinInput(basin);
            for (RecipeHolder<?> holder : merged) {
                if (BasinRecipeCompat.matches(input, holder.value(), level)
                        && BasinRecipeCompat.apply(input, holder.value(), level)) {
                    basin.notifyChangeOfContents();
                    return true;
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
        // 候选集改由统一配方池提供；matches 与 automation 过滤留在本类。
        // Create Fly 在加载期已把序列组装展开成普通 PRESSING 配方，因此这里天然覆盖。
        SingleRecipeInput recipeInput = new SingleRecipeInput(stack);
        for (RecipeHolder<?> holder : HandMadeRecipePool.getBaseRecipes(HandMadeTool.PRESS_HAMMER_DEPOT, level)) {
            // automation 过滤保留在调用方：配方池与 JEI 都不过滤这些“仅手动”配方。
            if (!AllRecipeTypes.CAN_BE_AUTOMATED.test(holder)) {
                continue;
            }

            // L3 独占配方（冲压家族）：只认归属置物台的那些
            if (holder.value() instanceof HandMadePressingRecipe exclusive) {
                if (exclusive.getTool() != HandMadeTool.PRESS_HAMMER_DEPOT) {
                    continue;
                }
                if (!exclusive.matches(recipeInput, level)) {
                    continue;
                }
                return holder;
            }

            if (!(holder.value() instanceof PressingRecipe pressing)) {
                continue;
            }
            if (!pressing.matches(recipeInput, level)) {
                continue;
            }
            return holder;
        }
        return null;
    }

    private static boolean tryPressTransported(Level level, BlockPos pos) {
        TransportedItemStackHandlerBehaviour handler =
                BlockEntityBehaviour.get(level, pos, TransportedItemStackHandlerBehaviour.TYPE);
        if (handler == null) {
            return false;
        }

        boolean[] success = {false};

        handler.handleProcessingOnAllItems(item -> {
            if (success[0]) {
                return TransportedResult.doNothing();
            }
            ItemStack stack = item.stack;
            if (stack.isEmpty()) {
                return TransportedResult.doNothing();
            }

            RecipeHolder<?> recipe = findPressingRecipe(level, stack);
            if (recipe == null) {
                return TransportedResult.doNothing();
            }

            // 应用配方
            List<ItemStack> results;
            SingleRecipeInput input = new SingleRecipeInput(stack.copyWithCount(1));
            if (recipe.value() instanceof CreateSingleStackRollableRecipe rollable) {
                results = RecipeApplier.applyRecipeOn(level.getRandom(), 1, input, rollable);
            } else {
                results = List.of();
            }

            // 配方已匹配 → 消耗 1 个原料（无论产出是否为空）
            success[0] = true;

            ItemStack left = stack.copy();
            left.shrink(1);

            List<TransportedItemStack> outputs = new ArrayList<>();
            for (ItemStack result : results) {
                if (result.isEmpty()) {
                    continue;
                }
                TransportedItemStack copy = item.copy();
                copy.stack = result;
                outputs.add(copy);
            }

            if (outputs.isEmpty()) {
                // 无产出
                if (left.isEmpty()) {
                    return TransportedResult.removeItem();
                }
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
