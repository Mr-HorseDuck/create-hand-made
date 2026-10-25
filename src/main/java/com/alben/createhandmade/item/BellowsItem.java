package com.alben.createhandmade.item;

import com.alben.createhandmade.ServerConfig;
import com.alben.createhandmade.bellows.BellowsMediaRegistry;
import com.alben.createhandmade.network.BellowsBlastPacket;
import com.alben.createhandmade.recipe.FanType;
import com.alben.createhandmade.recipe.HandMadeBellowsRecipe;
import com.alben.createhandmade.recipe.HandMadeRecipeTypes;
import com.zurrtum.create.AllDamageSources;
import com.zurrtum.create.AllSoundEvents;
import com.zurrtum.create.api.registry.CreateRegistries;
import com.zurrtum.create.api.behaviour.BlockEntityBehaviour;
import com.zurrtum.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.zurrtum.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.TransportedResult;
import com.zurrtum.create.content.kinetics.belt.transport.TransportedItemStack;
import com.zurrtum.create.content.kinetics.fan.processing.AllFanProcessingTypes;
import com.zurrtum.create.content.kinetics.fan.processing.FanProcessingType;
import com.zurrtum.create.content.logistics.depot.DepotBlockEntity;
import com.zurrtum.create.foundation.recipe.CreateSingleStackRollableRecipe;
import com.zurrtum.create.foundation.recipe.RecipeApplier;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class BellowsItem extends Item {

    // 蓄力门槛已移到 ServerConfig（tools.bellows.charge_ticks），运行时读 —— 见 releaseUsing()。

    /** 熔炼：着火时长（比烟熏长，参考鼓风 10 秒略缩短） */
    private static final int BLASTING_FIRE_TICKS = 120; // 6 秒
    /** 烟熏：着火时长（参考鼓风 2 秒） */
    private static final int SMOKING_FIRE_TICKS = 40;   // 2 秒
    /** 缠魂：失明 + 缓慢时长 */
    private static final int HAUNTING_EFFECT_TICKS = 60; // 3 秒

    /** 推力基础强度（会按距离衰减） */
    private static final double PUSH_BASE = 5;

    /**
     * 射线相关的最大有效距离（格）。
     * 仅在计算“射线终点”和“射线实体检测”时使用；加工判定与爆发粒子不受此限制。
     */
    private static final long BELLOWS_RAY_MAX_DISTANCE_SQR = 64L * 64L;

    /**
     * 风箱喷嘴相对玩家胸口的局部坐标（右手臂视角）：
     *   X = 屏幕右为正 / Y = 上为正 / Z = 前为正
     */
    private static final Vec3 BELLOWS_MUZZLE_OFFSET = new Vec3(0.55, -0.25, 0.7);

    public BellowsItem(Properties properties) {
        super(properties);
    }

    // ================= 右键拦截 =================

    public static InteractionResult interceptRightClick(Player player, Level level, InteractionHand hand,
                                                        BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (!(player.getMainHandItem().getItem() instanceof BellowsItem)) {
            return InteractionResult.PASS;
        }

        BlockPos pos = hit.getBlockPos();
        boolean shouldIntercept = false;

        if (level.getBlockEntity(pos) instanceof DepotBlockEntity depot) {
            TransportedItemStack held = depot.getHeldItem();
            shouldIntercept = held != null && !held.stack.isEmpty();
        } else if (BlockEntityBehaviour.get(level, pos, TransportedItemStackHandlerBehaviour.TYPE) != null) {
            shouldIntercept = true;
        }

        if (!shouldIntercept) {
            return InteractionResult.PASS;
        }

        player.startUsingItem(hand);
        return level.isClientSide() ? InteractionResult.SUCCESS : InteractionResult.SUCCESS_SERVER;
    }

    // ================= 蓄力 =================

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        // 风箱仅限主手使用（副手放介质）
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
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

    // ================= 蓄力刚满：提示音 =================

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingTicks) {
        if (!(entity instanceof Player player)) {
            return;
        }
        int usedTicks = getUseDuration(stack, entity) - remainingTicks;
        int chargeTicks = ServerConfig.INSTANCE.bellowsChargeTicks.get();
        if (usedTicks == chargeTicks && level.isClientSide()) {
            player.playSound(AllSoundEvents.CONFIRM.getMainEvent(), 1.0f, 1.0f);
        }
    }

    // ================= 松手 =================

    @Override
    public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!(entity instanceof Player player)) {
            return false;
        }

        int usedTicks = getUseDuration(stack, entity) - timeLeft;
        if (usedTicks < ServerConfig.INSTANCE.bellowsChargeTicks.get()) {
            return false;
        }
        if (level.isClientSide()) {
            return false;
        }

        // 副手介质
        ItemStack media = player.getOffhandItem();
        FanProcessingType type = media.isEmpty() ? null : BellowsMediaRegistry.resolve(media);
        String typeId = getTypeId(type);

        // 视线基础量
        double reach = player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);
        Vec3 eye = player.getEyePosition(1);
        Vec3 look = player.getViewVector(1);

        // 射线检测（从眼睛出发，用于判定命中方块）
        Vec3 clipEnd = eye.add(look.scale(reach));
        BlockHitResult hit = level.clip(new ClipContext(eye, clipEnd,
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));

        BlockPos pos = hit.getType() == HitResult.Type.BLOCK ? hit.getBlockPos() : null;

        // 粒子起点：风箱喷嘴（局部 → 世界）
        boolean rightArm = (player.getUsedItemHand() == InteractionHand.MAIN_HAND)
                == (player.getMainArm() == HumanoidArm.RIGHT);
        Vec3 origin = getItemMuzzlePos(player, rightArm, BELLOWS_MUZZLE_OFFSET);

        // ---- 路径 1：加工（原样使用 pos） ----
        List<ItemStack> particleItems = (pos != null)
                ? findTargetItems(level, pos)
                : List.of();

        boolean processed = false;
        if (pos != null && !particleItems.isEmpty() && type != null) {
            processed = applyToTarget(level, pos, type);
        }

        // ---- 路径 2：射线（独立计算终点） ----
        boolean posDistanceAbnormal = pos != null
                && pos.distSqr(player.blockPosition()) > BELLOWS_RAY_MAX_DISTANCE_SQR;
        Vec3 rayTarget = (pos != null && !posDistanceAbnormal)
                ? Vec3.atCenterOf(pos).add(0, 0.5, 0)
                : eye.add(look.scale(reach));

        // 射线实体检测：沿 [origin, rayTarget]
        List<Entity> hitEntities = findEntitiesInRay(level, player, origin, rayTarget);
        for (Entity e : hitEntities) {
            pushEntity(e, origin, rayTarget);
            if (type != null) {
                applyFanEffect(e, level, type);
            }
        }

        // 音效
        if (processed) {
            level.playSound(null, player.blockPosition(), SoundEvents.BREEZE_IDLE_AIR,
                    SoundSource.PLAYERS, 1.0f, 1.2f);
        } else if (type != null) {
            level.playSound(null, player.blockPosition(), SoundEvents.BREEZE_IDLE_AIR,
                    SoundSource.PLAYERS, 0.8f, 1.0f);
        } else {
            level.playSound(null, player.blockPosition(), SoundEvents.BREEZE_IDLE_AIR,
                    SoundSource.PLAYERS, 0.6f, 0.8f);
        }

        // 蓄满并释放 → 无条件扣 1 耐久
        stack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);

        broadcastBellows(
                level,
                processed ? pos : null,
                processed ? particleItems : List.of(),
                origin,
                rayTarget,
                typeId,
                processed
        );
        return true;
    }

    // ================= 射线实体检测 =================

    private static List<Entity> findEntitiesInRay(Level level, Player player, Vec3 origin, Vec3 target) {
        double lenSqr = origin.distanceToSqr(target);
        if (lenSqr < 1e-6 || lenSqr > BELLOWS_RAY_MAX_DISTANCE_SQR) {
            return List.of();
        }

        AABB searchBox = new AABB(origin, target).inflate(1.5);

        List<Entity> candidates = level.getEntities(player, searchBox,
                BellowsItem::isValidBellowsTarget);

        List<Entity> result = new ArrayList<>();
        for (Entity e : candidates) {
            Optional<Vec3> intersection = e.getBoundingBox().inflate(0.3).clip(origin, target);
            if (intersection.isPresent()) {
                result.add(e);
            }
        }
        return result;
    }

    /** 参考鼓风的实体过滤逻辑 */
    private static boolean isValidBellowsTarget(Entity entity) {
        if (!entity.isAlive()) {
            return false;
        }
        if (entity.isSpectator()) {
            return false;
        }
        // 创造飞行玩家不受影响（和鼓风一致）
        if (entity instanceof Player p) {
            if (p.isCreative() && p.getAbilities().flying) {
                return false;
            }
        }
        return true;
    }

    // ================= 推力 =================

    private static void pushEntity(Entity entity, Vec3 origin, Vec3 target) {
        Vec3 delta = target.subtract(origin);
        if (delta.lengthSqr() < 1e-6) {
            return;
        }
        Vec3 direction = delta.normalize();

        // 距离衰减：越远推力越弱
        double distance = Math.max(1.0, entity.position().distanceTo(origin));
        double pushStrength = PUSH_BASE / distance;

        // 蹲下减半（参考鼓风）
        if (entity.isShiftKeyDown()) {
            pushStrength *= 0.5;
        }

        Vec3 add = direction.scale(pushStrength);
        entity.setDeltaMovement(entity.getDeltaMovement().add(add));
        entity.fallDistance = 0;

        // 强制服务端把速度改变同步给客户端，否则玩家推不动
        entity.hurtMarked = true;
    }

    // ================= 效果施加（参考 AllFanProcessingTypes） =================

    private static void applyFanEffect(Entity entity, Level level, FanProcessingType type) {
        if (level.isClientSide()) {
            return;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        if (type == AllFanProcessingTypes.BLASTING) {
            // 熔炼：着火 6 秒 + 4 伤害
            if (!entity.fireImmune()) {
                entity.igniteForSeconds(BLASTING_FIRE_TICKS / 20f);
                entity.hurtServer(serverLevel, AllDamageSources.get(level).fan_lava, 4);
            }
        } else if (type == AllFanProcessingTypes.HAUNTING) {
            // 缠魂：失明 + 缓慢 3 秒
            if (entity instanceof LivingEntity living) {
                living.addEffect(new MobEffectInstance(
                        MobEffects.BLINDNESS, HAUNTING_EFFECT_TICKS, 0, false, false));
                living.addEffect(new MobEffectInstance(
                        MobEffects.SLOWNESS, HAUNTING_EFFECT_TICKS, 1, false, false));
            }
        } else if (type == AllFanProcessingTypes.SMOKING) {
            // 烟熏：着火 2 秒 + 2 伤害
            if (!entity.fireImmune()) {
                entity.igniteForSeconds(SMOKING_FIRE_TICKS / 20f);
                entity.hurtServer(serverLevel, AllDamageSources.get(level).fan_fire, 2);
            }
        } else if (type == AllFanProcessingTypes.SPLASHING) {
            // 洗涤：特定生物受伤 + 灭火
            if (entity instanceof EnderMan
                    || entity.getType() == EntityType.SNOW_GOLEM
                    || entity.getType() == EntityType.BLAZE) {
                entity.hurtServer(serverLevel, entity.damageSources().drown(), 2);
            }
            if (entity.isOnFire()) {
                entity.clearFire();
                level.playSound(null, entity.blockPosition(),
                        SoundEvents.GENERIC_EXTINGUISH_FIRE,
                        SoundSource.NEUTRAL, 0.7F,
                        1.6F + (level.getRandom().nextFloat() - level.getRandom().nextFloat()) * 0.4F);
            }
        }
    }

    // ================= 风箱喷嘴世界坐标 =================

    private static Vec3 getItemMuzzlePos(Player player, boolean rightArm, Vec3 offset) {
        double lx = offset.x * (rightArm ? 1 : -1);
        double ly = offset.y;
        double lz = offset.z;

        Vec3 look = player.getViewVector(1);

        Vec3 worldUp = new Vec3(0, 1, 0);
        Vec3 right = look.cross(worldUp);
        if (right.lengthSqr() < 1e-6) {
            right = new Vec3(0, 0, -1);
        } else {
            right = right.normalize();
        }

        Vec3 up = right.cross(look).normalize();

        Vec3 eye = player.getEyePosition(1);

        return eye
                .add(right.scale(lx))
                .add(up.scale(ly))
                .add(look.scale(lz));
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

    // ================= 加工 =================

    /** 把 Create 的鼓风类型映射成本模组配方层的 {@link FanType}。 */
    @Nullable
    private static FanType toFanType(FanProcessingType createType) {
        if (createType == AllFanProcessingTypes.BLASTING) {
            return FanType.BLASTING;
        }
        if (createType == AllFanProcessingTypes.SMOKING) {
            return FanType.SMOKING;
        }
        if (createType == AllFanProcessingTypes.HAUNTING) {
            return FanType.HAUNTING;
        }
        if (createType == AllFanProcessingTypes.SPLASHING) {
            return FanType.SPLASHING;
        }
        return null;
    }

    /**
     * 查一条能加工 {@code item} 的 L3 独占配方（{@code create_hand_made:bellows_recipe}）。
     *
     * <p>刻意不走 {@code HandMadeRecipePool}：池的语义是“某个 HandMadeTool 的候选集”，
     * 而风箱不在 HandMadeTool 体系里（它没有 tool 字段，按 fan_type 归类）。</p>
     */
    @Nullable
    private static RecipeHolder<HandMadeBellowsRecipe> findL3BellowsRecipe(Level level, ItemStack item,
                                                                          FanType fanType) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        SingleRecipeInput input = new SingleRecipeInput(item);
        for (RecipeHolder<?> holder : serverLevel.recipeAccess().getRecipes()) {
            if (!(holder.value() instanceof HandMadeBellowsRecipe recipe)) {
                continue;
            }
            if (recipe.fanType() != fanType) {
                continue;
            }
            if (!recipe.matches(input, level)) {
                continue;
            }
            return new RecipeHolder<>(holder.id(), recipe);
        }
        return null;
    }

    private static boolean applyToTarget(Level level, BlockPos pos, FanProcessingType type) {
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

            ItemStack single = stack.copyWithCount(1);

            // L3 优先：独占配方只替换“物品加工”这一步。
            // 未命中时原样回落到 Create 的鼓风逻辑（L1）—— 即 type.canProcess / type.process。
            List<ItemStack> results = null;
            FanType fanType = toFanType(type);
            if (fanType != null) {
                RecipeHolder<HandMadeBellowsRecipe> l3 = findL3BellowsRecipe(level, single, fanType);
                if (l3 != null && l3.value() instanceof CreateSingleStackRollableRecipe rollable) {
                    results = RecipeApplier.applyRecipeOn(level.getRandom(), 1,
                            new SingleRecipeInput(single), rollable);
                }
            }

            // L1 回落：真正的“能不能加工”仍由 Create 的 FanProcessingType 决定
            if (results == null) {
                if (!type.canProcess(single, level)) {
                    return TransportedResult.doNothing();
                }

                results = type.process(single, level);

                if (results == null) {
                    return TransportedResult.doNothing();
                }
            }

            success[0] = true;
            ItemStack left = stack.copy();
            left.shrink(1);

            List<TransportedItemStack> outputs = new ArrayList<>();
            for (ItemStack r : results) {
                if (r.isEmpty()) {
                    continue;
                }
                TransportedItemStack copy = item.copy();
                copy.stack = r;
                outputs.add(copy);
            }

            type.spawnProcessingParticles(level, handler.getWorldPositionOf(item));

            if (outputs.isEmpty()) {
                if (left.isEmpty()) {
                    return TransportedResult.removeItem();
                }
                TransportedItemStack leftStack = item.copy();
                leftStack.stack = left;
                return TransportedResult.convertTo(leftStack);
            } else {
                if (left.isEmpty()) {
                    return TransportedResult.convertTo(outputs);
                }
                TransportedItemStack leftStack = item.copy();
                leftStack.stack = left;
                return TransportedResult.convertToAndLeaveHeld(outputs, leftStack);
            }
        });

        return success[0];
    }

    // ================= 广播 =================

    private static void broadcastBellows(Level level,
                                         @Nullable BlockPos blastPos,
                                         List<ItemStack> stacks,
                                         Vec3 origin, Vec3 target,
                                         String typeId,
                                         boolean spawnBlast) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        BellowsBlastPacket packet = new BellowsBlastPacket(
                blastPos,
                origin.x, origin.y, origin.z,
                target.x, target.y, target.z,
                stacks,
                typeId,
                spawnBlast
        );

        double rangeSq = 64.0 * 64.0;
        for (ServerPlayer p : serverLevel.players()) {
            if (p.distanceToSqr(origin.x, origin.y, origin.z) <= rangeSq) {
                ServerPlayNetworking.send(p, packet);
            }
        }
    }

    /**
     * 把 FanProcessingType 反查成 ID 字符串（供客户端还原类型用）。
     */
    private static String getTypeId(@Nullable FanProcessingType type) {
        if (type == null) {
            return "";
        }
        Identifier key = CreateRegistries.FAN_PROCESSING_TYPE.getKey(type);
        return key != null ? key.toString() : "";
    }
}
