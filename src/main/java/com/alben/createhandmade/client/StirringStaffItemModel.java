package com.alben.createhandmade.client;

import com.alben.createhandmade.ServerConfig;
import com.alben.createhandmade.util.StickyUseSession;
import com.google.common.base.Suppliers;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
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

import java.util.function.Supplier;

/**
 * 搅拌杖的物品模型：<b>起手（转正 + 前伸）</b> + <b>加工（绕盆心画圆的搅拌运动）</b>两阶段。
 *
 * <h3>为什么不是普通 JSON 模型</h3>
 * <p>旧版是 Create 的 {@code CustomRenderedItemModelRenderer}（26.1 的 Create Fly 已删掉这套 API）。
 * 动画需要按使用进度改写 {@code localTransform}，普通 JSON 模型做不到，所以注册自定义
 * {@code ItemModel} 类型（{@code create_hand_made:stirring_staff}）。</p>
 *
 * <h3>计时</h3>
 * <p>起手进度用会话 tick（{@link StickyUseSession}）—— 连续搅拌只在第一次长按起手；加工相位用
 * 「会话加工 tick」= 会话 tick − 起手 ⇒ 跨自动续用连续。</p>
 *
 * <h3>坐标系</h3>
 * <p>与手锯<b>完全一致</b> ⇒ 旧版实测调好的数值可以直接搬：{@code item/handheld} 的第一人称变换
 * 相同，且渲染器都不做逆旋转。换算仍是那条共轭公式：</p>
 * <pre>
 * 旧：Display · M · T(-0.5)
 * 新：Display · T(-0.5) · localTransform
 * ⇒ localTransform = T(+0.5) · M · T(-0.5)
 * </pre>
 *
 * <h3>可调项（当前值均为实机定稿）</h3>
 * <ul>
 *   <li>位姿：{@link #POS_X}（前伸）、{@link #POS_Y}（高度）、{@link #POS_Z}（居中，乘 {@code modifier}）。</li>
 *   <li>搅拌圆：{@link #STIR_CIRCLE_RADIUS}、{@link #STIR_CIRCLE_TARGET_TICKS}（20t ≈ 1 圈/秒）、
 *       {@link #STIR_CIRCLE_MIN_PERIOD_TICKS}、{@link #STIR_CIRCLE_FADE_IN_TICKS}。</li>
 *   <li>旋转：见 {@code update()} 里 6 次 {@code rotate*} 前的注释（三明治自转 + XP/ZP/YP 收尾）。</li>
 * </ul>
 */
public final class StirringStaffItemModel implements ItemModel {

    /** 模型类型 id（{@code items/stirring_staff.json} 里写 {@code "type": "create_hand_made:stirring_staff"}）。 */
    public static final Identifier TYPE_ID =
            Identifier.fromNamespaceAndPath("create_hand_made", "stirring_staff");

    // ================= 加工位（起手终点）——实机微调入口 =================

    /** 前伸量（局部 −X = 往前下：0.906 前 / 0.423 下）——实机定稿。 */
    private static final float POS_X = -0.8f;

    /** 高度（局部 +Y = 往上前）——实机定稿。 */
    private static final float POS_Y = 0.80f;

    /** 左右居中量（乘 modifier：局部 +Z = 往左，把物品从身体一侧挪到正前方）——实机定稿。 */
    private static final float POS_Z = 0.8f;

    // ================= 加工段：搅拌圆 ——实机微调入口 =================

    /** 搅拌圆半径（模型格）——实机定稿。 */
    private static final float STIR_CIRCLE_RADIUS = 0.1f;

    /**
     * 目标转速：一圈约多少 tick（20t ≈ 1 圈/秒）——用于把 {@code processing_ticks} 换算成整数圈。
     * <p>要改成严格的「一件一圈」：把 {@code update()} 里的 {@code revolutions} 直接写死 {@code 1f}。</p>
     */
    private static final float STIR_CIRCLE_TARGET_TICKS = 20f;

    /** 单圈周期下限：兜住 {@code processing_ticks ≤ 5} 的极端配置，避免高速旋转糊成一片。 */
    private static final float STIR_CIRCLE_MIN_PERIOD_TICKS = 5f;

    /** 圆运动渐入包络：起手完成后多少 tick 进入满幅（与手锯的 3t 一致，避免阶跃）。 */
    private static final float STIR_CIRCLE_FADE_IN_TICKS = 3f;

    // ===================== 实例状态（bake 出来的模型数据） =====================

    private final QuadCollection quads;
    private final ModelRenderProperties properties;
    private final Matrix4fc transformation;
    private final Supplier<Vector3fc[]> extents;

