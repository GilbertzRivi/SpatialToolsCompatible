package net.oktawia.spatialtoolscmp.client.screens;

import java.util.List;
import java.util.stream.Collectors;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import net.oktawia.spatialtoolscmp.client.misc.widgets.ClonerMaterialListWidget;
import net.oktawia.spatialtoolscmp.compat.ae2.CraftingBufferMenu;
import net.oktawia.spatialtoolscmp.defs.LangDefs;

public class CraftingBufferScreen extends AbstractContainerScreen<CraftingBufferMenu> {

    private static final int BG_COLOR = 0xFFC6C6C6;
    private static final int BORDER_COLOR = 0xFF555555;
    private static final int TEXT_ERROR_COLOR = 0xFFAA0000;

    private ClonerMaterialListWidget itemList;

    public CraftingBufferScreen(CraftingBufferMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 176;
        this.imageHeight = 120;
        this.titleLabelX = 8;
        this.titleLabelY = 6;
        this.inventoryLabelX = Integer.MIN_VALUE;
        this.inventoryLabelY = Integer.MIN_VALUE;
    }

    @Override
    protected void init() {
        super.init();

        itemList = new ClonerMaterialListWidget(leftPos + 4, topPos + 20, imageWidth - 8, imageHeight - 24);
        itemList.setCraftButtonsEnabled(false);

        addRenderableWidget(itemList);
        rebuildList();
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        rebuildList();
    }

    private void rebuildList() {
        if (menu.isHasError()) {
            itemList.visible = false;
            itemList.setEntries(List.of());
            return;
        }

        if (menu.getEntries().isEmpty()) {
            itemList.visible = false;
            itemList.setEntries(List.of());
            return;
        }

        itemList.visible = true;

        List<ClonerMaterialListWidget.MaterialEntry> materialEntries = menu.getEntries().stream()
                .map(e -> new ClonerMaterialListWidget.MaterialEntry(
                        e.stack(),
                        e.requestedAmount(),
                        e.bufferedAmount(),
                        false))
                .collect(Collectors.toList());

        itemList.setEntries(materialEntries);
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

        if (menu.isHasError()) {
            Component errorLine1 = Component.translatable(
                    LangDefs.CRAFTING_BUFFER_ERROR_NO_CPUS_LINE_1.getTranslationKey());
            Component errorLine2 = Component.translatable(
                    LangDefs.CRAFTING_BUFFER_ERROR_NO_CPUS_LINE_2.getTranslationKey());

            graphics.drawString(font, errorLine1, leftPos + 8, topPos + 30, TEXT_ERROR_COLOR, false);
            graphics.drawString(font, errorLine2, leftPos + 8, topPos + 42, TEXT_ERROR_COLOR, false);
            return;
        }

        if (menu.getEntries().isEmpty()) {
            Component idleText = Component.translatable(
                    LangDefs.CRAFTING_BUFFER_IDLE.getTranslationKey());

            int textWidth = font.width(idleText);
            int x = leftPos + (imageWidth - textWidth) / 2;
            int y = topPos + 54;

            graphics.drawString(font, idleText, x, y, 0xFF444444, false);
        }
    }
}
