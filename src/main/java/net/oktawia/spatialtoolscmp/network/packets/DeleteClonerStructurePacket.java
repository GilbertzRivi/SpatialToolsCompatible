package net.oktawia.spatialtoolscmp.network.packets;

import java.util.List;
import java.util.function.Supplier;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import net.oktawia.spatialtoolscmp.logic.ClonerStructureLibraryStore;
import net.oktawia.spatialtoolscmp.logic.StructureToolPreviewDispatcher;
import net.oktawia.spatialtoolscmp.logic.StructureToolStackState;
import net.oktawia.spatialtoolscmp.menus.PortableSpatialClonerMenu;
import net.oktawia.spatialtoolscmp.network.NetworkHandler;
import net.oktawia.spatialtoolscmp.util.TemplateUtil;

public class DeleteClonerStructurePacket {

    private final int containerId;
    private final String id;

    public DeleteClonerStructurePacket(int containerId, String id) {
        this.containerId = containerId;
        this.id = id == null ? "" : id;
    }

    public static void encode(DeleteClonerStructurePacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.containerId);
        buffer.writeUtf(packet.id, 32767);
    }

    public static DeleteClonerStructurePacket decode(FriendlyByteBuf buffer) {
        return new DeleteClonerStructurePacket(
                buffer.readVarInt(),
                buffer.readUtf(32767));
    }

    public static void handle(DeleteClonerStructurePacket packet, Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();

        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();

            if (player == null || packet.id.isBlank()) {
                return;
            }

            if (!(player.containerMenu instanceof PortableSpatialClonerMenu menu)
                    || menu.containerId != packet.containerId) {
                return;
            }

            ItemStack stack = menu.getItemStack();

            try {
                String selectedId = StructureToolStackState.getStructureId(stack);
                boolean deletingSelected = packet.id.equals(selectedId);

                ClonerStructureLibraryStore.delete(player.server, player.getUUID(), packet.id);

                if (deletingSelected) {
                    StructureToolStackState.clearSelectedClonerLibraryEntry(stack);
                    TemplateUtil.setTemplateOffset(stack.getOrCreateTag(), BlockPos.ZERO);
                    TemplateUtil.setEnergyOrigin(stack.getOrCreateTag(), BlockPos.ZERO);
                    StructureToolPreviewDispatcher.sendPreviewToPlayer(player, null);
                }

                NetworkHandler.sendToPlayer(
                        player,
                        SyncClonerLibraryPacket.fromPlayer(
                                player.server,
                                player.getUUID(),
                                StructureToolStackState.getStructureId(stack)));
            } catch (Exception ignored) {
                NetworkHandler.sendToPlayer(
                        player,
                        SyncClonerLibraryPacket.fromStoreEntries(List.of(), ""));
            }
        });

        context.setPacketHandled(true);
    }
}
