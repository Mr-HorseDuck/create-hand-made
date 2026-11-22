package com.alben.createhandmade.client.config;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * 自绘的配置界面（<b>不依赖 Cloth Config</b>）。
 *
 * <p>版式照原版选项页的意思来：左边一列分类，右边是要改的项，底下一个「完成」。</p>
 * <pre>
 *  ┌───────────────────────────────────────────────┐
 *  │            机械动力：手搓学 配置              │
 *  ├──────────────┬────────────────────────────────┤
 *  │ 手锯         │  [ 整树砍伐：开 ]              │
 *  │ 冲压锤       │  [ 手锯起手时长：10    ▮▮▮ ]   │
 *  │ 风箱         │  [ 手锯切削时长：20    ▮▮▮ ]   │
 *  │ …            │                                │
 *  ├──────────────┴────────────────────────────────┤
 *  │ [恢复本页默认]            [      完成      ]  │
 *  └───────────────────────────────────────────────┘
 * </pre>
 *
 * <h2>几个刻意的取舍</h2>
 * <ul>
 *   <li><b>右侧不滚动</b>：每页最多 7 项（{@link #ROW_H} 固定行高，整块垂直居中）。
 *       分类因此拆得比较细——女仆配置的 12 项拆成「基础 / 冷却 / 屏蔽模组」三页。
 *       这样省掉整套自绘滚动列表，也不会出现「滚一半控件错位」。</li>
 *   <li><b>左侧分类可以滚</b>：分类数随可选模组变化（装了女仆就 +3 页），
 *       窗口矮的时候放不下，用滚轮滚；滚出可视区的按钮直接 {@code visible = false}，
 *       避免半截按钮露在标题栏上。</li>
 *   <li><b>改动即时生效</b>：控件一动就写进 {@code ForgeConfigSpec} 的值里，
 *       关界面（完成 / Esc）时统一落盘 + 广播 —— 与以前 Cloth 的 {@code setSaveConsumer} 一致。</li>
 *   <li><b>多人游戏整页只读</b>：SERVER 配置在多人里由服务端权威，
 *       与以前一样只显示两条提示文字，不给控件。</li>
 *   <li><b>打开时糊背景、藏 HUD</b>：{@link ScreenBackdrop} 把世界做一次模糊，
 *       同时临时把 {@code Options.hideGui} 打开 —— 物品栏、生命条、聊天、
 *       以及尊重该开关的小地图全部让位，关掉界面后原样恢复（原本按着 F1 的话也不会被改坏）。</li>
 * </ul>
 */
public class HandMadeConfigScreen extends Screen {

    // ---- 左侧分类栏 ----
    private static final int LEFT_X = 10;
    private static final int LEFT_W = 120;
    private static final int BTN_H = 20;
    private static final int BTN_GAP = 2;

    // ---- 右侧内容区 ----
    private static final int CONTENT_TOP = 34;
    private static final int PANEL_GAP = 10;
    private static final int ROW_H = 24;
    /** 带输入框的行要额外给标签留一行。 */
    private static final int ROW_H_STRINGS = 36;
    private static final int ROW_GAP = 4;
    private static final int MIN_WIDGET_W = 110;
    private static final int MAX_WIDGET_W = 240;

    private static final int BOTTOM_MARGIN = 30;

    private final Screen parent;
    private final List<ConfigCategorySpec> categories;
    private final Runnable saveCallback;
    /** 当前是否不在存档里（{@code getSingleplayerServer() == null}）—— 只读判定的前提之一。 */
    private final boolean multiplayer;

    private final List<Button> categoryButtons = new ArrayList<>();
    private final List<AbstractWidget> rowWidgets = new ArrayList<>();
    /** 控件 → 选项，用来在 render 里出悬停说明（IdentityHashMap：控件没有值语义）。 */
    private final Map<AbstractWidget, ConfigOption> tooltipTargets = new IdentityHashMap<>();

    private int selected;
    private int categoryScroll;
    private Button resetButton;

    /** 背景模糊；界面关闭时一并释放。 */
    private final ScreenBackdrop backdrop = new ScreenBackdrop();
    /** 打开界面前 HUD 是否已经被玩家自己藏起来了（F1）——关界面时要还原成这个值。 */
    private boolean hideGuiBefore;
    private boolean hideGuiCaptured;

    public HandMadeConfigScreen(Screen parent, List<ConfigCategorySpec> categories,
                                Runnable saveCallback, boolean multiplayer) {
        super(Component.translatable("config.create_hand_made.title"));
        this.parent = parent;
        this.categories = List.copyOf(categories);
        this.saveCallback = saveCallback;
        this.multiplayer = multiplayer;
    }

    // ==================== 构建 ====================

    @Override
    protected void init() {
        // 隐藏 HUD：物品栏 / 生命条 / 聊天 / 尊重该开关的小地图都会让位。
        // 只在第一次 init 时记录原值——窗口缩放会重跑 init()，那不该把「已隐藏」当成原值。
        if (!hideGuiCaptured) {
            hideGuiBefore = this.minecraft.options.hideGui;
            hideGuiCaptured = true;
        }
        this.minecraft.options.hideGui = true;

        // 窗口尺寸变化会重跑 init()，Screen 已经把控件清空了，这里同步清掉自己的引用
        categoryButtons.clear();
        rowWidgets.clear();
        tooltipTargets.clear();
        categoryScroll = 0;

        for (int i = 0; i < categories.size(); i++) {
            int index = i;
            Button button = Button.builder(categories.get(i).title, b -> selectCategory(index))
                    .bounds(LEFT_X, CONTENT_TOP, LEFT_W, BTN_H)
                    .build();
            categoryButtons.add(button);
            addRenderableWidget(button);
        }

        int bottomY = this.height - BOTTOM_MARGIN + 4;
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(this.width / 2 - 60, bottomY, 120, BTN_H)
                .build());

        resetButton = Button.builder(Component.translatable("config.create_hand_made.reset"),
                        b -> resetCurrentCategory())
                .bounds(LEFT_X, bottomY, LEFT_W, BTN_H)
                .build();
        resetButton.visible = !pageReadOnly();
        addRenderableWidget(resetButton);

        layoutCategoryButtons();
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
        for (int i = 0; i < categoryButtons.size(); i++) {
            // 当前页的按钮置灰（原版选项页也是这个语感：点不动 = 已经在这一页）
            categoryButtons.get(i).active = i != index;
        }
        rebuildRows();
    }

    /** 按当前分类重建右侧控件。 */
    private void rebuildRows() {
        for (AbstractWidget widget : rowWidgets) {
            removeWidget(widget);
        }
        rowWidgets.clear();
        tooltipTargets.clear();

        if (pageReadOnly()) {
            return;
        }
        for (Placement placement : layout()) {
            addRowWidget(placement);
        }
    }

    private void addRowWidget(Placement placement) {
        ConfigOption option = placement.option;
        int x = panelX();
        int width = panelWidth();
        int widgetY = placement.y + (placement.height - BTN_H) / 2;

        if (option instanceof ConfigOption.Bool bool) {
            Button button = Button.builder(boolLabel(bool), b -> {
                bool.set(!bool.get());
                b.setMessage(boolLabel(bool));
            }).bounds(x, widgetY, width, BTN_H).build();
            track(button, option);
        } else if (option instanceof ConfigOption.Int integer) {
            track(new IntSlider(x, widgetY, width, BTN_H, integer), option);
        } else if (option instanceof ConfigOption.Strings strings) {
            EditBox box = new EditBox(this.font, x, placement.y + 13, width, BTN_H, option.label);
            box.setMaxLength(1024);
            // 先填值再挂 responder：setValue 会触发一次 responder，先挂上会多写一次配置
            box.setValue(strings.join());
            box.setResponder(text -> strings.set(ConfigOption.Strings.parse(text)));
            box.setHint(Component.translatable("config.create_hand_made.list_hint")
                    .withStyle(ChatFormatting.DARK_GRAY));
            track(box, option);
        }
    }

    private void track(AbstractWidget widget, ConfigOption option) {
        tooltipTargets.put(widget, option);
        rowWidgets.add(widget);
        addRenderableWidget(widget);
    }

    // ==================== 布局 ====================

    private record Placement(ConfigOption option, int y, int height) {
    }

    /** 当前页每一项的纵向位置：整块在内容区里垂直居中。 */
    private List<Placement> layout() {
        ConfigCategorySpec category = categories.get(selected);
        int total = 0;
        for (ConfigOption option : category.options) {
            total += rowHeight(option) + ROW_GAP;
        }
        if (total > 0) {
            total -= ROW_GAP;
        }
        int available = this.height - CONTENT_TOP - BOTTOM_MARGIN - 8;
        int y = CONTENT_TOP + Math.max(0, (available - total) / 2);

        List<Placement> placements = new ArrayList<>(category.options.size());
        for (ConfigOption option : category.options) {
            int height = rowHeight(option);
            placements.add(new Placement(option, y, height));
            y += height + ROW_GAP;
        }
        return placements;
    }

    private static int rowHeight(ConfigOption option) {
        return option instanceof ConfigOption.Strings ? ROW_H_STRINGS : ROW_H;
    }

    /**
     * 当前这一页是不是只读：<b>多人游戏 + 该页是服务端权威</b>才只读。
     *
     * <p>{@code COMMON} 类型的本地配置（女仆 {@code blocked_mods}）在多人里照样能改 ——
     * 改的是自己机器上的文件。以前按整页一刀切，把这种本地项也锁住了。</p>
     */
    private boolean pageReadOnly() {
        return multiplayer && categories.get(selected).serverAuthoritative;
    }

    private int panelX() {
        return LEFT_X + LEFT_W + PANEL_GAP;
    }

    private int panelWidth() {
        int width = this.width - panelX() - 10;
        return Math.max(MIN_WIDGET_W, Math.min(MAX_WIDGET_W, width));
    }

    /** 左侧分类按钮按滚动量重新摆位，滚出可视区的直接隐藏。 */
    private void layoutCategoryButtons() {
        int top = CONTENT_TOP;
        int bottom = this.height - BOTTOM_MARGIN;
        for (int i = 0; i < categoryButtons.size(); i++) {
            Button button = categoryButtons.get(i);
            int y = top + i * (BTN_H + BTN_GAP) - categoryScroll;
            button.setY(y);
            button.visible = y >= top && y + BTN_H <= bottom;
        }
    }

    private int maxCategoryScroll() {
        int content = categoryButtons.size() * (BTN_H + BTN_GAP) - BTN_GAP;
        int visible = this.height - BOTTOM_MARGIN - CONTENT_TOP;
        return Math.max(0, content - visible);
    }

    // ==================== 绘制 ====================

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // 先糊背景：此时世界已经在主渲染目标里，模糊完再画半透明底色与控件
        backdrop.blur(partialTick);
        // ★ 1.20.1 的 Screen.render 只画 renderables，**背景要自己调**：
        //   存档里 = 半透明黑色渐变（盖在糊过的世界上），主菜单里 = 原版泥土底。
        //   少了这一句，屏幕上留着的还是上一帧（例如 Mods 列表）——就是「配置和 Mods 页重叠」。
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);

        graphics.drawCenteredString(this.font, this.title, this.width / 2, 6, 0xFFFFFF);
        // 分类名贴在右侧内容区上方（左对齐，避免和居中的总标题撞在一起）
        graphics.drawString(this.font, categories.get(selected).title,
                panelX(), CONTENT_TOP - 12, 0xA0A0A0, false);

        if (pageReadOnly()) {
            renderReadOnlyHints(graphics);
        } else {
            renderStringLabels(graphics);
            if (!categories.get(selected).serverAuthoritative) {
                renderLocalConfigHint(graphics);
            }
        }

        for (Map.Entry<AbstractWidget, ConfigOption> entry : tooltipTargets.entrySet()) {
            AbstractWidget widget = entry.getKey();
            if (!widget.visible || !widget.isMouseOver(mouseX, mouseY)) {
                continue;
            }
            List<Component> tooltip = entry.getValue().tooltip;
            if (!tooltip.isEmpty()) {
                graphics.renderComponentTooltip(this.font, tooltip, mouseX, mouseY);
            }
            break;
        }
    }

    /** 输入框类选项的标签画在框上方（按钮/滑块把标签塞在控件文字里，不需要）。 */
    private void renderStringLabels(GuiGraphics graphics) {
        int x = panelX();
        for (Placement placement : layout()) {
            if (placement.option instanceof ConfigOption.Strings) {
                graphics.drawString(this.font, placement.option.label, x, placement.y, 0xE0E0E0, false);
            }
        }
    }

    /** 「这一页是本地配置」的灰字提示（多人游戏里尤其需要，否则玩家会奇怪为什么这页能改）。 */
    private void renderLocalConfigHint(GuiGraphics graphics) {
        graphics.drawString(this.font,
                Component.translatable("config.create_hand_made.local_config_hint"),
                panelX(), this.height - BOTTOM_MARGIN - 12, 0x707070, false);
    }

    private void renderReadOnlyHints(GuiGraphics graphics) {
        int x = panelX();
        int width = panelWidth();
        int y = CONTENT_TOP + 8;
        for (String key : new String[]{
                "config.create_hand_made.multiplayer_hint",
                "config.create_hand_made.server_config_hint"}) {
            List<FormattedCharSequence> lines =
                    this.font.split(Component.translatable(key), width);
            for (FormattedCharSequence line : lines) {
                graphics.drawString(this.font, line, x, y, 0xFFA0A0A0, false);
                y += 11;
            }
            y += 6;
        }
    }

    // ==================== 交互 ====================

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX < LEFT_X + LEFT_W && maxCategoryScroll() > 0) {
            categoryScroll = (int) Math.max(0, Math.min(maxCategoryScroll(),
                    categoryScroll - delta * (BTN_H + BTN_GAP)));
            layoutCategoryButtons();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private void resetCurrentCategory() {
        for (ConfigOption option : categories.get(selected).options) {
            option.resetToDefault();
        }
        rebuildRows();
    }

    /**
     * 界面被换下时（完成 / Esc / 被别的界面顶掉）恢复 HUD 并释放模糊缓冲。
     *
     * <p>放在 {@code removed()} 而不是 {@code onClose()}：{@code /chm config} 之后玩家
     * 有可能直接按 Esc 以外的路径离开（比如别的模组抢开界面），这里才是唯一可靠的收尾点。</p>
     */
    @Override
    public void removed() {
        this.minecraft.options.hideGui = hideGuiBefore;
        hideGuiCaptured = false;
        backdrop.close();
        super.removed();
    }

    /** 「完成」与 Esc 都走这里：先落盘 + 广播，再回上一级。 */
    @Override
    public void onClose() {
        if (saveCallback != null) {
            saveCallback.run();
        }
        this.minecraft.setScreen(this.parent);
    }

    private static Component boolLabel(ConfigOption.Bool option) {
        return Component.translatable("options.generic_value", option.label,
                Component.translatable(option.get() ? "options.on" : "options.off"));
    }

    // ==================== 整数滑块 ====================

    /**
     * 整数滑块：值域来自配置声明（{@code defineInRange}），所以拖动过程中就把值写回去。
     *
     * <p>拖动产生的 {@code value} 是连续 double，这里立刻量化到整数并把滑块位置吸附到
     * 量化后的刻度上——否则松手后显示值与实际值会差一格。</p>
     */
    private static final class IntSlider extends AbstractSliderButton {

        private final ConfigOption.Int option;

        IntSlider(int x, int y, int width, int height, ConfigOption.Int option) {
            super(x, y, width, height, Component.empty(), fraction(option.get(), option));
            this.option = option;
            updateMessage();
        }

        private static double fraction(int value, ConfigOption.Int option) {
            int span = option.max() - option.min();
            return span <= 0 ? 0.0 : (double) (value - option.min()) / span;
        }

        @Override
        protected void updateMessage() {
            // 父类构造阶段可能回调到这里，此时 option 还没赋值——给个空文本兜住
            if (option == null) {
                setMessage(Component.empty());
                return;
            }
            setMessage(Component.translatable("options.generic_value", option.label,
                    Component.literal(Integer.toString(option.get()))));
        }

        @Override
        protected void applyValue() {
            if (option == null) {
                return;
            }
            int span = option.max() - option.min();
            int value = option.min() + (int) Math.round(this.value * span);
            option.set(value);
            // 吸附到刻度，避免显示值和实际值差一格
            this.value = fraction(option.get(), option);
        }
    }
}
