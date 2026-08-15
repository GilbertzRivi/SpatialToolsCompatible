package net.oktawia.spatialtoolscmp.compat.ae2;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import appeng.api.networking.IGrid;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.stacks.KeyCounter;

import net.oktawia.spatialtoolscmp.logic.buffer.BufferRequestState;
import net.oktawia.spatialtoolscmp.logic.buffer.ManagedBuffer;
import net.oktawia.spatialtoolscmp.network.packets.SyncClonerRequirementStatusPacket;

public final class AE2CraftingBufferOps {

    public static final int NO_BUFFER = 0;
    public static final int AVAILABLE = 1;
    public static final int ALL_BUSY = 2;
    public static final int CRAFTING_SCHEDULED = 3;
    public static final int TOO_MANY_ITEMS = 4;

    private AE2CraftingBufferOps() {
    }

    public static int getStatus(ServerLevel level, ItemStack toolStack) {
        IGrid grid = AE2GridLinkableHandler.getLinkedGrid(level, toolStack);
        if (grid == null)
            return NO_BUFFER;
        return getGridStatus(grid);
    }

    public static int requestCraftAll(
            ServerLevel level,
            ItemStack toolStack,
            ServerPlayer player,
            String structureName,
            List<SyncClonerRequirementStatusPacket.Entry> entries) {
        IGrid grid = AE2GridLinkableHandler.getLinkedGrid(level, toolStack);
        if (grid == null)
            return NO_BUFFER;

        boolean hasAny = false;
        CraftingBufferBlockEntity freeBuffer = null;

        for (CraftingBufferBlockEntity be : grid.getMachines(CraftingBufferBlockEntity.class)) {
            hasAny = true;
            if (!be.isBusy()) {
                freeBuffer = be;
                break;
            }
        }

        if (freeBuffer == null)
            return hasAny ? ALL_BUSY : NO_BUFFER;

        GenericStack[] toRequest = buildGridRequest(grid, entries);

        if (toRequest.length == 0) {
            return AVAILABLE;
        }

        String label = structureName == null || structureName.isBlank()
                ? player.getGameProfile().getName()
                : structureName;

        Component dummyName = Component.literal(label).withStyle(ChatFormatting.AQUA);

        ManagedBuffer.PrepareResult result = freeBuffer.prepare(
                player.getUUID(),
                label,
                toRequest,
                dummyName);

        return switch (result.status()) {
            case READY -> CRAFTING_SCHEDULED;
            case AWAITING_CONFIRM -> {
                if (result.dummyKey() == null) {
                    yield TOO_MANY_ITEMS;
                }

                AE2MEOps.openCraftingMenuForKey(result.dummyKey(), 1, player);
                yield CRAFTING_SCHEDULED;
            }
            case UNSUPPORTED -> TOO_MANY_ITEMS;
            case BUSY -> ALL_BUSY;
        };
    }

    private static GenericStack[] buildGridRequest(
            IGrid grid,
            List<SyncClonerRequirementStatusPacket.Entry> entries) {
        KeyCounter stored = new KeyCounter();
        grid.getStorageService().getInventory().getAvailableStacks(stored);

        List<GenericStack> request = new ArrayList<>(entries.size());

        for (SyncClonerRequirementStatusPacket.Entry entry : entries) {
            if (entry.required() <= 0) {
                continue;
            }

            AEItemKey key = AEItemKey.of(entry.stack());

            if (key == null) {
                continue;
            }

            if (!entry.craftable() && stored.get(key) < entry.required()) {
                continue;
            }

            request.add(new GenericStack(key, entry.required()));
        }

        return request.toArray(GenericStack[]::new);
    }

    @Nullable
    public static CraftingBufferBlockEntity findOwnedBuffer(ServerLevel level, ItemStack toolStack, UUID owner) {
        IGrid grid = AE2GridLinkableHandler.getLinkedGrid(level, toolStack);

        if (grid == null || owner == null) {
            return null;
        }

        for (CraftingBufferBlockEntity be : grid.getMachines(CraftingBufferBlockEntity.class)) {
            if (owner.equals(be.getOwnerId()) && be.getRequestState() != BufferRequestState.IDLE) {
                return be;
            }
        }

        return null;
    }

    private static int getGridStatus(IGrid grid) {
        boolean hasAny = false;
        for (CraftingBufferBlockEntity be : grid.getMachines(CraftingBufferBlockEntity.class)) {
            hasAny = true;
            if (!be.isBusy()) {
                return AVAILABLE;
            }
        }
        return hasAny ? ALL_BUSY : NO_BUFFER;
    }
}
