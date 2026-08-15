package net.oktawia.spatialtoolscmp.compat.gtceu.v8;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.gregtechceu.gtceu.api.block.PipeBlock;
import com.gregtechceu.gtceu.api.blockentity.PipeBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.multiblock.MultiblockWorldSavedData;
import com.gregtechceu.gtceu.api.multiblock.pattern.PatternState;
import com.gregtechceu.gtceu.api.pipenet.LevelPipeNet;
import com.gregtechceu.gtceu.api.pipenet.PipeNet;
import com.gregtechceu.gtceu.common.machine.owner.MachineOwner;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import net.oktawia.spatialtoolscmp.logic.ReplacerContext;
import net.oktawia.spatialtoolscmp.logic.ReplacerExtension;

public final class GTCEuReplacerExtension implements ReplacerExtension {

    @Override
    public boolean canHandleSource(ServerLevel level, BlockPos pos, BlockState state) {
        BlockEntity be = level.getBlockEntity(pos);
        return be instanceof PipeBlockEntity<?, ?>;
    }

    @Override
    public Set<BlockPos> findReplacementTargets(
            ServerLevel level,
            BlockPos startPos,
            BlockState sourceState,
            ReplacerContext ctx) {
        BlockEntity be = level.getBlockEntity(startPos);

        if (!(be instanceof PipeBlockEntity<?, ?> pipe)) {
            return Set.of();
        }

        try {
            PipeBlock<?, ?, ?> pipeBlock = (PipeBlock<?, ?, ?>) pipe.getPipeBlock();
            LevelPipeNet<?, ?> worldPipeNet = pipeBlock.getWorldPipeNet(level);
            PipeNet<?> net = worldPipeNet.getNetFromPos(startPos);

            if (net == null) {
                return Set.of(startPos.immutable());
            }

            int hardCap = ctx.hardCapMax();
            Set<BlockPos> positions = new LinkedHashSet<>();

            for (BlockPos nodePos : net.getAllNodes().keySet()) {
                BlockState nodeState = level.getBlockState(nodePos);
                if (nodeState.getBlock() == sourceState.getBlock()) {
                    positions.add(nodePos.immutable());
                }

                if (positions.size() >= hardCap) {
                    break;
                }
            }

            return positions;
        } catch (Throwable ignored) {
            return Set.of(startPos.immutable());
        }
    }

    @Override
    @Nullable
    public CompoundTag capturePreReplacementState(ServerLevel level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);

        if (!(be instanceof PipeBlockEntity<?, ?> pipe)) {
            return null;
        }

