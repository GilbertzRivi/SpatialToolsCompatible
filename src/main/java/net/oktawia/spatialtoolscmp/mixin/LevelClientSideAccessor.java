package net.oktawia.spatialtoolscmp.mixin;

import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Level.class)
public interface LevelClientSideAccessor {

    @Mutable
    @Accessor("isClientSide")
    void setClientSide(boolean clientSide);
}