    private StirringStaffItemModel(QuadCollection quads, ModelRenderProperties properties,
                                   Matrix4fc transformation) {
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
        boolean firstPerson = leftHand || displayContext == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
        int modifier = leftHand ? -1 : 1;

        Anim anim = computeAnim(item, displayContext, owner);
        if (firstPerson) {
            // 每帧都会变（起手进度 / 搅拌相位）⇒ 告诉渲染状态不要缓存
            output.setAnimated();
        }

        // 见类注释「坐标系」：先把原点从贴图中心搬回左下角，末尾再搬回去
        Matrix4f base = new Matrix4f(this.transformation).translate(0.5f, 0.5f, 0.5f);

        if (anim.active()) {
            float progress = anim.progress();
            float stirProgress = anim.stirProgress();
            int processingTicks = anim.processingTicks();
            int stirTicks = anim.stirTicks();

            // 搅拌圆：周期 = processing_ticks，但换算成「整数圈 / 件」：相位在每件边界回到 0（不跳变），
            // 转速则被钉在 ~1 圈/秒（STIR_CIRCLE_TARGET_TICKS），规避「processing 很长 ⇒ 一圈慢成静止」。
            float revolutions = Math.max(1f, Math.round(processingTicks / STIR_CIRCLE_TARGET_TICKS));
            float circlePeriod = Math.max(STIR_CIRCLE_MIN_PERIOD_TICKS, processingTicks / revolutions);
            // 相位用「会话加工 tick」⇒ 跨自动续用连续，不会每件归零
            float circlePhase = (stirTicks % circlePeriod) / circlePeriod;
            float circleAngle = circlePhase * (float) (Math.PI * 2.0);
            float circleX = (float) Math.sin(circleAngle) * STIR_CIRCLE_RADIUS * stirProgress;
            float circleZ = (float) Math.cos(circleAngle) * STIR_CIRCLE_RADIUS * stirProgress * modifier;

            // 位移：起手插值到正前方基准位（POS_*），再叠加圆运动偏移。
            // 镜像（实机定稿）：基准 POS_X / POS_Y 不乘 modifier、POS_Z 乘 modifier；
            //                  圆偏移 X 乘 −modifier、Z 乘 modifier。
            base.translate(POS_X * progress + circleX * (-modifier),
                    POS_Y * progress,
                    modifier * POS_Z * progress + circleZ);

            // 旋转（实机定稿，共 6 次 rotate*，乘序即书写顺序；全部乘 progress 做起手插值）：
            //   1) ZP(45) · YP(−90·modifier) · ZP(−45)：三明治写法，等价于绕「板面内对角轴」
            //      (−0.707, +0.707, 0) 自转 −90°·modifier —— 把杖头从贴图对角线方向转出来；
            //   2) XP(−90·modifier) · ZP(135) · YP(−90·modifier)：把杖头摆成「斜指屏幕前方」。
            // 镜像：两次 YP 与一次 XP 乘 modifier；两次 ZP 不乘。
            base.rotateZ(degrees(45f * progress));
            base.rotateY(degrees(-90f * modifier * progress));
            base.rotateZ(degrees(-45f * progress));

            base.rotateX(degrees(-90f * modifier * progress));
            base.rotateZ(degrees(135f * progress));
            base.rotateY(degrees(-90f * modifier * progress));
        }

        base.translate(-0.5f, -0.5f, -0.5f);

        ItemStackRenderState.LayerRenderState bodyLayer = output.newLayer();
        bodyLayer.setExtents(this.extents);
        bodyLayer.setLocalTransform(base);
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
     * @param active          是否应用搅拌姿势（起手进度 &gt; 0）
     * @param progress        起手进度 [0,1]
     * @param stirTicks       加工相位（会话 tick − 起手）；&lt; 0 = 还在起手期内
     * @param stirProgress    搅拌圆渐入包络 [0,1]
     * @param processingTicks 圆周期来源（{@code tools.stirring_staff.processing_ticks}）
     */
    private record Anim(boolean active, float progress, int stirTicks, float stirProgress, int processingTicks) {
        static final Anim NONE = new Anim(false, 0f, 0, 0f, 1);
    }

    private static Anim computeAnim(ItemStack item, ItemDisplayContext displayContext,
                                    @Nullable ItemOwner owner) {
        boolean leftHand = displayContext == ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
        boolean firstPerson = leftHand || displayContext == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
        if (!firstPerson || owner == null) {
            return Anim.NONE;
        }
        LivingEntity living = owner.asLivingEntity();
        if (!(living instanceof Player player)) {
            return Anim.NONE;
        }

        // 会话计时（连续搅拌只在第一次起手）；起手时长与 StirringStaffItem 读同一份 SERVER 配置
        InteractionHand hand = leftHand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        int elapsed = StickyUseSession.elapsedTicks(player, item, hand,
                StickyUseSession.DEFAULT_STICKY_TICKS);
        if (elapsed < 0) {
            return Anim.NONE;
        }

        int windupTicks = Math.max(1, ServerConfig.INSTANCE.stirringStaffWindupTicks.get());
        float progress = Math.min(1f, elapsed / (float) windupTicks);
        // 加工相位（会话 tick − 起手）；起手期内为负 ⇒ 圆运动包络为 0
        int stirTicks = Math.max(0, elapsed - windupTicks);
        float stirProgress = stirTicks <= 0 ? 0f : Math.min(1f, stirTicks / STIR_CIRCLE_FADE_IN_TICKS);
        // 搅拌圆的周期来源（SERVER 配置，运行时读，不能进静态字段）
        int processingTicks = Math.max(1, ServerConfig.INSTANCE.stirringStaffProcessingTicks.get());

        return new Anim(progress > 0f, progress, stirTicks, stirProgress, processingTicks);
    }

    // ===================== JSON 类型 =====================

    /**
     * {@code assets/create_hand_made/items/stirring_staff.json}：
     *
     * <pre>
     * {
     *   "model": {
     *     "type": "create_hand_made:stirring_staff",
     *     "model": "create_hand_made:item/stirring_staff"
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

            return new StirringStaffItemModel(quads, properties, new Matrix4f(transformation));
        }
    }
}
