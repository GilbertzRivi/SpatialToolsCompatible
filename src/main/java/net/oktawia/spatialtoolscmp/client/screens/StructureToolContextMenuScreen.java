package net.oktawia.spatialtoolscmp.client.screens;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;

import net.oktawia.spatialtoolscmp.IsModLoaded;
import net.oktawia.spatialtoolscmp.client.misc.Icon;
import net.oktawia.spatialtoolscmp.client.misc.StructureToolContextMenuClient;
import net.oktawia.spatialtoolscmp.client.misc.widgets.ToolModeDropdownWidget;
import net.oktawia.spatialtoolscmp.defs.LangDefs;
import net.oktawia.spatialtoolscmp.items.PortableSpatialCloner;
import net.oktawia.spatialtoolscmp.items.PortableSpatialPiper;
import net.oktawia.spatialtoolscmp.items.PortableSpatialReplacer;
import net.oktawia.spatialtoolscmp.items.helpers.SpatialMultiTool;
import net.oktawia.spatialtoolscmp.logic.ReplacerContext.ConnectivityMode;
import net.oktawia.spatialtoolscmp.logic.StructureToolStackState;
import net.oktawia.spatialtoolscmp.logic.extensions.GTCEuPiperExtension;
import net.oktawia.spatialtoolscmp.network.NetworkHandler;
import net.oktawia.spatialtoolscmp.network.packets.StructureToolContextActionPacket;

public class StructureToolContextMenuScreen extends Screen {

    private static final int PANEL_WIDTH = 124;
    private static final int OPTIONS_PANEL_WIDTH = 154;

    private static final int PANEL_PADDING = 8;
    private static final int PANEL_GAP_X = 44;
    private static final int PANEL_GAP_Y = 12;

    private static final int BUTTON_SIZE = 24;
    private static final int BUTTON_GAP = 6;
    private static final int BUTTON_STEP = BUTTON_SIZE + BUTTON_GAP;

    private static final int MOVE_PANEL_HEIGHT = 154;
    private static final int SELECTION_PANEL_HEIGHT = 184;
    private static final int TRANSFORM_PANEL_HEIGHT = 92;
    private static final int OPTIONS_PANEL_HEIGHT = 62;

    private static final int MOVE_GRID_WIDTH = BUTTON_SIZE * 3 + BUTTON_GAP * 2;

    private static final int TOOLTIP_MAX_CHARS = 30;

    private static final int PANEL_BG = 0xA8111111;
    private static final int PANEL_BORDER = 0xAAE0E0E0;
    private static final int PANEL_TITLE = 0xFFE0E0E0;

    private static final int BUTTON_BG = 0xAA333333;
    private static final int BUTTON_BG_HOVER = 0xCC44AA44;
    private static final int BUTTON_BG_DISABLED = 0x88333333;

    private static final int BUTTON_BORDER = 0xFFE0E0E0;
    private static final int BUTTON_BORDER_HOVER = 0xFF55FF55;
    private static final int BUTTON_BORDER_SELECTED = 0xFFFFDD55;
    private static final int BUTTON_BORDER_DISABLED = 0xFF777777;

    private static final int TEXT_NORMAL = 0xFFE0E0E0;
    private static final int TEXT_DIM = 0xFFAAAAAA;
    private static final int TEXT_GREEN = 0xFF55FF55;

    private static final int TOOL_DROPDOWN_TOP_OFFSET = 15;

    private static final int LOCAL_SELECT_RED = -1;
    private static final int LOCAL_SELECT_GREEN = -2;

    private static final int PIPER_PANEL_WIDTH = 150;
    private static final int PIPER_PANEL_HEIGHT = 62;

    private final List<ContextPanel> panels = new ArrayList<>();
    private final List<ContextButton> buttons = new ArrayList<>();

    private boolean selectedGreenCorner = false;

    private boolean lastShiftDown = false;
    private boolean lastHoldingCloner = false;
    private boolean lastHasStructure = false;
    private boolean lastHasSelectionA = false;
    private boolean lastHasSelectionB = false;
    private boolean lastAnchorEnabled = false;
    private boolean lastHoldingReplacer = false;
    private boolean lastHoldingPiper = false;
    private PortableSpatialPiper.FillMode lastPiperFillMode = PortableSpatialPiper.FillMode.PATH;
    private boolean lastPiperHasTarget = false;
    private PortableSpatialPiper.PipeDirectionMode lastPiperPipeDirection = PortableSpatialPiper.PipeDirectionMode.OFF;
    private boolean lastPiperTargetIsDirectionalPipe = false;
    private int lastReplacerRadius = PortableSpatialReplacer.DEFAULT_RADIUS;
    private ConnectivityMode lastReplacerConnectivity = ConnectivityMode.DIRECT;
    private boolean lastReplacerBlockstateMode = false;

    private PortableSpatialCloner.NestedInventoryResourceMode lastNestedMode = PortableSpatialCloner.NestedInventoryResourceMode.NONE;

    private StructureToolStackState.SelectionMode lastSelectionMode = StructureToolStackState.SelectionMode.DEFAULT;

    private int topInfoY = 0;

    private boolean lastMultiTool = false;
    private @Nullable SpatialMultiTool.Mode lastMultiToolMode = null;

    private final ToolModeDropdownWidget toolModeDropdown = new ToolModeDropdownWidget();

    public StructureToolContextMenuScreen() {
        super(Component.translatable(LangDefs.CONTEXT_MENU_TITLE.getTranslationKey()));
    }

    @Override
    protected void init() {
        rebuildLayout();
    }

