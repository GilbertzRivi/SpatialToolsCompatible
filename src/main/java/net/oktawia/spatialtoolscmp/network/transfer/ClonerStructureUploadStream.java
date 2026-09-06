package net.oktawia.spatialtoolscmp.network.transfer;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import net.oktawia.spatialtoolscmp.logic.ClonerStructureLibraryStore;
import net.oktawia.spatialtoolscmp.logic.ClonerStructureTransfer;
import net.oktawia.spatialtoolscmp.logic.StructureToolPreviewDispatcher;
import net.oktawia.spatialtoolscmp.logic.StructureToolStackState;
import net.oktawia.spatialtoolscmp.menus.PortableSpatialClonerMenu;
import net.oktawia.spatialtoolscmp.network.NetworkHandler;
import net.oktawia.spatialtoolscmp.network.packets.ImportClonerStructurePacket;
import net.oktawia.spatialtoolscmp.network.packets.SyncClonerLibraryPacket;
import net.oktawia.spatialtoolscmp.util.TemplateUtil;

public final class ClonerStructureUploadStream {

    private static final Map<UUID, Incoming> INCOMING = new HashMap<>();

    private ClonerStructureUploadStream() {
    }

    private static final class Incoming {
        private final int containerId;
        private final String name;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        private Incoming(int containerId, String name) {
            this.containerId = containerId;
            this.name = name;
        }
    }

    public static void accept(ServerPlayer player, ImportClonerStructurePacket packet) {
        switch (packet.signal()) {
            case ClonerStructureTransfer.SIGNAL_BEGIN ->
                INCOMING.put(player.getUUID(), new Incoming(packet.containerId(), packet.name()));
            case ClonerStructureTransfer.SIGNAL_DATA -> append(player, packet);
            case ClonerStructureTransfer.SIGNAL_END -> commit(player, packet.containerId());
            default -> forget(player.getUUID());
        }
    }

    public static void forget(UUID playerId) {
        INCOMING.remove(playerId);
    }

    private static void append(ServerPlayer player, ImportClonerStructurePacket packet) {
        Incoming incoming = INCOMING.get(player.getUUID());

        if (incoming == null) {
            return;
        }

        if (incoming.containerId != packet.containerId()
                || incoming.buffer.size() + packet.bytes().length > ClonerStructureTransfer.MAX_TRANSFER_BYTES) {
            forget(player.getUUID());
            return;
        }

        incoming.buffer.writeBytes(packet.bytes());
    }

    private static void commit(ServerPlayer player, int containerId) {
        Incoming incoming = INCOMING.remove(player.getUUID());

        if (incoming == null || incoming.containerId != containerId || incoming.buffer.size() == 0) {
            return;
        }

        if (!(player.containerMenu instanceof PortableSpatialClonerMenu menu)
                || menu.containerId != containerId) {
            return;
        }

        ItemStack stack = menu.getItemStack();

        try {
            ClonerStructureLibraryStore.Entry entry = ClonerStructureLibraryStore.importBytes(
                    player.server,
                    player.getUUID(),
                    incoming.buffer.toByteArray(),
                    incoming.name);

            CompoundTag tag = ClonerStructureLibraryStore.load(
                    player.server,
                    player.getUUID(),
                    entry.id());

            StructureToolStackState.setSelectedClonerLibraryEntry(
                    stack,
                    player.getUUID(),
                    entry.id());

            if (tag != null) {
                TemplateUtil.copyPreviewTransformState(tag, stack.getOrCreateTag());
            }

            NetworkHandler.sendToPlayer(
                    player,
                    SyncClonerLibraryPacket.fromStoreEntries(
                            ClonerStructureLibraryStore.list(player.server, player.getUUID()),
                            StructureToolStackState.getStructureId(stack)));

            StructureToolPreviewDispatcher.sendPreviewToPlayer(player, tag);
        } catch (Exception ignored) {
            NetworkHandler.sendToPlayer(
                    player,
                    SyncClonerLibraryPacket.fromStoreEntries(List.of(),
                            StructureToolStackState.getStructureId(stack)));
        }
    }
}
