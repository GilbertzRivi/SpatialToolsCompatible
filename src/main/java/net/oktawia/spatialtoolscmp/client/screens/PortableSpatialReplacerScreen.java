package net.oktawia.spatialtoolscmp.client.screens;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
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
import net.oktawia.spatialtoolscmp.items.PortableSpatialReplacer;
import net.oktawia.spatialtoolscmp.logic.ReplacerContext.ConnectivityMode;
import net.oktawia.spatialtoolscmp.menus.PortableSpatialReplacerMenu;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class PortableSpatialReplacerScreen
        extends AbstractContainerScreen<PortableSpatialReplacerMenu> {

    private static final ResourceLocation BACKGROUND =
            SpatialToolsCMP.makeId("textures/gui/background.png");

    private static final int PANEL_W = 256;
    private static final int PANEL_H = 256;

    private static final int TARGET_PANEL_X = 8;
    private static final int TARGET_PANEL_Y = 26;
    private static final int TARGET_PANEL_W = 92;
    private static final int TARGET_PANEL_H = 135;

    private static final int INFO_PANEL_X = 104;
    private static final int INFO_PANEL_Y = 26;
    private static final int INFO_PANEL_W = 144;
    private static final int INFO_PANEL_H = 135;

    private static final int TARGET_SLOT_X = TARGET_PANEL_X + 38;
    private static final int TARGET_SLOT_Y = TARGET_PANEL_Y + 34;

    private static final int RADIUS_LABEL_Y = TARGET_PANEL_Y + 55;

    private static final int RADIUS_BUTTON_Y = TARGET_PANEL_Y + 71;
    private static final int RADIUS_DOWN_X = TARGET_PANEL_X + 8;

    private static final int RADIUS_VALUE_X = TARGET_PANEL_X + 31;
    private static final int RADIUS_VALUE_Y = TARGET_PANEL_Y + 71;
    private static final int RADIUS_VALUE_W = 30;
    private static final int RADIUS_VALUE_H = 18;

    private static final int RADIUS_UP_X = TARGET_PANEL_X + 66;
    private static final int RADIUS_BUTTON_SIZE = 18;

    private static final int CONNECTIVITY_X = TARGET_PANEL_X + 8;
    private static final int CONNECTIVITY_Y = TARGET_PANEL_Y + 92;
    private static final int CONNECTIVITY_W = 76;
    private static final int CONNECTIVITY_H = 18;

    private static final int BLOCKSTATE_X = TARGET_PANEL_X + 8;
    private static final int BLOCKSTATE_Y = TARGET_PANEL_Y + 113;
    private static final int BLOCKSTATE_W = 76;
    private static final int BLOCKSTATE_H = 18;

    private Button radiusDownButton;
    private Button radiusUpButton;
    private Button connectivityButton;
    private Button blockstateButton;

    public PortableSpatialReplacerScreen(
            PortableSpatialReplacerMenu menu,
            Inventory playerInventory,
            Component title
    ) {
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

        this.radiusDownButton = Button.builder(
                Component.literal("-"),
                button -> this.menu.radiusDown()
        ).pos(
                this.leftPos + RADIUS_DOWN_X,
                this.topPos + RADIUS_BUTTON_Y
        ).size(
                RADIUS_BUTTON_SIZE,
                RADIUS_BUTTON_SIZE
        ).build();

        this.addRenderableWidget(this.radiusDownButton);

        this.radiusUpButton = Button.builder(
                Component.literal("+"),
                button -> this.menu.radiusUp()
        ).pos(
                this.leftPos + RADIUS_UP_X,
                this.topPos + RADIUS_BUTTON_Y
        ).size(
                RADIUS_BUTTON_SIZE,
                RADIUS_BUTTON_SIZE
        ).build();

        this.addRenderableWidget(this.radiusUpButton);

        this.connectivityButton = Button.builder(
                shortConnectivityLabel(),
                button -> {
                    this.menu.toggleConnectivity();
                    button.setMessage(shortConnectivityLabel());
                }
        ).pos(
                this.leftPos + CONNECTIVITY_X,
                this.topPos + CONNECTIVITY_Y
        ).size(
                CONNECTIVITY_W,
                CONNECTIVITY_H
        ).build();

        this.addRenderableWidget(this.connectivityButton);

        this.blockstateButton = Button.builder(
                blockstateLabel(),
                button -> {
                    this.menu.toggleBlockstateMode();
                    button.setMessage(blockstateLabel());
                }
        ).pos(
                this.leftPos + BLOCKSTATE_X,
                this.topPos + BLOCKSTATE_Y
        ).size(
                BLOCKSTATE_W,
                BLOCKSTATE_H
        ).build();

        this.addRenderableWidget(this.blockstateButton);
    }

    @Override
    protected void containerTick() {
        super.containerTick();

        ItemStack tool = findRelevantStack();
        boolean hasTool = !tool.isEmpty();

        if (this.radiusDownButton != null) {
            this.radiusDownButton.active = hasTool
                    && PortableSpatialReplacer.getRadius(tool) > PortableSpatialReplacer.MIN_RADIUS;
        }

        if (this.radiusUpButton != null) {
            this.radiusUpButton.active = hasTool
                    && PortableSpatialReplacer.getRadius(tool) < PortableSpatialReplacer.MAX_RADIUS;
        }

        if (this.connectivityButton != null) {
            this.connectivityButton.active = hasTool;
            this.connectivityButton.setMessage(shortConnectivityLabel());
        }

        if (this.blockstateButton != null) {
            this.blockstateButton.active = hasTool;
            this.blockstateButton.setMessage(blockstateLabel());
        }
    }

    @Override
    protected void renderBg(
            GuiGraphics graphics,
            float partialTick,
            int mouseX,
            int mouseY
    ) {
        graphics.blit(
                BACKGROUND,
                this.leftPos,
                this.topPos,
                0,
                0,
                this.imageWidth,
                this.imageHeight,
                256,
                256
        );

        renderReplacerPanels(graphics);
        renderTargetSection(graphics);
        renderRadiusSection(graphics);
        renderInfoSection(graphics);
    }

    private void renderReplacerPanels(GuiGraphics graphics) {
        int leftX = this.leftPos + TARGET_PANEL_X;
        int leftY = this.topPos + TARGET_PANEL_Y;

        int infoX = this.leftPos + INFO_PANEL_X;
        int infoY = this.topPos + INFO_PANEL_Y;

        graphics.fill(
                leftX,
                leftY,
                leftX + TARGET_PANEL_W,
                leftY + TARGET_PANEL_H,
                0x22FFFFFF
        );

        graphics.renderOutline(
                leftX,
                leftY,
                TARGET_PANEL_W,
                TARGET_PANEL_H,
                0xFF606060
        );

        graphics.fill(
                infoX,
                infoY,
                infoX + INFO_PANEL_W,
                infoY + INFO_PANEL_H,
                0x99000000
        );

        graphics.renderOutline(
                infoX,
                infoY,
                INFO_PANEL_W,
                INFO_PANEL_H,
                0xFF606060
        );
    }

    private void renderTargetSection(GuiGraphics graphics) {
        int panelX = this.leftPos + TARGET_PANEL_X;
        int panelY = this.topPos + TARGET_PANEL_Y;

        graphics.drawString(
                this.font,
                Component.translatable(LangDefs.REPLACER_TARGET_LABEL.getTranslationKey()),
                panelX + 6,
                panelY + 8,
                0xFF111111,
                false
        );

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
                    0xFFFF5555
            );
            return;
        }

        graphics.renderItem(target, slotX, slotY);
        graphics.renderItemDecorations(this.font, target, slotX, slotY);
    }

    private void renderRadiusSection(GuiGraphics graphics) {
        ItemStack tool = findRelevantStack();

        int radius = tool.isEmpty()
                ? PortableSpatialReplacer.DEFAULT_RADIUS
                : PortableSpatialReplacer.getRadius(tool);

        int labelCenterX = this.leftPos + TARGET_PANEL_X + TARGET_PANEL_W / 2;
        int labelY = this.topPos + RADIUS_LABEL_Y;

        drawCenteredStringShadow(
                graphics,
                Component.literal("Radius: " + radius),
                labelCenterX,
                labelY,
                0xFFFFFFFF
        );

        int boxX = this.leftPos + RADIUS_VALUE_X;
        int boxY = this.topPos + RADIUS_VALUE_Y;

        graphics.renderOutline(
                boxX,
                boxY,
                RADIUS_VALUE_W,
                RADIUS_VALUE_H,
                0xFF606060
        );

        drawCenteredStringShadow(
                graphics,
                Component.literal(String.valueOf(radius)),
                boxX + RADIUS_VALUE_W / 2,
                boxY + 5,
                0xFFFFFFFF
        );
    }

    private void renderInfoSection(GuiGraphics graphics) {
        int panelX = this.leftPos + INFO_PANEL_X;
        int panelY = this.topPos + INFO_PANEL_Y;
        int cx = panelX + INFO_PANEL_W / 2;

        ItemStack tool = findRelevantStack();
        ItemStack target = getTargetStack();

        if (target.isEmpty()) {
            drawCenteredWrapped(
                    graphics,
                    Component.translatable(LangDefs.REPLACER_NO_TARGET.getTranslationKey())
                            .withStyle(ChatFormatting.RED),
                    cx,
                    panelY + 34,
                    INFO_PANEL_W - 18,
                    0xFFFF7777
            );

            drawCenteredWrapped(
                    graphics,
                    Component.translatable(LangDefs.REPLACER_SELECT_TARGET_HINT.getTranslationKey())
                            .withStyle(ChatFormatting.WHITE),
                    cx,
                    panelY + 62,
                    INFO_PANEL_W - 18,
                    0xFFFFFFFF
            );
        } else {
            graphics.renderItem(target, cx - 8, panelY + 14);
            graphics.renderItemDecorations(this.font, target, cx - 8, panelY + 14);

            drawCenteredWrapped(
                    graphics,
                    target.getHoverName().copy().withStyle(ChatFormatting.AQUA),
                    cx,
                    panelY + 38,
                    INFO_PANEL_W - 18,
                    0xFF55FFFF
            );

            drawCenteredWrapped(
                    graphics,
                    connectivityLabel().copy().withStyle(ChatFormatting.WHITE),
                    cx,
                    panelY + 68,
                    INFO_PANEL_W - 18,
                    0xFFFFFFFF
            );
        }

        graphics.drawCenteredString(
                this.font,
                Component.translatable(
                        LangDefs.REPLACER_CAP_INFO.getTranslationKey(),
                        SpatialConfig.COMMON.PORTABLE_SPATIAL_REPLACER_MAX_BLOCKS.get()
                ),
                cx,
                panelY + 98,
                0xFFEDEDED
        );

        if (IsModLoaded.AE2) {
            renderAe2Status(graphics, tool, cx, panelY + 108);
        }
    }

    private void renderAe2Status(GuiGraphics graphics, ItemStack tool, int cx, int y) {
        if (tool.isEmpty()) {
            return;
        }

        int maxWidth = INFO_PANEL_W - 18;

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
                                LangDefs.REPLACER_LINKED_TO_AE2.getTranslationKey(),
                                coords
                        ).withStyle(ChatFormatting.AQUA),
                        cx,
                        y,
                        maxWidth,
                        0xFF55FFFF
                );
            } else {
                drawCenteredWrapped(
                        graphics,
                        Component.translatable(
                                LangDefs.REPLACER_NOT_LINKED_TO_AE2.getTranslationKey()
                        ).withStyle(ChatFormatting.WHITE),
                        cx,
                        y,
                        maxWidth,
                        0xFFEDEDED
                );
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    public void render(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);

        renderExtraTooltips(graphics, mouseX, mouseY);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(
            GuiGraphics graphics,
            int mouseX,
            int mouseY
    ) {
        graphics.drawString(
                this.font,
                this.title,
                this.titleLabelX,
                this.titleLabelY,
                0xFF111111,
                false
        );

        graphics.drawString(
                this.font,
                this.playerInventoryTitle,
                this.inventoryLabelX,
                this.inventoryLabelY,
                0xFF111111,
                false
        );
    }

    private void renderExtraTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        renderTargetTooltip(graphics, mouseX, mouseY);
        renderRadiusTooltip(graphics, mouseX, mouseY);
        renderConnectivityTooltip(graphics, mouseX, mouseY);
        renderBlockstateTooltip(graphics, mouseX, mouseY);
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
                            LangDefs.REPLACER_SELECT_TARGET_HINT.getTranslationKey()
                    ).withStyle(ChatFormatting.WHITE)),
                    mouseX,
                    mouseY
            );
            return;
        }

        graphics.renderTooltip(
                this.font,
                target.getTooltipLines(
                        Minecraft.getInstance().player,
                        TooltipFlag.Default.NORMAL
                ),
                target.getTooltipImage(),
                mouseX,
                mouseY
        );
    }

    private void renderRadiusTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        boolean overDown = this.radiusDownButton != null
                && this.radiusDownButton.isMouseOver(mouseX, mouseY);

        boolean overUp = this.radiusUpButton != null
                && this.radiusUpButton.isMouseOver(mouseX, mouseY);

        int boxX = this.leftPos + RADIUS_VALUE_X;
        int boxY = this.topPos + RADIUS_VALUE_Y;

        boolean overValue = isMouseInside(
                mouseX,
                mouseY,
                boxX,
                boxY,
                RADIUS_VALUE_W,
                RADIUS_VALUE_H
        );

        if (!overDown && !overUp && !overValue) {
            return;
        }

        ItemStack tool = findRelevantStack();

        int radius = tool.isEmpty()
                ? PortableSpatialReplacer.DEFAULT_RADIUS
                : PortableSpatialReplacer.getRadius(tool);

        graphics.renderComponentTooltip(
                this.font,
                List.of(Component.translatable(
                        LangDefs.REPLACER_RADIUS_LABEL.getTranslationKey(),
                        radius
                ).withStyle(ChatFormatting.WHITE)),
                mouseX,
                mouseY
        );
    }

    private void renderConnectivityTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (this.connectivityButton == null
                || !this.connectivityButton.isMouseOver(mouseX, mouseY)) {
            return;
        }

        ItemStack tool = findRelevantStack();
        ConnectivityMode mode = tool.isEmpty()
                ? ConnectivityMode.DIRECT
                : PortableSpatialReplacer.getConnectivityMode(tool);

        Component desc = Component.translatable(
                mode == ConnectivityMode.DIRECT
                        ? LangDefs.REPLACER_CONNECTIVITY_DIRECT_DESC.getTranslationKey()
                        : LangDefs.REPLACER_CONNECTIVITY_DIAGONAL_DESC.getTranslationKey()
        ).withStyle(ChatFormatting.GRAY);

        graphics.renderComponentTooltip(
                this.font,
                List.of(connectivityLabel().copy().withStyle(ChatFormatting.WHITE), desc),
                mouseX,
                mouseY
        );
    }

    private void renderBlockstateTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (this.blockstateButton == null
                || !this.blockstateButton.isMouseOver(mouseX, mouseY)) {
            return;
        }

        ItemStack tool = findRelevantStack();
        boolean strict = !tool.isEmpty() && PortableSpatialReplacer.isSameBlockstate(tool);

        Component desc = Component.translatable(
                strict
                        ? LangDefs.REPLACER_BLOCKSTATE_ON_DESC.getTranslationKey()
                        : LangDefs.REPLACER_BLOCKSTATE_OFF_DESC.getTranslationKey()
        ).withStyle(ChatFormatting.GRAY);

        graphics.renderComponentTooltip(
                this.font,
                List.of(blockstateLabel().copy().withStyle(ChatFormatting.WHITE), desc),
                mouseX,
                mouseY
        );
    }

    private void drawCenteredWrapped(
            GuiGraphics graphics,
            Component component,
            int centerX,
            int y,
            int maxWidth,
            int color
    ) {
        int yy = y;

        for (var line : this.font.split(component, maxWidth)) {
            graphics.drawCenteredString(this.font, line, centerX, yy, color);
            yy += 10;
        }
    }

    private void drawCenteredStringShadow(
            GuiGraphics graphics,
            Component component,
            int centerX,
            int y,
            int color
    ) {
        graphics.drawString(
                this.font,
                component,
                centerX - this.font.width(component) / 2,
                y,
                color,
                true
        );
    }

    private boolean isMouseInside(
            double mouseX,
            double mouseY,
            int x,
            int y,
            int width,
            int height
    ) {
        return mouseX >= x
                && mouseY >= y
                && mouseX < x + width
                && mouseY < y + height;
    }

    private ItemStack getTargetStack() {
        ItemStack tool = findRelevantStack();

        if (tool.isEmpty()) {
            return ItemStack.EMPTY;
        }

        return PortableSpatialReplacer.getTargetBlock(tool);
    }

    private ItemStack findRelevantStack() {
        ItemStack stack = this.menu.getToolStack();

        if (!stack.isEmpty() && stack.getItem() instanceof PortableSpatialReplacer) {
            return stack;
        }

        var player = Minecraft.getInstance().player;

        if (player == null) {
            return ItemStack.EMPTY;
        }

        ItemStack main = player.getMainHandItem();

        if (!main.isEmpty() && main.getItem() instanceof PortableSpatialReplacer) {
            return main;
        }

        ItemStack off = player.getOffhandItem();

        if (!off.isEmpty() && off.getItem() instanceof PortableSpatialReplacer) {
            return off;
        }

        return ItemStack.EMPTY;
    }

    private Component shortConnectivityLabel() {
        ItemStack tool = findRelevantStack();

        if (tool.isEmpty()) {
            return Component.literal("-");
        }

        ConnectivityMode mode = PortableSpatialReplacer.getConnectivityMode(tool);

        return Component.translatable(
                mode == ConnectivityMode.DIRECT
                        ? LangDefs.REPLACER_CONNECTIVITY_DIRECT.getTranslationKey()
                        : LangDefs.REPLACER_CONNECTIVITY_DIAGONAL.getTranslationKey()
        );
    }

    private Component connectivityLabel() {
        ItemStack tool = findRelevantStack();

        if (tool.isEmpty()) {
            return Component.literal("-");
        }

        ConnectivityMode mode = PortableSpatialReplacer.getConnectivityMode(tool);

        return Component.translatable(
                mode == ConnectivityMode.DIRECT
                        ? LangDefs.REPLACER_CONNECTIVITY_DIRECT.getTranslationKey()
                        : LangDefs.REPLACER_CONNECTIVITY_DIAGONAL.getTranslationKey()
        );
    }

    private Component blockstateLabel() {
        ItemStack tool = findRelevantStack();

        if (tool.isEmpty()) {
            return Component.literal("-");
        }

        return Component.translatable(
                PortableSpatialReplacer.isSameBlockstate(tool)
                        ? LangDefs.REPLACER_BLOCKSTATE_ON.getTranslationKey()
                        : LangDefs.REPLACER_BLOCKSTATE_OFF.getTranslationKey()
        );
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