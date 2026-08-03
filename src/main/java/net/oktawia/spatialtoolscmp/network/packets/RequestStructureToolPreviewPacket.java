package net.oktawia.spatialtoolscmp.network.packets;

import java.util.function.Supplier;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

import net.oktawia.spatialtoolscmp.items.PortableSpatialCloner;
import net.oktawia.spatialtoolscmp.items.PortableSpatialStorage;
import net.oktawia.spatialtoolscmp.logic.StructureToolPreviewDispatcher;
import net.oktawia.spatialtoolscmp.logic.StructureToolStackState;
import net.oktawia.spatialtoolscmp.logic.StructureToolUtil;
import net.oktawia.spatialtoolscmp.menus.PortableSpatialClonerMenu;

public class RequestStructureToolPreviewPacket {

    public static void encode(RequestStructureToolPreviewPacket packet, FriendlyByteBuf buffer) {
    }

    public static RequestStructureToolPreviewPacket decode(FriendlyByteBuf buffer) {
        return new RequestStructureToolPreviewPacket();
    }

    public static void handle(RequestStructureToolPreviewPacket packet,
            Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();

        context.enqueueWork(() -> {
            ServerPlayer sender = context.getSender();

            if (sender == null) {
                return;
            }

            ItemStack stack = ItemStack.EMPTY;

            if (sender.containerMenu instanceof PortableSpatialClonerMenu menu) {
                stack = menu.getItemStack();
            }

            if (stack.isEmpty()) {
                stack = StructureToolUtil.findActive(
                        sender,
                        PortableSpatialStorage.class,
                        PortableSpatialCloner.class);
            }

            if (stack.isEmpty()) {
                stack = StructureToolUtil.findHeld(
                        sender,
                        PortableSpatialStorage.class,
                        PortableSpatialCloner.class);
            }

            if (stack.isEmpty()) {
                StructureToolPreviewDispatcher.sendPreviewToPlayer(sender, null);
                return;
            }

            if (stack.getItem() instanceof PortableSpatialCloner) {
                StructureToolPreviewDispatcher.sendClonerPreviewForSelectedStructure(sender, stack);
                return;
            }

            String structureId = StructureToolStackState.getStructureId(stack);

            if (structureId.isBlank()) {
                StructureToolPreviewDispatcher.sendPreviewToPlayer(sender, null);
                return;
            }

            StructureToolPreviewDispatcher.sendPreviewForStructureId(sender, structureId);
        });

        context.setPacketHandled(true);
    }
}
