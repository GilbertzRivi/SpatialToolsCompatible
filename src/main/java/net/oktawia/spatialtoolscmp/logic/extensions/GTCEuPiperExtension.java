package net.oktawia.spatialtoolscmp.logic.extensions;

import com.gregtechceu.gtceu.api.block.PipeBlock;
import com.gregtechceu.gtceu.api.blockentity.PipeBlockEntity;
import com.gregtechceu.gtceu.common.block.FluidPipeBlock;
import com.gregtechceu.gtceu.common.block.ItemPipeBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.registries.ForgeRegistries;
import net.oktawia.spatialtoolscmp.items.PortableSpatialPiper;
import net.oktawia.spatialtoolscmp.logic.PiperExtension;
import net.oktawia.spatialtoolscmp.logic.PiperRoute;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class GTCEuPiperExtension implements PiperExtension {

    @Override
    public boolean onPathBuilt(
            ServerLevel level,
            List<BlockPos> orderedPath,
            Set<BlockPos> placed,
            ItemStack target,
            ItemStack toolStack
    ) {
        Map<BlockPos, Integer> masks = PiperRoute.pathConnectionMasks(orderedPath);

        PortableSpatialPiper.PipeDirectionMode directionMode =
                supportsPipeDirection(target)
                        ? PortableSpatialPiper.getPipeDirectionMode(toolStack)
                        : PortableSpatialPiper.PipeDirectionMode.OFF;

        Map<BlockPos, Direction> steps =
                directionMode == PortableSpatialPiper.PipeDirectionMode.OFF
                        ? Map.of()
                        : PiperRoute.pathStepDirections(orderedPath);

        boolean handled = false;

        for (BlockPos pos : placed) {
            if (!(level.getBlockState(pos).getBlock() instanceof PipeBlock<?, ?, ?>)) {
                continue;
            }

            CompoundTag hint = new CompoundTag();
            hint.putInt("connections", masks.getOrDefault(pos, 0));
            hint.putInt("blockedConnections", blockedMask(steps.get(pos), directionMode));

            GTCEuStructureExtension.scheduleReplacedPipeInit(level, pos, hint);
            applyBlockedFace(level, pos, steps.get(pos), directionMode);
            handled = true;
        }

        return handled;
    }

    public static boolean supportsPipeDirection(ItemStack target) {
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(target.getItem());
        Block block = itemId != null ? ForgeRegistries.BLOCKS.getValue(itemId) : null;

        return block instanceof ItemPipeBlock || block instanceof FluidPipeBlock;
    }

    public static int blockedMask(
            Direction step,
            PortableSpatialPiper.PipeDirectionMode directionMode
    ) {
        Direction blocked = blockedFace(step, directionMode);

        return blocked == null ? 0 : 1 << blocked.ordinal();
    }

    private static Direction blockedFace(
            Direction step,
            PortableSpatialPiper.PipeDirectionMode directionMode
    ) {
        if (step == null) {
            return null;
        }

        return switch (directionMode) {
            case OFF -> null;
            case ALONG_PATH -> step;
            case AGAINST_PATH -> step.getOpposite();
        };
    }

    private static void applyBlockedFace(
            ServerLevel level,
            BlockPos pos,
            Direction step,
            PortableSpatialPiper.PipeDirectionMode directionMode
    ) {
        Direction blocked = blockedFace(step, directionMode);

        if (blocked == null) {
            return;
        }

        BlockEntity be = level.getBlockEntity(pos);

        if (!(be instanceof PipeBlockEntity<?, ?> pipe)) {
            return;
        }

        try {
            pipe.setBlocked(blocked, true);
        } catch (Throwable ignored) {
        }
    }
}
