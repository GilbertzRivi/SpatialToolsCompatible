package net.oktawia.spatialtoolscmp.logic.extensions;

import java.util.Set;

import org.jetbrains.annotations.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.common.capabilities.ForgeCapabilities;

import mekanism.client.model.data.TransmitterModelData;
import mekanism.common.capabilities.Capabilities;
import mekanism.common.content.network.transmitter.Transmitter;
import mekanism.common.lib.transmitter.ConnectionType;
import mekanism.common.lib.transmitter.TransmissionType;
import mekanism.common.tile.transmitter.TileEntityTransmitter;

import net.oktawia.spatialtoolscmp.logic.ClientPiperExtension;

public final class MekanismClientPiperExtension implements ClientPiperExtension {

    private BlockState cachedState = null;
    private Set<TransmissionType> cachedTransmissionTypes = Set.of();

    @Override
    public @Nullable ModelData buildTargetModelData(
            BlockState targetState,
            ItemStack target,
            BlockPos pos,
            Set<BlockPos> allPositions) {
        Set<TransmissionType> transmissionTypes = transmissionTypes(targetState, pos);

        if (transmissionTypes.isEmpty()) {
            return null;
        }

        ClientLevel level = Minecraft.getInstance().level;
        TransmitterModelData transmitterData = new TransmitterModelData();

        for (Direction side : Direction.values()) {
            BlockPos neighbor = pos.relative(side);

            boolean connected = allPositions.contains(neighbor)
                    || connectsToWorld(level, neighbor, side, transmissionTypes);

            transmitterData.setConnectionData(
                    side,
                    connected ? ConnectionType.NORMAL : ConnectionType.NONE);
        }

        transmitterData.setHasColor(false);

        return ModelData.builder()
                .with(TileEntityTransmitter.TRANSMITTER_PROPERTY, transmitterData)
                .build();
    }

    private Set<TransmissionType> transmissionTypes(BlockState targetState, BlockPos pos) {
        if (targetState.equals(this.cachedState)) {
            return this.cachedTransmissionTypes;
        }

        this.cachedState = targetState;
        this.cachedTransmissionTypes = readTransmissionTypes(targetState, pos);

        return this.cachedTransmissionTypes;
    }

    private static Set<TransmissionType> readTransmissionTypes(BlockState targetState, BlockPos pos) {
        if (!(targetState.getBlock() instanceof EntityBlock entityBlock)) {
            return Set.of();
        }

        try {
            BlockEntity be = entityBlock.newBlockEntity(pos, targetState);

            if (be instanceof TileEntityTransmitter transmitter) {
                return transmitter.getTransmitter().getSupportedTransmissionTypes();
            }
        } catch (Throwable ignored) {
        }

        return Set.of();
    }

    private static boolean connectsToWorld(
            @Nullable ClientLevel level,
            BlockPos neighborPos,
            Direction side,
            Set<TransmissionType> transmissionTypes) {
        if (level == null) {
            return false;
        }

        BlockEntity neighbor = level.getBlockEntity(neighborPos);

        if (neighbor == null) {
            return false;
        }

        Direction neighborSide = side.getOpposite();

        if (neighbor instanceof TileEntityTransmitter neighborTransmitter) {
            return connectsToTransmitter(neighborTransmitter, neighborSide, transmissionTypes);
        }

        for (TransmissionType type : transmissionTypes) {
            if (hasAcceptorCapability(neighbor, neighborSide, type)) {
                return true;
            }
        }

        return false;
    }

    private static boolean connectsToTransmitter(
            TileEntityTransmitter neighbor,
            Direction neighborSide,
            Set<TransmissionType> transmissionTypes) {
        Transmitter<?, ?, ?> transmitter = neighbor.getTransmitter();

        if (transmitter.getConnectionTypeRaw(neighborSide) == ConnectionType.NONE) {
            return false;
        }

        for (TransmissionType type : transmissionTypes) {
            if (type.checkTransmissionType(neighbor)) {
                return true;
            }
        }

        return false;
    }

    private static boolean hasAcceptorCapability(
            BlockEntity neighbor,
            Direction side,
            TransmissionType type) {
        try {
            return switch (type) {
                case ENERGY -> neighbor.getCapability(Capabilities.STRICT_ENERGY, side).isPresent()
                        || neighbor.getCapability(ForgeCapabilities.ENERGY, side).isPresent();
                case FLUID -> neighbor.getCapability(ForgeCapabilities.FLUID_HANDLER, side).isPresent();
                case GAS -> neighbor.getCapability(Capabilities.GAS_HANDLER, side).isPresent();
                case INFUSION -> neighbor.getCapability(Capabilities.INFUSION_HANDLER, side).isPresent();
                case PIGMENT -> neighbor.getCapability(Capabilities.PIGMENT_HANDLER, side).isPresent();
                case SLURRY -> neighbor.getCapability(Capabilities.SLURRY_HANDLER, side).isPresent();
                case ITEM -> neighbor.getCapability(ForgeCapabilities.ITEM_HANDLER, side).isPresent();
                case HEAT -> neighbor.getCapability(Capabilities.HEAT_HANDLER, side).isPresent();
            };
        } catch (Throwable ignored) {
            return false;
        }
    }
}
