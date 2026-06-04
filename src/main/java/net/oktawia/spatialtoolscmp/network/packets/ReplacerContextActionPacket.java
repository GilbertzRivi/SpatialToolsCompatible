package net.oktawia.spatialtoolscmp.network.packets;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;
import net.oktawia.spatialtoolscmp.items.PortableSpatialReplacer;

import java.util.function.Supplier;

public class ReplacerContextActionPacket {

    public static final int RADIUS_UP = 1;
    public static final int RADIUS_DOWN = 2;
    public static final int TOGGLE_CONNECTIVITY = 3;
    public static final int TOGGLE_BLOCKSTATE = 4;

    private final int action;

    public ReplacerContextActionPacket(int action) {
        this.action = action;
    }

    public static void encode(ReplacerContextActionPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.action);
    }

    public static ReplacerContextActionPacket decode(FriendlyByteBuf buf) {
        return new ReplacerContextActionPacket(buf.readVarInt());
    }

    public static void handle(ReplacerContextActionPacket packet, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();

        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();
            if (player == null) return;

            ItemStack stack = player.getMainHandItem();
            if (stack.isEmpty() || !(stack.getItem() instanceof PortableSpatialReplacer)) return;

            switch (packet.action) {
                case RADIUS_UP -> PortableSpatialReplacer.setRadius(stack, PortableSpatialReplacer.getRadius(stack) + 1);
                case RADIUS_DOWN -> PortableSpatialReplacer.setRadius(stack, PortableSpatialReplacer.getRadius(stack) - 1);
                case TOGGLE_CONNECTIVITY -> PortableSpatialReplacer.cycleConnectivityMode(stack);
                case TOGGLE_BLOCKSTATE -> PortableSpatialReplacer.toggleSameBlockstate(stack);
            }

            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
            player.inventoryMenu.broadcastChanges();
        });

        context.setPacketHandled(true);
    }
}
