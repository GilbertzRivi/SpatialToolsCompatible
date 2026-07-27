package net.oktawia.spatialtoolscmp.logic.extensions;

import mekanism.api.Upgrade;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import net.oktawia.spatialtoolscmp.items.AbstractStructureCaptureToolItem;
import net.oktawia.spatialtoolscmp.logic.ClonerPasteContext;
import net.oktawia.spatialtoolscmp.logic.PlacementPlan;
import net.oktawia.spatialtoolscmp.logic.StructureCloneExtension;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class MekanismClonerExtension implements StructureCloneExtension {

    private static final String MOD_ID = "mekanism";

    private static final String NBT_ID = "id";

    private static final String NBT_COMPONENT_CONFIG = "componentConfig";
    private static final String NBT_COMPONENT_EJECTOR = "componentEjector";
    private static final String NBT_COMPONENT_UPGRADE = "componentUpgrade";
    private static final String NBT_UPGRADES = "upgrades";

    private static final String CLONE_KEY_MEKANISM = "mekanism";

    @Override
    public boolean handlesRequirements(BlockState state, @Nullable CompoundTag rawBeTag) {
        return isMekanismBlock(state, rawBeTag);
    }

    @Override
    public boolean collectMetadata(
            ServerLevel level,
            BlockPos pos,
            BlockEntity be,
            @Nullable CompoundTag rawBeTag,
            AbstractStructureCaptureToolItem.RequirementSink requirements,
            CompoundTag blockEntry
    ) {
        BlockState state = level.getBlockState(pos);

        if (!isMekanismBlock(state, rawBeTag)) {
            return false;
        }

        addBaseBlockRequirement(level, pos, requirements);

        CompoundTag mekanismData = collectMekanismData(rawBeTag);

        for (ItemStack upgradeStack : getUpgradeCosts(mekanismData)) {
            if (!upgradeStack.isEmpty()) {
                requirements.add(upgradeStack);
            }
        }

        if (!mekanismData.isEmpty()) {
            blockEntry.put(CLONE_KEY_MEKANISM, mekanismData);
        }

        return true;
    }

    @Override
    public Optional<PlacementPlan> buildPlacementPlan(
            ServerLevel level,
            Player player,
            BlockState state,
            @Nullable CompoundTag rawBeTag,
            @Nullable CompoundTag blockMetadata,
            ClonerPasteContext ctx
    ) {
        if (!isMekanismBlock(state, rawBeTag)) {
            return Optional.empty();
        }

        CompoundTag mekanismData = getMekanismMetadata(blockMetadata);
        CompoundTag filteredTag = createWhitelistedMekanismTag(rawBeTag, mekanismData);

        Map<Item, Integer> reserved = new LinkedHashMap<>();
        List<ItemStack> costs = new ArrayList<>();

        ItemStack baseItem = normalizeSingle(ctx.getRequiredBlockItem(state));

        if (baseItem.isEmpty()) {
            if (!player.isCreative()) {
                return Optional.of(PlacementPlan.none());
            }
        } else {
            if (!player.isCreative() && !ctx.canReserveForPaste(reserved, baseItem, 1)) {
                return Optional.of(PlacementPlan.none());
            }

            costs.add(baseItem);
        }

        for (ItemStack upgradeStack : getUpgradeCosts(mekanismData)) {
            if (upgradeStack.isEmpty()) {
                continue;
            }

            ItemStack cost = upgradeStack.copy();

            if (!player.isCreative()
                    && !ctx.canReserveForPaste(reserved, cost, cost.getCount())) {
                return Optional.of(PlacementPlan.none());
            }

            costs.add(cost);
        }

        return Optional.of(new PlacementPlan(
                true,
                state,
                filteredTag.isEmpty() ? null : filteredTag,
                costs
        ));
    }

    @Override
    public void onBlockPlaced(
            ServerLevel level,
            BlockPos pos,
            @Nullable BlockEntity be,
            @Nullable CompoundTag blockMetadata
    ) {
        if (be == null || getMekanismMetadata(blockMetadata).isEmpty()) {
            return;
        }

        be.setChanged();

        BlockState state = level.getBlockState(pos);
        level.sendBlockUpdated(pos, state, state, 3);
    }

    private static CompoundTag collectMekanismData(@Nullable CompoundTag rawBeTag) {
        CompoundTag out = new CompoundTag();

        if (rawBeTag == null || rawBeTag.isEmpty()) {
            return out;
        }

        copyWhitelistedKeys(rawBeTag, out);

        out.remove(NBT_COMPONENT_UPGRADE);
        out.remove(NBT_UPGRADES);

        copyUpgradeData(rawBeTag, out);

        return out;
    }

    private static void copyUpgradeData(CompoundTag rawBeTag, CompoundTag out) {
        if (rawBeTag.contains(NBT_COMPONENT_UPGRADE, Tag.TAG_COMPOUND)) {
            CompoundTag rawComponentUpgrade = rawBeTag.getCompound(NBT_COMPONENT_UPGRADE);

            if (rawComponentUpgrade.contains(NBT_UPGRADES, Tag.TAG_LIST)) {
                CompoundTag componentUpgrade = new CompoundTag();
                componentUpgrade.put(
                        NBT_UPGRADES,
                        rawComponentUpgrade.getList(NBT_UPGRADES, Tag.TAG_COMPOUND).copy()
                );

                out.put(NBT_COMPONENT_UPGRADE, componentUpgrade);
                return;
            }
        }

        if (rawBeTag.contains(NBT_UPGRADES, Tag.TAG_LIST)) {
            out.put(
                    NBT_UPGRADES,
                    rawBeTag.getList(NBT_UPGRADES, Tag.TAG_COMPOUND).copy()
            );
        }
    }

    private static CompoundTag getMekanismMetadata(@Nullable CompoundTag blockMetadata) {
        if (blockMetadata == null) {
            return new CompoundTag();
        }

        if (!blockMetadata.contains(CLONE_KEY_MEKANISM, Tag.TAG_COMPOUND)) {
            return new CompoundTag();
        }

        return blockMetadata.getCompound(CLONE_KEY_MEKANISM);
    }

    private static CompoundTag createWhitelistedMekanismTag(
            @Nullable CompoundTag rawBeTag,
            CompoundTag mekanismData
    ) {
        CompoundTag out = new CompoundTag();

        if (rawBeTag != null) {
            copyWhitelistedKeys(rawBeTag, out);

            out.remove(NBT_COMPONENT_UPGRADE);
            out.remove(NBT_UPGRADES);
        }

        copyWhitelistedKeys(mekanismData, out);

        return out;
    }

    private static void copyWhitelistedKeys(CompoundTag from, CompoundTag to) {
        copyStringIfPresent(from, to, NBT_ID);

        copyTagIfPresent(from, to, NBT_COMPONENT_CONFIG);
        copyTagIfPresent(from, to, NBT_COMPONENT_EJECTOR);
        copyTagIfPresent(from, to, NBT_COMPONENT_UPGRADE);
        copyTagIfPresent(from, to, NBT_UPGRADES);

        copyPrimitiveIfPresent(from, to, "controlType");
        copyPrimitiveIfPresent(from, to, "redstone");
        copyPrimitiveIfPresent(from, to, "strictInput");
        copyPrimitiveIfPresent(from, to, "autoEject");
        copyPrimitiveIfPresent(from, to, "roundRobin");

        copySideKeys(from, to);
    }

    private static void copySideKeys(CompoundTag from, CompoundTag to) {
        for (int side = 0; side < Direction.values().length; side++) {
            copyPrimitiveIfPresent(from, to, "side" + side);
            copyPrimitiveIfPresent(from, to, "eject" + side);
            copyPrimitiveIfPresent(from, to, "color" + side);
            copyPrimitiveIfPresent(from, to, "connection" + side);
            copyPrimitiveIfPresent(from, to, "mode" + side);
        }
    }

    private static List<ItemStack> getUpgradeCosts(CompoundTag mekanismData) {
        List<ItemStack> out = new ArrayList<>();
        ListTag upgrades = getUpgradeList(mekanismData);

        if (upgrades == null || upgrades.isEmpty()) {
            return out;
        }

        for (int i = 0; i < upgrades.size(); i++) {
            CompoundTag row = upgrades.getCompound(i);

            if (!row.contains("type", Tag.TAG_ANY_NUMERIC)
                    || !row.contains("amount", Tag.TAG_ANY_NUMERIC)) {
                continue;
            }

            Upgrade upgrade;

            try {
                upgrade = Upgrade.byIndexStatic(row.getInt("type"));
            } catch (Throwable ignored) {
                continue;
            }

            int amount = Mth.clamp(row.getInt("amount"), 0, upgrade.getMax());

            if (amount <= 0) {
                continue;
            }

            ItemStack stack = getUpgradeItemStack(upgrade, amount);

            if (!stack.isEmpty()) {
                out.add(stack);
            }
        }

        return out;
    }

    private static @Nullable ListTag getUpgradeList(CompoundTag mekanismData) {
        if (mekanismData.contains(NBT_COMPONENT_UPGRADE, Tag.TAG_COMPOUND)) {
            CompoundTag componentUpgrade = mekanismData.getCompound(NBT_COMPONENT_UPGRADE);

            if (componentUpgrade.contains(NBT_UPGRADES, Tag.TAG_LIST)) {
                return componentUpgrade.getList(NBT_UPGRADES, Tag.TAG_COMPOUND);
            }
        }

        if (mekanismData.contains(NBT_UPGRADES, Tag.TAG_LIST)) {
            return mekanismData.getList(NBT_UPGRADES, Tag.TAG_COMPOUND);
        }

        return null;
    }

    private static ItemStack getUpgradeItemStack(Upgrade upgrade, int amount) {
        ResourceLocation itemId = new ResourceLocation(
                MOD_ID,
                "upgrade_" + upgrade.getRawName()
        );

        Item item = ForgeRegistries.ITEMS.getValue(itemId);

        if (item == null || item == Items.AIR) {
            return ItemStack.EMPTY;
        }

        ItemStack stack = new ItemStack(item);
        stack.setCount(amount);

        return stack;
    }

    private static boolean isMekanismBlock(BlockState state, @Nullable CompoundTag rawBeTag) {
        ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(state.getBlock());

        if (blockId != null && MOD_ID.equals(blockId.getNamespace())) {
            return true;
        }

        if (rawBeTag == null) {
            return false;
        }

        String id = rawBeTag.getString(NBT_ID);

        if (id.isBlank()) {
            return false;
        }

        ResourceLocation beId = ResourceLocation.tryParse(id);

        return beId != null && MOD_ID.equals(beId.getNamespace());
    }

    private static void addBaseBlockRequirement(
            ServerLevel level,
            BlockPos pos,
            AbstractStructureCaptureToolItem.RequirementSink requirements
    ) {
        BlockHitResult hit = new BlockHitResult(
                Vec3.atCenterOf(pos),
                Direction.UP,
                pos,
                false
        );

        ItemStack picked = level.getBlockState(pos).getCloneItemStack(hit, level, pos, null);

        if (!picked.isEmpty()) {
            requirements.add(picked);
            return;
        }

        Item item = level.getBlockState(pos).getBlock().asItem();

        if (item != Items.AIR) {
            requirements.add(new ItemStack(item));
        }
    }

    private static ItemStack normalizeSingle(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack copy = stack.copy();
        copy.setCount(1);
        copy.setTag(null);

        return copy;
    }

    private static void copyStringIfPresent(CompoundTag from, CompoundTag to, String key) {
        if (from.contains(key, Tag.TAG_STRING)) {
            to.putString(key, from.getString(key));
        }
    }

    private static void copyTagIfPresent(CompoundTag from, CompoundTag to, String key) {
        if (from.contains(key)) {
            Tag tag = from.get(key);

            if (tag != null) {
                to.put(key, tag.copy());
            }
        }
    }

    private static void copyPrimitiveIfPresent(CompoundTag from, CompoundTag to, String key) {
        if (!from.contains(key)) {
            return;
        }

        Tag tag = from.get(key);

        if (tag == null) {
            return;
        }

        byte type = tag.getId();

        if (type == Tag.TAG_BYTE
                || type == Tag.TAG_SHORT
                || type == Tag.TAG_INT
                || type == Tag.TAG_LONG
                || type == Tag.TAG_FLOAT
                || type == Tag.TAG_DOUBLE
                || type == Tag.TAG_STRING) {
            to.put(key, tag.copy());
        }
    }

    @Override
    public boolean collectUndoRefunds(
            ServerLevel level,
            BlockPos pos,
            BlockState state,
            @Nullable BlockEntity be,
            List<ItemStack> refunds
    ) {
        CompoundTag currentTag = saveCurrentTag(be);

        if (!isMekanismBlock(state, currentTag)) {
            return false;
        }

        addBaseBlockRefund(level, pos, refunds);

        CompoundTag mekanismData = collectMekanismData(currentTag);

        for (ItemStack upgradeStack : getUpgradeCosts(mekanismData)) {
            if (!upgradeStack.isEmpty()) {
                refunds.add(upgradeStack);
            }
        }

        return true;
    }

    @Nullable
    private static CompoundTag saveCurrentTag(@Nullable BlockEntity be) {
        if (be == null) {
            return null;
        }

        try {
            return be.saveWithFullMetadata();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static void addBaseBlockRefund(
            ServerLevel level,
            BlockPos pos,
            List<ItemStack> refunds
    ) {
        BlockHitResult hit = new BlockHitResult(
                Vec3.atCenterOf(pos),
                Direction.UP,
                pos,
                false
        );

        ItemStack picked = level.getBlockState(pos).getCloneItemStack(hit, level, pos, null);

        if (!picked.isEmpty()) {
            refunds.add(picked);
            return;
        }

        Item item = level.getBlockState(pos).getBlock().asItem();

        if (item != Items.AIR) {
            refunds.add(new ItemStack(item));
        }
    }
}