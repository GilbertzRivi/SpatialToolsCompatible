package net.oktawia.spatialtoolscmp.compat.ae2;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.google.common.collect.ImmutableSet;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
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
import appeng.menu.me.crafting.CraftAmountMenu;
import appeng.menu.me.crafting.CraftConfirmMenu;

import net.oktawia.spatialtoolscmp.logic.buffer.BufferRequestState;
import net.oktawia.spatialtoolscmp.logic.buffer.ManagedBuffer;

public class CraftingBufferBlockEntity extends AENetworkBlockEntity
        implements PatternProviderLogicHost, ICraftingRequester, IGridTickable {

    public record DisplayEntry(ItemStack stack, long requestedAmount, long bufferedAmount) {
    }

    private static final int ABANDONED_CONFIRM_TICKS = 40;

    private final ManagedBuffer buffer;

    private boolean displayHasError = false;
    private GenericStack[] requestedStacks = new GenericStack[0];

    @Nullable
    private UUID ownerId = null;

    private String requestLabel = "";

    public CraftingBufferBlockEntity(BlockPos pos, BlockState state) {
        super(AE2BlockRegistrar.CRAFTING_BUFFER_BE_TYPE.get(), pos, state);

        this.buffer = new ManagedBuffer(
                getMainNode(),
                this,
                this,
                this::setChanged,
                this::onBufferReady);

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
    public void setRemoved() {
        super.setRemoved();
        buffer.onUnload();
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        buffer.onUnload();
    }

    @Override
    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);

        tag.put("buffer", buffer.toTag());
        tag.putBoolean("displayHasError", displayHasError);
        tag.putString("requestLabel", requestLabel);

        if (ownerId != null) {
            tag.putUUID("ownerId", ownerId);
        }

        tag.put("requested", saveRequestedStacks());
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

        requestLabel = tag.getString("requestLabel");
        ownerId = tag.hasUUID("ownerId") ? tag.getUUID("ownerId") : null;

        if (tag.contains("requested", Tag.TAG_LIST)) {
            requestedStacks = loadRequestedStacks(tag);
        }
    }

    @Override
    public TickingRequest getTickingRequest(IGridNode node) {
        return new TickingRequest(1, 1, false, false);
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        buffer.tick(ticksSinceLastCall);
        tickAbandonedRequest();

        if (buffer.getState() == BufferRequestState.ERROR) {
            markDisplayError();
        }

        clearDisplayRequestIfIdle();

        return buffer.isBusy() ? TickRateModulation.URGENT : TickRateModulation.IDLE;
    }

    public ManagedBuffer.PrepareResult prepare(
            @Nullable UUID requester,
            String label,
            GenericStack[] required,
            @Nullable Component dummyName) {
        this.requestedStacks = copyStacks(required);
        this.ownerId = requester;
        this.requestLabel = label == null ? "" : label;

        setChanged();

        ManagedBuffer.PrepareResult result = buffer.prepare(required, dummyName);

        if (result.status() == ManagedBuffer.PrepareStatus.READY
                || result.status() == ManagedBuffer.PrepareStatus.AWAITING_CONFIRM) {
            clearDisplayError();
        } else {
            clearDisplayRequestIfIdle();
        }

        return result;
    }

    public BufferRequestState getRequestState() {
        return buffer.getState();
    }

    public long getProgressDone() {
        return buffer.getProgressDone();
    }

    public long getProgressTotal() {
        return buffer.getExpectedTotal();
    }

    @Nullable
    public UUID getOwnerId() {
        return ownerId;
    }

    public String getRequestLabel() {
        return requestLabel;
    }

    public boolean isBusy() {
        return buffer.isBusy();
    }

    public boolean hasActiveCrafting() {
        return buffer.hasActiveCrafting();
    }

    public boolean hasDisplayError() {
        return displayHasError;
    }

    public List<DisplayEntry> getDisplayEntries() {
        Map<AEItemKey, long[]> amountsByKey = new LinkedHashMap<>();
        long sets = Math.max(1, buffer.getExpectedPatterns());

        for (GenericStack stack : requestedStacks) {
            if (stack != null && stack.what() instanceof AEItemKey itemKey && stack.amount() > 0) {
                long[] amounts = amountsByKey.computeIfAbsent(itemKey, k -> new long[2]);
                amounts[0] += stack.amount() * sets;
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

    private void tickAbandonedRequest() {
        if (buffer.getState() != BufferRequestState.AWAITING_CONFIRM || level == null || level.getServer() == null) {
            return;
        }

        ServerPlayer owner = ownerId == null ? null : level.getServer().getPlayerList().getPlayer(ownerId);

        if (owner != null && isCraftingRequestMenu(owner.containerMenu)) {
            buffer.touchAwaitingConfirm();
            return;
        }

        if (buffer.isAwaitingConfirmFor(ABANDONED_CONFIRM_TICKS)) {
            buffer.abortRequest();
        }
    }

    private static boolean isCraftingRequestMenu(@Nullable AbstractContainerMenu menu) {
        return menu instanceof CraftAmountMenu || menu instanceof CraftConfirmMenu;
    }

    private void onBufferReady() {
        clearDisplayError();
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

    private void clearDisplayRequestIfIdle() {
        if (displayHasError) {
            return;
        }

        if (buffer.isBusy()) {
            return;
        }

        if (hasBufferedItems()) {
            return;
        }

        if (requestedStacks.length > 0) {
            requestedStacks = new GenericStack[0];
            ownerId = null;
            requestLabel = "";
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

    private ListTag saveRequestedStacks() {
        ListTag list = new ListTag();

        for (GenericStack stack : requestedStacks) {
            if (stack == null || stack.what() == null || stack.amount() <= 0) {
                continue;
            }

            ItemStack wrapped = GenericStack.wrapInItemStack(stack);

            if (!wrapped.isEmpty()) {
                list.add(wrapped.save(new CompoundTag()));
            }
        }

        return list;
    }

    private static GenericStack[] loadRequestedStacks(CompoundTag tag) {
        ListTag list = tag.getList("requested", Tag.TAG_COMPOUND);
        List<GenericStack> stacks = new ArrayList<>(list.size());

        for (int i = 0; i < list.size(); i++) {
            ItemStack wrapped = ItemStack.of(list.getCompound(i));

            if (wrapped.isEmpty()) {
                continue;
            }

            GenericStack stack = GenericStack.fromItemStack(wrapped);

            if (stack == null || stack.what() == null || stack.amount() <= 0) {
                continue;
            }

            stacks.add(stack);
        }

        return stacks.toArray(GenericStack[]::new);
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
