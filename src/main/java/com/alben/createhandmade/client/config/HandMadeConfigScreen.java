package com.alben.createhandmade.client.config;

import com.alben.createhandmade.CreateHandMade;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.IconButton;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.createmod.catnip.gui.element.BoxElement;
import net.createmod.catnip.gui.widget.AbstractSimiWidget;
import net.createmod.catnip.theme.Color;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * 机械动力风格的配置界面（<b>自绘，不依赖 Cloth Config</b>）。
 *
 * <p>骨架和零件全部借用 Create / Catnip 自己的那套 UI：</p>
 * <ul>
 *   <li>窗口基类 {@link AbstractSimiScreen}（负责窗口居中、暗化背景、逐 tick 派发）；</li>
 *   <li>面板 {@link BoxElement}——Create 所有界面用的那种「深色底 + 渐变描边」盒子，
 *       两层描边 + 内发光都是代码画的，所以<b>不需要额外贴图</b>；</li>
 *   <li>数值用 {@link ScrollInput}（就是 Create 方块实体界面上滚轮调数字那种控件）；</li>
 *   <li>按钮用 {@link IconButton} + {@link AllIcons#I_CONFIG_SAVE} / {@link AllIcons#I_CONFIG_RESET} /
 *       {@link AllIcons#I_CONFIG_LOCKED}——Create 自己的配置界面上用的就是这几个图标；</li>
 *   <li>文字配色取 {@code UIRenderHelper.COLOR_TEXT*} 的同一组色值（#eeeeee / #a3a3a3 / #8ab6d6）。</li>
 * </ul>
 *
 * <p>数据层没变：仍然是 {@link ConfigCategorySpec} / {@link ConfigOption}，
 * 所以「主配置 8 页 + 女仆 4 页」「按页只读」「改动即时写回」这些语义与上一版完全一致。</p>
 *
 * <p>另外两点保留：打开时糊背景（{@link ScreenBackdrop}）、临时藏 HUD
 * （{@code Options.hideGui}，{@link #removed()} 里还原）。</p>
 */
public class HandMadeConfigScreen extends AbstractSimiScreen {

    // ==================== 版式 ====================
    private static final int WIDTH = 360;
    private static final int HEIGHT = 236;
    private static final int PADDING = 10;
    /** 左侧分类栏宽度。 */
    private static final int CAT_W = 112;
    private static final int CAT_ROW_H = 20;
    private static final int CAT_ROW_GAP = 2;
    /** 右侧每一行的高度。 */
    private static final int ROW_H = 24;
    private static final int ROW_GAP = 4;
    /** 滑块 / 输入框宽度。 */
    private static final int CONTROL_W = 118;
    private static final int CONTROL_H = 18;

    // ==================== 配色（与 Catnip 的文字色板同源） ====================
    private static final int TEXT = 0xFFEEEEEE;
    private static final int TEXT_DIM = 0xFFA3A3A3;
    private static final int TEXT_FAINT = 0xFF808080;
    /** Create 的浅蓝强调色。 */
    private static final int ACCENT = 0xFF8AB6D6;
    private static final int ACCENT_SOFT = 0x508AB6D6;
    private static final int BRASS = 0xFFB98A5A;
    private static final int ROW_HOVER = 0x18FFFFFF;
    private static final int ROW_SELECTED = 0x308AB6D6;

    private final Screen parent;
    private final List<ConfigCategorySpec> categories;
    private final Runnable saveCallback;
    private final boolean multiplayer;

    /** 面板（BoxElement 的两层描边 + 内发光都是代码画的，不需要贴图）。 */
    private BoxElement panel;
    private final ScreenBackdrop backdrop = new ScreenBackdrop();

    /** 右侧当前页的控件。 */
    private final List<AbstractWidget> rowWidgets = new ArrayList<>();
    /** 布尔开关的按钮 → 它对应的选项（点一下取反）。 */
    private final Map<IconButton, ConfigOption.Bool> boolButtons = new IdentityHashMap<>();
    /** 数值控件 → 选项（为了悬停说明）。 */
    private final Map<AbstractWidget, ConfigOption> hoverTargets = new IdentityHashMap<>();

    private IconButton doneButton;
    private IconButton resetButton;
    private int selected;
    private int categoryScroll;

    private boolean hideGuiBefore;
    private boolean hideGuiCaptured;

    public HandMadeConfigScreen(Screen parent, List<ConfigCategorySpec> categories,
                                Runnable saveCallback, boolean multiplayer) {
        super(Component.translatable("config.create_hand_made.title"));
        this.parent = parent;
        this.categories = List.copyOf(categories);
        this.saveCallback = saveCallback;
        this.multiplayer = multiplayer;
        // 必须在 super.init() 之前设置窗口尺寸（AbstractSimiScreen 的约定）
        setWindowSize(WIDTH, HEIGHT);
    }

    // ==================== 构建 ====================

    @Override
    protected void init() {
        super.init();   // 算出 guiLeft / guiTop

        // 藏 HUD：只在第一次 init 时记原值（窗口缩放会重跑 init）
        if (!hideGuiCaptured) {
            hideGuiBefore = this.minecraft.options.hideGui;
            hideGuiCaptured = true;
        }
        this.minecraft.options.hideGui = true;

        // 面板（BoxElement 画两层描边 + 内发光，位置是内容区左上角）
        // ★ 不能链式调用：at()/withBounds() 在 RenderElement 接口上是泛型 <T extends RenderElement> T，
        //   链起来静态类型会塌回接口，后面 withBackground 就找不到了。
        BoxElement box = new BoxElement();
        box.at(guiLeft + 3, guiTop + 3);
        box.withBounds(WIDTH - 6, HEIGHT - 6);
        box.withBackground(new Color(0xF0_1D1F24, true));
        box.gradientBorder(new Color(0xFF_4C525B, true), new Color(0xA0_212429, true));
        this.panel = box;

        rowWidgets.clear();
        boolButtons.clear();
        hoverTargets.clear();

        int buttonY = guiTop + HEIGHT - PADDING - 16;
        doneButton = new IconButton(guiLeft + WIDTH - PADDING - 16, buttonY, AllIcons.I_CONFIG_SAVE);
        doneButton.setToolTip(Component.translatable("gui.done"));
        doneButton.withCallback(this::onClose);
        addRenderableWidget(doneButton);

        resetButton = new IconButton(guiLeft + WIDTH - PADDING - 16 - 20, buttonY, AllIcons.I_CONFIG_RESET);
        resetButton.setToolTip(Component.translatable("config.create_hand_made.reset"));
        resetButton.withCallback(this::resetCurrentCategory);
        addRenderableWidget(resetButton);

        selectCategory(Math.min(selected, Math.max(0, categories.size() - 1)));
    }

    private void selectCategory(int index) {
        if (index < 0 || index >= categories.size()) {
            return;
        }
        selected = index;
        if (resetButton != null) {
            resetButton.visible = !pageReadOnly();
        }
        rebuildRowWidgets();
    }

    /** 当前页是否只读：多人游戏 + 该页是服务端权威（COMMON 的本地配置在多人下照样能改）。 */
    private boolean pageReadOnly() {
        return multiplayer && categories.get(selected).serverAuthoritative;
    }

    private void rebuildRowWidgets() {
        for (AbstractWidget widget : rowWidgets) {
            removeWidget(widget);
        }
        rowWidgets.clear();
        boolButtons.clear();
        hoverTargets.clear();
        if (pageReadOnly()) {
            return;
        }
        for (Placement placement : layout()) {
            addRowWidget(placement);
        }
    }

    private void addRowWidget(Placement placement) {
        ConfigOption option = placement.option;
        int controlX = controlX();
        int controlY = placement.y + (placement.height - CONTROL_H) / 2;

        if (option instanceof ConfigOption.Int integer) {
            track(new IntSlider(controlX, controlY, CONTROL_W, CONTROL_H, integer), option);
        } else if (option instanceof ConfigOption.Bool bool) {
            IconButton toggle = new IconButton(controlX + CONTROL_W - 16, controlY, 16, 16,
                    bool.get() ? AllIcons.I_CONFIRM : AllIcons.I_DISABLE);
            toggle.green = bool.get();
            toggle.withCallback(() -> {
                bool.set(!bool.get());
                toggle.setIcon(bool.get() ? AllIcons.I_CONFIRM : AllIcons.I_DISABLE);
                toggle.green = bool.get();
            });
            boolButtons.put(toggle, bool);
            track(toggle, option);
        }
    }

    private void track(AbstractWidget widget, ConfigOption option) {
        hoverTargets.put(widget, option);
        rowWidgets.add(widget);
        addRenderableWidget(widget);
    }

    // ==================== 版式计算 ====================

    private record Placement(ConfigOption option, int y, int height) {
    }

    /** 分类栏左缘 / 右缘。 */
    private int catX() {
        return guiLeft + PADDING;
    }

    private int catTop() {
        return guiTop + 30;
    }

    private int catBottom() {
        return guiTop + HEIGHT - PADDING - 20;
    }

    /** 右侧内容区左缘。 */
    private int contentX() {
        return catX() + CAT_W + PADDING;
    }

    private int contentRight() {
        return guiLeft + WIDTH - PADDING;
    }

    private int controlX() {
        return contentRight() - CONTROL_W;
    }

    /** 右侧每一项的纵向位置：整块垂直居中。 */
    private List<Placement> layout() {
        ConfigCategorySpec category = categories.get(selected);
        int total = 0;
        for (ConfigOption option : category.options) {
            total += ROW_H + ROW_GAP;
        }
        if (total > 0) {
            total -= ROW_GAP;
        }
        int available = catBottom() - catTop();
        int y = catTop() + Math.max(0, (available - total) / 2);

        List<Placement> placements = new ArrayList<>(category.options.size());
        for (ConfigOption option : category.options) {
            int height = ROW_H;
            placements.add(new Placement(option, y, height));
            y += height + ROW_GAP;
        }
        return placements;
    }

    private int maxCategoryScroll() {
        int content = categories.size() * (CAT_ROW_H + CAT_ROW_GAP) - CAT_ROW_GAP;
        int visible = catBottom() - catTop();
        return Math.max(0, content - visible);
    }

    /** 分类行的屏幕矩形（i 为分类序号）；被滚出可视区时返回 null。 */
    private int[] catRowRect(int index) {
        int y = catTop() + index * (CAT_ROW_H + CAT_ROW_GAP) - categoryScroll;
        if (y < catTop() || y + CAT_ROW_H > catBottom()) {
            return null;
        }
        return new int[]{catX(), y, CAT_W, CAT_ROW_H};
    }

    // ==================== 绘制 ====================

    @Override
    protected void renderWindowBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (this.minecraft.level == null) {
            // 主菜单里没有可糊的世界，铺原版泥土底盖住上一屏
            renderBackground(graphics);
            return;
        }
        // 存档里只留模糊：不叠原版的半透明黑罩（深色面板本身已经够压住视线了）
        backdrop.blur(partialTick);
    }

    @Override
    protected void renderWindow(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        panel.render(graphics);

        // 标题 + 下方一道浅蓝分隔线
        graphics.drawString(this.font, this.title,
                guiLeft + (WIDTH - this.font.width(this.title)) / 2, guiTop + 9, TEXT, true);
        graphics.fill(guiLeft + PADDING, guiTop + 21, guiLeft + WIDTH - PADDING, guiTop + 22, ACCENT_SOFT);
        // 分类栏与内容区之间的竖分隔
        graphics.fill(catX() + CAT_W + PADDING / 2, catTop(),
                catX() + CAT_W + PADDING / 2 + 1, catBottom(), 0x30FFFFFF);

        renderCategories(graphics, mouseX, mouseY);
        renderContent(graphics, mouseX, mouseY);

        if (pageReadOnly()) {
            drawLockedBadge(graphics);
        }
    }

    private void renderCategories(GuiGraphics graphics, int mouseX, int mouseY) {
        for (int i = 0; i < categories.size(); i++) {
            int[] rect = catRowRect(i);
            if (rect == null) {
                continue;
            }
            boolean hovered = mouseX >= rect[0] && mouseX < rect[0] + rect[2]
                    && mouseY >= rect[1] && mouseY < rect[1] + rect[3];
            boolean isSelected = i == selected;

            if (isSelected) {
                graphics.fill(rect[0], rect[1], rect[0] + rect[2], rect[1] + rect[3], ROW_SELECTED);
                graphics.fill(rect[0], rect[1], rect[0] + 2, rect[1] + rect[3], BRASS);
            } else if (hovered) {
                graphics.fill(rect[0], rect[1], rect[0] + rect[2], rect[1] + rect[3], ROW_HOVER);
            }

            Component title = categories.get(i).title;
            graphics.drawString(this.font, title, rect[0] + 8, rect[1] + 6,
                    isSelected ? ACCENT : TEXT_DIM, false);
        }
    }

    private void renderContent(GuiGraphics graphics, int mouseX, int mouseY) {
        ConfigCategorySpec category = categories.get(selected);

        if (pageReadOnly()) {
            int y = catTop() + 4;
            for (String key : new String[]{
                    "config.create_hand_made.multiplayer_hint",
                    "config.create_hand_made.server_config_hint"}) {
                y = drawWrapped(graphics, Component.translatable(key), contentX(),
                        contentRight(), y, TEXT_DIM) + 4;
            }
            return;
        }

        // 「本地配置」页面（COMMON 类型的本地文件）在多人下也能改，点明一下。
        // ★ 目前没有任何页走到这里（女仆 `blocked_mods` 已改为代码里写死），机制留着给将来的本地配置用。
        // ★ 画在内容区**底部**（按钮上方），不要贴着标题——之前放 catTop()-14 会和标题/分隔线撞在一起。
        if (!category.serverAuthoritative) {
            graphics.drawString(this.font,
                    Component.translatable("config.create_hand_made.local_config_hint"),
                    contentX(), catBottom() - 10, TEXT_FAINT, false);
        }

        for (Placement placement : layout()) {
            ConfigOption option = placement.option;
            int labelY = placement.y + 7;
            graphics.drawString(this.font, option.label, contentX(), labelY, TEXT, false);

            if (option instanceof ConfigOption.Bool bool) {
                // 「开 / 关」文字贴在按钮左侧
                Component state = Component.translatable(bool.get() ? "options.on" : "options.off");
                graphics.drawString(this.font, state,
                        controlX() + CONTROL_W - 20 - this.font.width(state), labelY,
                        bool.get() ? 0xFF88F788 : TEXT_FAINT, false);
            }
        }
    }

    /** Create 那个「锁」图标：只读页右上角的小徽记。 */
    private void drawLockedBadge(GuiGraphics graphics) {
        AllIcons.I_CONFIG_LOCKED.render(graphics, contentRight() - 16, guiTop + 8);
    }

    /** 按宽度折行绘制，返回下一行的 y。 */
    private int drawWrapped(GuiGraphics graphics, Component text, int x, int right,
                            int y, int color) {
        List<FormattedCharSequence> lines = this.font.split(text, right - x);
        for (FormattedCharSequence line : lines) {
            graphics.drawString(this.font, line, x, y, color, false);
            y += 10;
        }
        return y;
    }

    @Override
    protected void renderWindowForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 控件（滑块 / 开关）的悬停说明
        for (Map.Entry<AbstractWidget, ConfigOption> entry : hoverTargets.entrySet()) {
            AbstractWidget widget = entry.getKey();
            if (!widget.visible || !widget.isMouseOver(mouseX, mouseY)) {
                continue;
            }
            List<Component> tooltip = entry.getValue().tooltip;
            if (!tooltip.isEmpty()) {
                graphics.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
            }
            return;
        }

        // 右侧行的标签区域也算热区（不用非得压在控件上）
        if (!pageReadOnly() && mouseX >= contentX() && mouseX < contentRight()) {
            for (Placement placement : layout()) {
                if (mouseY < placement.y || mouseY >= placement.y + placement.height) {
                    continue;
                }
                List<Component> tooltip = placement.option.tooltip;
                if (!tooltip.isEmpty()) {
                    graphics.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
                }
                return;
            }
        }
    }

    // ==================== 交互 ====================

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }
        for (int i = 0; i < categories.size(); i++) {
            int[] rect = catRowRect(i);
            if (rect == null) {
                continue;
            }
            if (mouseX >= rect[0] && mouseX < rect[0] + rect[2]
                    && mouseY >= rect[1] && mouseY < rect[1] + rect[3]) {
                selectCategory(i);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX >= catX() && mouseX < catX() + CAT_W && maxCategoryScroll() > 0) {
            categoryScroll = (int) Math.max(0, Math.min(maxCategoryScroll(),
                    categoryScroll - delta * (CAT_ROW_H + CAT_ROW_GAP)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private void resetCurrentCategory() {
        for (ConfigOption option : categories.get(selected).options) {
            option.resetToDefault();
        }
        rebuildRowWidgets();
    }

    /** 关界面（完成 / Esc / 被顶掉）都走这里：保存 + 回上一级。 */
    @Override
    public void onClose() {
        if (saveCallback != null) {
            try {
                saveCallback.run();
            } catch (Throwable t) {
                CreateHandMade.LOGGER.error("[HandMade] 保存配置失败", t);
            }
        }
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public void removed() {
        this.minecraft.options.hideGui = hideGuiBefore;
        hideGuiCaptured = false;
        backdrop.close();
        super.removed();
    }

    // ==================== 给外部的提示文本用（保持与旧版一致的语义） ====================

    /** 供调试/自检：当前页标题。 */
    public Component currentCategoryTitle() {
        return categories.get(selected).title;
    }

    // ==================== 整数拖动条 ====================

    /**
     * 机械动力配色、<b>可以拖动</b>的整数条。
     *
     * <p>为什么不用 Create 的 {@code ScrollInput}：它只支持滚轮/点左右箭头，
     * 而这批参数（起手、加工、冷却）玩家习惯「拖一格看一格」，所以自己画一条：
     * 深色凹槽 + 底部浅蓝进度 + 右侧黄铜色旋钮，拖动、点击定位、滚轮微调三种都支持。</p>
     *
     * <p>值域来自 {@code defineInRange}，所以拖到底也出不了非法值。</p>
     */
    private static final class IntSlider extends AbstractSimiWidget {

        /**
         * 旋钮宽度。
         *
         * <p>★ 必须是「可走距离 = 控件宽 - 旋钮宽 - 2」的关系，否则会出现两个经典毛病：
         * 0% 时旋钮向左探出控件、100% 时到不了右端（之前那版就是）。</p>
         */
        private static final int KNOB_W = 6;

        private final ConfigOption.Int option;

        IntSlider(int x, int y, int width, int height, ConfigOption.Int option) {
            super(x, y, width, height);
            this.option = option;
        }

        private double fraction() {
            int span = option.max() - option.min();
            if (span <= 0) {
                return 0;
            }
            return (option.get() - option.min()) / (double) span;
        }

        /** 旋钮能在轨道里滑动的距离（像素）。 */
        private int travel() {
            return Math.max(1, this.width - KNOB_W - 2);
        }

        /** 当前值对应的旋钮左缘（相对控件左上角）。 */
        private int knobOffset() {
            return 1 + (int) Math.round(travel() * fraction());
        }

        private void setFromMouse(double mouseX) {
            // 以「旋钮中心」对齐鼠标，和绘制用同一套坐标系
            double t = (mouseX - (this.getX() + 1) - KNOB_W / 2.0) / travel();
            t = Math.max(0.0, Math.min(1.0, t));
            int span = option.max() - option.min();
            option.set(option.min() + (int) Math.round(t * span));
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int x = this.getX();
            int y = this.getY();
            int w = this.width;
            int h = this.height;
            boolean hovered = this.isHovered();

            // 凹槽
            graphics.fill(x, y, x + w, y + h, 0xFF17181B);
            graphics.fill(x, y, x + w, y + 1, hovered ? 0x40FFFFFF : 0x28FFFFFF);
            graphics.fill(x, y + h - 1, x + w, y + h, 0x40_000000);

            // 旋钮位置：0% 贴左内缘、100% 贴右内缘
            int knobX = x + knobOffset();

            // 底部进度：从左内缘铺到旋钮中心
            int center = knobX + KNOB_W / 2;
            graphics.fill(x + 1, y + h - 4, center, y + h - 2, ACCENT);

            // 旋钮（黄铜色竖条）
            graphics.fill(knobX, y + 1, knobX + KNOB_W, y + h - 1, hovered ? 0xFFE0B47A : BRASS);
            graphics.fill(knobX, y + 1, knobX + KNOB_W, y + 2, 0x60FFFFFF);

            // 条里只显示「当前值」——标签由 renderContent 在行左边画一次就够了，
            // 这里再画一次标签会变成两层字叠在一起。只调一次 drawString，用原版自带的阴影。
            Font font = Minecraft.getInstance().font;
            Component value = Component.literal(Integer.toString(option.get()));
            graphics.drawString(font, value, x + (w - font.width(value)) / 2, y + (h - 8) / 2,
                    TEXT, true);
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            setFromMouse(mouseX);
        }

        @Override
        protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
            setFromMouse(mouseX);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
            option.set(option.get() + (int) Math.signum(delta));
            return true;
        }

        @Override
        public void updateWidgetNarration(NarrationElementOutput output) {
            this.defaultButtonNarrationText(output);
        }
    }
}
