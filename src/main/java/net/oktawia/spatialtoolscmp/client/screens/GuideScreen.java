package net.oktawia.spatialtoolscmp.client.screens;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.lwjgl.glfw.GLFW;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

import net.oktawia.spatialtoolscmp.client.misc.Icon;
import net.oktawia.spatialtoolscmp.client.misc.guide.GuideBlock;
import net.oktawia.spatialtoolscmp.client.misc.guide.GuideLayout;
import net.oktawia.spatialtoolscmp.client.misc.guide.GuideLoader;
import net.oktawia.spatialtoolscmp.client.misc.widgets.IconButtonWidget;
import net.oktawia.spatialtoolscmp.defs.LangDefs;

public class GuideScreen extends Screen {

    private static final int MAX_PANEL_WIDTH = 420;
    private static final int PANEL_MARGIN_X = 24;
    private static final int PANEL_MARGIN_Y = 16;
    private static final int PANEL_PADDING = 12;

    private static final int HEADER_HEIGHT = 26;
    private static final int CLOSE_BUTTON_SIZE = 16;
    private static final int SCROLLBAR_WIDTH = 4;
    private static final int SCROLL_STEP = 16;

    private static final int PANEL_BACKGROUND = 0xE60D0D0D;
    private static final int PANEL_BORDER = 0xFF5A5A5A;
    private static final int SEPARATOR = 0xFF3C3C3C;
    private static final int SCROLLBAR_TRACK = 0x40FFFFFF;
    private static final int SCROLLBAR_THUMB = 0xFFAAAAAA;
    private static final int CODE_BACKGROUND = 0x40FFFFFF;
    private static final int QUOTE_BAR = 0xFF7A7A7A;
    private static final int QUOTE_BACKGROUND = 0x26FFFFFF;

    private static final int SECTION_BACKGROUND = 0x30FFFFFF;
    private static final int SECTION_BACKGROUND_HOVER = 0x50FFFFFF;
    private static final int CHEVRON_SIZE = 10;
    private static final int ICON_TEXTURE_SIZE = 16;

    private final Screen parent;
    private final ItemStack toolStack;
    private final List<GuideBlock> blocks;
    private final Set<Integer> collapsedSections = new HashSet<>();

    private GuideLayout layout;

    private int panelLeft;
    private int panelTop;
    private int panelWidth;
    private int panelHeight;
    private int contentTop;
    private int contentBottom;
    private int contentWidth;

    private int scroll;

    public GuideScreen(Screen parent, ItemStack toolStack) {
        super(toolStack.getHoverName());

        this.parent = parent;
        this.toolStack = toolStack;
        this.blocks = GuideLoader.get(toolStack.getItem());

        for (int section = 0; section < GuideLayout.countSections(this.blocks); section++) {
            this.collapsedSections.add(section);
        }
    }

    @Override
    protected void init() {
        this.panelWidth = Math.min(this.width - PANEL_MARGIN_X * 2, MAX_PANEL_WIDTH);
        this.panelHeight = this.height - PANEL_MARGIN_Y * 2;
        this.panelLeft = (this.width - this.panelWidth) / 2;
        this.panelTop = PANEL_MARGIN_Y;

        this.contentTop = this.panelTop + HEADER_HEIGHT + 6;
        this.contentBottom = this.panelTop + this.panelHeight - PANEL_PADDING;
        this.contentWidth = this.panelWidth - PANEL_PADDING * 2 - SCROLLBAR_WIDTH - 2;

        rebuildLayout();

        IconButtonWidget closeButton = new IconButtonWidget(
                this.panelLeft + this.panelWidth - CLOSE_BUTTON_SIZE - 5,
                this.panelTop + 5,
                CLOSE_BUTTON_SIZE,
                CLOSE_BUTTON_SIZE,
                Icon.CROSS,
                this::onClose);

        addRenderableWidget(closeButton);
    }

