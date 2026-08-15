package net.oktawia.spatialtoolscmp.compat.ae2;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import net.minecraft.nbt.CompoundTag;

import appeng.api.networking.crafting.ICraftingPlan;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.networking.crafting.ICraftingSubmitResult;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;

import net.oktawia.spatialtoolscmp.SpatialToolsCMP;
import net.oktawia.spatialtoolscmp.logic.buffer.ManagedBuffer;

public final class CraftingBufferRequests {
    private static final Map<UUID, ManagedBuffer> PENDING = new ConcurrentHashMap<>();

    private CraftingBufferRequests() {
    }

    public static void register(UUID requestId, ManagedBuffer buffer) {
        if (requestId == null || buffer == null) {
            return;
        }

        PENDING.put(requestId, buffer);
    }

    public static void unregister(@Nullable UUID requestId) {
        if (requestId == null) {
            return;
        }

        PENDING.remove(requestId);
    }

    @Nullable
    public static UUID readRequestId(@Nullable AEKey key) {
        if (!(key instanceof AEItemKey itemKey)) {
            return null;
        }

        CompoundTag tag = itemKey.getTag();

        if (tag == null || !tag.hasUUID(ManagedBuffer.DUMMY_ID_KEY)) {
            return null;
        }

        return tag.getUUID(ManagedBuffer.DUMMY_ID_KEY);
    }

    @Nullable
    public static ManagedBuffer find(@Nullable AEKey key) {
        UUID requestId = readRequestId(key);

        if (requestId == null) {
            return null;
        }

        ManagedBuffer buffer = PENDING.get(requestId);

        if (buffer == null) {
            return null;
        }

        if (!buffer.isRequestUsable()) {
            PENDING.remove(requestId, buffer);
            return null;
        }

        return buffer;
    }

    @Nullable
    public static ICraftingRequester resolveRequester(
            @Nullable ICraftingPlan plan,
            @Nullable ICraftingRequester original) {
        ManagedBuffer buffer = findForPlan(plan);

        if (buffer == null) {
            return original;
        }

        ICraftingRequester requester = buffer.getRequester();

        return requester != null ? requester : original;
    }

    public static void onSubmitResult(@Nullable ICraftingPlan plan, @Nullable ICraftingSubmitResult result) {
        if (result == null || !result.successful() || result.link() == null) {
            return;
        }

        ManagedBuffer buffer = findForPlan(plan);

        if (buffer == null) {
            return;
        }

        try {
            buffer.onJobSubmitted(result.link(), plan.finalOutput().amount());
        } catch (Throwable e) {
            SpatialToolsCMP.getLOGGER().debug("failed to attach crafting link to managed buffer", e);
        }
    }

    @Nullable
    private static ManagedBuffer findForPlan(@Nullable ICraftingPlan plan) {
        try {
            if (plan == null || plan.finalOutput() == null) {
                return null;
            }

            return find(plan.finalOutput().what());
        } catch (Throwable e) {
            SpatialToolsCMP.getLOGGER().debug("failed to resolve managed buffer for crafting plan", e);
            return null;
        }
    }
}
