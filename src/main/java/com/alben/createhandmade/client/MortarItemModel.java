package com.alben.createhandmade.client;

import com.alben.createhandmade.ModDataComponents;
import com.alben.createhandmade.item.CrusherMortarItem;
import com.alben.createhandmade.item.MortarContents;
import com.alben.createhandmade.item.MortarItem;
import com.alben.createhandmade.util.StickyUseSession;
import com.google.common.base.Suppliers;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.dispatch.BlockModelRotation;
import net.minecraft.client.renderer.item.CuboidItemModelWrapper;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.ModelRenderProperties;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
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

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * 研钵（{@code mortar}）与碾钵（{@code crusher_mortar}）共用的物品模型：
 * <b>钵 + 碗里的内容物 + 杵</b> 三个图层，并在第一人称播放「起手 → 捶捣」动画。
 *
 * <h3>为什么不是普通 JSON 模型</h3>
 * <p>26.1 的物品渲染管线是 {@code assets/<ns>/items/<id>.json} → {@link ItemModel} →
 * {@link ItemStackRenderState}（只装数据）→ {@code SubmitNodeCollector}（真正提交）。
 * {@code item/generated} 的多个 layer 会被生成在<b>同一平面</b>上，只能整体一起动；
 * 要让杵独立位移/旋转，必须自己往 {@code ItemStackRenderState} 里塞<b>多个图层</b>。
 * 因此这里注册一个自定义 {@code ItemModel} 类型（{@code create_hand_made:mortar}），
 * 由 {@link #Unbaked} 读 JSON 里的 {@code bowl} / {@code pestle} 两个模型 id。</p>
 *
 * <h3>坐标空间</h3>
 * <p>{@code LayerRenderState.submit} 的 pose = 物品显示变换（{@code item/generated} 的
 * firstperson rotation {@code [0,-90,25]} 等）+ {@code localTransform}，最后再统一
 * {@code translate(-0.5,-0.5,-0.5)}（见 {@code ItemTransform#apply}）。所以
 * {@code localTransform} 里的单位与老版 {@code PoseStack} 完全一致：<b>0.0625 = 1 像素</b>，
 * 原 NeoForge 渲染器的所有数值可以原样搬过来。</p>
 *
 * <h3>动画</h3>
 * <ul>
 *   <li><b>起手</b>：把 {@code item/generated} 第一人称「侧对屏幕」的姿态转正（逆旋转
 *       {@code Rz(-25)·Ry(+90)}，左手取反）并向前伸出。</li>
 *   <li><b>捶捣</b>：杵按 {@link #poundCurve}「抬 → 砸 → 贴合」循环，钵（连同内容物）反向下沉一半。</li>
 *   <li>相位与音效共用 {@link StickyUseSession#processingElapsedTicks}，所以「听到砸声」=「看到砸到底」。</li>
 * </ul>
 *
 * <h3>只在第一人称动</h3>
 * <p>GUI / 第三人称 / 掉落物都是静态合成（钵 → 内容物 → 杵），不读会话状态。</p>
 */
public final class MortarItemModel implements ItemModel {

    /** 模型类型 id（{@code items/*.json} 里写 {@code "type": "create_hand_made:mortar"}）。 */
    public static final Identifier TYPE_ID = Identifier.fromNamespaceAndPath("create_hand_made", "mortar");

    // ===================== 共用常量（与旧版渲染器一致） =====================

    /** {@code item/generated} 第一人称自带的 Z 滚转角（rotation [0,-90,25]）。 */
    private static final float TILT_DEGREES = 25f;

    /** 一个捶捣周期里「抬起」段结束的相位；剩下的 0.15 用于下砸（比抬起快 = 有砸的力度感）。 */
    private static final float LIFT_END = MortarItem.SLAM_PHASE - 0.15f;

    /** 左手杵的额外水平修正（位移镜像 + 旋转不镜像 + 杵贴图左右不对称带来的固定偏差）。 */
    private static final float LEFT_PESTLE_X_CORRECTION = -0.0625f;

    /** 杵的起始 Y（1 像素）。 */
    private static final float PESTLE_Y_BASE = 0.0625f;

    /** 使用时整体再远离玩家的额外前推（视图 -Z）。 */
    private static final float USING_Z_PUSH = 0.1f;

    /** 内容物的起始 Y 基准。 */
    private static final float CONTENTS_Y_BASE = 0.0f;

    // ===================== 实例状态（bake 出来的模型数据） =====================

    private final QuadCollection bowlQuads;
    private final ModelRenderProperties bowlProperties;
    private final QuadCollection pestleQuads;
    private final ModelRenderProperties pestleProperties;
    private final Matrix4fc transformation;
    private final Supplier<Vector3fc[]> bowlExtents;
    private final Supplier<Vector3fc[]> pestleExtents;

    private MortarItemModel(QuadCollection bowlQuads, ModelRenderProperties bowlProperties,
                            QuadCollection pestleQuads, ModelRenderProperties pestleProperties,
                            Matrix4fc transformation) {
        this.bowlQuads = bowlQuads;
        this.bowlProperties = bowlProperties;
        this.pestleQuads = pestleQuads;
        this.pestleProperties = pestleProperties;
        this.transformation = transformation;
        this.bowlExtents = Suppliers.memoize(() -> CuboidItemModelWrapper.computeExtents(bowlQuads.getAll()));
        this.pestleExtents = Suppliers.memoize(() -> CuboidItemModelWrapper.computeExtents(pestleQuads.getAll()));
    }

    // ===================== 每帧装填渲染数据 =====================

    @Override
    public void update(ItemStackRenderState output, ItemStack item, ItemModelResolver resolver,
                       ItemDisplayContext displayContext, @Nullable ClientLevel level,
                       @Nullable ItemOwner owner, int seed) {
        output.appendModelIdentityElement(this);

        MortarParams params = paramsFor(item);
        boolean leftHand = displayContext == ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
        boolean firstPerson = leftHand || displayContext == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
        boolean gui = displayContext == ItemDisplayContext.GUI;
        int modifier = leftHand ? -1 : 1;

        Anim anim = computeAnim(item, displayContext, owner);
        if (firstPerson) {
            // 每帧都会变（起手进度 / 捶捣相位）⇒ 告诉渲染状态不要缓存
            output.setAnimated();
        }

        // ---- 外层：起手 = 把「侧对屏幕」转正 + 向前伸出 ----
        //
        // ★ 坐标系对齐（关键）：26.1 的 localTransform 作用在管线自带的 T(-0.5) **之后**
        //   （ItemTransform#apply 最后一步是 translate(-0.5,-0.5,-0.5)），
        //   而旧 NeoForge 渲染器的起手变换作用在它**之前**：
        //       旧：Display · Windup · Part · T(-0.5)
        //       新：Display · T(-0.5) · localTransform
        //   直接把 Windup 当 localTransform 用，会让旋转支点从「贴图中心」跑到「贴图左下角」，
        //   并且整体多偏 T(-0.5)（实测表现为：钵比目标位置偏右约 7 像素 = 看起来还在右手，
        //   杵因为绕角点转 45° 而整体偏左约 8 像素）。
        //   所以这里先用 T(+0.5) 把原点搬回贴图左下角，各图层末尾再用 T(-0.5) 搬回去，
        //   等价于旧管线，原版实测调好的数值（baseX = -0.90 等）才能原样沿用。
        Matrix4f base = new Matrix4f(this.transformation).translate(0.5f, 0.5f, 0.5f);
        if (firstPerson) {
            float face = anim.windup;
            base.rotateZ((float) Math.toRadians(modifier * -TILT_DEGREES * face));
            base.rotateY((float) Math.toRadians(modifier * 90f * face));
            base.translate(modifier * params.baseX() * face, params.baseY() * face, params.baseZ() * face);
            base.translate(0f, 0f, -(params.frontDistance() + USING_Z_PUSH) * face);
        }

        // 钵（连同内容物）向下 -A*factor，杵向上 +A ⇒ 相对位移做「对撞」
        float bowlDrop = params.poundAmplitude() * params.bowlDropFactor() * anim.pound;

        // ---- 图层 1：钵 ----
        ItemStackRenderState.LayerRenderState bowlLayer = output.newLayer();
        bowlLayer.setExtents(this.bowlExtents);
        bowlLayer.setLocalTransform(new Matrix4f(base)
                .translate(0f, -bowlDrop, 0f)
                .translate(-0.5f, -0.5f, -0.5f));
        this.bowlProperties.applyToLayer(bowlLayer, displayContext);
        bowlLayer.prepareQuadList().addAll(this.bowlQuads.getAll());
        if (item.hasFoil()) {
            bowlLayer.setFoilType(ItemStackRenderState.FoilType.STANDARD);
            output.setAnimated();
        }

        // ---- 图层 2：碗里的内容物（任意物品，用 FIXED 变换缩进碗里） ----
        MortarContents contents = item.get(ModDataComponents.MORTAR_CONTENTS);
        if (contents != null && !contents.stack().isEmpty() && level != null) {
            float contentsX = modifier * params.contentsXOffset() * anim.windup;
            float zOffset = gui ? 0.5f : 0.0625f;
            // 注意：这里**不要**补 T(-0.5)。内容物由 ContentsRenderer 现搭一个
            // ItemStackRenderState，它自己的 FIXED 变换末尾已经带了 T(-0.5)，
            // 与旧渲染器里 renderStatic(FIXED) 的位置一致。
            Matrix4f contentsMatrix = new Matrix4f(base)
                    .translate(contentsX, CONTENTS_Y_BASE - bowlDrop, zOffset)
                    .scale(params.contentsScale())
                    .translate(0f, -0.05f, 0f);
            // 每捣一下原地多转一点（GUI 里不转，图标才干净）
            if (!gui) {
                contentsMatrix.rotateZ((float) Math.toRadians(anim.totalPounds * params.contentsSpinDegrees()));
            }
            ItemStackRenderState.LayerRenderState contentsLayer = output.newLayer();
            contentsLayer.setLocalTransform(contentsMatrix);
            this.bowlProperties.applyToLayer(contentsLayer, displayContext);
            contentsLayer.setupSpecialModel(ContentsRenderer.INSTANCE, contents.stack());
        }

        // ---- 图层 3：杵（向屏幕中间收 + 向上捶 + 朝相机让开钵 + 起手后转 45°） ----
        float pestleX = modifier * params.pestleXOffset() * anim.windup;
        if (leftHand) {
            pestleX += LEFT_PESTLE_X_CORRECTION * anim.windup;
        }
        Matrix4f pestleMatrix = new Matrix4f(base)
                .translate(pestleX, PESTLE_Y_BASE + params.poundAmplitude() * anim.pound,
                        params.pestleDepthOffset())
                .rotateZ((float) Math.toRadians(params.pestleSpinDegrees() * anim.windup))
                // 绕贴图中心转完之后再搬回贴图中心（见上方坐标系对齐说明）
                .translate(-0.5f, -0.5f, -0.5f);
        ItemStackRenderState.LayerRenderState pestleLayer = output.newLayer();
        pestleLayer.setExtents(this.pestleExtents);
        pestleLayer.setLocalTransform(pestleMatrix);
        this.pestleProperties.applyToLayer(pestleLayer, displayContext);
        pestleLayer.prepareQuadList().addAll(this.pestleQuads.getAll());
        if (item.hasFoil()) {
            pestleLayer.setFoilType(ItemStackRenderState.FoilType.STANDARD);
        }
    }

    // ===================== 动画相位 =====================

    /** 一帧的动画状态。{@code windup}/{@code pound} 都在 [0,1]。 */
    private record Anim(float windup, float pound, int totalPounds, boolean active) {
        static final Anim NONE = new Anim(0f, 0f, 0, false);
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

        InteractionHand hand = leftHand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        int elapsed = StickyUseSession.elapsedTicks(player, item, hand,
                StickyUseSession.DEFAULT_STICKY_TICKS);
        if (elapsed < 0) {
            return Anim.NONE;
        }

        boolean crusherMortar = item.getItem() instanceof CrusherMortarItem;
        int windupTicks = MortarItem.windupTicks(crusherMortar);
        int cycleTicks = MortarItem.poundCycleTicks(crusherMortar);

        // windupTicks = 0 视为「不要起手」，立刻到位（同时避免除零）
        float windup = windupTicks <= 0 ? 1f : Math.min(1f, elapsed / (float) windupTicks);

        int poundTicks = StickyUseSession.processingElapsedTicks(player, item, hand,
                StickyUseSession.DEFAULT_STICKY_TICKS, windupTicks);
        if (poundTicks < 0) {
            poundTicks = 0;   // 起手期内 = 杵在最低位
        }
        float pound = poundCurve((poundTicks % cycleTicks) / (float) cycleTicks) * windup;
        return new Anim(windup, pound, poundTicks / cycleTicks, true);
    }

    /**
     * 捶捣曲线：一个周期内杵的「抬起系数」，恒 ≥ 0（不会砸到钵下面）。
     *
     * <pre>
     * phase 0    → LIFT_END(0.45)    抬起：0 → +1（smoothstep，缓入缓出像抬手蓄力）
     * phase 0.45 → SLAM_PHASE(0.60)  下砸：+1 → 0（15% 周期走完全程 = 明显比抬起快）
     * phase 0.60 → 1.00              贴合/停顿：0（杵停在碗里，留出研磨节奏）
     * </pre>
     */
    private static float poundCurve(float phase) {
        if (phase < LIFT_END) {
            float t = phase / LIFT_END;
            return t * t * (3f - 2f * t);
        }
        if (phase < MortarItem.SLAM_PHASE) {
            float t = (phase - LIFT_END) / (MortarItem.SLAM_PHASE - LIFT_END);
            return 1f - t;
        }
        return 0f;
    }

    // ===================== 每个物品一套视觉参数 =====================

    /**
     * 一个物品的全套视觉参数（时间参数不在这里，仍读 {@code ServerConfig}）。
     *
     * @param baseX               基准位置（视图 X：负 = 往屏幕左，也就是往中间收）
     * @param baseY               基准位置 Y（视图上）
     * @param baseZ               基准位置 Z（正 = 朝相机）
     * @param frontDistance       起手前伸距离（沿视图 -Z）
     * @param poundAmplitude      捶捣振幅：杵向上 +A，钵向下 -A*bowlDropFactor
     * @param bowlDropFactor      钵（含内容物）下沉比例
     * @param pestleXOffset       杵相对钵的水平偏移
     * @param pestleSpinDegrees   杵的最终旋转角（第一人称按 windup 渐变；GUI 不转）
     * @param contentsScale       内容物缩放
     * @param contentsSpinDegrees 内容物每捣一下的旋转角
     * @param pestleDepthOffset   杵朝相机的深度偏移（必须 &gt; 一片板厚 1/16 = 0.0625）
     */
    private record MortarParams(
            float baseX, float baseY, float baseZ,
            float frontDistance,
            float poundAmplitude,
            float bowlDropFactor,
            float pestleXOffset,
            float pestleSpinDegrees,
            float contentsScale,
            float contentsSpinDegrees,
            float pestleDepthOffset) {

        /** 内容物水平偏移 = 杵的一半（内容物比杵更靠屏幕中间）。 */
        float contentsXOffset() {
            return pestleXOffset / 2f;
        }
    }

    /** 手搓研钵（{@code create_hand_made:mortar}）。 */
    private static final MortarParams MORTAR_PARAMS = new MortarParams(
            -0.90f, 0.0f, 0.0f,
            0.4f,
            0.25f,
            0.5f,
            -0.05f,
            45f,
            0.25f,
            45f,
            0.07f);

    /** 碾钵（{@code create_hand_made:crusher_mortar}）：视觉参数与研钵一致，只有时长/音效不同。 */
    private static final MortarParams CRUSHER_PARAMS = new MortarParams(
            -0.90f, 0.0f, 0.0f,
            0.4f,
            0.25f,
            0.5f,
            -0.05f,
            45f,
            0.25f,
            45f,
            0.07f);

    private static MortarParams paramsFor(ItemStack stack) {
        return stack.getItem() instanceof CrusherMortarItem ? CRUSHER_PARAMS : MORTAR_PARAMS;
    }

    // ===================== 碗里的内容物：交给物品管线渲染 =====================

    /**
     * 内容物渲染器：{@code submit} 时现搭一个 {@link ItemStackRenderState}（FIXED 变换），
     * 再交给同一个 {@code SubmitNodeCollector} 提交。这样任意物品（含附魔光效、方块物品）
     * 都能正确渲染，且与物品栏里的外观完全一致。
     */
    private static final class ContentsRenderer implements SpecialModelRenderer<ItemStack> {

        static final ContentsRenderer INSTANCE = new ContentsRenderer();

        @Override
        public void submit(@Nullable ItemStack argument, PoseStack poseStack,
                           SubmitNodeCollector submitNodeCollector, int lightCoords,
                           int overlayCoords, boolean hasFoil, int outlineColor) {
            if (argument == null || argument.isEmpty()) {
                return;
            }
            Minecraft minecraft = Minecraft.getInstance();
            ItemStackRenderState state = new ItemStackRenderState();
            minecraft.getItemModelResolver().updateForTopItem(state, argument,
                    ItemDisplayContext.FIXED, minecraft.level, null, 0);
            state.submit(poseStack, submitNodeCollector, lightCoords, overlayCoords, outlineColor);
        }

        @Override
        public void getExtents(Consumer<Vector3fc> output) {
            // 内容物只用于显示，不参与物品实体的包围盒
        }

        @Override
        public ItemStack extractArgument(ItemStack stack) {
            return ItemStack.EMPTY;
        }
    }

    // ===================== JSON 类型 =====================

    /**
     * {@code assets/create_hand_made/items/mortar.json}：
     *
     * <pre>
     * {
     *   "model": {
     *     "type": "create_hand_made:mortar",
     *     "bowl": "create_hand_made:item/mortar",
     *     "pestle": "create_hand_made:item/mortar_pestle"
     *   }
     * }
     * </pre>
     */
    public record Unbaked(Identifier bowl, Identifier pestle) implements ItemModel.Unbaked {

        public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Identifier.CODEC.fieldOf("bowl").forGetter(Unbaked::bowl),
                Identifier.CODEC.fieldOf("pestle").forGetter(Unbaked::pestle)
        ).apply(instance, Unbaked::new));

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }

        @Override
        public void resolveDependencies(ResolvableModel.Resolver resolver) {
            resolver.markDependency(this.bowl);
            resolver.markDependency(this.pestle);
        }

        @Override
        public ItemModel bake(ItemModel.BakingContext context, Matrix4fc transformation) {
            ModelBaker baker = context.blockModelBaker();

            ResolvedModel bowlModel = baker.getModel(this.bowl);
            TextureSlots bowlSlots = bowlModel.getTopTextureSlots();
            QuadCollection bowlQuads = bowlModel.bakeTopGeometry(bowlSlots, baker, BlockModelRotation.IDENTITY);
            ModelRenderProperties bowlProperties =
                    ModelRenderProperties.fromResolvedModel(baker, bowlModel, bowlSlots);

            ResolvedModel pestleModel = baker.getModel(this.pestle);
            TextureSlots pestleSlots = pestleModel.getTopTextureSlots();
            QuadCollection pestleQuads = pestleModel.bakeTopGeometry(pestleSlots, baker, BlockModelRotation.IDENTITY);
            ModelRenderProperties pestleProperties =
                    ModelRenderProperties.fromResolvedModel(baker, pestleModel, pestleSlots);

            return new MortarItemModel(bowlQuads, bowlProperties, pestleQuads, pestleProperties,
                    new Matrix4f(transformation));
        }
    }
}
