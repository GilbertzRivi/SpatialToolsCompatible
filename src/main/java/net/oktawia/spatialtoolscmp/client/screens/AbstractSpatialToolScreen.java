package net.oktawia.spatialtoolscmp.client.screens;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import net.oktawia.spatialtoolscmp.IsModLoaded;
import net.oktawia.spatialtoolscmp.client.misc.Icon;
import net.oktawia.spatialtoolscmp.client.misc.widgets.IconButtonWidget;
import net.oktawia.spatialtoolscmp.client.misc.widgets.PowerUpgradePanelWidget;
import net.oktawia.spatialtoolscmp.client.misc.widgets.ToolModeDropdownWidget;
import net.oktawia.spatialtoolscmp.compat.ae2.AE2Compat;
import net.oktawia.spatialtoolscmp.defs.LangDefs;
import net.oktawia.spatialtoolscmp.items.AbstractStructureCaptureToolItem;
import net.oktawia.spatialtoolscmp.menus.AbstractPortableStructureToolMenu;

public abstract class AbstractSpatialToolScreen<M extends AbstractPortableStructureToolMenu>
        extends AbstractContainerScreen<M> {

    protected static final int TOOL_MODE_DROPDOWN_GAP = 4;

    private static final int GUIDE_BUTTON_SIZE = 16;
    private static final int GUIDE_BUTTON_INSET = 4;

    protected final PowerUpgradePanelWidget powerUpgradePanel = new PowerUpgradePanelWidget();
    protected final ToolModeDropdownWidget toolModeDropdown = new ToolModeDropdownWidget();

    protected List<Component> compatibleUpgradeTooltip = buildCompatibleUpgradesTooltip();
    protected List<Component> compatibleCraftingUpgradeTooltip = buildCompatibleCraftingUpgradeTooltip();

    protected AbstractSpatialToolScreen(M menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title);
    }

    @Override
    protected void init() {
        super.init();

        int guideButtonY = this.topPos - GUIDE_BUTTON_SIZE - TOOL_MODE_DROPDOWN_GAP;
        boolean fitsAbovePanel = guideButtonY >= 0;

        IconButtonWidget guideButton = new IconButtonWidget(
                this.leftPos + this.imageWidth - GUIDE_BUTTON_SIZE - (fitsAbovePanel ? 0 : GUIDE_BUTTON_INSET),
                fitsAbovePanel ? guideButtonY : this.topPos + GUIDE_BUTTON_INSET,
                GUIDE_BUTTON_SIZE,
                GUIDE_BUTTON_SIZE,
                Icon.QUESTION,
                () -> Minecraft.getInstance().setScreen(new GuideScreen(this, getMenu().getItemStack())));

        guideButton.setTooltip(Tooltip.create(Component.translatable(LangDefs.GUIDE_BUTTON.getTranslationKey())));

        addRenderableWidget(guideButton);
    }

    protected void renderToolModeDropdown(GuiGraphics graphics, int mouseX, int mouseY) {
        layoutToolModeDropdown();
        this.toolModeDropdown.render(graphics, this.font, mouseX, mouseY);
    }

    protected void layoutToolModeDropdown() {
        this.toolModeDropdown.setToolStack(getMenu().getItemStack());
        this.toolModeDropdown.setPosition(
                this.leftPos,
                this.topPos - ToolModeDropdownWidget.ROW_HEIGHT - TOOL_MODE_DROPDOWN_GAP);
    }

    protected boolean toolModeDropdownClicked(double mouseX, double mouseY, int button) {
        if (button != 0) {
            return false;
        }

        layoutToolModeDropdown();
        return this.toolModeDropdown.mouseClicked(mouseX, mouseY);
    }

    public Rect2i getToolModeDropdownArea() {
        layoutToolModeDropdown();
        return this.toolModeDropdown.getBounds();
    }

    public boolean hasToolModeDropdown() {
        layoutToolModeDropdown();
        return this.toolModeDropdown.isVisible();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (toolModeDropdownClicked(mouseX, mouseY, button)) {
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    protected void renderPowerUpgradePanel(GuiGraphics graphics) {
        this.powerUpgradePanel.setScreenPosition(this.leftPos, this.topPos);
        this.powerUpgradePanel.setSlots(
                getMenu().getPowerUpgradeSlotCount(),
                getMenu().getMaxUpgradesCount());
        this.powerUpgradePanel.renderBackground(graphics);
        this.powerUpgradePanel.renderCraftingSlotBadge(
                graphics,
                getMenu().hasCraftingUpgradeInstalled());
    }

    protected void renderPowerUpgradeTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (getMenu().getPowerUpgradeSlotCount() <= 0) {
            return;
        }

        this.powerUpgradePanel.setScreenPosition(this.leftPos, this.topPos);
        this.powerUpgradePanel.setSlots(
                getMenu().getPowerUpgradeSlotCount(),
                getMenu().getMaxUpgradesCount());

        boolean overPanel = this.powerUpgradePanel.isMouseOver(mouseX, mouseY);
        boolean overPowerSlot = getMenu().isPowerUpgradeSlot(this.hoveredSlot);
        boolean overCraftingSlot = getMenu().isCraftingUpgradeSlot(this.hoveredSlot);

        if (!overPanel && !overPowerSlot && !overCraftingSlot) {
            return;
        }

        if (this.hoveredSlot != null && !overPowerSlot && !overCraftingSlot) {
            return;
        }

        if (this.hoveredSlot != null && this.hoveredSlot.hasItem()) {
            return;
        }

        List<Component> lines = overCraftingSlot
                ? this.compatibleCraftingUpgradeTooltip
                : this.compatibleUpgradeTooltip;

        if (lines.isEmpty()) {
            return;
        }

        graphics.renderComponentTooltip(
                this.font,
                lines,
                mouseX,
                mouseY);
    }

    static List<Component> buildCompatibleUpgradesTooltip() {
        List<Component> lines = new ArrayList<>();

        lines.add(Component.translatable(
                LangDefs.VALID_UPGRADES.getTranslationKey()).withStyle(ChatFormatting.WHITE));

        Map<String, List<ItemStack>> stacksByMod = new LinkedHashMap<>();

        for (ItemStack stack : AbstractStructureCaptureToolItem.getConfiguredPowerUpgradeItemStacks()) {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());

            if (id == null) {
                continue;
            }

            stacksByMod
                    .computeIfAbsent(id.getNamespace(), ignored -> new ArrayList<>())
                    .add(stack);
        }

        if (stacksByMod.isEmpty()) {
            lines.add(Component.literal("No valid energy upgrade items configured")
                    .withStyle(ChatFormatting.RED));

            lines.add(Component.literal("Edit: features.energyUpgrades.items")
                    .withStyle(ChatFormatting.GRAY));

            return lines;
        }

        for (Map.Entry<String, List<ItemStack>> entry : stacksByMod.entrySet()) {
            lines.add(modDisplayName(entry.getKey()).copy().withStyle(ChatFormatting.WHITE));

            for (ItemStack stack : entry.getValue()) {
                lines.add(stack.getHoverName().copy().withStyle(ChatFormatting.GRAY));
            }
        }

        return lines;
    }

    static List<Component> buildCompatibleCraftingUpgradeTooltip() {
        List<Component> lines = new ArrayList<>();

        lines.add(Component.translatable(
                LangDefs.CRAFTING_UPGRADE_SLOT_HINT.getTranslationKey()).withStyle(ChatFormatting.YELLOW));

        lines.add(Component.translatable(
                LangDefs.VALID_UPGRADES.getTranslationKey()).withStyle(ChatFormatting.WHITE));

        if (IsModLoaded.AE2) {
            lines.add(AE2Compat.modDisplayName().copy());
            lines.add(AE2Compat.craftingUpgradeDisplayName().copy().withStyle(ChatFormatting.GRAY));
        } else {
            lines.add(Component.translatable(
                    LangDefs.AE2_NOT_INSTALLED.getTranslationKey()).withStyle(ChatFormatting.RED));

            lines.add(Component.translatable(
                    LangDefs.NOT_AVAILABLE.getTranslationKey()).withStyle(ChatFormatting.GRAY));
        }

        return lines;
    }

    private static Component modDisplayName(String modId) {
        return ModList.get()
                .getModContainerById(modId)
                .map(container -> Component.literal(container.getModInfo().getDisplayName()))
                .orElse(Component.literal(modId));
    }
}
