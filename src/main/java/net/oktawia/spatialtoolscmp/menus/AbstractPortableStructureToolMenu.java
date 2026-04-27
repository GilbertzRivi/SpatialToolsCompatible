package net.oktawia.spatialtoolscmp.menus;

import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.SlotItemHandler;
import net.oktawia.spatialtoolscmp.client.misc.widgets.PowerUpgradePanelWidget;
import net.oktawia.spatialtoolscmp.items.AbstractStructureCaptureToolItem;
import net.oktawia.spatialtoolscmp.network.NetworkHandler;
import net.oktawia.spatialtoolscmp.network.packets.SendLongStringToClientPacket;
import net.oktawia.spatialtoolscmp.util.TemplateUtil;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public abstract class AbstractPortableStructureToolMenu extends AbstractContainerMenu {

    protected static final int BUTTON_REQUEST_PREVIEW = 0;
    protected static final int BUTTON_FLIP_VERTICAL = 1;
    protected static final int BUTTON_ROTATE_CLOCKWISE = 2;
    protected static final int BUTTON_FLIP_EAST_WEST = 3;
    protected static final int BUTTON_FLIP_NORTH_SOUTH = 4;
    protected static final int BUTTON_FLIP_EAST_WEST_AROUND_ORIGIN = 5;
    protected static final int BUTTON_FLIP_NORTH_SOUTH_AROUND_ORIGIN = 6;
    protected static final int BUTTON_FLIP_VERTICAL_AROUND_ORIGIN = 7;
    protected static final int BUTTON_ROTATE_CLOCKWISE_AROUND_ORIGIN = 8;

    protected static final int BUTTON_OFFSET_LEFT = 20;
    protected static final int BUTTON_OFFSET_RIGHT = 21;
    protected static final int BUTTON_OFFSET_UP = 22;
    protected static final int BUTTON_OFFSET_DOWN = 23;
    protected static final int BUTTON_OFFSET_FRONT = 24;
    protected static final int BUTTON_OFFSET_BACK = 25;

    protected static final int POWER_UPGRADE_SLOT_X = PowerUpgradePanelWidget.SLOT_LEFT;
    protected static final int POWER_UPGRADE_SLOT_Y = PowerUpgradePanelWidget.SLOT_TOP;
    protected static final int POWER_UPGRADE_SLOT_STEP = PowerUpgradePanelWidget.SLOT_STEP;

    protected static final int PLAYER_SLOT_COUNT = 36;

    protected static final String PREVIEW_SIDE_MAP_KEY = "crazy_preview_side_map";
    protected static final int CHUNK_SIZE = 1_000_000;

    protected final Inventory playerInventory;

    @Getter
    protected final Player player;

    protected final ItemStack toolStack;
    protected final int toolSlotIndex;

    protected AbstractPortableStructureToolMenu(
            MenuType<?> menuType,
            int id,
            Inventory playerInventory,
            ItemStack toolStack
    ) {
        super(menuType, id);

        this.playerInventory = playerInventory;
        this.player = playerInventory.player;
        this.toolStack = toolStack;
        this.toolSlotIndex = findToolSlotIndex(playerInventory, toolStack);

        createPlayerInventorySlots(playerInventory);
        addPowerUpgradeSlots();

        if (!isClientSide()) {
            requestPreview();
        }
    }

    protected boolean isClientSide() {
        return this.player.level().isClientSide();
    }

    public ItemStack getItemStack() {
        return this.toolStack;
    }

    protected abstract boolean hasStoredStructure();

    protected abstract byte[] getStructureBytes();

    protected abstract void setStructureBytes(byte[] bytes);

    @Override
    public boolean stillValid(Player player) {
        if (this.toolStack.isEmpty()) {
            return false;
        }

        if (this.toolSlotIndex >= 0 && this.toolSlotIndex < player.getInventory().getContainerSize()) {
            ItemStack current = player.getInventory().getItem(this.toolSlotIndex);
            return current == this.toolStack || ItemStack.matches(current, this.toolStack);
        }

        if (player.getMainHandItem() == this.toolStack || player.getOffhandItem() == this.toolStack) {
            return true;
        }

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);

            if (stack == this.toolStack) {
                return true;
            }
        }

        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= this.slots.size()) {
            return ItemStack.EMPTY;
        }

        Slot slot = this.slots.get(index);

        if (!slot.hasItem() || slot instanceof LockedSlot) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();

        int upgradeStart = PLAYER_SLOT_COUNT;
        int energyUpgradeStart = upgradeStart;
        int energyUpgradeEnd = upgradeStart + getMaxUpgradesCount();
        int craftingUpgradeIndex = getCraftingUpgradeMenuSlotIndex();

        if (index < PLAYER_SLOT_COUNT) {
            if (AbstractStructureCaptureToolItem.isValidPowerUpgradeItem(stack)) {
                if (!moveItemStackTo(stack, energyUpgradeStart, energyUpgradeEnd, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (
                    craftingUpgradeIndex >= 0
                            && AbstractStructureCaptureToolItem.isValidCraftingUpgradeItem(stack)
            ) {
                if (!moveItemStackTo(stack, craftingUpgradeIndex, craftingUpgradeIndex + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else {
                return ItemStack.EMPTY;
            }
        } else {
            if (!moveItemStackTo(stack, 0, PLAYER_SLOT_COUNT, false)) {
                return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }

        slot.onTake(player, stack);

        return original;
    }

    public int getCraftingUpgradeSlotIndex() {
        ItemStack stack = getItemStack();

        if (!(stack.getItem() instanceof AbstractStructureCaptureToolItem toolItem)) {
            return -1;
        }

        return toolItem.getCraftingUpgradeSlotIndex();
    }

    public int getCraftingUpgradeMenuSlotIndex() {
        int logicalSlot = getCraftingUpgradeSlotIndex();

        if (logicalSlot < 0) {
            return -1;
        }

        return PLAYER_SLOT_COUNT + logicalSlot;
    }

    public boolean hasCraftingUpgradeInstalled() {
        ItemStack stack = getItemStack();

        if (!(stack.getItem() instanceof AbstractStructureCaptureToolItem toolItem)) {
            return false;
        }

        return toolItem.hasInstalledCraftingUpgrade(stack);
    }

    private static int findToolSlotIndex(Inventory inventory, ItemStack toolStack) {
        if (toolStack.isEmpty()) {
            return -1;
        }

        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (inventory.getItem(i) == toolStack) {
                return i;
            }
        }

        for (int i = 0; i < inventory.getContainerSize(); i++) {
            if (ItemStack.matches(inventory.getItem(i), toolStack)) {
                return i;
            }
        }

        return -1;
    }

    protected void createPlayerInventorySlots(Inventory inventory) {
        int inventoryLeft = 47;
        int inventoryTop = 174;

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                int index = col + row * 9 + 9;

                addSlot(createPlayerSlot(
                        inventory,
                        index,
                        inventoryLeft + col * 18,
                        inventoryTop + row * 18
                ));
            }
        }

        int hotbarLeft = 47;
        int hotbarTop = 232;

        for (int col = 0; col < 9; col++) {
            addSlot(createPlayerSlot(
                    inventory,
                    col,
                    hotbarLeft + col * 18,
                    hotbarTop
            ));
        }
    }

    protected Slot createPlayerSlot(Inventory inventory, int index, int x, int y) {
        if (index == this.toolSlotIndex) {
            return new LockedSlot(inventory, index, x, y);
        }

        return new Slot(inventory, index, x, y);
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (player != this.player) {
            return false;
        }

        switch (buttonId) {
            case BUTTON_REQUEST_PREVIEW -> requestPreviewServer();
            case BUTTON_FLIP_VERTICAL -> flipVerticalServer();
            case BUTTON_ROTATE_CLOCKWISE -> rotateClockwiseServer(1);
            case BUTTON_FLIP_EAST_WEST -> flipEastWestServer();
            case BUTTON_FLIP_NORTH_SOUTH -> flipNorthSouthServer();
            case BUTTON_FLIP_EAST_WEST_AROUND_ORIGIN -> flipEastWestAroundOriginServer();
            case BUTTON_FLIP_NORTH_SOUTH_AROUND_ORIGIN -> flipNorthSouthAroundOriginServer();
            case BUTTON_FLIP_VERTICAL_AROUND_ORIGIN -> flipVerticalAroundOriginServer();
            case BUTTON_ROTATE_CLOCKWISE_AROUND_ORIGIN -> rotateClockwiseAroundOriginServer(1);

            case BUTTON_OFFSET_LEFT -> offsetLeftServer();
            case BUTTON_OFFSET_RIGHT -> offsetRightServer();
            case BUTTON_OFFSET_UP -> offsetUpServer();
            case BUTTON_OFFSET_DOWN -> offsetDownServer();
            case BUTTON_OFFSET_FRONT -> offsetFrontServer();
            case BUTTON_OFFSET_BACK -> offsetBackServer();

            default -> {
                return false;
            }
        }

        return true;
    }

    public void requestPreview() {
        if (isClientSide()) {
            sendButtonToServer(BUTTON_REQUEST_PREVIEW);
            return;
        }

        requestPreviewServer();
    }

    protected void requestPreviewServer() {
        if (!hasStoredStructure()) {
            clearItemPreviewMirror();
            sendPreviewString("");
            return;
        }

        byte[] bytes = getStructureBytes();

        if (bytes == null || bytes.length == 0) {
            clearItemPreviewMirror();
            sendPreviewString("");
            return;
        }

        try {
            CompoundTag tag = TemplateUtil.decompressNbt(bytes);
            syncItemPreviewMirror(tag);
            sendPreviewString(TemplateUtil.toBase64(bytes));
        } catch (Exception ignored) {
            clearItemPreviewMirror();
            sendPreviewString("");
        }
    }

    public void flipVertical() {
        if (isClientSide()) {
            sendButtonToServer(BUTTON_FLIP_VERTICAL);
            return;
        }

        flipVerticalServer();
    }

    protected void flipVerticalServer() {
        if (!hasStoredStructure()) {
            return;
        }

        applyTransformAndResend(
                TemplateUtil::applyFlipVToTag,
                buildVerticalFlipSideMap()
        );
    }

    public void rotateClockwise(Integer times) {
        int turns = times == null ? 1 : times;

        if (isClientSide()) {
            sendButtonToServer(BUTTON_ROTATE_CLOCKWISE);
            return;
        }

        rotateClockwiseServer(turns);
    }

    protected void rotateClockwiseServer(int turns) {
        int normalized = normalizeQuarterTurns(turns);

        if (!hasStoredStructure() || normalized == 0) {
            return;
        }

        applyTransformAndResend(
                tag -> TemplateUtil.applyRotateCWToTag(tag, turns),
                buildRotationSideMap(normalized)
        );
    }

    public void flipVerticalAroundOrigin() {
        if (isClientSide()) {
            sendButtonToServer(BUTTON_FLIP_VERTICAL_AROUND_ORIGIN);
            return;
        }

        flipVerticalAroundOriginServer();
    }

    protected void flipVerticalAroundOriginServer() {
        if (!hasStoredStructure()) {
            return;
        }

        applyTransformAndResend(
                TemplateUtil::applyFlipVAroundOriginToTag,
                buildVerticalFlipSideMap()
        );
    }

    public void rotateClockwiseAroundOrigin(Integer times) {
        int turns = times == null ? 1 : times;

        if (isClientSide()) {
            sendButtonToServer(BUTTON_ROTATE_CLOCKWISE_AROUND_ORIGIN);
            return;
        }

        rotateClockwiseAroundOriginServer(turns);
    }

    protected void rotateClockwiseAroundOriginServer(int turns) {
        int normalized = normalizeQuarterTurns(turns);

        if (!hasStoredStructure() || normalized == 0) {
            return;
        }

        applyTransformAndResend(
                tag -> TemplateUtil.applyRotateCWAroundOriginToTag(tag, turns),
                buildRotationSideMap(normalized)
        );
    }

    public void flipEastWest() {
        if (isClientSide()) {
            sendButtonToServer(BUTTON_FLIP_EAST_WEST);
            return;
        }

        flipEastWestServer();
    }

    protected void flipEastWestServer() {
        if (!hasStoredStructure()) {
            return;
        }

        applyTransformAndResend(
                TemplateUtil::applyFlipEastWestToTag,
                buildEastWestFlipSideMap()
        );
    }

    public void flipNorthSouth() {
        if (isClientSide()) {
            sendButtonToServer(BUTTON_FLIP_NORTH_SOUTH);
            return;
        }

        flipNorthSouthServer();
    }

    protected void flipNorthSouthServer() {
        if (!hasStoredStructure()) {
            return;
        }

        applyTransformAndResend(
                TemplateUtil::applyFlipNorthSouthToTag,
                buildNorthSouthFlipSideMap()
        );
    }

    public void flipEastWestAroundOrigin() {
        if (isClientSide()) {
            sendButtonToServer(BUTTON_FLIP_EAST_WEST_AROUND_ORIGIN);
            return;
        }

        flipEastWestAroundOriginServer();
    }

    protected void flipEastWestAroundOriginServer() {
        if (!hasStoredStructure()) {
            return;
        }

        applyTransformAndResend(
                TemplateUtil::applyFlipEastWestAroundOriginToTag,
                buildEastWestFlipSideMap()
        );
    }

    public void flipNorthSouthAroundOrigin() {
        if (isClientSide()) {
            sendButtonToServer(BUTTON_FLIP_NORTH_SOUTH_AROUND_ORIGIN);
            return;
        }

        flipNorthSouthAroundOriginServer();
    }

    protected void flipNorthSouthAroundOriginServer() {
        if (!hasStoredStructure()) {
            return;
        }

        applyTransformAndResend(
                TemplateUtil::applyFlipNorthSouthAroundOriginToTag,
                buildNorthSouthFlipSideMap()
        );
    }

    public void offsetLeft() {
        if (isClientSide()) {
            sendButtonToServer(BUTTON_OFFSET_LEFT);
            return;
        }

        offsetLeftServer();
    }

    protected void offsetLeftServer() {
        applyOffsetAndResend(-1, 0, 0);
    }

    public void offsetRight() {
        if (isClientSide()) {
            sendButtonToServer(BUTTON_OFFSET_RIGHT);
            return;
        }

        offsetRightServer();
    }

    protected void offsetRightServer() {
        applyOffsetAndResend(1, 0, 0);
    }

    public void offsetUp() {
        if (isClientSide()) {
            sendButtonToServer(BUTTON_OFFSET_UP);
            return;
        }

        offsetUpServer();
    }

    protected void offsetUpServer() {
        applyOffsetAndResend(0, 1, 0);
    }

    public void offsetDown() {
        if (isClientSide()) {
            sendButtonToServer(BUTTON_OFFSET_DOWN);
            return;
        }

        offsetDownServer();
    }

    protected void offsetDownServer() {
        applyOffsetAndResend(0, -1, 0);
    }

    public void offsetFront() {
        if (isClientSide()) {
            sendButtonToServer(BUTTON_OFFSET_FRONT);
            return;
        }

        offsetFrontServer();
    }

    protected void offsetFrontServer() {
        applyOffsetAndResend(0, 0, -1);
    }

    public void offsetBack() {
        if (isClientSide()) {
            sendButtonToServer(BUTTON_OFFSET_BACK);
            return;
        }

        offsetBackServer();
    }

    protected void offsetBackServer() {
        applyOffsetAndResend(0, 0, 1);
    }

    @OnlyIn(Dist.CLIENT)
    protected void sendButtonToServer(int buttonId) {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.gameMode == null) {
            return;
        }

        minecraft.gameMode.handleInventoryButtonClick(this.containerId, buttonId);
    }

    @FunctionalInterface
    protected interface TagTransform {
        CompoundTag apply(CompoundTag tag);
    }

    protected void applyTransformAndResend(TagTransform transform, int[] appliedSideMap) {
        byte[] bytes = getStructureBytes();

        if (bytes == null || bytes.length == 0) {
            return;
        }

        try {
            CompoundTag tag = TemplateUtil.decompressNbt(bytes);
            CompoundTag transformed = transform.apply(tag);

            setStructureBytes(TemplateUtil.compressNbt(transformed));
            updatePreviewSideMap(appliedSideMap);
            syncItemPreviewMirror(transformed);
        } catch (Exception ignored) {
            return;
        }

        requestPreviewServer();
    }

    protected void applyOffsetAndResend(int dx, int dy, int dz) {
        byte[] bytes = getStructureBytes();

        if (bytes == null || bytes.length == 0) {
            return;
        }

        try {
            CompoundTag tag = TemplateUtil.decompressNbt(bytes);
            CompoundTag transformed = TemplateUtil.applyOffsetToTag(tag, dx, dy, dz);

            setStructureBytes(TemplateUtil.compressNbt(transformed));
            syncItemPreviewMirror(transformed);
        } catch (Exception ignored) {
            return;
        }

        requestPreviewServer();
    }

    protected void syncItemPreviewMirror(CompoundTag structureTag) {
        CompoundTag stackTag = getItemStack().getOrCreateTag();
        TemplateUtil.copyPreviewTransformState(structureTag, stackTag);
    }

    protected void clearItemPreviewMirror() {
        CompoundTag stackTag = getItemStack().getOrCreateTag();

        TemplateUtil.setTemplateOffset(stackTag, BlockPos.ZERO);
        TemplateUtil.setEnergyOrigin(stackTag, BlockPos.ZERO);
    }

    protected int normalizeQuarterTurns(int turns) {
        return ((turns % 4) + 4) % 4;
    }

    protected void sendPreviewString(String base64) {
        if (!(getPlayer() instanceof ServerPlayer serverPlayer)) {
            return;
        }

        NetworkHandler.sendToPlayer(serverPlayer, new SendLongStringToClientPacket("__RESET__"));

        if (base64 != null && !base64.isEmpty()) {
            byte[] bytes = base64.getBytes(StandardCharsets.UTF_8);
            int total = (int) Math.ceil((double) bytes.length / CHUNK_SIZE);

            for (int i = 0; i < total; i++) {
                int start = i * CHUNK_SIZE;
                int end = Math.min(bytes.length, (i + 1) * CHUNK_SIZE);
                byte[] part = Arrays.copyOfRange(bytes, start, end);

                NetworkHandler.sendToPlayer(
                        serverPlayer,
                        new SendLongStringToClientPacket(new String(part, StandardCharsets.UTF_8))
                );
            }
        }

        NetworkHandler.sendToPlayer(serverPlayer, new SendLongStringToClientPacket("__END__"));
    }

    protected void updatePreviewSideMap(int[] appliedSideMap) {
        CompoundTag stackTag = getItemStack().getOrCreateTag();

        int[] current = readPreviewSideMap(stackTag);
        int[] combined = composeSideMaps(current, appliedSideMap);

        writePreviewSideMap(stackTag, combined);
    }

    protected int[] readPreviewSideMap(CompoundTag tag) {
        int[] identity = identitySideMap();

        if (tag == null || !tag.contains(PREVIEW_SIDE_MAP_KEY, Tag.TAG_INT_ARRAY)) {
            return identity;
        }

        int[] raw = tag.getIntArray(PREVIEW_SIDE_MAP_KEY);

        if (raw.length != Direction.values().length) {
            return identity;
        }

        for (Direction side : Direction.values()) {
            int mapped = raw[side.ordinal()];

            if (mapped < 0 || mapped >= Direction.values().length) {
                return identity;
            }
        }

        return raw;
    }

    protected void writePreviewSideMap(CompoundTag tag, int[] sideMap) {
        tag.putIntArray(PREVIEW_SIDE_MAP_KEY, sideMap);
    }

    protected int[] composeSideMaps(int[] current, int[] applied) {
        int[] result = new int[Direction.values().length];

        for (Direction side : Direction.values()) {
            int currentMapped = current[side.ordinal()];
            result[side.ordinal()] = applied[currentMapped];
        }

        return result;
    }

    protected int[] identitySideMap() {
        int[] map = new int[Direction.values().length];

        for (Direction side : Direction.values()) {
            map[side.ordinal()] = side.ordinal();
        }

        return map;
    }

    protected int[] buildRotationSideMap(int normalizedQuarterTurns) {
        int[] map = identitySideMap();

        for (Direction side : Direction.values()) {
            Direction mapped = side;

            for (int i = 0; i < normalizedQuarterTurns; i++) {
                mapped = rotateClockwiseSide(mapped);
            }

            map[side.ordinal()] = mapped.ordinal();
        }

        return map;
    }

    protected Direction rotateClockwiseSide(Direction side) {
        return switch (side) {
            case NORTH -> Direction.EAST;
            case EAST -> Direction.SOUTH;
            case SOUTH -> Direction.WEST;
            case WEST -> Direction.NORTH;
            case UP -> Direction.UP;
            case DOWN -> Direction.DOWN;
        };
    }

    protected int[] buildVerticalFlipSideMap() {
        int[] map = identitySideMap();

        map[Direction.UP.ordinal()] = Direction.DOWN.ordinal();
        map[Direction.DOWN.ordinal()] = Direction.UP.ordinal();

        return map;
    }

    protected int[] buildEastWestFlipSideMap() {
        int[] map = identitySideMap();

        map[Direction.EAST.ordinal()] = Direction.WEST.ordinal();
        map[Direction.WEST.ordinal()] = Direction.EAST.ordinal();

        return map;
    }

    protected int[] buildNorthSouthFlipSideMap() {
        int[] map = identitySideMap();

        map[Direction.NORTH.ordinal()] = Direction.SOUTH.ordinal();
        map[Direction.SOUTH.ordinal()] = Direction.NORTH.ordinal();

        return map;
    }

    public int getPowerUpgradeSlotCount() {
        ItemStack stack = getItemStack();

        if (!(stack.getItem() instanceof AbstractStructureCaptureToolItem toolItem)) {
            return 0;
        }

        return toolItem.getPowerUpgradeSlots();
    }

    public int getMaxUpgradesCount() {
        ItemStack stack = getItemStack();

        if (!(stack.getItem() instanceof AbstractStructureCaptureToolItem toolItem)) {
            return 0;
        }

        return toolItem.getMaxPowerUpgrades();
    }

    protected void addPowerUpgradeSlots() {
        ItemStack stack = getItemStack();

        if (!(stack.getItem() instanceof AbstractStructureCaptureToolItem toolItem)) {
            return;
        }

        int slots = toolItem.getPowerUpgradeSlots();

        if (slots <= 0) {
            return;
        }

        stack.getCapability(ForgeCapabilities.ITEM_HANDLER).ifPresent(handler -> {
            for (int slot = 0; slot < slots; slot++) {
                addSlot(new UpgradeSlot(
                        handler,
                        slot,
                        POWER_UPGRADE_SLOT_X,
                        POWER_UPGRADE_SLOT_Y + slot * POWER_UPGRADE_SLOT_STEP,
                        toolItem.isCraftingUpgradeSlot(slot)
                ));
            }
        });
    }

    public boolean isPowerUpgradeSlot(@Nullable Slot slot) {
        return slot instanceof UpgradeSlot upgradeSlot && upgradeSlot.isPowerUpgradeSlot();
    }

    public boolean isCraftingUpgradeSlot(@Nullable Slot slot) {
        return slot instanceof UpgradeSlot upgradeSlot && upgradeSlot.isCraftingUpgradeSlot();
    }

    private static final class UpgradeSlot extends SlotItemHandler {

        private final boolean craftingUpgradeSlot;

        private UpgradeSlot(IItemHandler itemHandler, int index, int x, int y, boolean craftingUpgradeSlot) {
            super(itemHandler, index, x, y);
            this.craftingUpgradeSlot = craftingUpgradeSlot;
        }

        private boolean isPowerUpgradeSlot() {
            return !this.craftingUpgradeSlot;
        }

        private boolean isCraftingUpgradeSlot() {
            return this.craftingUpgradeSlot;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public int getMaxStackSize(ItemStack stack) {
            return 1;
        }
    }

    private static class LockedSlot extends Slot {

        private LockedSlot(Inventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Override
        public boolean mayPickup(Player player) {
            return false;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }
}