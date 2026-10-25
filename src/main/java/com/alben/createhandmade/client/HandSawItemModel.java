package com.alben.createhandmade.client;

import com.alben.createhandmade.ServerConfig;
import com.alben.createhandmade.util.StickyUseSession;
import com.google.common.base.Suppliers;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.dispatch.BlockModelRotation;
import net.minecraft.client.renderer.item.CuboidItemModelWrapper;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.ModelRenderProperties;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.client.resources.model.geometry.QuadCollection;
import net.minecraft.client.resources.model.sprite.TextureSlots;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * 手锯的物品模型：<b>副手原料预览 + 锯子本体</b> 两个图层，并在第一人称播放
 * 「起手伸出 → 振动切削」/「潜行左键砍树」两套动画。
 *
 * <h3>为什么不是普通 JSON 模型</h3>
 * <p>旧版是 Create 的 {@code CustomRenderedItemModelRenderer}（26.1 的 Create Fly 已经删掉这套 API）。
 * 它需要在一个渲染 pass 里额外画一件<b>别的物品</b>（副手的原料），普通 JSON 模型做不到，
 * 所以这里照 {@link MortarItemModel} 的做法注册自定义 {@code ItemModel} 类型
 * （{@code create_hand_made:hand_saw}），往 {@link ItemStackRenderState} 里塞多个图层。</p>
 *
 * <h3>坐标系（与研钵同一条共轭公式）</h3>
 * <p>旧管线：{@code ArmPose · Display · M · T(-0.5)}（{@code M} = 渲染器自己做的位移/旋转）。</p>
 * <p>新管线：{@code ArmPose · Display · T(-0.5) · localTransform}。</p>
 * <p>⇒ {@code localTransform = T(+0.5) · M · T(-0.5)}。这样旧版实测调好的
 * {@code SAW_TARGET_*} / {@code OFFHAND_*} 全部可以原样搬过来。</p>
 *
 * <h3>副手预览层为什么<b>不</b>补尾部 T(-0.5)</h3>
 * <p>预览层不是四边形，而是 {@link SpecialModelRenderer}：它在 {@code submit} 里现搭一个
 * {@code ItemStackRenderState}（{@code ItemDisplayContext.FIXED}），而 FIXED 变换<b>自带</b>
 * 收尾的 {@code T(-0.5)}。旧版是 {@code Display · M_off · Fixed · T(-0.5)}，
 * 新版是 {@code Display · T(-0.5) · L_off · Fixed · T(-0.5)} ⇒ {@code L_off = T(+0.5) · M_off}，
 * 尾部那个 {@code T(-0.5)} 由 FIXED 自己补。</p>
 *
 * <h3>只在第一人称动</h3>
 * <p>GUI / 第三人称 / 掉落物都是普通静态外观（只走锯子本体一层），不读会话状态 ——
 * 这样图标永远干净，也不必在没有玩家上下文时碰 {@code ServerConfig}。</p>
 */
public final class HandSawItemModel implements ItemModel {

    /** 模型类型 id（{@code items/hand_saw.json} 里写 {@code "type": "create_hand_made:hand_saw"}）。 */
    public static final Identifier TYPE_ID = Identifier.fromNamespaceAndPath("create_hand_made", "hand_saw");

    /** 砍树姿态：从 0 到 1 的伸出过程所需 tick（渲染器独有，不进 config）。 */
    private static final float FELL_WINDUP = 5f;

    // ================= 副手物品：独立玩家空间坐标 =================
    private static final float OFFHAND_START_X = -0.5f;
    private static final float OFFHAND_START_Y = -0.55f;
    /** 实测定稿：起点 Z 从 0.15 改为 1.0（起点落在副手侧画面外）。 */
    private static final float OFFHAND_START_Z = 1.0f;

    private static final float OFFHAND_TARGET_X = -0.4f;
    private static final float OFFHAND_TARGET_Y = 0.12f;
    private static final float OFFHAND_TARGET_Z = 0.6f;

    private static final float OFFHAND_SCALE = 0.4f;
    private static final float OFFHAND_YAW = 90f;
    /** 振动阶段副手物品的微颤幅度。 */
    private static final float OFFHAND_SHAKE = 0.02f;

    // ================= 锯子目标位置 =================
    private static final float SAW_TARGET_X = -1f;
    private static final float SAW_TARGET_Y = 0.45f;
    private static final float SAW_TARGET_Z = 0.45f;
    /** 振动阶段锯子的摆动幅度。 */
    private static final float SAW_SWING = 0.1f;
    /** 起手阶段完成后锯子沿 YP 轴旋转的角度。 */
    private static final float SAW_ROTATE_Y = 90f;
    private static final float SAW_ROTATE_Z = 90f;

