package net.oktawia.spatialtoolscmp.client.screens;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import net.oktawia.spatialtoolscmp.items.PortableSpatialCloner;
import net.oktawia.spatialtoolscmp.items.PortableSpatialStorage;
import net.oktawia.spatialtoolscmp.logic.StructureToolUtil;
import net.oktawia.spatialtoolscmp.menus.PortableSpatialStorageMenu;

public class PortableSpatialStorageScreen
        extends AbstractPortableStructureToolScreen<PortableSpatialStorageMenu> {

    private static final int BUTTON_SIZE = 16;

    private static final int PREVIEW_LEFT = 8;
    private static final int PREVIEW_TOP = 26;
    private static final int PREVIEW_WIDTH = 240;
    private static final int PREVIEW_HEIGHT = 135;

    public PortableSpatialStorageScreen(PortableSpatialStorageMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);

        this.imageWidth = 256;
        this.imageHeight = 256;

        this.titleLabelX = 8;
        this.titleLabelY = 6;

        this.inventoryLabelX = 47;
        this.inventoryLabelY = 163;
    }

    @Override
    protected void init() {
        super.init();
        initCommonWidgets(buildCompatibleUpgradesTooltip());
        finishInit();
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        super.renderBg(graphics, partialTick, mouseX, mouseY);
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
    }

    @Override
    protected PreviewRect getPreviewRect() {
        return new PreviewRect(
                this.leftPos + PREVIEW_LEFT,
                this.topPos + PREVIEW_TOP,
                PREVIEW_WIDTH,
                PREVIEW_HEIGHT);
    }

    @Override
    protected ItemStack findRelevantStack() {
        var player = Minecraft.getInstance().player;

        if (player == null) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = StructureToolUtil.findActive(
                player,
                PortableSpatialStorage.class,
                PortableSpatialCloner.class);

        if (stack.isEmpty()) {
            stack = StructureToolUtil.findHeld(
                    player,
                    PortableSpatialStorage.class,
                    PortableSpatialCloner.class);
        }

        return stack;
    }
}