    private void rebuildLayout() {
        this.panels.clear();
        this.buttons.clear();

        ItemStack held = getHeldStack();

        this.lastMultiTool = SpatialMultiTool.isMultiTool(held);
        this.lastMultiToolMode = SpatialMultiTool.getMode(held);

        this.lastShiftDown = isShiftPhysicallyDown();
        this.lastHoldingCloner = isHoldingCloner();
        this.lastHoldingReplacer = !held.isEmpty() && held.getItem() instanceof PortableSpatialReplacer;
        this.lastHoldingPiper = !held.isEmpty() && held.getItem() instanceof PortableSpatialPiper;

        if (this.lastHoldingPiper) {
            this.lastPiperFillMode = PortableSpatialPiper.getFillMode(held);
            this.lastPiperHasTarget = !PortableSpatialPiper.getTargetBlock(held).isEmpty();
            this.lastPiperPipeDirection = PortableSpatialPiper.getPipeDirectionMode(held);
            this.lastPiperTargetIsDirectionalPipe = isDirectionalPipeTarget(held);
        }
        this.lastHasStructure = hasStoredStructure();
        this.lastHasSelectionA = StructureToolStackState.getSelectionA(held) != null;
        this.lastHasSelectionB = StructureToolStackState.getSelectionB(held) != null;
        this.lastAnchorEnabled = StructureToolStackState.isAnchorEnabled(held);
        this.lastNestedMode = getNestedInventoryMode();
        this.lastSelectionMode = getSelectionMode();

        if (this.lastHoldingReplacer) {
            this.lastReplacerRadius = PortableSpatialReplacer.getRadius(held);
            this.lastReplacerConnectivity = PortableSpatialReplacer.getConnectivityMode(held);
            this.lastReplacerBlockstateMode = PortableSpatialReplacer.isSameBlockstate(held);
        }

        if (!this.lastHasSelectionB) {
            this.selectedGreenCorner = false;
        }

        int centerX = this.width / 2;
        int centerY = this.height / 2;

        boolean hasAnySelection = this.lastHasSelectionA || this.lastHasSelectionB;
        int leftPanelHeight = this.lastHasStructure
                ? MOVE_PANEL_HEIGHT
                : hasAnySelection
                        ? SELECTION_PANEL_HEIGHT
                        : MOVE_PANEL_HEIGHT;

        int topPanelHeight = Math.max(leftPanelHeight, TRANSFORM_PANEL_HEIGHT);
        int totalHeight = topPanelHeight + PANEL_GAP_Y + OPTIONS_PANEL_HEIGHT;

        int topY = centerY - totalHeight / 2;
        this.topInfoY = topY - 44;

        if (this.lastMultiToolMode == null) {
            this.topInfoY = centerY
                    - ToolModeDropdownWidget.ROW_HEIGHT * (SpatialMultiTool.MODES.size() + 2) / 2;
            this.toolModeDropdown.setOpen(true);
            return;
        }

        int leftPanelX = centerX - PANEL_WIDTH - PANEL_GAP_X / 2;
        int rightPanelX = centerX + PANEL_GAP_X / 2;

        int leftPanelY = topY;
        int transformPanelY = topY + (topPanelHeight - TRANSFORM_PANEL_HEIGHT) / 2;
        int optionsPanelY = topY + topPanelHeight + PANEL_GAP_Y;

        if (this.lastHoldingReplacer) {
            buildReplacerLayout(centerX, centerY);
            return;
        }

        if (this.lastHoldingPiper) {
            buildPiperLayout(centerX, centerY);
            return;
        }

        if (this.lastHasStructure) {
            addPanel(
                    leftPanelX,
                    leftPanelY,
                    PANEL_WIDTH,
                    MOVE_PANEL_HEIGHT,
                    Component.translatable(LangDefs.CONTEXT_MENU_OFFSET_GROUP.getTranslationKey()),
                    null);

            buildOffsetPanel(leftPanelX, leftPanelY);
        } else if (hasAnySelection) {
            addPanel(
                    leftPanelX,
                    leftPanelY,
                    PANEL_WIDTH,
                    SELECTION_PANEL_HEIGHT,
                    Component.translatable(LangDefs.CONTEXT_MENU_MODIFY_SELECTION_GROUP.getTranslationKey()),
                    null);

            buildSelectionPanel(leftPanelX, leftPanelY);
        } else {
            addPanel(
                    leftPanelX,
                    leftPanelY,
                    PANEL_WIDTH,
                    MOVE_PANEL_HEIGHT,
                    Component.translatable(LangDefs.CONTEXT_MENU_MODIFY_SELECTION_GROUP.getTranslationKey()),
                    Component.translatable(LangDefs.CONTEXT_MENU_SELECT_SOMETHING_FIRST.getTranslationKey()));
        }

        if (this.lastHasStructure) {
            addPanel(
                    rightPanelX,
                    transformPanelY,
                    PANEL_WIDTH,
                    TRANSFORM_PANEL_HEIGHT,
                    Component.translatable(LangDefs.CONTEXT_MENU_TRANSFORM_GROUP.getTranslationKey()),
                    null);

            buildTransformPanel(rightPanelX, transformPanelY);
        } else {
            addPanel(
                    rightPanelX,
                    transformPanelY,
                    PANEL_WIDTH,
                    TRANSFORM_PANEL_HEIGHT,
                    Component.translatable(LangDefs.CONTEXT_MENU_TRANSFORM_GROUP.getTranslationKey()),
                    Component.translatable(LangDefs.CONTEXT_MENU_CUT_COPY_FIRST.getTranslationKey()));
        }

        int optionsPanelX = centerX - OPTIONS_PANEL_WIDTH / 2;

        addPanel(
                optionsPanelX,
                optionsPanelY,
                OPTIONS_PANEL_WIDTH,
                OPTIONS_PANEL_HEIGHT,
                Component.translatable(LangDefs.CONTEXT_MENU_OPTIONS_GROUP.getTranslationKey()),
                null);

        buildOptionsPanel(optionsPanelX, optionsPanelY);
    }

    private static final int REPLACER_PANEL_WIDTH = 180;
    private static final int REPLACER_PANEL_HEIGHT = 104;

