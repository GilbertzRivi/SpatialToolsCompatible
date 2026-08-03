package net.oktawia.spatialtoolscmp.compat;

import java.util.EnumSet;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.integration.ae2.machine.feature.IGridConnectedMachine;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

import appeng.api.networking.IManagedGridNode;

public final class GTCEuAE2PostPasteOps {

    private GTCEuAE2PostPasteOps() {
    }

    public static boolean isAe2GridConnectedMachine(@Nullable BlockEntity blockEntity) {
        if (!(blockEntity instanceof MetaMachineBlockEntity mmbe)) {
            return false;
        }

        try {
            return mmbe.getMetaMachine() instanceof IGridConnectedMachine;
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void refreshGridNodeIfPresent(
            ServerLevel level,
            BlockPos pos,
            BlockEntity blockEntity) {
        if (!(blockEntity instanceof MetaMachineBlockEntity mmbe)) {
            return;
        }

        if (!(mmbe.getMetaMachine() instanceof IGridConnectedMachine gridMachine)) {
            return;
        }

        IManagedGridNode mainNode;

        try {
            mainNode = gridMachine.getMainNode();
        } catch (Throwable ignored) {
            return;
        }

        if (mainNode == null) {
            return;
        }

        try {
            mainNode.setInWorldNode(true);
            mainNode.setExposedOnSides(getExposedSides(gridMachine));

            if (!mainNode.isReady()) {
                mainNode.create(level, pos);
            }
        } catch (Throwable ignored) {
        }

        try {
            gridMachine.updateMEStatus();
        } catch (Throwable ignored) {
        }

        try {
            blockEntity.setChanged();
        } catch (Throwable ignored) {
        }
    }

    private static EnumSet<Direction> getExposedSides(IGridConnectedMachine machine) {
        try {
            if (machine.self().hasFrontFacing()) {
                Direction front = machine.self().getFrontFacing();

                if (front != null) {
                    return EnumSet.of(front);
                }
            }
        } catch (Throwable ignored) {
        }

        return EnumSet.allOf(Direction.class);
    }
}
