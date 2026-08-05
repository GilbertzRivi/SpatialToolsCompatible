package net.oktawia.spatialtoolscmp.client.scene;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.AbortableIterationConsumer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.entity.LevelEntityGetter;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.level.storage.WritableLevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.Scoreboard;
import net.minecraft.world.ticks.LevelTickAccess;

public class PreviewLevel extends Level {

    private static final LevelEntityGetter<Entity> NO_ENTITIES = new LevelEntityGetter<>() {

        @Override
        public @Nullable Entity get(int id) {
            return null;
        }

        @Override
        public @Nullable Entity get(UUID uuid) {
            return null;
        }

        @Override
        public Iterable<Entity> getAll() {
            return Collections.emptyList();
        }

        @Override
        public <U extends Entity> void get(EntityTypeTest<Entity, U> test, AbortableIterationConsumer<U> consumer) {
        }

        @Override
        public void get(AABB bounds, Consumer<Entity> consumer) {
        }

        @Override
        public <U extends Entity> void get(EntityTypeTest<Entity, U> test, AABB bounds,
                AbortableIterationConsumer<U> consumer) {
        }
    };

    private final Level parent;

    private final PreviewChunkSource chunkSource = new PreviewChunkSource(this);
    private final BiomeManager biomeManager = new BiomeManager(this, 0);
    private final Map<BlockPos, BlockState> blocks = new HashMap<>();
    private final Map<BlockPos, BlockEntity> blockEntities = new HashMap<>();

    public PreviewLevel(Level parent) {
        this(parent, true);
    }

    public PreviewLevel(Level parent, boolean clientSide) {
        super(
                (WritableLevelData) parent.getLevelData(),
                parent.dimension(),
                parent.registryAccess(),
                parent.dimensionTypeRegistration(),
                parent::getProfiler,
                clientSide,
                false,
                0,
                0);

        this.parent = parent;
    }

    public void clear() {
        this.blocks.clear();
        this.blockEntities.clear();
    }

    @Override
    public boolean setBlock(BlockPos pos, BlockState state, int flags, int recursionLeft) {
        BlockPos immutable = pos.immutable();

        this.blocks.put(immutable, state);
        this.blockEntities.remove(immutable);
        return true;
    }

    @Override
    public void setBlockEntity(BlockEntity blockEntity) {
        this.blockEntities.put(blockEntity.getBlockPos().immutable(), blockEntity);
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        return this.blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState());
    }

    @Override
    public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
        return this.blockEntities.get(pos);
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return getBlockState(pos).getFluidState();
    }

    @Override
    public boolean isLoaded(BlockPos pos) {
        return true;
    }

    @Override
    public ChunkSource getChunkSource() {
        return this.chunkSource;
    }

    @Override
    public LevelLightEngine getLightEngine() {
        return this.parent.getLightEngine();
    }

    @Override
    public int getBrightness(LightLayer layer, BlockPos pos) {
        return 15;
    }

    @Override
    public int getRawBrightness(BlockPos pos, int amount) {
        return 15;
    }

    @Override
    public boolean canSeeSky(BlockPos pos) {
        return true;
    }

    @Override
    public float getShade(Direction direction, boolean shade) {
        return switch (direction) {
            case DOWN, UP -> 0.9F;
            case NORTH, SOUTH -> 0.8F;
            case WEST, EAST -> 0.6F;
        };
    }

    @Override
    public int getBlockTint(BlockPos pos, ColorResolver colorResolver) {
        return this.parent.getBlockTint(pos, colorResolver);
    }

    @Override
    public Holder<Biome> getBiome(BlockPos pos) {
        return this.parent.getBiome(pos);
    }

    @Override
    public Holder<Biome> getUncachedNoiseBiome(int x, int y, int z) {
        return this.parent.getUncachedNoiseBiome(x, y, z);
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z) {
        return this.parent.getNoiseBiome(x, y, z);
    }

    @Override
    public BiomeManager getBiomeManager() {
        return this.biomeManager;
    }

    @Override
    public RegistryAccess registryAccess() {
        return this.parent.registryAccess();
    }

    @Override
    public FeatureFlagSet enabledFeatures() {
        return this.parent.enabledFeatures();
    }

    @Override
    public LevelTickAccess<Block> getBlockTicks() {
        return this.parent.getBlockTicks();
    }

    @Override
    public LevelTickAccess<Fluid> getFluidTicks() {
        return this.parent.getFluidTicks();
    }

    @Override
    public RecipeManager getRecipeManager() {
        return this.parent.getRecipeManager();
    }

    @Override
    public Scoreboard getScoreboard() {
        return this.parent.getScoreboard();
    }

    @Override
    public int getFreeMapId() {
        return 0;
    }

    @Override
    public @Nullable MapItemSavedData getMapData(String mapName) {
        return null;
    }

    @Override
    public void setMapData(String mapId, MapItemSavedData data) {
    }

    @Override
    public @Nullable Entity getEntity(int id) {
        return null;
    }

    @Override
    protected LevelEntityGetter<Entity> getEntities() {
        return NO_ENTITIES;
    }

    @Override
    public List<? extends Player> players() {
        return Collections.emptyList();
    }

    @Override
    public String gatherChunkSourceStats() {
        return "";
    }

    @Override
    public void sendBlockUpdated(BlockPos pos, BlockState oldState, BlockState newState, int flags) {
    }

    @Override
    public void destroyBlockProgress(int breakerId, BlockPos pos, int progress) {
    }

    @Override
    public void levelEvent(@Nullable Player player, int type, BlockPos pos, int data) {
    }

    @Override
    public void gameEvent(GameEvent event, Vec3 position, GameEvent.Context context) {
    }

    @Override
    public void gameEvent(@Nullable Entity entity, GameEvent event, BlockPos pos) {
    }

    @Override
    public void playSound(@Nullable Player player, double x, double y, double z, SoundEvent sound, SoundSource source,
            float volume, float pitch) {
    }

    @Override
    public void playSound(@Nullable Player player, Entity entity, SoundEvent sound, SoundSource source, float volume,
            float pitch) {
    }

    @Override
    public void playSeededSound(@Nullable Player player, double x, double y, double z, Holder<SoundEvent> sound,
            SoundSource source, float volume, float pitch, long seed) {
    }

    @Override
    public void playSeededSound(@Nullable Player player, double x, double y, double z, SoundEvent sound,
            SoundSource source, float volume, float pitch, long seed) {
    }

    @Override
    public void playSeededSound(@Nullable Player player, Entity entity, Holder<SoundEvent> sound, SoundSource source,
            float volume, float pitch, long seed) {
    }
}
