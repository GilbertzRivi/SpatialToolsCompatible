package net.oktawia.spatialtoolscmp.client.scene;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.EmptyLevelChunk;
import net.minecraft.world.level.chunk.LightChunk;
import net.minecraft.world.level.lighting.LevelLightEngine;

public class PreviewChunkSource extends ChunkSource {

    private final PreviewLevel level;
    private final Map<Long, EmptyLevelChunk> chunks = new HashMap<>();

    public PreviewChunkSource(PreviewLevel level) {
        this.level = level;
    }

    @Override
    public @Nullable ChunkAccess getChunk(int x, int z, ChunkStatus status, boolean load) {
        return getChunk(x, z);
    }

    @Override
    public LightChunk getChunkForLighting(int x, int z) {
        return getChunk(x, z);
    }

    @Override
    public void tick(BooleanSupplier hasTimeLeft, boolean tickChunks) {
    }

    @Override
    public String gatherStats() {
        return "";
    }

    @Override
    public int getLoadedChunksCount() {
        return 0;
    }

    @Override
    public LevelLightEngine getLightEngine() {
        return this.level.getLightEngine();
    }

    @Override
    public BlockGetter getLevel() {
        return this.level;
    }

    private EmptyLevelChunk getChunk(int x, int z) {
        return this.chunks.computeIfAbsent(ChunkPos.asLong(x, z), key -> new EmptyLevelChunk(
                this.level,
                new ChunkPos(x, z),
                this.level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.PLAINS)));
    }
}
