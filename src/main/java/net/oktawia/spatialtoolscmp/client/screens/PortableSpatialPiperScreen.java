package net.oktawia.spatialtoolscmp.client.screens;

import java.util.List;

import org.lwjgl.glfw.GLFW;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import net.oktawia.spatialtoolscmp.IsModLoaded;
import net.oktawia.spatialtoolscmp.SpatialConfig;
import net.oktawia.spatialtoolscmp.SpatialToolsCMP;
import net.oktawia.spatialtoolscmp.compat.ae2.AE2GridLinkableHandler;
import net.oktawia.spatialtoolscmp.defs.LangDefs;
import net.oktawia.spatialtoolscmp.items.PortableSpatialPiper;
import net.oktawia.spatialtoolscmp.logic.PiperRoute;
import net.oktawia.spatialtoolscmp.menus.PortableSpatialPiperMenu;

public class PortableSpatialPiperScreen
        extends AbstractSpatialToolScreen<PortableSpatialPiperMenu> {

    private static final ResourceLocation BACKGROUND = SpatialToolsCMP.makeId("textures/gui/background.png");

    private static final int PANEL_W = 256;
    private static final int PANEL_H = 256;

    private static final int TARGET_PANEL_X = 8;
    private static final int TARGET_PANEL_Y = 26;
    private static final int TARGET_PANEL_W = 104;
    private static final int TARGET_PANEL_H = 135;

    private static final int INFO_PANEL_X = 116;
    private static final int INFO_PANEL_Y = 26;
    private static final int INFO_PANEL_W = 132;
    private static final int INFO_PANEL_H = 135;

    private static final int TARGET_SLOT_X = TARGET_PANEL_X + 44;
    private static final int TARGET_SLOT_Y = TARGET_PANEL_Y + 34;

    private static final int ROUTE_LABEL_Y = TARGET_PANEL_Y + 66;
    private static final int BLOCKS_LABEL_Y = TARGET_PANEL_Y + 78;

    private static final int CLEAR_BUTTON_X = TARGET_PANEL_X + 6;
    private static final int CLEAR_BUTTON_Y = TARGET_PANEL_Y + 106;
    private static final int CLEAR_BUTTON_W = TARGET_PANEL_W - 12;
    private static final int CLEAR_BUTTON_H = 18;

    private Button clearTargetButton;

    public PortableSpatialPiperScreen(
            PortableSpatialPiperMenu menu,
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

        this.clearTargetButton = Button.builder(
                Component.translatable(LangDefs.CONTEXT_MENU_CANCEL_SELECTION.getTranslationKey()),
                button -> this.menu.clearTarget()).pos(
                        this.leftPos + CLEAR_BUTTON_X,
                        this.topPos + CLEAR_BUTTON_Y)
                .size(
                        CLEAR_BUTTON_W,
                        CLEAR_BUTTON_H)
                .build();

        this.addRenderableWidget(this.clearTargetButton);
    }

    @Override
    protected void containerTick() {
        super.containerTick();

        if (this.clearTargetButton != null) {
            this.clearTargetButton.active = !getTargetStack().isEmpty();
        }
    }

    @Override
    protected void renderBg(
            GuiGraphics graphics,
            float partialTick,
            int mouseX,
            int mouseY) {
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

        renderPowerUpgradePanel(graphics);
        renderPanels(graphics);
        renderTargetSection(graphics);
        renderInfoSection(graphics);
    }

    private void renderPanels(GuiGraphics graphics) {
        int leftX = this.leftPos + TARGET_PANEL_X;
        int leftY = this.topPos + TARGET_PANEL_Y;

        int infoX = this.leftPos + INFO_PANEL_X;
        int infoY = this.topPos + INFO_PANEL_Y;

        graphics.fill(
                leftX,
                leftY,
                leftX + TARGET_PANEL_W,
                leftY + TARGET_PANEL_H,
                0x22FFFFFF);

        graphics.renderOutline(
                leftX,
                leftY,
                TARGET_PANEL_W,
                TARGET_PANEL_H,
                0xFF606060);

        graphics.fill(
                infoX,
                infoY,
                infoX + INFO_PANEL_W,
                infoY + INFO_PANEL_H,
                0x99000000);

        graphics.renderOutline(
                infoX,
                infoY,
                INFO_PANEL_W,
                INFO_PANEL_H,
                0xFF606060);
    }

    private void renderTargetSection(GuiGraphics graphics) {
        int panelX = this.leftPos + TARGET_PANEL_X;
        int panelY = this.topPos + TARGET_PANEL_Y;

        graphics.drawString(
                this.font,
                Component.translatable(LangDefs.PIPER_TARGET_LABEL.getTranslationKey()),
                panelX + 6,
                panelY + 8,
                0xFF111111,
                false);

        int slotX = this.leftPos + TARGET_SLOT_X;
        int slotY = this.topPos + TARGET_SLOT_Y;

        graphics.fill(slotX - 4, slotY - 4, slotX + 20, slotY + 20, 0xFFB8B8B8);
        graphics.renderOutline(slotX - 4, slotY - 4, 24, 24, 0xFF404040);

        ItemStack target = getTargetStack();

        if (target.isEmpty()) {
            graphics.drawCenteredString(
                    this.font,
                    Component.literal("?").withStyle(ChatFormatting.RED),
                    slotX + 8,
                    slotY + 4,
                    0xFFFF5555);
        } else {
            graphics.renderItem(target, slotX, slotY);
            graphics.renderItemDecorations(this.font, target, slotX, slotY);
        }

        List<BlockPos> route = getRoute();
        int centerX = panelX + TARGET_PANEL_W / 2;

        drawCenteredStringPlain(
                graphics,
                Component.translatable(
                        LangDefs.PIPER_ROUTE_LABEL.getTranslationKey(),
                        route.size()),
                centerX,
                this.topPos + ROUTE_LABEL_Y,
                0xFF111111);

        drawCenteredStringPlain(
                graphics,
                Component.translatable(
                        LangDefs.STRUCTURE_SIZE.getTranslationKey(),
                        routeBlocks(route)),
                centerX,
                this.topPos + BLOCKS_LABEL_Y,
                0xFF111111);
    }

    private long routeBlocks(List<BlockPos> route) {
        ItemStack tool = findRelevantStack();

        boolean fill = !tool.isEmpty()
                && PortableSpatialPiper.getFillMode(tool) == PortableSpatialPiper.FillMode.FILL;

        return fill ? PiperRoute.regionSize(route) : PiperRoute.length(route);
    }

    private void renderInfoSection(GuiGraphics graphics) {
        int panelX = this.leftPos + INFO_PANEL_X;
        int panelY = this.topPos + INFO_PANEL_Y;
        int cx = panelX + INFO_PANEL_W / 2;

        ItemStack tool = findRelevantStack();
        ItemStack target = getTargetStack();

        int y = panelY + 10;

        if (target.isEmpty()) {
            y = drawCenteredWrapped(
                    graphics,
                    Component.translatable(LangDefs.REPLACER_NO_TARGET.getTranslationKey())
                            .withStyle(ChatFormatting.RED),
                    cx,
                    y,
                    0xFFFF7777) + 6;

            y = drawCenteredWrapped(
                    graphics,
                    Component.translatable(LangDefs.PIPER_SELECT_TARGET_HINT.getTranslationKey())
                            .withStyle(ChatFormatting.WHITE),
                    cx,
                    y,
                    0xFFFFFFFF) + 6;
        } else {
            y = drawCenteredWrapped(
                    graphics,
                    target.getHoverName().copy().withStyle(ChatFormatting.AQUA),
                    cx,
                    y,
                    0xFF55FFFF) + 6;

            y = drawCenteredWrapped(
                    graphics,
                    Component.translatable(LangDefs.PIPER_ROUTE_HINT.getTranslationKey())
                            .withStyle(ChatFormatting.WHITE),
                    cx,
                    y,
                    0xFFFFFFFF) + 4;

            y = drawCenteredWrapped(
                    graphics,
                    Component.translatable(LangDefs.PIPER_CONFIRM_HINT.getTranslationKey())
                            .withStyle(ChatFormatting.WHITE),
                    cx,
                    y,
                    0xFFFFFFFF) + 6;
        }

        y = drawCenteredWrapped(
                graphics,
                Component.translatable(
                        LangDefs.REPLACER_CAP_INFO.getTranslationKey(),
                        SpatialConfig.COMMON.PORTABLE_SPATIAL_PIPER_MAX_BLOCKS.get()),
                cx,
                y,
                0xFFEDEDED) + 4;

        if (IsModLoaded.AE2) {
            renderAe2Status(graphics, tool, cx, y);
        }
    }

    private void renderAe2Status(GuiGraphics graphics, ItemStack tool, int cx, int y) {
        if (tool.isEmpty()) {
            return;
        }

        try {
            GlobalPos ae2Pos = AE2GridLinkableHandler.getLinkedPos(tool);

            if (ae2Pos != null) {
                String coords = ae2Pos.pos().getX()
                        + " "
                        + ae2Pos.pos().getY()
                        + " "
                        + ae2Pos.pos().getZ();

                drawCenteredWrapped(
                        graphics,
                        Component.translatable(
                                LangDefs.PORTABLE_SPATIAL_CLONER_LINKED_TO_AE2.getTranslationKey(),
                                coords).withStyle(ChatFormatting.AQUA),
                        cx,
                        y,
                        0xFF55FFFF);
            } else {
                drawCenteredWrapped(
                        graphics,
                        Component.translatable(
                                LangDefs.REPLACER_NOT_LINKED_TO_AE2.getTranslationKey())
                                .withStyle(ChatFormatting.WHITE),
                        cx,
                        y,
                        0xFFEDEDED);
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void render(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);

        renderToolModeDropdown(graphics, mouseX, mouseY);
        renderTargetTooltip(graphics, mouseX, mouseY);
        renderPowerUpgradeTooltip(graphics, mouseX, mouseY);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(
            GuiGraphics graphics,
            int mouseX,
            int mouseY) {
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

    private void renderTargetTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = this.leftPos + TARGET_SLOT_X;
        int y = this.topPos + TARGET_SLOT_Y;

        if (!isMouseInside(mouseX, mouseY, x, y, 16, 16)) {
            return;
        }

        ItemStack target = getTargetStack();

        if (target.isEmpty()) {
            graphics.renderComponentTooltip(
                    this.font,
                    List.of(Component.translatable(
                            LangDefs.PIPER_SELECT_TARGET_HINT.getTranslationKey()).withStyle(ChatFormatting.WHITE)),
                    mouseX,
                    mouseY);
            return;
        }

        graphics.renderTooltip(
                this.font,
                target.getTooltipLines(
                        Minecraft.getInstance().player,
                        TooltipFlag.Default.NORMAL),
                target.getTooltipImage(),
                mouseX,
                mouseY);
    }

    private int drawCenteredWrapped(
            GuiGraphics graphics,
            Component component,
            int centerX,
            int y,
            int color) {
        int yy = y;

        for (var line : this.font.split(component, INFO_PANEL_W - 16)) {
            graphics.drawCenteredString(this.font, line, centerX, yy, color);
            yy += 10;
        }

        return yy;
    }

    private void drawCenteredStringPlain(
            GuiGraphics graphics,
            Component component,
            int centerX,
            int y,
            int color) {
        graphics.drawString(
                this.font,
                component,
                centerX - this.font.width(component) / 2,
                y,
                color,
                false);
    }

    private boolean isMouseInside(
            double mouseX,
            double mouseY,
            int x,
            int y,
            int width,
            int height) {
        return mouseX >= x
                && mouseY >= y
                && mouseX < x + width
                && mouseY < y + height;
    }

    private List<BlockPos> getRoute() {
        ItemStack tool = findRelevantStack();

        return tool.isEmpty() ? List.of() : PortableSpatialPiper.getRoute(tool);
    }

    private ItemStack getTargetStack() {
        ItemStack tool = findRelevantStack();

        return tool.isEmpty() ? ItemStack.EMPTY : PortableSpatialPiper.getTargetBlock(tool);
    }

    private ItemStack findRelevantStack() {
        ItemStack stack = this.menu.getToolStack();

        if (!stack.isEmpty() && stack.getItem() instanceof PortableSpatialPiper) {
            return stack;
        }

        var player = Minecraft.getInstance().player;

        if (player == null) {
            return ItemStack.EMPTY;
        }

        ItemStack main = player.getMainHandItem();

        if (!main.isEmpty() && main.getItem() instanceof PortableSpatialPiper) {
            return main;
        }

        ItemStack off = player.getOffhandItem();

        if (!off.isEmpty() && off.getItem() instanceof PortableSpatialPiper) {
            return off;
        }

        return ItemStack.EMPTY;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.onClose();
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
