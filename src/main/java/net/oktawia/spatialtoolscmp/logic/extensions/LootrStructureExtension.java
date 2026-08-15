package net.oktawia.spatialtoolscmp.logic.extensions;

import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

import net.oktawia.spatialtoolscmp.items.AbstractStructureCaptureToolItem;
import net.oktawia.spatialtoolscmp.logic.ClonerPasteContext;
import net.oktawia.spatialtoolscmp.logic.PlacementPlan;
import net.oktawia.spatialtoolscmp.logic.StructureCloneExtension;
import net.oktawia.spatialtoolscmp.logic.StructurePasteExtension;
import net.oktawia.spatialtoolscmp.util.StructureToolKeys;

public final class LootrStructureExtension implements StructureCloneExtension, StructurePasteExtension {

    private static final String ID_NAMESPACE = "lootr";
    private static final String CLONE_KEY = "lootr";
    private static final String NBT_TILE_ID = "tileId";
    private static final String NBT_OPENERS = "LootrOpeners";

    @Override
    public boolean handlesRequirements(BlockState state, @Nullable CompoundTag rawBeTag) {
        return false;
    }

    @Override
    public boolean collectMetadata(
            ServerLevel level,
            BlockPos pos,
            BlockEntity be,
            @Nullable CompoundTag rawBeTag,
            AbstractStructureCaptureToolItem.RequirementSink requirements,
            CompoundTag blockEntry) {
        if (!isLootrBlock(level.getBlockState(pos))) {
            return false;
        }

        CompoundTag full = be.saveWithFullMetadata();

        if (!full.hasUUID(NBT_TILE_ID)) {
            return false;
        }

        CompoundTag data = new CompoundTag();
        data.putUUID(NBT_TILE_ID, full.getUUID(NBT_TILE_ID));

        if (full.contains(NBT_OPENERS, Tag.TAG_LIST)) {
            data.put(NBT_OPENERS, full.getList(NBT_OPENERS, Tag.TAG_INT_ARRAY).copy());
        }

        blockEntry.put(CLONE_KEY, data);
        return true;
    }

    @Override
    public Optional<PlacementPlan> buildPlacementPlan(
            ServerLevel level,
            Player player,
            BlockState state,
            @Nullable CompoundTag rawBeTag,
            @Nullable CompoundTag blockMetadata,
            ClonerPasteContext ctx) {
        return Optional.empty();
    }

    @Override
    public void onBlockPlaced(
            ServerLevel level,
            Player player,
            BlockPos pos,
            @Nullable BlockEntity be,
            @Nullable CompoundTag blockMetadata) {
    }

    @Override
    public void onTemplatePasted(ServerLevel level, BlockPos placementOrigin, CompoundTag templateTag) {
        if (!templateTag.contains(StructureToolKeys.CLONE_METADATA_KEY, Tag.TAG_COMPOUND)) {
            return;
        }

        CompoundTag metadata = templateTag.getCompound(StructureToolKeys.CLONE_METADATA_KEY);
        ListTag blocks = metadata.getList(StructureToolKeys.CLONE_METADATA_BLOCKS_KEY, Tag.TAG_COMPOUND);

        for (int i = 0; i < blocks.size(); i++) {
            CompoundTag blockEntry = blocks.getCompound(i);

            if (!blockEntry.contains(CLONE_KEY, Tag.TAG_COMPOUND)) {
                continue;
            }

            CompoundTag posTag = blockEntry.getCompound(StructureToolKeys.CLONE_KEY_POS);
            BlockPos worldPos = placementOrigin.offset(
                    posTag.getInt("x"),
                    posTag.getInt("y"),
                    posTag.getInt("z")).immutable();

            if (!isLootrBlock(level.getBlockState(worldPos))) {
                continue;
            }

            BlockEntity be = level.getBlockEntity(worldPos);

            if (be == null) {
                continue;
            }

            restoreLootrIdentity(level, worldPos, be, blockEntry.getCompound(CLONE_KEY));
        }
    }

    private static void restoreLootrIdentity(
            ServerLevel level,
            BlockPos pos,
            BlockEntity be,
            CompoundTag data) {
        if (!data.hasUUID(NBT_TILE_ID)) {
            return;
        }

        CompoundTag beTag = be.saveWithoutMetadata();
        beTag.putUUID(NBT_TILE_ID, data.getUUID(NBT_TILE_ID));

        if (data.contains(NBT_OPENERS, Tag.TAG_LIST)) {
            beTag.put(NBT_OPENERS, data.getList(NBT_OPENERS, Tag.TAG_INT_ARRAY).copy());
        }

        be.load(beTag);
        be.setChanged();

        BlockState state = level.getBlockState(pos);
        level.sendBlockUpdated(pos, state, state, 3);
    }

    private static boolean isLootrBlock(BlockState state) {
        ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        return blockId != null && ID_NAMESPACE.equals(blockId.getNamespace());
    }
}