    /** 每个玩家「开始砍树姿态」时的 tickCount，用于做渐进。 */
    private static final Map<UUID, Integer> FELL_START_TICK = new HashMap<>();

    // ===================== 实例状态（bake 出来的模型数据） =====================

    private final QuadCollection quads;
    private final ModelRenderProperties properties;
    private final Matrix4fc transformation;
    private final Supplier<Vector3fc[]> extents;

    private HandSawItemModel(QuadCollection quads, ModelRenderProperties properties, Matrix4fc transformation) {
        this.quads = quads;
        this.properties = properties;
        this.transformation = transformation;
        this.extents = Suppliers.memoize(() -> CuboidItemModelWrapper.computeExtents(quads.getAll()));
    }

    // ===================== 每帧装填渲染数据 =====================

    @Override
    public void update(ItemStackRenderState output, ItemStack item, ItemModelResolver resolver,
                       ItemDisplayContext displayContext, @Nullable ClientLevel level,
                       @Nullable ItemOwner owner, int seed) {
        output.appendModelIdentityElement(this);

        boolean leftHand = displayContext == ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
        int modifier = leftHand ? -1 : 1;

        Anim anim = computeAnim(item, displayContext, owner);
        if (anim.active()) {
            // 每帧都会变（起手进度 / 振动相位）⇒ 告诉渲染状态不要缓存
            output.setAnimated();
        }

        // 见类注释「坐标系」：先把原点从贴图中心搬回左下角，各图层末尾再搬回去。
        Matrix4f base = new Matrix4f(this.transformation).translate(0.5f, 0.5f, 0.5f);

        // ---- 图层 1：副手原料预览（切削中才画） ----
        if (anim.cutting() && !anim.offhand().isEmpty()) {
            float move = anim.move();
            float vibrate = anim.vibrate();

            // ★ 实测定稿（左手逐位对照右手）：X 不镜像、Y 不镜像、Z 镜像、YAW 镜像
            //   与锯子本体同一条规律：Z 分量镜像、X/Y 不镜像
            float startZ = OFFHAND_START_Z * modifier;
            float targetZ = OFFHAND_TARGET_Z * modifier;

            // ★ 位移用 moveProgress，振动只叠加微颤
            float tx = OFFHAND_START_X + (OFFHAND_TARGET_X - OFFHAND_START_X) * move;
            float ty = OFFHAND_START_Y + (OFFHAND_TARGET_Y - OFFHAND_START_Y) * move;
            float tz = startZ + (targetZ - startZ) * move;

            float shake = (float) Math.sin(anim.tickCount()) * OFFHAND_SHAKE * vibrate;
            tx += shake;
            ty += shake;

            // 注意：**不要**补 T(-0.5) —— FIXED 变换自带（见类注释）
            Matrix4f offhandMatrix = new Matrix4f(base)
                    .translate(tx, ty, tz)
                    .scale(OFFHAND_SCALE)
                    .rotateY(degrees(OFFHAND_YAW * modifier));

            ItemStackRenderState.LayerRenderState offhandLayer = output.newLayer();
            offhandLayer.setLocalTransform(offhandMatrix);
            this.properties.applyToLayer(offhandLayer, displayContext);
            offhandLayer.setupSpecialModel(FixedItemRenderer.INSTANCE, anim.offhand());
        }

        // ---- 图层 2：锯子本体 ----
        Matrix4f body = new Matrix4f(base);
        if (anim.fell() > 0f) {
            float fell = anim.fell();
            // 实测确定（与切削同规律）：X/Y 不镜像、Z 镜像
            body.translate(0.1f * fell, -0.15f * fell, modifier * -1f * fell);
        } else if (anim.cutting()) {
            float move = anim.move();
            float vibrate = anim.vibrate();

            // ★ 位移阶段：从原位滑到目标（X 不镜像、Y 不镜像、Z 镜像）
            float sawX = SAW_TARGET_X * move;
            float sawY = SAW_TARGET_Y * move;
            float sawZ = SAW_TARGET_Z * modifier * move;

            // ★ 振动阶段：叠加摆动（Y 不镜像、Z 镜像）
            float swing = (float) Math.sin(anim.tickCount()) * SAW_SWING * vibrate;
            sawY += swing;
            sawZ += swing * modifier;

            body.translate(sawX, sawY, sawZ);
            // ★ 起手完成后：沿 YP 轴旋转 90°（用 moveProgress 做平滑过渡）
            //   实测定稿：YP 乘 modifier、ZP 不乘
            body.rotateY(degrees(SAW_ROTATE_Y * modifier * move));
            body.rotateZ(degrees(SAW_ROTATE_Z * move));
        }
        body.translate(-0.5f, -0.5f, -0.5f);

        ItemStackRenderState.LayerRenderState bodyLayer = output.newLayer();
        bodyLayer.setExtents(this.extents);
        bodyLayer.setLocalTransform(body);
        this.properties.applyToLayer(bodyLayer, displayContext);
        bodyLayer.prepareQuadList().addAll(this.quads.getAll());
        if (item.hasFoil()) {
            bodyLayer.setFoilType(ItemStackRenderState.FoilType.STANDARD);
            output.setAnimated();
        }
    }

