package net.oktawia.spatialtoolscmp.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import net.minecraft.world.level.Level;

@Mixin(Level.class)
public interface LevelClientSideAccessor {

    @Mutable
    @Accessor("isClientSide")
    void setClientSide(boolean clientSide);
}
