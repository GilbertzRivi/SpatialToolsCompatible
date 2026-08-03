package net.oktawia.spatialtoolscmp.datagen;

import net.minecraft.data.DataGenerator;
import net.minecraft.data.PackOutput;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.data.event.GatherDataEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import net.oktawia.spatialtoolscmp.SpatialToolsCMP;

@Mod.EventBusSubscriber(modid = SpatialToolsCMP.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class SpatialDataGenerators {
    @SubscribeEvent
    public static void gatherData(GatherDataEvent event) {
        DataGenerator generator = event.getGenerator();
        PackOutput packOutput = generator.getPackOutput();
        ExistingFileHelper existingFileHelper = event.getExistingFileHelper();
        generator.addProvider(event.includeServer(), new SpatialRecipeProvider(packOutput));
        generator.addProvider(event.includeClient(), new SpatialItemModelProvider(packOutput, existingFileHelper));
        generator.addProvider(event.includeClient(), new SpatialLangProvider(packOutput, "en_us"));
    }
}
