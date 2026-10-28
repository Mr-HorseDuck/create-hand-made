package com.alben.createhandmade.item;

import com.alben.createhandmade.Config;
import com.alben.createhandmade.bellows.BellowsMediaRegistry;
import com.alben.createhandmade.item.renderer.BellowsItemRenderer;
import com.alben.createhandmade.network.BellowsBlastPacket;
import com.alben.createhandmade.network.ModNetwork;
import com.alben.createhandmade.recipe.HandMadeRecipeTypes;
import com.alben.createhandmade.recipe.type.FanType;
import com.alben.createhandmade.recipe.type.HandMadeBellowsRecipe;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.TransportedResult;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.kinetics.fan.processing.AllFanProcessingTypes;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.damageTypes.CreateDamageSources;
import com.simibubi.create.foundation.item.render.SimpleCustomRenderer;
import com.simibubi.create.foundation.recipe.RecipeApplier;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
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
import net.minecraft.world.item.crafting.Recipe;
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
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.wrapper.RecipeWrapper;
import net.minecraftforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

@Mod.EventBusSubscriber
public class BellowsItem extends Item {

    private static final int BLASTING_FIRE_TICKS = 120;
    private static final int SMOKING_FIRE_TICKS = 40;
    private static final int HAUNTING_EFFECT_TICKS = 60;

    private static final double PUSH_BASE = 5;

    private static final long BELLOWS_RAY_MAX_DISTANCE_SQR = 64L * 64L;

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

