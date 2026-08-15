package net.oktawia.spatialtoolscmp.logic.buffer;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.google.common.collect.ImmutableSet;

import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import it.unimi.dsi.fastutil.objects.Object2LongLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import lombok.Getter;

import appeng.api.config.Actionable;
import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.networking.IGrid;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.CraftingJobStatus;
import appeng.api.networking.crafting.ICraftingCPU;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.StorageHelper;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.crafting.pattern.AEProcessingPattern;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.me.cluster.implementations.CraftingCPUCluster;

import net.oktawia.spatialtoolscmp.SpatialToolsCMP;
import net.oktawia.spatialtoolscmp.compat.ae2.CraftingBufferRequests;

public class ManagedBuffer {
    public static final String DUMMY_ID_KEY = "s";

    public static final int CONFIRM_TIMEOUT_TICKS = 20 * 120;

    public static final int READY_DISPLAY_TICKS = 20 * 5;

    private static final int DUMMY_CLEANUP_TICKS = 20 * 10;

    public enum PrepareStatus {
        BUSY,
        READY,
        AWAITING_CONFIRM,
        UNSUPPORTED
    }

    public record PrepareResult(PrepareStatus status, @Nullable AEItemKey dummyKey) {
    }

    private final Object2LongOpenHashMap<AEKey> items = new Object2LongOpenHashMap<>();
    private final ManagedBufferLogic logic;
    private final IManagedGridNode mainNode;
    private final PatternProviderLogicHost logicHost;
    private final IActionHost actionHost;
    private final Runnable onDirty;
    private final Runnable onReady;

    private final List<ICraftingLink> activeLinks = new ArrayList<>();

    @Getter
    private BufferRequestState state = BufferRequestState.IDLE;

    @Getter
    @Nullable
    private UUID requestId = null;

    @Nullable
    private ItemStack dummyStack = null;

    @Nullable
    private AEItemKey dummyKey = null;

    @Getter
    private long expectedPatterns = 0;

    private long pushedPatterns = 0;

    @Getter
    private long expectedTotal = 0;

    @Getter
    private long progressDone = 0;

    private long patternTotal = 0;

    private GenericStack[] patternStacks = new GenericStack[0];

    private boolean aborting = false;
    private long awaitingSinceTick = 0;
    private long readyUntilTick = 0;
    private long dummyCleanupUntilTick = 0;
    private long pendingDummyReturn = 0;

    @Getter
    private boolean flushPending = false;
    private int flushTickAcc = 0;
    private long readyAtTick = 0;

    public ManagedBuffer(IManagedGridNode mainNode, PatternProviderLogicHost logicHost,
            IActionHost actionHost, Runnable onDirty, Runnable onReady) {
        this.mainNode = mainNode;
        this.logicHost = logicHost;
        this.actionHost = actionHost;
        this.onDirty = onDirty;
        this.onReady = onReady;
        this.items.defaultReturnValue(0L);
        this.logic = new ManagedBufferLogic(mainNode, logicHost, this);
    }

    public PatternProviderLogic getLogic() {
        return logic;
    }

    @Nullable
    public ICraftingRequester getRequester() {
        return actionHost instanceof ICraftingRequester requester ? requester : null;
    }

    public boolean isRequestUsable() {
        return state == BufferRequestState.AWAITING_CONFIRM || state == BufferRequestState.CRAFTING;
    }

    public boolean isAwaitingConfirmFor(int ticks) {
        return state == BufferRequestState.AWAITING_CONFIRM && now() - awaitingSinceTick >= ticks;
    }

    public void touchAwaitingConfirm() {
        if (state == BufferRequestState.AWAITING_CONFIRM) {
            awaitingSinceTick = now();
        }
    }

    public boolean isBusy() {
        return state.isBusy() || hasActiveCrafting() || flushPending;
    }

    public long get(AEKey key) {
        return items.getLong(key);
    }

    public void add(AEKey key, long amount) {
        if (amount <= 0)
            return;
        items.put(key, get(key) + amount);
        onDirty.run();
    }

