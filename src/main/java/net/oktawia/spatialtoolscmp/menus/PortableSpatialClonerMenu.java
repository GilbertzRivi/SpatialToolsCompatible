package net.oktawia.spatialtoolscmp.menus;

import java.util.List;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import net.oktawia.spatialtoolscmp.IsModLoaded;
import net.oktawia.spatialtoolscmp.compat.ae2.AE2CraftingBufferOps;
import net.oktawia.spatialtoolscmp.compat.ae2.AE2MEOps;
import net.oktawia.spatialtoolscmp.compat.ae2.CraftingBufferBlockEntity;
import net.oktawia.spatialtoolscmp.defs.SpatialMenuRegistrar;
import net.oktawia.spatialtoolscmp.items.PortableSpatialCloner;
import net.oktawia.spatialtoolscmp.logic.ClonerRequirementStatus;
import net.oktawia.spatialtoolscmp.logic.ClonerStructureLibraryStore;
import net.oktawia.spatialtoolscmp.logic.StructureToolStackState;
import net.oktawia.spatialtoolscmp.logic.buffer.BufferRequestState;
import net.oktawia.spatialtoolscmp.network.NetworkHandler;
import net.oktawia.spatialtoolscmp.network.packets.RequestClonerCraftingPacket;
import net.oktawia.spatialtoolscmp.network.packets.RequestCraftAllPacket;
import net.oktawia.spatialtoolscmp.network.packets.SetClonerNestedInventoryModePacket;
import net.oktawia.spatialtoolscmp.network.packets.SyncClonerCraftingProgressPacket;
import net.oktawia.spatialtoolscmp.network.packets.SyncClonerLibraryPacket;
import net.oktawia.spatialtoolscmp.network.packets.SyncClonerRequirementStatusPacket;
import net.oktawia.spatialtoolscmp.network.packets.SyncCraftAllStatusPacket;
import net.oktawia.spatialtoolscmp.util.TemplateUtil;

public class PortableSpatialClonerMenu extends AbstractPortableStructureToolMenu {

    private int requirementSyncTick = 0;

    public PortableSpatialClonerMenu(int id, Inventory playerInventory) {
        super(
                SpatialMenuRegistrar.PORTABLE_SPATIAL_CLONER_MENU.get(),
                id,
                playerInventory,
                findToolStack(playerInventory));

        if (!isClientSide()) {
            syncRequirementsToClient();
            syncLibraryToClient();
        }
    }

    private static ItemStack findToolStack(Inventory playerInventory) {
        ItemStack stack = PortableSpatialCloner.findActive(playerInventory.player);

        if (stack.isEmpty()) {
            stack = PortableSpatialCloner.findHeld(playerInventory.player);
        }

        return stack;
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();

        if (!isClientSide()) {
            requirementSyncTick++;

            if (requirementSyncTick >= 20) {
                requirementSyncTick = 0;
                syncRequirementsToClient();
            }
        }
    }

    @Override
    protected boolean hasStoredStructure() {
        return !StructureToolStackState.getStructureId(getItemStack()).isBlank();
    }

    @Override
    protected byte[] getStructureBytes() {
        if (!(getPlayer() instanceof ServerPlayer serverPlayer)) {
            return null;
        }

        try {
            CompoundTag tag = ClonerStructureLibraryStore.loadSelectedOrMigrateLegacy(
                    serverPlayer.server,
                    serverPlayer.getUUID(),
                    getItemStack());

            if (tag == null || tag.isEmpty()) {
                return null;
            }

            return TemplateUtil.compressNbt(tag);
        } catch (Exception ignored) {
            return null;
        }
    }

