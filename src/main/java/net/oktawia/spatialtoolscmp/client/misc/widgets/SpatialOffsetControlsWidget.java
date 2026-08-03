package net.oktawia.spatialtoolscmp.client.misc.widgets;

import org.jetbrains.annotations.NotNull;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import net.oktawia.spatialtoolscmp.client.misc.Icon;
import net.oktawia.spatialtoolscmp.defs.LangDefs;

public class SpatialOffsetControlsWidget extends AbstractWidget {

    public static final int LEFT = -46;
    public static final int TOP = 34;

    public static final int BUTTON_SIZE = 16;
    public static final int BUTTON_STEP_X = 20;
    public static final int BUTTON_STEP_Y = 18;

    public static final int DISPLAY_WIDTH = 36;
    public static final int DISPLAY_HEIGHT = 12;
    public static final int DISPLAY_TOP = 58;
    public static final int DISPLAY_STEP_Y = 20;

    public static final int WIDTH = 56;
    public static final int HEIGHT = 112;

    private final IconButtonWidget offsetNorthButton;
    private final IconButtonWidget offsetSouthButton;
    private final IconButtonWidget offsetWestButton;
    private final IconButtonWidget offsetEastButton;
    private final IconButtonWidget offsetUpButton;
    private final IconButtonWidget offsetDownButton;

    private final SimpleTextFieldWidget offsetDisplayX;
    private final SimpleTextFieldWidget offsetDisplayY;
    private final SimpleTextFieldWidget offsetDisplayZ;

    private int screenLeft;
    private int screenTop;

    public SpatialOffsetControlsWidget(
            Runnable offsetWest,
            Runnable offsetEast,
            Runnable offsetUp,
            Runnable offsetDown,
            Runnable offsetNorth,
            Runnable offsetSouth) {
        super(0, 0, WIDTH, HEIGHT, Component.empty());

        this.offsetNorthButton = new IconButtonWidget(Icon.ARROW_BACK, offsetNorth);
        this.offsetSouthButton = new IconButtonWidget(Icon.ARROW_FRONT, offsetSouth);
        this.offsetWestButton = new IconButtonWidget(Icon.ARROW_LEFT, offsetWest);
        this.offsetEastButton = new IconButtonWidget(Icon.ARROW_RIGHT, offsetEast);
        this.offsetUpButton = new IconButtonWidget(Icon.ARROW_UP, offsetUp);
        this.offsetDownButton = new IconButtonWidget(Icon.ARROW_DOWN, offsetDown);

        this.offsetNorthButton.setTooltip(Tooltip.create(
                Component.translatable(LangDefs.OFFSET_NORTH_TOOLTIP.getTranslationKey())));
        this.offsetSouthButton.setTooltip(Tooltip.create(
                Component.translatable(LangDefs.OFFSET_SOUTH_TOOLTIP.getTranslationKey())));
        this.offsetWestButton.setTooltip(Tooltip.create(
                Component.translatable(LangDefs.OFFSET_WEST_TOOLTIP.getTranslationKey())));
        this.offsetEastButton.setTooltip(Tooltip.create(
                Component.translatable(LangDefs.OFFSET_EAST_TOOLTIP.getTranslationKey())));
        this.offsetUpButton.setTooltip(Tooltip.create(
                Component.translatable(LangDefs.OFFSET_UP_TOOLTIP.getTranslationKey())));
        this.offsetDownButton.setTooltip(Tooltip.create(
                Component.translatable(LangDefs.OFFSET_DOWN_TOOLTIP.getTranslationKey())));

        this.offsetDisplayX = new SimpleTextFieldWidget(
                Minecraft.getInstance().font,
                0,
                0,
                DISPLAY_WIDTH,
                DISPLAY_HEIGHT,
                Component.empty());
        this.offsetDisplayX.setEditable(false);
        this.offsetDisplayX.setMaxLength(8);
        this.offsetDisplayX.setTooltip(Tooltip.create(Component.translatable(
                LangDefs.OFFSET_X_TOOLTIP.getTranslationKey())));
        this.offsetDisplayX.setValue("X:0");

        this.offsetDisplayY = new SimpleTextFieldWidget(
                Minecraft.getInstance().font,
                0,
                0,
                DISPLAY_WIDTH,
                DISPLAY_HEIGHT,
                Component.empty());
        this.offsetDisplayY.setEditable(false);
        this.offsetDisplayY.setMaxLength(8);
        this.offsetDisplayY.setTooltip(Tooltip.create(Component.translatable(
                LangDefs.OFFSET_Y_TOOLTIP.getTranslationKey())));
        this.offsetDisplayY.setValue("Y:0");

        this.offsetDisplayZ = new SimpleTextFieldWidget(
                Minecraft.getInstance().font,
                0,
                0,
                DISPLAY_WIDTH,
                DISPLAY_HEIGHT,
                Component.empty());
        this.offsetDisplayZ.setEditable(false);
        this.offsetDisplayZ.setMaxLength(8);
        this.offsetDisplayZ.setTooltip(Tooltip.create(Component.translatable(
                LangDefs.OFFSET_Z_TOOLTIP.getTranslationKey())));
        this.offsetDisplayZ.setValue("Z:0");

        updateChildPositions();
    }

    public void setScreenPosition(int screenLeft, int screenTop) {
        this.screenLeft = screenLeft;
        this.screenTop = screenTop;

        setX(screenLeft + LEFT);
        setY(screenTop + TOP);

        updateChildPositions();
    }