    private void buildReplacerLayout(int centerX, int centerY) {
        int panelX = centerX - REPLACER_PANEL_WIDTH / 2;
        int panelY = centerY - REPLACER_PANEL_HEIGHT / 2;

        ConnectivityMode mode = this.lastReplacerConnectivity;
        LangDefs connectLabel = mode == ConnectivityMode.DIRECT
                ? LangDefs.REPLACER_CONNECTIVITY_DIRECT
                : LangDefs.REPLACER_CONNECTIVITY_DIAGONAL;

        Component panelTitle = Component.translatable(LangDefs.CONTEXT_MENU_REPLACER_GROUP.getTranslationKey())
                .append("  R:" + this.lastReplacerRadius + "  ")
                .append(Component.translatable(connectLabel.getTranslationKey()));

        addPanel(panelX, panelY, REPLACER_PANEL_WIDTH, REPLACER_PANEL_HEIGHT, panelTitle, null);

        int midX = panelX + REPLACER_PANEL_WIDTH / 2;
        int leftX = midX - BUTTON_STEP - BUTTON_SIZE / 2;
        int rightX = midX + BUTTON_STEP - BUTTON_SIZE / 2;

        int rowY = panelY + 40;
        addButton(StructureToolContextActionPacket.REPLACER_RADIUS_DOWN, false,
                LangDefs.REPLACER_RADIUS_DOWN_TOOLTIP, Icon.ARROW_LEFT, leftX, rowY);
        addButton(StructureToolContextActionPacket.REPLACER_RADIUS_UP, false,
                LangDefs.REPLACER_RADIUS_UP_TOOLTIP, Icon.ARROW_RIGHT, rightX, rowY);

        int toggleY = panelY + 70;
        addButton(StructureToolContextActionPacket.REPLACER_TOGGLE_CONNECTIVITY, false,
                LangDefs.REPLACER_CONNECTIVITY_TOGGLE_TOOLTIP,
                mode == ConnectivityMode.DIAGONAL ? Icon.CHECK : Icon.CROSS,
                leftX, toggleY);
        addButton(StructureToolContextActionPacket.REPLACER_TOGGLE_BLOCKSTATE, false,
                LangDefs.REPLACER_BLOCKSTATE_TOGGLE_TOOLTIP,
                this.lastReplacerBlockstateMode ? Icon.CHECK : Icon.CROSS,
                rightX, toggleY);
    }

    private static boolean isDirectionalPipeTarget(ItemStack held) {
        if (!IsModLoaded.GTCEU) {
            return false;
        }

        ItemStack target = PortableSpatialPiper.getTargetBlock(held);

        return !target.isEmpty() && GTCEuPiperExtension.supportsPipeDirection(target);
    }

    private static Icon iconForPipeDirection(PortableSpatialPiper.PipeDirectionMode mode) {
        return switch (mode) {
            case OFF -> Icon.CROSS;
            case ALONG_PATH -> Icon.ARROW_FRONT;
            case AGAINST_PATH -> Icon.ARROW_BACK;
        };
    }

    private void buildPiperLayout(int centerX, int centerY) {
        int panelX = centerX - PIPER_PANEL_WIDTH / 2;
        int panelY = centerY - PIPER_PANEL_HEIGHT / 2;

        addPanel(
                panelX,
                panelY,
                PIPER_PANEL_WIDTH,
                PIPER_PANEL_HEIGHT,
                Component.translatable(LangDefs.CONTEXT_MENU_OPTIONS_GROUP.getTranslationKey()),
                null);

        int rowWidth = BUTTON_SIZE * 4 + BUTTON_GAP * 3;
        int buttonX = panelX + (PIPER_PANEL_WIDTH - rowWidth) / 2;
        int buttonY = panelY + 30;

        addButton(
                StructureToolContextActionPacket.TOGGLE_SELECTION_MODE,
                false,
                LangDefs.CONTEXT_MENU_SELECTION_MODE,
                iconForSelectionMode(this.lastSelectionMode),
                buttonX,
                buttonY);

        addButton(
                StructureToolContextActionPacket.PIPER_TOGGLE_FILL_MODE,
                false,
                LangDefs.PIPER_FILL_MODE,
                this.lastPiperFillMode == PortableSpatialPiper.FillMode.FILL ? Icon.CHECK : Icon.CROSS,
                buttonX + BUTTON_SIZE + BUTTON_GAP,
                buttonY);

        addButton(
                StructureToolContextActionPacket.PIPER_CYCLE_PIPE_DIRECTION,
                false,
                LangDefs.PIPER_PIPE_DIRECTION,
                iconForPipeDirection(this.lastPiperPipeDirection),
                buttonX + (BUTTON_SIZE + BUTTON_GAP) * 2,
                buttonY,
                this.lastPiperTargetIsDirectionalPipe,
                false);

        addButton(
                StructureToolContextActionPacket.PIPER_CLEAR_TARGET,
                false,
                LangDefs.CONTEXT_MENU_CANCEL_SELECTION,
                Icon.MINUS,
                buttonX + (BUTTON_SIZE + BUTTON_GAP) * 3,
                buttonY,
                this.lastPiperHasTarget,
                false);
    }

    private void buildOffsetPanel(int panelX, int panelY) {
        int baseY = panelY + 24;

        buildMovementGrid(
                panelX,
                baseY,
                StructureToolContextActionPacket.OFFSET_LEFT,
                StructureToolContextActionPacket.OFFSET_RIGHT,
                StructureToolContextActionPacket.OFFSET_FRONT,
                StructureToolContextActionPacket.OFFSET_BACK,
                StructureToolContextActionPacket.OFFSET_UP,
                StructureToolContextActionPacket.OFFSET_DOWN);
    }

    private void buildSelectionPanel(int panelX, int panelY) {
        int selectorY = panelY + 24;
        int selectorGap = BUTTON_GAP;
        int selectorTotalWidth = BUTTON_SIZE * 2 + selectorGap;
        int selectorX = panelX + (PANEL_WIDTH - selectorTotalWidth) / 2;

        addButton(
                LOCAL_SELECT_RED,
                false,
                LangDefs.CONTEXT_MENU_RED_CORNER,
                Icon.CROSS,
                selectorX,
                selectorY,
                true,
                !this.selectedGreenCorner);

        addButton(
                LOCAL_SELECT_GREEN,
                false,
                LangDefs.CONTEXT_MENU_GREEN_CORNER,
                Icon.CHECK,
                selectorX + BUTTON_SIZE + selectorGap,
                selectorY,
                this.lastHasSelectionB,
                this.selectedGreenCorner);

        int baseY = panelY + 24 + BUTTON_SIZE + BUTTON_GAP;

        if (this.selectedGreenCorner) {
            buildMovementGrid(
                    panelX,
                    baseY,
                    StructureToolContextActionPacket.MOVE_SELECTION_GREEN_WEST,
                    StructureToolContextActionPacket.MOVE_SELECTION_GREEN_EAST,
                    StructureToolContextActionPacket.MOVE_SELECTION_GREEN_NORTH,
                    StructureToolContextActionPacket.MOVE_SELECTION_GREEN_SOUTH,
                    StructureToolContextActionPacket.MOVE_SELECTION_GREEN_UP,
                    StructureToolContextActionPacket.MOVE_SELECTION_GREEN_DOWN);
        } else {
            buildMovementGrid(
                    panelX,
                    baseY,
                    StructureToolContextActionPacket.MOVE_SELECTION_RED_WEST,
                    StructureToolContextActionPacket.MOVE_SELECTION_RED_EAST,
                    StructureToolContextActionPacket.MOVE_SELECTION_RED_NORTH,
                    StructureToolContextActionPacket.MOVE_SELECTION_RED_SOUTH,
                    StructureToolContextActionPacket.MOVE_SELECTION_RED_UP,
                    StructureToolContextActionPacket.MOVE_SELECTION_RED_DOWN);
        }
    }

