package com.alben.createhandmade.item;

import com.alben.createhandmade.ServerConfig;
import com.alben.createhandmade.util.StickyUseSession;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModel;
import com.simibubi.create.foundation.item.render.CustomRenderedItemModelRenderer;
import com.simibubi.create.foundation.item.render.PartialItemModelRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * 搅拌杖第一人称自定义渲染：<b>起手（转正 + 前伸）</b> + <b>加工（绕盆心画圆的搅拌运动）</b>两阶段。
 *
 * <h3>计时</h3>
 * <p>起手进度用会话 tick（{@link StickyUseSession}）—— 连续搅拌只在第一次长按起手；加工相位用
 * 「会话加工 tick」= 会话 tick − 起手 ⇒ 跨自动续用连续（b2.6 修的就是这个相位）。</p>
 *
 * <h3>坐标参考系（与手锯<b>完全一致</b> ⇒ 手锯的实测定稿值可以直接搬）</h3>
 * <p>搅拌杖与手锯的模型父级都是 {@code item/handheld}，第一人称变换相同
 * （{@code rotation [0,-90,25]}、{@code translation [1.13,3.2,1.13]}、{@code scale 0.68}），
 * 而且两者的渲染器都<b>不做逆旋转</b> ⇒ translate / mulPose 处在同一个"侧着的"局部坐标系里。
 * 把它换算成视图方向（右手，实测推导）：</p>
 * <ul>
 *   <li>局部 <b>+X</b> ≈ 视图「后 0.906 + 上 0.423」⇒ <b>−X = 往前伸</b>（略往下）；</li>
 *   <li>局部 <b>+Y</b> ≈ 视图「上 0.906 + 前 0.423」⇒ 抬高 / 前伸；</li>
 *   <li>局部 <b>+Z</b> = 视图「左」⇒ 右手要乘 {@code modifier}（具体哪些项乘、哪些取反见 render 里的注释）。</li>
 * </ul>
 *
 * <h3>起手终点 = 正前方搅拌位（实机定稿）</h3>
 * <p>{@link #POS_X}/{@link #POS_Y}/{@link #POS_Z} 是起手插值的目标位移（都乘 {@code progress}）：
 * {@code POS_X} 往前往下伸、{@code POS_Y} 抬高、{@code POS_Z} 沿 {@code modifier} 把物品从身体一侧
 * 挪到正前方。三个值都是实机标定结果；改之前先看 {@code render()} 里 translate 的镜像注释。</p>
 *
 * <h3>加工段的搅拌运动（绕盆心画圆）</h3>
 * <p>加工段在基准位之上叠加一个水平小圆：{@code circleX = sin(2π·phase)·R·(−modifier)}、
 * {@code circleZ = cos(2π·phase)·R·modifier}（X、Z 都乘 {@code modifier}，X 再取反 —— 镜像系数实机定稿）。
 * 相位取自<b>会话加工 tick</b>（跨续用连续、不会每件归零）。</p>
 * <p><b>周期来源 = {@code processing_ticks}</b>（SERVER 配置，运行时读），并把它换算成<b>整数圈 / 件</b>：
 * {@code 圈数 = max(1, round(processing_ticks / 20))}、{@code 单圈周期 = processing_ticks / 圈数}
 * ⇒ 每件刚好走整数圈（相位在每件边界回到 0，不跳变），同时转速被钉在 ~1 圈/秒附近，
 * 避免两个极端：{@code processing_ticks} 很长时"一件一圈"慢成静止、很小时变成糊成一片的高速旋转。
 * 下限 {@link #STIR_CIRCLE_MIN_PERIOD_TICKS} 再兜住 {@code processing_ticks ≤ 5} 的极端配置。
 * 想要严格的"一件一圈"，把圈数写死成 1 即可（见 {@link #STIR_CIRCLE_TARGET_TICKS} 注释）。</p>
 *
 * <h3>可调项（当前值均为实机定稿）</h3>
 * <ul>
 *   <li>位姿：{@link #POS_X}（前伸）、{@link #POS_Y}（高度）、{@link #POS_Z}（居中，乘 {@code modifier}）。</li>
 *   <li>搅拌圆：{@link #STIR_CIRCLE_RADIUS}（0.1）、{@link #STIR_CIRCLE_TARGET_TICKS}（20t ≈ 1 圈/秒）、
 *       {@link #STIR_CIRCLE_MIN_PERIOD_TICKS}、{@link #STIR_CIRCLE_FADE_IN_TICKS}。</li>
 *   <li>旋转：见 {@code render()} 中 6 次 {@code mulPose} 前的注释（三明治自转 + XP/ZP/YP 收尾）。</li>
 * </ul>
 */
public class StirringStaffRenderer extends CustomRenderedItemModelRenderer {

    // ★ 起手过渡时长不写死：与 StirringStaffItem 读同一份 SERVER 配置（tools.stirring_staff.windup_ticks），
    //   在 render() 里运行时读 —— SERVER 配置只在进入世界后加载，静态字段会抛 checkState。

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
     * <p>要改成严格的"一件一圈"（processing 很长时会很慢）：把圈数直接写死
     * {@code float revolutions = 1f;}（见下面 render 里的那两行）。</p>
     */
    private static final float STIR_CIRCLE_TARGET_TICKS = 20f;

    /** 单圈周期下限：兜住 {@code processing_ticks ≤ 5} 的极端配置，避免高速旋转糊成一片。 */
    private static final float STIR_CIRCLE_MIN_PERIOD_TICKS = 5f;

    /** 圆运动渐入包络：起手完成后多少 tick 进入满幅（与手锯的 3t 一致，避免阶跃） */
    private static final float STIR_CIRCLE_FADE_IN_TICKS = 3f;

    @Override
    protected void render(ItemStack stack, CustomRenderedItemModel model, PartialItemModelRenderer renderer,
                          ItemDisplayContext transformType, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        Minecraft mc = Minecraft.getInstance();
        ItemRenderer itemRenderer = mc.getItemRenderer();
        LocalPlayer player = mc.player;

        boolean leftHand = transformType == ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
        boolean firstPerson = leftHand || transformType == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND;

        // 判断玩家是否正在使用这把搅拌杖
        boolean using = false;
        float progress = 0f;        // 起手进度 0 → 1
        int stirTicks = -1;         // 加工相位（会话 tick − 起手）；< 0 = 还在起手期内
        float stirProgress = 0f;    // 搅拌圆渐入包络 0 → 1
        int processingTicks = 1;    // 圆周期来源（tools.stirring_staff.processing_ticks）
        if (firstPerson && player != null && player.isUsingItem()) {
            ItemStack useItem = player.getUseItem();
            if (useItem == stack) {
                using = true;
                // 会话计时（连续搅拌只在第一次起手）；起手时长与 StirringStaffItem 读同一份 SERVER 配置
                InteractionHand useHand = leftHand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
                int elapsed = StickyUseSession.elapsedTicks(player, stack, useHand,
                        StickyUseSession.DEFAULT_STICKY_TICKS);
                if (elapsed < 0) elapsed = player.getTicksUsingItem();   // 兜底
                int windupTicks = Math.max(1, ServerConfig.INSTANCE.stirringStaffWindupTicks.get());
                progress = Math.min(1f, elapsed / (float) windupTicks);
                // ★ b2.6：加工相位（会话 tick − 起手）；起手期内为负 ⇒ 圆运动包络为 0
                stirTicks = elapsed - windupTicks;
                stirProgress = stirTicks <= 0 ? 0f : Math.min(1f, stirTicks / STIR_CIRCLE_FADE_IN_TICKS);
                // ★ b2.7：搅拌圆的周期来源（SERVER 配置，运行时读，不能进静态字段）
                processingTicks = Math.max(1, ServerConfig.INSTANCE.stirringStaffProcessingTicks.get());
            }
        }

        ms.pushPose();

        // 只有使用中才应用搅拌姿势，且随 progress 做插值
        if (using && progress > 0f) {
            int modifier = leftHand ? -1 : 1;

            // ★ b2.7 搅拌圆（调研方案 1：绕盆心画小圆）——
            //   周期 = processing_ticks，但换算成"整数圈 / 件"：相位在每件边界回到 0（不跳变），
            //   转速则被钉在 ~1 圈/秒（STIR_CIRCLE_TARGET_TICKS），规避"processing 很长 ⇒ 一圈慢成静止"。
            //   想要严格一件一圈：把下一行的 revolutions 换成 1f 即可。
            float revolutions = Math.max(1f, Math.round(processingTicks / STIR_CIRCLE_TARGET_TICKS));
            float circlePeriod = Math.max(STIR_CIRCLE_MIN_PERIOD_TICKS, processingTicks / revolutions);
            //   相位用「会话加工 tick」⇒ 跨自动续用连续，不会每件归零。
            float circlePhase = (stirTicks % circlePeriod) / circlePeriod;   // 0..1 循环
            float circleAngle = circlePhase * (float) (Math.PI * 2.0);
            float circleX = (float) Math.sin(circleAngle) * STIR_CIRCLE_RADIUS * stirProgress;
            float circleZ = (float) Math.cos(circleAngle) * STIR_CIRCLE_RADIUS * stirProgress * modifier;

            // 位移：起手插值到正前方基准位（POS_*），再叠加圆运动偏移。
            // 镜像（实机定稿）：基准 POS_X / POS_Y 不乘 modifier、POS_Z 乘 modifier；
            //                  圆偏移 X 乘 −modifier、Z 乘 modifier。
            ms.translate(POS_X * progress + circleX*(-modifier),
                    POS_Y * progress,
                    modifier * POS_Z * progress + circleZ);

            // 旋转（实机定稿，共 6 次 mulPose，乘序即下面的书写顺序；全部乘 progress 做起手插值）：
            //   1) ZP(45) · YP(−90·modifier) · ZP(−45)：三明治写法，等价于绕「板面内对角轴」
            //      (−0.707, +0.707, 0) 自转 −90°·modifier —— 把杖头从贴图对角线方向转出来；
            //   2) XP(−90·modifier) · ZP(135) · YP(−90·modifier)：把杖头摆成「斜指屏幕前方」。
            // 镜像：两次 YP 与一次 XP 乘 modifier；两次 ZP 不乘。
            ms.mulPose(Axis.ZP.rotationDegrees(45f * progress));
            ms.mulPose(Axis.YP.rotationDegrees(-90f  * modifier * progress));
            ms.mulPose(Axis.ZP.rotationDegrees(-45f  * progress));

            ms.mulPose(Axis.XP.rotationDegrees(-90f * modifier * progress));
            ms.mulPose(Axis.ZP.rotationDegrees(135f * progress));
            ms.mulPose(Axis.YP.rotationDegrees(-90f *  modifier *progress));
        }

        itemRenderer.render(stack, ItemDisplayContext.NONE, false, ms, buffer, light, overlay,
                model.getOriginalModel());

        ms.popPose();
    }
}
