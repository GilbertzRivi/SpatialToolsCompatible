package net.oktawia.spatialtoolscmp.compat.ae2;

import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import appeng.block.AEBaseEntityBlock;
import appeng.blockentity.AEBaseBlockEntity;

import net.oktawia.spatialtoolscmp.SpatialToolsCMP;
import net.oktawia.spatialtoolscmp.defs.SpatialCreativeTabRegistrar;

public final class AE2BlockRegistrar {

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS,
            SpatialToolsCMP.MODID);

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS,
            SpatialToolsCMP.MODID);

    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES = DeferredRegister
            .create(ForgeRegistries.BLOCK_ENTITY_TYPES, SpatialToolsCMP.MODID);

    public static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(ForgeRegistries.MENU_TYPES,
            SpatialToolsCMP.MODID);

    public static final RegistryObject<CraftingBufferBlock> CRAFTING_BUFFER_BLOCK = BLOCKS.register("crafting_buffer",
            CraftingBufferBlock::new);

    public static final RegistryObject<Item> CRAFTING_BUFFER_ITEM = ITEMS.register("crafting_buffer",
            () -> new BlockItem(
                    CRAFTING_BUFFER_BLOCK.get(), new Item.Properties()));

    public static final RegistryObject<BlockEntityType<CraftingBufferBlockEntity>> CRAFTING_BUFFER_BE_TYPE = reg(
            "crafting_buffer", CRAFTING_BUFFER_BLOCK, CraftingBufferBlockEntity::new, CraftingBufferBlockEntity.class);

    public static final RegistryObject<MenuType<CraftingBufferMenu>> CRAFTING_BUFFER_MENU_TYPE = MENU_TYPES.register(
            "crafting_buffer_menu",
            () -> IForgeMenuType.create(CraftingBufferMenu::new));

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private static <T extends AEBaseBlockEntity> RegistryObject<BlockEntityType<T>> reg(
            String id,
            RegistryObject<? extends AEBaseEntityBlock<?>> block,
            BlockEntityType.BlockEntitySupplier<T> factory,
            Class<T> beClass) {
        return (RegistryObject<BlockEntityType<T>>) (RegistryObject<?>) BLOCK_ENTITY_TYPES.register(id, () -> {
            var blk = block.get();
            var type = BlockEntityType.Builder.of(factory, blk).build(null);
            blk.setBlockEntity((Class) beClass, (BlockEntityType) type, null, null);
            return type;
        });
    }

    public static void registerEventBus(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITY_TYPES.register(bus);
        MENU_TYPES.register(bus);
    }

    public static void registerCreativeTabItems() {
        SpatialCreativeTabRegistrar.registerExtraItem(CRAFTING_BUFFER_ITEM);
    }

    private AE2BlockRegistrar() {
    }
}
