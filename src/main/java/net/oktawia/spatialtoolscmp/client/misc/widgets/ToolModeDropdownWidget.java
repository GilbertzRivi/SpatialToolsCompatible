package net.oktawia.spatialtoolscmp.client.misc.widgets;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.oktawia.spatialtoolscmp.defs.LangDefs;
import net.oktawia.spatialtoolscmp.items.helpers.SpatialMultiTool;
import net.oktawia.spatialtoolscmp.network.NetworkHandler;
import net.oktawia.spatialtoolscmp.network.packets.SwapSpatialToolPacket;
import org.jetbrains.annotations.Nullable;

public class ToolModeDropdownWidget {

    public static final int WIDTH = 148;
    public static final int ROW_HEIGHT = 16;

    private static final int HEADER_BG = 0xFF2B2B2B;
    private static final int ROW_BG = 0xFF141414;
    private static final int ROW_BG_HOVER = 0xFF3C8C3C;

    private static final int BORDER = 0xFFE0E0E0;
    private static final int BORDER_SELECTED = 0xFFFFDD55;

    private static final int TEXT = 0xFFE0E0E0;
    private static final int TEXT_DIM = 0xFFAAAAAA;
    private static final int TEXT_SELECTED = 0xFF55FF55;

    private int x;
    private int y;

    private boolean visible;
    private boolean open;

    private @Nullable SpatialMultiTool.Mode currentMode;

    public void setPosition(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public void setToolStack(ItemStack stack) {
        this.visible = SpatialMultiTool.isMultiTool(stack);
        this.currentMode = SpatialMultiTool.getMode(stack);

        if (!this.visible) {
            this.open = false;
        }
    }

    public boolean isVisible() {
        return this.visible;
    }

    public boolean isOpen() {
        return this.visible && this.open;
    }

    public void setOpen(boolean open) {
        this.open = open && this.visible;
    }

    public int getHeight() {
        return this.open
                ? ROW_HEIGHT * (1 + SpatialMultiTool.MODES.size())
                : ROW_HEIGHT;
    }

    public Rect2i getBounds() {
        return new Rect2i(this.x, this.y, WIDTH, getHeight());
    }

    public static Component modeName(@Nullable SpatialMultiTool.Mode mode) {
        return mode == null
                ? Component.translatable(LangDefs.MULTITOOL_NO_MODE.getTranslationKey())
                : Component.translatable(mode.item().getDescriptionId());
    }

    public void render(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        if (!this.visible) {
            return;
        }

        graphics.fill(this.x, this.y, this.x + WIDTH, this.y + ROW_HEIGHT, HEADER_BG);
        graphics.renderOutline(
                this.x,
                this.y,
                WIDTH,
                ROW_HEIGHT,
                this.open ? BORDER_SELECTED : BORDER
        );

        graphics.drawCenteredString(
                font,
                modeName(this.currentMode),
                this.x + WIDTH / 2,
                this.y + 4,
                this.currentMode == null ? TEXT_DIM : TEXT
        );

        graphics.drawString(
                font,
                this.open ? "-" : "+",
                this.x + WIDTH - 10,
                this.y + 4,
                TEXT_DIM,
                false
        );

        if (!this.open) {
            return;
        }

        int rowY = this.y + ROW_HEIGHT;

        for (SpatialMultiTool.Mode mode : SpatialMultiTool.MODES) {
            boolean hovered = isInside(mouseX, mouseY, rowY);
            boolean active = mode == this.currentMode;

            graphics.fill(this.x, rowY, this.x + WIDTH, rowY + ROW_HEIGHT, hovered ? ROW_BG_HOVER : ROW_BG);
            graphics.renderOutline(this.x, rowY, WIDTH, ROW_HEIGHT, active ? BORDER_SELECTED : BORDER);

            graphics.drawCenteredString(
                    font,
                    modeName(mode),
                    this.x + WIDTH / 2,
                    rowY + 4,
                    active ? TEXT_SELECTED : TEXT
            );

            rowY += ROW_HEIGHT;
        }
    }

    public boolean isExpandedMouseOver(double mouseX, double mouseY) {
        return this.visible
                && this.open
                && mouseX >= this.x
                && mouseX < this.x + WIDTH
                && mouseY >= this.y
                && mouseY < this.y + getHeight();
    }

    public boolean mouseClicked(double mouseX, double mouseY) {
        if (!this.visible) {
            return false;
        }

        boolean insideColumn = mouseX >= this.x && mouseX < this.x + WIDTH;

        if (insideColumn && mouseY >= this.y && mouseY < this.y + ROW_HEIGHT) {
            this.open = !this.open;
            return true;
        }

        if (!this.open) {
            return false;
        }

        int rowY = this.y + ROW_HEIGHT;

        for (SpatialMultiTool.Mode mode : SpatialMultiTool.MODES) {
            if (insideColumn && mouseY >= rowY && mouseY < rowY + ROW_HEIGHT) {
                this.open = false;

                if (mode != this.currentMode) {
                    NetworkHandler.sendToServer(new SwapSpatialToolPacket(mode));
                }

                return true;
            }

            rowY += ROW_HEIGHT;
        }

        this.open = false;
        return true;
    }

    private boolean isInside(double mouseX, double mouseY, int rowY) {
        return mouseX >= this.x
                && mouseX < this.x + WIDTH
                && mouseY >= rowY
                && mouseY < rowY + ROW_HEIGHT;
    }
}
