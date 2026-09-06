package net.oktawia.spatialtoolscmp.network.transfer;

import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import net.oktawia.spatialtoolscmp.SpatialToolsCMP;

@Mod.EventBusSubscriber(modid = SpatialToolsCMP.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClonerStructureTransferServerEvents {

    private ClonerStructureTransferServerEvents() {
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        ClonerStructureUploadStream.forget(event.getEntity().getUUID());
    }
}
