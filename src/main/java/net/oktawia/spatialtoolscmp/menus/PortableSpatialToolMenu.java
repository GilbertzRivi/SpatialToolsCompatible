package net.oktawia.spatialtoolscmp.menus;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.oktawia.spatialtoolscmp.defs.SpatialMenuRegistrar;
import net.oktawia.spatialtoolscmp.items.PortableSpatialTool;

public class PortableSpatialToolMenu extends AbstractPortableStructureToolMenu {

    public PortableSpatialToolMenu(int id, Inventory playerInventory) {
        super(
                SpatialMenuRegistrar.PORTABLE_SPATIAL_TOOL_MENU.get(),
                id,
                playerInventory,
                findToolStack(playerInventory)
        );
    }

    private static ItemStack findToolStack(Inventory inventory) {
        ItemStack main = inventory.player.getMainHandItem();

        if (!main.isEmpty() && main.getItem() instanceof PortableSpatialTool) {
            return main;
        }

        ItemStack off = inventory.player.getOffhandItem();

        if (!off.isEmpty() && off.getItem() instanceof PortableSpatialTool) {
            return off;
        }

        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        ItemStack stack = getItemStack();

        return !stack.isEmpty()
                && stack.getItem() instanceof PortableSpatialTool;
    }

    @Override
    protected boolean hasStoredStructure() {
        return false;
    }

    @Override
    protected byte[] getStructureBytes() {
        return null;
    }

    @Override
    protected void setStructureBytes(byte[] bytes) {
    }
}