    public long extract(AEKey key, long amount) {
        if (amount <= 0)
            return 0;
        long have = get(key);
        long take = Math.min(have, amount);
        if (take <= 0)
            return 0;
        long left = have - take;
        if (left <= 0)
            items.removeLong(key);
        else
            items.put(key, left);
        onDirty.run();
        return take;
    }

    public boolean isEmpty() {
        return items.isEmpty();
    }

    public long getBufferedTotal() {
        long total = 0;

        for (Object2LongMap.Entry<AEKey> e : items.object2LongEntrySet()) {
            if (e.getKey() != null && e.getLongValue() > 0) {
                total += e.getLongValue();
            }
        }

        return total;
    }

    private long computeProgressDone() {
        long buffered = getBufferedTotal();

        if (state != BufferRequestState.CRAFTING || patternTotal <= 0) {
            return buffered;
        }

        long remainingPushes = Math.max(0, expectedPatterns - pushedPatterns);

        if (remainingPushes <= 0) {
            return buffered;
        }

        long inFlight = patternTotal * remainingPushes;

        return buffered + Math.min(inFlight, securedPatternAmount());
    }

    private long securedPatternAmount() {
        CraftingCpuLogic cpu = findRequestCpu();

        if (cpu == null) {
            return 0;
        }

        long secured = 0;

        for (GenericStack stack : patternStacks) {
            if (stack == null || stack.what() == null) {
                continue;
            }

            secured += Math.min(stack.amount(), cpu.getStored(stack.what()));
        }

        return secured;
    }

    @Nullable
    private CraftingCpuLogic findRequestCpu() {
        var grid = grid();

        if (requestId == null || grid == null || patternStacks.length == 0) {
            return null;
        }

        for (ICraftingCPU cpu : grid.getCraftingService().getCpus()) {
            CraftingJobStatus status = cpu.getJobStatus();

            if (status == null || status.crafting() == null
                    || !requestId.equals(CraftingBufferRequests.readRequestId(status.crafting().what()))) {
                continue;
            }

            return cpu instanceof CraftingCPUCluster cluster ? cluster.craftingLogic : null;
        }

        return null;
    }

    private GenericStack[] condense(GenericStack[] required) {
        var merged = new Object2LongLinkedOpenHashMap<AEKey>();
        merged.defaultReturnValue(0L);

        for (var stack : required) {
            if (stack == null || stack.what() == null || stack.amount() <= 0)
                continue;
            merged.put(stack.what(), merged.getLong(stack.what()) + stack.amount());
        }

        GenericStack[] result = new GenericStack[merged.size()];
        int i = 0;

        for (var e : merged.object2LongEntrySet()) {
            result[i++] = new GenericStack(e.getKey(), e.getLongValue());
        }

        return result;
    }

    private GenericStack[] networkShortfall(GenericStack[] condensed) {
        var grid = grid();
        var storage = grid == null ? null : grid.getStorageService().getInventory();

        List<GenericStack> shortfall = new ArrayList<>(condensed.length);

        for (var stack : condensed) {
            long need = stack.amount() - get(stack.what());

            if (need > 0 && storage != null) {
                need -= storage.extract(stack.what(), need, Actionable.SIMULATE, src());
            }

            if (need > 0) {
                shortfall.add(new GenericStack(stack.what(), need));
            }
        }

        return shortfall.toArray(GenericStack[]::new);
    }

