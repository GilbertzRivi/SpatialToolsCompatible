package net.oktawia.spatialtoolscmp.compat.ae2;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableSet;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import appeng.api.config.Actionable;
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

import net.oktawia.spatialtoolscmp.logic.buffer.ManagedBuffer;

public class CraftingBufferBlockEntity extends AENetworkBlockEntity
        implements PatternProviderLogicHost, ICraftingRequester, IGridTickable {

    public record DisplayEntry(ItemStack stack, long requestedAmount, long bufferedAmount) {
    }

    private final ManagedBuffer buffer;

    private boolean hasActiveRequest = false;
    private boolean displayHasError = false;
    private GenericStack[] requestedStacks = new GenericStack[0];

    public CraftingBufferBlockEntity(BlockPos pos, BlockState state) {
        super(AE2BlockRegistrar.CRAFTING_BUFFER_BE_TYPE.get(), pos, state);

        this.buffer = new ManagedBuffer(
                getMainNode(),
                this,
                this,
                this::setChanged,
                this::onBufferReady,
                () -> hasActiveRequest);

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
        tag.putBoolean("displayHasError", displayHasError);
    }

    @Override
    public void loadTag(CompoundTag tag) {
        super.loadTag(tag);

        if (tag.contains("buffer", Tag.TAG_COMPOUND)) {
            buffer.fromTag(tag.getCompound("buffer"));
        }

        if (tag.contains("displayHasError", Tag.TAG_BYTE)) {
            displayHasError = tag.getBoolean("displayHasError");
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
            markDisplayError();
        }

        if (buffer.getLastError() != null) {
            hasActiveRequest = false;
            markDisplayError();
        }

        if (hasActiveRequest && !hasAnyProgress()) {
            hasActiveRequest = false;
            markDisplayError();
        }

        clearDisplayRequestIfIdle();

        return (buffer.hasActiveCrafting() || buffer.isFlushPending() || hasActiveRequest)
                ? TickRateModulation.URGENT
                : TickRateModulation.IDLE;
    }

    public boolean request(GenericStack[] required, boolean allowedToCraft) {
        this.requestedStacks = copyStacks(required);
        setChanged();

        hasActiveRequest = true;

        boolean ready = buffer.request(required, allowedToCraft);

        if (buffer.getLastError() != null) {
            hasActiveRequest = false;
            markDisplayError();
        } else if (ready) {
            hasActiveRequest = false;
            clearDisplayError();
        } else if (!hasAnyProgress()) {
            hasActiveRequest = false;
            markDisplayError();
        }

        clearDisplayRequestIfIdle();

        return ready;
    }

    public boolean hasActiveCrafting() {
        return buffer.hasActiveCrafting();
    }

    @Nullable
    public String getLastError() {
        return buffer.getLastError();
    }

    public boolean hasDisplayError() {
        return displayHasError;
    }

    public List<DisplayEntry> getDisplayEntries() {
        Map<AEItemKey, long[]> amountsByKey = new LinkedHashMap<>();

        for (GenericStack stack : requestedStacks) {
            if (stack != null && stack.what() instanceof AEItemKey itemKey && stack.amount() > 0) {
                long[] amounts = amountsByKey.computeIfAbsent(itemKey, k -> new long[2]);
                amounts[0] += stack.amount();
            }
        }

        for (GenericStack stack : buffer.getItemsAsStacks()) {
            if (stack != null && stack.what() instanceof AEItemKey itemKey && stack.amount() > 0) {
                long[] amounts = amountsByKey.computeIfAbsent(itemKey, k -> new long[2]);
                amounts[1] += stack.amount();
            }
        }

        List<DisplayEntry> result = new ArrayList<>(amountsByKey.size());

        for (Map.Entry<AEItemKey, long[]> entry : amountsByKey.entrySet()) {
            long requestedAmount = entry.getValue()[0];
            long bufferedAmount = entry.getValue()[1];

            if (requestedAmount > 0 || bufferedAmount > 0) {
                result.add(new DisplayEntry(
                        entry.getKey().toStack(),
                        requestedAmount,
                        bufferedAmount));
            }
        }

        return List.copyOf(result);
    }

    private void onBufferReady() {
        hasActiveRequest = false;
        clearDisplayError();
        clearDisplayRequestIfIdle();
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

    private void markDisplayError() {
        if (!displayHasError) {
            displayHasError = true;
            setChanged();
        }
    }

    private void clearDisplayError() {
        if (displayHasError) {
            displayHasError = false;
            setChanged();
        }
    }

    private boolean hasAnyProgress() {
        if (buffer.hasActiveCrafting()) {
            return true;
        }

        if (buffer.isFlushPending()) {
            return true;
        }

        if (!buffer.getRequestedJobs().isEmpty()) {
            return true;
        }

        return hasBufferedItems();
    }

    private void clearDisplayRequestIfIdle() {
        if (displayHasError) {
            return;
        }

        if (hasActiveRequest) {
            return;
        }

        if (buffer.hasActiveCrafting()) {
            return;
        }

        if (buffer.isFlushPending()) {
            return;
        }

        if (!buffer.getRequestedJobs().isEmpty()) {
            return;
        }

        if (hasBufferedItems()) {
            return;
        }

        if (requestedStacks.length > 0) {
            requestedStacks = new GenericStack[0];
            setChanged();
        }
    }

    private boolean hasBufferedItems() {
        for (GenericStack stack : buffer.getItemsAsStacks()) {
            if (stack != null && stack.amount() > 0) {
                return true;
            }
        }

        return false;
    }

    private static GenericStack[] copyStacks(GenericStack[] stacks) {
        if (stacks == null || stacks.length == 0) {
            return new GenericStack[0];
        }

        List<GenericStack> copy = new ArrayList<>(stacks.length);

        for (GenericStack stack : stacks) {
            if (stack != null && stack.what() != null && stack.amount() > 0) {
                copy.add(new GenericStack(stack.what(), stack.amount()));
            }
        }

        return copy.toArray(GenericStack[]::new);
    }
}
