package net.oktawia.spatialtoolscmp.client.misc.widgets;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import lombok.Setter;

public class SimpleTextFieldWidget extends EditBox {

    private static final int PADDING = 2;

    private static final int DEFAULT_BACKGROUND_COLOR = 0xFF111111;
    private static final int DEFAULT_BACKGROUND_FOCUSED_COLOR = 0xFF151515;
    private static final int DEFAULT_BACKGROUND_DISABLED_COLOR = 0xFF090909;

    private static final int DEFAULT_BORDER_COLOR = 0xFF555555;
    private static final int DEFAULT_BORDER_FOCUSED_COLOR = 0xFF55FFFF;
    private static final int DEFAULT_BORDER_DISABLED_COLOR = 0xFF333333;

    private static final int DEFAULT_TEXT_COLOR = 0xFFE0E0E0;
    private static final int DEFAULT_TEXT_DISABLED_COLOR = 0xFF777777;
    private static final int DEFAULT_PLACEHOLDER_COLOR = 0xFF777777;

    private final int fontPad;

    private int backgroundColor = DEFAULT_BACKGROUND_COLOR;
    private int backgroundFocusedColor = DEFAULT_BACKGROUND_FOCUSED_COLOR;
    private int backgroundDisabledColor = DEFAULT_BACKGROUND_DISABLED_COLOR;

    private int borderColor = DEFAULT_BORDER_COLOR;
    private int borderFocusedColor = DEFAULT_BORDER_FOCUSED_COLOR;
    private int borderDisabledColor = DEFAULT_BORDER_DISABLED_COLOR;

    @Setter
    private int placeholderColor = DEFAULT_PLACEHOLDER_COLOR;

    private boolean editable = true;

    @Nullable
    private Component placeholder;

    public SimpleTextFieldWidget(Font font, int x, int y, int width, int height) {
        this(font, x, y, width, height, Component.empty());
    }

    public SimpleTextFieldWidget(Font font, int x, int y, int width, int height, Component message) {
        super(
                font,
                x + PADDING,
                y + PADDING,
                Math.max(1, width - 2 * PADDING - font.width("_")),
                Math.max(1, height - 2 * PADDING),
                message);

        this.fontPad = font.width("_");

        super.setBordered(false);
        this.setTextColor(DEFAULT_TEXT_COLOR);
        this.setTextColorUneditable(DEFAULT_TEXT_DISABLED_COLOR);
    }

    @Override
    public void setBordered(boolean bordered) {
        super.setBordered(false);
    }

    @Override
    public void setEditable(boolean editable) {
        this.editable = editable;
        super.setEditable(editable);
    }

    public void move(int x, int y) {
        super.setX(x + PADDING);
        super.setY(y + PADDING);
    }

    public void resize(int width, int height) {
        super.setWidth(Math.max(1, width - 2 * PADDING - this.fontPad));
        this.height = Math.max(1, height - 2 * PADDING);
    }

    public void setBounds(int x, int y, int width, int height) {
        move(x, y);
        resize(width, height);
    }

    @Nullable

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        VisualBounds bounds = getVisualBounds();

        return mouseX >= bounds.left()
                && mouseX < bounds.right()
                && mouseY >= bounds.top()
                && mouseY < bounds.bottom();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isMouseOver(mouseX, mouseY)) {
            mouseX = Mth.clamp(mouseX, getX(), getX() + this.width - 1);
            mouseY = Mth.clamp(mouseY, getY(), getY() + this.height - 1);
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }

        return isFocused()
                && canConsumeInput()
                && keyCode != GLFW.GLFW_KEY_TAB
                && keyCode != GLFW.GLFW_KEY_ESCAPE;
    }

    @Override
    public void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (!this.visible) {
            return;
        }

        VisualBounds bounds = getVisualBounds();

        int bgColor;
        int outlineColor;

        if (!this.editable) {
            bgColor = this.backgroundDisabledColor;
            outlineColor = this.borderDisabledColor;
        } else if (this.isFocused()) {
            bgColor = this.backgroundFocusedColor;
            outlineColor = this.borderFocusedColor;
        } else {
            bgColor = this.backgroundColor;
            outlineColor = this.borderColor;
        }

        guiGraphics.fill(bounds.left(), bounds.top(), bounds.right(), bounds.bottom(), bgColor);
        drawBorder(guiGraphics, bounds.left(), bounds.top(), bounds.right(), bounds.bottom(), outlineColor);

        super.renderWidget(guiGraphics, mouseX, mouseY, partialTick);

        if (this.placeholder != null && !this.isFocused() && this.getValue().isEmpty()) {
            guiGraphics.drawString(
                    Minecraft.getInstance().font,
                    this.placeholder,
                    getX(),
                    getY(),
                    this.placeholderColor,
                    false);
        }
    }

    private VisualBounds getVisualBounds() {
        int left = getX() - PADDING;
        int top = getY() - PADDING;
        int right = left + this.width + 2 * PADDING + this.fontPad;
        int bottom = top + this.height + 2 * PADDING;

        return new VisualBounds(left, top, right, bottom);
    }

    private static void drawBorder(GuiGraphics guiGraphics, int left, int top, int right, int bottom, int color) {
        guiGraphics.fill(left, top, right, top + 1, color);
        guiGraphics.fill(left, bottom - 1, right, bottom, color);
        guiGraphics.fill(left, top, left + 1, bottom, color);
        guiGraphics.fill(right - 1, top, right, bottom, color);
    }

    private record VisualBounds(int left, int top, int right, int bottom) {
    }
}
