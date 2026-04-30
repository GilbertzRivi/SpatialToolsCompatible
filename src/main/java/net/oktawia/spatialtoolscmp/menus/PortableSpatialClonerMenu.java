package net.oktawia.spatialtoolscmp.menus;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import net.oktawia.spatialtoolscmp.IsModLoaded;
import net.oktawia.spatialtoolscmp.compat.ae2.AE2MEOps;
import net.oktawia.spatialtoolscmp.defs.SpatialMenuRegistrar;
import net.oktawia.spatialtoolscmp.items.PortableSpatialCloner;
import net.oktawia.spatialtoolscmp.logic.ClonerStructureLibraryStore;
import net.oktawia.spatialtoolscmp.logic.StructureToolStackState;
import net.oktawia.spatialtoolscmp.network.NetworkHandler;
import net.oktawia.spatialtoolscmp.network.packets.SetClonerNestedInventoryModePacket;
import net.oktawia.spatialtoolscmp.network.packets.RequestClonerCraftingPacket;
import net.oktawia.spatialtoolscmp.network.packets.SyncClonerLibraryPacket;
import net.oktawia.spatialtoolscmp.network.packets.SyncClonerRequirementStatusPacket;
import net.oktawia.spatialtoolscmp.util.StructureToolKeys;
import net.oktawia.spatialtoolscmp.util.TemplateUtil;

import java.util.ArrayList;
import java.util.List;

public class PortableSpatialClonerMenu extends AbstractPortableStructureToolMenu {

    private int requirementSyncTick = 0;

    public PortableSpatialClonerMenu(int id, Inventory playerInventory) {
        super(
                SpatialMenuRegistrar.PORTABLE_SPATIAL_CLONER_MENU.get(),
                id,
                playerInventory,
                findToolStack(playerInventory)
        );

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
                    getItemStack()
            );

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
                        tag
                );

                StructureToolStackState.setSelectedClonerLibraryEntry(
                        getItemStack(),
                        serverPlayer.getUUID(),
                        entry.id()
                );

                syncLibraryToClient();
                syncRequirementsToClient();
                return;
            }

            ClonerStructureLibraryStore.saveExisting(
                    serverPlayer.server,
                    serverPlayer.getUUID(),
                    currentId,
                    tag
            );

            StructureToolStackState.setSelectedClonerLibraryEntry(
                    getItemStack(),
                    serverPlayer.getUUID(),
                    currentId
            );

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
                            StructureToolStackState.getStructureId(getItemStack())
                    )
            );
        } catch (Exception ignored) {
            NetworkHandler.sendToPlayer(
                    serverPlayer,
                    SyncClonerLibraryPacket.fromStoreEntries(
                            List.of(),
                            StructureToolStackState.getStructureId(getItemStack())
                    )
            );
        }
    }

    public void cycleNestedInventoryMode() {
        PortableSpatialCloner.NestedInventoryResourceMode next =
                PortableSpatialCloner.getNestedInventoryResourceMode(getItemStack()).next();

        PortableSpatialCloner.setNestedInventoryResourceMode(getItemStack(), next);

        if (isClientSide()) {
            NetworkHandler.sendToServer(new SetClonerNestedInventoryModePacket(
                    this.containerId,
                    next.id()
            ));
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
                    amount
            ));
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

    private void syncRequirementsToClient() {
        if (!(getPlayer() instanceof ServerPlayer serverPlayer)) {
            return;
        }

        NetworkHandler.sendToPlayer(
                serverPlayer,
                new SyncClonerRequirementStatusPacket(
                        this.containerId,
                        buildRequirementEntries()
                )
        );
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

        if (!structureTag.contains(StructureToolKeys.CLONE_METADATA_KEY, Tag.TAG_COMPOUND)) {
            return List.of();
        }

        CompoundTag metadata = structureTag.getCompound(StructureToolKeys.CLONE_METADATA_KEY);

        if (!metadata.contains(StructureToolKeys.CLONE_REQUIREMENTS_KEY, Tag.TAG_LIST)) {
            return List.of();
        }

        ListTag requirements = metadata.getList(
                StructureToolKeys.CLONE_REQUIREMENTS_KEY,
                Tag.TAG_COMPOUND
        );

        List<SyncClonerRequirementStatusPacket.Entry> out = new ArrayList<>();

        PortableSpatialCloner.NestedInventoryResourceMode nestedMode =
                PortableSpatialCloner.getNestedInventoryResourceMode(getItemStack());

        for (int i = 0; i < requirements.size(); i++) {
            CompoundTag row = requirements.getCompound(i);

            if (!row.contains(StructureToolKeys.CLONE_KEY_STACK, Tag.TAG_COMPOUND)) {
                continue;
            }

            ItemStack stack = ItemStack.of(row.getCompound(StructureToolKeys.CLONE_KEY_STACK));

            if (stack.isEmpty()) {
                continue;
            }

            stack = stack.copy();
            stack.setCount(1);

            long required = Math.max(1L, row.getLong(StructureToolKeys.CLONE_KEY_COUNT));
            long available = countPlayerInventory(stack);
            boolean craftable = false;

            if (getPlayer().level() instanceof ServerLevel serverLevel) {
                if (nestedMode.usePlayerNested()) {
                    available += PortableSpatialCloner.countNestedInventoryInPlayerInventory(
                            serverLevel,
                            getPlayer(),
                            getItemStack(),
                            stack
                    );
                }

                if (PortableSpatialCloner.hasItemHandlerLink(getItemStack())) {
                    available += PortableSpatialCloner.countLinkedItemHandlerStorage(
                            serverLevel,
                            getItemStack(),
                            stack
                    );

                    if (nestedMode.useConnectedNested()) {
                        available += PortableSpatialCloner.countNestedInventoryInLinkedItemHandlerStorage(
                                serverLevel,
                                getItemStack(),
                                stack
                        );
                    }
                } else if (IsModLoaded.AE2) {
                    try {
                        available += AE2MEOps.getAmount(
                                stack,
                                getItemStack(),
                                serverLevel
                        );
                    } catch (Throwable ignored) {
                    }

                    try {
                        craftable = hasCraftingUpgradeInstalled()
                                && AE2MEOps.isCraftable(
                                stack,
                                getItemStack(),
                                serverLevel
                        );
                    } catch (Throwable ignored) {
                        craftable = false;
                    }
                }
            }

            out.add(new SyncClonerRequirementStatusPacket.Entry(
                    stack,
                    available,
                    required,
                    craftable
            ));
        }

        return out;
    }

    private long countPlayerInventory(ItemStack targetStack) {
        if (targetStack.isEmpty()) {
            return 0L;
        }

        long total = 0L;
        Inventory inventory = getPlayer().getInventory();

        for (ItemStack stack : inventory.items) {
            if (!stack.isEmpty() && ItemStack.isSameItemSameTags(stack, targetStack)) {
                total += stack.getCount();
            }
        }

        for (ItemStack stack : inventory.offhand) {
            if (!stack.isEmpty() && ItemStack.isSameItemSameTags(stack, targetStack)) {
                total += stack.getCount();
            }
        }

        return total;
    }
}