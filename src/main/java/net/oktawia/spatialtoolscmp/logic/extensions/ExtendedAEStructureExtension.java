package net.oktawia.spatialtoolscmp.logic.extensions;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import com.glodblock.github.extendedae.common.tileentities.TileWirelessConnector;
import com.glodblock.github.extendedae.common.tileentities.TileWirelessHub;
import com.glodblock.github.extendedae.config.EPPConfig;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

import net.oktawia.spatialtoolscmp.items.AbstractStructureCaptureToolItem;
import net.oktawia.spatialtoolscmp.logic.ClonerPasteContext;
import net.oktawia.spatialtoolscmp.logic.PlacementPlan;
import net.oktawia.spatialtoolscmp.logic.StructureCloneExtension;
import net.oktawia.spatialtoolscmp.util.NbtUtil;

public final class ExtendedAEStructureExtension implements StructureCloneExtension {

    private static final String ID_NAMESPACE = "expatternprovider";
    private static final String ID_CONNECTOR = "expatternprovider:wireless_connect";
    private static final String CLONE_KEY = "expatternprovider";

    private static final String NBT_ID = "id";
    private static final String NBT_COLOR = "color";
    private static final String NBT_HAS_OTHER = "hasOther";
    private static final String NBT_OTHER_IS_HUB = "otherIsHub";
    private static final String NBT_OPX = "opx";
    private static final String NBT_OPY = "opy";
    private static final String NBT_OPZ = "opz";
    private static final String NBT_OX = "ox";
    private static final String NBT_OY = "oy";
    private static final String NBT_OZ = "oz";

    private static final long NEXT_TICK_DELAY = 1L;
    private static final List<PendingConnectInit> PENDING = new ArrayList<>();
    private static boolean registered = false;

    private static final boolean HUB_AVAILABLE = isClassPresent(
            "com.glodblock.github.extendedae.common.tileentities.TileWirelessHub");