    private void buildMovementGrid(
            int panelX,
            int baseY,
            int westAction,
            int eastAction,
            int northAction,
            int southAction,
            int upAction,
            int downAction) {
        int baseX = panelX + (PANEL_WIDTH - MOVE_GRID_WIDTH) / 2;

        int centerButtonX = baseX + BUTTON_STEP;
        int middleY = baseY + BUTTON_STEP;

        addButton(
                northAction,
                false,
                LangDefs.OFFSET_NORTH_TOOLTIP,
                Icon.ARROW_UP,
                centerButtonX,
                baseY);

        addButton(
                westAction,
                false,
                LangDefs.OFFSET_WEST_TOOLTIP,
                Icon.ARROW_LEFT,
                baseX,
                middleY);

        addButton(
                eastAction,
                false,
                LangDefs.OFFSET_EAST_TOOLTIP,
                Icon.ARROW_RIGHT,
                baseX + BUTTON_STEP * 2,
                middleY);

        addButton(
                southAction,
                false,
                LangDefs.OFFSET_SOUTH_TOOLTIP,
                Icon.ARROW_DOWN,
                centerButtonX,
                baseY + BUTTON_STEP * 2);

        int yAxisY = baseY + BUTTON_STEP * 3 + 8;

        addButton(
                downAction,
                false,
                LangDefs.OFFSET_DOWN_TOOLTIP,
                Icon.ARROW_DOWN,
                baseX,
                yAxisY);

        addButton(
                upAction,
                false,
                LangDefs.OFFSET_UP_TOOLTIP,
                Icon.ARROW_UP,
                baseX + BUTTON_STEP * 2,
                yAxisY);
    }

    private void buildTransformPanel(int panelX, int panelY) {
        int gridWidth = BUTTON_SIZE * 3 + BUTTON_GAP * 2;
        int baseX = panelX + (PANEL_WIDTH - gridWidth) / 2;
        int baseY = panelY + 24;

        boolean aroundOrigin = this.lastShiftDown;

        addButton(
                StructureToolContextActionPacket.ROTATE_CLOCKWISE,
                aroundOrigin,
                aroundOrigin ? LangDefs.ROTATE_CLOCKWISE_AROUND_ORIGIN : LangDefs.ROTATE_CLOCKWISE,
                Icon.ROTATE,
                baseX,
                baseY);

        addButton(
                StructureToolContextActionPacket.FLIP_EAST_WEST,
                aroundOrigin,
                aroundOrigin ? LangDefs.FLIP_EAST_WEST_AROUND_ORIGIN : LangDefs.FLIP_EAST_WEST,
                Icon.ARROW_LEFT,
                baseX + BUTTON_STEP,
                baseY);

        addButton(
                StructureToolContextActionPacket.FLIP_NORTH_SOUTH,
                aroundOrigin,
                aroundOrigin ? LangDefs.FLIP_NORTH_SOUTH_AROUND_ORIGIN : LangDefs.FLIP_NORTH_SOUTH,
                Icon.ARROW_FRONT,
                baseX + BUTTON_STEP * 2,
                baseY);

        addButton(
                StructureToolContextActionPacket.FLIP_VERTICAL,
                aroundOrigin,
                aroundOrigin ? LangDefs.FLIP_VERTICAL_AROUND_ORIGIN : LangDefs.FLIP_VERTICAL,
                Icon.ARROW_UP,
                baseX + BUTTON_STEP,
                baseY + BUTTON_STEP);
    }

    private void buildOptionsPanel(int panelX, int panelY) {
        List<OptionButtonSpec> specs = new ArrayList<>();

        specs.add(new OptionButtonSpec(
                StructureToolContextActionPacket.TOGGLE_SELECTION_MODE,
                LangDefs.CONTEXT_MENU_SELECTION_MODE,
                iconForSelectionMode(this.lastSelectionMode),
                true,
                false));

        specs.add(new OptionButtonSpec(
                StructureToolContextActionPacket.TOGGLE_ANCHOR,
                LangDefs.CONTEXT_MENU_ANCHOR,
                this.lastAnchorEnabled ? Icon.ANCHOR : Icon.ANCHOR_CROSS,
                this.lastHasStructure,
                this.lastAnchorEnabled));

        if (this.lastHasSelectionA || this.lastHasSelectionB) {
            specs.add(new OptionButtonSpec(
                    StructureToolContextActionPacket.CANCEL_SELECTION,
                    LangDefs.CONTEXT_MENU_CANCEL_SELECTION,
                    Icon.MINUS,
                    true,
                    false));
        }

        if (this.lastHoldingCloner) {
            specs.add(new OptionButtonSpec(
                    StructureToolContextActionPacket.CYCLE_NESTED_ITEMS,
                    LangDefs.CONTEXT_MENU_NESTED_ITEMS,
                    iconForNestedInventoryMode(this.lastNestedMode),
                    true,
                    false));
        }

        int totalWidth = specs.size() * BUTTON_SIZE + Math.max(0, specs.size() - 1) * BUTTON_GAP;
        int x = panelX + (OPTIONS_PANEL_WIDTH - totalWidth) / 2;
        int y = panelY + 24;

        for (OptionButtonSpec spec : specs) {
            addButton(
                    spec.action(),
                    false,
                    spec.label(),
                    spec.icon(),
                    x,
                    y,
                    spec.enabled(),
                    spec.highlighted());

            x += BUTTON_SIZE + BUTTON_GAP;
        }
    }

