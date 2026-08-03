package net.oktawia.spatialtoolscmp.network.packets;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.network.NetworkEvent;
import net.oktawia.spatialtoolscmp.defs.LangDefs;
import net.oktawia.spatialtoolscmp.items.AbstractStructureCaptureToolItem;
import net.oktawia.spatialtoolscmp.items.PortableSpatialCloner;
import net.oktawia.spatialtoolscmp.items.PortableSpatialPiper;
import net.oktawia.spatialtoolscmp.items.PortableSpatialReplacer;
import net.oktawia.spatialtoolscmp.items.PortableSpatialStorage;
import net.oktawia.spatialtoolscmp.logic.ClonerStructureLibraryStore;
import net.oktawia.spatialtoolscmp.logic.StructureToolPreviewDispatcher;
import net.oktawia.spatialtoolscmp.logic.StructureToolStackState;
import net.oktawia.spatialtoolscmp.logic.StructureToolStructureStore;
import net.oktawia.spatialtoolscmp.logic.StructureToolUtil;
import net.oktawia.spatialtoolscmp.util.TemplateUtil;

import java.util.UUID;
import java.util.function.Supplier;

public class StructureToolContextActionPacket {

    private static final String PREVIEW_SIDE_MAP_KEY = StructureToolStackState.TAG_PREVIEW_SIDE_MAP;

    public static final int OFFSET_LEFT = 1;
    public static final int OFFSET_RIGHT = 2;
    public static final int OFFSET_UP = 3;
    public static final int OFFSET_DOWN = 4;
    public static final int OFFSET_FRONT = 5;
    public static final int OFFSET_BACK = 6;

    public static final int ROTATE_CLOCKWISE = 20;
    public static final int FLIP_EAST_WEST = 21;
    public static final int FLIP_NORTH_SOUTH = 22;
    public static final int FLIP_VERTICAL = 23;

    public static final int CYCLE_NESTED_ITEMS = 40;

    public static final int TOGGLE_ANCHOR = 60;
    public static final int TOGGLE_SELECTION_MODE = 61;
    public static final int CANCEL_SELECTION = 62;

    public static final int MOVE_SELECTION_RED_WEST = 80;
    public static final int MOVE_SELECTION_RED_EAST = 81;
    public static final int MOVE_SELECTION_RED_NORTH = 82;
    public static final int MOVE_SELECTION_RED_SOUTH = 83;
    public static final int MOVE_SELECTION_RED_UP = 84;
    public static final int MOVE_SELECTION_RED_DOWN = 85;

    public static final int MOVE_SELECTION_GREEN_WEST = 90;
    public static final int MOVE_SELECTION_GREEN_EAST = 91;
    public static final int MOVE_SELECTION_GREEN_NORTH = 92;
    public static final int MOVE_SELECTION_GREEN_SOUTH = 93;
    public static final int MOVE_SELECTION_GREEN_UP = 94;
    public static final int MOVE_SELECTION_GREEN_DOWN = 95;

    public static final int REPLACER_RADIUS_UP = 100;
    public static final int REPLACER_RADIUS_DOWN = 101;
    public static final int REPLACER_TOGGLE_CONNECTIVITY = 102;
    public static final int REPLACER_TOGGLE_BLOCKSTATE = 103;

    public static final int PIPER_TOGGLE_FILL_MODE = 110;
    public static final int PIPER_CLEAR_TARGET = 111;
    public static final int PIPER_CYCLE_PIPE_DIRECTION = 112;

    private final int action;
    private final boolean aroundOrigin;

    public StructureToolContextActionPacket(int action, boolean aroundOrigin) {
        this.action = action;
        this.aroundOrigin = aroundOrigin;
    }