    private static boolean isClassPresent(String name) {
        try {
            Class.forName(name, false, ExtendedAEStructureExtension.class.getClassLoader());
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public boolean handlesRequirements(BlockState state, @Nullable CompoundTag rawBeTag) {
        ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (blockId != null && ID_NAMESPACE.equals(blockId.getNamespace())) {
            return true;
        }
        return isExPAPTag(rawBeTag);
    }

    @Override
    public boolean collectMetadata(
            ServerLevel level,
            BlockPos pos,
            BlockEntity be,
            @Nullable CompoundTag rawBeTag,
            AbstractStructureCaptureToolItem.RequirementSink requirements,
            CompoundTag blockEntry) {
        if (!isExPAPTag(rawBeTag)) {
            return false;
        }

        addBaseBlockRequirement(level, pos, requirements);

        CompoundTag data = new CompoundTag();
        data.putString(NBT_ID, rawBeTag.getString(NBT_ID));
        NbtUtil.copyStringIfPresent(rawBeTag, data, NBT_COLOR);

        if (ID_CONNECTOR.equals(rawBeTag.getString(NBT_ID)) && be instanceof TileWirelessConnector connector) {
            BlockPos otherPos = readConnectorOtherSide(connector);
            if (otherPos != null) {
                data.putBoolean(NBT_HAS_OTHER, true);
                data.putBoolean(NBT_OTHER_IS_HUB, HUB_AVAILABLE && HubOps.isHub(level.getBlockEntity(otherPos)));
                data.putInt(NBT_OPX, otherPos.getX());
                data.putInt(NBT_OPY, otherPos.getY());
                data.putInt(NBT_OPZ, otherPos.getZ());
                data.putInt(NBT_OX, otherPos.getX() - pos.getX());
                data.putInt(NBT_OY, otherPos.getY() - pos.getY());
                data.putInt(NBT_OZ, otherPos.getZ() - pos.getZ());
            }
        }

        blockEntry.put(CLONE_KEY, data);
        return true;
    }

    @Override
    public Optional<PlacementPlan> buildPlacementPlan(
            ServerLevel level,
            Player player,
            BlockState state,
            @Nullable CompoundTag rawBeTag,
            @Nullable CompoundTag blockMetadata,
            ClonerPasteContext ctx) {
        if (!isExPAPTag(rawBeTag)) {
            return Optional.empty();
        }

        CompoundTag data = getCloneData(blockMetadata);
        ItemStack baseItem = normalizeSingle(ctx.getRequiredBlockItem(state));

        if (baseItem.isEmpty() && !player.isCreative()) {
            return Optional.of(PlacementPlan.none());
        }

        List<ItemStack> costs = new ArrayList<>();
        if (!baseItem.isEmpty()) {
            costs.add(baseItem);
        }

        CompoundTag beTag = new CompoundTag();
        NbtUtil.copyStringIfPresent(rawBeTag, beTag, NBT_ID);
        String color = data.contains(NBT_COLOR, Tag.TAG_STRING) ? data.getString(NBT_COLOR)
                : rawBeTag.getString(NBT_COLOR);
        if (!color.isBlank()) {
            beTag.putString(NBT_COLOR, color);
        }

        return Optional.of(new PlacementPlan(true, state, beTag, costs));
    }

    @Override
    public void onBlockPlaced(
            ServerLevel level,
            Player player,
            BlockPos pos,
            @Nullable BlockEntity be,
            @Nullable CompoundTag blockMetadata) {
        if (!(be instanceof TileWirelessConnector)) {
            return;
        }

        CompoundTag data = getCloneData(blockMetadata);

        if (!data.getBoolean(NBT_HAS_OTHER)) {
            return;
        }

        ensureRegistered();

        PENDING.add(new PendingConnectInit(
                level,
                pos.immutable(),
                data.copy(),
                level.getGameTime() + NEXT_TICK_DELAY));
    }

    @Override
    public boolean collectUndoRefunds(
            ServerLevel level,
            BlockPos pos,
            BlockState state,
            @Nullable BlockEntity be,
            List<ItemStack> refunds) {
        ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (blockId == null || !ID_NAMESPACE.equals(blockId.getNamespace())) {
            return false;
        }
        addBaseBlockRefund(level, pos, refunds);
        return true;
    }

    private static void ensureRegistered() {
        if (!registered) {
            MinecraftForge.EVENT_BUS.register(ExtendedAEStructureExtension.class);
            registered = true;
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            PENDING.removeIf(p -> p.level == level);
        }
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (!(event.level instanceof ServerLevel level)) {
            return;
        }
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        long now = level.getGameTime();
        Iterator<PendingConnectInit> it = PENDING.iterator();

        while (it.hasNext()) {
            PendingConnectInit pending = it.next();
            if (pending.level != level) {
                continue;
            }
            if (now < pending.runAtGameTime) {
                continue;
            }
            applyWirelessLink(level, pending.connectorPos, pending.data);
            it.remove();
        }
    }

    private static void applyWirelessLink(ServerLevel level, BlockPos connPos, CompoundTag data) {
        BlockEntity be = level.getBlockEntity(connPos);
        if (!(be instanceof TileWirelessConnector connector)) {
            return;
        }
        if (connector.getFrequency() != 0L) {
            return;
        }

        BlockPos candidate = connPos.offset(data.getInt(NBT_OX), data.getInt(NBT_OY), data.getInt(NBT_OZ));
        if (tryConnectTo(connector, level.getBlockEntity(candidate))) {
            return;
        }

        if (!data.getBoolean(NBT_OTHER_IS_HUB)) {
            return;
        }

        BlockPos originalOtherPos = new BlockPos(data.getInt(NBT_OPX), data.getInt(NBT_OPY), data.getInt(NBT_OPZ));

        if (Math.sqrt(connPos.distSqr(originalOtherPos)) > EPPConfig.wirelessMaxRange) {
            return;
        }

        tryConnectTo(connector, level.getBlockEntity(originalOtherPos));
    }

    private static boolean tryConnectTo(TileWirelessConnector connector, @Nullable BlockEntity otherBe) {
        if (HUB_AVAILABLE) {
            Boolean hubResult = HubOps.tryConnectHub(connector, otherBe);
            if (hubResult != null) {
                return hubResult;
            }
        }

        if (otherBe instanceof TileWirelessConnector otherConnector && otherConnector.getFrequency() == 0L) {
            long newFreq = connector.getNewFreq();
            otherConnector.setFrequency(newFreq);
            otherConnector.setChanged();
            connector.setFrequency(newFreq);
            connector.setChanged();
            return true;
        }

        return false;
    }

    private record PendingConnectInit(
            ServerLevel level,
            BlockPos connectorPos,
            CompoundTag data,
            long runAtGameTime) {
    }

    private static final class HubOps {
        static boolean isHub(@Nullable BlockEntity be) {
            return be instanceof TileWirelessHub;
        }

        @Nullable
        static Boolean tryConnectHub(TileWirelessConnector connector, @Nullable BlockEntity otherBe) {
            if (!(otherBe instanceof TileWirelessHub hub)) {
                return null;
            }
            int port = hub.allocatePort();
            if (port < 0) {
                return false;
            }
            long newFreq = connector.getNewFreq();
            hub.setFrequency(newFreq, port);
            connector.setFrequency(newFreq);
            connector.setChanged();
            return true;
        }
    }

    @Nullable
    private static BlockPos readConnectorOtherSide(TileWirelessConnector connector) {
        try {
            return connector.getOtherSide();
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean isExPAPTag(@Nullable CompoundTag tag) {
        if (tag == null) {
            return false;
        }
        String id = tag.getString(NBT_ID);
        return !id.isBlank() && id.startsWith(ID_NAMESPACE + ":");
    }

    private static CompoundTag getCloneData(@Nullable CompoundTag blockMetadata) {
        if (blockMetadata == null || !blockMetadata.contains(CLONE_KEY, Tag.TAG_COMPOUND)) {
            return new CompoundTag();
        }
        return blockMetadata.getCompound(CLONE_KEY);
    }

    private static ItemStack normalizeSingle(ItemStack stack) {
        if (stack.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack copy = stack.copy();
        copy.setCount(1);
        copy.setTag(null);
        return copy;
    }

    private static void addBaseBlockRequirement(
            ServerLevel level,
            BlockPos pos,
            AbstractStructureCaptureToolItem.RequirementSink requirements) {
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
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

    private static void addBaseBlockRefund(ServerLevel level, BlockPos pos, List<ItemStack> refunds) {
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
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
