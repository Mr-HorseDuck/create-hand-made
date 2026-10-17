package com.alben.createhandmade.item;

import com.alben.createhandmade.CreateHandMade;
import com.alben.createhandmade.ModDataComponents;
import com.alben.createhandmade.ServerConfig;
import com.alben.createhandmade.util.StickyUseSession;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModel;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModelRenderer;
import com.simibubi.create.foundation.item.render.PartialItemModelRenderer;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * 研钵（{@code mortar}）与碾钵（{@code crusher_mortar}）<b>共用</b>的渲染器：
 * 两个物品都由「钵 + 杵」两个部件拼成，并播放同一套「起手 → 捶捣」动画。
 *
 * <h3>部件从哪来</h3>
 * <ul>
 *   <li><b>钵</b>：各物品自己的注册模型（{@code models/item/mortar.json} /
 *       {@code models/item/crusher_mortar.json}，都是 {@code item/generated}），
 *       经 {@code model.getOriginalModel()} 取到 —— 所以这里天然按物品区分，不需要额外常量。</li>
 *   <li><b>杵</b>：各物品一份独立模型（{@code item/mortar_pestle} / {@code item/crusher_mortar_pestle}），
 *       用 {@link PartialModel#of} 单独烘焙后取回，放在 {@link MortarParams} 里。</li>
 * </ul>
 * <p>为什么不把杵做成同一个 JSON 的 layer1：{@code item/generated} 的多个 layer 会被
 * {@code ItemModelGenerator} 生成在<b>同一个平面</b>（同一个 from/to），只能整体一起动。
 * 要独立变换就必须拿到独立的 {@link BakedModel}（T8 已实测：{@code get()} 非 null、quads=23）。</p>
 *
 * <h3>视觉参数按物品分家（{@link MortarParams}）</h3>
 * <p>所有"看得见的"数值（基准位置、前伸、振幅、偏移、缩放、旋转、深度偏移、杵的模型）都在
 * {@link MortarParams} 里，研钵与碾钵各一份：{@link #MORTAR_PARAMS} / {@link #CRUSHER_PARAMS}，
 * 由 {@link #paramsFor(ItemStack)} 按物品类型选择。碾钵的初始值先抄研钵，之后各自微调。</p>
 * <p><b>时间参数</b>：起手时长与捶捣周期从 {@link ServerConfig} 按物品读取
 * （研钵 {@code mortar_*} / 碾钵 {@code crusher_mortar_*}，两套独立可调，SERVER 类型连接时同步）；
 * 单物品加工时长由 {@code getUseDuration} 决定，而 {@code getUseDuration} 读的是
 * {@code StickyUseSession.beginUse} 定下的"本次使用总时长"：<b>会话首次 = 起手 +
 * 配方 {@code processing_time} × 系数，同一次长按的后续续用 = 只有加工</b>（b2.6）。</p>
 *
 * <h3>两阶段动画</h3>
 * <ol>
 *   <li><b>起手</b>（0 → {@code windup_ticks}）：整体转正 + 向前伸出，进度 {@code windup} 0→1。</li>
 *   <li><b>捶捣</b>：杵按 {@link #poundCurve}「抬 → 砸 → 贴合」循环（周期 {@code pound_cycle_ticks}），
 *       同时钵（连同内容物）向下走一半做"对撞"，相对位移 1.5A。</li>
 * </ol>
 *
 * <h3>只在第一人称播动画</h3>
 * <p>GUI 与第三人称都渲染成<b>静态合成</b>（钵 → 内容物 → 杵），不读配置、不动 ——
 * 这样图标永远干净，也不必在没有玩家上下文时碰 SERVER 配置（未加载时会抛异常）。</p>
 *
 * <h3>坐标系：item/generated 第一人称是「侧着」的，必须先转正</h3>
 * <ul>
 *   <li>第一人称变换 = {@code rotation [0,-90,25]}、{@code translation [1.13,3.2,1.13]}、{@code scale 0.68}
 *       （左手由 {@code ItemTransform.apply(true)} 把 Y/Z 旋转取反 → {@code [0,+90,-25]}）。</li>
 *   <li>它确实会施加到 pose 上：{@code ClientHooks.handleCameraTransforms} 无条件调 {@code applyTransform}，
 *       Create 的 {@code CustomRenderedItemModel.applyTransform} 只改返回值、仍然调 {@code super}。</li>
 *   <li>{@code rotationXYZ} 的合成是 {@code Rx·Ry·Rz} ⇒ 旋转矩阵 {@code R = Ry(∓90)·Rz(±25)}；
 *       {@code Ry(-90)} 把这片板转成"侧对屏幕"，这就是旧版看起来又窄又斜的原因。</li>
 *   <li>{@code ItemModelGenerator} 生成的是<b>一片 1 像素厚的板</b>（z = 7.5~8.5）+ 一圈轮廓侧面。</li>
 * </ul>
 * <p>做法：用 {@code windup} 插值把逆旋转 <b>{@code R⁻¹ = Rz(-25)·Ry(+90)}</b>（左手取反）乘回 pose。
 * pose 是右乘 ⇒ 必须先 {@code mulPose(ZP)} 再 {@code mulPose(YP)}；而且 <b>BASE / 前伸这类位移要写在
 * {@code mulPose} 之后</b>，才会被解释成"转正后的视图方向"（前 = {@code -Z}、上 = {@code +Y}、
 * 朝相机 = {@code +Z}）。转正后位移不需要镜像，镜像量全部落在旋转角上（{@code modifier}）。</p>
 * <p>只有第一人称需要转正：GUI 在 JSON 里没有条目（{@code NO_TRANSFORM}），第三人称/ground/fixed 旋转都是 0。</p>
 *
 * <h3>「起手」只在整个长按期间播一次，加工相位也跨续用连续（b2.6）</h3>
 * <p>按住右键连续研磨时，每磨完一件客户端会自动再次 {@code use()} → {@code startUsingItem}，
 * {@code getTicksUsingItem()} 归零会让起手重播。本类改用<b>会话计时</b>：两次「正在使用」的间隔不超过
 * {@link StickyUseSession#DEFAULT_STICKY_TICKS} 就视为同一次长按，起手进度继续累计；真正松手后才重新起手。</p>
 * <p>捶捣相位也取自会话（{@link StickyUseSession#processingElapsedTicks}），与出声的相位严格一致
 * （见 {@code MortarItem.SLAM_PHASE}）。<b>b2.6 之前</b>相位按"本次使用"的 tick 算，于是每次续用的头
 * {@code windup_ticks} 内杵都停在碗里、音效同停（实测表现为"1t 加工 + 100t 静止"）；
 * 现在起手只在会话首次付一次，续用直接接着捣。</p>
 *
 * <h3>杵的深度偏移</h3>
 * <p>两片板严格共面、板厚 {@code 1/16 = 0.0625}，所以偏移必须<b>大于一片板厚</b>才能真正分开；
 * 0.005 / 0.02 都小于板厚 ⇒ 两片板互相穿插 ⇒ 重叠处闪。</p>
 *
 * <h3>碾钵的节奏已与研钵对齐（R6）</h3>
 * <p>{@code CrusherMortarItem} 是<b>独立实现</b>（<b>不</b>继承 {@code MortarItem}），所以 R1~R4 在研钵上做的
 * 音效/姿势改动最初没有传导过来。R6 已把它补齐：{@code UseAnim.NONE}、起手门槛改读
 * {@code mortar_windup_ticks}、音效/粒子改成每 {@code mortar_pound_cycle_ticks} 一次并用
 * {@code MortarItem.slamTick(cycle)} 对齐「砸到底」。两侧现在共用同一套相位常量与 SERVER 配置。</p>
 *
 * <h3>左手为什么需要额外修正（{@link #LEFT_PESTLE_X_CORRECTION}）</h3>
 * <p>这<b>不是</b>镜像矩阵的 bug，而是两个已确认设计叠加出来的<b>本质不对称</b>：</p>
 * <ol>
 *   <li>位移（{@code pestleXOffset}）<b>乘 modifier 镜像</b> —— 保证两只手的杵都朝屏幕中间靠；</li>
 *   <li>旋转（{@code pestleSpinDegrees}）<b>不镜像</b> —— R4 实测"左右手同向转 45°"才是对的；</li>
 *   <li>而杵的贴图本身<b>左右不对称</b>（杵柄偏在一侧）。</li>
 * </ol>
 * <p>镜像位移 + 同向旋转，会让这片不对称的贴图在左右手的落点差一个固定矢量（实测约 1 像素，
 * 左手偏右）⇒ 用一个"左手专用常量"补偿是正确做法。想"治本"只有把旋转也镜像，
 * 但那会改掉 R4 已经确认的观感（左手杵会朝反方向斜），所以不采用。</p>
 *
 * <h3>待实测</h3>
 * <p>{@link #MORTAR_PARAMS} / {@link #CRUSHER_PARAMS} 里的每个数值。</p>
 */
public class MortarItemRenderer extends CustomRenderedItemModelRenderer {

    // ===================== 共用常量（两支一样，改一处两边都动） =====================

    /** {@code item/generated} 第一人称自带的 Z 滚转角（原版模型 firstperson rotation [0,-90,25]）。 */
    private static final float TILT_DEGREES = 25f;

    /** 一个捶捣周期里「抬起」段结束的相位；剩下的 0.15 用于下砸（比抬起快 = 有砸的力度感）。 */
    private static final float LIFT_END = MortarItem.SLAM_PHASE - 0.15f;

    /**
     * 「同一次长按」的容差（tick）统一放在 {@link StickyUseSession#DEFAULT_STICKY_TICKS}，
     * 四个工具（手锯 / 研钵 / 碾钵 / 搅拌杖）共用同一个值。
     */

    /**
     * 左手杵的额外水平修正（R6 实测：左手比右手偏右 1 像素 ⇒ 往左挪 1 像素）。
     * <p><b>不乘 {@code modifier}</b> —— 它本身就是"左手专用"的修正量。
     * 根因见类注释「左手为什么需要额外修正」：不是镜像矩阵的 bug，而是"位移镜像 + 旋转不镜像"
     * 叠加杵贴图本身左右不对称产生的固定偏差。</p>
     */
    private static final float LEFT_PESTLE_X_CORRECTION = -0.0625f;

    /** 杵的起始 Y（R6 实测：整体上移 1 像素）。捶捣时在此之上再加 {@code poundAmplitude * pound}。 */
    private static final float PESTLE_Y_BASE = 0.0625f;

    /**
     * 使用时"整体再远离玩家一点"的额外前推（视图 -Z）。
     * <p>与 {@code frontDistance}（起手伸出多远）<b>叠加</b>：两者都乘 {@code windup}，
     * 所以数学上等价于一个和为 {@code frontDistance + USING_Z_PUSH} 的常量 —— 拆成两个只是为了让
     * "起手伸出量"和"使用时的额外前推"各自可调。若将来要"起手过程近、到位后远"，
     * 给这一项换个曲线（例如 {@code windup * windup}）即可。</p>
     */
    private static final float USING_Z_PUSH = 0.1f;

    /**
     * 内容物的起始 Y 基准。
     * <p>旧值 {@code 0.125f}（注释写的"上移 2 像素"）；R6 实测要求<b>下移 2 像素</b>
     * ⇒ {@code 0.125f - 0.125f = 0f}。碗内基础位置由后面的 {@code translate(0, -0.05, 0)} 继续负责。</p>
     */
    private static final float CONTENTS_Y_BASE = 0.0f;

    // ===================== 杵的独立模型（每个物品一个） =====================

    private static final PartialModel MORTAR_PESTLE = pestle("item/mortar_pestle");
    private static final PartialModel CRUSHER_MORTAR_PESTLE = pestle("item/crusher_mortar_pestle");

    /**
     * 造一个杵的 {@link PartialModel}。
     * <p><b>必须用本模组的命名空间</b>：曾经误用 {@code Create.asResource(...)}（那是 {@code create:}），
     * 模型加载失败后退化成 missing 模型，表现为整个物品糊成黑紫块。</p>
     */
    private static PartialModel pestle(String path) {
        return PartialModel.of(ResourceLocation.fromNamespaceAndPath(CreateHandMade.MODID, path));
    }

    // ===================== 每个物品一套视觉参数 =====================

    /**
     * 一个物品的全套视觉参数（<b>时间参数不在这里</b>：起手/周期仍读共用的 SERVER 配置）。
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
     * @param pestleModel         该物品的杵模型
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
            float pestleDepthOffset,
            PartialModel pestleModel) {

        /** 内容物水平偏移 = 杵的一半（内容物比杵更靠屏幕中间）。 */
        float contentsXOffset() {
            return pestleXOffset / 2f;
        }
    }

    /** 手搓研钵（{@code create_hand_made:mortar}）。 */
    private static final MortarParams MORTAR_PARAMS = new MortarParams(
            -0.90f,   // baseX（R5：-0.30 → -0.45，R6：→ -0.90。0.90 模型单位 ≈ 14.4 贴图像素，可能过头）
            0.0f,     // baseY
            0.0f,     // baseZ
            0.4f,     // frontDistance
            0.25f,    // poundAmplitude
            0.5f,     // bowlDropFactor
            -0.05f,   // pestleXOffset
            45f,      // pestleSpinDegrees
            0.25f,    // contentsScale
            45f,      // contentsSpinDegrees
            0.07f,    // pestleDepthOffset
            MORTAR_PESTLE);

    /**
     * 碾钵（{@code create_hand_made:crusher_mortar}）：初始值 = 研钵的值，待实测后各自调。
     * <p>时间参数不在这里（共用 config）；音效/手臂姿势的差异见类注释。</p>
     */
    private static final MortarParams CRUSHER_PARAMS = new MortarParams(
            -0.90f,   // baseX（R6：跟随研钵翻倍；研钵若过头这里也一起退）
            0.0f,     // baseY
            0.0f,     // baseZ
            0.4f,     // frontDistance
            0.25f,    // poundAmplitude
            0.5f,     // bowlDropFactor
            -0.05f,   // pestleXOffset
            45f,      // pestleSpinDegrees
            0.25f,    // contentsScale
            45f,      // contentsSpinDegrees
            0.07f,    // pestleDepthOffset
            CRUSHER_MORTAR_PESTLE);

    /** 按物品挑参数（两个物品共用本渲染器，靠物品类型分支）。 */
    private static MortarParams paramsFor(ItemStack stack) {
        return isCrusherMortar(stack) ? CRUSHER_PARAMS : MORTAR_PARAMS;
    }

    /** 是碾钵吗？ */
    private static boolean isCrusherMortar(ItemStack stack) {
        return stack.getItem() instanceof CrusherMortarItem;
    }

    // ===== 客户端会话状态 =====
    // 会话锚点（起始 tick / 上次使用 tick / 手别）已抽到公共类 StickyUseSession；
    // 这里只留研钵/碾钵自己的"内容物累计旋转"状态。
    /** 上一帧「本次使用内」的捶捣次数，用来检测又砸了一下。 */
    private static int lastPoundCountInUse = 0;
    /** 跨自动续用累计的捶捣次数（内容物每下转 45° 用它，避免续用时跳回 0°）。 */
    private static int totalPounds = 0;

    @Override
    protected void render(ItemStack stack, CustomRenderedItemModel model, PartialItemModelRenderer renderer,
                          ItemDisplayContext transformType, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        MortarParams params = paramsFor(stack);

        Minecraft mc = Minecraft.getInstance();
        ItemRenderer itemRenderer = mc.getItemRenderer();
        LocalPlayer player = mc.player;

        boolean leftHand = transformType == ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
        boolean firstPerson = leftHand || transformType == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;
        boolean isGui = transformType == ItemDisplayContext.GUI;

        MortarContents contents = stack.get(ModDataComponents.MORTAR_CONTENTS.get());
        boolean hasContents = contents != null && !contents.stack().isEmpty();

        // ---- 动画进度：仅第一人称、且"正在使用的就是这只手上的这一把"时才算 ----
        // 两个条件缺一不可：
        //   1) 手别匹配（getUsedItemHand）- 防止双手各拿一把时，副手那把也跟着动；
        //   2) 物品与组件匹配 - 用 isSameItemSameComponents 而不是引用比较（==），
        //      这样即使渲染路径传进来的是副本也能正确判定（引用比较失败会让动画静默不播）。
        InteractionHand useHand = leftHand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        boolean using = firstPerson && player != null && player.isUsingItem()
                && player.getUsedItemHand() == useHand
                && ItemStack.isSameItemSameComponents(player.getUseItem(), stack);

        float windup = 0f;   // 0 = 原版手持姿态（侧对屏幕），1 = 完全伸出且正对相机
        float pound = 0f;    // 杵的抬起系数，实际位移 = params.poundAmplitude() * pound
        int poundCount = 0;  // 本次使用内的捶捣次数，用于累计"总共捣了多少下"

        if (firstPerson && player != null) {
            // ---- 会话计时（公共实现 StickyUseSession）：跨"自动续用"保持起手，
            //      只有真正新的一次长按才重播；状态按（玩家, 物品, 手）分开，双手互不影响 ----
            int elapsed = StickyUseSession.elapsedTicks(player, stack, useHand,
                    StickyUseSession.DEFAULT_STICKY_TICKS);

            if (elapsed >= 0) {
                // SERVER 配置：服务端连接时同步过来的值（单机为同一份文件）。
                // R8 起研钵与碾钵各有一套时间参数，所以按物品选（视觉参数在 MortarParams 里，
                // 时间参数是"运行时读配置"，因此不进 record）。
                boolean crusherMortar = isCrusherMortar(stack);
                int windupTicks = MortarItem.windupTicks(crusherMortar);
                int cycleTicks = MortarItem.poundCycleTicks(crusherMortar);

                // windupTicks = 0 视为"不要起手"，立刻到位（同时避免除零）
                windup = windupTicks <= 0 ? 1f : Math.min(1f, elapsed / (float) windupTicks);

                // 捶捣相位改用「会话加工 tick」（b2.6）：会话首次使用时与旧的
                // "本次使用 tick − 起手"完全等价，续用时继续累加 ⇒ 不再每磨完一件
                // 就把杵按在最低位静止一个起手时长。服务端音效用的是同一相位。
                int poundTicks = StickyUseSession.processingElapsedTicks(player, stack, useHand,
                        StickyUseSession.DEFAULT_STICKY_TICKS, windupTicks);
                if (poundTicks < 0) poundTicks = 0;   // 起手期内 = 杵在最低位（与旧行为一致）
                poundCount = poundTicks / cycleTicks;
                pound = poundCurve((poundTicks % cycleTicks) / (float) cycleTicks) * windup;

                // 累计"总共捣了多少下"：poundCount 是本次使用内的计数（会自动续用归零），
                // 直接拿它当角度会让物品每磨完一件跳回 0°；这里只在真正使用的那只手上累加。
                if (using) {
                    totalPounds += poundCount >= lastPoundCountInUse
                            ? poundCount - lastPoundCountInUse
                            : poundCount;
                    lastPoundCountInUse = poundCount;
                }
            }
        }

        // 杵的模型：资源包缺失时为 null（例如玩家用资源包覆盖了模型），此时只画钵、不崩
        BakedModel pestle = params.pestleModel().get();

        // 转正之后局部轴 = 视图轴（前 = -Z、上 = +Y、朝相机 = +Z），所以位移不需要镜像，
        // 镜像量全部落在旋转角上（modifier）。
        int modifier = leftHand ? -1 : 1;

        // 杵的水平位置 = 基准偏移 × 手别镜像 × windup（静止时不偏移，保持原版位置），
        // 左手再补一个实测修正（同样只在使用时生效，不乘 modifier）。
        float pestleX = modifier * params.pestleXOffset() * windup;
        if (leftHand) {
            pestleX += LEFT_PESTLE_X_CORRECTION * windup;
        }

        ms.pushPose();

        // ---- 阶段 1：起手 = 把模型从"侧对屏幕"转成"正对相机" + 向前伸出 ----
        // 注意顺序：BASE / 前伸这类位移必须写在 mulPose 之后，才会被解释成"转正后的视图方向"
        // （写在 mulPose 之前则会被逆旋转一起转掉，方向就不是屏幕右/屏幕里了）。
        if (firstPerson) {
            float face = windup;   // 想恒定正对就写 1f；想"先转正再前伸"就用 min(1f, windup * 2f)
            ms.mulPose(Axis.ZP.rotationDegrees(modifier * -TILT_DEGREES * face));
            ms.mulPose(Axis.YP.rotationDegrees(modifier * 90f * face));
            // 基准位置也乘 windup：静止手持（windup=0）时"居中"完全不生效 ⇒ 就在原版原位；
            // 起手完成后（windup=1）才完全挪到屏幕中间。这是 R7 的实测要求。
            ms.translate(modifier * params.baseX() * windup, params.baseY() * windup, params.baseZ() * windup);
            ms.translate(0f, 0f, -(params.frontDistance() + USING_Z_PUSH) * windup);
        }

        if (isGui) {
            // ===== GUI：静态合成，不播动画 =====
            // 顺序：钵 → 内容物 → 杵（后画的在上），与世界里保持同一套图层关系
            renderBowl(itemRenderer, stack, model, ms, buffer, light, overlay);
            renderContents(itemRenderer, contents, hasContents, player, ms, buffer, light, overlay,
                    0.5f, 0, params, modifier * params.contentsXOffset() * windup);
            // GUI 里的杵不转（R5 决定），角度直接给 0
            renderPestle(itemRenderer, stack, pestle, ms, buffer, light, overlay,
                    pestleX, PESTLE_Y_BASE, params.pestleDepthOffset(), 0f);
        } else {
            // ===== 世界 / 第一人称 / 第三人称 =====
            // 钵（连同碗里的内容物）向下 -A*factor，杵向上 +A ⇒ 相对位移做"对撞"；
            // 顺序：内容物 → 钵（形成"在碗里"）→ 杵（杵在最上层，像搭在碗沿上）
            ms.pushPose();
            ms.translate(0f, -params.poundAmplitude() * params.bowlDropFactor() * pound, 0f);
            renderContents(itemRenderer, contents, hasContents, player, ms, buffer, light, overlay,
                    0.0625f, totalPounds, params, modifier * params.contentsXOffset() * windup);
            renderBowl(itemRenderer, stack, model, ms, buffer, light, overlay);
            ms.popPose();

            // 杵：向屏幕中间收一点 + 向上捶 + 朝相机让开钵；旋转乘 windup ⇒ 不用时 0°（原位），
            // 起手过程中逐渐转到 pestleSpinDegrees，起手结束后保持。不镜像（左右手同向）。
            renderPestle(itemRenderer, stack, pestle, ms, buffer, light, overlay,
                    pestleX, PESTLE_Y_BASE + params.poundAmplitude() * pound,
                    params.pestleDepthOffset(), params.pestleSpinDegrees() * windup);
        }

        ms.popPose();
    }

    /** 画钵（物品本体模型 —— 每个物品自己的 {@code models/item/<id>.json}）。 */
    private static void renderBowl(ItemRenderer itemRenderer, ItemStack stack, CustomRenderedItemModel model,
                                  PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        itemRenderer.render(stack, ItemDisplayContext.NONE, false, ms, buffer, light, overlay,
                model.getOriginalModel());
    }

    /**
     * 画杵。
     *
     * <p>与 {@link #renderBowl} 用<b>同一个</b>渲染调用（{@code ItemDisplayContext.NONE}），
     * 保证两个部件走同一条烘焙/渲染路径（块图集 vs 物品图集），不会出现色调或光照不一致。</p>
     *
     * <p>位移 {@code (dx, dy, dz)}（相对钵的水平偏移 + 捶捣向上 + 朝相机的深度偏移）先做，<b>再</b>绕
     * 贴图法线转 {@code spinDegrees} —— 这个顺序让旋转发生在"已经挪到位的原点"上，即<b>原地转</b>
     * （vanilla 的 {@code translate(-0.5,-0.5,-0.5)} 已把贴图中心对齐到原点，见 {@code ItemRenderer.java:124}）。</p>
     *
     * <p>{@code spinDegrees} 是<b>最终角度</b>：由调用方决定要不要乘 {@code windup}（第一人称要，
     * GUI 静态图标不要）；镜像也由调用方决定（当前：位移镜像、旋转<b>不</b>镜像）。</p>
     */
    private static void renderPestle(ItemRenderer itemRenderer, ItemStack stack, BakedModel pestle,
                                     PoseStack ms, MultiBufferSource buffer, int light, int overlay,
                                     float dx, float dy, float dz, float spinDegrees) {
        if (pestle == null) return;

        ms.pushPose();
        ms.translate(dx, dy, dz);
        ms.mulPose(Axis.ZP.rotationDegrees(spinDegrees));
        itemRenderer.render(stack, ItemDisplayContext.NONE, false, ms, buffer, light, overlay, pestle);
        ms.popPose();
    }

    /**
     * 画碗里的内容物（用物品自己的 FIXED 变换缩到碗内）。
     *
     * @param zOffset     世界/第一人称用 0.0625（靠近玩家 1 像素），GUI 用 0.5（GUI 里不靠近玩家）
     * @param totalPounds 累计捶捣次数（跨自动续用），每一下让物品在原地多转
     *                    {@code params.contentsSpinDegrees()} 度
     * @param contentsX   水平偏移（已含手别镜像与 windup；调用方算好，静止时为 0 = 不偏移）
     */
    private static void renderContents(ItemRenderer itemRenderer, MortarContents contents, boolean hasContents,
                                       LocalPlayer player, PoseStack ms, MultiBufferSource buffer,
                                       int light, int overlay, float zOffset, int totalPounds,
                                       MortarParams params, float contentsX) {
        if (!hasContents || player == null) return;

        ms.pushPose();
        // 水平偏移（= 杵的一半，往屏幕中间收）+ 起始 Y（R6：下移 2 像素 ⇒ 0.125 → 0）
        ms.translate(contentsX, CONTENTS_Y_BASE, zOffset);
        ms.scale(params.contentsScale(), params.contentsScale(), params.contentsScale());
        ms.translate(0.0f, -0.05f, 0.0f);
        // 每捣一下原地多转一点（绕贴图法线 = 屏幕平面内转，物品全程可见）。
        // 若想要"翻滚"效果（翻面），把 ZP 换成 YP —— 但扁平贴图物品转到 90°/270° 会侧成一条线。
        ms.mulPose(Axis.ZP.rotationDegrees(totalPounds * params.contentsSpinDegrees()));
        itemRenderer.renderStatic(contents.stack(), ItemDisplayContext.FIXED,
                light, overlay, ms, buffer, player.level(), 0);
        ms.popPose();
    }

    /**
     * 捶捣曲线：一个周期内杵的「抬起系数」，恒 ≥ 0（不会砸到钵下面）。
     *
     * <pre>
     * phase 0        → LIFT_END(0.45)              抬起：0 → +1（smoothstep，缓入缓出像抬手蓄力）
     * phase 0.45     → SLAM_PHASE(0.60)            下砸：+1 → 0（15% 周期走完全程 = 明显比抬起快）
     * phase 0.60     → 1.00                        贴合/停顿：0（杵停在碗里，留出研磨节奏）
     * </pre>
     *
     * <p><b>与音效的锚点</b>：{@code phase == MortarItem.SLAM_PHASE} 就是「砸到底」那一刻，
     * 手搓研钵的服务端在同一个 tick 播研磨音（见 {@code MortarItem.slamTick}）。两边共用同一个常量，
     * 所以「听到声音」= 「看到砸到底」。（碾钵的出声道仍是旧逻辑，见类注释。）</p>
     *
     * @param phase 周期内进度 [0, 1)
     * @return 抬起系数，范围 [0, 1]
     */
    private static float poundCurve(float phase) {
        // 抬起：smoothstep，0 → +1
        if (phase < LIFT_END) {
            float t = phase / LIFT_END;
            return t * t * (3f - 2f * t);
        }
        // 下砸：+1 → 0（匀速下落，比抬起快，做出"砸"的力度感；不过冲，避免砸穿钵底）
        if (phase < MortarItem.SLAM_PHASE) {
            float t = (phase - LIFT_END) / (MortarItem.SLAM_PHASE - LIFT_END);
            return 1f - t;
        }
        // 贴合/停顿
        return 0f;
    }
}
