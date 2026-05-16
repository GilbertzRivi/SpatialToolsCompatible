package net.oktawia.spatialtoolscmp.datagen;

import net.minecraft.data.PackOutput;
import net.minecraftforge.common.data.LanguageProvider;
import net.minecraftforge.registries.ForgeRegistries;
import net.oktawia.spatialtoolscmp.SpatialToolsCMP;
import net.oktawia.spatialtoolscmp.defs.SpatialItemRegistrar;
import net.oktawia.spatialtoolscmp.defs.LangDefs;
import net.oktawia.spatialtoolscmp.util.Utils;

public class SpatialLangProvider extends LanguageProvider {
    public SpatialLangProvider(PackOutput output, String locale) {
        super(output, SpatialToolsCMP.MODID, locale);
    }

    @Override
    protected void addTranslations() {
        for (var item : SpatialItemRegistrar.getItems()){
            this.add(item.getDescriptionId(), Utils.toTitle(ForgeRegistries.ITEMS.getKey(item).getPath()));
        }
        for (var entry : LangDefs.values()) {
            this.add(entry.getTranslationKey(), entry.getEnglishText());
        }
    }
}
