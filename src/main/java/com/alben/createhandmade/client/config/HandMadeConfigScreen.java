package com.alben.createhandmade.client.config;

import com.alben.createhandmade.CreateHandMade;
import com.simibubi.create.foundation.gui.AllIcons;
import com.simibubi.create.foundation.gui.widget.IconButton;
import net.createmod.catnip.gui.AbstractSimiScreen;
import net.createmod.catnip.gui.element.BoxElement;
import net.createmod.catnip.gui.widget.AbstractSimiWidget;
import net.createmod.catnip.theme.Color;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.lwjgl.glfw.GLFW;

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
 * 所以「主配置 8 页 + 女仆 3 页」「按页只读」「改动即时写回」这些语义与上一版完全一致。</p>
 *
 * <p>两处交互强化：</p>
 * <ul>
 *   <li><b>只读页照常显示数值</b>（灰掉、不可拖），不再是一片空白——多人游戏里玩家至少要能看见
 *       服务端当前调成了多少；</li>
 *   <li><b>可见的改动痕迹</b>：改过的项在标签右侧打一个黄铜点；「恢复本页默认」按下之后按钮变成
 *       「撤销」，可以把刚才那一页的值原样放回去（{@link ConfigOption#snapshot()}）。</li>
 * </ul>
 *
 * <p>还有三件「用起来别别扭」的事：</p>
 * <ul>
 *   <li><b>窗口高度跟着分类数量走</b>：分类多到一屏放不下时才出现滚动条（而不是死盯着 236 px）；</li>
 *   <li><b>标签超宽就截断加省略号</b>，不会压到滑块或值域上；</li>
 *   <li><b>键盘也能调</b>：↑↓ 选行、←→ 改值（Shift 十步）、PageUp/PageDown 换页、Enter 保存退出。</li>
 * </ul>
 *
 * <p>另外两点保留：打开时糊背景（{@link ScreenBackdrop}）、临时藏 HUD
 * （{@code Options.hideGui}，{@link #removed()} 里还原）。</p>
 */
public class HandMadeConfigScreen extends AbstractSimiScreen {

    // ==================== 版式 ====================
    private static final int WIDTH = 360;
    /**
     * 窗口最矮高度。
     *
     * <p>高度不再是死数：{@code 60 + 分类内容高} 就是「一屏正好放下全部分类」所需的高度
     * （上边 30 px 标题、下边 30 px 按钮），8 个分类算出来正好 234 ≈ 原来的 236。
     * 分类多于此值就靠滚动条；反过来屏幕太矮时压到这个下限，再矮就只能建议调 GUI 缩放。</p>
     */
    private static final int MIN_HEIGHT = 200;
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
    /** 分类栏右侧那条滚动条的宽度。 */
    private static final int CAT_SCROLLBAR_W = 2;
    /** 分类栏上下「还有内容」的渐隐高度。 */
    private static final int CAT_FADE = 6;
    /** 鼠标停多久才弹说明（毫秒）——立即弹会在扫过整页时糊一脸。 */
    private static final long TOOLTIP_DELAY_MS = 250L;

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

    /** 本屏的实际窗口高度：按分类数量算，再被屏幕高度压（见 {@link #MIN_HEIGHT}）。 */
    private final int windowHeight;

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

    /** 「恢复本页默认」之前那一页的值；非空表示重置按钮当前是「撤销」。 */
    private final Map<ConfigOption, Object> resetSnapshot = new IdentityHashMap<>();
    /** 当前悬停的项与开始悬停的时刻（配合 {@link #TOOLTIP_DELAY_MS} 延迟弹说明）。 */
    private ConfigOption hoveredOption;
    private long hoveredSince;
    /** 键盘操作选中第几行（↑↓ / Tab 移动）；只在动过键盘之后才画焦点框。 */
    private int focusedRow;
    private boolean keyboardFocusShown;

    private boolean hideGuiBefore;
    private boolean hideGuiCaptured;

    public HandMadeConfigScreen(Screen parent, List<ConfigCategorySpec> categories,
                                Runnable saveCallback, boolean multiplayer) {
        super(Component.translatable("config.create_hand_made.title"));
        this.parent = parent;
        this.categories = List.copyOf(categories);
        this.saveCallback = saveCallback;
        this.multiplayer = multiplayer;
        // 窗口高度跟着分类数量走：让「装了女仆兼容（11 类）」也能一屏放下，装不下才滚，
        // 顺便避免小窗口下窗口底边伸出屏幕外。必须在 super.init() 之前设（AbstractSimiScreen 的约定）。
        int categoryContent = categories.size() * (CAT_ROW_H + CAT_ROW_GAP) - CAT_ROW_GAP;
        int tallestPage = 0;
        for (ConfigCategorySpec category : categories) {
            tallestPage = Math.max(tallestPage,
                    category.options.size() * (ROW_H + ROW_GAP) - ROW_GAP);
        }
        int needed = 60 + Math.max(categoryContent, tallestPage);
        int available = Math.max(120, Minecraft.getInstance().getWindow().getGuiScaledHeight() - 40);
        this.windowHeight = Math.min(Math.max(needed, MIN_HEIGHT), available);
        setWindowSize(WIDTH, this.windowHeight);
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
        box.withBounds(WIDTH - 6, windowHeight - 6);
        box.withBackground(new Color(0xF0_1D1F24, true));
        box.gradientBorder(new Color(0xFF_4C525B, true), new Color(0xA0_212429, true));
        this.panel = box;

        rowWidgets.clear();
        boolButtons.clear();
        hoverTargets.clear();
        resetSnapshot.clear();
        hoveredOption = null;

        int buttonY = guiTop + windowHeight - PADDING - 16;
        doneButton = new IconButton(guiLeft + WIDTH - PADDING - 16, buttonY, AllIcons.I_CONFIG_SAVE);
        doneButton.setToolTip(Component.translatable("gui.done"));
        doneButton.withCallback(this::onDone);
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
        // 换页就把撤销快照丢掉：撤销只对「刚按下的那一页」有意义，跨页还原会莫名其妙
        if (index != selected) {
            resetSnapshot.clear();
        }
        selected = index;
        focusedRow = 0;
        refreshResetButton();
        rebuildRowWidgets();
    }

    /** 点按钮 / 换页的那点「咔哒」声（原版按钮的 {@code UI_BUTTON_CLICK}，音高 1.0）。 */
    private void playClick() {
        Minecraft.getInstance().getSoundManager()
                .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
    }

    /** 「完成」：先响一声再走保存流程（Esc 关界面不会响）。 */
    private void onDone() {
        playClick();
        onClose();
    }

    /** 当前页是否只读：多人游戏 + 该页是服务端权威（COMMON 的本地配置在多人下照样能改）。 */
    private boolean pageReadOnly() {
        return multiplayer && categories.get(selected).serverAuthoritative;
    }

    /** 重置按钮有两副面孔：有快照时是「撤销」（回退箭头），否则是「恢复本页默认」。 */
    private void refreshResetButton() {
        if (resetButton == null) {
            return;
        }
        boolean canUndo = !resetSnapshot.isEmpty();
        resetButton.visible = !pageReadOnly();
        resetButton.setIcon(canUndo ? AllIcons.I_CONFIG_BACK : AllIcons.I_CONFIG_RESET);
        resetButton.setToolTip(Component.translatable(canUndo
                ? "config.create_hand_made.undo_reset"
                : "config.create_hand_made.reset"));
        resetButton.withCallback(canUndo ? this::undoReset : this::resetCurrentCategory);
    }

    private void rebuildRowWidgets() {
        for (AbstractWidget widget : rowWidgets) {
            removeWidget(widget);
        }
        rowWidgets.clear();
        boolButtons.clear();
        hoverTargets.clear();
        hoveredOption = null;

        // 只读页也把控件建出来，只是禁用它——空页面看不出服务端当前调成了多少
        boolean readOnly = pageReadOnly();
        for (Placement placement : layout()) {
            addRowWidget(placement, readOnly);
        }
        // 换页 / 重建后行数可能变少，把键盘焦点夹回范围内
        focusedRow = Math.max(0, Math.min(focusedRow,
                Math.max(0, categories.get(selected).options.size() - 1)));
    }

    private void addRowWidget(Placement placement, boolean readOnly) {
        ConfigOption option = placement.option;
        int controlX = controlX();
        int controlY = placement.y + (placement.height - CONTROL_H) / 2;

        if (option instanceof ConfigOption.Int integer) {
            track(new IntSlider(controlX, controlY, CONTROL_W, CONTROL_H, integer, readOnly), option);
        } else if (option instanceof ConfigOption.Bool bool && !readOnly) {
            // 只读时不放按钮（点了也没用），renderContent 里直接把「开 / 关」写在控件位右侧
            IconButton toggle = new IconButton(controlX + CONTROL_W - 16, controlY, 16, 16,
                    bool.get() ? AllIcons.I_CONFIRM : AllIcons.I_DISABLE);
            toggle.green = bool.get();
            toggle.withCallback(() -> {
                bool.set(!bool.get());
                toggle.setIcon(bool.get() ? AllIcons.I_CONFIRM : AllIcons.I_DISABLE);
                toggle.green = bool.get();
                playClick();
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
        return guiTop + windowHeight - PADDING - 20;
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

    /**
     * 超宽就截断并补一个省略号。
     *
     * <p>中文标签一般放得下，但英文 / 其他语言翻译可能一长串，硬画会直接压到滑块和值域上。
     * 用 {@code plainSubstrByWidth} 而不是自己数宽度，是因为它按渲染宽度逐字累加，
     * 全角、组合字符都不会算错。</p>
     */
    private Component clip(Component text, int maxWidth) {
        if (maxWidth <= 0 || this.font.width(text) <= maxWidth) {
            return text;
        }
        String raw = text.getString();
        String dots = "…";
        int room = Math.max(0, maxWidth - this.font.width(dots));
        return Component.literal(this.font.plainSubstrByWidth(raw, room) + dots)
                .withStyle(text.getStyle());
    }


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

        // 键盘提示：不带箭头字符（原版字体不一定有），只用文字描述，底部左侧一条灰字
        if (doneButton != null) {
            graphics.drawString(this.font,
                    Component.translatable("config.create_hand_made.keyboard_hint"),
                    guiLeft + PADDING, doneButton.getY() + 4, TEXT_FAINT, false);
        }

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

            Component title = clip(categories.get(i).title, rect[2] - 8 - 4);
            graphics.drawString(this.font, title, rect[0] + 8, rect[1] + 6,
                    isSelected ? ACCENT : TEXT_DIM, false);
        }

        // 装了女仆兼容时分类有 11 项，可视高度只放得下 8 项——必须让人看出「下面还有」
        renderCategoryScrollbar(graphics);
        drawCategoryFade(graphics);
    }

    /** 分类栏右侧的细滚动条（滚轮可滚时才有）。 */
    private void renderCategoryScrollbar(GuiGraphics graphics) {
        int max = maxCategoryScroll();
        if (max <= 0) {
            return;
        }
        int x = catX() + CAT_W - CAT_SCROLLBAR_W;
        int top = catTop();
        int height = catBottom() - top;
        int content = Math.max(height, categories.size() * (CAT_ROW_H + CAT_ROW_GAP) - CAT_ROW_GAP);
        int knobH = Math.max(12, height * height / content);
        int knobY = top + (int) Math.round((height - knobH) * (categoryScroll / (double) max));

        graphics.fill(x, top, x + CAT_SCROLLBAR_W, top + height, 0x20FFFFFF);
        graphics.fill(x, knobY, x + CAT_SCROLLBAR_W, knobY + knobH, BRASS);
    }

    /**
     * 分类栏上下各画一道渐隐，盖住滚出去的那半行。
     *
     * <p>逐行调 alpha（面板底色 {@code 0x1D1F24}），不做贴图。只在<b>那一侧真有内容被切掉</b>时画：
     * 滚到顶就没有上渐隐、滚到底就没有下渐隐，否则首末两行的文字会被无谓地压暗。</p>
     */
    private void drawCategoryFade(GuiGraphics graphics) {
        int max = maxCategoryScroll();
        if (max <= 0) {
            return;
        }
        int left = catX();
        int right = catX() + CAT_W - CAT_SCROLLBAR_W - 1;
        for (int i = 0; i < CAT_FADE; i++) {
            int alpha = 0xE0 * (CAT_FADE - i) / CAT_FADE;
            int color = (alpha << 24) | 0x1D1F24;
            if (categoryScroll > 0) {
                graphics.fill(left, catTop() + i, right, catTop() + i + 1, color);
            }
            if (categoryScroll < max) {
                graphics.fill(left, catBottom() - i - 1, right, catBottom() - i, color);
            }
        }
    }

    private void renderContent(GuiGraphics graphics, int mouseX, int mouseY) {
        ConfigCategorySpec category = categories.get(selected);
        boolean readOnly = pageReadOnly();

        // 只读时把「本页由服务端决定」写在底部按钮上方，不挤占数值区
        if (readOnly) {
            graphics.drawString(this.font,
                    Component.translatable("config.create_hand_made.readonly_hint"),
                    contentX(), catBottom() - 10, TEXT_FAINT, false);
        } else if (!category.serverAuthoritative) {
            // 「本地配置」页面（COMMON 类型的本地文件）在多人下也能改，点明一下。
            // ★ 目前没有任何页走到这里，机制留着给将来的本地配置用。
            // ★ 画在内容区**底部**（按钮上方），不要贴着标题——之前放 catTop()-14 会和标题/分隔线撞在一起。
            graphics.drawString(this.font,
                    Component.translatable("config.create_hand_made.local_config_hint"),
                    contentX(), catBottom() - 10, TEXT_FAINT, false);
        }

        List<Placement> placements = layout();
        for (int row = 0; row < placements.size(); row++) {
            Placement placement = placements.get(row);
            ConfigOption option = placement.option;
            int labelY = placement.y + 7;

            // 键盘焦点框（只在动过键盘之后画），鼠标玩家不会看到多余方框
            if (keyboardFocusShown && row == focusedRow && !readOnly) {
                graphics.fill(contentX() - 3, placement.y + 1,
                        contentRight(), placement.y + 2, ACCENT_SOFT);
                graphics.fill(contentX() - 3, placement.y + placement.height - 1,
                        contentRight(), placement.y + placement.height, ACCENT_SOFT);
                graphics.fill(contentX() - 3, placement.y + 1,
                        contentX() - 2, placement.y + placement.height - 1, ACCENT_SOFT);
            }

            // 值域标签占的那点宽度先从标签里扣掉，否则长标签会被值域压住（或者值域被迫不画）
            int reserved = option instanceof ConfigOption.Int integer
                    ? this.font.width(integer.min() + "-" + integer.max()) + 8
                    : 0;
            int labelMax = controlX() - contentX() - 6 - reserved;
            Component label = clip(option.label, labelMax);
            graphics.drawString(this.font, label, contentX(), labelY, readOnly ? TEXT_DIM : TEXT, false);

            // 偏离默认值的项：标签右侧点一颗黄铜色小点（只读页不点，否则满屏都是点）
            if (!readOnly && !option.isDefault()) {
                int dotX = contentX() + this.font.width(label) + 5;
                graphics.fill(dotX, labelY + 3, dotX + 3, labelY + 6, BRASS);
            }

            if (option instanceof ConfigOption.Int integer) {
                // 值域贴在滑块左侧、右对齐；标签太长时宁可不画，也不压字
                Component range = Component.literal(integer.min() + "-" + integer.max());
                int rangeX = controlX() - 6 - this.font.width(range);
                if (rangeX >= contentX() + this.font.width(label) + 12) {
                    graphics.drawString(this.font, range, rangeX, labelY, TEXT_FAINT, false);
                }
            } else if (option instanceof ConfigOption.Bool bool) {
                // 「开 / 关」文字贴在按钮左侧；只读页没有按钮，就贴到控件位右缘
                Component state = Component.translatable(bool.get() ? "options.on" : "options.off");
                int stateRight = readOnly ? controlX() + CONTROL_W : controlX() + CONTROL_W - 20;
                int stateColor = bool.get() ? (readOnly ? 0xFF5F9E5F : 0xFF88F788) : TEXT_FAINT;
                graphics.drawString(this.font, state, stateRight - this.font.width(state), labelY,
                        stateColor, false);
            }
        }
    }

    /** Create 那个「锁」图标：只读页右上角的小徽记。 */
    private void drawLockedBadge(GuiGraphics graphics) {
        AllIcons.I_CONFIG_LOCKED.render(graphics, contentRight() - 16, guiTop + 8);
    }

    @Override
    protected void renderWindowForeground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        ConfigOption hovered = findHoveredOption(mouseX, mouseY);
        if (hovered == null) {
            hoveredOption = null;
            return;
        }
        long now = Util.getMillis();
        if (hovered != hoveredOption) {
            // 刚移上去：记时间，先不弹
            hoveredOption = hovered;
            hoveredSince = now;
            return;
        }
        if (now - hoveredSince < TOOLTIP_DELAY_MS) {
            return;
        }

        List<Component> lines = new ArrayList<>(hovered.tooltip);
        lines.add(Component.translatable("config.create_hand_made.default_value", hovered.defaultText())
                .withStyle(ChatFormatting.DARK_GRAY));
        // 值域在两端的行里可能被长标签挤掉，说明里再给一份兜底
        if (hovered instanceof ConfigOption.Int integer) {
            lines.add(Component.translatable("config.create_hand_made.value_range",
                            Component.literal(integer.min() + "-" + integer.max()))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        graphics.renderComponentTooltip(this.font, lines, mouseX, mouseY);
    }

    /** 鼠标下面是哪一项：先看控件，再看整行的标签区（不用非得压在滑块上）。 */
    private ConfigOption findHoveredOption(int mouseX, int mouseY) {
        for (Map.Entry<AbstractWidget, ConfigOption> entry : hoverTargets.entrySet()) {
            AbstractWidget widget = entry.getKey();
            if (widget.visible && widget.isMouseOver(mouseX, mouseY)) {
                return entry.getValue();
            }
        }
        if (mouseX >= contentX() && mouseX < contentRight()) {
            for (Placement placement : layout()) {
                if (mouseY >= placement.y && mouseY < placement.y + placement.height) {
                    return placement.option;
                }
            }
        }
        return null;
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
                playClick();
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

    // ==================== 键盘 ====================

    /**
     * 键盘操作：↑↓ / Tab 选行、←→ 改值（Shift 十步）、PageUp/PageDown 换页、Enter 保存退出。
     *
     * <p>只在「动过键盘」之后才画焦点框（{@link #keyboardFocusShown}），否则纯鼠标玩家会看到
     * 一个莫名其妙的方框跟着页面出现。</p>
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_PAGE_UP || keyCode == GLFW.GLFW_KEY_PAGE_DOWN) {
            int next = selected + (keyCode == GLFW.GLFW_KEY_PAGE_UP ? -1 : 1);
            if (next >= 0 && next < categories.size()) {
                keyboardFocusShown = true;
                playClick();
                selectCategory(next);
                ensureCategoryVisible();
            }
            return true;
        }

        List<Placement> placements = layout();
        if (!placements.isEmpty()) {
            boolean readOnly = pageReadOnly();
            int size = placements.size();
            if (keyCode == GLFW.GLFW_KEY_UP || keyCode == GLFW.GLFW_KEY_DOWN
                    || keyCode == GLFW.GLFW_KEY_TAB) {
                int step = keyCode == GLFW.GLFW_KEY_UP ? -1 : 1;
                focusedRow = Math.floorMod(focusedRow + step, size);
                keyboardFocusShown = true;
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_LEFT || keyCode == GLFW.GLFW_KEY_RIGHT) {
                keyboardFocusShown = true;
                if (!readOnly) {
                    adjustFocused(focusedRow, keyCode == GLFW.GLFW_KEY_LEFT ? -1 : 1);
                }
                return true;
            }
        }

        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            onDone();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** 键盘改值：Int 走 step（Shift 十步），Bool 直接取反。 */
    private void adjustFocused(int row, int direction) {
        List<Placement> placements = layout();
        if (row < 0 || row >= placements.size()) {
            return;
        }
        ConfigOption option = placements.get(row).option;
        int step = direction * (Screen.hasShiftDown() ? 10 : 1);
        if (option instanceof ConfigOption.Int integer) {
            integer.set(integer.get() + step);
            playClick();
        } else if (option instanceof ConfigOption.Bool bool) {
            bool.set(!bool.get());
            playClick();
            rebuildRowWidgets();   // 开关按钮的图标要跟着换
        }
    }

    /** 键盘换页后把选中的分类滚进可视区。 */
    private void ensureCategoryVisible() {
        int rowTop = selected * (CAT_ROW_H + CAT_ROW_GAP);
        int rowBottom = rowTop + CAT_ROW_H;
        int visible = catBottom() - catTop();
        if (rowTop < categoryScroll) {
            categoryScroll = rowTop;
        } else if (rowBottom > categoryScroll + visible) {
            categoryScroll = rowBottom - visible;
        }
        categoryScroll = Math.max(0, Math.min(maxCategoryScroll(), categoryScroll));
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        keyboardFocusShown = false;
        super.mouseMoved(mouseX, mouseY);
    }

    /** 「恢复本页默认」：先把当前值存一份，再把整页打回默认——存的那份让按钮变成「撤销」。 */
    private void resetCurrentCategory() {
        playClick();
        resetSnapshot.clear();
        for (ConfigOption option : categories.get(selected).options) {
            resetSnapshot.put(option, option.snapshot());
        }
        for (ConfigOption option : categories.get(selected).options) {
            option.resetToDefault();
        }
        refreshResetButton();
        rebuildRowWidgets();
    }

    /** 「撤销」：把刚才恢复默认前的值原样放回去。 */
    private void undoReset() {
        playClick();
        for (Map.Entry<ConfigOption, Object> entry : resetSnapshot.entrySet()) {
            entry.getKey().restore(entry.getValue());
        }
        resetSnapshot.clear();
        refreshResetButton();
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
    private final class IntSlider extends AbstractSimiWidget {

        /**
         * 旋钮宽度。
         *
         * <p>★ 必须是「可走距离 = 控件宽 - 旋钮宽 - 2」的关系，否则会出现两个经典毛病：
         * 0% 时旋钮向左探出控件、100% 时到不了右端（之前那版就是）。</p>
         */
        private static final int KNOB_W = 6;

        private final ConfigOption.Int option;
        /** 只读页（多人 + SERVER 页）：照样画出来，但不动、不亮。 */
        private final boolean readOnly;
        /** Shift 微调时累计的鼠标位移，够 1 像素才走一步。 */
        private double fineAccum;

        IntSlider(int x, int y, int width, int height, ConfigOption.Int option, boolean readOnly) {
            super(x, y, width, height);
            this.option = option;
            this.readOnly = readOnly;
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
            boolean hovered = !readOnly && this.isHovered();

            // 凹槽（只读时整体压暗一档，既能看清数值，也一眼看出动不了）
            int edge = readOnly ? 0x18FFFFFF : (hovered ? 0x40FFFFFF : 0x28FFFFFF);
            int accent = readOnly ? 0x60_8AB6D6 : ACCENT;
            int knob = readOnly ? 0x80_B98A5A : (hovered ? 0xFFE0B47A : BRASS);
            graphics.fill(x, y, x + w, y + h, readOnly ? 0xFF131417 : 0xFF17181B);
            graphics.fill(x, y, x + w, y + 1, edge);
            graphics.fill(x, y + h - 1, x + w, y + h, 0x40_000000);

            // 旋钮位置：0% 贴左内缘、100% 贴右内缘
            int knobX = x + knobOffset();

            // 底部进度：从左内缘铺到旋钮中心
            int center = knobX + KNOB_W / 2;
            graphics.fill(x + 1, y + h - 4, center, y + h - 2, accent);

            // 旋钮（黄铜色竖条）
            graphics.fill(knobX, y + 1, knobX + KNOB_W, y + h - 1, knob);
            if (!readOnly) {
                graphics.fill(knobX, y + 1, knobX + KNOB_W, y + 2, 0x60FFFFFF);
            }

            // 条里只显示「当前值」——标签由 renderContent 在行左边画一次就够了，
            // 这里再画一次标签会变成两层字叠在一起。只调一次 drawString，用原版自带的阴影。
            Font font = Minecraft.getInstance().font;
            Component value = Component.literal(Integer.toString(option.get()));
            graphics.drawString(font, value, x + (w - font.width(value)) / 2, y + (h - 8) / 2,
                    readOnly ? TEXT_DIM : TEXT, true);
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            if (!readOnly) {
                setFromMouse(mouseX);
                playClick();
            }
        }

        @Override
        protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
            if (readOnly) {
                return;
            }
            if (Screen.hasShiftDown()) {
                // 按住 Shift：按位移走 ±1，大跨度（1-1200）也能落到整数上
                fineAccum += dragX;
                int steps = (int) fineAccum;
                if (steps != 0) {
                    fineAccum -= steps;
                    option.set(option.get() + steps);
                }
            } else {
                fineAccum = 0;
                setFromMouse(mouseX);
            }
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            return !readOnly && super.mouseClicked(mouseX, mouseY, button);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
            if (readOnly) {
                return false;
            }
            // Shift + 滚轮 = ±10，普通滚轮 = ±1
            int step = Screen.hasShiftDown() ? 10 : 1;
            option.set(option.get() + (int) Math.signum(delta) * step);
            return true;
        }

        @Override
        public void updateWidgetNarration(NarrationElementOutput output) {
            this.defaultButtonNarrationText(output);
        }
    }
}