    public PrepareResult prepare(GenericStack[] required, @Nullable Component displayName) {
        if (isBusy()) {
            return new PrepareResult(PrepareStatus.BUSY, null);
        }

        if (!flushUnneeded(required)) {
            return new PrepareResult(PrepareStatus.BUSY, null);
        }

        GenericStack[] inputs = condense(required);

        if (inputs.length > AEProcessingPattern.MAX_INPUT_SLOTS) {
            inputs = networkShortfall(inputs);
        }

        if (inputs.length == 0) {
            expectedTotal = 0;
            progressDone = 0;
            patternTotal = 0;
            patternStacks = new GenericStack[0];
            expectedPatterns = 1;
            pushedPatterns = 1;
            fireReady();
            return new PrepareResult(PrepareStatus.READY, null);
        }

        if (inputs.length > AEProcessingPattern.MAX_INPUT_SLOTS) {
            return new PrepareResult(PrepareStatus.UNSUPPORTED, null);
        }

        long inputSum = 0;
        for (var stack : inputs) {
            inputSum += stack.amount();
        }

        ItemStack dummy = logicHost.getBlockEntity().getBlockState().getBlock().asItem().getDefaultInstance();
        UUID id = UUID.randomUUID();
        dummy.getOrCreateTag().putUUID(DUMMY_ID_KEY, id);

        if (displayName != null) {
            dummy.setHoverName(displayName);
        }

        AEItemKey key = AEItemKey.of(dummy);

        if (key == null) {
            return new PrepareResult(PrepareStatus.UNSUPPORTED, null);
        }

        var patternStack = PatternDetailsHelper.encodeProcessingPattern(
                inputs,
                new GenericStack[] { new GenericStack(key, 1) });

        logic.getPatternInv().setItemDirect(0, patternStack);
        logic.updatePatterns();

        this.requestId = id;
        this.dummyStack = dummy;
        this.dummyKey = key;
        this.expectedPatterns = 0;
        this.pushedPatterns = 0;
        this.patternTotal = inputSum;
        this.patternStacks = inputs;
        this.expectedTotal = inputSum;
        this.awaitingSinceTick = now();
        this.progressDone = 0;
        this.state = BufferRequestState.AWAITING_CONFIRM;

        CraftingBufferRequests.register(id, this);
        onDirty.run();

        return new PrepareResult(PrepareStatus.AWAITING_CONFIRM, key);
    }

    public void onJobSubmitted(ICraftingLink link, long amount) {
        if (link == null) {
            return;
        }

        activeLinks.add(link);

        long requested = Math.max(1, amount);

        if (state == BufferRequestState.CRAFTING) {
            this.expectedPatterns += requested;
            this.expectedTotal += requested * patternTotal;
        } else {
            this.expectedPatterns = requested;
            this.pushedPatterns = 0;
            this.expectedTotal += (requested - 1) * patternTotal;
            this.state = BufferRequestState.CRAFTING;
        }

        onDirty.run();
    }

    public void onPushPatternComplete() {
        pushedPatterns++;

        if (pushedPatterns < Math.max(1, expectedPatterns)) {
            onDirty.run();
            return;
        }

        finishRequest();
    }

    private void finishRequest() {
        clearPattern();
        CraftingBufferRequests.unregister(requestId);

        readyAtTick = now() + 1;
        dummyCleanupUntilTick = now() + DUMMY_CLEANUP_TICKS;

        if (activeLinks.isEmpty()) {
            pendingDummyReturn = Math.max(1, pushedPatterns);
        } else {
            cancelAllLinks();
        }

        onDirty.run();
    }

    public void abortRequest() {
        endRequest(BufferRequestState.IDLE);
    }

    public void failRequest() {
        endRequest(BufferRequestState.ERROR);
    }

    private void endRequest(BufferRequestState finalState) {
        if (aborting) {
            return;
        }

        aborting = true;

        try {
            doEndRequest(finalState);
        } finally {
            aborting = false;
        }
    }

    private void doEndRequest(BufferRequestState finalState) {
        clearPattern();
        cancelAllLinks();
        CraftingBufferRequests.unregister(requestId);

        requestId = null;
        dummyStack = null;
        dummyKey = null;
        expectedPatterns = 0;
        pushedPatterns = 0;
        expectedTotal = 0;
        patternTotal = 0;
        patternStacks = new GenericStack[0];
        awaitingSinceTick = 0;
        readyAtTick = 0;
        state = finalState;

        beginFlush();
        onDirty.run();
    }

