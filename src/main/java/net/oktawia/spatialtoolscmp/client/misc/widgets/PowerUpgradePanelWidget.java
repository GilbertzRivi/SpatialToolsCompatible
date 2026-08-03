package net.oktawia.spatialtoolscmp.client.misc.widgets;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;

import net.oktawia.spatialtoolscmp.SpatialToolsCMP;

public class PowerUpgradePanelWidget {

    public static final int PANEL_LEFT = 258;
    public static final int PANEL_TOP = 0;

    public static final int PANEL_FRAME = 7;
    public static final int SLOT_FRAME = 1;
    public static final int SLOT_SIZE = 16;
    public static final int SLOT_TOTAL_SIZE = SLOT_FRAME + SLOT_SIZE + SLOT_FRAME;

    public static final int PANEL_WIDTH = PANEL_FRAME + SLOT_TOTAL_SIZE + PANEL_FRAME;

    public static final int SLOT_LEFT = PANEL_LEFT + PANEL_FRAME + SLOT_FRAME;
    public static final int SLOT_TOP = PANEL_TOP + PANEL_FRAME + SLOT_FRAME;
    public static final int SLOT_STEP = SLOT_TOTAL_SIZE;

    private static final ResourceLocation STORAGE_TEXTURE = SpatialToolsCMP.makeId(
            "textures/gui/upgrades_storage.png");

    private static final ResourceLocation CLONER_TEXTURE = SpatialToolsCMP.makeId(
            "textures/gui/upgrades_cloner.png");

    private int leftPos;
    private int topPos;
    private int slots;
    private int maxUpgrades;

    public void setScreenPosition(int leftPos, int topPos) {
        this.leftPos = leftPos;
        this.topPos = topPos;
    }

    public void setSlots(int slots, int maxUpgrades) {
        this.slots = Math.max(0, slots);
        this.maxUpgrades = Math.max(0, maxUpgrades);
    }

    public void renderBackground(GuiGraphics graphics) {
        if (this.slots <= 0) {
            return;
        }

        int x = this.leftPos + PANEL_LEFT;
        int y = this.topPos + PANEL_TOP;
        int height = getPanelHeight();

        ResourceLocation texture = this.slots >= 5
                ? CLONER_TEXTURE
                : STORAGE_TEXTURE;

        graphics.blit(
                texture,
                x,
                y,
                0,
                0,
                PANEL_WIDTH,
                height,
                PANEL_WIDTH,
                height);

        renderDisabledSlots(graphics);
    }

    private void renderDisabledSlots(GuiGraphics graphics) {
        if (this.maxUpgrades >= this.slots) {
            return;
        }

        int craftingSlot = getCraftingSlotIndex();
        int slotX = this.leftPos + SLOT_LEFT;

        for (int slot = this.maxUpgrades; slot < this.slots; slot++) {
            if (slot == craftingSlot) {
                continue;
            }

            int slotY = this.topPos + SLOT_TOP + slot * SLOT_STEP;

            graphics.fill(
                    slotX,
                    slotY,
                    slotX + SLOT_SIZE,
                    slotY + SLOT_SIZE,
                    0x99000000);
        }
    }

    public void renderCraftingSlotBadge(GuiGraphics graphics, boolean installed) {
        if (installed) {
            return;
        }

        int craftingSlot = getCraftingSlotIndex();

        if (craftingSlot < 0) {
            return;
        }

        int x = this.leftPos + SLOT_LEFT;
        int y = this.topPos + SLOT_TOP + craftingSlot * SLOT_STEP;

        int frame = 0xFFEEAA22;

        graphics.fill(x - 2, y - 2, x + SLOT_SIZE + 2, y, frame);
        graphics.fill(x - 2, y + SLOT_SIZE, x + SLOT_SIZE + 2, y + SLOT_SIZE + 2, frame);
        graphics.fill(x - 2, y, x, y + SLOT_SIZE, frame);
        graphics.fill(x + SLOT_SIZE, y, x + SLOT_SIZE + 2, y + SLOT_SIZE, frame);
    }

    private int getCraftingSlotIndex() {
        return this.slots > this.maxUpgrades
                ? this.maxUpgrades
                : -1;
    }

    public int getPanelHeight() {
        return getPanelHeight(this.slots);
    }

    public int getAbsoluteLeft() {
        return this.leftPos + PANEL_LEFT;
    }

    public int getAbsoluteTop() {
        return this.topPos + PANEL_TOP;
    }

    public int getAbsoluteRight() {
        return getAbsoluteLeft() + PANEL_WIDTH;
    }

    public int getAbsoluteBottom() {
        return getAbsoluteTop() + getPanelHeight();
    }

    public static int getPanelHeight(int slots) {
        return PANEL_FRAME + Math.max(0, slots) * SLOT_TOTAL_SIZE + PANEL_FRAME;
    }

    public static Rect2i getExtraArea(AbstractContainerScreen<?> screen, int slots) {
        return new Rect2i(
                screen.getGuiLeft() + PANEL_LEFT,
                screen.getGuiTop() + PANEL_TOP,
                PANEL_WIDTH,
                getPanelHeight(slots));
    }

    public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= getAbsoluteLeft()
                && mouseY >= getAbsoluteTop()
                && mouseX < getAbsoluteRight()
                && mouseY < getAbsoluteBottom();
    }
}