    // ================= 蓄力刚满：提示音 =================

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingTicks) {
        if (!(entity instanceof Player player)) return;
        int usedTicks = getUseDuration(stack) - remainingTicks;
        int chargeTicks = Config.INSTANCE.bellowsChargeTicks.get();
        if (usedTicks == chargeTicks && level.isClientSide) {
            player.playSound(AllSoundEvents.CONFIRM.getMainEvent(), 1.0f, 1.0f);
        }
    }

    // ================= 松手 =================

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!(entity instanceof Player player)) return;
        if (player.getUsedItemHand() != InteractionHand.MAIN_HAND) return;

        int usedTicks = getUseDuration(stack) - timeLeft;
        if (usedTicks < Config.INSTANCE.bellowsChargeTicks.get()) return;
        if (level.isClientSide) return;

        // ★ 1. 拿副手介质的候选类型
        ItemStack media = player.getOffhandItem();
        BellowsMediaRegistry.EffectiveTypes effective = media.isEmpty()
                ? new BellowsMediaRegistry.EffectiveTypes(List.of(), null)
                : BellowsMediaRegistry.resolveEffective(media);

        double reach = player.getAttributeValue(ForgeMod.BLOCK_REACH.get());
        Vec3 eye = player.getEyePosition(1);
        Vec3 look = player.getViewVector(1);

        Vec3 clipEnd = eye.add(look.scale(reach));
        BlockHitResult hit = level.clip(new ClipContext(eye, clipEnd,
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));

        BlockPos pos = hit.getType() == HitResult.Type.BLOCK ? hit.getBlockPos() : null;

        boolean rightArm = (player.getUsedItemHand() == InteractionHand.MAIN_HAND)
                == (player.getMainArm() == HumanoidArm.RIGHT);
        Vec3 origin = getItemMuzzlePos(player, rightArm, BELLOWS_MUZZLE_OFFSET);

        List<ItemStack> particleItems = (pos != null)
                ? findTargetItems(level, pos)
                : List.of();

        // ★ 2. 从候选类型里选一个能加工目标的；没有方块目标时选优先级最高的（用于实体效果）
        FanProcessingType type = selectTypeForTargets(level, effective, particleItems);
        String typeId = getTypeId(type);

        boolean processed = false;
        if (pos != null && !particleItems.isEmpty() && type != null) {
            processed = applyToTarget(level, pos, type);
        }

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

        broadcastBellows(level,
                processed ? pos : null,
                processed ? particleItems : List.of(),
                origin, rayTarget, typeId, processed);
    }

    // ================= 从候选里选类型 =================

    /**
     * 从副手介质的候选类型里选一个用于本次加工。
     *
     * <ul>
     *   <li>候选为空 → null</li>
     *   <li>配置指定了 preferred，且 preferred 能加工任意一个目标 → preferred</li>
     *   <li>否则按生效优先级从高到低，选第一个能加工任意一个目标的</li>
     *   <li>没有方块目标（只对实体吹）时，返回优先级最高的类型</li>
     * </ul>
     */
    @Nullable
    private static FanProcessingType selectTypeForTargets(Level level,
                                                          BellowsMediaRegistry.EffectiveTypes effective,
                                                          List<ItemStack> targets) {
        if (effective.ids().isEmpty()) return null;

        // preferred 优先
        if (effective.preferred() != null) {
            FanProcessingType preferred = BellowsMediaRegistry.getType(effective.preferred());
            if (preferred != null && matchesAnyTarget(level, preferred, targets)) {
                return preferred;
            }
        }

        // 按生效优先级从高到低
        List<ResourceLocation> sorted = new ArrayList<>(effective.ids());
        sorted.sort((a, b) -> Integer.compare(
                BellowsMediaRegistry.getEffectivePriority(b),
                BellowsMediaRegistry.getEffectivePriority(a)));

        for (ResourceLocation id : sorted) {
            FanProcessingType candidate = BellowsMediaRegistry.getType(id);
            if (candidate == null) continue;
            if (matchesAnyTarget(level, candidate, targets)) {
                return candidate;
            }
        }

        return null;
    }

    /** targets 为空视为"匹配"（只对实体吹的场景）；否则任一物品能被 type 加工即匹配。 */
    private static boolean matchesAnyTarget(Level level, FanProcessingType type, List<ItemStack> targets) {
        if (targets.isEmpty()) return true;
        for (ItemStack t : targets) {
            if (canProcess(level, t, type)) return true;
        }
        return false;
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

    /**
     * 实体效果。当前只处理 Create 4 种内置类型。
     * 第三方类型的实体转化（DnD / 龙+ 等）暂不处理。
     */
    // 在 BellowsItem 类中，找到 applyFanEffect 方法，替换为以下版本：

private static void applyFanEffect(Entity entity, Level level, FanProcessingType type) {
    if (level.isClientSide) return;

    ResourceLocation typeId = BellowsMediaRegistry.getTypeId(type);
    if (typeId == null) return;

    String ns = typeId.getNamespace();
    String path = typeId.getPath();

    // ========== Create 原版 4 种 ==========
    if (type == AllFanProcessingTypes.BLASTING) {
        if (!entity.fireImmune()) {
            entity.setSecondsOnFire(BLASTING_FIRE_TICKS / 20);
            entity.hurt(CreateDamageSources.fanLava(level), 4);
        }
        return;
    }
    if (type == AllFanProcessingTypes.HAUNTING) {
        if (entity instanceof LivingEntity living) {
            living.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, HAUNTING_EFFECT_TICKS, 0, false, false));
            living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, HAUNTING_EFFECT_TICKS, 1, false, false));
        }
        return;
    }
    if (type == AllFanProcessingTypes.SMOKING) {
        if (!entity.fireImmune()) {
            entity.setSecondsOnFire(SMOKING_FIRE_TICKS / 20);
            entity.hurt(CreateDamageSources.fanFire(level), 2);
        }
        return;
    }
    if (type == AllFanProcessingTypes.SPLASHING) {
        if (entity instanceof EnderMan
                || entity.getType() == EntityType.SNOW_GOLEM
                || entity.getType() == EntityType.BLAZE) {
            entity.hurt(entity.damageSources().drown(), 2);
        }
        if (entity.isOnFire()) {
            entity.clearFire();
            level.playSound(null, entity.blockPosition(), SoundEvents.GENERIC_EXTINGUISH_FIRE,
                    SoundSource.NEUTRAL, 0.7F,
                    1.6F + (level.random.nextFloat() - level.random.nextFloat()) * 0.4F);
        }
        return;
    }

    // ========== CMC (Create: More Catalysts) ==========
    if (ns.equals("create_more_catalysts")) {
        switch (path) {
            case "withering" -> {
                if (entity instanceof LivingEntity living && entity.getType() != EntityType.WITHER) {
                    living.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 0));
                }
                if (entity.getType() == EntityType.SKELETON) {
                    transformEntity(entity, EntityType.WITHER_SKELETON, level);
                }
            }
            case "snow_flow" -> {
                if (entity instanceof LivingEntity living) {
                    living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 120, 0));
                }
                if (entity.getType() == EntityType.SKELETON) {
                    transformEntity(entity, EntityType.STRAY, level);
                }
                if (entity.getType() == EntityType.VILLAGER) {
                    transformEntity(entity, EntityType.SNOW_GOLEM, level);
                }
            }
            case "purifying" -> {
                if (entity instanceof LivingEntity living && entity.getType() != EntityType.WITHER) {
                    living.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 120, 0));
                }
                if (entity.getType() == EntityType.ZOMBIE_VILLAGER) {
                    transformEntity(entity, EntityType.VILLAGER, level);
                }
            }
            case "sculking" -> {
                if (entity instanceof LivingEntity living) {
                    living.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 120, 0));
                    living.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 120, 0));
                }
                if (entity.getType() == EntityType.IRON_GOLEM) {
                    transformEntity(entity, EntityType.WARDEN, level);
                }
            }
            case "sand_blow" -> {
                if (entity instanceof LivingEntity living) {
                    living.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 120, 0));
                }
                if (entity.getType() == EntityType.ZOMBIE) {
                    transformEntity(entity, EntityType.HUSK, level);
                }
            }
            case "exploding" -> {
                if (entity.getType() == EntityType.CREEPER) {
                    level.explode(null, entity.getX(), entity.getY(), entity.getZ(),
                            3.0F, Level.ExplosionInteraction.NONE);
                }
            }
            case "tidal" -> {
                if (entity instanceof LivingEntity living) {
                    living.addEffect(new MobEffectInstance(MobEffects.CONDUIT_POWER, 120, 0));
                }
            }
            case "tear" -> {
                if (entity instanceof LivingEntity living) {
                    living.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 120, 0));
                }
                if (entity.getType() == EntityType.PHANTOM) {
                    transformEntity(entity, EntityType.GHAST, level);
                }
            }
            default -> {
                // 其余 CMC 类型对实体无明确效果，留空
            }
        }
        return;
    }

    // 其他第三方类型（龙+ 的 coloring 等）暂时不处理实体效果
}