    private boolean flushUnneeded(GenericStack[] required) {
        if (items.isEmpty()) {
            flushPending = false;
            return true;
        }

        var needed = new Object2LongOpenHashMap<AEKey>();
        needed.defaultReturnValue(0L);
        for (var s : required) {
            if (s != null && s.what() != null && s.amount() > 0) {
                needed.put(s.what(), needed.getLong(s.what()) + s.amount());
            }
        }

        var grid = grid();
        if (grid != null) {
            var inv = grid.getStorageService().getInventory();
            var es = grid.getEnergyService();
            var it = items.object2LongEntrySet().iterator();
            while (it.hasNext()) {
                var e = it.next();
                long have = e.getLongValue();
                long excess = have - needed.getLong(e.getKey());
                if (excess <= 0)
                    continue;

                long inserted = StorageHelper.poweredInsert(es, inv, e.getKey(), excess, src(), Actionable.MODULATE);
                long remaining = have - inserted;
                if (remaining <= 0)
                    it.remove();
                else
                    e.setValue(remaining);
            }
        }

        boolean stillHasExcess = false;
        for (var e : items.object2LongEntrySet()) {
            if (e.getLongValue() > needed.getLong(e.getKey())) {
                stillHasExcess = true;
                break;
            }
        }

        flushPending = stillHasExcess;
        onDirty.run();
        return !stillHasExcess;
    }

    public void tick(int ticksSinceLastCall) {
        tickRequestState();
        tickCrafting();
        tickDummy();
        tickFlush(ticksSinceLastCall);
        tickProgress();
    }

    private void tickProgress() {
        if (state == BufferRequestState.IDLE) {
            progressDone = 0;
            return;
        }

        progressDone = Math.max(progressDone, computeProgressDone());
    }

    private void tickRequestState() {
        if (state == BufferRequestState.AWAITING_CONFIRM
                && readyAtTick == 0
                && activeLinks.isEmpty()
                && now() - awaitingSinceTick > CONFIRM_TIMEOUT_TICKS) {
            SpatialToolsCMP.getLOGGER().debug("managed buffer request {} was never confirmed, dropping it", requestId);
            abortRequest();
            return;
        }

        if (state == BufferRequestState.READY && now() > readyUntilTick && !flushPending) {
            requestId = null;
            dummyStack = null;
            expectedPatterns = 0;
            pushedPatterns = 0;
            expectedTotal = 0;
            patternTotal = 0;
            patternStacks = new GenericStack[0];
            state = BufferRequestState.IDLE;
            onDirty.run();
        }

        if (!state.isBusy()
                && state != BufferRequestState.READY
                && !items.isEmpty()
                && !flushPending
                && !hasActiveCrafting()) {
            beginFlush();
        }
    }

    private void tickCrafting() {
        if (readyAtTick > 0 && now() >= readyAtTick) {
            readyAtTick = 0;
            fireReady();
        }
    }

    private void tickDummy() {
        if (dummyKey == null) {
            return;
        }

        var grid = grid();

        if (grid == null) {
            return;
        }

        if (pendingDummyReturn > 0) {
            var inv = grid.getStorageService().getInventory();
            var es = grid.getEnergyService();

            StorageHelper.poweredInsert(es, inv, dummyKey, pendingDummyReturn, src(), Actionable.MODULATE);
            pendingDummyReturn = 0;
        }

        if (dummyCleanupUntilTick > 0) {
            if (now() > dummyCleanupUntilTick) {
                dummyCleanupUntilTick = 0;
                dummyKey = null;
                return;
            }

            grid.getStorageService().getInventory().extract(dummyKey, Long.MAX_VALUE, Actionable.MODULATE, src());
        }
    }

    public ImmutableSet<ICraftingLink> getRequestedJobs() {
        return ImmutableSet.copyOf(activeLinks);
    }

    public long insertCraftedItems(AEKey what, long amount, Actionable mode) {
        if (what == null) {
            return 0;
        }

        if (dummyKey != null && dummyKey.equals(what)) {
            return amount;
        }

        if (mode == Actionable.MODULATE) {
            add(what, amount);
        }

        return 0;
    }

    public void jobStateChange(ICraftingLink link) {
        activeLinks.remove(link);
        onDirty.run();

        if (link.isDone()) {
            if (activeLinks.isEmpty() && readyAtTick == 0) {
                readyAtTick = now() + 1;
            }
        } else if (link.isCanceled()) {
            if (readyAtTick == 0 && activeLinks.isEmpty() && state == BufferRequestState.CRAFTING) {
                failRequest();
            }
        }
    }

