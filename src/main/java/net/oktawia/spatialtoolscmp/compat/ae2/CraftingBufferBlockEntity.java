package net.oktawia.spatialtoolscmp.compat.ae2;

import appeng.api.config.Actionable;
import net.minecraft.core.Direction;

import java.util.EnumSet;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.util.IConfigManager;
import appeng.blockentity.grid.AENetworkBlockEntity;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import com.google.common.collect.ImmutableSet;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.oktawia.spatialtoolscmp.logic.buffer.ManagedBuffer;
import org.jetbrains.annotations.Nullable;

public class CraftingBufferBlockEntity extends AENetworkBlockEntity
        implements PatternProviderLogicHost, ICraftingRequester, IGridTickable {

    private final ManagedBuffer buffer;
    private boolean hasActiveRequest = false;

    public CraftingBufferBlockEntity(BlockPos pos, BlockState state) {
        super(AE2BlockRegistrar.CRAFTING_BUFFER_BE_TYPE.get(), pos, state);

        this.buffer = new ManagedBuffer(
                getMainNode(),
                this,
                this,
                this::setChanged,
                this::onBufferReady,
                () -> hasActiveRequest
        );

        getMainNode()
                .setIdlePowerUsage(1.0)
                .addService(IGridTickable.class, this)
                .addService(ICraftingRequester.class, this)
                .setVisualRepresentation(new ItemStack(AE2BlockRegistrar.CRAFTING_BUFFER_BLOCK.get().asItem()));
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            buffer.onLoad();
        }
    }

    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("buffer", buffer.toTag());
    }

    @Override
    public void loadTag(CompoundTag tag) {
        super.loadTag(tag);
        if (tag.contains("buffer", Tag.TAG_COMPOUND)) {
            buffer.fromTag(tag.getCompound("buffer"));
        }
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(1, 1, false, false);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        GenericStack missing = buffer.tick(ticksSinceLastCall);
        if (missing != null) {
            hasActiveRequest = false;
        }
        return (buffer.hasActiveCrafting() || buffer.isFlushPending())
                ? TickRateModulation.URGENT : TickRateModulation.IDLE;
    }

    public boolean request(GenericStack[] required, boolean allowedToCraft) {
        hasActiveRequest = true;
        boolean ready = buffer.request(required, allowedToCraft);
        if (ready) hasActiveRequest = false;
        return ready;
    }

    public boolean hasActiveCrafting() {
        return buffer.hasActiveCrafting();
    }

    @Nullable
    public String getLastError() {
        return buffer.getLastError();
    }

    private void onBufferReady() {
        hasActiveRequest = false;
        setChanged();
    }

    @Override
    public EnumSet<Direction> getTargets() {
        return EnumSet.allOf(Direction.class);
    }

    @Override
    public PatternProviderLogic getLogic() {
        return buffer.getLogic();
    }

    @Override
    public BlockEntity getBlockEntity() {
        return this;
    }

    @Override
    public void saveChanges() {
        setChanged();
    }

    @Override
    public IConfigManager getConfigManager() {
        return buffer.getLogic().getConfigManager();
    }

    @Override
    public ImmutableSet<ICraftingLink> getRequestedJobs() {
        return buffer.getRequestedJobs();
    }

    @Override
    public long insertCraftedItems(ICraftingLink link, AEKey what, long amount, Actionable mode) {
        return buffer.insertCraftedItems(what, amount, mode);
    }

    @Override
    public void jobStateChange(ICraftingLink link) {
        buffer.jobStateChange(link);
    }

    @Override
    public AEItemKey getTerminalIcon() {
        return AEItemKey.of(AE2BlockRegistrar.CRAFTING_BUFFER_BLOCK.get().asItem());
    }

    @Override
    public ItemStack getMainMenuIcon() {
        return new ItemStack(AE2BlockRegistrar.CRAFTING_BUFFER_BLOCK.get().asItem());
    }
}
