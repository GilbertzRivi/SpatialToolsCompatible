package net.oktawia.spatialtoolscmp.menus;

import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import net.oktawia.spatialtoolscmp.defs.SpatialMenuRegistrar;
import net.oktawia.spatialtoolscmp.items.PortableSpatialCloner;
import net.oktawia.spatialtoolscmp.items.PortableSpatialStorage;
import net.oktawia.spatialtoolscmp.logic.StructureToolStackState;
import net.oktawia.spatialtoolscmp.logic.StructureToolStructureStore;
import net.oktawia.spatialtoolscmp.logic.StructureToolUtil;
import net.oktawia.spatialtoolscmp.util.TemplateUtil;

public class PortableSpatialStorageMenu extends AbstractPortableStructureToolMenu {

    public PortableSpatialStorageMenu(int id, Inventory playerInventory) {
        super(
                SpatialMenuRegistrar.PORTABLE_SPATIAL_STORAGE_MENU.get(),
                id,
                playerInventory,
                findToolStack(playerInventory));
    }

    private static ItemStack findToolStack(Inventory playerInventory) {
        ItemStack stack = StructureToolUtil.findActive(
                playerInventory.player,
                PortableSpatialStorage.class,
                PortableSpatialCloner.class);

        if (stack.isEmpty()) {
            stack = StructureToolUtil.findHeld(
                    playerInventory.player,
                    PortableSpatialStorage.class,
                    PortableSpatialCloner.class);
        }

        return stack;
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

        String id = StructureToolStackState.getStructureId(getItemStack());

        if (id.isBlank()) {
            return null;
        }

        try {
            CompoundTag tag = StructureToolStructureStore.load(serverPlayer.server, id);

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
            String id = StructureToolStackState.getStructureId(getItemStack());

            if (id.isBlank()) {
                id = UUID.randomUUID().toString();
                StructureToolStackState.setStructureId(getItemStack(), id);
            }

            StructureToolStructureStore.save(serverPlayer.server, id, tag);
        } catch (Exception ignored) {
        }
    }
}