    public void beginFlush() {
        if (items.isEmpty()) {
            flushPending = false;
            return;
        }
        flushPending = true;
        flushTickAcc = 0;
        onDirty.run();
    }

    private void tickFlush(int ticksSinceLastCall) {
        if (!flushPending)
            return;
        flushTickAcc += ticksSinceLastCall;
        if (flushTickAcc >= 20) {
            flushTickAcc = 0;
            flushOnce();
        }
    }

    private void flushOnce() {
        var grid = grid();
        if (grid == null)
            return;
        var inv = grid.getStorageService().getInventory();
        var es = grid.getEnergyService();
        var it = items.object2LongEntrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            long amt = e.getLongValue();
            if (amt <= 0) {
                it.remove();
                continue;
            }
            long inserted = StorageHelper.poweredInsert(es, inv, e.getKey(), amt, src(), Actionable.MODULATE);
            if (inserted >= amt)
                it.remove();
            else
                e.setValue(amt - inserted);
        }
        if (items.isEmpty())
            flushPending = false;
        onDirty.run();
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();

        tag.put("entries", saveGenericStacks(toEntryArray()));

        ListTag linkTags = new ListTag();
        for (var link : activeLinks) {
            if (link.isCanceled() || link.isDone())
                continue;
            CompoundTag lt = new CompoundTag();
            link.writeToNBT(lt);
            linkTags.add(lt);
        }
        tag.put("links", linkTags);

        tag.putBoolean("flushPending", flushPending);
        tag.putInt("flushTickAcc", flushTickAcc);

        tag.putInt("state", state.ordinal());
        tag.putLong("expectedPatterns", expectedPatterns);
        tag.putLong("pushedPatterns", pushedPatterns);
        tag.putLong("expectedTotal", expectedTotal);
        tag.putLong("patternTotal", patternTotal);
        tag.put("patternStacks", saveGenericStacks(patternStacks));
        tag.putLong("awaitingFor", Math.max(0, now() - awaitingSinceTick));

        if (requestId != null) {
            tag.putUUID("requestId", requestId);
        }

        if (dummyStack != null && !dummyStack.isEmpty()) {
            tag.put("dummy", dummyStack.save(new CompoundTag()));
        }

        ItemStack patternSlot = logic.getPatternInv().getStackInSlot(0);
        if (!patternSlot.isEmpty()) {
            tag.put("patternSlot", patternSlot.save(new CompoundTag()));
        }

