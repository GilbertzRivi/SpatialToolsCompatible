package net.oktawia.spatialtoolscmp.network;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;
import net.oktawia.spatialtoolscmp.SpatialToolsCMP;
import net.oktawia.spatialtoolscmp.network.packets.*;

public final class NetworkHandler {

    private static final String PROTOCOL_VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            SpatialToolsCMP.makeId("main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals
    );

    private static int nextId = 0;

    private NetworkHandler() {}

    public static void registerMessages() {
        CHANNEL.messageBuilder(SendLongStringToClientPacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SendLongStringToClientPacket::encode)
                .decoder(SendLongStringToClientPacket::decode)
                .consumerMainThread(SendLongStringToClientPacket::handle)
                .add();


        CHANNEL.messageBuilder(RequestStructureToolPreviewPacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RequestStructureToolPreviewPacket::encode)
                .decoder(RequestStructureToolPreviewPacket::decode)
                .consumerMainThread(RequestStructureToolPreviewPacket::handle)
                .add();

        CHANNEL.messageBuilder(ShowHudMessagePacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ShowHudMessagePacket::encode)
                .decoder(ShowHudMessagePacket::decode)
                .consumerMainThread(ShowHudMessagePacket::handle)
                .add();

        CHANNEL.messageBuilder(SyncClonerRequirementStatusPacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncClonerRequirementStatusPacket::encode)
                .decoder(SyncClonerRequirementStatusPacket::decode)
                .consumerMainThread(SyncClonerRequirementStatusPacket::handle)
                .add();

        CHANNEL.messageBuilder(RequestClonerLibraryPacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RequestClonerLibraryPacket::encode)
                .decoder(RequestClonerLibraryPacket::decode)
                .consumerMainThread(RequestClonerLibraryPacket::handle)
                .add();

        CHANNEL.messageBuilder(SelectClonerStructurePacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SelectClonerStructurePacket::encode)
                .decoder(SelectClonerStructurePacket::decode)
                .consumerMainThread(SelectClonerStructurePacket::handle)
                .add();

        CHANNEL.messageBuilder(RenameClonerStructurePacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RenameClonerStructurePacket::encode)
                .decoder(RenameClonerStructurePacket::decode)
                .consumerMainThread(RenameClonerStructurePacket::handle)
                .add();

        CHANNEL.messageBuilder(SyncClonerLibraryPacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncClonerLibraryPacket::encode)
                .decoder(SyncClonerLibraryPacket::decode)
                .consumerMainThread(SyncClonerLibraryPacket::handle)
                .add();

        CHANNEL.messageBuilder(DeleteClonerStructurePacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(DeleteClonerStructurePacket::encode)
                .decoder(DeleteClonerStructurePacket::decode)
                .consumerMainThread(DeleteClonerStructurePacket::handle)
                .add();

        CHANNEL.messageBuilder(ExportClonerStructurePacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ExportClonerStructurePacket::encode)
                .decoder(ExportClonerStructurePacket::decode)
                .consumerMainThread(ExportClonerStructurePacket::handle)
                .add();

        CHANNEL.messageBuilder(ExportClonerStructureResultPacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(ExportClonerStructureResultPacket::encode)
                .decoder(ExportClonerStructureResultPacket::decode)
                .consumerMainThread(ExportClonerStructureResultPacket::handle)
                .add();

        CHANNEL.messageBuilder(ImportClonerStructurePacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(ImportClonerStructurePacket::encode)
                .decoder(ImportClonerStructurePacket::decode)
                .consumerMainThread(ImportClonerStructurePacket::handle)
                .add();

        CHANNEL.messageBuilder(RequestClonerCraftingPacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RequestClonerCraftingPacket::encode)
                .decoder(RequestClonerCraftingPacket::decode)
                .consumerMainThread(RequestClonerCraftingPacket::handle)
                .add();

        CHANNEL.messageBuilder(SetClonerNestedInventoryModePacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(SetClonerNestedInventoryModePacket::encode)
                .decoder(SetClonerNestedInventoryModePacket::decode)
                .consumerMainThread(SetClonerNestedInventoryModePacket::handle)
                .add();

        CHANNEL.messageBuilder(StructureToolContextActionPacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(StructureToolContextActionPacket::encode)
                .decoder(StructureToolContextActionPacket::decode)
                .consumerMainThread(StructureToolContextActionPacket::handle)
                .add();

        CHANNEL.messageBuilder(CreateClonerFolderPacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(CreateClonerFolderPacket::encode)
                .decoder(CreateClonerFolderPacket::decode)
                .consumerMainThread(CreateClonerFolderPacket::handle)
                .add();

        CHANNEL.messageBuilder(DeleteClonerFolderPacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(DeleteClonerFolderPacket::encode)
                .decoder(DeleteClonerFolderPacket::decode)
                .consumerMainThread(DeleteClonerFolderPacket::handle)
                .add();

        CHANNEL.messageBuilder(MoveClonerStructureToFolderPacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(MoveClonerStructureToFolderPacket::encode)
                .decoder(MoveClonerStructureToFolderPacket::decode)
                .consumerMainThread(MoveClonerStructureToFolderPacket::handle)
                .add();

        CHANNEL.messageBuilder(SyncCraftAllStatusPacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncCraftAllStatusPacket::encode)
                .decoder(SyncCraftAllStatusPacket::decode)
                .consumerMainThread(SyncCraftAllStatusPacket::handle)
                .add();

        CHANNEL.messageBuilder(RequestCraftAllPacket.class, nextId++, NetworkDirection.PLAY_TO_SERVER)
                .encoder(RequestCraftAllPacket::encode)
                .decoder(RequestCraftAllPacket::decode)
                .consumerMainThread(RequestCraftAllPacket::handle)
                .add();

        CHANNEL.messageBuilder(SyncCraftingBufferStatusPacket.class, nextId++, NetworkDirection.PLAY_TO_CLIENT)
                .encoder(SyncCraftingBufferStatusPacket::encode)
                .decoder(SyncCraftingBufferStatusPacket::decode)
                .consumerMainThread(SyncCraftingBufferStatusPacket::handle)
                .add();
    }

    public static void sendToPlayer(ServerPlayer player, Object packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
    }

    public static void sendToTrackingChunk(LevelChunk chunk, Object packet) {
        CHANNEL.send(PacketDistributor.TRACKING_CHUNK.with(() -> chunk), packet);
    }

    public static void sendToServer(Object packet) {
        CHANNEL.sendToServer(packet);
    }
}