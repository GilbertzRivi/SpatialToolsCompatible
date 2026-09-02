package net.oktawia.spatialtoolscmp.logic;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public final class StructureToolStackState {

    private static final String TAG_SEL_A = "sel_a";
    private static final String TAG_SEL_B = "sel_b";
    private static final String TAG_ORIGIN = "origin";
    private static final String TAG_SRC_FACING = "src_facing";
    private static final String TAG_STRUCTURE_ID = "structure_id";
    private static final String TAG_CLONER_LIBRARY_OWNER = "cloner_library_owner";

    private static final String TAG_ANCHOR_ENABLED = "anchor_enabled";
    private static final String TAG_ANCHOR_POS = "anchor_pos";
    private static final String TAG_ANCHOR_DIMENSION = "anchor_dimension";

    private static final String TAG_SELECTION_MODE = "selection_mode";
    private static final String TAG_GREEN_CORNER_SELECTED = "green_corner_selected";

    private static final String TAG_ROTATE_PANEL_MODE = "rotate_panel_mode";

    public static final String TAG_PREVIEW_SIDE_MAP = "crazy_preview_side_map";

    private StructureToolStackState() {
    }

    public enum SelectionMode {
        DEFAULT(0),
        BLOCK_IN_FRONT(1);

        private final int id;

        SelectionMode(int id) {
            this.id = id;
        }

        public int id() {
            return this.id;
        }

        public SelectionMode next() {
            return switch (this) {
                case DEFAULT -> BLOCK_IN_FRONT;
                case BLOCK_IN_FRONT -> DEFAULT;
            };
        }

        public static SelectionMode byId(int id) {
            for (SelectionMode mode : values()) {
                if (mode.id == id) {
                    return mode;
                }
            }

            return DEFAULT;
        }
    }

    public static void setSelectionA(ItemStack stack, @Nullable BlockPos pos) {
        setPos(stack, TAG_SEL_A, pos);
    }

    public static void setSelectionB(ItemStack stack, @Nullable BlockPos pos) {
        setPos(stack, TAG_SEL_B, pos);
    }

    public static void setStructureId(ItemStack stack, @Nullable String id) {
        CompoundTag tag = stack.getOrCreateTag();

        if (id == null || id.isBlank()) {
            tag.remove(TAG_STRUCTURE_ID);
        } else {
            tag.putString(TAG_STRUCTURE_ID, id);
        }
    }

    public static void setClonerLibraryOwner(ItemStack stack, @Nullable UUID owner) {
        CompoundTag tag = stack.getOrCreateTag();

        if (owner == null) {
            tag.remove(TAG_CLONER_LIBRARY_OWNER);
        } else {
            tag.putString(TAG_CLONER_LIBRARY_OWNER, owner.toString());
        }
    }

    public static void setSelectedClonerLibraryEntry(
            ItemStack stack,
            @Nullable UUID owner,
            @Nullable String id) {
        if (owner == null || id == null || id.isBlank()) {
            clearSelectedClonerLibraryEntry(stack);
            return;
        }

        setClonerLibraryOwner(stack, owner);
        setStructureId(stack, id);
    }

    public static void clearSelectedClonerLibraryEntry(ItemStack stack) {
        setStructureId(stack, null);
        setClonerLibraryOwner(stack, null);
        resetPreviewSideMap(stack);
        clearAnchor(stack);
    }

    public static BlockPos getSelectionA(ItemStack stack) {
        return getPos(stack, TAG_SEL_A);
    }

    public static BlockPos getSelectionB(ItemStack stack) {
        return getPos(stack, TAG_SEL_B);
    }

    public static String getStructureId(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? "" : tag.getString(TAG_STRUCTURE_ID);
    }

    public static @Nullable UUID getClonerLibraryOwner(ItemStack stack) {
        CompoundTag tag = stack.getTag();

        if (tag == null || !tag.contains(TAG_CLONER_LIBRARY_OWNER, Tag.TAG_STRING)) {
            return null;
        }

        try {
            return UUID.fromString(tag.getString(TAG_CLONER_LIBRARY_OWNER));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static boolean hasStructure(ItemStack stack) {
        return !getStructureId(stack).isBlank();
    }

    public static void clearSelection(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();

        tag.remove(TAG_SEL_A);
        tag.remove(TAG_SEL_B);
        tag.remove(TAG_ORIGIN);
        tag.remove(TAG_SRC_FACING);
        tag.remove(TAG_GREEN_CORNER_SELECTED);
    }

    public static void clearStructure(ItemStack stack) {
        setStructureId(stack, null);
        clearAnchor(stack);
        setRotatePanelMode(stack, false);
    }

    public static void setAnchor(ItemStack stack, ResourceKey<Level> dimension, BlockPos pos) {
        if (stack == null || stack.isEmpty() || dimension == null || pos == null) {
            return;
        }

        CompoundTag tag = stack.getOrCreateTag();

        tag.putBoolean(TAG_ANCHOR_ENABLED, true);
        tag.putString(TAG_ANCHOR_DIMENSION, dimension.location().toString());
        setPos(stack, TAG_ANCHOR_POS, pos.immutable());
    }

    public static void clearAnchor(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return;
        }

        CompoundTag tag = stack.getOrCreateTag();

        tag.remove(TAG_ANCHOR_ENABLED);
        tag.remove(TAG_ANCHOR_DIMENSION);
        tag.remove(TAG_ANCHOR_POS);
    }

    public static boolean isAnchorEnabled(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        CompoundTag tag = stack.getTag();

        if (tag == null || !tag.getBoolean(TAG_ANCHOR_ENABLED)) {
            return false;
        }

        return getPos(stack, TAG_ANCHOR_POS) != null;
    }

    public static @Nullable BlockPos getAnchorPos(ItemStack stack) {
        if (!isAnchorEnabled(stack)) {
            return null;
        }

        return getPos(stack, TAG_ANCHOR_POS);
    }

    public static boolean isAnchorInDimension(ItemStack stack, ResourceKey<Level> dimension) {
        if (!isAnchorEnabled(stack) || dimension == null) {
            return false;
        }

        CompoundTag tag = stack.getTag();

        if (tag == null || !tag.contains(TAG_ANCHOR_DIMENSION, Tag.TAG_STRING)) {
            return false;
        }

        return dimension.location().toString().equals(tag.getString(TAG_ANCHOR_DIMENSION));
    }

    public static @Nullable BlockPos getAnchorIfValid(ItemStack stack, ResourceKey<Level> dimension) {
        if (!isAnchorInDimension(stack, dimension)) {
            return null;
        }

        return getAnchorPos(stack);
    }

    public static SelectionMode getSelectionMode(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return SelectionMode.DEFAULT;
        }

        CompoundTag tag = stack.getTag();

        if (tag == null || !tag.contains(TAG_SELECTION_MODE, Tag.TAG_ANY_NUMERIC)) {
            return SelectionMode.DEFAULT;
        }

        return SelectionMode.byId(tag.getInt(TAG_SELECTION_MODE));
    }

    public static void setSelectionMode(ItemStack stack, SelectionMode mode) {
        if (stack == null || stack.isEmpty()) {
            return;
        }

        stack.getOrCreateTag().putInt(
                TAG_SELECTION_MODE,
                mode == null ? SelectionMode.DEFAULT.id() : mode.id());
    }

    public static boolean isGreenCornerSelected(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        if (getSelectionB(stack) == null) {
            return false;
        }

        CompoundTag tag = stack.getTag();

        return tag != null && tag.getBoolean(TAG_GREEN_CORNER_SELECTED);
    }

    public static void setGreenCornerSelected(ItemStack stack, boolean selected) {
        if (stack == null || stack.isEmpty()) {
            return;
        }

        if (selected) {
            stack.getOrCreateTag().putBoolean(TAG_GREEN_CORNER_SELECTED, true);
            return;
        }

        CompoundTag tag = stack.getTag();

        if (tag != null) {
            tag.remove(TAG_GREEN_CORNER_SELECTED);
        }
    }

    public static boolean isRotatePanelMode(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }

        CompoundTag tag = stack.getTag();

        return tag != null && tag.getBoolean(TAG_ROTATE_PANEL_MODE);
    }

    public static void setRotatePanelMode(ItemStack stack, boolean rotate) {
        if (stack == null || stack.isEmpty()) {
            return;
        }

        if (rotate) {
            stack.getOrCreateTag().putBoolean(TAG_ROTATE_PANEL_MODE, true);
            return;
        }

        CompoundTag tag = stack.getTag();

        if (tag != null) {
            tag.remove(TAG_ROTATE_PANEL_MODE);
        }
    }

    public static SelectionMode cycleSelectionMode(ItemStack stack) {
        SelectionMode next = getSelectionMode(stack).next();
        setSelectionMode(stack, next);
        return next;
    }

    public static boolean isBlockInFrontSelectionMode(ItemStack stack) {
        return getSelectionMode(stack) == SelectionMode.BLOCK_IN_FRONT;
    }

    public static BlockPos getBlockInFrontSelectionPos(Player player) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();

        Vec3 target = eye.add(look.normalize().scale(3.0D));

        return BlockPos.containing(target);
    }

    public static BlockPos resolveSelectionPos(ItemStack stack, Player player, BlockPos clickedPos) {
        if (isBlockInFrontSelectionMode(stack)) {
            return getBlockInFrontSelectionPos(player).immutable();
        }

        return clickedPos.immutable();
    }

    public static int[] getPreviewSideMap(ItemStack stack) {
        int[] identity = identitySideMap();
        CompoundTag tag = stack.getTag();

        if (tag == null || !tag.contains(TAG_PREVIEW_SIDE_MAP, Tag.TAG_INT_ARRAY)) {
            return identity;
        }

        int[] raw = tag.getIntArray(TAG_PREVIEW_SIDE_MAP);

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

    public static void resetPreviewSideMap(ItemStack stack) {
        stack.getOrCreateTag().putIntArray(TAG_PREVIEW_SIDE_MAP, identitySideMap());
    }

    public static int[] identitySideMap() {
        int[] map = new int[Direction.values().length];

        for (Direction side : Direction.values()) {
            map[side.ordinal()] = side.ordinal();
        }

        return map;
    }

    private static void setPos(ItemStack stack, String key, @Nullable BlockPos pos) {
        CompoundTag tag = stack.getOrCreateTag();

        if (pos == null) {
            tag.remove(key);
            return;
        }

        tag.putIntArray(key, new int[] { pos.getX(), pos.getY(), pos.getZ() });
    }

    private static @Nullable BlockPos getPos(ItemStack stack, String key) {
        CompoundTag tag = stack.getTag();

        if (tag == null || !tag.contains(key)) {
            return null;
        }

        int[] arr = tag.getIntArray(key);

        if (arr.length != 3) {
            return null;
        }

        return new BlockPos(arr[0], arr[1], arr[2]);
    }

    public static boolean hasAnySelection(ItemStack stack) {
        return getSelectionA(stack) != null || getSelectionB(stack) != null;
    }

    public static void moveSelectionA(ItemStack stack, int dx, int dy, int dz) {
        BlockPos current = getSelectionA(stack);

        if (current == null) {
            return;
        }

        setSelectionA(stack, current.offset(dx, dy, dz));
    }

    public static void moveSelectionB(ItemStack stack, int dx, int dy, int dz) {
        BlockPos current = getSelectionB(stack);

        if (current == null) {
            return;
        }

        setSelectionB(stack, current.offset(dx, dy, dz));
    }
}