    @Override
    protected void setStructureBytes(byte[] bytes) {
        if (!(getPlayer() instanceof ServerPlayer serverPlayer)) {
            return;
        }

        if (bytes == null || bytes.length == 0) {
            return;
        }

        try {
            CompoundTag tag = TemplateUtil.decompressNbt(bytes);
            String currentId = StructureToolStackState.getStructureId(getItemStack());

            if (currentId == null || currentId.isBlank()) {
                ClonerStructureLibraryStore.Entry entry = ClonerStructureLibraryStore.saveForCurrentSelection(
                        serverPlayer.server,
                        serverPlayer.getUUID(),
                        getItemStack(),
                        tag);

                StructureToolStackState.setSelectedClonerLibraryEntry(
                        getItemStack(),
                        serverPlayer.getUUID(),
                        entry.id());

                syncLibraryToClient();
                syncRequirementsToClient();
                return;
            }

            ClonerStructureLibraryStore.saveExisting(
                    serverPlayer.server,
                    serverPlayer.getUUID(),
                    currentId,
                    tag);

            StructureToolStackState.setSelectedClonerLibraryEntry(
                    getItemStack(),
                    serverPlayer.getUUID(),
                    currentId);

            syncLibraryToClient();
            syncRequirementsToClient();
        } catch (Exception ignored) {
        }
    }

    private void syncLibraryToClient() {
        if (!(getPlayer() instanceof ServerPlayer serverPlayer)) {
            return;
        }

        try {
            NetworkHandler.sendToPlayer(
                    serverPlayer,
                    SyncClonerLibraryPacket.fromStoreEntries(
                            ClonerStructureLibraryStore.list(serverPlayer.server, serverPlayer.getUUID()),
                            StructureToolStackState.getStructureId(getItemStack())));
        } catch (Exception ignored) {
            NetworkHandler.sendToPlayer(
                    serverPlayer,
                    SyncClonerLibraryPacket.fromStoreEntries(
                            List.of(),
                            StructureToolStackState.getStructureId(getItemStack())));
        }
    }

    public void cycleNestedInventoryMode() {
        PortableSpatialCloner.NestedInventoryResourceMode next = PortableSpatialCloner
                .getNestedInventoryResourceMode(getItemStack()).next();

        PortableSpatialCloner.setNestedInventoryResourceMode(getItemStack(), next);

        if (isClientSide()) {
            NetworkHandler.sendToServer(new SetClonerNestedInventoryModePacket(
                    this.containerId,
                    next.id()));
            return;
        }

        syncRequirementsToClient();
    }

    public void setNestedInventoryMode(PortableSpatialCloner.NestedInventoryResourceMode mode) {
        if (mode == null) {
            return;
        }

        PortableSpatialCloner.setNestedInventoryResourceMode(getItemStack(), mode);

        if (!isClientSide()) {
            syncRequirementsToClient();
        }
    }

    public void craftRequest(ResourceLocation itemId, long amount) {
        if (itemId == null || amount <= 0) {
            return;
        }

        if (isClientSide()) {
            NetworkHandler.sendToServer(new RequestClonerCraftingPacket(
                    this.containerId,
                    itemId,
                    amount));
            return;
        }

        handleCraftRequest(itemId, amount);
    }

