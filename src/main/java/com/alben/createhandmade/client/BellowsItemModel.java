package com.alben.createhandmade.client;

import com.alben.createhandmade.ServerConfig;
import com.alben.createhandmade.bellows.BellowsMediaRegistry;
import com.alben.createhandmade.item.BellowsItem;
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
import net.minecraft.world.entity.HumanoidArm;
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
 * 风箱的物品模型：<b>风箱本体 + 副手介质预览</b> 两个图层，蓄力时介质从画面外飞到风箱前方。
 *
 * <h3>为什么不是普通 JSON 模型</h3>
 * <p>旧版是 Create 的 {@code CustomRenderedItemModelRenderer}（26.1 的 Create Fly 已删掉这套 API）。
 * 它需要在一个渲染 pass 里额外画<b>另一件物品</b>（副手的介质），普通 JSON 模型做不到，
 * 所以这里照 {@link HandSawItemModel} 的做法注册自定义 {@code ItemModel} 类型
 * （{@code create_hand_made:bellows}）。</p>
 *
 * <h3>坐标系</h3>
 * <p>与手锯/研钵同一条共轭公式：</p>
 * <pre>
 * 旧：Display · M · T(-0.5)
 * 新：Display · T(-0.5) · localTransform
 * ⇒ 本体（M = 单位阵）：localTransform = 单位阵
 * ⇒ 介质（子图层走 FIXED，自带 T(-0.5)）：localTransform = T(+0.5) · M
 * </pre>
 * <p>所以旧渲染器里实测调好的 {@code TARGET_*} / {@code START_*} / 旋转角全部可以原样搬过来。</p>
 *
 * <h3>只在第一人称动</h3>
 * <p>GUI / 第三人称 / 掉落物都是普通静态外观（只走风箱本体一层），不读会话状态 ——
 * 这样图标永远干净，也不必在没有玩家上下文时碰 {@code ServerConfig}。</p>
 */
public final class BellowsItemModel implements ItemModel {

    /** 模型类型 id（{@code items/bellows.json} 里写 {@code "type": "create_hand_made:bellows"}）。 */
    public static final Identifier TYPE_ID = Identifier.fromNamespaceAndPath("create_hand_made", "bellows");

    // ================= 副手介质：位置 / 缩放（实测定稿） =================

    /** 副手最终位置（主手物品局部空间）。 */
    private static final float TARGET_X = -0.5f;
    private static final float TARGET_Y = 0.5f;
    private static final float TARGET_Z = 0.1f;

    /** 副手起始位置（近似玩家屏幕右下角）。 */
    private static final float START_X = 0f;
    private static final float START_Y = 0f;
    private static final float START_Z = 1f;

    private static final float TARGET_SCALE = 0.2f;
    private static final float START_SCALE = 1f;

    /** 介质朝向（实测定稿）：YP 90°、XP 45° 均乘 modifier。 */
    private static final float MEDIA_YAW = 90f;
    private static final float MEDIA_PITCH = 45f;

    /** 末端额外的 Y 轴旋转：仅左手追加 180°。 */
    private static final float LEFT_HAND_END_YAW = 180f;

    // ===================== 实例状态（bake 出来的模型数据） =====================

    private final QuadCollection quads;
    private final ModelRenderProperties properties;
    private final Matrix4fc transformation;
    private final Supplier<Vector3fc[]> extents;

