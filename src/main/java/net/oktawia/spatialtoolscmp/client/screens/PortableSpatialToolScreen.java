package net.oktawia.spatialtoolscmp.client.screens;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

import net.oktawia.spatialtoolscmp.SpatialToolsCMP;
import net.oktawia.spatialtoolscmp.defs.LangDefs;
import net.oktawia.spatialtoolscmp.menus.PortableSpatialToolMenu;

public class PortableSpatialToolScreen extends AbstractSpatialToolScreen<PortableSpatialToolMenu> {

    private static final ResourceLocation BACKGROUND = SpatialToolsCMP.makeId("textures/gui/background.png");

    private static final int PANEL_W = 256;
    private static final int PANEL_H = 256;

    private static final int HINT_PANEL_X = 8;
    private static final int HINT_PANEL_Y = 26;
    private static final int HINT_PANEL_W = 240;
    private static final int HINT_PANEL_H = 135;

    public PortableSpatialToolScreen(
            PortableSpatialToolMenu menu,
            Inventory playerInventory,
            Component title) {
        super(menu, playerInventory, title);

        this.imageWidth = PANEL_W;
        this.imageHeight = PANEL_H;

        this.titleLabelX = 8;
        this.titleLabelY = 6;

        this.inventoryLabelX = 47;
        this.inventoryLabelY = 163;
    }

    @Override
    protected void init() {
        super.init();

        layoutToolModeDropdown();
        this.toolModeDropdown.setOpen(true);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.blit(
                BACKGROUND,
                this.leftPos,
                this.topPos,
                0,
                0,
                this.imageWidth,
                this.imageHeight,
                256,
                256);

        int panelX = this.leftPos + HINT_PANEL_X;
        int panelY = this.topPos + HINT_PANEL_Y;

        graphics.fill(panelX, panelY, panelX + HINT_PANEL_W, panelY + HINT_PANEL_H, 0x99000000);
        graphics.renderOutline(panelX, panelY, HINT_PANEL_W, HINT_PANEL_H, 0xFF606060);

        graphics.drawCenteredString(
                this.font,
                Component.translatable(LangDefs.MULTITOOL_PICK_MODE_HINT.getTranslationKey())
                        .withStyle(ChatFormatting.GRAY),
                panelX + HINT_PANEL_W / 2,
                panelY + HINT_PANEL_H / 2 - 4,
                0xFFE0E0E0);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);

        renderToolModeDropdown(graphics, mouseX, mouseY);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(
                this.font,
                this.title,
                this.titleLabelX,
                this.titleLabelY,
                0xFF111111,
                false);

        graphics.drawString(
                this.font,
                this.playerInventoryTitle,
                this.inventoryLabelX,
                this.inventoryLabelY,
                0xFF111111,
                false);
    }
}
