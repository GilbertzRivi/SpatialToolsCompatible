package net.oktawia.spatialtoolscmp.network.packets;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import net.oktawia.spatialtoolscmp.items.helpers.SpatialMultiTool;
import net.oktawia.spatialtoolscmp.logic.StructureToolPreviewDispatcher;
import net.oktawia.spatialtoolscmp.menus.AbstractPortableStructureToolMenu;

public class SwapSpatialToolPacket {

    private final int modeIndex;

    public SwapSpatialToolPacket(SpatialMultiTool.Mode mode) {
        this.modeIndex = mode.ordinal();
    }

    private SwapSpatialToolPacket(int modeIndex) {
        this.modeIndex = modeIndex;
    }

    public static void encode(SwapSpatialToolPacket packet, FriendlyByteBuf buf) {
        buf.writeVarInt(packet.modeIndex);
    }

    public static SwapSpatialToolPacket decode(FriendlyByteBuf buf) {
        return new SwapSpatialToolPacket(buf.readVarInt());
    }

    public static void handle(
            SwapSpatialToolPacket packet,
            Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();

        context.enqueueWork(() -> {
            ServerPlayer player = context.getSender();

            if (player == null) {
                return;
            }

            if (packet.modeIndex < 0 || packet.modeIndex >= SpatialMultiTool.MODES.size()) {
                return;
            }

            InteractionHand hand = SpatialMultiTool.isMultiTool(player.getMainHandItem())
                    ? InteractionHand.MAIN_HAND
                    : InteractionHand.OFF_HAND;

            ItemStack held = player.getItemInHand(hand);

            if (!SpatialMultiTool.isMultiTool(held)) {
                return;
            }

            SpatialMultiTool.Mode target = SpatialMultiTool.MODES.get(packet.modeIndex);

            if (SpatialMultiTool.getMode(held) == target) {
                return;
            }

            boolean toolMenuWasOpen = player.containerMenu instanceof AbstractPortableStructureToolMenu;

            if (toolMenuWasOpen) {
                player.closeContainer();
            }

            player.setItemInHand(hand, SpatialMultiTool.swap(held, target));

            player.getInventory().setChanged();
            player.containerMenu.broadcastChanges();
            player.inventoryMenu.broadcastChanges();

            StructureToolPreviewDispatcher.sendPreviewToPlayer(player, null);

            if (toolMenuWasOpen) {
                target.item().openMenu(player, hand);
            }
        });

        context.setPacketHandled(true);
    }
}
