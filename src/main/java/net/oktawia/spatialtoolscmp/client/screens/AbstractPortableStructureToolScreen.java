package net.oktawia.spatialtoolscmp.client.screens;

import com.lowdragmc.lowdraglib.gui.widget.SceneWidget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.utils.TrackedDummyWorld;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import net.oktawia.spatialtoolscmp.IsModLoaded;
import net.oktawia.spatialtoolscmp.SpatialToolsCMP;
import net.oktawia.spatialtoolscmp.client.misc.widgets.PowerUpgradePanelWidget;
import net.oktawia.spatialtoolscmp.client.misc.widgets.SpatialOffsetControlsWidget;
import net.oktawia.spatialtoolscmp.client.misc.widgets.SpatialTransformationsWidget;
import net.oktawia.spatialtoolscmp.client.misc.widgets.PortableSpatialStorageDummyWorld;
import net.oktawia.spatialtoolscmp.client.renderer.PortableSpatialStoragePreviewSync;
import net.oktawia.spatialtoolscmp.client.misc.widgets.PortableSpatialStorageSceneWidget;
import net.oktawia.spatialtoolscmp.client.renderer.PreviewBlock;
import net.oktawia.spatialtoolscmp.client.renderer.PreviewStructure;
import net.oktawia.spatialtoolscmp.compat.ae2.AE2Compat;
import net.oktawia.spatialtoolscmp.defs.LangDefs;
import net.oktawia.spatialtoolscmp.items.AbstractStructureCaptureToolItem;
import net.oktawia.spatialtoolscmp.logic.StructureToolStackState;
import net.oktawia.spatialtoolscmp.menus.AbstractPortableStructureToolMenu;
import net.oktawia.spatialtoolscmp.util.TemplateUtil;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import java.util.*;

