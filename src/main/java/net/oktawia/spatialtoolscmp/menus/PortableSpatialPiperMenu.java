package net.oktawia.spatialtoolscmp.menus;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import net.oktawia.spatialtoolscmp.defs.SpatialMenuRegistrar;
import net.oktawia.spatialtoolscmp.items.PortableSpatialPiper;

public class PortableSpatialPiperMenu extends AbstractPortableStructureToolMenu {

    private static final int BUTTON_CLEAR_TARGET = 30;

    public PortableSpatialPiperMenu(int id, Inventory playerInventory) {
        super(
                SpatialMenuRegistrar.PORTABLE_SPATIAL_PIPER_MENU.get(),
                id,
                playerInventory,
                findToolStack(playerInventory));
    }

    private static ItemStack findToolStack(Inventory inventory) {
        ItemStack main = inventory.player.getMainHandItem();

        if (!main.isEmpty() && main.getItem() instanceof PortableSpatialPiper) {
            return main;
        }

        ItemStack off = inventory.player.getOffhandItem();

        if (!off.isEmpty() && off.getItem() instanceof PortableSpatialPiper) {
            return off;
        }

        return ItemStack.EMPTY;
    }

    public ItemStack getToolStack() {
        return getItemStack();
    }

    public void clearTarget() {
        ItemStack stack = getItemStack();

        if (stack.isEmpty()) {
            return;
        }

        PortableSpatialPiper.setTargetBlock(stack, ItemStack.EMPTY);
        PortableSpatialPiper.clearRoute(stack);

        if (isClientSide()) {
            sendButtonToServer(BUTTON_CLEAR_TARGET);
        }
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (buttonId != BUTTON_CLEAR_TARGET) {
            return super.clickMenuButton(player, buttonId);
        }

        if (player != this.player) {
            return false;
        }

        ItemStack stack = getItemStack();

        if (!stack.isEmpty()) {
            PortableSpatialPiper.setTargetBlock(stack, ItemStack.EMPTY);
            PortableSpatialPiper.clearRoute(stack);
        }

        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        ItemStack stack = getItemStack();

        return !stack.isEmpty()
                && stack.getItem() instanceof PortableSpatialPiper;
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