    public static void encode(StructureToolContextActionPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.action);
        buf.writeBoolean(packet.aroundOrigin);
    }

    public static StructureToolContextActionPacket decode(FriendlyByteBuf buf) {
        return new StructureToolContextActionPacket(
                buf.readVarInt(),
                buf.readBoolean()
        );
    }

    public static void handle(
            StructureToolContextActionPacket packet,
            Supplier<NetworkEvent.Context> contextSupplier
    ) {
        NetworkEvent.Context context = contextSupplier.get();

        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();

            if (player == null) {
                return;
            }

            ItemStack stack = player.getMainHandItem();

            if (stack.isEmpty()) {
                return;
            }

            if (packet.action == CYCLE_NESTED_ITEMS) {
                if (stack.getItem() instanceof PortableSpatialCloner) {
                    PortableSpatialCloner.cycleNestedInventoryResourceMode(stack);
                    syncStack(player);
                }

                return;
            }

            if (packet.action == REPLACER_RADIUS_UP
                    || packet.action == REPLACER_RADIUS_DOWN
                    || packet.action == REPLACER_TOGGLE_CONNECTIVITY
                    || packet.action == REPLACER_TOGGLE_BLOCKSTATE) {
                if (stack.getItem() instanceof PortableSpatialReplacer) {
                    switch (packet.action) {
                        case REPLACER_RADIUS_UP ->
                                PortableSpatialReplacer.setRadius(stack, PortableSpatialReplacer.getRadius(stack) + 1);
                        case REPLACER_RADIUS_DOWN ->
                                PortableSpatialReplacer.setRadius(stack, PortableSpatialReplacer.getRadius(stack) - 1);
                        case REPLACER_TOGGLE_CONNECTIVITY ->
                                PortableSpatialReplacer.cycleConnectivityMode(stack);
                        case REPLACER_TOGGLE_BLOCKSTATE ->
                                PortableSpatialReplacer.toggleSameBlockstate(stack);
                    }
                    syncStack(player);
                }
                return;
            }

            if (packet.action == PIPER_TOGGLE_FILL_MODE) {
                if (stack.getItem() instanceof PortableSpatialPiper) {
                    PortableSpatialPiper.cycleFillMode(stack);
                    syncStack(player);
                }

                return;
            }

            if (packet.action == PIPER_CYCLE_PIPE_DIRECTION) {
                if (stack.getItem() instanceof PortableSpatialPiper) {
                    PortableSpatialPiper.cyclePipeDirectionMode(stack);
                    syncStack(player);
                }

                return;
            }

            if (packet.action == PIPER_CLEAR_TARGET) {
                if (stack.getItem() instanceof PortableSpatialPiper) {
                    PortableSpatialPiper.setTargetBlock(stack, ItemStack.EMPTY);
                    PortableSpatialPiper.clearRoute(stack);
                    syncStack(player);
                }

                return;
            }

            if (packet.action == TOGGLE_SELECTION_MODE) {
                if (stack.getItem() instanceof AbstractStructureCaptureToolItem) {
                    StructureToolStackState.cycleSelectionMode(stack);
                    syncStack(player);
                }

                return;
            }

            if (!(stack.getItem() instanceof PortableSpatialStorage)
                    && !(stack.getItem() instanceof PortableSpatialCloner)) {
                return;
            }

            if (packet.action == CANCEL_SELECTION) {
                cancelSelection(player, stack);
                return;
            }

            if (isMoveSelectionAction(packet.action)) {
                moveSelectionCorner(player, stack, packet.action);
                return;
            }

            if (packet.action == TOGGLE_ANCHOR) {
                toggleAnchor(player, stack);
                return;
            }

            applyStructureAction(player, stack, packet.action, packet.aroundOrigin);
        });

        context.setPacketHandled(true);
    }

    private static void cancelSelection(ServerPlayer player, ItemStack stack) {
        StructureToolStackState.clearSelection(stack);
        StructureToolStackState.resetPreviewSideMap(stack);

        CompoundTag tag = stack.getTag();

        if (tag != null) {
            tag.remove("selectionDimension");
        }

        syncStack(player);
        StructureToolPreviewDispatcher.sendPreviewToPlayer(player, null);
    }

    private static void toggleAnchor(ServerPlayer player, ItemStack stack) {
        if (StructureToolStackState.isAnchorEnabled(stack)) {
            StructureToolStackState.clearAnchor(stack);
            syncStack(player);
            return;
        }

        BlockPos anchorPos = getCurrentPasteOrigin(player);

        if (anchorPos == null) {
            player.displayClientMessage(
                    Component.translatable(LangDefs.NO_BLOCK_IN_RANGE.getTranslationKey()),
                    true
            );
            return;
        }

        StructureToolStackState.setAnchor(
                stack,
                player.level().dimension(),
                anchorPos
        );

        syncStack(player);
    }

    private static BlockPos getCurrentPasteOrigin(ServerPlayer player) {
        BlockHitResult hit = StructureToolUtil.rayTrace(player.level(), player, 50.0D);

        if (hit.getType() != HitResult.Type.BLOCK) {
            return null;
        }

        return hit.getBlockPos().relative(hit.getDirection()).immutable();
    }

    private static boolean isMoveSelectionAction(int action) {
        return (action >= MOVE_SELECTION_RED_WEST && action <= MOVE_SELECTION_RED_DOWN)
                || (action >= MOVE_SELECTION_GREEN_WEST && action <= MOVE_SELECTION_GREEN_DOWN);
    }

    private static void moveSelectionCorner(ServerPlayer player, ItemStack stack, int action) {
        boolean green = action >= MOVE_SELECTION_GREEN_WEST;

        int normalized = green
                ? action - MOVE_SELECTION_GREEN_WEST
                : action - MOVE_SELECTION_RED_WEST;

        int dx = 0;
        int dy = 0;
        int dz = 0;

        switch (normalized) {
            case 0 -> dx = -1;
            case 1 -> dx = 1;
            case 2 -> dz = -1;
            case 3 -> dz = 1;
            case 4 -> dy = 1;
            case 5 -> dy = -1;
            default -> {
                return;
            }
        }

        if (green) {
            StructureToolStackState.moveSelectionB(stack, dx, dy, dz);
        } else {
            StructureToolStackState.moveSelectionA(stack, dx, dy, dz);
        }

        syncStack(player);
    }

    private static void applyStructureAction(
            ServerPlayer player,
            ItemStack stack,
            int action,
            boolean aroundOrigin
    ) {
        String id = StructureToolStackState.getStructureId(stack);

        if (id == null || id.isBlank()) {
            return;
        }

        CompoundTag tag = loadStructure(player, stack, id);

        if (tag == null || tag.isEmpty()) {
            return;
        }

        CompoundTag transformed = switch (action) {
            case OFFSET_LEFT -> TemplateUtil.applyOffsetToTag(tag, -1, 0, 0);
            case OFFSET_RIGHT -> TemplateUtil.applyOffsetToTag(tag, 1, 0, 0);
            case OFFSET_UP -> TemplateUtil.applyOffsetToTag(tag, 0, 1, 0);
            case OFFSET_DOWN -> TemplateUtil.applyOffsetToTag(tag, 0, -1, 0);
            case OFFSET_FRONT -> TemplateUtil.applyOffsetToTag(tag, 0, 0, -1);
            case OFFSET_BACK -> TemplateUtil.applyOffsetToTag(tag, 0, 0, 1);

            case ROTATE_CLOCKWISE -> aroundOrigin
                    ? TemplateUtil.applyRotateCWAroundOriginToTag(tag, 1)
                    : TemplateUtil.applyRotateCWToTag(tag, 1);

            case FLIP_EAST_WEST -> aroundOrigin
                    ? TemplateUtil.applyFlipEastWestAroundOriginToTag(tag)
                    : TemplateUtil.applyFlipEastWestToTag(tag);

            case FLIP_NORTH_SOUTH -> aroundOrigin
                    ? TemplateUtil.applyFlipNorthSouthAroundOriginToTag(tag)
                    : TemplateUtil.applyFlipNorthSouthToTag(tag);

            case FLIP_VERTICAL -> aroundOrigin
                    ? TemplateUtil.applyFlipVAroundOriginToTag(tag)
                    : TemplateUtil.applyFlipVToTag(tag);

            default -> null;
        };

        if (transformed == null || transformed.isEmpty()) {
            return;
        }

        String savedId = saveStructure(player, stack, id, transformed);

        if (savedId == null || savedId.isBlank()) {
            return;
        }

        if (action == ROTATE_CLOCKWISE) {
            updatePreviewSideMap(stack, buildRotationSideMap(1));
        } else if (action == FLIP_EAST_WEST) {
            updatePreviewSideMap(stack, buildEastWestFlipSideMap());
        } else if (action == FLIP_NORTH_SOUTH) {
            updatePreviewSideMap(stack, buildNorthSouthFlipSideMap());
        } else if (action == FLIP_VERTICAL) {
            updatePreviewSideMap(stack, buildVerticalFlipSideMap());
        }

        TemplateUtil.copyPreviewTransformState(transformed, stack.getOrCreateTag());

        syncStack(player);
        StructureToolPreviewDispatcher.sendPreviewToPlayer(player, transformed);
    }

    private static CompoundTag loadStructure(ServerPlayer player, ItemStack stack, String id) {
        try {
            if (stack.getItem() instanceof PortableSpatialCloner) {
                return ClonerStructureLibraryStore.loadSelectedOrMigrateLegacy(
                        player.server,
                        player.getUUID(),
                        stack
                );
            }

            if (stack.getItem() instanceof PortableSpatialStorage) {
                return StructureToolStructureStore.load(player.server, id);
            }
        } catch (Exception ignored) {
        }

        return null;
    }

    private static String saveStructure(ServerPlayer player, ItemStack stack, String id, CompoundTag tag) {
        try {
            if (stack.getItem() instanceof PortableSpatialCloner) {
                String currentId = StructureToolStackState.getStructureId(stack);

                if (currentId == null || currentId.isBlank()) {
                    ClonerStructureLibraryStore.Entry entry = ClonerStructureLibraryStore.saveForCurrentSelection(
                            player.server,
                            player.getUUID(),
                            stack,
                            tag
                    );

                    StructureToolStackState.setSelectedClonerLibraryEntry(
                            stack,
                            player.getUUID(),
                            entry.id()
                    );

                    return entry.id();
                }

                ClonerStructureLibraryStore.saveExisting(
                        player.server,
                        player.getUUID(),
                        currentId,
                        tag
                );

                StructureToolStackState.setSelectedClonerLibraryEntry(
                        stack,
                        player.getUUID(),
                        currentId
                );

                return currentId;
            }

            if (stack.getItem() instanceof PortableSpatialStorage) {
                String currentId = StructureToolStackState.getStructureId(stack);

                if (currentId == null || currentId.isBlank()) {
                    currentId = UUID.randomUUID().toString();
                    StructureToolStackState.setStructureId(stack, currentId);
                }

                StructureToolStructureStore.save(player.server, currentId, tag);
                return currentId;
            }
        } catch (Exception ignored) {
        }

        return null;
    }

    private static void syncStack(ServerPlayer player) {
        player.getInventory().setChanged();
        player.containerMenu.broadcastChanges();
        player.inventoryMenu.broadcastChanges();
    }

    private static void updatePreviewSideMap(ItemStack stack, int[] appliedSideMap) {
        CompoundTag stackTag = stack.getOrCreateTag();

        int[] current = readPreviewSideMap(stackTag);
        int[] combined = composeSideMaps(current, appliedSideMap);

        writePreviewSideMap(stackTag, combined);
    }

    private static int[] readPreviewSideMap(CompoundTag tag) {
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

    private static void writePreviewSideMap(CompoundTag tag, int[] sideMap) {
        tag.putIntArray(PREVIEW_SIDE_MAP_KEY, sideMap);
    }

    private static int[] composeSideMaps(int[] current, int[] applied) {
        int[] result = new int[Direction.values().length];

        for (Direction side : Direction.values()) {
            int currentMapped = current[side.ordinal()];
            result[side.ordinal()] = applied[currentMapped];
        }

        return result;
    }

    private static int[] identitySideMap() {
        int[] map = new int[Direction.values().length];

        for (Direction side : Direction.values()) {
            map[side.ordinal()] = side.ordinal();
        }

        return map;
    }

    private static int[] buildRotationSideMap(int normalizedQuarterTurns) {
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

    private static Direction rotateClockwiseSide(Direction side) {
        return switch (side) {
            case NORTH -> Direction.EAST;
            case EAST -> Direction.SOUTH;
            case SOUTH -> Direction.WEST;
            case WEST -> Direction.NORTH;
            case UP -> Direction.UP;
            case DOWN -> Direction.DOWN;
        };
    }

    private static int[] buildVerticalFlipSideMap() {
        int[] map = identitySideMap();

        map[Direction.UP.ordinal()] = Direction.DOWN.ordinal();
        map[Direction.DOWN.ordinal()] = Direction.UP.ordinal();

        return map;
    }

    private static int[] buildEastWestFlipSideMap() {
        int[] map = identitySideMap();

        map[Direction.EAST.ordinal()] = Direction.WEST.ordinal();
        map[Direction.WEST.ordinal()] = Direction.EAST.ordinal();

        return map;
    }

    private static int[] buildNorthSouthFlipSideMap() {
        int[] map = identitySideMap();

        map[Direction.NORTH.ordinal()] = Direction.SOUTH.ordinal();
        map[Direction.SOUTH.ordinal()] = Direction.NORTH.ordinal();

        return map;
    }
}