    private void addPanel(
            int x,
            int y,
            int width,
            int height,
            Component title,
            Component message) {
        this.panels.add(new ContextPanel(
                x,
                y,
                width,
                height,
                title,
                message));
    }

    private void addButton(
            int action,
            boolean aroundOrigin,
            LangDefs label,
            Icon icon,
            int x,
            int y) {
        addButton(action, aroundOrigin, label, icon, x, y, true, false);
    }

    private void addButton(
            int action,
            boolean aroundOrigin,
            LangDefs label,
            Icon icon,
            int x,
            int y,
            boolean enabled,
            boolean highlighted) {
        this.buttons.add(new ContextButton(
                action,
                aroundOrigin,
                label,
                icon,
                x,
                y,
                enabled,
                highlighted));
    }

    @Override
    public void tick() {
        if (!StructureToolContextMenuClient.isContextKeyDown()
                || !StructureToolContextMenuClient.hasStructureToolInMainHand()) {
            this.minecraft.setScreen(null);
            return;
        }

        ItemStack held = getHeldStack();

        boolean shiftDown = isShiftPhysicallyDown();
        boolean holdingCloner = isHoldingCloner();
        boolean hasStructure = hasStoredStructure();
        boolean hasSelectionA = StructureToolStackState.getSelectionA(held) != null;
        boolean hasSelectionB = StructureToolStackState.getSelectionB(held) != null;
        boolean anchorEnabled = StructureToolStackState.isAnchorEnabled(held);

        PortableSpatialCloner.NestedInventoryResourceMode nestedMode = getNestedInventoryMode();
        StructureToolStackState.SelectionMode selectionMode = getSelectionMode();

        boolean holdingReplacer = !held.isEmpty() && held.getItem() instanceof PortableSpatialReplacer;
        boolean holdingPiper = !held.isEmpty() && held.getItem() instanceof PortableSpatialPiper;

        PortableSpatialPiper.FillMode piperFillMode = holdingPiper
                ? PortableSpatialPiper.getFillMode(held)
                : this.lastPiperFillMode;

        boolean piperHasTarget = holdingPiper
                ? !PortableSpatialPiper.getTargetBlock(held).isEmpty()
                : this.lastPiperHasTarget;

        PortableSpatialPiper.PipeDirectionMode piperPipeDirection = holdingPiper
                ? PortableSpatialPiper.getPipeDirectionMode(held)
                : this.lastPiperPipeDirection;

        boolean piperDirectionalPipe = holdingPiper
                ? isDirectionalPipeTarget(held)
                : this.lastPiperTargetIsDirectionalPipe;

        int replacerRadius = holdingReplacer ? PortableSpatialReplacer.getRadius(held) : this.lastReplacerRadius;
        ConnectivityMode replacerConn = holdingReplacer ? PortableSpatialReplacer.getConnectivityMode(held)
                : this.lastReplacerConnectivity;
        boolean replacerBlockstate = holdingReplacer ? PortableSpatialReplacer.isSameBlockstate(held)
                : this.lastReplacerBlockstateMode;

        if (SpatialMultiTool.isMultiTool(held) != this.lastMultiTool
                || SpatialMultiTool.getMode(held) != this.lastMultiToolMode
                || shiftDown != this.lastShiftDown
                || holdingCloner != this.lastHoldingCloner
                || holdingReplacer != this.lastHoldingReplacer
                || holdingPiper != this.lastHoldingPiper
                || piperFillMode != this.lastPiperFillMode
                || piperHasTarget != this.lastPiperHasTarget
                || piperPipeDirection != this.lastPiperPipeDirection
                || piperDirectionalPipe != this.lastPiperTargetIsDirectionalPipe
                || hasStructure != this.lastHasStructure
                || hasSelectionA != this.lastHasSelectionA
                || hasSelectionB != this.lastHasSelectionB
                || anchorEnabled != this.lastAnchorEnabled
                || nestedMode != this.lastNestedMode
                || selectionMode != this.lastSelectionMode
                || replacerRadius != this.lastReplacerRadius
                || replacerConn != this.lastReplacerConnectivity
                || replacerBlockstate != this.lastReplacerBlockstateMode) {
            rebuildLayout();
        }
    }

