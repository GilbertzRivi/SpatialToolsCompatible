package net.oktawia.spatialtoolscmp.menus;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import net.oktawia.spatialtoolscmp.defs.SpatialMenuRegistrar;
import net.oktawia.spatialtoolscmp.items.PortableSpatialReplacer;
import net.oktawia.spatialtoolscmp.logic.ReplacerContext.ConnectivityMode;
import net.oktawia.spatialtoolscmp.network.NetworkHandler;
import net.oktawia.spatialtoolscmp.network.packets.ReplacerContextActionPacket;

public class PortableSpatialReplacerMenu extends AbstractPortableStructureToolMenu {

    public PortableSpatialReplacerMenu(int id, Inventory playerInventory) {
        super(
                SpatialMenuRegistrar.PORTABLE_SPATIAL_REPLACER_MENU.get(),
                id,
                playerInventory,
                findToolStack(playerInventory));
    }

    private static ItemStack findToolStack(Inventory inventory) {
        ItemStack main = inventory.player.getMainHandItem();

        if (!main.isEmpty() && main.getItem() instanceof PortableSpatialReplacer) {
            return main;
        }

        ItemStack off = inventory.player.getOffhandItem();

        if (!off.isEmpty() && off.getItem() instanceof PortableSpatialReplacer) {
            return off;
        }

        return ItemStack.EMPTY;
    }

    public ItemStack getToolStack() {
        return getItemStack();
    }

    public void radiusDown() {
        ItemStack stack = getItemStack();

        if (stack.isEmpty()) {
            return;
        }

        PortableSpatialReplacer.setRadius(
                stack,
                PortableSpatialReplacer.getRadius(stack) - 1);

        if (isClientSide()) {
            NetworkHandler.sendToServer(new ReplacerContextActionPacket(
                    ReplacerContextActionPacket.RADIUS_DOWN));
        }
    }

    public void radiusUp() {
        ItemStack stack = getItemStack();

        if (stack.isEmpty()) {
            return;
        }

        PortableSpatialReplacer.setRadius(
                stack,
                PortableSpatialReplacer.getRadius(stack) + 1);

        if (isClientSide()) {
            NetworkHandler.sendToServer(new ReplacerContextActionPacket(
                    ReplacerContextActionPacket.RADIUS_UP));
        }
    }

    public void toggleBlockstateMode() {
        ItemStack stack = getItemStack();

        if (stack.isEmpty()) {
            return;
        }

        PortableSpatialReplacer.toggleSameBlockstate(stack);

        if (isClientSide()) {
            NetworkHandler.sendToServer(new ReplacerContextActionPacket(
                    ReplacerContextActionPacket.TOGGLE_BLOCKSTATE));
        }
    }

    public void toggleConnectivity() {
        ItemStack stack = getItemStack();

        if (stack.isEmpty()) {
            return;
        }

        ConnectivityMode current = PortableSpatialReplacer.getConnectivityMode(stack);

        PortableSpatialReplacer.setConnectivityMode(
                stack,
                current == ConnectivityMode.DIRECT
                        ? ConnectivityMode.DIAGONAL
                        : ConnectivityMode.DIRECT);

        if (isClientSide()) {
            NetworkHandler.sendToServer(new ReplacerContextActionPacket(
                    ReplacerContextActionPacket.TOGGLE_CONNECTIVITY));
        }
    }

    @Override
    public boolean stillValid(Player player) {
        ItemStack stack = getItemStack();

        return !stack.isEmpty()
                && stack.getItem() instanceof PortableSpatialReplacer;
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