    private void rebuildLayout() {
        List<GuideBlock> renderedBlocks = this.blocks.isEmpty()
                ? List.of(GuideBlock.paragraph(
                        Component.translatable(LangDefs.GUIDE_MISSING.getTranslationKey())
                                .withStyle(ChatFormatting.GRAY)))
                : this.blocks;

        this.layout = GuideLayout.build(this.font, renderedBlocks, this.contentWidth, this.collapsedSections);
        this.scroll = Mth.clamp(this.scroll, 0, maxScroll());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);

        graphics.fill(
                this.panelLeft,
                this.panelTop,
                this.panelLeft + this.panelWidth,
                this.panelTop + this.panelHeight,
                PANEL_BACKGROUND);

        graphics.renderOutline(this.panelLeft, this.panelTop, this.panelWidth, this.panelHeight, PANEL_BORDER);

        graphics.renderItem(this.toolStack, this.panelLeft + PANEL_PADDING - 4, this.panelTop + 5);

        graphics.drawString(
                this.font,
                this.title,
                this.panelLeft + PANEL_PADDING + 16,
                this.panelTop + 9,
                0xFFFFFFFF,
                false);

        graphics.fill(
                this.panelLeft + 1,
                this.panelTop + HEADER_HEIGHT,
                this.panelLeft + this.panelWidth - 1,
                this.panelTop + HEADER_HEIGHT + 1,
                SEPARATOR);

        renderContent(graphics, mouseX, mouseY);
        renderScrollbar(graphics);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderContent(GuiGraphics graphics, int mouseX, int mouseY) {
        int left = this.panelLeft + PANEL_PADDING;

        graphics.enableScissor(
                this.panelLeft + 1,
                this.contentTop,
                this.panelLeft + this.panelWidth - 1,
                this.contentBottom);

        for (GuideLayout.GuideLine line : this.layout.lines()) {
            int y = this.contentTop + line.y() - this.scroll;

            if (y + this.font.lineHeight * 2 < this.contentTop || y > this.contentBottom) {
                continue;
            }

            int x = left + line.x();

            switch (line.decoration()) {
                case RULE -> graphics.fill(left, y + 4, left + this.contentWidth, y + 5, SEPARATOR);
                case CODE -> graphics.fill(
                        left,
                        y - 1,
                        left + this.contentWidth,
                        y + this.font.lineHeight + 1,
                        CODE_BACKGROUND);
                case QUOTE -> {
                    graphics.fill(
                            left,
                            y - 1,
                            left + this.contentWidth,
                            y + this.font.lineHeight + 1,
                            QUOTE_BACKGROUND);

                    graphics.fill(left, y - 1, left + 2, y + this.font.lineHeight + 1, QUOTE_BAR);
                }
                case SECTION -> renderSectionRow(graphics, line, left, y, mouseX, mouseY);
                case NONE -> {
                }
            }

            if (line.text() == null) {
                continue;
            }

            if (line.scale() == 1.0F) {
                graphics.drawString(this.font, line.text(), x, y, line.color(), false);
                continue;
            }

            graphics.pose().pushPose();
            graphics.pose().translate(x, y, 0.0F);
            graphics.pose().scale(line.scale(), line.scale(), 1.0F);
            graphics.drawString(this.font, line.text(), 0, 0, line.color(), false);
            graphics.pose().popPose();
        }

        graphics.disableScissor();
    }

