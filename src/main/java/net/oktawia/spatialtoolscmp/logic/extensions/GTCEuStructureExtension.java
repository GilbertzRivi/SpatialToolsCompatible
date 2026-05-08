package net.oktawia.spatialtoolscmp.logic.extensions;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.blockentity.PipeBlockEntity;
import com.gregtechceu.gtceu.api.cover.CoverBehavior;
import com.gregtechceu.gtceu.api.machine.feature.IDataStickInteractable;
import com.gregtechceu.gtceu.api.pipenet.PipeCoverContainer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.oktawia.spatialtoolscmp.items.AbstractStructureCaptureToolItem;
import net.oktawia.spatialtoolscmp.logic.ClonerPasteContext;
import net.oktawia.spatialtoolscmp.logic.PlacementPlan;
import net.oktawia.spatialtoolscmp.logic.StructureCloneExtension;
import net.oktawia.spatialtoolscmp.logic.StructurePasteExtension;
import net.oktawia.spatialtoolscmp.util.NbtUtil;
import net.oktawia.spatialtoolscmp.util.StructureToolKeys;
import net.oktawia.spatialtoolscmp.util.TemplateUtil;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public final class GTCEuStructureExtension implements StructureCloneExtension, StructurePasteExtension {

    private static final String NBT_ID = "id";
    private static final String NBT_COVER = "cover";
    private static final String NBT_TRANSFORM_UP = "isTransformUp";
    private static final String NBT_RENDER_STATE = "renderState";
    private static final String NBT_RENDER_STATE_NAME = "Name";
    private static final String NBT_RENDER_PROPERTIES = "Properties";
    private static final String NBT_RENDER_TRANSFORM_UP = "transform_up";

    private static final long PIPE_POST_INIT_DELAY_TICKS = 1L;

    private static final List<PendingInit> PENDING = new ArrayList<>();
    private static boolean registered = false;

    @Override
    public boolean handlesRequirements(BlockState state, @Nullable CompoundTag rawBeTag) {
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        return id != null && id.toString().startsWith(StructureToolKeys.GTCEU_ID_PREFIX);
    }

    @Override
    public boolean collectMetadata(
            ServerLevel level,
            BlockPos pos,
            BlockEntity be,
            @Nullable CompoundTag rawBeTag,
            AbstractStructureCaptureToolItem.RequirementSink requirements,
            CompoundTag blockEntry
    ) {
        if (handlesRequirements(level.getBlockState(pos), rawBeTag)) {
            addBaseBlockRequirement(level, pos, requirements);
        }

        CompoundTag gregData = collectGregMetadata(rawBeTag, be, requirements);

        if (gregData.isEmpty()) {
            return false;
        }

        blockEntry.put(StructureToolKeys.CLONE_KEY_GREG, gregData);
        return true;
    }

    @Override
    public Optional<PlacementPlan> buildPlacementPlan(
            ServerLevel level,
            Player player,
            BlockState state,
            @Nullable CompoundTag rawBeTag,
            @Nullable CompoundTag blockMetadata,
            ClonerPasteContext ctx
    ) {
        if (!isGregBlockEntityTag(rawBeTag)) {
            return Optional.empty();
        }

        CompoundTag gregMeta = getGregMetadata(blockMetadata);

        PlacementPlan plan = isGregPipeTag(rawBeTag)
                ? buildGregPipePlacementPlan(player, state, rawBeTag, gregMeta, ctx)
                : buildGenericGregPlacementPlan(player, state, rawBeTag, gregMeta, ctx);

        return Optional.of(plan);
    }

    @Override
    public void onBlockPlaced(
            ServerLevel level,
            BlockPos pos,
            @Nullable BlockEntity be,
            @Nullable CompoundTag blockMetadata
    ) {
        CompoundTag gregMeta = getGregMetadata(blockMetadata);

        if (gregMeta.isEmpty()) {
            return;
        }

        CompoundTag currentTag = saveCurrentTag(be);
        CompoundTag postPlacementTag = createPostPlacementPipeTag(be, currentTag, gregMeta);

        if (postPlacementTag.isEmpty()) {
            return;
        }

        scheduleSinglePipePostPlacementInit(
                level,
                pos,
                postPlacementTag,
                createCoverSnapshotForGuard(currentTag)
        );
    }

    private static PlacementPlan buildGregPipePlacementPlan(
            Player player,
            BlockState pipeState,
            CompoundTag rawBeTag,
            CompoundTag gregMeta,
            ClonerPasteContext ctx
    ) {
        CompoundTag pipeData = gregMeta.getCompound(StructureToolKeys.CLONE_KEY_GREG_PIPE);
        CompoundTag coverData = gregMeta.getCompound(StructureToolKeys.CLONE_KEY_GREG_COVER);

        ItemStack pipeItem = normalizeSingle(ctx.getRequiredBlockItem(pipeState));
        String frameMaterial = pipeData.getString("frameMaterial");

        ItemStack frameItem = normalizeSingle(getGregFrameItem(frameMaterial));
        BlockState frameState = getGregFrameState(frameMaterial);

        if (player.isCreative()) {
            List<ItemStack> costs = new ArrayList<>();

            if (!pipeItem.isEmpty()) {
                costs.add(pipeItem);
            }

            if (!frameItem.isEmpty()) {
                costs.add(frameItem);
            }

            CompoundTag filteredCover = filterGregCoverForPlacement(
                    coverData,
                    null,
                    true,
                    costs,
                    null
            );

            CompoundTag beTag = createWhitelistedGregPipeTag(rawBeTag, pipeData, filteredCover);
            return new PlacementPlan(true, pipeState, beTag, costs);
        }

        Map<Item, Integer> reserved = new LinkedHashMap<>();
        List<ItemStack> costs = new ArrayList<>();

        boolean canPlacePipe = !pipeItem.isEmpty()
                && ctx.canReserveForPaste(reserved, pipeItem, 1);

        if (canPlacePipe) {
            costs.add(pipeItem);

            CompoundTag effectivePipeData = pipeData.copy();

            if (!frameItem.isEmpty()) {
                if (ctx.canReserveForPaste(reserved, frameItem, 1)) {
                    costs.add(frameItem);
                } else {
                    effectivePipeData.remove("frameMaterial");
                }
            } else {
                effectivePipeData.remove("frameMaterial");
            }

            CompoundTag filteredCover = filterGregCoverForPlacement(
                    coverData,
                    reserved,
                    false,
                    costs,
                    ctx
            );

            CompoundTag beTag = createWhitelistedGregPipeTag(rawBeTag, effectivePipeData, filteredCover);

            return new PlacementPlan(true, pipeState, beTag, costs);
        }

        if (!frameItem.isEmpty() && frameState != null && ctx.countAvailableForPaste(frameItem) > 0) {
            return new PlacementPlan(true, frameState, null, List.of(frameItem));
        }

        return PlacementPlan.none();
    }

    private static PlacementPlan buildGenericGregPlacementPlan(
            Player player,
            BlockState stateToPlace,
            CompoundTag rawBeTag,
            CompoundTag gregMeta,
            ClonerPasteContext ctx
    ) {
        CompoundTag machineData = gregMeta.getCompound(StructureToolKeys.CLONE_KEY_GREG_MACHINE);
        CompoundTag coverData = gregMeta.getCompound(StructureToolKeys.CLONE_KEY_GREG_COVER);

        ItemStack baseItem = normalizeSingle(ctx.getRequiredBlockItem(stateToPlace));

        if (baseItem.isEmpty() && !player.isCreative()) {
            return PlacementPlan.none();
        }

        if (player.isCreative()) {
            List<ItemStack> costs = new ArrayList<>();

            if (!baseItem.isEmpty()) {
                costs.add(baseItem);
            }

            CompoundTag filteredCover = filterGregCoverForPlacement(
                    coverData,
                    null,
                    true,
                    costs,
                    null
            );

            CompoundTag beTag = createWhitelistedGregMachineTag(rawBeTag, machineData, filteredCover);

            return new PlacementPlan(true, stateToPlace, beTag, costs);
        }

        Map<Item, Integer> reserved = new LinkedHashMap<>();

        if (!ctx.canReserveForPaste(reserved, baseItem, 1)) {
            return PlacementPlan.none();
        }

        List<ItemStack> costs = new ArrayList<>();
        costs.add(baseItem);

        CompoundTag filteredCover = filterGregCoverForPlacement(
                coverData,
                reserved,
                false,
                costs,
                ctx
        );

        CompoundTag beTag = createWhitelistedGregMachineTag(rawBeTag, machineData, filteredCover);

        return new PlacementPlan(true, stateToPlace, beTag, costs);
    }

    private static CompoundTag filterGregCoverForPlacement(
            CompoundTag coverTag,
            @Nullable Map<Item, Integer> reserved,
            boolean creative
    ) {
        return filterGregCoverForPlacement(coverTag, reserved, creative, null, null);
    }

    private static CompoundTag filterGregCoverForPlacement(
            CompoundTag coverTag,
            @Nullable Map<Item, Integer> reserved,
            boolean creative,
            @Nullable List<ItemStack> costs,
            @Nullable ClonerPasteContext ctx
    ) {
        CompoundTag filteredCover = new CompoundTag();

        if (coverTag == null || coverTag.isEmpty()) {
            return filteredCover;
        }

        for (String sideKey : coverTag.getAllKeys()) {
            Tag sideTag = coverTag.get(sideKey);

            if (sideTag == null) {
                continue;
            }

            List<ItemStack> attachItems = new ArrayList<>();

            collectGregAttachItems(sideTag, item -> {
                ItemStack normalized = normalizeCostStack(item);

                if (!normalized.isEmpty()) {
                    attachItems.add(normalized);
                }
            });

            boolean keepSide = true;

            if (!creative && !attachItems.isEmpty()) {
                if (reserved == null || ctx == null) {
                    keepSide = false;
                } else {
                    for (ItemStack attachItem : attachItems) {
                        int amount = Math.max(1, attachItem.getCount());

                        ItemStack wanted = attachItem.copy();
                        wanted.setCount(1);
                        wanted.setTag(null);

                        if (!ctx.canReserveForPaste(reserved, wanted, amount)) {
                            keepSide = false;
                            break;
                        }
                    }
                }
            }

            if (keepSide) {
                filteredCover.put(sideKey, sideTag.copy());

                if (costs != null) {
                    costs.addAll(attachItems);
                }
            }
        }

        return filteredCover;
    }

    private static ItemStack normalizeCostStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack cost = stack.copy();

        int amount = Math.max(1, cost.getCount());

        cost.setCount(amount);
        cost.setTag(null);

        return cost;
    }

    private static CompoundTag collectGregMetadata(
            @Nullable CompoundTag rawBeTag,
            BlockEntity be,
            AbstractStructureCaptureToolItem.RequirementSink requirements
    ) {
        CompoundTag out = new CompoundTag();

        if (!isGregBlockEntityTag(rawBeTag)) {
            return out;
        }

        NbtUtil.copyStringIfPresent(rawBeTag, out, NBT_ID);

        if (rawBeTag.contains(NBT_COVER, Tag.TAG_COMPOUND)) {
            CompoundTag coverTag = rawBeTag.getCompound(NBT_COVER).copy();

            out.put(StructureToolKeys.CLONE_KEY_GREG_COVER, coverTag);
            collectGregCoverRequirements(coverTag, requirements);
        }

        if (isGregPipeTag(rawBeTag)) {
            CompoundTag pipeTag = new CompoundTag();

            NbtUtil.copyIntIfPresent(rawBeTag, pipeTag, "connections");
            NbtUtil.copyIntIfPresent(rawBeTag, pipeTag, "blockedConnections");
            NbtUtil.copyIntIfPresent(rawBeTag, pipeTag, "paintingColor");

            if (rawBeTag.contains("frameMaterial", Tag.TAG_STRING)) {
                String frameMaterial = rawBeTag.getString("frameMaterial");

                pipeTag.putString("frameMaterial", frameMaterial);
                collectGregPipeFrameRequirement(frameMaterial, requirements);
            }

            if (!pipeTag.isEmpty()) {
                out.put(StructureToolKeys.CLONE_KEY_GREG_PIPE, pipeTag);
            }
        }

        if (isGregMachineTag(rawBeTag)) {
            CompoundTag machineTag = new CompoundTag();

            NbtUtil.copyTagIfPresent(rawBeTag, machineTag, "ownerUUID");
            NbtUtil.copyStringIfPresent(rawBeTag, machineTag, "workingMode");
            NbtUtil.copyStringIfPresent(rawBeTag, machineTag, "voidingMode");
            NbtUtil.copyByteIfPresent(rawBeTag, machineTag, "batchEnabled");
            NbtUtil.copyByteIfPresent(rawBeTag, machineTag, "isWorkingEnabled");
            NbtUtil.copyByteIfPresent(rawBeTag, machineTag, "workingEnabled");
            NbtUtil.copyByteIfPresent(rawBeTag, machineTag, "isMuffled");
            NbtUtil.copyByteIfPresent(rawBeTag, machineTag, "isDistinct");
            NbtUtil.copyIntIfPresent(rawBeTag, machineTag, "paintingColor");
            NbtUtil.copyIntIfPresent(rawBeTag, machineTag, "currentParallel");
            NbtUtil.copyTagIfPresent(rawBeTag, machineTag, "circuitInventory");

            if (isGregTransformerTag(rawBeTag)) {
                copyGregTransformerState(rawBeTag, machineTag);
            }

            copyGregMachineSideConfig(rawBeTag, machineTag);

            CompoundTag dataStick = collectGregDataStick(be);

            if (!dataStick.isEmpty()) {
                machineTag.put("dataStick", dataStick);
            }

            if (!machineTag.isEmpty()) {
                out.put(StructureToolKeys.CLONE_KEY_GREG_MACHINE, machineTag);
            }
        }

        return out;
    }

    private static CompoundTag collectGregDataStick(BlockEntity be) {
        if (!(be instanceof MetaMachineBlockEntity mmbe)) {
            return new CompoundTag();
        }

        if (!(mmbe.getMetaMachine() instanceof IDataStickInteractable interactable)) {
            return new CompoundTag();
        }

        if (!(be.getLevel() instanceof ServerLevel serverLevel)) {
            return new CompoundTag();
        }

        ItemStack stick = new ItemStack(Items.STICK);
        Player fakePlayer = FakePlayerFactory.getMinecraft(serverLevel);

        try {
            InteractionResult result = interactable.onDataStickShiftUse(fakePlayer, stick);

            if (!result.consumesAction() && result != InteractionResult.SUCCESS) {
                return new CompoundTag();
            }

            CompoundTag tag = stick.getTag();
            return tag == null ? new CompoundTag() : tag.copy();
        } catch (Throwable ignored) {
            return new CompoundTag();
        }
    }

    private static void collectGregPipeFrameRequirement(
            String frameMaterial,
            AbstractStructureCaptureToolItem.RequirementSink requirements
    ) {
        if (frameMaterial == null || frameMaterial.isBlank()) {
            return;
        }

        String materialPath = frameMaterial;
        int namespaceSeparator = materialPath.indexOf(':');

        if (namespaceSeparator >= 0 && namespaceSeparator + 1 < materialPath.length()) {
            materialPath = materialPath.substring(namespaceSeparator + 1);
        }

        ResourceLocation frameId = new ResourceLocation("gtceu", materialPath + "_frame");
        Item frameItem = ForgeRegistries.ITEMS.getValue(frameId);

        if (frameItem != null && frameItem != Items.AIR) {
            requirements.add(new ItemStack(frameItem));
        }
    }

    private static void copyGregMachineSideConfig(CompoundTag from, CompoundTag to) {
        NbtUtil.copyTagIfPresent(from, to, "outputFacingItems");
        NbtUtil.copyTagIfPresent(from, to, "outputFacingFluids");

        NbtUtil.copyTagIfPresent(from, to, "inputFacingItems");
        NbtUtil.copyTagIfPresent(from, to, "inputFacingFluids");

        NbtUtil.copyByteIfPresent(from, to, "autoOutputItems");
        NbtUtil.copyByteIfPresent(from, to, "autoOutputFluids");

        NbtUtil.copyByteIfPresent(from, to, "allowInputFromOutputSideItems");
        NbtUtil.copyByteIfPresent(from, to, "allowInputFromOutputSideFluids");

        copyGregLockedFluidSettings(from, to);
    }

    private static void copyGregLockedFluidSettings(CompoundTag from, CompoundTag to) {
        copyGregLockedFluidSettingsRecursive(from, to);
    }

    private static void copyGregLockedFluidSettingsRecursive(CompoundTag from, CompoundTag to) {
        for (String key : from.getAllKeys()) {
            Tag value = from.get(key);

            if (value == null) {
                continue;
            }

            if (isGregLockedFluidKey(key)) {
                to.put(key, value.copy());
                continue;
            }

            if (value instanceof CompoundTag childFrom) {
                CompoundTag childTo = to.contains(key, Tag.TAG_COMPOUND)
                        ? to.getCompound(key).copy()
                        : new CompoundTag();

                copyGregLockedFluidSettingsRecursive(childFrom, childTo);

                if (!childTo.isEmpty()) {
                    to.put(key, childTo);
                }
            }
        }
    }

    private static boolean isGregLockedFluidKey(String key) {
        String normalized = key.toLowerCase(Locale.ROOT);

        return normalized.equals("lockedfluid")
                || normalized.equals("lockedfluids")
                || normalized.equals("lockedfluidname")
                || normalized.equals("lockedfluidstack")
                || normalized.equals("filterfluid")
                || normalized.equals("fluidfilter");
    }

    private static void collectGregCoverRequirements(
            CompoundTag coverTag,
            AbstractStructureCaptureToolItem.RequirementSink requirements
    ) {
        for (String sideKey : coverTag.getAllKeys()) {
            collectGregAttachItems(coverTag.get(sideKey), requirements::add);
        }
    }

    private static CompoundTag createWhitelistedGregPipeTag(
            CompoundTag rawBeTag,
            CompoundTag pipeData,
            CompoundTag filteredCover
    ) {
        CompoundTag out = new CompoundTag();

        NbtUtil.copyStringIfPresent(rawBeTag, out, NBT_ID);
        NbtUtil.copyIntIfPresent(pipeData, out, "connections");
        NbtUtil.copyIntIfPresent(pipeData, out, "blockedConnections");
        NbtUtil.copyIntIfPresent(pipeData, out, "paintingColor");
        NbtUtil.copyStringIfPresent(pipeData, out, "frameMaterial");

        if (!filteredCover.isEmpty()) {
            out.put(NBT_COVER, filteredCover.copy());
        }

        return out;
    }

    private static CompoundTag createWhitelistedGregMachineTag(
            CompoundTag rawBeTag,
            CompoundTag machineData,
            CompoundTag filteredCover
    ) {
        CompoundTag out = new CompoundTag();

        NbtUtil.copyStringIfPresent(rawBeTag, out, NBT_ID);
        NbtUtil.copyTagIfPresent(machineData, out, "ownerUUID");
        NbtUtil.copyStringIfPresent(machineData, out, "workingMode");
        NbtUtil.copyStringIfPresent(machineData, out, "voidingMode");
        NbtUtil.copyByteIfPresent(machineData, out, "batchEnabled");
        NbtUtil.copyByteIfPresent(machineData, out, "isWorkingEnabled");
        NbtUtil.copyByteIfPresent(machineData, out, "workingEnabled");
        NbtUtil.copyByteIfPresent(machineData, out, "isMuffled");
        NbtUtil.copyByteIfPresent(machineData, out, "isDistinct");
        NbtUtil.copyIntIfPresent(machineData, out, "paintingColor");
        NbtUtil.copyIntIfPresent(machineData, out, "currentParallel");
        NbtUtil.copyTagIfPresent(machineData, out, "circuitInventory");

        copyGregTransformerState(machineData, out);

        copyGregMachineSideConfig(machineData, out);

        if (machineData.contains("dataStick", Tag.TAG_COMPOUND)) {
            out.put("dataStick", machineData.getCompound("dataStick").copy());
        }

        if (!filteredCover.isEmpty()) {
            out.put(NBT_COVER, filteredCover.copy());
        }

        return out;
    }

    private static boolean isGregTransformerTag(@Nullable CompoundTag tag) {
        if (tag == null) {
            return false;
        }

        String id = tag.getString(NBT_ID).toLowerCase(Locale.ROOT);

        if (id.contains("transformer")) {
            return true;
        }

        if (tag.contains(NBT_TRANSFORM_UP, Tag.TAG_BYTE)) {
            return true;
        }

        if (!tag.contains(NBT_RENDER_STATE, Tag.TAG_COMPOUND)) {
            return false;
        }

        CompoundTag renderState = tag.getCompound(NBT_RENDER_STATE);

        String renderName = renderState.getString(NBT_RENDER_STATE_NAME).toLowerCase(Locale.ROOT);

        if (renderName.contains("transformer")) {
            return true;
        }

        if (!renderState.contains(NBT_RENDER_PROPERTIES, Tag.TAG_COMPOUND)) {
            return false;
        }

        CompoundTag properties = renderState.getCompound(NBT_RENDER_PROPERTIES);

        return properties.contains(NBT_RENDER_TRANSFORM_UP, Tag.TAG_STRING);
    }

    private static void copyGregTransformerState(CompoundTag from, CompoundTag to) {
        NbtUtil.copyByteIfPresent(from, to, NBT_TRANSFORM_UP);

        CompoundTag renderState = copyGregTransformerRenderState(from);

        if (!renderState.isEmpty()) {
            to.put(NBT_RENDER_STATE, renderState);
        }
    }

    private static CompoundTag copyGregTransformerRenderState(CompoundTag from) {
        CompoundTag out = new CompoundTag();

        if (!from.contains(NBT_RENDER_STATE, Tag.TAG_COMPOUND)) {
            return out;
        }

        CompoundTag renderState = from.getCompound(NBT_RENDER_STATE);

        if (!renderState.contains(NBT_RENDER_PROPERTIES, Tag.TAG_COMPOUND)) {
            return out;
        }

        CompoundTag properties = renderState.getCompound(NBT_RENDER_PROPERTIES);

        if (!properties.contains(NBT_RENDER_TRANSFORM_UP, Tag.TAG_STRING)) {
            return out;
        }

        NbtUtil.copyStringIfPresent(renderState, out, NBT_RENDER_STATE_NAME);

        CompoundTag outProperties = new CompoundTag();
        NbtUtil.copyStringIfPresent(properties, outProperties, NBT_RENDER_TRANSFORM_UP);

        if (!outProperties.isEmpty()) {
            out.put(NBT_RENDER_PROPERTIES, outProperties);
        }

        return out;
    }

    private static CompoundTag createPostPlacementPipeTag(
            @Nullable BlockEntity be,
            @Nullable CompoundTag currentTag,
            CompoundTag gregMeta
    ) {
        String id = gregMeta.getString(NBT_ID);

        if ((id == null || id.isBlank()) && currentTag != null) {
            id = currentTag.getString(NBT_ID);
        }

        if ((id == null || id.isBlank()) && be != null) {
            try {
                id = be.saveWithFullMetadata().getString(NBT_ID);
            } catch (Throwable ignored) {
                id = "";
            }
        }

        if (id == null || id.isBlank() || !isGregPipeId(id)) {
            return new CompoundTag();
        }

        CompoundTag rawIdTag = new CompoundTag();
        rawIdTag.putString(NBT_ID, id);

        CompoundTag pipeData = gregMeta.getCompound(StructureToolKeys.CLONE_KEY_GREG_PIPE);
        CompoundTag coverData = getPlacedCoverDataForPostInit(be, currentTag, gregMeta);

        return createWhitelistedGregPipeTag(rawIdTag, pipeData, coverData);
    }

    private static CompoundTag getPlacedCoverDataForPostInit(
            @Nullable BlockEntity be,
            @Nullable CompoundTag currentTag,
            CompoundTag gregMeta
    ) {
        if (currentTag != null && currentTag.contains(NBT_COVER, Tag.TAG_COMPOUND)) {
            return currentTag.getCompound(NBT_COVER).copy();
        }

        if (be != null) {
            return new CompoundTag();
        }

        return gregMeta.getCompound(StructureToolKeys.CLONE_KEY_GREG_COVER).copy();
    }

    @Nullable
    private static CompoundTag createCoverSnapshotForGuard(@Nullable CompoundTag currentTag) {
        if (currentTag == null) {
            return null;
        }

        return copyCoverOrEmpty(currentTag);
    }

    private static CompoundTag copyCoverOrEmpty(CompoundTag tag) {
        if (!tag.contains(NBT_COVER, Tag.TAG_COMPOUND)) {
            return new CompoundTag();
        }

        return tag.getCompound(NBT_COVER).copy();
    }

    private static boolean hasCoverChangedSinceQueued(
            @Nullable BlockEntity blockEntity,
            @Nullable CompoundTag observedCoverTag
    ) {
        if (blockEntity == null || observedCoverTag == null) {
            return false;
        }

        CompoundTag currentTag = saveCurrentTag(blockEntity);

        if (currentTag == null) {
            return false;
        }

        CompoundTag currentCover = copyCoverOrEmpty(currentTag);

        return !currentCover.equals(observedCoverTag);
    }

    private static CompoundTag getGregMetadata(@Nullable CompoundTag blockMetadata) {
        if (blockMetadata == null) {
            return new CompoundTag();
        }

        if (!blockMetadata.contains(StructureToolKeys.CLONE_KEY_GREG, Tag.TAG_COMPOUND)) {
            return new CompoundTag();
        }

        return blockMetadata.getCompound(StructureToolKeys.CLONE_KEY_GREG);
    }

    private static boolean isGregBlockEntityTag(@Nullable CompoundTag tag) {
        if (tag == null) {
            return false;
        }

        String id = tag.getString(NBT_ID);

        if (!id.isBlank() && id.startsWith(StructureToolKeys.GTCEU_ID_PREFIX)) {
            return true;
        }

        return StructureToolKeys.GT_CABLE_ID.equals(id)
                || StructureToolKeys.GT_ITEM_PIPE_ID.equals(id)
                || StructureToolKeys.GT_FLUID_PIPE_ID.equals(id)
                || (tag.contains("connections", Tag.TAG_INT)
                && tag.contains("blockedConnections", Tag.TAG_INT)
                && tag.contains("frameMaterial", Tag.TAG_STRING));
    }

    private static boolean isGregPipeTag(@Nullable CompoundTag tag) {
        if (tag == null) {
            return false;
        }

        return isGregPipeId(tag.getString(NBT_ID));
    }

    private static boolean isGregPipeId(String id) {
        return StructureToolKeys.GT_FLUID_PIPE_ID.equals(id)
                || StructureToolKeys.GT_ITEM_PIPE_ID.equals(id)
                || StructureToolKeys.GT_CABLE_ID.equals(id);
    }

    private static boolean isGregMachineTag(@Nullable CompoundTag tag) {
        if (tag == null) {
            return false;
        }

        String id = tag.getString(NBT_ID);

        return !id.isBlank()
                && id.startsWith(StructureToolKeys.GTCEU_ID_PREFIX)
                && !isGregPipeId(id);
    }

    private static ItemStack normalizeSingle(ItemStack stack) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack copy = stack.copy();

        copy.setCount(1);
        copy.setTag(null);

        return copy;
    }

    private static void addBaseBlockRequirement(
            ServerLevel level,
            BlockPos pos,
            AbstractStructureCaptureToolItem.RequirementSink requirements
    ) {
        BlockHitResult hit = new BlockHitResult(
                Vec3.atCenterOf(pos),
                Direction.UP,
                pos,
                false
        );

        ItemStack picked = level.getBlockState(pos).getCloneItemStack(hit, level, pos, null);

        if (!picked.isEmpty()) {
            requirements.add(picked);
            return;
        }

        Item item = level.getBlockState(pos).getBlock().asItem();

        if (item != Items.AIR) {
            requirements.add(new ItemStack(item));
        }
    }

    private static void scheduleSinglePipePostPlacementInit(
            ServerLevel level,
            BlockPos pos,
            CompoundTag blockEntityTag,
            @Nullable CompoundTag observedCoverTag
    ) {
        if (!isGregPipeTag(blockEntityTag)) {
            return;
        }

        if (isPostPlacementAlreadyPending(level, pos)) {
            return;
        }

        ensureRegistered();

        List<PendingBlockInit> blocks = new ArrayList<>();
        blocks.add(new PendingBlockInit(
                pos.immutable(),
                blockEntityTag.copy(),
                observedCoverTag == null ? null : observedCoverTag.copy()
        ));

        PENDING.add(new PendingInit(
                level,
                blocks,
                level.getGameTime() + PIPE_POST_INIT_DELAY_TICKS
        ));
    }

    private static void ensureRegistered() {
        if (!registered) {
            MinecraftForge.EVENT_BUS.register(GTCEuStructureExtension.class);
            registered = true;
        }
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (!(event.level instanceof ServerLevel level)) {
            return;
        }

        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        long now = level.getGameTime();

        Iterator<PendingInit> iterator = PENDING.iterator();

        while (iterator.hasNext()) {
            PendingInit pending = iterator.next();

            if (pending.level != level) {
                continue;
            }

            if (now < pending.runAtGameTime) {
                continue;
            }

            runPostPlacementInit(level, pending.blocks);
            iterator.remove();
        }
    }

    private static void runPostPlacementInit(ServerLevel level, List<PendingBlockInit> blocks) {
        List<BlockPos> refreshedPositions = new ArrayList<>();

        for (PendingBlockInit pendingBlock : blocks) {
            BlockPos worldPos = pendingBlock.pos();
            CompoundTag blockEntityTag = pendingBlock.blockEntityTag();

            if (!isGregPipeTag(blockEntityTag)) {
                continue;
            }

            BlockEntity blockEntity = level.getBlockEntity(worldPos);

            if (!(blockEntity instanceof PipeBlockEntity<?, ?> pipe)) {
                continue;
            }

            if (hasCoverChangedSinceQueued(blockEntity, pendingBlock.observedCoverTag())) {
                continue;
            }

            initSinglePipe(level, worldPos, pipe, blockEntityTag);
            refreshedPositions.add(worldPos);
        }

        for (BlockPos pos : refreshedPositions) {
            notifyPostPlacedNeighborhood(level, pos);
        }
    }

    private static void initSinglePipe(
            ServerLevel level,
            BlockPos pos,
            PipeBlockEntity<?, ?> pipe,
            CompoundTag originalTag
    ) {
        CompoundTag tag = originalTag.copy();

        tag.putInt("x", pos.getX());
        tag.putInt("y", pos.getY());
        tag.putInt("z", pos.getZ());

        pipe.load(tag);
        pipe.clearRemoved();

        PipeCoverContainer coverContainer = pipe.getCoverContainer();

        coverContainer.onLoad();

        for (Direction side : Direction.values()) {
            CoverBehavior cover = coverContainer.getCoverAtSide(side);

            if (cover == null) {
                continue;
            }

            coverContainer.setCoverAtSide(cover, side);
            cover.onLoad();
            cover.getSyncStorage().markAllDirty();
        }

        pipe.getSyncStorage().markAllDirty();
        coverContainer.getSyncStorage().markAllDirty();

        coverContainer.scheduleNeighborShapeUpdate();
        coverContainer.notifyBlockUpdate();
        coverContainer.scheduleRenderUpdate();
        coverContainer.markDirty();

        pipe.notifyBlockUpdate();
        pipe.scheduleRenderUpdate();
        pipe.onChanged();
        pipe.setChanged();

        BlockState state = level.getBlockState(pos);

        level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
        level.getChunkSource().blockChanged(pos);

        for (Direction side : Direction.values()) {
            BlockPos neighborPos = pos.relative(side);
            BlockState neighborState = level.getBlockState(neighborPos);

            level.sendBlockUpdated(neighborPos, neighborState, neighborState, Block.UPDATE_ALL);
        }

        ClientboundBlockEntityDataPacket packet = ClientboundBlockEntityDataPacket.create(pipe);

        for (ServerPlayer player : level.players()) {
            player.connection.send(packet);
        }
    }

    private static void notifyPostPlacedNeighborhood(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);

        try {
            level.updateNeighborsAt(pos, state.getBlock());
        } catch (Throwable ignored) {
        }

        level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
        level.getChunkSource().blockChanged(pos);

        for (Direction side : Direction.values()) {
            BlockPos neighborPos = pos.relative(side);
            BlockState neighborState = level.getBlockState(neighborPos);

            try {
                level.updateNeighborsAt(neighborPos, neighborState.getBlock());
            } catch (Throwable ignored) {
            }

            try {
                neighborState.neighborChanged(
                        level,
                        neighborPos,
                        state.getBlock(),
                        pos,
                        false
                );
            } catch (Throwable ignored) {
            }

            level.sendBlockUpdated(neighborPos, neighborState, neighborState, Block.UPDATE_ALL);
            level.getChunkSource().blockChanged(neighborPos);
        }
    }

    @Override
    public void onTemplatePasted(ServerLevel level, BlockPos placementOrigin, CompoundTag templateTag) {
        List<PendingBlockInit> blocks = new ArrayList<>();

        for (TemplateUtil.BlockInfo info : TemplateUtil.parseRawBlocksFromTag(templateTag)) {
            BlockPos worldPos = placementOrigin.offset(info.pos()).immutable();

            if (isPostPlacementAlreadyPending(level, worldPos)) {
                continue;
            }

            BlockEntity blockEntity = level.getBlockEntity(worldPos);

            if (!(blockEntity instanceof PipeBlockEntity<?, ?>)) {
                continue;
            }

            CompoundTag currentTag = saveCurrentTag(blockEntity);

            if (!isGregPipeTag(currentTag)) {
                continue;
            }

            blocks.add(new PendingBlockInit(
                    worldPos,
                    currentTag.copy(),
                    createCoverSnapshotForGuard(currentTag)
            ));
        }

        if (blocks.isEmpty()) {
            return;
        }

        ensureRegistered();

        PENDING.add(new PendingInit(
                level,
                blocks,
                level.getGameTime() + PIPE_POST_INIT_DELAY_TICKS
        ));
    }

    private static boolean isPostPlacementAlreadyPending(ServerLevel level, BlockPos pos) {
        for (PendingInit pending : PENDING) {
            if (pending.level != level) {
                continue;
            }

            for (PendingBlockInit block : pending.blocks) {
                if (block.pos().equals(pos)) {
                    return true;
                }
            }
        }

        return false;
    }

    @Nullable
    private static BlockState getGregFrameState(String frameMaterial) {
        if (frameMaterial == null || frameMaterial.isBlank()) {
            return null;
        }

        String materialPath = frameMaterial;
        int sep = materialPath.indexOf(':');

        if (sep >= 0 && sep + 1 < materialPath.length()) {
            materialPath = materialPath.substring(sep + 1);
        }

        ResourceLocation frameId = new ResourceLocation("gtceu", materialPath + "_frame");
        Block frameBlock = ForgeRegistries.BLOCKS.getValue(frameId);

        if (frameBlock == null || frameBlock == Blocks.AIR) {
            return null;
        }

        return frameBlock.defaultBlockState();
    }

    private static ItemStack getGregFrameItem(String frameMaterial) {
        if (frameMaterial == null || frameMaterial.isBlank()) {
            return ItemStack.EMPTY;
        }

        String materialPath = frameMaterial;
        int sep = materialPath.indexOf(':');

        if (sep >= 0 && sep + 1 < materialPath.length()) {
            materialPath = materialPath.substring(sep + 1);
        }

        ResourceLocation frameId = new ResourceLocation("gtceu", materialPath + "_frame");
        Item frameItem = ForgeRegistries.ITEMS.getValue(frameId);

        if (frameItem == null || frameItem == Items.AIR) {
            return ItemStack.EMPTY;
        }

        return new ItemStack(frameItem);
    }

    private static void collectGregAttachItems(@Nullable Tag tag, java.util.function.Consumer<ItemStack> sink) {
        if (tag == null) {
            return;
        }

        if (tag instanceof CompoundTag compoundTag) {
            collectNamedCostItem(compoundTag, "attachItem", sink);
            collectNamedCostItem(compoundTag, "filterItem", sink);

            for (String key : compoundTag.getAllKeys()) {
                if ("attachItem".equals(key) || "filterItem".equals(key)) {
                    continue;
                }

                if ("matches".equals(key)) {
                    continue;
                }

                collectGregAttachItems(compoundTag.get(key), sink);
            }

            return;
        }

        if (tag instanceof ListTag listTag) {
            for (int i = 0; i < listTag.size(); i++) {
                collectGregAttachItems(listTag.get(i), sink);
            }
        }
    }

    private static void collectNamedCostItem(
            CompoundTag parent,
            String key,
            java.util.function.Consumer<ItemStack> sink
    ) {
        if (!parent.contains(key, Tag.TAG_COMPOUND)) {
            return;
        }

        ItemStack stack = readCostItemStack(parent.getCompound(key));

        if (!stack.isEmpty()) {
            sink.accept(stack);
        }
    }

    private static ItemStack readCostItemStack(CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = NbtUtil.tryReadSavedItemStack(tag);

        if (stack.isEmpty()) {
            try {
                stack = ItemStack.of(tag.copy());
            } catch (Throwable ignored) {
                return ItemStack.EMPTY;
            }
        }

        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack cost = stack.copy();

        int amount = Math.max(1, cost.getCount());

        cost.setCount(amount);
        cost.setTag(null);

        return cost;
    }

    private record PendingBlockInit(
            BlockPos pos,
            CompoundTag blockEntityTag,
            @Nullable CompoundTag observedCoverTag
    ) {
    }

    private static final class PendingInit {
        private final ServerLevel level;
        private final List<PendingBlockInit> blocks;
        private final long runAtGameTime;

        private PendingInit(ServerLevel level, List<PendingBlockInit> blocks, long runAtGameTime) {
            this.level = level;
            this.blocks = blocks;
            this.runAtGameTime = runAtGameTime;
        }
    }

    @Override
    public boolean collectUndoRefunds(
            ServerLevel level,
            BlockPos pos,
            BlockState state,
            @Nullable BlockEntity be,
            List<ItemStack> refunds
    ) {
        CompoundTag currentTag = saveCurrentTag(be);

        if (!handlesRequirements(state, currentTag) && !isGregBlockEntityTag(currentTag)) {
            return false;
        }

        addBaseBlockRefund(level, pos, refunds);

        if (currentTag == null) {
            return true;
        }

        if (currentTag.contains(NBT_COVER, Tag.TAG_COMPOUND)) {
            collectGregCoverRequirements(currentTag.getCompound(NBT_COVER), refunds::add);
        }

        if (isGregPipeTag(currentTag) && currentTag.contains("frameMaterial", Tag.TAG_STRING)) {
            ItemStack frameItem = getGregFrameItem(currentTag.getString("frameMaterial"));

            if (!frameItem.isEmpty()) {
                refunds.add(frameItem);
            }
        }

        return true;
    }

    @Nullable
    private static CompoundTag saveCurrentTag(@Nullable BlockEntity be) {
        if (be == null) {
            return null;
        }

        try {
            return be.saveWithFullMetadata();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void addBaseBlockRefund(
            ServerLevel level,
            BlockPos pos,
            List<ItemStack> refunds
    ) {
        BlockHitResult hit = new BlockHitResult(
                Vec3.atCenterOf(pos),
                Direction.UP,
                pos,
                false
        );

        ItemStack picked = level.getBlockState(pos).getCloneItemStack(hit, level, pos, null);

        if (!picked.isEmpty()) {
            refunds.add(picked);
            return;
        }

        Item item = level.getBlockState(pos).getBlock().asItem();

        if (item != Items.AIR) {
            refunds.add(new ItemStack(item));
        }
    }
}