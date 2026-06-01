package net.oktawia.spatialtoolscmp.compat.ae2;

import appeng.api.networking.IGrid;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.oktawia.spatialtoolscmp.network.packets.SyncClonerRequirementStatusPacket;

import java.util.List;

public final class AE2CraftingBufferOps {

    public static final int NO_BUFFER = 0;
    public static final int AVAILABLE = 1;
    public static final int ALL_BUSY = 2;
    public static final int CRAFTING_SCHEDULED = 3;

    private AE2CraftingBufferOps() {}

    public static int getStatus(ServerLevel level, ItemStack toolStack) {
        IGrid grid = AE2GridLinkableHandler.getLinkedGrid(level, toolStack);
        if (grid == null) return NO_BUFFER;
        return getGridStatus(grid);
    }

    public static int requestCraftAll(ServerLevel level, ItemStack toolStack, List<SyncClonerRequirementStatusPacket.Entry> entries) {
        IGrid grid = AE2GridLinkableHandler.getLinkedGrid(level, toolStack);
        if (grid == null) return NO_BUFFER;

        boolean hasAny = false;
        CraftingBufferBlockEntity freeBuffer = null;

        for (CraftingBufferBlockEntity be : grid.getMachines(CraftingBufferBlockEntity.class)) {
            hasAny = true;
            if (!be.hasActiveCrafting()) {
                freeBuffer = be;
                break;
            }
        }

        if (freeBuffer == null) return hasAny ? ALL_BUSY : NO_BUFFER;

        // Only craftable items that are actually missing — non-craftable or already-available items
        // are excluded to prevent the crafting plan from failing.
        GenericStack[] toRequest = entries.stream()
                .filter(e -> e.craftable() && e.required() > e.available())
                .map(e -> new GenericStack(AEItemKey.of(e.stack()), e.required()))
                .filter(s -> s.what() != null && s.amount() > 0)
                .toArray(GenericStack[]::new);

        if (toRequest.length > 0) {
            freeBuffer.request(toRequest, true);
        }

        return CRAFTING_SCHEDULED;
    }

    private static int getGridStatus(IGrid grid) {
        boolean hasAny = false;
        for (CraftingBufferBlockEntity be : grid.getMachines(CraftingBufferBlockEntity.class)) {
            hasAny = true;
            if (!be.hasActiveCrafting()) {
                return AVAILABLE;
            }
        }
        return hasAny ? ALL_BUSY : NO_BUFFER;
    }
}
