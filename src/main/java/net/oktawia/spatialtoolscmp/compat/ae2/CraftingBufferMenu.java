package net.oktawia.spatialtoolscmp.compat.ae2;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.oktawia.spatialtoolscmp.network.NetworkHandler;
import net.oktawia.spatialtoolscmp.network.packets.SyncCraftingBufferStatusPacket;
import org.jetbrains.annotations.Nullable;

public class CraftingBufferMenu extends AbstractContainerMenu {

    @Nullable
    private final CraftingBufferBlockEntity be;
    @Nullable
    private final ServerPlayer serverPlayer;

    private String statusText;
    private String lastSentStatus;

    public CraftingBufferMenu(int id, Inventory inv, Player player, CraftingBufferBlockEntity be) {
        super(AE2BlockRegistrar.CRAFTING_BUFFER_MENU_TYPE.get(), id);
        this.be = be;
        this.serverPlayer = player instanceof ServerPlayer sp ? sp : null;
        this.statusText = computeStatus();
        this.lastSentStatus = this.statusText;
    }

    public CraftingBufferMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        super(AE2BlockRegistrar.CRAFTING_BUFFER_MENU_TYPE.get(), id);
        this.be = null;
        this.serverPlayer = null;
        this.statusText = buf.readUtf();
        this.lastSentStatus = this.statusText;
    }

    @Override
    public boolean stillValid(Player player) {
        return be == null || !be.isRemoved();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (be != null && serverPlayer != null) {
            String current = computeStatus();
            if (!current.equals(lastSentStatus)) {
                lastSentStatus = current;
                NetworkHandler.sendToPlayer(serverPlayer, new SyncCraftingBufferStatusPacket(containerId, current));
            }
        }
    }

    public String getStatusText() {
        return statusText;
    }

    public void updateStatus(String status) {
        this.statusText = status;
    }

    private String computeStatus() {
        if (be == null) return "";
        String err = be.getLastError();
        return err != null ? err : "";
    }
}
