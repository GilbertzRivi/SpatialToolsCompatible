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

import java.util.ArrayList;
import java.util.List;

public class CraftingBufferMenu extends AbstractContainerMenu {

    public record ItemEntry(ItemStack stack, long requestedAmount, long bufferedAmount) {}

    @Nullable
    private final CraftingBufferBlockEntity be;

    @Nullable
    private final ServerPlayer serverPlayer;

    private boolean hasError;
    private List<ItemEntry> entries;

    private boolean lastSentHasError;
    private List<ItemEntry> lastSentEntries;

    public CraftingBufferMenu(int id, Inventory inv, Player player, CraftingBufferBlockEntity be) {
        super(AE2BlockRegistrar.CRAFTING_BUFFER_MENU_TYPE.get(), id);

        this.be = be;
        this.serverPlayer = player instanceof ServerPlayer sp ? sp : null;

        this.hasError = computeHasError();
        this.entries = computeEntries();

        this.lastSentHasError = this.hasError;
        this.lastSentEntries = List.copyOf(this.entries);
    }

    public CraftingBufferMenu(int id, Inventory inv, FriendlyByteBuf buf) {
        super(AE2BlockRegistrar.CRAFTING_BUFFER_MENU_TYPE.get(), id);

        this.be = null;
        this.serverPlayer = null;

        this.hasError = buf.readBoolean();

        int count = buf.readVarInt();
        List<ItemEntry> list = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            ItemStack stack = buf.readItem();
            long requestedAmount = buf.readLong();
            long bufferedAmount = buf.readLong();

            list.add(new ItemEntry(stack, requestedAmount, bufferedAmount));
        }

        this.entries = List.copyOf(list);

        this.lastSentHasError = this.hasError;
        this.lastSentEntries = this.entries;
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
            boolean currentHasError = computeHasError();
            List<ItemEntry> currentEntries = computeEntries();

            if (currentHasError != lastSentHasError || !entriesEqual(currentEntries, lastSentEntries)) {
                lastSentHasError = currentHasError;
                lastSentEntries = List.copyOf(currentEntries);

                sendStateToClient(currentHasError, currentEntries);
            }
        }
    }

    public boolean isHasError() {
        return hasError;
    }

    public List<ItemEntry> getEntries() {
        return entries;
    }

    public void updateState(
            boolean hasError,
            List<ItemStack> stacks,
            List<Long> requestedAmounts,
            List<Long> bufferedAmounts
    ) {
        this.hasError = hasError;

        List<ItemEntry> list = new ArrayList<>(stacks.size());

        for (int i = 0; i < stacks.size(); i++) {
            list.add(new ItemEntry(
                    stacks.get(i),
                    requestedAmounts.get(i),
                    bufferedAmounts.get(i)
            ));
        }

        this.entries = List.copyOf(list);
    }

    private boolean computeHasError() {
        return be != null && be.hasDisplayError();
    }

    private List<ItemEntry> computeEntries() {
        if (be == null) {
            return List.of();
        }

        List<CraftingBufferBlockEntity.DisplayEntry> displayEntries = be.getDisplayEntries();
        List<ItemEntry> list = new ArrayList<>(displayEntries.size());

        for (CraftingBufferBlockEntity.DisplayEntry entry : displayEntries) {
            list.add(new ItemEntry(
                    entry.stack(),
                    entry.requestedAmount(),
                    entry.bufferedAmount()
            ));
        }

        return List.copyOf(list);
    }

    private void sendStateToClient(boolean hasError, List<ItemEntry> currentEntries) {
        List<ItemStack> stacks = new ArrayList<>(currentEntries.size());
        List<Long> requestedAmounts = new ArrayList<>(currentEntries.size());
        List<Long> bufferedAmounts = new ArrayList<>(currentEntries.size());

        for (ItemEntry e : currentEntries) {
            stacks.add(e.stack());
            requestedAmounts.add(e.requestedAmount());
            bufferedAmounts.add(e.bufferedAmount());
        }

        NetworkHandler.sendToPlayer(
                serverPlayer,
                new SyncCraftingBufferStatusPacket(
                        containerId,
                        hasError,
                        stacks,
                        requestedAmounts,
                        bufferedAmounts
                )
        );
    }

    private static boolean entriesEqual(List<ItemEntry> a, List<ItemEntry> b) {
        if (a.size() != b.size()) {
            return false;
        }

        for (int i = 0; i < a.size(); i++) {
            ItemEntry ea = a.get(i);
            ItemEntry eb = b.get(i);

            if (ea.requestedAmount() != eb.requestedAmount()) {
                return false;
            }

            if (ea.bufferedAmount() != eb.bufferedAmount()) {
                return false;
            }

            if (!ItemStack.matches(ea.stack(), eb.stack())) {
                return false;
            }
        }

        return true;
    }
}