/**
 * 把实体替换为目标类型，保留位置、朝向、血量。
 */
private static void transformEntity(Entity entity, EntityType<?> targetType, Level level) {
    if (level.isClientSide) return;
    if (entity.getType() == targetType) return;

    Entity newEntity = targetType.create(level);
    if (newEntity == null) return;

    newEntity.moveTo(entity.getX(), entity.getY(), entity.getZ(),
            entity.getYRot(), entity.getXRot());
    if (entity instanceof LivingEntity living && newEntity instanceof LivingEntity newLiving) {
        newLiving.setHealth(Math.min(living.getHealth(), newLiving.getMaxHealth()));
    }

    entity.discard();
    level.addFreshEntity(newEntity);
}

    // 其他第三方类型（龙+ 的 coloring 等）暂时不处理实体效果
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

        return eye.add(right.scale(lx)).add(up.scale(ly)).add(look.scale(lz));
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

    // ================= FanProcessingType ↔ FanType =================

    @Nullable
    private static FanType toFanType(FanProcessingType createType) {
        if (createType == AllFanProcessingTypes.BLASTING) return FanType.BLASTING;
        if (createType == AllFanProcessingTypes.SMOKING) return FanType.SMOKING;
        if (createType == AllFanProcessingTypes.HAUNTING) return FanType.HAUNTING;
        if (createType == AllFanProcessingTypes.SPLASHING) return FanType.SPLASHING;
        return null;
    }

    // ================= L3 配方查询 =================

    /**
     * 查 L3 风箱配方并应用，返回产物列表；没有匹配返回 null（调用方回落到 L1）。
     */
    @Nullable
    private static List<ItemStack> findL3Results(Level level, ItemStack single, FanProcessingType type) {
        ResourceLocation typeId = BellowsMediaRegistry.getTypeId(type);
        if (typeId == null) return null;

        ItemStackHandler handler = new ItemStackHandler(1);
        handler.setStackInSlot(0, single);
        RecipeWrapper wrapper = new RecipeWrapper(handler);

        for (Recipe<?> r : level.getRecipeManager()
                .getAllRecipesFor(HandMadeRecipeTypes.BELLOWS_RECIPE.get())) {
            if (!(r instanceof HandMadeBellowsRecipe recipe)) continue;
            if (!typeId.equals(recipe.getFanType())) continue;
            if (!recipe.matches(wrapper, level)) continue;
            return RecipeApplier.applyRecipeOn(level, single, recipe, true);
        }
        return null;
        }

    /**
     * 判断某个物品能否被指定鼓风类型加工。女仆行为用它区分"原料"和"产物"。
     * L3 优先，L1 兜底，和 {@link #applyToTarget} 内部逻辑一致。
     */
    public static boolean canProcess(Level level, ItemStack item, FanProcessingType type) {
        if (item.isEmpty()) return false;
        ItemStack single = item.copyWithCount(1);
        if (findL3Results(level, single, type) != null) return true;
        return type.canProcess(single, level);
    }

    // ================= 加工 =================

    public static boolean applyToTarget(Level level, BlockPos pos, FanProcessingType type) {
        TransportedItemStackHandlerBehaviour handler =
                BlockEntityBehaviour.get(level, pos, TransportedItemStackHandlerBehaviour.TYPE);
        if (handler == null) return false;

        boolean[] success = {false};

        handler.handleProcessingOnAllItems(item -> {
            if (success[0]) return TransportedResult.doNothing();
            ItemStack stack = item.stack;
            if (stack.isEmpty()) return TransportedResult.doNothing();

            ItemStack single = stack.copyWithCount(1);

            // L3 优先
            List<ItemStack> results = findL3Results(level, single, type);

            // L1 兜底
            if (results == null) {
                if (!type.canProcess(single, level)) return TransportedResult.doNothing();
                results = type.process(single, level);
                if (results == null) return TransportedResult.doNothing();
            }

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

    /** 反查类型 id。第三方类型也走 BellowsMediaRegistry，不再硬编码 4 种。 */
    private static String getTypeId(@Nullable FanProcessingType type) {
        if (type == null) return "";
        ResourceLocation id = BellowsMediaRegistry.getTypeId(type);
        return id == null ? "" : id.toString();
    }
}