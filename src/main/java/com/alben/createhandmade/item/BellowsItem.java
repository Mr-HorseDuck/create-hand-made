package com.alben.createhandmade.item;

import com.alben.createhandmade.CreateHandMade;
import com.alben.createhandmade.ServerConfig;
import com.alben.createhandmade.bellows.BellowsMediaRegistry;
import com.alben.createhandmade.network.BellowsBlastPacket;
import com.alben.createhandmade.recipe.HandMadeBellowsRecipe;
import com.alben.createhandmade.recipe.HandMadeRecipeTypes;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.api.registry.CreateBuiltInRegistries;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.TransportedResult;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;
import com.simibubi.create.content.logistics.depot.DepotBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.item.render.SimpleCustomRenderer;
import com.simibubi.create.foundation.recipe.RecipeApplier;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
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

    // 蓄力门槛已移到 ServerConfig（tools.bellows.charge_ticks），运行时读 —— 见 releaseUsing()。
    // 不能进静态字段：SERVER 配置只在进入世界后加载，静态读取会抛 checkState。

    /** 推力基础强度（会按距离衰减） */
    private static final double PUSH_BASE = 5;

    /**
     * 射线相关的最大有效距离（格）。
     * 仅在计算"射线终点"和"射线实体检测"时使用；加工判定与爆发粒子不受此限制。
     */
    private static final long BELLOWS_RAY_MAX_DISTANCE_SQR = 64L * 64L;

    /**
     * 风箱喷嘴相对玩家胸口的局部坐标（右手臂视角）：
     *   X = 屏幕右为正
     *   Y = 上为正
     *   Z = 前为正
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
        // 风箱仅限主手使用（副手放介质）
        if (hand != InteractionHand.MAIN_HAND) {
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
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    // ================= 蓄力期间每 tick：提示音 + 持续吹风 =================

    /**
     * 蓄力期间每 tick 的回调。
     *
     * <p><b>这里做两件事：</b></p>
     * <ol>
     *   <li>蓄力刚满那一刻（{@code usedTicks == tools.bellows.charge_ticks}）在客户端播一声提示音，
     *       与冲压锤 {@code PressHammerItem#onUseTick} 同一套做法、同一个音效
     *       （{@link AllSoundEvents#CONFIRM}）。只在客户端播：这是给操作者本人的即时反馈，
     *       而服务端的 {@code playSound(null, …)} 是广播，会让附近所有人都听到。</li>
     *   <li><b>T11 B4：蓄满之后每 tick 对射线上的实体调一次 {@code type.affectEntity}</b> ——
     *       "持续吹风"语义。见 {@link #applySustainedEntityEffect}。</li>
     * </ol>
     */
    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remainingTicks) {
        if (!(entity instanceof Player player)) return;
        int usedTicks = getUseDuration(stack, entity) - remainingTicks;
        int chargeTicks = ServerConfig.INSTANCE.bellowsChargeTicks.get();

        if (usedTicks == chargeTicks && level.isClientSide) {
            player.playSound(AllSoundEvents.CONFIRM.getMainEvent(), 1.0f, 1.0f);
        }

        // ★ 蓄满才开始"吹风"；服务端独占（第三方类型的 affectEntity 基本都有 isClientSide 早退，
        //   但世界变换类副作用也只该在服务端跑一次）
        if (level.isClientSide || usedTicks < chargeTicks) return;
        // 蓄力期间不施加任何实体效果（全部改为松手击中的瞬间结算）
    }

    /**
     * 持续吹风：每 tick 对射线上的实体施加一次介质效果（T11 B4）。
     *
     * <h3>为什么必须每 tick 调</h3>
     * <p>Create 自己的 {@code AirCurrent} 就是<b>每 tick</b>对气流内实体调一次
     * {@code FanProcessingType#affectEntity}；很多第三方类型把"进度"累加在实体身上，只吹一次永远到不了终点。
     * 实测 Create: More Catalysts 正是这种：{@code FanEntityTransformHelper#advanceTransformation}
     * 把计数器写进实体 persistentData，每次调用 +1，累计到 <b>50</b> 才真正生成新生物
     * （所以"药水效果中了、转化没发生"——药水是单次 addEffect，转化需要 50 次）。</p>
     *
     * <p>起始时刻取"蓄力完成"，与"松手才爆发"的既有设计一致：按住不放 = 持续吹风，松手 = 一次冲击。</p>
     *
     * @param chargedTicks 蓄满之后又过了多少 tick（0 = 刚蓄满），仅用于日志节流
     */
    private static void applySustainedEntityEffect(Level level, Player player, int chargedTicks) {
        ItemStack media = player.getOffhandItem();
        if (media.isEmpty()) return;

        BellowsMediaRegistry.EffectiveTypes effective = BellowsMediaRegistry.resolveEffective(media);
        List<ResourceLocation> candidateIds = effective.ids();
        if (candidateIds.isEmpty()) return;

        FanProcessingType type = pickHighestPriority(candidateIds, effective.preferred());
        if (type == null) return;

        // 射线参数与 releaseUsing 保持同一套（喷嘴起点 / 视线终点 / 距离上限）
        double reach = player.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE);
        Vec3 eye = player.getEyePosition(1);
        Vec3 look = player.getViewVector(1);

        BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(look.scale(reach)),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        boolean hitBlock = hit.getType() == HitResult.Type.BLOCK;
        boolean posDistanceAbnormal = hitBlock
                && hit.getBlockPos().distSqr(player.blockPosition()) > BELLOWS_RAY_MAX_DISTANCE_SQR;
        Vec3 rayTarget = (hitBlock && !posDistanceAbnormal)
                ? Vec3.atCenterOf(hit.getBlockPos()).add(0, 0.5, 0)
                : eye.add(look.scale(reach));

        boolean rightArm = (player.getUsedItemHand() == InteractionHand.MAIN_HAND)
                == (player.getMainArm() == HumanoidArm.RIGHT);
        Vec3 origin = getItemMuzzlePos(player, rightArm, BELLOWS_MUZZLE_OFFSET);

        List<Entity> targets = findEntitiesInRay(level, player, origin, rayTarget);
        if (targets.isEmpty()) return;

        // 日志节流：每 10 tick 一条，够确认"真的被调用了"，又不至于把日志刷爆
        boolean logThisTick = chargedTicks == 0 || chargedTicks % 10 == 0;

        for (Entity e : targets) {
            // ★ 完全委托（T11 B3）：效果内容全在类型对象里，本模组不写死任何原版判断
            type.affectEntity(e, level);
            if (logThisTick) {
                CreateHandMade.LOGGER.debug("bellows: affectEntity({}) via {} (charged tick {})",
                        e.getType(), getTypeId(type), chargedTicks);
            }
        }
    }

    // ================= 松手 =================

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!(entity instanceof Player player)) return;

        int usedTicks = getUseDuration(stack, entity) - timeLeft;
        // 蓄力门槛读 SERVER 配置（运行时读 —— SERVER 配置只在进入世界后加载）
        if (usedTicks < ServerConfig.INSTANCE.bellowsChargeTicks.get()) return;
        if (level.isClientSide) return;

        // 副手介质：解析出「候选类型 id 列表」（T11 B2：可能不止一个 —— 装了多个鼓风扩展时，
        // 同一介质会同时关联到 dndesires:sanding / create_processing:sanding 等）。
        // 真正选哪个由 applyToTarget 按"能不能加工这一件 + 有效优先级"决定
        // （有效优先级 = 数据包 priority 覆盖值，没配才用 getPriority()，见 priorityOf；
        //   T11 B8 起配置里的 "item=type" 是"优先"而不是"独占"）；
        // 这里的 type 只服务于两件不需要具体物品的事：实体效果、广播里的类型名。
        ItemStack media = player.getOffhandItem();
        BellowsMediaRegistry.EffectiveTypes effective = media.isEmpty()
                ? new BellowsMediaRegistry.EffectiveTypes(List.of(), null)
                : BellowsMediaRegistry.resolveEffective(media);
        List<ResourceLocation> candidateIds = effective.ids();
        FanProcessingType type = pickHighestPriority(candidateIds, effective.preferred());
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
        // ★ 左右判据必须是"这条手臂在玩家的哪一侧"，而不是"在哪个槽位"：
        //   左手模式（mainArm = LEFT）下主手出现在屏幕左侧，只看槽位会把喷嘴放到反侧。
        boolean rightArm = (player.getUsedItemHand() == InteractionHand.MAIN_HAND)
                == (player.getMainArm() == HumanoidArm.RIGHT);
        Vec3 origin = getItemMuzzlePos(player, rightArm, BELLOWS_MUZZLE_OFFSET);

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
        FanProcessingType[] pickedType = {null};
        if (pos != null && !particleItems.isEmpty() && type != null) {
            processed = applyToTarget(level, pos, candidateIds, effective.preferred(), pickedType);
        }
        // ★ 有加工目标时，实体效果跟随"物品实际选中的类型"：
        //   否则会出现"物品没被加工、旁边的实体却中了另一类型的效果"（两条路径优先级算法不同）。
        FanProcessingType effectType = pickedType[0] != null ? pickedType[0] : type;
        typeId = getTypeId(effectType);

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
            // ★ 实体效果：先给"物品选中/最高优先级"的类型，再补一次"最高优先级类型"。
            //   介质同时被 CMC 和龙+ 认领时（沙子、龙首等），物品加工只能走其中一方；
            //   如果实体效果也只听物品选型，CMC 的生物转化就永远不触发 —— 所以补齐第二次。
            //   没有可做的事的类型是空操作，不会重复施加（见 applyEntityEffectNow 的 NBT 判据）。
            // 遍历全部候选类型：共用触媒时，物品加工只能走一方，另一方（CIE/CMC）的实体效果也要有机会生效
            for (ResourceLocation candidateId : candidateIds) {
                FanProcessingType candidateType = CreateBuiltInRegistries.FAN_PROCESSING_TYPE.get(candidateId);
                if (candidateType == null) continue;
                applyEntityEffectNow(candidateType, e, level);
                applyHardcodedEntityEffect(e, candidateId, level);
                if (e.isRemoved()) break;
            }
            // ★ 让"粒子型"鼓风（龙+ 的染色/喷砂就是这一类：效果挂在粒子/气流接触上，
            //   自己没有 affectEntity）也能作用到生物身上 —— 在实体位置补一次处理粒子。
            if (effectType != null) {
                effectType.spawnProcessingParticles(level, e.position());
            }
            if (!e.isRemoved() && type != null && type != effectType) {
                // （已由上面的候选遍历覆盖）
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
                                                                          ResourceLocation fanTypeId) {
        SingleRecipeInput input = new SingleRecipeInput(item);
        for (RecipeHolder<?> holder : level.getRecipeManager()
                .getAllRecipesFor(HandMadeRecipeTypes.BELLOWS_RECIPE.getType())) {
            if (!(holder.value() instanceof HandMadeBellowsRecipe recipe)) continue;
            // 两边都是类型 id（create:blasting 等）；配方侧 null 视为不匹配
            if (!fanTypeId.equals(recipe.getFanTypeId())) continue;
            if (!recipe.matches(input, level)) continue;
            return new RecipeHolder<>(holder.id(), recipe);
        }
        return null;
    }

    /**
     * 冲突触媒的<b>依次遍历</b>选型（T11 B9）：把所有候选按"有效优先级从高到低"排好，
     * 逐个尝试，<b>第一个能给出加工的类型胜出</b>。
     *
     * <p>每个候选的"能用"判定（两者任一即算能用）：</p>
     * <ul>
     *   <li><b>L3 独占配方命中</b>（{@code create_hand_made:bellows_recipe}，走本模组的配方）；</li>
     *   <li><b>L1 {@code canProcess} 为真</b>（第三方类型自己的配方表认得这件物品）。</li>
     * </ul>
     *
     * <p><b>为什么必须"先筛能用、再排优先级"</b>：排在前面的类型若吃不下这一件，
     * 就应该让位给后面的 —— 否则一个优先级虚高（CMC 动辄 2000）却毫无配方的类型会永久霸占
     * 龙首、细雪这类多模组共用的触媒，把龙+ / DnD 全部饿死。</p>
     *
     * <p><b>不需要"加工失败后回退"</b>：{@code process()} 是一次性把输入变成输出，
     * 只要它能吃下就必然产出；选型阶段已经用 L3/canProcess 把"吃不下的"排除干净了。</p>
     *
     * <p>排序用 {@link Integer#compare} 降序 + 稳定排序（{@link List#sort} 是稳定的），
     * 所以同优先级时保持候选原始顺序（数据包在前、自动发现补后），结果可复现。</p>
     *
     * @param preferred 配置里指定的优先类型 id（{@code null} = 没配）；它直接拿 {@code Integer.MAX_VALUE}
     * @return 选中的 {@code (类型对象, 类型 id, 命中的 L3 配方)}；候选都吃不下则 null
     */
    @Nullable
    private static BestMediaType selectBestType(Level level, ItemStack single,
                                                List<ResourceLocation> candidateIds,
                                                @Nullable ResourceLocation preferred) {
        // ① 先算出每个候选的"能用"与优先级，排成一张表（用记录而不是两个平行 list，少一处错位风险）
        List<Candidate> usable = new ArrayList<>();
        for (ResourceLocation id : candidateIds) {
            FanProcessingType candidate = CreateBuiltInRegistries.FAN_PROCESSING_TYPE.get(id);
            if (candidate == null) continue;

            RecipeHolder<HandMadeBellowsRecipe> l3 = findL3BellowsRecipe(level, single, id);
            boolean canProcess = l3 == null && candidate.canProcess(single, level);
            if (l3 == null && !canProcess) continue;   // 吃不下 ⇒ 让位给后面的候选

            int priority = priorityOf(id, candidate, preferred);
            usable.add(new Candidate(candidate, id, l3, priority, l3 != null));
        }
        if (usable.isEmpty()) return null;

        // ② 有效优先级从高到低（稳定排序：同优先级保持候选顺序）
        usable.sort((a, b) -> Integer.compare(b.priority(), a.priority()));

        if (CreateHandMade.LOGGER.isDebugEnabled()) {
            List<String> order = new ArrayList<>(usable.size());
            for (Candidate c : usable) {
                order.add(c.id() + (c.viaL3() ? "(L3)" : "(L1)") + ":" + c.priority());
            }
            CreateHandMade.LOGGER.debug("bellows: candidates for {} -> {} (pick first)", single.getItem(), order);
        }

        // ③ 依次遍历：第一个能用的就是答案（L3 优先的语义已经体现在"能用"判定里）
        Candidate pick = usable.get(0);
        return new BestMediaType(pick.type(), pick.id(), pick.l3Recipe());
    }

    /** {@link #selectBestType} 的中间表：候选能用的理由 + 有效优先级。 */
    private record Candidate(FanProcessingType type, ResourceLocation id,
                             @Nullable RecipeHolder<HandMadeBellowsRecipe> l3Recipe,
                             int priority, boolean viaL3) {}

    /** {@link #selectBestType} 的结果：类型对象 + 类型 id + 命中的 L3 配方（可能为 null）。 */
    private record BestMediaType(FanProcessingType type, ResourceLocation id,
                                 @Nullable RecipeHolder<HandMadeBellowsRecipe> l3Recipe) {}

    /**
     * 取一个类型的<b>有效</b>优先级（T11 B4，T11 B8 加了"配置优先"）。
     *
     * <p>三级取值，先命中先用：</p>
     * <ol>
     *   <li><b>配置优先</b>（{@code bellows_type_overrides} 里的 {@code item=type}）：
     *       直接给 {@link Integer#MAX_VALUE}，于是它在所有候选里必然胜出 ——
     *       但注意它仍要<b>先通过"能不能加工这一件"</b>的筛选，所以吃不下时会自然回落到别的类型；</li>
     *   <li><b>数据包 priority</b>：配了就完全无视 {@code getPriority()}（不叠加、不比较）；</li>
     *   <li>否则用类型自己的 {@code getPriority()}。</li>
     * </ol>
     *
     * <p>多模组共存时 {@code getPriority()} 的尺度完全不统一（CMC 的凋零写死 2000，
     * DnD 的冷冻 1100 / 超级冶炼 1200，Create 自带的是 200~400 量级），没有这层覆盖就只能永远
     * 触发数值最大的那个。</p>
     */
    private static int priorityOf(ResourceLocation id, FanProcessingType type,
                                  @Nullable ResourceLocation preferred) {
        if (preferred != null && preferred.equals(id)) return Integer.MAX_VALUE;
        Integer typeOverride = BellowsMediaRegistry.typePriorityOverride(id);
        if (typeOverride != null) return typeOverride;
        Integer override = BellowsMediaRegistry.priorityOverride(id);
        return override != null ? override : type.getPriority();
    }

    /**
     * 只需要"一个类型"、没有具体物品可判定的场合（实体效果 / 广播的类型名）用：
     * 直接取候选里优先级最高的可解析类型。实体没有"配方能不能吃"这一说，所以不筛 canProcess。
     */
    @Nullable
    private static FanProcessingType pickHighestPriority(List<ResourceLocation> candidateIds,
                                                        @Nullable ResourceLocation preferred) {
        FanProcessingType best = null;
        int bestPriority = Integer.MIN_VALUE;
        for (ResourceLocation id : candidateIds) {
            FanProcessingType candidate = CreateBuiltInRegistries.FAN_PROCESSING_TYPE.get(id);
            if (candidate == null) continue;
            int priority = priorityOf(id, candidate, preferred);
            if (best == null || priority > bestPriority) {
                best = candidate;
                bestPriority = priority;
            }
        }
        return best;
    }

    private static boolean applyToTarget(Level level, BlockPos pos, List<ResourceLocation> candidateIds,
                                         @Nullable ResourceLocation preferred,
                                         FanProcessingType[] pickedOut) {
        TransportedItemStackHandlerBehaviour handler =
                BlockEntityBehaviour.get(level, pos, TransportedItemStackHandlerBehaviour.TYPE);
        if (handler == null) return false;

        boolean[] success = {false};
        // 诊断日志节流用（数组元素可变，"没匹配上"的日志最多 5 秒一条）
        long[] lastNoMatchLogTick = {Long.MIN_VALUE / 2};

        handler.handleProcessingOnAllItems(item -> {
            if (success[0]) return TransportedResult.doNothing();
            ItemStack stack = item.stack;
            if (stack.isEmpty()) return TransportedResult.doNothing();

            ItemStack single = stack.copyWithCount(1);

            // ★ T11 B2：这一件物品可能同时被多个类型"认领"（沙子：dndesires:sanding /
            //   create_processing:sanding / create_more_catalysts:sand_blow …）。
            //   先筛"能不能加工这一件"，再取优先级最高的那个 —— 同一时间只触发一个类型。
            //
            //   选定后仍是 L3 优先：独占配方（create_hand_made:bellows_recipe）只替换"物品加工"这一步；
            //   L3 不存在时回落到 L1 的 type.process()。
            //   介质不消耗：与 L1 一致，本方法从不减少副手物品；
            //   加工成功只扣风箱自身 1 点耐久（见 releaseUsing 末尾）。
            BestMediaType best = selectBestType(level, single, candidateIds, preferred);
            if (best == null) {
                // ★ T11 B8 诊断：介质认得出、但没有任何类型吃下这一件时，给一条可排查的日志。
                //   节流到 5 秒一次，避免玩家连点时刷屏。
                long now = level.getGameTime();
                if (now - lastNoMatchLogTick[0] >= 100) {
                    lastNoMatchLogTick[0] = now;
                    CreateHandMade.LOGGER.info(
                            "bellows: no fan type could process {} (media candidates = {}, preferred = {})",
                            single.getItem(), candidateIds, preferred);
                }
                return TransportedResult.doNothing();
            }

            List<ItemStack> results = null;
            if (best.l3Recipe() != null) {                // 与 L1 的 haunting / splashing 一样带 returnProcessingRemainder=true
                // （Create 的 blasting / smoking 传 false，那两族的 L1 配方是 vanilla cooking recipe，
                //  本模组的 L3 一律是 ProcessingRecipe，按后者口径统一）。
                results = RecipeApplier.applyRecipeOn(level, single, best.l3Recipe().value(), true);
            }

            // L1：真正的"能不能加工"已由 selectBestType 用 canProcess 判过
            if (results == null) {
                results = best.type().process(single, level);
                if (results == null) return TransportedResult.doNothing();
            }
            // 记录"实际选中的类型"，供松手时的实体效果复用（保证两条路径选型一致）
            pickedOut[0] = best.type();
            // ★ 去掉"输入容器的空壳"（例如龙+ 冷冻把水桶冻成细雪桶时附带的空桶）
            results = dropContainerRemainder(single, results);

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

            best.type().spawnProcessingParticles(level, handler.getWorldPositionOf(item));

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

    /**
     * 过滤掉"输入容器的空壳"：第三方鼓风类型常常把被倒空的容器（空桶）也算进产物
     * （龙+ 冷冻：水桶 → 细雪桶 + 空桶）。风箱是一次性加工，玩家看来就是凭空多出个空桶。
     *
     * <p>只在<b>产物里还有别的东西</b>时才丢，避免把"桶本身变成空桶"这种正常配方也吃掉。</p>
     */
    private static List<ItemStack> dropContainerRemainder(ItemStack input, List<ItemStack> results) {
        if (results == null || results.size() < 2) return results;

        ItemStack remainder = input.getCraftingRemainingItem();
        if (remainder.isEmpty()) return results;

        List<ItemStack> filtered = new ArrayList<>(results.size());
        boolean dropped = false;
        for (ItemStack stack : results) {
            if (!dropped && !stack.isEmpty() && ItemStack.isSameItem(stack, remainder)) {
                dropped = true;
                continue;
            }
            filtered.add(stack);
        }
        if (dropped && !filtered.isEmpty()) {
            if (CreateHandMade.LOGGER.isDebugEnabled()) {
                CreateHandMade.LOGGER.debug("bellows: dropped container remainder {} from results of {}",
                        remainder.getItem(), input.getItem());
            }
            return filtered;
        }
        return results;
    }

    /**
     * 击中瞬间结算实体效果（用户要求：转化在击中那一刻完成，而不是持续吹）。
     *
     * <p>CMC 这类模组的转化是"每次调用 +1、累计 50 次才换生物"（进度存在实体 persistentData 里）。
     * 这里用<b>快进</b>模拟"一次吹够"：反复调用 {@code affectEntity}，
     * 每轮比较实体 persistentData 是否变化 ——</p>
     * <ul>
     *   <li><b>变了</b>（转化进度在推进）⇒ 继续调用，最多 64 轮；</li>
     *   <li><b>没变</b>（治疗 / 药水 / 空实现）⇒ 说明这次调用已生效且不再有"进度"可推，
     *       立刻停止，避免把治疗效果刷 50 次；</li>
     *   <li>实体被移除/死亡（转化完成，旧实体被 discard）⇒ 停止。</li>
     * </ul>
     * <p>进度键是对方私有的，所以只看"NBT 有没有变"这种与实现无关的判据。</p>
     */
    private static void applyEntityEffectNow(@Nullable FanProcessingType type, Entity entity, Level level) {
        if (type == null) return;
        for (int i = 0; i < 64; i++) {
            CompoundTag before = entity.getPersistentData().copy();
            type.affectEntity(entity, level);

            if (!entity.isAlive() || entity.isRemoved()) return;        // 转化完成
            if (before.equals(entity.getPersistentData())) return;      // 没有进度可推，只算一次
        }
    }

    /**
     * 写死的实体效果补充（T11 收尾）：龙+ 的染色/喷砂对生物不生效，因为它们的染色
     * 走的是 Create 的 AirCurrent 气流 Mixin（{@code AirCurrentMixin}
     * {@code #tickAffectedEntities$spawnProcessingParticlesWithParticleData}），
     * 而风箱并不产生气流。这里按"染料变体名"直接给可染色实体上色，等价复刻其可见结果。
     */
    private static void applyHardcodedEntityEffect(Entity entity, ResourceLocation typeId, Level level) {
        // —— 更多触媒（CMC）的特殊实体效果：上游只对真实气流生效，风箱这里写死复刻 ——
        if (typeId.getNamespace().equals("create_more_catalysts")) {
            entity.invulnerableTime = 0;   // 绕过受击无敌帧：每次鼓风都吃满这一下
            switch (typeId.getPath()) {
                case "exploding" -> {
                    entity.invulnerableTime = 0;   // 绕过受击无敌帧，保证每次鼓风都能吃满这一下
                    level.explode(entity, entity.getX(), entity.getY(), entity.getZ(),
                            1.5f, Level.ExplosionInteraction.NONE);
                }
                case "snow_flow" -> {
                    entity.setTicksFrozen(entity.getTicksRequiredToFreeze() + 40);
                    entity.setIsInPowderSnow(true);   // 触发细雪覆盖层与减速表现
                }
                default -> {
                }
            }
        }
        if (!typeId.getNamespace().equals("create_dragons_plus")) return;
        String path = typeId.getPath();
        if (!path.startsWith("coloring_")) return;

        DyeColor color = DyeColor.byName(path.substring("coloring_".length()), null);
        if (color == null) return;
        // 原版可染色实体一共三类：羊（羊毛）+ 猫/狼（项圈）
        if (entity instanceof Sheep sheep) {
            sheep.setColor(color);
        } else {
            // 猫 / 狼的项圈色：1.21.1 里 setCollarColor 是 private，只能反射调用
            setCollarColorReflectively(entity, color);
        }
    }

    /** 反射设置猫/狼的项圈颜色（1.21.1 的 setCollarColor 是 private）。 */
    private static void setCollarColorReflectively(Entity entity, DyeColor color) {
        if (!(entity instanceof Cat) && !(entity instanceof Wolf)) return;
        for (Class<?> c = entity.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                var m = c.getDeclaredMethod("setCollarColor", DyeColor.class);
                m.setAccessible(true);
                m.invoke(entity, color);
                return;
            } catch (NoSuchMethodException ignored) {
                // 继续往上找
            } catch (Throwable ignored) {
                return;
            }
        }
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