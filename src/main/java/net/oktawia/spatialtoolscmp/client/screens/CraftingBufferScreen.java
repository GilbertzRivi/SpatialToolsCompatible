package net.oktawia.spatialtoolscmp.client.screens;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.oktawia.spatialtoolscmp.compat.ae2.CraftingBufferMenu;

public class CraftingBufferScreen extends AbstractContainerScreen<CraftingBufferMenu> {

    private static final int BG_COLOR = 0xFFC6C6C6;
    private static final int BORDER_COLOR = 0xFF555555;
    private static final int TEXT_COLOR = 0xFF404040;
    private static final int TEXT_OK_COLOR = 0xFF007700;
    private static final int TEXT_ERROR_COLOR = 0xFFAA0000;

    public CraftingBufferScreen(CraftingBufferMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 56;
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        this.inventoryLabelX = Integer.MIN_VALUE;
        this.inventoryLabelY = Integer.MIN_VALUE;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;
        graphics.fill(x, y, x + imageWidth, y + imageHeight, BG_COLOR);
        graphics.fill(x, y, x + imageWidth, y + 1, BORDER_COLOR);
        graphics.fill(x, y + imageHeight - 1, x + imageWidth, y + imageHeight, BORDER_COLOR);
        graphics.fill(x, y, x + 1, y + imageHeight, BORDER_COLOR);
        graphics.fill(x + imageWidth - 1, y, x + imageWidth, y + imageHeight, BORDER_COLOR);
        graphics.fill(x, y + 18, x + imageWidth, y + 19, BORDER_COLOR);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);

        String statusText = menu.getStatusText();
        boolean isOk = statusText.isEmpty();
        String line = isOk ? "Status: OK" : "Status: " + statusText;
        int color = isOk ? TEXT_OK_COLOR : TEXT_ERROR_COLOR;

        graphics.drawString(font, line, leftPos + 8, topPos + 26, color, false);
    }
}
