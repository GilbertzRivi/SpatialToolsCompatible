package net.oktawia.spatialtoolscmp.client.misc.widgets;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.oktawia.spatialtoolscmp.client.misc.Icon;
import net.oktawia.spatialtoolscmp.defs.LangDefs;
import org.jetbrains.annotations.NotNull;

public class SpatialTransformationsWidget extends AbstractWidget {

    public static final int LEFT = 174;
    public static final int TOP = 6;

    public static final int BUTTON_SIZE = 16;
    public static final int BUTTON_STEP = 20;

    public static final int WIDTH = BUTTON_STEP * 3 + BUTTON_SIZE;
    public static final int HEIGHT = BUTTON_SIZE;

    private final IconButtonWidget flipEastWestButton;
    private final IconButtonWidget flipNorthSouthButton;
    private final IconButtonWidget flipVerticalButton;
    private final IconButtonWidget rotateButton;

    private int screenLeft;
    private int screenTop;
    private boolean transformAroundOriginMode;

    public SpatialTransformationsWidget(
            Runnable flipEastWest,
            Runnable flipEastWestAroundOrigin,
            Runnable flipNorthSouth,
            Runnable flipNorthSouthAroundOrigin,
            Runnable flipVertical,
            Runnable flipVerticalAroundOrigin,
            Runnable rotateClockwise,
            Runnable rotateClockwiseAroundOrigin
    ) {
        super(0, 0, WIDTH, HEIGHT, Component.empty());

        this.flipEastWestButton = new IconButtonWidget(Icon.ARROW_LEFT, () -> {
            if (this.transformAroundOriginMode) {
                flipEastWestAroundOrigin.run();
            } else {
                flipEastWest.run();
            }
        });

        this.flipNorthSouthButton = new IconButtonWidget(Icon.ARROW_RIGHT, () -> {
            if (this.transformAroundOriginMode) {
                flipNorthSouthAroundOrigin.run();
            } else {
                flipNorthSouth.run();
            }
        });

        this.flipVerticalButton = new IconButtonWidget(Icon.ARROW_UP, () -> {
            if (this.transformAroundOriginMode) {
                flipVerticalAroundOrigin.run();
            } else {
                flipVertical.run();
            }
        });

        this.rotateButton = new IconButtonWidget(Icon.ROTATE, () -> {
            if (this.transformAroundOriginMode) {
                rotateClockwiseAroundOrigin.run();
            } else {
                rotateClockwise.run();
            }
        });

        updateChildPositions();
        updateTooltips();
    }

    public void setScreenPosition(int screenLeft, int screenTop) {
        this.screenLeft = screenLeft;
        this.screenTop = screenTop;

        setX(screenLeft + LEFT);
        setY(screenTop + TOP);

        updateChildPositions();
    }

    public void setTransformAroundOriginMode(boolean transformAroundOriginMode) {
        if (this.transformAroundOriginMode == transformAroundOriginMode) {
            return;
        }

        this.transformAroundOriginMode = transformAroundOriginMode;
        updateTooltips();
    }

    private void updateChildPositions() {
        int x = this.screenLeft + LEFT;
        int y = this.screenTop + TOP;

        setButtonBounds(this.flipEastWestButton, x, y);
        setButtonBounds(this.flipNorthSouthButton, x + BUTTON_STEP, y);
        setButtonBounds(this.flipVerticalButton, x + BUTTON_STEP * 2, y);
        setButtonBounds(this.rotateButton, x + BUTTON_STEP * 3, y);
    }

    private static void setButtonBounds(IconButtonWidget button, int x, int y) {
        button.setPosition(x, y);
        button.resize(BUTTON_SIZE, BUTTON_SIZE);
    }

    private void updateTooltips() {
        boolean aroundOrigin = this.transformAroundOriginMode;

        this.flipEastWestButton.setTooltip(Tooltip.create(
                Component.translatable(
                        aroundOrigin
                                ? LangDefs.FLIP_EAST_WEST_AROUND_ORIGIN.getTranslationKey()
                                : LangDefs.FLIP_EAST_WEST.getTranslationKey()
                )
        ));

        this.flipNorthSouthButton.setTooltip(Tooltip.create(
                Component.translatable(
                        aroundOrigin
                                ? LangDefs.FLIP_NORTH_SOUTH_AROUND_ORIGIN.getTranslationKey()
                                : LangDefs.FLIP_NORTH_SOUTH.getTranslationKey()
                )
        ));

        this.flipVerticalButton.setTooltip(Tooltip.create(
                Component.translatable(
                        aroundOrigin
                                ? LangDefs.FLIP_VERTICAL_AROUND_ORIGIN.getTranslationKey()
                                : LangDefs.FLIP_VERTICAL.getTranslationKey()
                )
        ));

        this.rotateButton.setTooltip(Tooltip.create(
                Component.translatable(
                        aroundOrigin
                                ? LangDefs.ROTATE_CLOCKWISE_AROUND_ORIGIN.getTranslationKey()
                                : LangDefs.ROTATE_CLOCKWISE.getTranslationKey()
                )
        ));
    }

    @Override
    protected void renderWidget(
            @NotNull GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        if (!this.visible) {
            return;
        }

        updateChildPositions();

        this.flipEastWestButton.active = this.active;
        this.flipNorthSouthButton.active = this.active;
        this.flipVerticalButton.active = this.active;
        this.rotateButton.active = this.active;

        this.flipEastWestButton.visible = true;
        this.flipNorthSouthButton.visible = true;
        this.flipVerticalButton.visible = true;
        this.rotateButton.visible = true;

        this.flipEastWestButton.render(graphics, mouseX, mouseY, partialTick);
        this.flipNorthSouthButton.render(graphics, mouseX, mouseY, partialTick);
        this.flipVerticalButton.render(graphics, mouseX, mouseY, partialTick);
        this.rotateButton.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.visible || !this.active) {
            return false;
        }

        return this.flipEastWestButton.mouseClicked(mouseX, mouseY, button)
                || this.flipNorthSouthButton.mouseClicked(mouseX, mouseY, button)
                || this.flipVerticalButton.mouseClicked(mouseX, mouseY, button)
                || this.rotateButton.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        return this.flipEastWestButton.mouseReleased(mouseX, mouseY, button)
                || this.flipNorthSouthButton.mouseReleased(mouseX, mouseY, button)
                || this.flipVerticalButton.mouseReleased(mouseX, mouseY, button)
                || this.rotateButton.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(
            double mouseX,
            double mouseY,
            int button,
            double dragX,
            double dragY
    ) {
        return this.flipEastWestButton.mouseDragged(mouseX, mouseY, button, dragX, dragY)
                || this.flipNorthSouthButton.mouseDragged(mouseX, mouseY, button, dragX, dragY)
                || this.flipVerticalButton.mouseDragged(mouseX, mouseY, button, dragX, dragY)
                || this.rotateButton.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        return this.flipEastWestButton.mouseScrolled(mouseX, mouseY, delta)
                || this.flipNorthSouthButton.mouseScrolled(mouseX, mouseY, delta)
                || this.flipVerticalButton.mouseScrolled(mouseX, mouseY, delta)
                || this.rotateButton.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    protected void updateWidgetNarration(@NotNull NarrationElementOutput narrationElementOutput) {
    }
}