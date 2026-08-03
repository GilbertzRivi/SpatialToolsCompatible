package net.oktawia.spatialtoolscmp.mixin.cbmultipart;

import codechicken.lib.data.MCDataOutput;
import codechicken.multipart.api.part.MultiPart;
import codechicken.multipart.network.MultiPartSPH;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Consumer;

@Mixin(value = MultiPartSPH.class, remap = false)
public class MultiPartSPHMixin {

    @Inject(method = "dispatchPartUpdate", at = @At("HEAD"), cancellable = true)
    private static void spatialtoolscmp$skipUpdatesOutsideServerLevel(
            MultiPart part,
            Consumer<MCDataOutput> data,
            CallbackInfo ci
    ) {
        if (!(part.level() instanceof ServerLevel)) {
            ci.cancel();
        }
    }
}