        CompoundTag saved = new CompoundTag();
        saved.putInt("connections", pipe.getConnections());
        saved.putInt("blockedConnections", pipe.getBlockedConnections());
        GTCEuStructureExtension.copyPreservedPipeState(pipe, saved);
        return saved;
    }

    @Override
    @Nullable
    public List<ItemStack> collectPreservedSourceRefunds(
            ServerLevel level,
            BlockPos pos,
            BlockState state,
            @Nullable BlockEntity be,
            Block targetBlock) {
        if (!(be instanceof PipeBlockEntity<?, ?>) || !(targetBlock instanceof PipeBlock<?, ?, ?>)) {
            return null;
        }

        List<ItemStack> refunds = new ArrayList<>();
        GTCEuStructureExtension.collectPipeBaseRefund(level, pos, refunds);
        return refunds;
    }

    @Override
    public void onBlockReplaced(ServerLevel level, BlockPos pos, @Nullable CompoundTag savedState) {
        GTCEuStructureExtension.scheduleReplacedPipeInit(level, pos, savedState);
    }

    @Override
    public boolean canHandleTarget(ServerLevel level, Block targetBlock) {
        return targetBlock instanceof PipeBlock<?, ?, ?>;
    }

    @Override
    public void onNewBlocksPlaced(ServerLevel level, Set<BlockPos> positions) {
        for (BlockPos pos : positions) {
            GTCEuStructureExtension.scheduleReplacedPipeInit(level, pos, buildConnectionHint(level, pos, positions));
        }
    }

    private final Set<BlockPos> pendingControllers = new LinkedHashSet<>();
    private final Map<BlockPos, MachineFacing> pendingFacings = new LinkedHashMap<>();
    private final Map<BlockPos, UUID> pendingOwners = new LinkedHashMap<>();

    private record MachineFacing(Direction front, @Nullable Direction upwards) {
    }

    @Override
    public void onBeforeReplacement(ServerLevel level, Set<BlockPos> positions, @Nullable ServerPlayer player) {
        pendingControllers.clear();
        collectAffectedControllers(level, positions, pendingControllers);

        pendingFacings.clear();
        captureMachineFacings(level, positions, pendingFacings);

        pendingOwners.clear();
        captureMachineOwners(level, positions, pendingOwners);
    }

    @Override
    public void onReplacementDone(ServerLevel level, Set<BlockPos> positions, @Nullable ServerPlayer player) {
        Map<BlockPos, MachineFacing> facings = new LinkedHashMap<>(pendingFacings);
        pendingFacings.clear();

        for (Map.Entry<BlockPos, MachineFacing> entry : facings.entrySet()) {
            restoreMachineFacing(level, entry.getKey(), entry.getValue());
        }

        Map<BlockPos, UUID> owners = new LinkedHashMap<>(pendingOwners);
        pendingOwners.clear();

        UUID placingOwner = player != null ? player.getUUID() : null;

        for (BlockPos pos : positions) {
            UUID owner = owners.getOrDefault(pos, placingOwner);

            if (owner != null) {
                applyMachineOwner(level, pos, owner);
            }
        }

        Set<BlockPos> controllers = new LinkedHashSet<>(pendingControllers);
        pendingControllers.clear();

        collectAffectedControllers(level, positions, controllers);

        MultiblockWorldSavedData mwsd = MultiblockWorldSavedData.getOrCreate(level);

        for (BlockPos controllerPos : controllers) {
            forceRevalidate(level, mwsd, controllerPos);
        }
    }

    private static void captureMachineFacings(
            ServerLevel level,
            Set<BlockPos> positions,
            Map<BlockPos, MachineFacing> out) {
        for (BlockPos pos : positions) {
            if (!(level.getBlockEntity(pos) instanceof MetaMachine mmbe)) {
                continue;
            }

            var machine = mmbe;

            if (!machine.hasFrontFacing()) {
                continue;
            }

            Direction upwards = machine.allowExtendedFacing() ? machine.getUpwardsFacing() : null;
            out.put(pos.immutable(), new MachineFacing(machine.getFrontFacing(), upwards));
        }
    }

    private static void captureMachineOwners(ServerLevel level, Set<BlockPos> positions, Map<BlockPos, UUID> out) {
        for (BlockPos pos : positions) {
            if (!(level.getBlockEntity(pos) instanceof MetaMachine machine)) {
                continue;
            }

            try {
                UUID owner = machine.getOwnerUUID();

                if (owner != null && !owner.equals(MachineOwner.EMPTY)) {
                    out.put(pos.immutable(), owner);
                }
            } catch (Throwable ignored) {
            }
        }
    }

    private static void applyMachineOwner(ServerLevel level, BlockPos pos, UUID owner) {
        if (!(level.getBlockEntity(pos) instanceof MetaMachine machine)) {
            return;
        }

        try {
            if (owner.equals(machine.getOwnerUUID())) {
                return;
            }

            machine.setOwnerUUID(owner);
            GTCEuStructureExtension.syncGregBlockEntity(level, pos, machine);
        } catch (Throwable ignored) {
        }
    }

    private static void restoreMachineFacing(ServerLevel level, BlockPos pos, MachineFacing facing) {
        if (!(level.getBlockEntity(pos) instanceof MetaMachine mmbe)) {
            return;
        }

        try {
            var machine = mmbe;

            machine.setFrontFacing(facing.front());

            if (facing.upwards() != null && machine.allowExtendedFacing()) {
                machine.setUpwardsFacing(facing.upwards());
            }

            GTCEuStructureExtension.syncGregBlockEntity(level, pos, mmbe);
        } catch (Throwable ignored) {
        }
    }

    private static void collectAffectedControllers(ServerLevel level, Set<BlockPos> positions, Set<BlockPos> out) {
        MultiblockWorldSavedData mwsd = MultiblockWorldSavedData.getOrCreate(level);

        for (Set<PatternState> states : mwsd.mapping.values()) {
            for (PatternState state : states) {
                BlockPos controllerPos = state.getControllerPos();

                if (controllerPos == null) {
                    continue;
                }

                for (BlockPos pos : positions) {
                    if (state.getCache().containsKey(pos.asLong())) {
                        out.add(controllerPos.immutable());
                        break;
                    }
                }
            }
        }
    }

    private static void forceRevalidate(ServerLevel level, MultiblockWorldSavedData mwsd, BlockPos controllerPos) {
        try {
            BlockEntity be = level.getBlockEntity(controllerPos);

            if (!(be instanceof MultiblockControllerMachine controller)) {
                return;
            }

            GTCEuStructureExtension.resetPatternStates(mwsd, controller);
            controller.checkAndFormStructure();

            if (controller.isFormed()) {
                controller.notifyBlockUpdate();
                GTCEuStructureExtension.syncGregBlockEntity(level, controllerPos, be);
            } else {
                GTCEuStructureExtension.scheduleStructureCheck(level, controller);
            }
        } catch (Throwable ignored) {
        }
    }

    private static CompoundTag buildConnectionHint(ServerLevel level, BlockPos pos, Set<BlockPos> allPositions) {
        int connections = 0;
        for (Direction dir : Direction.values()) {
            BlockPos neighbor = pos.relative(dir);
            if (allPositions.contains(neighbor)) {
                connections |= 1 << dir.ordinal();
            }
        }
        CompoundTag tag = new CompoundTag();
        tag.putInt("connections", connections);
        tag.putInt("blockedConnections", 0);
        return tag;
    }
}
