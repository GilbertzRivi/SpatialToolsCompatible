package net.oktawia.spatialtoolscmp.client.screens;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;

import me.shedaniel.clothconfig2.api.AbstractConfigListEntry;
import me.shedaniel.clothconfig2.api.ConfigBuilder;
import me.shedaniel.clothconfig2.api.ConfigCategory;
import me.shedaniel.clothconfig2.api.ConfigEntryBuilder;

import net.oktawia.spatialtoolscmp.SpatialConfig;
import net.oktawia.spatialtoolscmp.defs.LangDefs;
import net.oktawia.spatialtoolscmp.defs.SpatialItemRegistrar;

public class SpatialConfigScreen {

    private SpatialConfigScreen() {
    }

    public static Screen create(Screen parent) {
        ConfigBuilder b = ConfigBuilder.create()
                .setParentScreen(parent)
                .setTitle(t(LangDefs.CONFIG_TITLE));

        b.setSavingRunnable(SpatialConfig.COMMON_SPEC::save);

        ConfigEntryBuilder eb = b.entryBuilder();
        SpatialConfig.Common cfg = SpatialConfig.COMMON;

        ConfigCategory root = b.getOrCreateCategory(t(LangDefs.CONFIG_CATEGORY_SETTINGS));

        root.addEntry(eb.startBooleanToggle(t(LangDefs.CONFIG_ENTRY_USE_POWER), cfg.USE_POWER.get())
                .setDefaultValue(true)
                .setTooltip(tooltip(LangDefs.CONFIG_DESC_USE_POWER))
                .setSaveConsumer(cfg.USE_POWER::set)
                .build());

        root.addEntry(integer(eb, LangDefs.CONFIG_ENTRY_PREVIEW_MAX_BLOCKS,
                cfg.PREVIEW_MAX_BLOCKS.get(), 512, 0,
                cfg.PREVIEW_MAX_BLOCKS::set,
                LangDefs.CONFIG_DESC_PREVIEW_MAX_BLOCKS));

        addSection(root, eb, t(LangDefs.CONFIG_SECTION_ENERGY_UPGRADES), entries -> {
            entries.add(strings(eb, LangDefs.CONFIG_ENTRY_ENERGY_UPGRADE_ITEMS,
                    cfg.ENERGY_UPGRADE_ITEMS.get(), List.of("ae2:energy_card"),
                    cfg.ENERGY_UPGRADE_ITEMS::set,
                    LangDefs.CONFIG_DESC_ENERGY_UPGRADE_ITEMS));
        },
                LangDefs.CONFIG_SECTION_ENERGY_UPGRADES_DESC);

        addSection(root, eb, itemName(SpatialItemRegistrar.PORTABLE_SPATIAL_STORAGE.get()), entries -> {
            entries.add(integer(eb, LangDefs.CONFIG_ENTRY_COST,
                    cfg.PORTABLE_SPATIAL_STORAGE_COST.get(), 1, 0,
                    cfg.PORTABLE_SPATIAL_STORAGE_COST::set,
                    LangDefs.CONFIG_DESC_COST));
            entries.add(decimal(eb, LangDefs.CONFIG_ENTRY_ENERGY_COST_MULTIPLIER,
                    cfg.PORTABLE_SPATIAL_STORAGE_ENERGY_COST_MULTIPLIER.get(), 1.0D, 0.0D,
                    cfg.PORTABLE_SPATIAL_STORAGE_ENERGY_COST_MULTIPLIER::set,
                    LangDefs.CONFIG_DESC_ENERGY_COST_MULTIPLIER));
            entries.add(integer(eb, LangDefs.CONFIG_ENTRY_BASE_POWER_CAPACITY,
                    cfg.PORTABLE_SPATIAL_STORAGE_BASE_INTERNAL_POWER_CAPACITY.get(), 200000, 0,
                    cfg.PORTABLE_SPATIAL_STORAGE_BASE_INTERNAL_POWER_CAPACITY::set,
                    LangDefs.CONFIG_DESC_BASE_POWER_CAPACITY));
            entries.add(integer(eb, LangDefs.CONFIG_ENTRY_MAX_STRUCTURE_SIZE,
                    cfg.PORTABLE_SPATIAL_STORAGE_MAX_STRUCTURE_SIZE.get(), -1, -1,
                    cfg.PORTABLE_SPATIAL_STORAGE_MAX_STRUCTURE_SIZE::set,
                    LangDefs.CONFIG_DESC_MAX_STRUCTURE_SIZE,
                    LangDefs.CONFIG_DESC_UNLIMITED_MINUS_ONE));
        });

        addSection(root, eb, itemName(SpatialItemRegistrar.PORTABLE_SPATIAL_CLONER.get()), entries -> {
            entries.add(integer(eb, LangDefs.CONFIG_ENTRY_COST,
                    cfg.PORTABLE_SPATIAL_CLONER_COST.get(), 1, 0,
                    cfg.PORTABLE_SPATIAL_CLONER_COST::set,
                    LangDefs.CONFIG_DESC_COST));
            entries.add(decimal(eb, LangDefs.CONFIG_ENTRY_ENERGY_COST_MULTIPLIER,
                    cfg.PORTABLE_SPATIAL_CLONER_ENERGY_COST_MULTIPLIER.get(), 1.0D, 0.0D,
                    cfg.PORTABLE_SPATIAL_CLONER_ENERGY_COST_MULTIPLIER::set,
                    LangDefs.CONFIG_DESC_ENERGY_COST_MULTIPLIER));
            entries.add(integer(eb, LangDefs.CONFIG_ENTRY_BASE_POWER_CAPACITY,
                    cfg.PORTABLE_SPATIAL_CLONER_BASE_INTERNAL_POWER_CAPACITY.get(), 200000, 0,
                    cfg.PORTABLE_SPATIAL_CLONER_BASE_INTERNAL_POWER_CAPACITY::set,
                    LangDefs.CONFIG_DESC_BASE_POWER_CAPACITY));
            entries.add(integer(eb, LangDefs.CONFIG_ENTRY_MAX_STRUCTURE_SIZE,
                    cfg.PORTABLE_SPATIAL_CLONER_MAX_STRUCTURE_SIZE.get(), -1, -1,
                    cfg.PORTABLE_SPATIAL_CLONER_MAX_STRUCTURE_SIZE::set,
                    LangDefs.CONFIG_DESC_MAX_STRUCTURE_SIZE,
                    LangDefs.CONFIG_DESC_UNLIMITED_MINUS_ONE));
        });

        addSection(root, eb, itemName(SpatialItemRegistrar.PORTABLE_SPATIAL_REPLACER.get()), entries -> {
            entries.add(integer(eb, LangDefs.CONFIG_ENTRY_COST,
                    cfg.PORTABLE_SPATIAL_REPLACER_COST.get(), 1, 0,
                    cfg.PORTABLE_SPATIAL_REPLACER_COST::set,
                    LangDefs.CONFIG_DESC_COST));
            entries.add(decimal(eb, LangDefs.CONFIG_ENTRY_ENERGY_COST_MULTIPLIER,
                    cfg.PORTABLE_SPATIAL_REPLACER_ENERGY_COST_MULTIPLIER.get(), 1.0D, 0.0D,
                    cfg.PORTABLE_SPATIAL_REPLACER_ENERGY_COST_MULTIPLIER::set,
                    LangDefs.CONFIG_DESC_ENERGY_COST_MULTIPLIER));
            entries.add(integer(eb, LangDefs.CONFIG_ENTRY_BASE_POWER_CAPACITY,
                    cfg.PORTABLE_SPATIAL_REPLACER_BASE_INTERNAL_POWER_CAPACITY.get(), 200000, 0,
                    cfg.PORTABLE_SPATIAL_REPLACER_BASE_INTERNAL_POWER_CAPACITY::set,
                    LangDefs.CONFIG_DESC_BASE_POWER_CAPACITY));
            entries.add(integer(eb, LangDefs.CONFIG_ENTRY_MAX_BLOCKS,
                    cfg.PORTABLE_SPATIAL_REPLACER_MAX_BLOCKS.get(), 1024, 0,
                    cfg.PORTABLE_SPATIAL_REPLACER_MAX_BLOCKS::set,
                    LangDefs.CONFIG_DESC_REPLACER_MAX_BLOCKS));
            entries.add(strings(eb, LangDefs.CONFIG_ENTRY_BLACKLIST,
                    cfg.PORTABLE_SPATIAL_REPLACER_BLACKLIST.get(),
                    List.of("#forge:ores", "#forge:raw_materials"),
                    cfg.PORTABLE_SPATIAL_REPLACER_BLACKLIST::set,
                    LangDefs.CONFIG_DESC_BLACKLIST_SYNTAX,
                    LangDefs.CONFIG_DESC_BLACKLIST_OPERATORS,
                    LangDefs.CONFIG_DESC_BLACKLIST_INDESTRUCTIBLE));
        });

        addSection(root, eb, itemName(SpatialItemRegistrar.PORTABLE_SPATIAL_PIPER.get()), entries -> {
            entries.add(integer(eb, LangDefs.CONFIG_ENTRY_COST,
                    cfg.PORTABLE_SPATIAL_PIPER_COST.get(), 1, 0,
                    cfg.PORTABLE_SPATIAL_PIPER_COST::set,
                    LangDefs.CONFIG_DESC_COST));
            entries.add(decimal(eb, LangDefs.CONFIG_ENTRY_ENERGY_COST_MULTIPLIER,
                    cfg.PORTABLE_SPATIAL_PIPER_ENERGY_COST_MULTIPLIER.get(), 1.0D, 0.0D,
                    cfg.PORTABLE_SPATIAL_PIPER_ENERGY_COST_MULTIPLIER::set,
                    LangDefs.CONFIG_DESC_ENERGY_COST_MULTIPLIER));
            entries.add(integer(eb, LangDefs.CONFIG_ENTRY_BASE_POWER_CAPACITY,
                    cfg.PORTABLE_SPATIAL_PIPER_BASE_INTERNAL_POWER_CAPACITY.get(), 200000, 0,
                    cfg.PORTABLE_SPATIAL_PIPER_BASE_INTERNAL_POWER_CAPACITY::set,
                    LangDefs.CONFIG_DESC_BASE_POWER_CAPACITY));
            entries.add(integer(eb, LangDefs.CONFIG_ENTRY_MAX_BLOCKS,
                    cfg.PORTABLE_SPATIAL_PIPER_MAX_BLOCKS.get(), 1024, 0,
                    cfg.PORTABLE_SPATIAL_PIPER_MAX_BLOCKS::set,
                    LangDefs.CONFIG_DESC_PIPER_MAX_BLOCKS));
            entries.add(integer(eb, LangDefs.CONFIG_ENTRY_SELECTION_RANGE,
                    cfg.PORTABLE_SPATIAL_PIPER_SELECTION_RANGE.get(), 32, 1,
                    cfg.PORTABLE_SPATIAL_PIPER_SELECTION_RANGE::set,
                    LangDefs.CONFIG_DESC_SELECTION_RANGE));
        });

        addSection(root, eb, itemName(SpatialItemRegistrar.PORTABLE_SPATIAL_TOOL.get()), entries -> {
            entries.add(integer(eb, LangDefs.CONFIG_ENTRY_BASE_POWER_CAPACITY,
                    cfg.PORTABLE_SPATIAL_TOOL_BASE_INTERNAL_POWER_CAPACITY.get(), 200000, 0,
                    cfg.PORTABLE_SPATIAL_TOOL_BASE_INTERNAL_POWER_CAPACITY::set,
                    LangDefs.CONFIG_DESC_BASE_POWER_CAPACITY,
                    LangDefs.CONFIG_DESC_MULTITOOL_POWER_CAPACITY_CAP));
        });

        return b.build();
    }