public abstract class AbstractPortableStructureToolScreen<M extends AbstractPortableStructureToolMenu>
        extends AbstractContainerScreen<M> {

    protected static final Direction[] DIRECTIONS = {
            Direction.NORTH,
            Direction.SOUTH,
            Direction.WEST,
            Direction.EAST,
            Direction.UP,
            Direction.DOWN
    };

    protected static final int DIRECTION_COMPASS_SIZE = 48;
    protected static final float DIRECTION_COMPASS_ORTHO_RANGE = 18.5F;

    protected static final ResourceLocation BACKGROUND = SpatialToolsCMP.makeId("textures/gui/background.png");

    protected List<Component> compatibleUpgradeTooltip = List.of();
    protected List<Component> compatibleCraftingUpgradeTooltip = List.of();

    protected final PowerUpgradePanelWidget powerUpgradePanel = new PowerUpgradePanelWidget();
    protected SpatialTransformationsWidget transformationsWidget;
    protected SpatialOffsetControlsWidget offsetControls;

    protected final WidgetGroup directionCompassRoot = new WidgetGroup(0, 0, 0, 0);
    protected TrackedDummyWorld directionCompassWorld;
    protected SceneWidget directionCompassScene;
    protected Set<BlockPos> directionCompassCore = Collections.emptySet();

    protected boolean transformAroundOriginMode = false;
    protected boolean previewStructureFromSharedCache = false;
    protected boolean initialCameraAlignedToPlayer = false;

    protected final WidgetGroup root = new WidgetGroup(0, 0, 0, 0);
    protected final PortableSpatialStorageDummyWorld world = new PortableSpatialStorageDummyWorld();
    protected PortableSpatialStorageSceneWidget scene;

    protected PreviewStructure previewStructure;
    protected Set<BlockPos> renderedCore = Collections.emptySet();
    protected BlockPos min = BlockPos.ZERO;
    protected BlockPos max = BlockPos.ZERO;

    protected boolean rotating = false;
    protected double lastMouseX;
    protected double lastMouseY;
    protected float yaw = 0.0F;
    protected float pitch = 90.0F;
    protected float distance = -20.0F;
    protected int lastSceneWidth = -1;
    protected int lastSceneHeight = -1;
    protected int previewReloadDelay = -1;

    protected AbstractPortableStructureToolScreen(
            M menu,
            Inventory playerInventory,
            Component title
    ) {
        super(menu, playerInventory, title);
    }

    protected final void initCommonWidgets(List<Component> compatibleUpgrades) {
        this.compatibleUpgradeTooltip = compatibleUpgrades == null
                ? List.of()
                : List.copyOf(compatibleUpgrades);
        this.compatibleCraftingUpgradeTooltip = buildCompatibleCraftingUpgradeTooltip();

        this.transformationsWidget = new SpatialTransformationsWidget(
                () -> getMenu().flipEastWest(),
                () -> getMenu().flipEastWestAroundOrigin(),
                () -> getMenu().flipNorthSouth(),
                () -> getMenu().flipNorthSouthAroundOrigin(),
                () -> getMenu().flipVertical(),
                () -> getMenu().flipVerticalAroundOrigin(),
                () -> getMenu().rotateClockwise(1),
                () -> getMenu().rotateClockwiseAroundOrigin(1)
        );

        this.transformationsWidget.setScreenPosition(this.leftPos, this.topPos);

        this.offsetControls = new SpatialOffsetControlsWidget(
                () -> getMenu().offsetLeft(),
                () -> getMenu().offsetRight(),
                () -> getMenu().offsetUp(),
                () -> getMenu().offsetDown(),
                () -> getMenu().offsetFront(),
                () -> getMenu().offsetBack()
        );

        this.offsetControls.setScreenPosition(this.leftPos, this.topPos);
        this.offsetControls.setOffset(readCurrentOffset());

        refreshTransformAroundOriginMode();
        updateTransformButtonTooltips();

        this.addRenderableWidget(this.transformationsWidget);
        this.addRenderableWidget(this.offsetControls);
    }

    protected final void finishInit() {
        this.root.setSize(this.width, this.height);
        this.directionCompassRoot.setSize(this.width, this.height);

        reloadPreviewNow();
        getMenu().requestPreview();
    }

    protected boolean hasPreviewStructureForCompass() {
        return this.previewStructure != null
                && !this.previewStructure.blocks().isEmpty();
    }

    protected void renderDirectionCompassIfNeeded(
            GuiGraphics graphics,
            int mouseX,
            int mouseY,
            float partialTick
    ) {
        if (!hasPreviewStructureForCompass()) {
            clearDirectionCompassScene();
            return;
        }

        ensureDirectionCompassScene();
        updateDirectionCompassSceneLayoutAndCamera();

        this.directionCompassRoot.drawInBackground(graphics, mouseX, mouseY, partialTick);
        this.directionCompassRoot.drawInForeground(graphics, mouseX, mouseY, partialTick);
        this.directionCompassRoot.drawOverlay(graphics, mouseX, mouseY, partialTick);
    }

    protected record PreviewRect(int x, int y, int width, int height) {
    }

    protected abstract PreviewRect getPreviewRect();

    protected abstract ItemStack findRelevantStack();

    protected void onPreviewTagLoaded(CompoundTag syncedTag) {
    }

    protected void onClearExtraState() {
    }

    protected void renderExtraOverlays(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    protected void layoutCommonWidgets() {
        if (this.transformationsWidget != null) {
            this.transformationsWidget.setScreenPosition(this.leftPos, this.topPos);
        }

        if (this.offsetControls != null) {
            this.offsetControls.setScreenPosition(this.leftPos, this.topPos);
        }
    }

    private void renderPowerUpgradeTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        if (getMenu().getPowerUpgradeSlotCount() <= 0) {
            return;
        }

        this.powerUpgradePanel.setScreenPosition(this.leftPos, this.topPos);
        this.powerUpgradePanel.setSlots(
                getMenu().getPowerUpgradeSlotCount(),
                getMenu().getMaxUpgradesCount()
        );

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
                mouseY
        );
    }

    static List<Component> buildCompatibleCraftingUpgradeTooltip() {
        List<Component> lines = new ArrayList<>();

        lines.add(Component.translatable(
                LangDefs.CRAFTING_UPGRADE_SLOT_HINT.getTranslationKey()
        ).withStyle(ChatFormatting.YELLOW));

        lines.add(Component.translatable(
                LangDefs.VALID_UPGRADES.getTranslationKey()
        ).withStyle(ChatFormatting.WHITE));

        if (IsModLoaded.AE2) {
            lines.add(AE2Compat.modDisplayName().copy());
            lines.add(AE2Compat.craftingUpgradeDisplayName().copy().withStyle(ChatFormatting.GRAY));
        } else {
            lines.add(Component.translatable(
                    LangDefs.AE2_NOT_INSTALLED.getTranslationKey()
            ).withStyle(ChatFormatting.RED));

            lines.add(Component.translatable(
                    LangDefs.NOT_AVAILABLE.getTranslationKey()
            ).withStyle(ChatFormatting.GRAY));
        }

        return lines;
    }

    protected boolean isShiftPhysicallyDown() {
        long window = Minecraft.getInstance().getWindow().getWindow();

        return GLFW.glfwGetKey(window, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(window, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
    }

    protected void refreshTransformAroundOriginMode() {
        this.transformAroundOriginMode = isShiftPhysicallyDown();
    }

    protected void updateTransformButtonTooltips() {
        if (this.transformationsWidget == null) {
            return;
        }

        this.transformationsWidget.setTransformAroundOriginMode(this.transformAroundOriginMode);
    }

    protected void syncOffsetDisplays() {
        if (this.offsetControls == null) {
            return;
        }

        this.offsetControls.setScreenPosition(this.leftPos, this.topPos);
        this.offsetControls.setOffset(readCurrentOffset());
    }

    protected BlockPos readCurrentOffset() {
        ItemStack stack = findRelevantStack();

        if (stack.isEmpty()) {
            return BlockPos.ZERO;
        }

        CompoundTag tag = stack.getTag();
        return TemplateUtil.getTemplateOffset(tag);
    }

    public void markPreviewDirty() {
        this.previewReloadDelay = 1;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        renderPowerUpgradePanelWidget(graphics);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        layoutCommonWidgets();

        if (this.scene != null) {
            PreviewRect rect = getPreviewRect();

            if (this.scene.getPositionX() != rect.x()
                    || this.scene.getPositionY() != rect.y()
                    || this.scene.getSizeWidth() != rect.width()
                    || this.scene.getSizeHeight() != rect.height()) {
                this.scene.setSelfPosition(rect.x(), rect.y());
                this.scene.setSize(rect.width(), rect.height());

                if (rect.width() != this.lastSceneWidth || rect.height() != this.lastSceneHeight) {
                    this.lastSceneWidth = rect.width();
                    this.lastSceneHeight = rect.height();

                    int sizeX = this.max.getX() - this.min.getX() + 1;
                    int sizeY = this.max.getY() - this.min.getY() + 1;
                    int sizeZ = this.max.getZ() - this.min.getZ() + 1;
                    int maxDim = Math.max(sizeX, Math.max(sizeY, sizeZ));

                    float scaleW = (float) rect.width() / 160.0F;
                    float scaleH = (float) rect.height() / 120.0F;
                    float scale = Math.max(0.75F, Math.min(scaleW, scaleH));

                    this.distance = Math.max(6.0F, (maxDim * 2.2F) / scale);
                    this.scene.setZoom(this.distance);
                }
            }
        }

        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        graphics.flush();

        updateDirectionCompassSceneLayoutAndCamera();

        this.root.drawInBackground(graphics, mouseX, mouseY, partialTick);
        this.root.drawInForeground(graphics, mouseX, mouseY, partialTick);
        this.root.drawOverlay(graphics, mouseX, mouseY, partialTick);

        RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        graphics.flush();

        if (!renderEmptyPreviewBehindWidgets()) {
            renderEmptyPreviewMessage(graphics);
        }

        renderDirectionCompassIfNeeded(graphics, mouseX, mouseY, partialTick);

        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();

        renderExtraOverlays(graphics, mouseX, mouseY, partialTick);
        renderPowerUpgradeTooltip(graphics, mouseX, mouseY);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    protected boolean renderEmptyPreviewBehindWidgets() {
        return false;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.scene != null && insideScene(mouseX, mouseY) && button == 0) {
            this.rotating = true;
            this.lastMouseX = mouseX;
            this.lastMouseY = mouseY;
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (this.rotating && this.scene != null) {
            double dx = mouseX - this.lastMouseX;
            double dy = mouseY - this.lastMouseY;

            this.lastMouseX = mouseX;
            this.lastMouseY = mouseY;

            this.pitch += (float) (dx * 0.5F);
            this.yaw = Math.max(-89.0F, Math.min(89.0F, this.yaw + (float) (dy * 0.5F)));

            this.scene.setCameraYawAndPitch(this.yaw, this.pitch);
            return true;
        }

        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            this.rotating = false;
        }

        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (this.scene != null && insideScene(mouseX, mouseY)) {
            float step = 1.0F;
            float minDistance = 2.0F;
            float maxDistance = 256.0F;

            this.distance = Math.max(minDistance, Math.min(maxDistance, this.distance - (float) delta * step));
            this.scene.setZoom(this.distance);
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    @Override
    protected void containerTick() {
        super.containerTick();

        this.root.updateScreen();
        this.directionCompassRoot.updateScreen();

        syncOffsetDisplays();

        refreshTransformAroundOriginMode();
        updateTransformButtonTooltips();

        updateDirectionCompassSceneLayoutAndCamera();

        if (this.previewReloadDelay >= 0) {
            if (this.previewReloadDelay == 0) {
                this.previewReloadDelay = -1;
                reloadPreviewNow();
            } else {
                this.previewReloadDelay--;
            }
        }
    }

    @Override
    public void removed() {
        super.removed();
        clearScene();
    }

    public void reloadPreviewNow() {
        ItemStack stack = findRelevantStack();

        if (stack.isEmpty()) {
            clearScene();
            return;
        }

        String structureId = StructureToolStackState.getStructureId(stack);

        PreviewStructure newStructure = null;
        boolean fromSharedCache = false;

        if (!structureId.isBlank()) {
            newStructure = PortableSpatialStoragePreviewSync.cacheGet(structureId);
            fromSharedCache = newStructure != null;
        }

        if (newStructure == null || newStructure.blocks().isEmpty()) {
            clearScene();
            return;
        }

        if (this.previewStructure != null && !this.previewStructureFromSharedCache) {
            this.previewStructure.close();
        }

        this.previewStructure = newStructure;
        this.previewStructureFromSharedCache = fromSharedCache;

        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (PreviewBlock block : newStructure.blocks()) {
            BlockPos pos = block.pos();

            minX = Math.min(minX, pos.getX());
            minY = Math.min(minY, pos.getY());
            minZ = Math.min(minZ, pos.getZ());

            maxX = Math.max(maxX, pos.getX());
            maxY = Math.max(maxY, pos.getY());
            maxZ = Math.max(maxZ, pos.getZ());
        }

        this.min = new BlockPos(minX, minY, minZ);
        this.max = new BlockPos(maxX, maxY, maxZ);

        this.world.loadPreviewStructure(newStructure);

        if (this.scene == null) {
            this.scene = new PortableSpatialStorageSceneWidget(0, 0, 32, 32, this.world);
            this.root.clearAllWidgets();
            this.root.addWidget(this.scene);
        }

        BlockPos originMarkerPos = BlockPos.ZERO;
        BlockPos floorAnchorPos = BlockPos.ZERO;

        CompoundTag stackTag = stack.getTag();

        if (stackTag != null) {
            originMarkerPos = TemplateUtil.getEnergyOrigin(stackTag);
            floorAnchorPos = TemplateUtil.getEnergyOrigin(stackTag);
        }

        this.renderedCore = computeSurface(newStructure);

        this.scene.setPreview(
                newStructure,
                StructureToolStackState.getPreviewSideMap(stack),
                this.renderedCore,
                originMarkerPos,
                floorAnchorPos
        );

        CompoundTag syncedTag = PortableSpatialStoragePreviewSync.cacheGetRawTag(structureId);
        onPreviewTagLoaded(syncedTag);

        BlockPos size = this.max.subtract(this.min).offset(1, 1, 1);
        BlockPos center = new BlockPos(
                (int) (this.min.getX() + size.getX() * 0.5D),
                (int) (this.min.getY() + size.getY() * 0.5D),
                (int) (this.min.getZ() + size.getZ() * 0.5D)
        );

        this.scene.setCenter(center.getCenter().toVector3f());

        if (!this.initialCameraAlignedToPlayer) {
            alignInitialCameraToPlayer();
        }

        this.scene.setCameraYawAndPitch(this.yaw, this.pitch);
        this.scene.setZoom(this.distance);

        this.lastSceneWidth = -1;
        this.lastSceneHeight = -1;
    }

    protected void clearScene() {
        this.renderedCore = Collections.emptySet();
        this.world.loadPreviewStructure(null);

        if (this.previewStructure != null && !this.previewStructureFromSharedCache) {
            this.previewStructure.close();
        }

        this.previewStructure = null;
        this.previewStructureFromSharedCache = false;

        if (this.scene != null) {
            this.root.clearAllWidgets();
            this.scene = null;
        }

        this.initialCameraAlignedToPlayer = false;
        this.min = BlockPos.ZERO;
        this.max = BlockPos.ZERO;
        this.lastSceneWidth = -1;
        this.lastSceneHeight = -1;

        clearDirectionCompassScene();

        onClearExtraState();
    }

    protected boolean insideScene(double mouseX, double mouseY) {
        return this.scene != null
                && mouseX >= this.scene.getPositionX()
                && mouseY >= this.scene.getPositionY()
                && mouseX < this.scene.getPositionX() + this.scene.getSizeWidth()
                && mouseY < this.scene.getPositionY() + this.scene.getSizeHeight();
    }

    protected static Set<BlockPos> computeSurface(PreviewStructure structure) {
        HashSet<BlockPos> out = new HashSet<>();

        if (structure == null || structure.blocks().isEmpty()) {
            return out;
        }

        Set<BlockPos> all = new HashSet<>();

        for (PreviewBlock block : structure.blocks()) {
            all.add(block.pos());
        }

        for (PreviewBlock block : structure.blocks()) {
            BlockPos pos = block.pos();

            for (Direction direction : DIRECTIONS) {
                if (!all.contains(pos.relative(direction))) {
                    out.add(pos);
                    break;
                }
            }
        }

        return out;
    }

    protected void alignInitialCameraToPlayer() {
        if (this.initialCameraAlignedToPlayer) {
            return;
        }

        var player = Minecraft.getInstance().player;

        if (player == null) {
            return;
        }

        this.yaw = 25.0F;
        this.pitch = Mth.wrapDegrees(player.getYRot() - 90.0F);
        this.initialCameraAlignedToPlayer = true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            onClose();
            return true;
        }
        refreshTransformAroundOriginMode();
        updateTransformButtonTooltips();
        return true;
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        boolean result = super.keyReleased(keyCode, scanCode, modifiers);

        refreshTransformAroundOriginMode();
        updateTransformButtonTooltips();

        return result;
    }

    protected PreviewRect getDirectionCompassRect() {
        PreviewRect preview = getPreviewRect();

        return new PreviewRect(
                preview.x() + preview.width() - DIRECTION_COMPASS_SIZE - 4,
                preview.y() + 4,
                DIRECTION_COMPASS_SIZE,
                DIRECTION_COMPASS_SIZE
        );
    }

    protected void ensureDirectionCompassScene() {
        if (this.directionCompassScene != null) {
            return;
        }

        this.directionCompassWorld = new TrackedDummyWorld();
        this.directionCompassCore = PortableSpatialStorageSceneWidget.buildDirectionCompassStructure(this.directionCompassWorld);

        this.directionCompassScene = new SceneWidget(
                0,
                0,
                DIRECTION_COMPASS_SIZE,
                DIRECTION_COMPASS_SIZE,
                this.directionCompassWorld
        );

        this.directionCompassScene
                .useOrtho(true)
                .setRenderFacing(false)
                .setRenderSelect(false)
                .setDraggable(false)
                .setScalable(false)
                .setIntractable(false)
                .setHoverTips(false)
                .setClearColor(0x00000000);

        this.directionCompassScene.setRenderedCore(this.directionCompassCore);
        this.directionCompassScene.setOrthoRange(DIRECTION_COMPASS_ORTHO_RANGE);
        this.directionCompassScene.setZoom(1.0F);
        this.directionCompassScene.setCenter(new Vector3f(0.5F, 0.75F, 0.5F));
        this.directionCompassScene.setCameraYawAndPitch(this.yaw, this.pitch);

        this.directionCompassRoot.clearAllWidgets();
        this.directionCompassRoot.addWidget(this.directionCompassScene);
    }

    protected void updateDirectionCompassSceneLayoutAndCamera() {
        if (this.directionCompassScene == null) {
            ensureDirectionCompassScene();
        }

        if (this.directionCompassScene == null) {
            return;
        }

        PreviewRect rect = getDirectionCompassRect();

        if (this.directionCompassScene.getPositionX() != rect.x()
                || this.directionCompassScene.getPositionY() != rect.y()
                || this.directionCompassScene.getSizeWidth() != rect.width()
                || this.directionCompassScene.getSizeHeight() != rect.height()) {
            this.directionCompassScene.setSelfPosition(rect.x(), rect.y());
            this.directionCompassScene.setSize(rect.width(), rect.height());
        }

        this.directionCompassScene.setOrthoRange(DIRECTION_COMPASS_ORTHO_RANGE);
        this.directionCompassScene.setZoom(1.0F);
        this.directionCompassScene.setCameraYawAndPitch(this.yaw, this.pitch);
    }

    protected void clearDirectionCompassScene() {
        this.directionCompassRoot.clearAllWidgets();
        this.directionCompassScene = null;
        this.directionCompassWorld = null;
        this.directionCompassCore = Collections.emptySet();
    }

    private void renderPowerUpgradePanelWidget(GuiGraphics graphics) {
        this.powerUpgradePanel.setScreenPosition(this.leftPos, this.topPos);
        this.powerUpgradePanel.setSlots(
                getMenu().getPowerUpgradeSlotCount(),
                getMenu().getMaxUpgradesCount()
        );
        this.powerUpgradePanel.renderBackground(graphics);
        this.powerUpgradePanel.renderCraftingSlotBadge(
                graphics,
                getMenu().hasCraftingUpgradeInstalled()
        );
    }

    static List<Component> buildCompatibleUpgradesTooltip() {
        List<Component> lines = new ArrayList<>();

        lines.add(Component.translatable(
                LangDefs.VALID_UPGRADES.getTranslationKey()
        ).withStyle(ChatFormatting.WHITE));

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

    private static Component modDisplayName(String modId) {
        return ModList.get()
                .getModContainerById(modId)
                .map(container -> Component.literal(container.getModInfo().getDisplayName()))
                .orElse(Component.literal(modId));
    }

    protected Component getEmptyPreviewTitle(ItemStack stack) {
        if (!stack.isEmpty() && !StructureToolStackState.getStructureId(stack).isBlank()) {
            return Component.translatable(LangDefs.PREVIEW_EMPTY_LOADING.getTranslationKey());
        }

        return Component.translatable(LangDefs.PREVIEW_EMPTY_NO_STRUCTURE.getTranslationKey());
    }

    protected Component getEmptyPreviewHint(ItemStack stack) {
        if (!stack.isEmpty() && !StructureToolStackState.getStructureId(stack).isBlank()) {
            return Component.translatable(LangDefs.PREVIEW_EMPTY_SYNC_HINT.getTranslationKey());
        }

        return Component.translatable(LangDefs.PREVIEW_EMPTY_CAPTURE_HINT.getTranslationKey());
    }

    protected void renderEmptyPreviewMessage(GuiGraphics graphics) {
        if (this.scene != null) {
            return;
        }

        PreviewRect rect = getPreviewRect();
        ItemStack stack = findRelevantStack();

        Component title = getEmptyPreviewTitle(stack);
        Component hint = getEmptyPreviewHint(stack);

        int centerX = rect.x() + rect.width() / 2;
        int centerY = rect.y() + rect.height() / 2;
        int textWidth = Math.max(24, rect.width() - 16);

        graphics.fill(
                rect.x(),
                rect.y(),
                rect.x() + rect.width(),
                rect.y() + rect.height(),
                0x66000000
        );

        int titleHeight = getWrappedTextHeight(title, textWidth, 10);
        int hintHeight = getWrappedTextHeight(hint, textWidth, 9);
        int totalHeight = titleHeight + 6 + hintHeight;

        int y = centerY - totalHeight / 2;

        y = drawCenteredWrappedText(
                graphics,
                title,
                centerX,
                y,
                textWidth,
                0xFFE0E0E0,
                10
        );

        y += 6;

        drawCenteredWrappedText(
                graphics,
                hint,
                centerX,
                y,
                textWidth,
                0xFF9A9A9A,
                9
        );
    }

    private int getWrappedTextHeight(Component text, int width, int lineHeight) {
        return this.font.split(text, width).size() * lineHeight;
    }

    private int drawCenteredWrappedText(
            GuiGraphics graphics,
            Component text,
            int centerX,
            int y,
            int width,
            int color,
            int lineHeight
    ) {
        for (var line : this.font.split(text, width)) {
            graphics.drawCenteredString(
                    this.font,
                    line,
                    centerX,
                    y,
                    color
            );

            y += lineHeight;
        }

        return y;
    }
}