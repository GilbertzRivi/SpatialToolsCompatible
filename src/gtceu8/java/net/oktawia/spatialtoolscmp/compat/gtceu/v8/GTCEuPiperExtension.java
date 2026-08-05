package net.oktawia.spatialtoolscmp.compat.gtceu.v8;

import java.util.List;
import java.util.Map;
import java.util.Set;

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

import net.oktawia.spatialtoolscmp.compat.gtceu.GTCEuCompat;
import net.oktawia.spatialtoolscmp.items.PortableSpatialPiper;
import net.oktawia.spatialtoolscmp.logic.PiperExtension;
import net.oktawia.spatialtoolscmp.logic.PiperRoute;

public final class GTCEuPiperExtension implements PiperExtension {

    @Override
    public boolean onPathBuilt(
            ServerLevel level,
            List<BlockPos> orderedPath,
            Set<BlockPos> placed,
            ItemStack target,
            ItemStack toolStack) {
        Map<BlockPos, Integer> masks = PiperRoute.pathConnectionMasks(orderedPath);

        PortableSpatialPiper.PipeDirectionMode directionMode = supportsPipeDirection(target)
                ? PortableSpatialPiper.getPipeDirectionMode(toolStack)
                : PortableSpatialPiper.PipeDirectionMode.OFF;

        Map<BlockPos, Direction> steps = directionMode == PortableSpatialPiper.PipeDirectionMode.OFF
                ? Map.of()
                : PiperRoute.pathStepDirections(orderedPath);

        boolean handled = false;

        for (BlockPos pos : placed) {
            if (!(level.getBlockState(pos).getBlock() instanceof PipeBlock<?, ?, ?>)) {
                continue;
            }

            CompoundTag hint = new CompoundTag();
            hint.putInt("connections", masks.getOrDefault(pos, 0));
            hint.putInt("blockedConnections", GTCEuCompat.blockedMask(steps.get(pos), directionMode));

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

    private static void applyBlockedFace(
            ServerLevel level,
            BlockPos pos,
            Direction step,
            PortableSpatialPiper.PipeDirectionMode directionMode) {
        Direction blocked = GTCEuCompat.blockedFace(step, directionMode);

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
