package net.oktawia.spatialtoolscmp.client.screens;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.registries.ForgeRegistries;
import net.oktawia.spatialtoolscmp.IsModLoaded;
import net.oktawia.spatialtoolscmp.client.misc.PortableSpatialClonerRequirementSync;
import net.oktawia.spatialtoolscmp.client.misc.widgets.ClonerMaterialListWidget;
import net.oktawia.spatialtoolscmp.client.misc.widgets.SearchableClonerStructureDropdownWidget;
import net.oktawia.spatialtoolscmp.defs.LangDefs;
import net.oktawia.spatialtoolscmp.items.PortableSpatialCloner;
import net.oktawia.spatialtoolscmp.logic.StructureToolStackState;
import net.oktawia.spatialtoolscmp.menus.PortableSpatialClonerMenu;
import net.oktawia.spatialtoolscmp.network.NetworkHandler;
import net.oktawia.spatialtoolscmp.network.packets.RequestClonerLibraryPacket;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class PortableSpatialClonerScreen
        extends AbstractPortableStructureToolScreen<PortableSpatialClonerMenu> {

    private static final int PREVIEW_LEFT = 104;
    private static final int PREVIEW_TOP = 26;
    private static final int PREVIEW_WIDTH = 144;
    private static final int PREVIEW_HEIGHT = 135;

    private static final int STRUCTURE_SELECTOR_LEFT = 8;
    private static final int STRUCTURE_SELECTOR_TOP = 26;
    private static final int STRUCTURE_SELECTOR_WIDTH = 92;
    private static final int STRUCTURE_SELECTOR_HEIGHT = 31;

    private static final int MATERIAL_LIST_LEFT = 8;
    private static final int MATERIAL_LIST_TOP = 60;
    private static final int MATERIAL_LIST_WIDTH = 92;
    private static final int MATERIAL_LIST_HEIGHT = 101;

    private ClonerMaterialListWidget materialList;
    private SearchableClonerStructureDropdownWidget structureSelector;

    private boolean requestedLibrary = false;

    public PortableSpatialClonerScreen(
            PortableSpatialClonerMenu menu,
            Inventory playerInventory,
            Component title
    ) {
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

        this.materialList = new ClonerMaterialListWidget(
                this.leftPos + MATERIAL_LIST_LEFT,
                this.topPos + MATERIAL_LIST_TOP,
                MATERIAL_LIST_WIDTH,
                MATERIAL_LIST_HEIGHT
        );

        this.materialList.setCraftRequestHandler(entry -> {
            ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(entry.stack().getItem());

            if (itemId == null) {
                return;
            }

            long missing = entry.missing();

            if (missing <= 0) {
                return;
            }

            getMenu().craftRequest(itemId, missing);
        });

        this.addRenderableWidget(this.materialList);

        this.structureSelector = new SearchableClonerStructureDropdownWidget(
                this.leftPos + STRUCTURE_SELECTOR_LEFT,
                this.topPos + STRUCTURE_SELECTOR_TOP,
                STRUCTURE_SELECTOR_WIDTH,
                STRUCTURE_SELECTOR_HEIGHT,
                () -> getMenu().containerId
        );

        this.addRenderableWidget(this.structureSelector);

        layoutWidgets();

        finishInit();

        if (!this.requestedLibrary) {
            this.requestedLibrary = true;
            NetworkHandler.sendToServer(new RequestClonerLibraryPacket(getMenu().containerId));
        }
    }

    private void layoutWidgets() {
        int left = this.leftPos;
        int top = this.topPos;

        if (this.structureSelector != null) {
            this.structureSelector.move(
                    left + STRUCTURE_SELECTOR_LEFT,
                    top + STRUCTURE_SELECTOR_TOP
            );
            this.structureSelector.resize(
                    STRUCTURE_SELECTOR_WIDTH,
                    STRUCTURE_SELECTOR_HEIGHT
            );
        }

        if (this.materialList != null) {
            this.materialList.move(
                    left + MATERIAL_LIST_LEFT,
                    top + MATERIAL_LIST_TOP
            );
            this.materialList.resize(
                    MATERIAL_LIST_WIDTH,
                    MATERIAL_LIST_HEIGHT
            );
        }
    }

    @Override
    protected void renderBg(
            GuiGraphics graphics,
            float partialTick,
            int mouseX,
            int mouseY
    ) {
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
                256
        );

        if (renderEmptyPreviewBehindWidgets()) {
            renderEmptyPreviewMessage(graphics);
        }
    }

    @Override
    protected boolean renderEmptyPreviewBehindWidgets() {
        return this.structureSelector != null && this.structureSelector.isOpen();
    }

    @Override
    protected PreviewRect getPreviewRect() {
        return new PreviewRect(
                this.leftPos + PREVIEW_LEFT,
                this.topPos + PREVIEW_TOP,
                PREVIEW_WIDTH,
                PREVIEW_HEIGHT
        );
    }

    @Override
    protected ItemStack findRelevantStack() {
        var player = Minecraft.getInstance().player;

        if (player == null) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = PortableSpatialCloner.findActive(player);

        if (stack.isEmpty()) {
            stack = PortableSpatialCloner.findHeld(player);
        }

        return stack;
    }

    @Override
    protected void onClearExtraState() {
        if (this.materialList != null) {
            this.materialList.setEntries(List.of());
        }
    }

    @Override
    public void removed() {
        super.removed();
        PortableSpatialClonerRequirementSync.clear(getMenu().containerId);
    }

    @Override
    protected void containerTick() {
        super.containerTick();

        if (this.structureSelector != null) {
            this.structureSelector.refreshFromClientCache();
        }

        syncRequirementEntries();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.materialList != null && this.materialList.mouseClicked(mouseX, mouseY, button)) {
            this.setFocused(this.materialList);
            return true;
        }

        if (this.structureSelector != null && this.structureSelector.mouseClicked(mouseX, mouseY, button)) {
            this.setFocused(this.structureSelector);
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (this.structureSelector != null && this.structureSelector.mouseScrolled(mouseX, mouseY, delta)) {
            return true;
        }

        if (this.materialList != null && this.materialList.mouseScrolled(mouseX, mouseY, delta)) {
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.structureSelector != null && this.structureSelector.wantsKeyboardCapture()) {
            return this.structureSelector.keyPressed(keyCode, scanCode, modifiers);
        }

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.onClose();
            return true;
        }

        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (this.structureSelector != null && this.structureSelector.wantsKeyboardCapture()) {
            return this.structureSelector.charTyped(codePoint, modifiers);
        }

        return super.charTyped(codePoint, modifiers);
    }

    @Override
    protected void renderExtraOverlays(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        if (this.structureSelector == null || !this.structureSelector.isExpandedMouseOver(mouseX, mouseY)) {
            renderMaterialTooltip(graphics, mouseX, mouseY);
        }

        if (this.structureSelector != null) {
            this.structureSelector.renderDropdownOverlay(graphics, mouseX, mouseY, partialTick);

            Component tooltip = this.structureSelector.getHoveredTooltip(mouseX, mouseY);

            if (tooltip != null) {
                graphics.renderTooltip(
                        Minecraft.getInstance().font,
                        tooltip,
                        mouseX,
                        mouseY
                );
            }
        }
    }

    private void syncRequirementEntries() {
        if (this.materialList == null) {
            return;
        }

        this.materialList.setCraftButtonsEnabled(hasCraftingUpgrade());
        this.materialList.setEntries(
                PortableSpatialClonerRequirementSync.getEntries(getMenu().containerId)
        );
    }

    protected boolean hasCraftingUpgrade() {
        return IsModLoaded.AE2 && getMenu().hasCraftingUpgradeInstalled();
    }

    private void renderMaterialTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (this.materialList == null) {
            return;
        }

        if (this.materialList.isHoveringCraftButton(mouseX, mouseY)) {
            return;
        }

        ClonerMaterialListWidget.MaterialEntry hovered = this.materialList.getHoveredEntry(mouseX, mouseY);

        if (hovered == null) {
            return;
        }

        List<Component> lines = new ArrayList<>(
                hovered.stack().getTooltipLines(Minecraft.getInstance().player, TooltipFlag.Default.NORMAL)
        );

        lines.add(Component.translatable(
                LangDefs.AVAILABLE_COUNT.getTranslationKey(),
                String.format("%,d", hovered.available())
        ));

        lines.add(Component.translatable(
                LangDefs.REQUIRED_COUNT.getTranslationKey(),
                String.format("%,d", hovered.required())
        ));

        lines.add(Component.translatable(
                hovered.complete()
                        ? LangDefs.STATUS_OK.getTranslationKey()
                        : LangDefs.STATUS_MISSING.getTranslationKey()
        ));

        graphics.renderTooltip(
                Minecraft.getInstance().font,
                lines,
                hovered.stack().getTooltipImage(),
                mouseX,
                mouseY
        );
    }

    @Override
    protected Component getEmptyPreviewTitle(ItemStack stack) {
        if (!stack.isEmpty() && !StructureToolStackState.getStructureId(stack).isBlank()) {
            return Component.translatable(LangDefs.PREVIEW_EMPTY_LOADING.getTranslationKey());
        }

        return Component.translatable(LangDefs.PREVIEW_EMPTY_NO_SELECTION.getTranslationKey());
    }

    @Override
    protected Component getEmptyPreviewHint(ItemStack stack) {
        if (!stack.isEmpty() && !StructureToolStackState.getStructureId(stack).isBlank()) {
            return Component.translatable(LangDefs.PREVIEW_EMPTY_SYNC_HINT.getTranslationKey());
        }

        return Component.translatable(LangDefs.PREVIEW_EMPTY_SELECT_HINT.getTranslationKey());
    }
}