package net.oktawia.spatialtoolscmp.mixin.ae2;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import appeng.client.render.cablebus.CableBusBakedModel;
import appeng.client.render.cablebus.FacadeBuilder;

@Mixin(value = CableBusBakedModel.class, remap = false)
public interface CableBusBakedModelAccessor {

    @Accessor("facadeBuilder")
    FacadeBuilder getFacadeBuilder();
}
