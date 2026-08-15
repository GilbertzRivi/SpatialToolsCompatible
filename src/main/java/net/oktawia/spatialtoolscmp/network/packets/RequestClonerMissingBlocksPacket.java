package net.oktawia.spatialtoolscmp.network.packets;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import net.oktawia.spatialtoolscmp.items.PortableSpatialCloner;
import net.oktawia.spatialtoolscmp.logic.ClonerRequirementStatus;
import net.oktawia.spatialtoolscmp.network.NetworkHandler;

public record RequestClonerMissingBlocksPacket() {

    public static void encode(RequestClonerMissingBlocksPacket packet, FriendlyByteBuf buffer) {
    }

    public static RequestClonerMissingBlocksPacket decode(FriendlyByteBuf buffer) {
        return new RequestClonerMissingBlocksPacket();
    }

    public static void handle(
            RequestClonerMissingBlocksPacket packet,
            Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();

        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();

            if (player == null) {
                return;
            }

            NetworkHandler.sendToPlayer(
                    player,
                    new SyncClonerMissingBlocksPacket(hasMissingBlocks(player)));
        });

        context.setPacketHandled(true);
    }

    private static boolean hasMissingBlocks(ServerPlayer player) {
        ItemStack stack = player.getMainHandItem();

        if (!(stack.getItem() instanceof PortableSpatialCloner)) {
            return false;
        }

        return ClonerRequirementStatus.hasMissing(
                ClonerRequirementStatus.buildForSelectedStructure(player, stack));
    }
}
