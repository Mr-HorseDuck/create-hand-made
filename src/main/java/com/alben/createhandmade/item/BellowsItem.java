package com.alben.createhandmade.item;

import com.alben.createhandmade.bellows.BellowsMediaRegistry;
import com.alben.createhandmade.network.BellowsBlastPacket;
import com.alben.createhandmade.recipe.FanType;
import com.alben.createhandmade.recipe.HandMadeBellowsRecipe;
import com.alben.createhandmade.recipe.HandMadeRecipeTypes;
import com.simibubi.create.api.registry.CreateBuiltInRegistries;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

@EventBusSubscriber
public class BellowsItem extends Item {

    private static final int CHARGE_TICKS = 10;  // 0.5 秒

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
     * 仅在计算"射线终点"和"射线实体检测"时使用；加工判定与爆发粒子不受此限制。
     */
    private static final long BELLOWS_RAY_MAX_DISTANCE_SQR = 64L * 64L;

    /**
     * 风箱喷嘴相对玩家胸口的局部坐标（主手视角）：
     *   X = 屏幕右为正
     *   Y = 上为正
     *   Z = 前为正
     * 主手/副手会自动镜像 X。
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
    @SuppressWarnings("removal")
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

    // ================= 松手 =================

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!(entity instanceof Player player)) return;

        int usedTicks = getUseDuration(stack, entity) - timeLeft;
        if (usedTicks < CHARGE_TICKS) return;
        if (level.isClientSide) return;

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
        boolean mainHand = player.getUsedItemHand() == InteractionHand.MAIN_HAND;
        Vec3 origin = getItemMuzzlePos(player, mainHand, BELLOWS_MUZZLE_OFFSET);

        // =========================================================
        // ★ 两条独立路径：
        //   1) 加工 / 爆发粒子 → 用原始 pos（物理结构坐标也可），不受距离限制
        //   2) 射线粒子渲染 / 射线实体检测 → 用 rayTarget，超出正常距离时回退到视线方向
        // =========================================================

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

        // 广播：
        //   blastPos / stacks → 加工爆发用（原始 pos）
        //   origin / rayTarget → 射线粒子渲染用（安全距离）
        //   spawnBlast → 只有加工成功才触发末端爆发
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

    /**
     * 找到射线路径上所有有效生物。
     * 有效判定参考鼓风：存活、非旁观、非创造飞行。
     */
    private static List<Entity> findEntitiesInRay(Level level, Player player, Vec3 origin, Vec3 target) {
        // 防御：射线路径异常时直接返回（正常射线长度不会超过交互距离）
        double lenSqr = origin.distanceToSqr(target);
        if (lenSqr < 1e-6 || lenSqr > BELLOWS_RAY_MAX_DISTANCE_SQR) {
            return List.of();
        }

        // 射线路径的膨胀包围盒
        AABB searchBox = new AABB(origin, target).inflate(1.5);

        List<Entity> candidates = level.getEntities(player, searchBox,
                BellowsItem::isValidBellowsTarget);

        List<Entity> result = new ArrayList<>();
        for (Entity e : candidates) {
            // 精确检测：射线是否与实体包围盒相交
            Optional<Vec3> intersection = e.getBoundingBox().inflate(0.3).clip(origin, target);
            if (intersection.isPresent()) {
                result.add(e);
            }
        }
        return result;
    }

    /** 参考鼓风的实体过滤逻辑 */
    private static boolean isValidBellowsTarget(Entity entity) {
        if (!entity.isAlive()) return false;
        if (entity.isSpectator()) return false;
        // 创造飞行玩家不受影响（和鼓风一致）
        if (entity instanceof Player p) {
            if (p.isCreative() && p.getAbilities().flying) return false;
        }
        return true;
    }

    // ================= 推力 =================

    /**
     * 沿射线方向推实体。
     * 参考鼓风：距离越远推力越小；玩家蹲下时推力减半。
     */
    private static void pushEntity(Entity entity, Vec3 origin, Vec3 target) {
        Vec3 delta = target.subtract(origin);
        if (delta.lengthSqr() < 1e-6) return;
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

        // ★ 关键：强制服务端把速度改变同步给客户端，否则玩家推不动
        entity.hurtMarked = true;
    }

    // ================= 效果施加（参考 AllFanProcessingTypes） =================