    public void setOffset(BlockPos offset) {
        if (offset == null) {
            offset = BlockPos.ZERO;
        }

        String x = "X:" + offset.getX();
        String y = "Y:" + offset.getY();
        String z = "Z:" + offset.getZ();

        if (!x.equals(this.offsetDisplayX.getValue())) {
            this.offsetDisplayX.setValue(x);
        }

        if (!y.equals(this.offsetDisplayY.getValue())) {
            this.offsetDisplayY.setValue(y);
        }

        if (!z.equals(this.offsetDisplayZ.getValue())) {
            this.offsetDisplayZ.setValue(z);
        }
    }

    private void updateChildPositions() {
        int x = this.screenLeft + LEFT;
        int y = this.screenTop + TOP;

        setButtonBounds(this.offsetNorthButton, x, y);
        setButtonBounds(this.offsetUpButton, x + BUTTON_STEP_X, y);

        setButtonBounds(this.offsetWestButton, x, y + BUTTON_STEP_Y);
        setButtonBounds(this.offsetEastButton, x + BUTTON_STEP_X, y + BUTTON_STEP_Y);

        setButtonBounds(this.offsetSouthButton, x, y + BUTTON_STEP_Y * 2);
        setButtonBounds(this.offsetDownButton, x + BUTTON_STEP_X, y + BUTTON_STEP_Y * 2);

        this.offsetDisplayX.setBounds(x, y + DISPLAY_TOP, DISPLAY_WIDTH, DISPLAY_HEIGHT);
        this.offsetDisplayY.setBounds(x, y + DISPLAY_TOP + DISPLAY_STEP_Y, DISPLAY_WIDTH, DISPLAY_HEIGHT);
        this.offsetDisplayZ.setBounds(x, y + DISPLAY_TOP + DISPLAY_STEP_Y * 2, DISPLAY_WIDTH, DISPLAY_HEIGHT);
    }

    private static void setButtonBounds(IconButtonWidget button, int x, int y) {
        button.setPosition(x, y);
        button.resize(BUTTON_SIZE, BUTTON_SIZE);
    }

    @Override
    protected void renderWidget(
            @NotNull GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick) {
        if (!this.visible) {
            return;
        }

        updateChildPositions();

        this.offsetNorthButton.active = this.active;
        this.offsetSouthButton.active = this.active;
        this.offsetWestButton.active = this.active;
        this.offsetEastButton.active = this.active;
        this.offsetUpButton.active = this.active;
        this.offsetDownButton.active = this.active;

        this.offsetNorthButton.render(graphics, mouseX, mouseY, partialTick);
        this.offsetSouthButton.render(graphics, mouseX, mouseY, partialTick);
        this.offsetWestButton.render(graphics, mouseX, mouseY, partialTick);
        this.offsetEastButton.render(graphics, mouseX, mouseY, partialTick);
        this.offsetUpButton.render(graphics, mouseX, mouseY, partialTick);
        this.offsetDownButton.render(graphics, mouseX, mouseY, partialTick);

        this.offsetDisplayX.render(graphics, mouseX, mouseY, partialTick);
        this.offsetDisplayY.render(graphics, mouseX, mouseY, partialTick);
        this.offsetDisplayZ.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.visible || !this.active) {
            return false;
        }

        return this.offsetNorthButton.mouseClicked(mouseX, mouseY, button)
                || this.offsetSouthButton.mouseClicked(mouseX, mouseY, button)
                || this.offsetWestButton.mouseClicked(mouseX, mouseY, button)
                || this.offsetEastButton.mouseClicked(mouseX, mouseY, button)
                || this.offsetUpButton.mouseClicked(mouseX, mouseY, button)
                || this.offsetDownButton.mouseClicked(mouseX, mouseY, button)
                || this.offsetDisplayX.mouseClicked(mouseX, mouseY, button)
                || this.offsetDisplayY.mouseClicked(mouseX, mouseY, button)
                || this.offsetDisplayZ.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return this.offsetNorthButton.mouseReleased(mouseX, mouseY, button)
                || this.offsetSouthButton.mouseReleased(mouseX, mouseY, button)
                || this.offsetWestButton.mouseReleased(mouseX, mouseY, button)
                || this.offsetEastButton.mouseReleased(mouseX, mouseY, button)
                || this.offsetUpButton.mouseReleased(mouseX, mouseY, button)
                || this.offsetDownButton.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(
            double mouseX,
            double mouseY,
            int button,
            double dragX,
            double dragY) {
        return this.offsetNorthButton.mouseDragged(mouseX, mouseY, button, dragX, dragY)
                || this.offsetSouthButton.mouseDragged(mouseX, mouseY, button, dragX, dragY)
                || this.offsetWestButton.mouseDragged(mouseX, mouseY, button, dragX, dragY)
                || this.offsetEastButton.mouseDragged(mouseX, mouseY, button, dragX, dragY)
                || this.offsetUpButton.mouseDragged(mouseX, mouseY, button, dragX, dragY)
                || this.offsetDownButton.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return this.offsetNorthButton.mouseScrolled(mouseX, mouseY, delta)
                || this.offsetSouthButton.mouseScrolled(mouseX, mouseY, delta)
                || this.offsetWestButton.mouseScrolled(mouseX, mouseY, delta)
                || this.offsetEastButton.mouseScrolled(mouseX, mouseY, delta)
                || this.offsetUpButton.mouseScrolled(mouseX, mouseY, delta)
                || this.offsetDownButton.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    protected void updateWidgetNarration(@NotNull NarrationElementOutput narrationElementOutput) {
    }

    public static Rect2i getExtraArea(AbstractContainerScreen<?> screen) {
        return new Rect2i(
                screen.getGuiLeft() + LEFT,
                screen.getGuiTop() + TOP,
                WIDTH,
                HEIGHT);
    }
}