    private void renderSectionRow(GuiGraphics graphics, GuideLayout.GuideLine line, int left, int y, int mouseX,
            int mouseY) {
        int rowTop = y - GuideLayout.SECTION_ROW_PADDING;
        int rowBottom = rowTop + GuideLayout.sectionRowHeight(this.font);
        int rowRight = left + this.contentWidth;

        boolean hovered = mouseX >= left && mouseX < rowRight && mouseY >= rowTop && mouseY < rowBottom
                && mouseY >= this.contentTop && mouseY < this.contentBottom;

        graphics.fill(left, rowTop, rowRight, rowBottom, hovered ? SECTION_BACKGROUND_HOVER : SECTION_BACKGROUND);

        Icon chevron = this.collapsedSections.contains(line.sectionIndex()) ? Icon.ARROW_RIGHT : Icon.ARROW_DOWN;

        graphics.blit(
                chevron.texture(),
                rowRight - CHEVRON_SIZE - GuideLayout.SECTION_ROW_PADDING,
                rowTop + (rowBottom - rowTop - CHEVRON_SIZE) / 2,
                CHEVRON_SIZE,
                CHEVRON_SIZE,
                0.0F,
                0.0F,
                ICON_TEXTURE_SIZE,
                ICON_TEXTURE_SIZE,
                ICON_TEXTURE_SIZE,
                ICON_TEXTURE_SIZE);
    }

    private void toggleSectionAt(double mouseX, double mouseY) {
        int left = this.panelLeft + PANEL_PADDING;
        int rowRight = left + this.contentWidth;
        int rowHeight = GuideLayout.sectionRowHeight(this.font);

        for (GuideLayout.GuideLine line : this.layout.lines()) {
            if (line.decoration() != GuideLayout.Decoration.SECTION) {
                continue;
            }

            int rowTop = this.contentTop + line.y() - this.scroll - GuideLayout.SECTION_ROW_PADDING;

            if (mouseX < left || mouseX >= rowRight || mouseY < rowTop || mouseY >= rowTop + rowHeight) {
                continue;
            }

            if (!this.collapsedSections.remove(line.sectionIndex())) {
                this.collapsedSections.add(line.sectionIndex());
            }

            rebuildLayout();
            return;
        }
    }

    private void renderScrollbar(GuiGraphics graphics) {
        int maxScroll = maxScroll();

        if (maxScroll <= 0) {
            return;
        }

        int trackHeight = this.contentBottom - this.contentTop;
        int trackLeft = this.panelLeft + this.panelWidth - PANEL_PADDING + 2;

        graphics.fill(
                trackLeft,
                this.contentTop,
                trackLeft + SCROLLBAR_WIDTH,
                this.contentBottom,
                SCROLLBAR_TRACK);

        int thumbHeight = Math.max(16, trackHeight * trackHeight / this.layout.height());
        int thumbTop = this.contentTop + (trackHeight - thumbHeight) * this.scroll / maxScroll;

        graphics.fill(
                trackLeft,
                thumbTop,
                trackLeft + SCROLLBAR_WIDTH,
                thumbTop + thumbHeight,
                SCROLLBAR_THUMB);
    }

    private int maxScroll() {
        return Math.max(0, this.layout.height() - (this.contentBottom - this.contentTop));
    }

    private void scrollBy(int amount) {
        this.scroll = Mth.clamp(this.scroll + amount, 0, maxScroll());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (super.mouseClicked(mouseX, mouseY, button)) {
            return true;
        }

        if (button == 0 && mouseY >= this.contentTop && mouseY < this.contentBottom) {
            toggleSectionAt(mouseX, mouseY);
            return true;
        }

        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        scrollBy((int) (-delta * SCROLL_STEP));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        int page = this.contentBottom - this.contentTop - this.font.lineHeight;

        switch (keyCode) {
            case GLFW.GLFW_KEY_DOWN -> {
                scrollBy(SCROLL_STEP);
                return true;
            }
            case GLFW.GLFW_KEY_UP -> {
                scrollBy(-SCROLL_STEP);
                return true;
            }
            case GLFW.GLFW_KEY_PAGE_DOWN -> {
                scrollBy(page);
                return true;
            }
            case GLFW.GLFW_KEY_PAGE_UP -> {
                scrollBy(-page);
                return true;
            }
            case GLFW.GLFW_KEY_HOME -> {
                this.scroll = 0;
                return true;
            }
            case GLFW.GLFW_KEY_END -> {
                this.scroll = maxScroll();
                return true;
            }
            default -> {
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
        }
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