    private static void addSection(
            ConfigCategory root,
            ConfigEntryBuilder eb,
            Component name,
            Consumer<List<AbstractConfigListEntry>> entriesBuilder,
            LangDefs... tooltip) {
        List<AbstractConfigListEntry> entries = new ArrayList<>();
        entriesBuilder.accept(entries);

        root.addEntry(eb.startSubCategory(name, entries)
                .setTooltip(tooltip(tooltip))
                .setExpanded(false)
                .build());
    }

    private static AbstractConfigListEntry integer(
            ConfigEntryBuilder eb,
            LangDefs name,
            int value,
            int defaultValue,
            int min,
            Consumer<Integer> saveConsumer,
            LangDefs... tooltip) {
        return eb.startIntField(t(name), value)
                .setDefaultValue(defaultValue)
                .setMin(min)
                .setTooltip(tooltip(tooltip))
                .setSaveConsumer(saveConsumer)
                .build();
    }

    private static AbstractConfigListEntry decimal(
            ConfigEntryBuilder eb,
            LangDefs name,
            double value,
            double defaultValue,
            double min,
            Consumer<Double> saveConsumer,
            LangDefs... tooltip) {
        return eb.startDoubleField(t(name), value)
                .setDefaultValue(defaultValue)
                .setMin(min)
                .setTooltip(tooltip(tooltip))
                .setSaveConsumer(saveConsumer)
                .build();
    }

