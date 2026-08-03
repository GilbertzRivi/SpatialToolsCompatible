package net.oktawia.spatialtoolscmp.logic.extensions;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.oktawia.spatialtoolscmp.logic.ClientPiperExtension;
import net.oktawia.spatialtoolscmp.logic.PiperExtension;
import org.jetbrains.annotations.Nullable;

public final class AE2ClientPiperExtension implements ClientPiperExtension {

    @Override
    public @Nullable PiperExtension.PathAction resolvePathAction(
            ClientLevel level,
            BlockPos pos,
            BlockState state,
            ItemStack target
    ) {
        return AE2PiperExtension.pathAction(level, pos, target);
    }
}
