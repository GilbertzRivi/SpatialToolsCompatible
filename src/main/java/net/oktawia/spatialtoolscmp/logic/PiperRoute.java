package net.oktawia.spatialtoolscmp.logic;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PiperRoute {

    private PiperRoute() {
    }

    public static BlockPos snapToAxis(BlockPos from, BlockPos to) {
        int dx = to.getX() - from.getX();
        int dy = to.getY() - from.getY();
        int dz = to.getZ() - from.getZ();

        int ax = Math.abs(dx);
        int ay = Math.abs(dy);
        int az = Math.abs(dz);

        if (ax == 0 && ay == 0 && az == 0) {
            return from.immutable();
        }

        if (ax >= ay && ax >= az) {
            return new BlockPos(to.getX(), from.getY(), from.getZ());
        }

        if (az >= ay) {
            return new BlockPos(from.getX(), from.getY(), to.getZ());
        }

        return new BlockPos(from.getX(), to.getY(), from.getZ());
    }

    public static Set<BlockPos> expand(List<BlockPos> waypoints, int maxPositions) {
        Set<BlockPos> positions = new LinkedHashSet<>();

        if (waypoints.isEmpty() || maxPositions <= 0) {
            return positions;
        }

        positions.add(waypoints.get(0).immutable());

        for (int i = 1; i < waypoints.size(); i++) {
            appendSegment(positions, waypoints.get(i - 1), waypoints.get(i), maxPositions);

            if (positions.size() >= maxPositions) {
                break;
            }
        }

        return positions;
    }

    public static BlockPos snapForRegion(List<BlockPos> points, BlockPos target) {
        BlockPos first = points.get(0);

        int dx = target.getX() - first.getX();
        int dy = target.getY() - first.getY();
        int dz = target.getZ() - first.getZ();

        boolean freeX = axisSize(points, Direction.Axis.X) == 1;
        boolean freeY = axisSize(points, Direction.Axis.Y) == 1;
        boolean freeZ = axisSize(points, Direction.Axis.Z) == 1;

        if (!freeX && !freeY && !freeZ) {
            freeX = true;
            freeY = true;
            freeZ = true;
        }

        int bestDelta = 0;
        Direction.Axis bestAxis = null;

        if (freeX && Math.abs(dx) > bestDelta) {
            bestDelta = Math.abs(dx);
            bestAxis = Direction.Axis.X;
        }

        if (freeZ && Math.abs(dz) > bestDelta) {
            bestDelta = Math.abs(dz);
            bestAxis = Direction.Axis.Z;
        }

        if (freeY && Math.abs(dy) > bestDelta) {
            bestAxis = Direction.Axis.Y;
        }

        if (bestAxis == null) {
            return first.immutable();
        }

        return switch (bestAxis) {
            case X -> new BlockPos(target.getX(), first.getY(), first.getZ());
            case Y -> new BlockPos(first.getX(), target.getY(), first.getZ());
            case Z -> new BlockPos(first.getX(), first.getY(), target.getZ());
        };
    }

    public static Set<BlockPos> expandRegion(List<BlockPos> points, int maxPositions) {
        Set<BlockPos> positions = new LinkedHashSet<>();

        if (points.isEmpty() || maxPositions <= 0) {
            return positions;
        }

        BlockPos min = regionMin(points);
        BlockPos max = regionMax(points);

        for (int x = min.getX(); x <= max.getX(); x++) {
            for (int y = min.getY(); y <= max.getY(); y++) {
                for (int z = min.getZ(); z <= max.getZ(); z++) {
                    positions.add(new BlockPos(x, y, z));

                    if (positions.size() >= maxPositions) {
                        return positions;
                    }
                }
            }
        }

        return positions;
    }

    public static Map<BlockPos, Integer> pathConnectionMasks(Collection<BlockPos> orderedPath) {
        Map<BlockPos, Integer> masks = new HashMap<>();
        BlockPos previous = null;

        for (BlockPos pos : orderedPath) {
            BlockPos current = pos.immutable();
            masks.putIfAbsent(current, 0);

            if (previous != null) {
                Direction step = stepBetween(previous, current);

                if (step != null) {
                    masks.merge(previous, 1 << step.ordinal(), (a, b) -> a | b);
                    masks.merge(current, 1 << step.getOpposite().ordinal(), (a, b) -> a | b);
                }
            }

            previous = current;
        }

        return masks;
    }

    public static Map<BlockPos, Direction> pathStepDirections(Collection<BlockPos> orderedPath) {
        Map<BlockPos, Direction> steps = new HashMap<>();
        BlockPos previous = null;
        Direction lastStep = null;

        for (BlockPos pos : orderedPath) {
            BlockPos current = pos.immutable();

            if (previous != null) {
                Direction step = stepBetween(previous, current);

                if (step != null) {
                    steps.put(previous, step);
                    lastStep = step;
                }
            }

            previous = current;
        }

        if (previous != null && lastStep != null) {
            steps.putIfAbsent(previous, lastStep);
        }

        return steps;
    }

    @Nullable
    private static Direction stepBetween(BlockPos from, BlockPos to) {
        BlockPos delta = to.subtract(from);

        for (Direction direction : Direction.values()) {
            if (direction.getNormal().equals(delta)) {
                return direction;
            }
        }

        return null;
    }

    public static long regionSize(List<BlockPos> points) {
        if (points.isEmpty()) {
            return 0L;
        }

        return (long) axisSize(points, Direction.Axis.X)
                * axisSize(points, Direction.Axis.Y)
                * axisSize(points, Direction.Axis.Z);
    }

    private static int axisSize(List<BlockPos> points, Direction.Axis axis) {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;

        for (BlockPos pos : points) {
            int value = axis.choose(pos.getX(), pos.getY(), pos.getZ());

            min = Math.min(min, value);
            max = Math.max(max, value);
        }

        return max - min + 1;
    }

    private static BlockPos regionMin(List<BlockPos> points) {
        BlockPos first = points.get(0);

        int x = first.getX();
        int y = first.getY();
        int z = first.getZ();

        for (BlockPos pos : points) {
            x = Math.min(x, pos.getX());
            y = Math.min(y, pos.getY());
            z = Math.min(z, pos.getZ());
        }

        return new BlockPos(x, y, z);
    }

    private static BlockPos regionMax(List<BlockPos> points) {
        BlockPos first = points.get(0);

        int x = first.getX();
        int y = first.getY();
        int z = first.getZ();

        for (BlockPos pos : points) {
            x = Math.max(x, pos.getX());
            y = Math.max(y, pos.getY());
            z = Math.max(z, pos.getZ());
        }

        return new BlockPos(x, y, z);
    }

    public static int length(List<BlockPos> waypoints) {
        if (waypoints.isEmpty()) {
            return 0;
        }

        int total = 1;

        for (int i = 1; i < waypoints.size(); i++) {
            total += steps(waypoints.get(i - 1), waypoints.get(i));
        }

        return total;
    }

    private static void appendSegment(
            Set<BlockPos> positions,
            BlockPos from,
            BlockPos to,
            int maxPositions
    ) {
        int steps = steps(from, to);

        int stepX = Integer.signum(to.getX() - from.getX());
        int stepY = Integer.signum(to.getY() - from.getY());
        int stepZ = Integer.signum(to.getZ() - from.getZ());

        BlockPos current = from.immutable();

        for (int step = 0; step < steps && positions.size() < maxPositions; step++) {
            current = current.offset(stepX, stepY, stepZ);
            positions.add(current);
        }
    }

    private static int steps(BlockPos from, BlockPos to) {
        return Math.max(
                Math.abs(to.getX() - from.getX()),
                Math.max(
                        Math.abs(to.getY() - from.getY()),
                        Math.abs(to.getZ() - from.getZ())
                )
        );
    }
}