    private static AbstractConfigListEntry strings(
            ConfigEntryBuilder eb,
            LangDefs name,
            List<? extends String> value,
            List<String> defaultValue,
            Consumer<List<String>> saveConsumer,
            LangDefs... tooltip) {
        return eb.startStrList(t(name), new ArrayList<>(value))
                .setDefaultValue(defaultValue)
                .setTooltip(tooltip(tooltip))
                .setSaveConsumer(saveConsumer)
                .build();
    }

    private static Component itemName(Item item) {
        return Component.translatable(item.getDescriptionId());
    }

    private static Component t(LangDefs def) {
        return Component.translatable(def.getTranslationKey());
    }

    private static Component[] tooltip(LangDefs... defs) {
        List<Component> out = new ArrayList<>();

        for (LangDefs def : defs) {
            addWrappedTooltipLine(out, t(def));
        }

        return out.toArray(Component[]::new);
    }

    private static void addWrappedTooltipLine(List<Component> out, Component component) {
        String text = component.getString();

        if (text == null || text.isBlank()) {
            out.add(Component.empty());
            return;
        }

        int maxWidth = getTooltipMaxWidth();
        var font = Minecraft.getInstance().font;

        StringBuilder line = new StringBuilder();

        for (String word : text.split(" ")) {
            if (word.isBlank()) {
                continue;
            }

            String candidate = line.isEmpty() ? word : line + " " + word;

            if (font.width(candidate) <= maxWidth || line.isEmpty()) {
                line.setLength(0);
                line.append(candidate);
                continue;
            }

            out.add(Component.literal(line.toString()));
            line.setLength(0);
            line.append(word);
        }

        if (!line.isEmpty()) {
            out.add(Component.literal(line.toString()));
        }
    }

    private static int getTooltipMaxWidth() {
        Minecraft minecraft = Minecraft.getInstance();

        if (minecraft.screen == null) {
            return 240;
        }

        return Math.max(160, Math.min(240, minecraft.screen.width - 100));
    }
}