    private static void applyFanEffect(Entity entity, Level level, FanProcessingType type) {
        if (level.isClientSide) return;

        if (type == AllFanProcessingTypes.BLASTING) {
            // 熔炼：着火 6 秒 + 4 伤害
            if (!entity.fireImmune()) {
                entity.igniteForSeconds(BLASTING_FIRE_TICKS / 20f);
                entity.hurt(CreateDamageSources.fanLava(level), 4);
            }
        } else if (type == AllFanProcessingTypes.HAUNTING) {
            // 缠魂：失明 + 缓慢 3 秒
            if (entity instanceof LivingEntity living) {
                living.addEffect(new MobEffectInstance(
                        MobEffects.BLINDNESS, HAUNTING_EFFECT_TICKS, 0, false, false));
                living.addEffect(new MobEffectInstance(
                        MobEffects.MOVEMENT_SLOWDOWN, HAUNTING_EFFECT_TICKS, 1, false, false));
            }
        } else if (type == AllFanProcessingTypes.SMOKING) {
            // 烟熏：着火 2 秒 + 2 伤害
            if (!entity.fireImmune()) {
                entity.igniteForSeconds(SMOKING_FIRE_TICKS / 20f);
                entity.hurt(CreateDamageSources.fanFire(level), 2);
            }
        } else if (type == AllFanProcessingTypes.SPLASHING) {
            // 洗涤：特定生物受伤 + 灭火
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

    private static Vec3 getItemMuzzlePos(Player player, boolean mainHand, Vec3 offset) {
        double lx = offset.x * (mainHand ? 1 : -1);
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

    /**
     * 把 Create 的鼓风类型映射成本模组配方层的 {@link FanType}。
     *
     * <p><b>为什么放在这里（而不是 {@code BellowsMediaRegistry} 或 {@code FanType} 里）：</b></p>
     * <ul>
     *   <li>放在 {@link FanType} 会让 {@code recipe} 包 import Create 的风扇实现类
     *       （{@code AllFanProcessingTypes}），而批次 1 的 {@code FanType} 注释明确说了
     *       "刻意不引用 Create 的类型对象" —— 这条边界要守住；</li>
     *   <li>放在 {@link BellowsMediaRegistry} 更"对称"（它已经拥有"介质 → Create 类型"的正向映射），
     *       但目前只有本类这一个调用点，而批次 1 的 {@code FanType} 注释已经写明
     *       "与 Create 类型的映射放在使用方（BellowsItem）"。等将来 JEI / L2 也需要反查时
     *       再把它挪进 registry 包即可。</li>
     * </ul>
     *
     * @return 对应的 {@link FanType}；未知类型（理论上不会发生）返回 null
     */
    @Nullable
    private static FanType toFanType(FanProcessingType createType) {
        if (createType == AllFanProcessingTypes.BLASTING) return FanType.BLASTING;
        if (createType == AllFanProcessingTypes.SMOKING) return FanType.SMOKING;
        if (createType == AllFanProcessingTypes.HAUNTING) return FanType.HAUNTING;
        if (createType == AllFanProcessingTypes.SPLASHING) return FanType.SPLASHING;
        return null;
    }

    /**
     * 查一条能加工 {@code item} 的 L3 独占配方（{@code create_hand_made:bellows_recipe}）。
     *
     * <p><b>刻意不走 {@link com.alben.createhandmade.recipe.HandMadeRecipePool}：</b>
     * 池的语义是"某个 {@code HandMadeTool} 的候选集"，而风箱不在 {@code HandMadeTool} 体系里
     * （它没有 tool 字段，按 {@code fan_type} 归类）。这里直接查 RecipeManager，
     * 只做两件判定：{@code fan_type} 相同、{@code matches} 通过。</p>
     *
     * <p>遍历用 {@code RecipeHolder<?>} + {@code instanceof} 模式匹配（与手锯 / 灌注枪的查法一致）：
     * {@code HandMadeRecipeTypes.getType()} 的泛型参数无法从上下文推断，
     * 直接强转 {@code RecipeType<HandMadeBellowsRecipe>} 会被编译器判为不兼容类型。</p>
     *
     * <p>顺序取第一条命中的 —— 与 L1 路径"取第一个能加工的"语义一致。</p>
     */
    @Nullable
    private static RecipeHolder<HandMadeBellowsRecipe> findL3BellowsRecipe(Level level, ItemStack item,
                                                                          FanType fanType) {
        SingleRecipeInput input = new SingleRecipeInput(item);
        for (RecipeHolder<?> holder : level.getRecipeManager()
                .getAllRecipesFor(HandMadeRecipeTypes.BELLOWS_RECIPE.getType())) {
            if (!(holder.value() instanceof HandMadeBellowsRecipe recipe)) continue;
            if (recipe.getFanType() != fanType) continue;
            if (!recipe.matches(input, level)) continue;
            return new RecipeHolder<>(holder.id(), recipe);
        }
        return null;
    }

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

            // ★ L3 优先：独占配方（create_hand_made:bellows_recipe）只替换"物品加工"这一步。
            //   命中条件 = 副手介质解析出的鼓风类型（fanType）与配方的 fan_type 相同。
            //   未命中（含"没拿介质"：此时 type 为 null，本方法根本不会被调用）时，
            //   原样回落到 Create 的鼓风逻辑（L1）—— 即 type.canProcess / type.process。
            //
            //   介质不消耗：与 L1 一致，本方法从不减少副手物品；
            //   加工成功只扣风箱自身 1 点耐久（见 releaseUsing 末尾）。
            List<ItemStack> results = null;
            FanType fanType = toFanType(type);
            if (fanType != null) {
                RecipeHolder<HandMadeBellowsRecipe> l3 = findL3BellowsRecipe(level, single, fanType);
                if (l3 != null) {
                    // 与 L1 的 haunting / splashing 一样带 returnProcessingRemainder=true
                    // （Create 的 blasting / smoking 传 false，那两族的 L1 配方是 vanilla cooking recipe，
                    //  本模组的 L3 一律是 ProcessingRecipe，按后者口径统一）。
                    results = RecipeApplier.applyRecipeOn(level, single, l3.value(), true);
                }
            }

            // L1 回落：真正的"能不能加工"仍由 Create 的 FanProcessingType 决定
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
                PacketDistributor.sendToPlayer(p, packet);
            }
        }
    }

    /**
     * 把 FanProcessingType 反查成 ID 字符串（供客户端还原类型用）。
     *
     * <p>直接查 Create 的 {@code FAN_PROCESSING_TYPE} 注册表（写法与 Create 自己的
     * {@code FanProcessing.java:98} 一致）—— 这样将来注册新类型时客户端同步自动可用，
     * 不需要再维护一张硬编码表。既有 4 种类型查到的是同一批 id
     * （{@code create:blasting} / {@code create:smoking} / {@code create:haunting} /
     * {@code create:splashing}），所以现有粒子与音效行为不变。</p>
     */
    private static String getTypeId(@Nullable FanProcessingType type) {
        if (type == null) return "";
        ResourceLocation key = CreateBuiltInRegistries.FAN_PROCESSING_TYPE.getKey(type);
        return key != null ? key.toString() : "";
    }
}