    public void handleCraftRequest(ResourceLocation itemId, long amount) {
        if (isClientSide()) {
            return;
        }

        if (itemId == null || amount <= 0) {
            return;
        }

        if (!(getPlayer() instanceof ServerPlayer serverPlayer)) {
            return;
        }

        if (!(serverPlayer.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        if (!hasCraftingUpgradeInstalled()) {
            return;
        }

        if (PortableSpatialCloner.hasItemHandlerLink(getItemStack())) {
            return;
        }

        if (IsModLoaded.AE2) {
            try {
                Item item = ForgeRegistries.ITEMS.getValue(itemId);

                if (item == null) {
                    return;
                }

                ItemStack filter = new ItemStack(item);

                if (filter.isEmpty()) {
                    return;
                }

                if (!AE2MEOps.isCraftable(filter, getItemStack(), serverLevel)) {
                    return;
                }

                AE2MEOps.openCraftingMenu(filter, amount, serverPlayer);
            } catch (Throwable ignored) {
            }
        }
    }

    public void craftAll() {
        if (isClientSide()) {
            NetworkHandler.sendToServer(new RequestCraftAllPacket(this.containerId));
            return;
        }
        handleCraftAll();
    }

    public void handleCraftAll() {
        if (isClientSide())
            return;
        if (!(getPlayer() instanceof ServerPlayer serverPlayer))
            return;
        if (!(serverPlayer.level() instanceof ServerLevel serverLevel))
            return;
        if (!hasCraftingUpgradeInstalled())
            return;
        if (PortableSpatialCloner.hasItemHandlerLink(getItemStack()))
            return;
        if (!IsModLoaded.AE2)
            return;

        try {
            int status = AE2CraftingBufferOps.requestCraftAll(
                    serverLevel,
                    getItemStack(),
                    serverPlayer,
                    getSelectedStructureName(serverPlayer),
                    buildRequirementEntries());
            NetworkHandler.sendToPlayer(serverPlayer, new SyncCraftAllStatusPacket(this.containerId, status));
        } catch (Throwable ignored) {
        }
    }

    private String getSelectedStructureName(ServerPlayer serverPlayer) {
        String id = StructureToolStackState.getStructureId(getItemStack());

        if (id == null || id.isBlank()) {
            return "";
        }

        try {
            ClonerStructureLibraryStore.Entry entry = ClonerStructureLibraryStore.get(
                    serverPlayer.server,
                    serverPlayer.getUUID(),
                    id);

            return entry == null ? "" : entry.name();
        } catch (Exception ignored) {
            return "";
        }
    }

    private void syncCraftingProgressToClient(ServerPlayer serverPlayer, ServerLevel serverLevel) {
        CraftingBufferBlockEntity buffer = AE2CraftingBufferOps.findOwnedBuffer(
                serverLevel,
                getItemStack(),
                serverPlayer.getUUID());

        if (buffer == null) {
            NetworkHandler.sendToPlayer(
                    serverPlayer,
                    new SyncClonerCraftingProgressPacket(
                            this.containerId,
                            BufferRequestState.IDLE.ordinal(),
                            0L,
                            0L,
                            ""));
            return;
        }

        NetworkHandler.sendToPlayer(
                serverPlayer,
                new SyncClonerCraftingProgressPacket(
                        this.containerId,
                        buffer.getRequestState().ordinal(),
                        buffer.getProgressDone(),
                        buffer.getProgressTotal(),
                        buffer.getRequestLabel()));
    }

    private void syncRequirementsToClient() {
        if (!(getPlayer() instanceof ServerPlayer serverPlayer)) {
            return;
        }

        NetworkHandler.sendToPlayer(
                serverPlayer,
                new SyncClonerRequirementStatusPacket(
                        this.containerId,
                        buildRequirementEntries()));

        if (IsModLoaded.AE2
                && hasCraftingUpgradeInstalled()
                && !PortableSpatialCloner.hasItemHandlerLink(getItemStack())
                && serverPlayer.level() instanceof ServerLevel serverLevel) {
            try {
                int status = AE2CraftingBufferOps.getStatus(serverLevel, getItemStack());
                NetworkHandler.sendToPlayer(serverPlayer, new SyncCraftAllStatusPacket(this.containerId, status));
                syncCraftingProgressToClient(serverPlayer, serverLevel);
            } catch (Throwable ignored) {
            }
        }
    }

    private List<SyncClonerRequirementStatusPacket.Entry> buildRequirementEntries() {
        if (!hasStoredStructure()) {
            return List.of();
        }

        byte[] bytes = getStructureBytes();

        if (bytes == null || bytes.length == 0) {
            return List.of();
        }

        CompoundTag structureTag;

        try {
            structureTag = TemplateUtil.decompressNbt(bytes);
        } catch (Exception ignored) {
            return List.of();
        }

        return ClonerRequirementStatus.build(getPlayer(), getItemStack(), structureTag);
    }
}
