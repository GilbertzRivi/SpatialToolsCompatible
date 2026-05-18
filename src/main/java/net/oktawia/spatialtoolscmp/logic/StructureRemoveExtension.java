package net.oktawia.spatialtoolscmp.logic;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

public interface StructureRemoveExtension {
    void onBeforeBlockRemoved(ServerLevel level, BlockPos pos);
}