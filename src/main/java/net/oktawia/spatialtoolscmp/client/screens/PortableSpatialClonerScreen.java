package net.oktawia.spatialtoolscmp.client.screens;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.registries.ForgeRegistries;
import net.oktawia.spatialtoolscmp.IsModLoaded;
import net.oktawia.spatialtoolscmp.client.misc.CraftingBufferStatusClientCache;
import net.oktawia.spatialtoolscmp.client.misc.Icon;
import net.oktawia.spatialtoolscmp.client.misc.PortableSpatialClonerRequirementSync;
import net.oktawia.spatialtoolscmp.client.misc.widgets.ClonerMaterialListWidget;
import net.oktawia.spatialtoolscmp.client.misc.widgets.IconButtonWidget;
import net.oktawia.spatialtoolscmp.client.misc.widgets.SearchableClonerStructureDropdownWidget;
import net.oktawia.spatialtoolscmp.compat.ae2.AE2GridLinkableHandler;
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

    private static final int NESTED_MODE_BUTTON_LEFT = 154;
    private static final int NESTED_MODE_BUTTON_TOP = 6;
    private static final int NESTED_MODE_BUTTON_SIZE = 16;

    private static final int CRAFT_ALL_BUTTON_LEFT = 134;
    private static final int CRAFT_ALL_BUTTON_TOP = 6;
    private static final int CRAFT_ALL_BUTTON_SIZE = 16;

    private static final int NESTED_MODE_TOOLTIP_MAX_CHARS = 28;

    private ClonerMaterialListWidget materialList;
    private SearchableClonerStructureDropdownWidget structureSelector;
    private IconButtonWidget nestedInventoryModeButton;
    private IconButtonWidget craftAllButton;

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

        this.nestedInventoryModeButton = new IconButtonWidget(
                this.leftPos + NESTED_MODE_BUTTON_LEFT,
                this.topPos + NESTED_MODE_BUTTON_TOP,
                NESTED_MODE_BUTTON_SIZE,
                NESTED_MODE_BUTTON_SIZE,
                iconForNestedInventoryMode(getNestedInventoryMode()),
                button -> getMenu().cycleNestedInventoryMode()
        );

        this.addRenderableWidget(this.nestedInventoryModeButton);

        this.craftAllButton = new IconButtonWidget(
                this.leftPos + CRAFT_ALL_BUTTON_LEFT,
                this.topPos + CRAFT_ALL_BUTTON_TOP,
                CRAFT_ALL_BUTTON_SIZE,
                CRAFT_ALL_BUTTON_SIZE,
                Icon.CRAFT_HAMMER,
                button -> getMenu().craftAll()
        );
        this.craftAllButton.visible = false;
        this.addRenderableWidget(this.craftAllButton);

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

        if (this.nestedInventoryModeButton != null) {
            this.nestedInventoryModeButton.setPosition(
                    left + NESTED_MODE_BUTTON_LEFT,
                    top + NESTED_MODE_BUTTON_TOP
            );
            this.nestedInventoryModeButton.resize(
                    NESTED_MODE_BUTTON_SIZE,
                    NESTED_MODE_BUTTON_SIZE
            );
        }

        if (this.craftAllButton != null) {
            this.craftAllButton.setPosition(
                    left + CRAFT_ALL_BUTTON_LEFT,
                    top + CRAFT_ALL_BUTTON_TOP
            );
            this.craftAllButton.resize(CRAFT_ALL_BUTTON_SIZE, CRAFT_ALL_BUTTON_SIZE);
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
        CraftingBufferStatusClientCache.clear(getMenu().containerId);
    }

    @Override
    protected void containerTick() {
        super.containerTick();

        if (this.structureSelector != null) {
            this.structureSelector.refreshFromClientCache();
        }

        if (this.nestedInventoryModeButton != null) {
            this.nestedInventoryModeButton.setIcon(iconForNestedInventoryMode(getNestedInventoryMode()));
        }

        syncRequirementEntries();
        updateCraftAllButton();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (toolModeDropdownClicked(mouseX, mouseY, button)) {
            return true;
        }

        if (this.nestedInventoryModeButton != null && this.nestedInventoryModeButton.mouseClicked(mouseX, mouseY, button)) {
            this.setFocused(this.nestedInventoryModeButton);
            return true;
        }

        boolean dropdownConsumesClick = this.structureSelector != null
                && this.structureSelector.isExpandedMouseOver(mouseX, mouseY);

        if (!dropdownConsumesClick && this.materialList != null && this.materialList.mouseClicked(mouseX, mouseY, button)) {
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

        renderNestedInventoryModeTooltip(graphics, mouseX, mouseY);
        renderCraftAllHighlight(graphics);
        renderCraftAllTooltip(graphics, mouseX, mouseY);

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
        return !PortableSpatialCloner.hasItemHandlerLink(findRelevantStack())
                && IsModLoaded.AE2
                && getMenu().hasCraftingUpgradeInstalled();
    }

    private PortableSpatialCloner.NestedInventoryResourceMode getNestedInventoryMode() {
        return PortableSpatialCloner.getNestedInventoryResourceMode(findRelevantStack());
    }

    private static Icon iconForNestedInventoryMode(PortableSpatialCloner.NestedInventoryResourceMode mode) {
        return switch (mode) {
            case NONE -> Icon.CROSS;
            case PLAYER -> Icon.PLAYER_INV;
            case CONNECTED -> Icon.EXTERNAL_INV;
            case BOTH -> Icon.CHECK;
        };
    }

    private List<Component> tooltipForNestedInventoryMode(PortableSpatialCloner.NestedInventoryResourceMode mode) {
        List<Component> lines = new ArrayList<>();

        Component mainTooltip = switch (mode) {
            case NONE -> Component.translatable(
                    LangDefs.PORTABLE_SPATIAL_CLONER_NESTED_MODE_NONE_TOOLTIP.getTranslationKey()
            );
            case PLAYER -> Component.translatable(
                    LangDefs.PORTABLE_SPATIAL_CLONER_NESTED_MODE_PLAYER_TOOLTIP.getTranslationKey()
            );
            case CONNECTED -> Component.translatable(
                    LangDefs.PORTABLE_SPATIAL_CLONER_NESTED_MODE_CONNECTED_TOOLTIP.getTranslationKey()
            );
            case BOTH -> Component.translatable(
                    LangDefs.PORTABLE_SPATIAL_CLONER_NESTED_MODE_BOTH_TOOLTIP.getTranslationKey()
            );
        };

        addWrappedTooltipLines(lines, mainTooltip);

        if (mode.useConnectedNested() && isLinkedToAe2()) {
            addWrappedTooltipLines(
                    lines,
                    Component.translatable(
                            LangDefs.PORTABLE_SPATIAL_CLONER_NESTED_MODE_AE2_IGNORED_TOOLTIP.getTranslationKey()
                    ).withStyle(ChatFormatting.DARK_GRAY)
            );
        }

        return lines;
    }

    private boolean isLinkedToAe2() {
        if (!IsModLoaded.AE2) {
            return false;
        }

        ItemStack stack = findRelevantStack();

        if (stack.isEmpty() || PortableSpatialCloner.hasItemHandlerLink(stack)) {
            return false;
        }

        try {
            return AE2GridLinkableHandler.getLinkedPos(stack) != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void renderNestedInventoryModeTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (this.nestedInventoryModeButton == null || !this.nestedInventoryModeButton.isMouseOver(mouseX, mouseY)) {
            return;
        }

        graphics.renderComponentTooltip(
                Minecraft.getInstance().font,
                tooltipForNestedInventoryMode(getNestedInventoryMode()),
                mouseX,
                mouseY
        );
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

    private void updateCraftAllButton() {
        if (this.craftAllButton == null) return;

        int status = CraftingBufferStatusClientCache.get(getMenu().containerId);
        boolean noBuffer = status == CraftingBufferStatusClientCache.NO_BUFFER;
        boolean show = hasCraftingUpgrade() && status != CraftingBufferStatusClientCache.UNKNOWN;

        this.craftAllButton.visible = show;

        boolean hasCraftableMissing = PortableSpatialClonerRequirementSync.getEntries(getMenu().containerId)
                .stream()
                .anyMatch(e -> e.craftable() && e.available() < e.required());

        this.craftAllButton.active = show
                && status == CraftingBufferStatusClientCache.AVAILABLE
                && hasCraftableMissing;

        this.craftAllButton.setDarkBackground(noBuffer);

        this.craftAllButton.setIcon(
                status == CraftingBufferStatusClientCache.CRAFTING_SCHEDULED
                        ? Icon.CRAFT_HAMMER_DARK
                        : Icon.CRAFT_HAMMER
        );
    }

    private void renderCraftAllHighlight(GuiGraphics graphics) {
        if (this.craftAllButton == null || !this.craftAllButton.visible) return;

        int status = CraftingBufferStatusClientCache.get(getMenu().containerId);

        int color;
        if (status == CraftingBufferStatusClientCache.CRAFTING_SCHEDULED) {
            color = 0xFF44EE44;
        } else if (status == CraftingBufferStatusClientCache.NO_BUFFER) {
            color = 0xFFEEAA22;
        } else {
            return;
        }

        int x = this.craftAllButton.getX();
        int y = this.craftAllButton.getY();
        int w = this.craftAllButton.getWidth();
        int h = this.craftAllButton.getHeight();

        graphics.fill(x - 2, y - 2, x + w + 2, y, color);
        graphics.fill(x - 2, y + h, x + w + 2, y + h + 2, color);
        graphics.fill(x - 2, y, x, y + h, color);
        graphics.fill(x + w, y, x + w + 2, y + h, color);
    }

    private void renderCraftAllTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (this.craftAllButton == null || !this.craftAllButton.visible) return;
        if (mouseX < this.craftAllButton.getX() || mouseX >= this.craftAllButton.getX() + this.craftAllButton.getWidth()
                || mouseY < this.craftAllButton.getY() || mouseY >= this.craftAllButton.getY() + this.craftAllButton.getHeight()) return;

        int status = CraftingBufferStatusClientCache.get(getMenu().containerId);
        List<Component> lines = new ArrayList<>();

        if (status == CraftingBufferStatusClientCache.NO_BUFFER) {
            addWrappedTooltipLines(
                    lines,
                    Component.translatable(LangDefs.CRAFT_ALL_NEEDS_BUFFER_TITLE.getTranslationKey())
                            .withStyle(ChatFormatting.YELLOW)
            );
            addWrappedTooltipLines(
                    lines,
                    Component.translatable(LangDefs.CRAFT_ALL_NEEDS_BUFFER_HINT.getTranslationKey())
                            .withStyle(ChatFormatting.GRAY)
            );

            graphics.renderComponentTooltip(Minecraft.getInstance().font, lines, mouseX, mouseY);
            return;
        }

        boolean hasCraftableMissing = PortableSpatialClonerRequirementSync.getEntries(getMenu().containerId)
                .stream()
                .anyMatch(e -> e.craftable() && e.available() < e.required());

        LangDefs key;
        if (status == CraftingBufferStatusClientCache.CRAFTING_SCHEDULED) {
            key = LangDefs.CRAFT_ALL_SCHEDULED;
        } else if (status == CraftingBufferStatusClientCache.ALL_BUSY) {
            key = LangDefs.CRAFT_ALL_ALL_BUSY;
        } else if (!hasCraftableMissing) {
            key = LangDefs.CRAFT_ALL_NOTHING_TO_CRAFT;
        } else {
            key = LangDefs.CRAFT_ALL_TOOLTIP;
        }

        addWrappedTooltipLines(lines, Component.translatable(key.getTranslationKey()));

        graphics.renderComponentTooltip(Minecraft.getInstance().font, lines, mouseX, mouseY);
    }

    private static void addWrappedTooltipLines(List<Component> lines, Component component) {
        addWrappedTooltipLines(lines, component, NESTED_MODE_TOOLTIP_MAX_CHARS);
    }

    private static void addWrappedTooltipLines(List<Component> lines, Component component, int maxChars) {
        if (component == null) {
            return;
        }

        String text = component.getString().trim();

        if (text.isBlank()) {
            return;
        }

        int max = Math.max(8, maxChars);
        String remaining = text;

        while (remaining.length() > max) {
            int split = remaining.lastIndexOf(' ', max);

            if (split <= 0) {
                split = remaining.indexOf(' ', max);

                if (split <= 0) {
                    break;
                }
            }

            String line = remaining.substring(0, split).trim();

            if (!line.isBlank()) {
                lines.add(Component.literal(line).withStyle(component.getStyle()));
            }

            remaining = remaining.substring(split + 1).trim();
        }

        if (!remaining.isBlank()) {
            lines.add(Component.literal(remaining).withStyle(component.getStyle()));
        }
    }
}