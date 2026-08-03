package net.oktawia.spatialtoolscmp.logic;

import java.util.Collection;

import net.minecraft.core.BlockPos;

public final class SpatialPowerCost {

    private SpatialPowerCost() {
    }

    public static double distanceSum(Collection<BlockPos> positions, BlockPos origin) {
        double total = 0.0D;

        for (BlockPos pos : positions) {
            double dx = pos.getX() - origin.getX();
            double dy = pos.getY() - origin.getY();
            double dz = pos.getZ() - origin.getZ();

            total += Math.sqrt(dx * dx + dy * dy + dz * dz);
        }

        return total;
    }

    public static double fromDistanceSum(double distanceSum, double effectivePowerPerBlock) {
        if (effectivePowerPerBlock <= 0.0D) {
            return 0.0D;
        }

        return Math.max(1.0D, Math.max(0.0D, distanceSum) * effectivePowerPerBlock);
    }

    public static double cost(
            Collection<BlockPos> positions,
            BlockPos origin,
            double effectivePowerPerBlock) {
        if (positions.isEmpty()) {
            return 0.0D;
        }

        return fromDistanceSum(distanceSum(positions, origin), effectivePowerPerBlock);
    }
}
