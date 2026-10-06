package com.alben.createhandmade.item;

import com.alben.createhandmade.bellows.BellowsMediaRegistry;
import com.alben.createhandmade.network.BellowsBlastPacket;
import com.alben.createhandmade.network.ModNetwork;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.TransportedResult;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.kinetics.fan.processing.AllFanProcessingTypes;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.damageTypes.CreateDamageSources;
import com.simibubi.create.foundation.item.render.SimpleCustomRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

@Mod.EventBusSubscriber
public class BellowsItem extends Item {

    private static final int CHARGE_TICKS = 10;

    private static final int BLASTING_FIRE_TICKS = 120;
    private static final int SMOKING_FIRE_TICKS = 40;
    private static final int HAUNTING_EFFECT_TICKS = 60;

    private static final double PUSH_BASE = 5;

    private static final long BELLOWS_RAY_MAX_DISTANCE_SQR = 64L * 64L;

    /**
     * 风箱喷嘴相对玩家胸口的局部坐标（右手臂视角）。
     * ★ 由"这条手臂在玩家的哪一侧"决定是否镜像 X（见 getItemMuzzlePos），
     *   不是由"主手/副手槽位"决定 —— 左手模式下二者结论相反。
     */
    private static final Vec3 BELLOWS_MUZZLE_OFFSET = new Vec3(0.55, -0.25, 0.7);

    public BellowsItem(Properties properties) {
        super(properties);
    }

    @Override
    public int getEnchantmentValue(ItemStack stack) {
        return 15;
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(SimpleCustomRenderer.create(this, new BellowsItemRenderer()));
    }

    // ================= 右键拦截 =================

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (!(player.getMainHandItem().getItem() instanceof BellowsItem)) return;

        Level level = event.getLevel();
        BlockPos pos = event.getPos();

        boolean shouldIntercept = false;

        if (level.getBlockEntity(pos) instanceof DepotBlockEntity depot) {
            shouldIntercept = !depot.getHeldItem().isEmpty();
        } else if (BlockEntityBehaviour.get(level, pos, TransportedItemStackHandlerBehaviour.TYPE) != null) {
            shouldIntercept = true;
        }

