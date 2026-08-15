package net.oktawia.spatialtoolscmp.logic;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import net.oktawia.spatialtoolscmp.IsModLoaded;
import net.oktawia.spatialtoolscmp.compat.ae2.AE2MEOps;
import net.oktawia.spatialtoolscmp.items.AbstractStructureCaptureToolItem;
import net.oktawia.spatialtoolscmp.items.PortableSpatialCloner;
import net.oktawia.spatialtoolscmp.network.packets.SyncClonerRequirementStatusPacket;
import net.oktawia.spatialtoolscmp.util.StructureToolKeys;

public final class ClonerRequirementStatus {

    private static final int REQUIREMENT_CACHE_SIZE = 8;

    private record Requirement(ItemStack stack, long required) {
    }

    private record CachedRequirements(long stamp, List<Requirement> rows) {
    }

    private static final Map<String, CachedRequirements> REQUIREMENT_CACHE = Collections.synchronizedMap(
            new LinkedHashMap<>(REQUIREMENT_CACHE_SIZE, 0.75f, true) {

                @Override
                protected boolean removeEldestEntry(Map.Entry<String, CachedRequirements> eldest) {
                    return size() > REQUIREMENT_CACHE_SIZE;
                }
            });

    private ClonerRequirementStatus() {
    }

    public static List<SyncClonerRequirementStatusPacket.Entry> build(
            Player player,
            ItemStack toolStack,
            CompoundTag structureTag) {
        if (player == null || toolStack.isEmpty() || structureTag == null) {
            return List.of();
        }

        return toEntries(player, toolStack, readRequirements(structureTag));
    }