    @Override
    public void onClose() {
        if (StructureToolContextMenuClient.isContextKeyDown()) {
            StructureToolContextMenuClient.suppressUntilRelease();
        }

        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.onClose();
            return true;
        }

        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int mouseButton) {
        if (mouseButton != 0) {
            return true;
        }

        if (layoutToolModeDropdown().mouseClicked(mouseX, mouseY)) {
            return true;
        }

        ContextButton hovered = getHoveredButton(mouseX, mouseY);

        if (hovered == null) {
            return true;
        }

        if (!hovered.enabled()) {
            return true;
        }

        if (hovered.action() == LOCAL_SELECT_RED) {
            this.selectedGreenCorner = false;
            rebuildLayout();
            return true;
        }

        if (hovered.action() == LOCAL_SELECT_GREEN) {
            if (this.lastHasSelectionB) {
                this.selectedGreenCorner = true;
                rebuildLayout();
            }

            return true;
        }

        if (isStructureAction(hovered.action()) && !hasStoredStructure()) {
            showClientMessage(LangDefs.CONTEXT_MENU_STRUCTURE_REQUIRED);
            return true;
        }

        if (isMoveSelectionAction(hovered.action()) && !hasAnySelection()) {
            showClientMessage(LangDefs.CONTEXT_MENU_SELECT_SOMETHING_FIRST);
            return true;
        }

        if (hovered.action() == StructureToolContextActionPacket.CYCLE_NESTED_ITEMS) {
            ItemStack held = getHeldStack();

            if (!held.isEmpty() && held.getItem() instanceof PortableSpatialCloner) {
                PortableSpatialCloner.cycleNestedInventoryResourceMode(held);
                rebuildLayout();
            }
        }

        if (hovered.action() == StructureToolContextActionPacket.TOGGLE_SELECTION_MODE) {
            ItemStack held = getHeldStack();

            if (!held.isEmpty()) {
                StructureToolStackState.cycleSelectionMode(held);
                rebuildLayout();
            }
        }

        if (hovered.action() == StructureToolContextActionPacket.PIPER_TOGGLE_FILL_MODE) {
            ItemStack held = getHeldStack();

            if (!held.isEmpty() && held.getItem() instanceof PortableSpatialPiper) {
                PortableSpatialPiper.cycleFillMode(held);
                rebuildLayout();
            }
        }

        if (hovered.action() == StructureToolContextActionPacket.PIPER_CYCLE_PIPE_DIRECTION) {
            ItemStack held = getHeldStack();

            if (!held.isEmpty() && held.getItem() instanceof PortableSpatialPiper) {
                PortableSpatialPiper.cyclePipeDirectionMode(held);
                rebuildLayout();
            }
        }

        if (hovered.action() == StructureToolContextActionPacket.PIPER_CLEAR_TARGET) {
            ItemStack held = getHeldStack();

            if (!held.isEmpty() && held.getItem() instanceof PortableSpatialPiper) {
                PortableSpatialPiper.setTargetBlock(held, ItemStack.EMPTY);
                PortableSpatialPiper.clearRoute(held);
                rebuildLayout();
            }
        }

        if (hovered.action() == StructureToolContextActionPacket.CANCEL_SELECTION) {
            ItemStack held = getHeldStack();

            if (!held.isEmpty()) {
                StructureToolStackState.clearSelection(held);
                StructureToolStackState.resetPreviewSideMap(held);
                rebuildLayout();
            }
        }

        boolean aroundOrigin = isTransformAction(hovered.action()) && isShiftPhysicallyDown();

        NetworkHandler.sendToServer(new StructureToolContextActionPacket(
                hovered.action(),
                aroundOrigin));

        return true;
    }

    private void showClientMessage(LangDefs message) {
        Minecraft mc = Minecraft.getInstance();

        if (mc.player != null) {
            mc.player.displayClientMessage(
                    Component.translatable(message.getTranslationKey()),
                    true);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0x12000000);

        ToolModeDropdownWidget dropdown = layoutToolModeDropdown();

        renderTopInfo(graphics);

        if (dropdown.isOpen()) {
            dropdown.render(graphics, this.font, mouseX, mouseY);
            return;
        }

        renderPanels(graphics);

        ContextButton hovered = getHoveredButton(mouseX, mouseY);

        for (ContextButton button : this.buttons) {
            renderButton(graphics, button, button == hovered);
        }

        dropdown.render(graphics, this.font, mouseX, mouseY);

        if (hovered != null) {
            graphics.renderComponentTooltip(
                    this.font,
                    tooltipFor(hovered),
                    mouseX,
                    mouseY);
        }
    }

    private void renderTopInfo(GuiGraphics graphics) {
        ItemStack held = getHeldStack();

        Component title = Component.translatable(LangDefs.CONTEXT_MENU_TITLE.getTranslationKey());
        Component toolName = held.isEmpty() ? Component.empty() : held.getHoverName();

        int centerX = this.width / 2;
        int y = this.topInfoY;

        graphics.drawCenteredString(
                this.font,
                title,
                centerX,
                y,
                TEXT_GREEN);

        if (!this.lastMultiTool && !toolName.getString().isBlank()) {
            graphics.drawCenteredString(
                    this.font,
                    toolName,
                    centerX,
                    y + 14,
                    TEXT_NORMAL);
        }

        if (this.lastMultiToolMode == null) {
            return;
        }

        if (!this.lastHoldingReplacer && !this.lastHoldingPiper && !this.toolModeDropdown.isOpen()) {
            int hintY = this.lastMultiTool
                    ? y + TOOL_DROPDOWN_TOP_OFFSET + ToolModeDropdownWidget.ROW_HEIGHT + 2
                    : y + 28;

            graphics.drawCenteredString(
                    this.font,
                    Component.translatable(LangDefs.CONTEXT_MENU_HOLD_SHIFT_ORIGIN.getTranslationKey()),
                    centerX,
                    hintY,
                    this.lastShiftDown ? TEXT_GREEN : TEXT_DIM);
        }
    }

    private ToolModeDropdownWidget layoutToolModeDropdown() {
        this.toolModeDropdown.setToolStack(getHeldStack());
        this.toolModeDropdown.setPosition(
                this.width / 2 - ToolModeDropdownWidget.WIDTH / 2,
                this.topInfoY + TOOL_DROPDOWN_TOP_OFFSET);

        return this.toolModeDropdown;
    }

    private void renderPanels(GuiGraphics graphics) {
        for (ContextPanel panel : this.panels) {
            renderPanel(graphics, panel);

            if (panel.message() != null) {
                renderPanelMessage(graphics, panel);
            }
        }
    }

    private void renderPanel(GuiGraphics graphics, ContextPanel panel) {
        int x = panel.x();
        int y = panel.y();
        int width = panel.width();
        int height = panel.height();

        graphics.fill(x, y, x + width, y + height, PANEL_BG);

        graphics.fill(x, y, x + width, y + 1, PANEL_BORDER);
        graphics.fill(x, y + height - 1, x + width, y + height, PANEL_BORDER);
        graphics.fill(x, y, x + 1, y + height, PANEL_BORDER);
        graphics.fill(x + width - 1, y, x + width, y + height, PANEL_BORDER);

        graphics.drawCenteredString(
                this.font,
                panel.title(),
                x + width / 2,
                y + 7,
                PANEL_TITLE);
    }

    private void renderPanelMessage(GuiGraphics graphics, ContextPanel panel) {
        int textWidth = panel.width() - PANEL_PADDING * 2;
        int centerX = panel.x() + panel.width() / 2;
        int centerY = panel.y() + panel.height() / 2 + 8;

        List<FormattedCharSequence> lines = wrapComponent(panel.message(), textWidth);
        int totalHeight = lines.size() * 10;
        int y = centerY - totalHeight / 2;

        for (FormattedCharSequence line : lines) {
            int lineWidth = this.font.width(line);

            graphics.drawString(
                    this.font,
                    line,
                    centerX - lineWidth / 2,
                    y,
                    TEXT_DIM,
                    false);

            y += 10;
        }
    }

    private void renderButton(GuiGraphics graphics, ContextButton button, boolean hovered) {
        int x = button.x();
        int y = button.y();

        int bg = button.enabled()
                ? hovered ? BUTTON_BG_HOVER : BUTTON_BG
                : BUTTON_BG_DISABLED;

        int border = button.enabled()
                ? button.highlighted()
                        ? BUTTON_BORDER_SELECTED
                        : hovered ? BUTTON_BORDER_HOVER : BUTTON_BORDER
                : BUTTON_BORDER_DISABLED;

        graphics.fill(x, y, x + BUTTON_SIZE, y + BUTTON_SIZE, bg);

        graphics.fill(x, y, x + BUTTON_SIZE, y + 1, border);
        graphics.fill(x, y + BUTTON_SIZE - 1, x + BUTTON_SIZE, y + BUTTON_SIZE, border);
        graphics.fill(x, y, x + 1, y + BUTTON_SIZE, border);
        graphics.fill(x + BUTTON_SIZE - 1, y, x + BUTTON_SIZE, y + BUTTON_SIZE, border);

        int iconPadding = 4;
        int iconSize = BUTTON_SIZE - iconPadding * 2;

        graphics.blit(
                button.icon().texture(),
                x + iconPadding,
                y + iconPadding,
                0,
                0,
                iconSize,
                iconSize,
                iconSize,
                iconSize);

        if (!button.enabled()) {
            graphics.fill(x, y, x + BUTTON_SIZE, y + BUTTON_SIZE, 0x77000000);
        }
    }

    private List<Component> tooltipFor(ContextButton button) {
        List<Component> lines = new ArrayList<>();

        if (button.action() == LOCAL_SELECT_RED || button.action() == LOCAL_SELECT_GREEN) {
            lines.add(Component.translatable(button.label().getTranslationKey()));
            return lines;
        }

        lines.add(Component.translatable(button.label().getTranslationKey()));

        if (isTransformAction(button.action()) && !this.lastHoldingReplacer) {
            lines.add(Component.translatable(
                    LangDefs.CONTEXT_MENU_HOLD_SHIFT_ORIGIN.getTranslationKey()).withStyle(
                            isShiftPhysicallyDown() ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY));
        }

        if (button.action() == StructureToolContextActionPacket.TOGGLE_SELECTION_MODE) {
            Component modeTooltip = this.lastSelectionMode == StructureToolStackState.SelectionMode.BLOCK_IN_FRONT
                    ? Component.translatable(LangDefs.CONTEXT_MENU_SELECTION_MODE_BLOCK_IN_FRONT.getTranslationKey())
                    : Component.translatable(LangDefs.CONTEXT_MENU_SELECTION_MODE_DEFAULT.getTranslationKey());

            addWrappedTooltipLines(lines, modeTooltip.copy().withStyle(ChatFormatting.GRAY));
        }

        if (button.action() == StructureToolContextActionPacket.PIPER_TOGGLE_FILL_MODE) {
            LangDefs desc = this.lastPiperFillMode == PortableSpatialPiper.FillMode.FILL
                    ? LangDefs.PIPER_FILL_MODE_FILL
                    : LangDefs.PIPER_FILL_MODE_PATH;

            addWrappedTooltipLines(
                    lines,
                    Component.translatable(desc.getTranslationKey()).withStyle(ChatFormatting.GRAY));
        }

        if (button.action() == StructureToolContextActionPacket.PIPER_CYCLE_PIPE_DIRECTION) {
            LangDefs desc;

            if (!this.lastPiperTargetIsDirectionalPipe) {
                desc = LangDefs.PIPER_PIPE_DIRECTION_UNSUPPORTED;
            } else {
                desc = switch (this.lastPiperPipeDirection) {
                    case OFF -> LangDefs.PIPER_PIPE_DIRECTION_OFF;
                    case ALONG_PATH -> LangDefs.PIPER_PIPE_DIRECTION_ALONG;
                    case AGAINST_PATH -> LangDefs.PIPER_PIPE_DIRECTION_AGAINST;
                };
            }

            addWrappedTooltipLines(
                    lines,
                    Component.translatable(desc.getTranslationKey()).withStyle(
                            this.lastPiperTargetIsDirectionalPipe
                                    ? ChatFormatting.GRAY
                                    : ChatFormatting.RED));
        }

        if (button.action() == StructureToolContextActionPacket.TOGGLE_ANCHOR) {
            Component anchorTooltip = this.lastAnchorEnabled
                    ? Component.translatable(LangDefs.CONTEXT_MENU_ANCHOR_ENABLED.getTranslationKey())
                    : Component.translatable(LangDefs.CONTEXT_MENU_ANCHOR_DISABLED.getTranslationKey());

            addWrappedTooltipLines(lines, anchorTooltip.copy().withStyle(ChatFormatting.GRAY));
        }

        if (button.action() == StructureToolContextActionPacket.REPLACER_TOGGLE_CONNECTIVITY) {
            LangDefs desc = this.lastReplacerConnectivity == ConnectivityMode.DIAGONAL
                    ? LangDefs.REPLACER_CONNECTIVITY_DIAGONAL_DESC
                    : LangDefs.REPLACER_CONNECTIVITY_DIRECT_DESC;
            addWrappedTooltipLines(lines,
                    Component.translatable(desc.getTranslationKey()).withStyle(ChatFormatting.GRAY));
        }

        if (button.action() == StructureToolContextActionPacket.REPLACER_TOGGLE_BLOCKSTATE) {
            LangDefs desc = this.lastReplacerBlockstateMode
                    ? LangDefs.REPLACER_BLOCKSTATE_ON_DESC
                    : LangDefs.REPLACER_BLOCKSTATE_OFF_DESC;
            addWrappedTooltipLines(lines,
                    Component.translatable(desc.getTranslationKey()).withStyle(ChatFormatting.GRAY));
        }

        if (button.action() == StructureToolContextActionPacket.CYCLE_NESTED_ITEMS
                && this.lastHoldingCloner) {
            PortableSpatialCloner.NestedInventoryResourceMode mode = getNestedInventoryMode();

            Component modeTooltip = switch (mode) {
                case NONE -> Component.translatable(
                        LangDefs.PORTABLE_SPATIAL_CLONER_NESTED_MODE_NONE_TOOLTIP.getTranslationKey());
                case PLAYER -> Component.translatable(
                        LangDefs.PORTABLE_SPATIAL_CLONER_NESTED_MODE_PLAYER_TOOLTIP.getTranslationKey());
                case CONNECTED -> Component.translatable(
                        LangDefs.PORTABLE_SPATIAL_CLONER_NESTED_MODE_CONNECTED_TOOLTIP.getTranslationKey());
                case BOTH -> Component.translatable(
                        LangDefs.PORTABLE_SPATIAL_CLONER_NESTED_MODE_BOTH_TOOLTIP.getTranslationKey());
            };

            addWrappedTooltipLines(lines, modeTooltip.copy().withStyle(ChatFormatting.GRAY));
        }

        if (!button.enabled()
                && button.action() != StructureToolContextActionPacket.PIPER_CYCLE_PIPE_DIRECTION) {
            lines.add(Component.translatable(
                    LangDefs.CONTEXT_MENU_STRUCTURE_REQUIRED.getTranslationKey()).withStyle(ChatFormatting.RED));
        }

        return lines;
    }

    private ContextButton getHoveredButton(double mouseX, double mouseY) {
        for (ContextButton button : this.buttons) {
            if (mouseX >= button.x()
                    && mouseX < button.x() + BUTTON_SIZE
                    && mouseY >= button.y()
                    && mouseY < button.y() + BUTTON_SIZE) {
                return button;
            }
        }

        return null;
    }

    private boolean isShiftPhysicallyDown() {
        Minecraft mc = Minecraft.getInstance();

        if (mc.getWindow() == null) {
            return false;
        }

        long window = mc.getWindow().getWindow();

        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
    }

    private boolean hasStoredStructure() {
        ItemStack held = getHeldStack();

        if (held.isEmpty()) {
            return false;
        }

        return !StructureToolStackState.getStructureId(held).isBlank();
    }

    private boolean hasAnySelection() {
        ItemStack held = getHeldStack();

        if (held.isEmpty()) {
            return false;
        }

        return StructureToolStackState.getSelectionA(held) != null
                || StructureToolStackState.getSelectionB(held) != null;
    }

    private boolean isHoldingCloner() {
        ItemStack held = getHeldStack();
        return !held.isEmpty() && held.getItem() instanceof PortableSpatialCloner;
    }

    private ItemStack getHeldStack() {
        Minecraft mc = Minecraft.getInstance();

        if (mc.player == null) {
            return ItemStack.EMPTY;
        }

        return mc.player.getMainHandItem();
    }

    private PortableSpatialCloner.NestedInventoryResourceMode getNestedInventoryMode() {
        ItemStack held = getHeldStack();

        if (held.isEmpty() || !(held.getItem() instanceof PortableSpatialCloner)) {
            return PortableSpatialCloner.NestedInventoryResourceMode.NONE;
        }

        return PortableSpatialCloner.getNestedInventoryResourceMode(held);
    }

    private StructureToolStackState.SelectionMode getSelectionMode() {
        ItemStack held = getHeldStack();

        if (held.isEmpty()) {
            return StructureToolStackState.SelectionMode.DEFAULT;
        }

        return StructureToolStackState.getSelectionMode(held);
    }

    private static Icon iconForNestedInventoryMode(PortableSpatialCloner.NestedInventoryResourceMode mode) {
        return switch (mode) {
            case NONE -> Icon.CROSS;
            case PLAYER -> Icon.PLAYER_INV;
            case CONNECTED -> Icon.EXTERNAL_INV;
            case BOTH -> Icon.CHECK;
        };
    }

    private static Icon iconForSelectionMode(StructureToolStackState.SelectionMode mode) {
        return switch (mode) {
            case DEFAULT -> Icon.CROSS;
            case BLOCK_IN_FRONT -> Icon.PLUS;
        };
    }

    private static boolean isTransformAction(int action) {
        return action == StructureToolContextActionPacket.ROTATE_CLOCKWISE
                || action == StructureToolContextActionPacket.FLIP_EAST_WEST
                || action == StructureToolContextActionPacket.FLIP_NORTH_SOUTH
                || action == StructureToolContextActionPacket.FLIP_VERTICAL;
    }

    private static boolean isStructureAction(int action) {
        return action == StructureToolContextActionPacket.OFFSET_LEFT
                || action == StructureToolContextActionPacket.OFFSET_RIGHT
                || action == StructureToolContextActionPacket.OFFSET_UP
                || action == StructureToolContextActionPacket.OFFSET_DOWN
                || action == StructureToolContextActionPacket.OFFSET_FRONT
                || action == StructureToolContextActionPacket.OFFSET_BACK
                || isTransformAction(action)
                || action == StructureToolContextActionPacket.TOGGLE_ANCHOR;
    }

    private static boolean isMoveSelectionAction(int action) {
        return (action >= StructureToolContextActionPacket.MOVE_SELECTION_RED_WEST
                && action <= StructureToolContextActionPacket.MOVE_SELECTION_RED_DOWN)
                || (action >= StructureToolContextActionPacket.MOVE_SELECTION_GREEN_WEST
                        && action <= StructureToolContextActionPacket.MOVE_SELECTION_GREEN_DOWN);
    }

    private List<FormattedCharSequence> wrapComponent(Component component, int maxWidth) {
        return this.font.split(component, maxWidth);
    }

    private static void addWrappedTooltipLines(List<Component> lines, Component component) {
        addWrappedTooltipLines(lines, component, TOOLTIP_MAX_CHARS);
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

    private record ContextPanel(
            int x,
            int y,
            int width,
            int height,
            Component title,
            Component message) {
    }

    private record ContextButton(
            int action,
            boolean aroundOrigin,
            LangDefs label,
            Icon icon,
            int x,
            int y,
            boolean enabled,
            boolean highlighted) {
    }

    private record OptionButtonSpec(
            int action,
            LangDefs label,
            Icon icon,
            boolean enabled,
            boolean highlighted) {
    }
}
