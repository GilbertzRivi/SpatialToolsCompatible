package net.oktawia.spatialtoolscmp.client.renderer.message;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.oktawia.spatialtoolscmp.network.packets.ShowHudMessagePacket;

@OnlyIn(Dist.CLIENT)
public final class ClientHudPacketHandler {

    private ClientHudPacketHandler() {
    }

    public static void handleShowHudMessage(ShowHudMessagePacket msg) {
        ClientHotbarMessage.Line[] lines = msg.lines().stream()
                .map(line -> ClientHotbarMessage.line(line.text(), line.color()))
                .toArray(ClientHotbarMessage.Line[]::new);

        ClientHotbarMessage.show(msg.durationTicks(), lines);
    }
}