    public static List<SyncClonerRequirementStatusPacket.Entry> buildForSelectedStructure(
            ServerPlayer player,
            ItemStack toolStack) {
        if (player == null || toolStack.isEmpty()) {
            return List.of();
        }

        String id = StructureToolStackState.getStructureId(toolStack);
        UUID owner = StructureToolStackState.getClonerLibraryOwner(toolStack);

        if (id.isBlank()) {
            return List.of();
        }

        String cacheKey = owner + ":" + id;
        long stamp = 0L;

        try {
            if (owner != null) {
                stamp = ClonerStructureLibraryStore.lastModified(player.server, owner, id);

                CachedRequirements cached = REQUIREMENT_CACHE.get(cacheKey);

                if (cached != null && cached.stamp() == stamp && stamp != 0L) {
                    return toEntries(player, toolStack, cached.rows());
                }
            }

            CompoundTag structureTag = ClonerStructureLibraryStore.loadSelectedOrMigrateLegacy(
                    player.server,
                    player.getUUID(),
                    toolStack);

            if (structureTag == null || structureTag.isEmpty()) {
                return List.of();
            }

            List<Requirement> rows = readRequirements(structureTag);

            if (stamp != 0L) {
                REQUIREMENT_CACHE.put(cacheKey, new CachedRequirements(stamp, rows));
            }

            return toEntries(player, toolStack, rows);
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private static List<Requirement> readRequirements(CompoundTag structureTag) {
        if (!structureTag.contains(StructureToolKeys.CLONE_METADATA_KEY, Tag.TAG_COMPOUND)) {
            return List.of();
        }

        CompoundTag metadata = structureTag.getCompound(StructureToolKeys.CLONE_METADATA_KEY);

        if (!metadata.contains(StructureToolKeys.CLONE_REQUIREMENTS_KEY, Tag.TAG_LIST)) {
            return List.of();
        }

        ListTag requirements = metadata.getList(
                StructureToolKeys.CLONE_REQUIREMENTS_KEY,
                Tag.TAG_COMPOUND);

        List<Requirement> rows = new ArrayList<>(requirements.size());

        for (int i = 0; i < requirements.size(); i++) {
            CompoundTag row = requirements.getCompound(i);

            if (!row.contains(StructureToolKeys.CLONE_KEY_STACK, Tag.TAG_COMPOUND)) {
                continue;
            }

            ItemStack stack = ItemStack.of(row.getCompound(StructureToolKeys.CLONE_KEY_STACK));

            if (stack.isEmpty()) {
                continue;
            }

            stack = stack.copy();
            stack.setCount(1);

            rows.add(new Requirement(stack, Math.max(1L, row.getLong(StructureToolKeys.CLONE_KEY_COUNT))));
        }

        return rows;
    }

    private static List<SyncClonerRequirementStatusPacket.Entry> toEntries(
            Player player,
            ItemStack toolStack,
            List<Requirement> rows) {
        List<SyncClonerRequirementStatusPacket.Entry> out = new ArrayList<>(rows.size());

        PortableSpatialCloner.NestedInventoryResourceMode nestedMode = PortableSpatialCloner
                .getNestedInventoryResourceMode(toolStack);

        for (Requirement row : rows) {
            ItemStack stack = row.stack().copy();

            out.add(new SyncClonerRequirementStatusPacket.Entry(
                    stack,
                    countAvailable(player, toolStack, stack, nestedMode),
                    row.required(),
                    isCraftable(player, toolStack, stack)));
        }

        return out;
    }

    public static boolean hasMissing(List<SyncClonerRequirementStatusPacket.Entry> entries) {
        for (SyncClonerRequirementStatusPacket.Entry entry : entries) {
            if (entry.available() < entry.required()) {
                return true;
            }
        }

        return false;
    }

    private static long countAvailable(
            Player player,
            ItemStack toolStack,
            ItemStack wanted,
            PortableSpatialCloner.NestedInventoryResourceMode nestedMode) {
        long available = countPlayerInventory(player, wanted);

        if (!(player.level() instanceof ServerLevel serverLevel)) {
            return available;
        }

        if (nestedMode.usePlayerNested()) {
            available += PortableSpatialCloner.countNestedInventoryInPlayerInventory(
                    serverLevel,
                    player,
                    toolStack,
                    wanted);
        }

        if (PortableSpatialCloner.hasItemHandlerLink(toolStack)) {
            available += PortableSpatialCloner.countLinkedItemHandlerStorage(
                    serverLevel,
                    toolStack,
                    wanted);

            if (nestedMode.useConnectedNested()) {
                available += PortableSpatialCloner.countNestedInventoryInLinkedItemHandlerStorage(
                        serverLevel,
                        toolStack,
                        wanted);
            }
        } else if (IsModLoaded.AE2) {
            try {
                available += AE2MEOps.getAmount(wanted, toolStack, serverLevel);
            } catch (Throwable ignored) {
            }
        }

        return available;
    }

    private static boolean isCraftable(Player player, ItemStack toolStack, ItemStack wanted) {
        if (!IsModLoaded.AE2
                || !hasCraftingUpgrade(toolStack)
                || PortableSpatialCloner.hasItemHandlerLink(toolStack)
                || !(player.level() instanceof ServerLevel serverLevel)) {
            return false;
        }

        try {
            return AE2MEOps.isCraftable(wanted, toolStack, serverLevel);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean hasCraftingUpgrade(ItemStack toolStack) {
        return toolStack.getItem() instanceof AbstractStructureCaptureToolItem toolItem
                && toolItem.hasInstalledCraftingUpgrade(toolStack);
    }

    private static long countPlayerInventory(Player player, ItemStack wanted) {
        if (wanted.isEmpty()) {
            return 0L;
        }

        long total = 0L;
        Inventory inventory = player.getInventory();

        for (ItemStack stack : inventory.items) {
            if (!stack.isEmpty() && ItemStack.isSameItemSameTags(stack, wanted)) {
                total += stack.getCount();
            }
        }

        for (ItemStack stack : inventory.offhand) {
            if (!stack.isEmpty() && ItemStack.isSameItemSameTags(stack, wanted)) {
                total += stack.getCount();
            }
        }

        return total;
    }
}