        return tag;
    }

    public void fromTag(CompoundTag tag) {
        items.clear();

        GenericStack[] entries = loadGenericStacks(tag.getList("entries", Tag.TAG_COMPOUND));
        for (GenericStack s : entries) {
            if (s == null || s.amount() <= 0 || s.what() == null)
                continue;
            items.put(s.what(), s.amount());
        }

        flushPending = tag.getBoolean("flushPending") && !items.isEmpty();
        flushTickAcc = tag.getInt("flushTickAcc");
        readyAtTick = 0;
        readyUntilTick = 0;
        dummyCleanupUntilTick = 0;
        pendingDummyReturn = 0;
        activeLinks.clear();

        state = BufferRequestState.byOrdinal(tag.getInt("state"));
        expectedPatterns = tag.getLong("expectedPatterns");
        pushedPatterns = tag.getLong("pushedPatterns");
        expectedTotal = tag.getLong("expectedTotal");
        patternTotal = tag.getLong("patternTotal");
        patternStacks = loadGenericStacks(tag.getList("patternStacks", Tag.TAG_COMPOUND));
        awaitingSinceTick = -tag.getLong("awaitingFor");

        if (tag.hasUUID("requestId")) {
            requestId = tag.getUUID("requestId");
        } else {
            requestId = null;
        }

        if (tag.contains("dummy", Tag.TAG_COMPOUND)) {
            dummyStack = ItemStack.of(tag.getCompound("dummy"));
            dummyKey = dummyStack.isEmpty() ? null : AEItemKey.of(dummyStack);
        } else {
            dummyStack = null;
            dummyKey = null;
        }

        if (state == BufferRequestState.READY) {
            state = BufferRequestState.IDLE;
        }

        if (actionHost instanceof ICraftingRequester requester) {
            ListTag linkTags = tag.getList("links", Tag.TAG_COMPOUND);
            for (int i = 0; i < linkTags.size(); i++) {
                CompoundTag lt = linkTags.getCompound(i);
                ICraftingLink link = StorageHelper.loadCraftingLink(lt, requester);
                if (link != null) {
                    activeLinks.add(link);
                }
            }
        }

        if (tag.contains("patternSlot", Tag.TAG_COMPOUND)) {
            ItemStack patternSlot = ItemStack.of(tag.getCompound("patternSlot"));
            if (!patternSlot.isEmpty()) {
                logic.getPatternInv().setItemDirect(0, patternSlot);
            }
        }
    }

    public void onLoad() {
        if (!logic.getPatternInv().getStackInSlot(0).isEmpty()) {
            logic.updatePatterns();
        }

        if (awaitingSinceTick <= 0) {
            awaitingSinceTick = now() + awaitingSinceTick;
        }

        if (requestId != null && isRequestUsable()) {
            CraftingBufferRequests.register(requestId, this);
        }
    }

    public void onUnload() {
        CraftingBufferRequests.unregister(requestId);
    }

    public GenericStack[] getItemsAsStacks() {
        return toEntryArray();
    }

    public boolean hasActiveCrafting() {
        return !activeLinks.isEmpty() || readyAtTick > 0;
    }

    private void clearPattern() {
        logic.getPatternInv().setItemDirect(0, ItemStack.EMPTY);
        logic.updatePatterns();
    }

    private void cancelAllLinks() {
        var toCancel = new ArrayList<>(activeLinks);
        activeLinks.clear();
        for (var link : toCancel) {
            try {
                link.cancel();
            } catch (Throwable e) {
                SpatialToolsCMP.getLOGGER().debug("error cancelling crafting link", e);
            }
        }
    }

    private void fireReady() {
        state = BufferRequestState.READY;
        readyUntilTick = now() + READY_DISPLAY_TICKS;
        CraftingBufferRequests.unregister(requestId);
        beginFlush();
        onReady.run();
    }

    private IActionSource src() {
        return IActionSource.ofMachine(actionHost);
    }

    private long now() {
        Level level = logicHost.getBlockEntity().getLevel();
        return level == null ? 0 : level.getGameTime();
    }

    @Nullable
    private IGrid grid() {
        return mainNode.getGrid();
    }

    private GenericStack[] toEntryArray() {
        GenericStack[] result = new GenericStack[items.size()];
        int i = 0;

        for (Object2LongMap.Entry<AEKey> e : items.object2LongEntrySet()) {
            if (e.getLongValue() > 0 && e.getKey() != null) {
                result[i++] = new GenericStack(e.getKey(), e.getLongValue());
            }
        }

        if (i == result.length) {
            return result;
        }

        GenericStack[] trimmed = new GenericStack[i];
        System.arraycopy(result, 0, trimmed, 0, i);
        return trimmed;
    }

    private static ListTag saveGenericStacks(GenericStack[] stacks) {
        ListTag list = new ListTag();

        for (GenericStack stack : stacks) {
            if (stack == null || stack.what() == null || stack.amount() <= 0)
                continue;

            ItemStack wrapped = GenericStack.wrapInItemStack(stack);
            if (!wrapped.isEmpty()) {
                list.add(wrapped.save(new CompoundTag()));
            }
        }

        return list;
    }

    private static GenericStack[] loadGenericStacks(ListTag list) {
        GenericStack[] tmp = new GenericStack[list.size()];
        int count = 0;

        for (int i = 0; i < list.size(); i++) {
            ItemStack wrapped = ItemStack.of(list.getCompound(i));
            if (wrapped.isEmpty())
                continue;

            GenericStack stack = GenericStack.fromItemStack(wrapped);
            if (stack == null || stack.what() == null || stack.amount() <= 0)
                continue;

            tmp[count++] = stack;
        }

        if (count == tmp.length) {
            return tmp;
        }

        GenericStack[] trimmed = new GenericStack[count];
        System.arraycopy(tmp, 0, trimmed, 0, count);
        return trimmed;
    }
}