    private static float degrees(float value) {
        return (float) Math.toRadians(value);
    }

    // ===================== 动画相位 =====================

    /**
     * 一帧的动画状态。
     *
     * @param active   本帧是否在动（决定要不要 setAnimated）
     * @param cutting  是否在切削（= 在会话内，含起手）
     * @param move     起手位移进度 [0,1]
     * @param vibrate  振动渐入 [0,1]（起手结束后 3 tick 拉满）
     * @param fell     砍树姿态进度 [0,1]
     * @param tickCount 玩家 tickCount（做 sin 微颤用，旧版就是直接用整 tick）
     * @param offhand  副手原料（仅 cutting 时有意义）
     */
    private record Anim(boolean active, boolean cutting, float move, float vibrate, float fell,
                        int tickCount, ItemStack offhand) {
        static final Anim NONE = new Anim(false, false, 0f, 0f, 0f, 0, ItemStack.EMPTY);
    }

    private static Anim computeAnim(ItemStack item, ItemDisplayContext displayContext,
                                    @Nullable ItemOwner owner) {
        boolean firstPerson = displayContext == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                || displayContext == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
        if (!firstPerson || owner == null) {
            return Anim.NONE;
        }
        LivingEntity living = owner.asLivingEntity();
        if (!(living instanceof Player player)) {
            return Anim.NONE;
        }

        // ★ 手锯只在主手使用（副手是供料位），所以手别恒为 MAIN_HAND ——
        //   这样副手拿着的第二把手锯不会跟着动。
        int elapsed = StickyUseSession.elapsedTicks(player, item, InteractionHand.MAIN_HAND,
                StickyUseSession.DEFAULT_STICKY_TICKS);

        if (elapsed >= 0) {
            // 起手时长读 SERVER 配置（运行时读，静态字段会在配置未加载时抛异常）
            float windupTicks = Math.max(1, ServerConfig.INSTANCE.handSawWindupTicks.get());
            float move = Math.min(1f, elapsed / windupTicks);
            // ★ 振动渐入包络（3 tick 内进入满幅，之后保持满幅）
            float afterMove = Math.max(0f, elapsed - windupTicks);
            float vibrate = Math.min(1f, afterMove / 3f);
            return new Anim(true, true, move, vibrate, 0f, player.tickCount, player.getOffhandItem());
        }

        // ---- 未使用：潜行 + 按住左键 = 砍树姿态 ----
        boolean shouldFell = player.isShiftKeyDown()
                && Minecraft.getInstance().options.keyAttack.isDown()
                && ItemStack.isSameItemSameComponents(player.getMainHandItem(), item);
        if (!shouldFell) {
            FELL_START_TICK.remove(player.getUUID());
            return Anim.NONE;
        }

        Integer previous = FELL_START_TICK.putIfAbsent(player.getUUID(), player.tickCount);
        int startTick = previous != null ? previous : player.tickCount;
        float fell = Math.min(1f, (player.tickCount - startTick) / FELL_WINDUP);
        return new Anim(true, false, 0f, 0f, fell, player.tickCount, ItemStack.EMPTY);
    }

    // ===================== JSON 类型 =====================

    /**
     * {@code assets/create_hand_made/items/hand_saw.json}：
     *
     * <pre>
     * {
     *   "model": {
     *     "type": "create_hand_made:hand_saw",
     *     "model": "create_hand_made:item/hand_saw"
     *   }
     * }
     * </pre>
     */
    public record Unbaked(Identifier model) implements ItemModel.Unbaked {

        public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Identifier.CODEC.fieldOf("model").forGetter(Unbaked::model)
        ).apply(instance, Unbaked::new));

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }

        @Override
        public void resolveDependencies(ResolvableModel.Resolver resolver) {
            resolver.markDependency(this.model);
        }

        @Override
        public ItemModel bake(ItemModel.BakingContext context, Matrix4fc transformation) {
            ModelBaker baker = context.blockModelBaker();

            ResolvedModel resolved = baker.getModel(this.model);
            TextureSlots slots = resolved.getTopTextureSlots();
            QuadCollection quads = resolved.bakeTopGeometry(slots, baker, BlockModelRotation.IDENTITY);
            ModelRenderProperties properties = ModelRenderProperties.fromResolvedModel(baker, resolved, slots);

            return new HandSawItemModel(quads, properties, new Matrix4f(transformation));
        }
    }
}