        if (shouldIntercept) {
            event.setUseBlock(Event.Result.DENY);
        }
    }

    // ================= 蓄力 =================

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        // ★ 限主手使用，副手专门放介质
        if (hand == InteractionHand.OFF_HAND) {
            return InteractionResultHolder.pass(player.getItemInHand(hand));
        }

        ItemStack stack = player.getItemInHand(hand);
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000;
    }

    // ================= 松手 =================

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!(entity instanceof Player player)) return;

        // ★ 只处理主手，副手用不了
        if (player.getUsedItemHand() != InteractionHand.MAIN_HAND) return;

        int usedTicks = getUseDuration(stack) - timeLeft;
        if (usedTicks < CHARGE_TICKS) return;
        if (level.isClientSide) return;

        ItemStack media = player.getOffhandItem();
        FanProcessingType type = media.isEmpty() ? null : BellowsMediaRegistry.resolve(media);
        String typeId = getTypeId(type);

        double reach = player.getAttributeValue(ForgeMod.BLOCK_REACH.get());
        Vec3 eye = player.getEyePosition(1);
        Vec3 look = player.getViewVector(1);

        Vec3 clipEnd = eye.add(look.scale(reach));
        BlockHitResult hit = level.clip(new ClipContext(eye, clipEnd,
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));

        BlockPos pos = hit.getType() == HitResult.Type.BLOCK ? hit.getBlockPos() : null;

        // ★ 左右判据必须是"这条手臂在玩家的哪一侧"，而不是"在哪个槽位"：
        //   左手模式（mainArm = LEFT）下主手出现在屏幕左侧，只看槽位会把喷嘴放到反侧。
        boolean rightArm = (player.getUsedItemHand() == InteractionHand.MAIN_HAND)
                == (player.getMainArm() == HumanoidArm.RIGHT);
        Vec3 origin = getItemMuzzlePos(player, rightArm, BELLOWS_MUZZLE_OFFSET);

        // ---- 路径 1：加工 ----
        List<ItemStack> particleItems = (pos != null)
                ? findTargetItems(level, pos)
                : List.of();

        boolean processed = false;
        if (pos != null && !particleItems.isEmpty() && type != null) {
            processed = applyToTarget(level, pos, type);
        }

        // ---- 路径 2：射线 ----
        boolean posDistanceAbnormal = pos != null
                && pos.distSqr(player.blockPosition()) > BELLOWS_RAY_MAX_DISTANCE_SQR;
        Vec3 rayTarget = (pos != null && !posDistanceAbnormal)
                ? Vec3.atCenterOf(pos).add(0, 0.5, 0)
                : eye.add(look.scale(reach));

        List<Entity> hitEntities = findEntitiesInRay(level, player, origin, rayTarget);
        for (Entity e : hitEntities) {
            pushEntity(e, origin, rayTarget);
            if (type != null) {
                applyFanEffect(e, level, type);
            }
        }

        if (processed) {
            level.playSound(null, player.blockPosition(), SoundEvents.PHANTOM_FLAP,
                    SoundSource.PLAYERS, 1.0f, 1.2f);
        } else if (type != null) {
            level.playSound(null, player.blockPosition(), SoundEvents.PHANTOM_FLAP,
                    SoundSource.PLAYERS, 0.8f, 1.0f);
        } else {
            level.playSound(null, player.blockPosition(), SoundEvents.PHANTOM_FLAP,
                    SoundSource.PLAYERS, 0.6f, 0.8f);
        }

        stack.hurtAndBreak(1, player, e -> e.broadcastBreakEvent(EquipmentSlot.MAINHAND));

        broadcastBellows(
                level,
                processed ? pos : null,
                processed ? particleItems : List.of(),
                origin,
                rayTarget,
                typeId,
                processed
        );
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

    private static boolean isValidBellowsTarget(Entity entity) {
        if (!entity.isAlive()) return false;
        if (entity.isSpectator()) return false;
        if (entity instanceof Player p) {
            if (p.isCreative() && p.getAbilities().flying) return false;
        }
        return true;
    }

    // ================= 推力 =================

    private static void pushEntity(Entity entity, Vec3 origin, Vec3 target) {
        Vec3 delta = target.subtract(origin);
        if (delta.lengthSqr() < 1e-6) return;
        Vec3 direction = delta.normalize();

        double distance = Math.max(1.0, entity.position().distanceTo(origin));
        double pushStrength = PUSH_BASE / distance;

        if (entity.isShiftKeyDown()) {
            pushStrength *= 0.5;
        }

        Vec3 add = direction.scale(pushStrength);
        entity.setDeltaMovement(entity.getDeltaMovement().add(add));
        entity.fallDistance = 0;

        entity.hurtMarked = true;
    }

    // ================= 效果施加 =================

    private static void applyFanEffect(Entity entity, Level level, FanProcessingType type) {
        if (level.isClientSide) return;

        if (type == AllFanProcessingTypes.BLASTING) {
            if (!entity.fireImmune()) {
                entity.setSecondsOnFire(BLASTING_FIRE_TICKS / 20);
                entity.hurt(CreateDamageSources.fanLava(level), 4);
            }
        } else if (type == AllFanProcessingTypes.HAUNTING) {
            if (entity instanceof LivingEntity living) {
                living.addEffect(new MobEffectInstance(
                        MobEffects.BLINDNESS, HAUNTING_EFFECT_TICKS, 0, false, false));
                living.addEffect(new MobEffectInstance(
                        MobEffects.MOVEMENT_SLOWDOWN, HAUNTING_EFFECT_TICKS, 1, false, false));
            }
        } else if (type == AllFanProcessingTypes.SMOKING) {
            if (!entity.fireImmune()) {
                entity.setSecondsOnFire(SMOKING_FIRE_TICKS / 20);
                entity.hurt(CreateDamageSources.fanFire(level), 2);
            }
        } else if (type == AllFanProcessingTypes.SPLASHING) {
            if (entity instanceof EnderMan
                    || entity.getType() == EntityType.SNOW_GOLEM
                    || entity.getType() == EntityType.BLAZE) {
                entity.hurt(entity.damageSources().drown(), 2);
            }
            if (entity.isOnFire()) {
                entity.clearFire();
                level.playSound(null, entity.blockPosition(),
                        SoundEvents.GENERIC_EXTINGUISH_FIRE,
                        SoundSource.NEUTRAL, 0.7F,
                        1.6F + (level.random.nextFloat() - level.random.nextFloat()) * 0.4F);
            }
        }
    }

    // ================= 风箱喷嘴世界坐标 =================

    /**
     * @param rightArm true = 风箱所在手臂在玩家右侧，false = 在左侧
     */
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
            ItemStack s = depot.getHeldItem();
            if (!s.isEmpty()) result.add(s.copy());
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

    // ================= 加工 =================

    private static boolean applyToTarget(Level level, BlockPos pos, FanProcessingType type) {
        TransportedItemStackHandlerBehaviour handler =
                BlockEntityBehaviour.get(level, pos, TransportedItemStackHandlerBehaviour.TYPE);
        if (handler == null) return false;

        boolean[] success = {false};

        handler.handleProcessingOnAllItems(item -> {
            if (success[0]) return TransportedResult.doNothing();
            ItemStack stack = item.stack;
            if (stack.isEmpty()) return TransportedResult.doNothing();

            ItemStack single = stack.copyWithCount(1);

            if (!type.canProcess(single, level)) return TransportedResult.doNothing();

            List<ItemStack> results = type.process(single, level);

            if (results == null) return TransportedResult.doNothing();

            success[0] = true;
            ItemStack left = stack.copy();
            left.shrink(1);

            List<TransportedItemStack> outputs = new ArrayList<>();
            for (ItemStack r : results) {
                if (r.isEmpty()) continue;
                TransportedItemStack copy = item.copy();
                copy.stack = r;
                outputs.add(copy);
            }

            type.spawnProcessingParticles(level, handler.getWorldPositionOf(item));

            if (outputs.isEmpty()) {
                if (left.isEmpty()) return TransportedResult.removeItem();
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
        if (!(level instanceof ServerLevel serverLevel)) return;

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
                ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), packet);
            }
        }
    }

    /** 把 FanProcessingType 反查成 ID 字符串 */
    private static String getTypeId(@Nullable FanProcessingType type) {
        if (type == null) return "";
        if (type == AllFanProcessingTypes.BLASTING) return "create:blasting";
        if (type == AllFanProcessingTypes.HAUNTING) return "create:haunting";
        if (type == AllFanProcessingTypes.SMOKING) return "create:smoking";
        if (type == AllFanProcessingTypes.SPLASHING) return "create:splashing";
        return "";
    }
}