    private BellowsItemModel(QuadCollection quads, ModelRenderProperties properties, Matrix4fc transformation) {
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
        if (anim.showMedia()) {
            // 蓄力进度每 tick 都在变 ⇒ 告诉渲染状态不要缓存
            output.setAnimated();
        }

        // ---- 图层 1：风箱本体（旧渲染器的 M = 单位阵） ----
        ItemStackRenderState.LayerRenderState bodyLayer = output.newLayer();
        bodyLayer.setExtents(this.extents);
        bodyLayer.setLocalTransform(new Matrix4f(this.transformation));
        this.properties.applyToLayer(bodyLayer, displayContext);
        bodyLayer.prepareQuadList().addAll(this.quads.getAll());
        if (item.hasFoil()) {
            bodyLayer.setFoilType(ItemStackRenderState.FoilType.STANDARD);
            output.setAnimated();
        }

        // ---- 图层 2：副手介质（画在本体之上） ----
        if (anim.showMedia() && !anim.media().isEmpty()) {
            float progress = anim.progress();
            // smoothstep 缓动
            float eased = progress * progress * (3f - 2f * progress);

            // ★ 介质位移镜像（实测定稿）：X 不镜像、Y 不镜像、Z 镜像
            float startZ = START_Z * modifier;
            float targetZ = TARGET_Z * modifier;

            float tx = START_X + (TARGET_X - START_X) * eased;
            float ty = START_Y + (TARGET_Y - START_Y) * eased;
            float tz = startZ + (targetZ - startZ) * eased;
            float scale = START_SCALE + (TARGET_SCALE - START_SCALE) * eased;

            // 见类注释「坐标系」：先 T(+0.5) 把原点搬回贴图左下角；
            // **不要**补尾部 T(-0.5) —— FIXED 变换自带
            Matrix4f mediaMatrix = new Matrix4f(this.transformation)
                    .translate(0.5f, 0.5f, 0.5f)
                    .translate(tx, ty, tz)
                    .scale(scale)
                    .rotateY(degrees(MEDIA_YAW * modifier))
                    .rotateX(degrees(MEDIA_PITCH * modifier));
            if (leftHand) {
                mediaMatrix.rotateY(degrees(LEFT_HAND_END_YAW));
            }

            ItemStackRenderState.LayerRenderState mediaLayer = output.newLayer();
            mediaLayer.setLocalTransform(mediaMatrix);
            this.properties.applyToLayer(mediaLayer, displayContext);
            mediaLayer.setupSpecialModel(FixedItemRenderer.INSTANCE, anim.media());
        }
    }

    private static float degrees(float value) {
        return (float) Math.toRadians(value);
    }

    // ===================== 蓄力相位 =====================

    /**
     * 一帧的动画状态。
     *
     * @param showMedia 是否画副手介质预览
     * @param progress  蓄力进度 [0,1]
     * @param media     副手介质（仅 {@code showMedia} 时有意义）
     */
    private record Anim(boolean showMedia, float progress, ItemStack media) {
        static final Anim NONE = new Anim(false, 0f, ItemStack.EMPTY);
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

        // 本次渲染的是「哪一只手」：leftHand = 画在左臂上；主手是否为左臂由主手设置决定
        InteractionHand myHand = ((player.getMainArm() == HumanoidArm.LEFT) == leftHand)
                ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;

        // 必须正在使用这一手的风箱
        if (!player.isUsingItem()) {
            return Anim.NONE;
        }
        if (player.getUsedItemHand() != myHand) {
            return Anim.NONE;
        }
        if (!(player.getItemInHand(myHand).getItem() instanceof BellowsItem)) {
            return Anim.NONE;
        }

        // 介质始终放在「另一只手」
        ItemStack media = player.getItemInHand(myHand == InteractionHand.MAIN_HAND
                ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
        if (media.isEmpty() || BellowsMediaRegistry.resolve(media) == null) {
            return Anim.NONE;
        }

        // 蓄力时长与 BellowsItem 读同一份 SERVER 配置（运行时读，静态字段会在配置未加载时抛异常）
        float chargeTicks = Math.max(1, ServerConfig.INSTANCE.bellowsChargeTicks.get());
        float progress = Math.min(1f, player.getTicksUsingItem() / chargeTicks);
        return new Anim(true, progress, media);
    }

    // ===================== JSON 类型 =====================

    /**
     * {@code assets/create_hand_made/items/bellows.json}：
     *
     * <pre>
     * {
     *   "model": {
     *     "type": "create_hand_made:bellows",
     *     "model": "create_hand_made:item/bellows"
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

            return new BellowsItemModel(quads, properties, new Matrix4f(transformation));
        }
    }
}
