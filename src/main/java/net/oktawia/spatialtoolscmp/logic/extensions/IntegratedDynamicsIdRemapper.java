package net.oktawia.spatialtoolscmp.logic.extensions;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.oktawia.spatialtoolscmp.util.StructureToolKeys;
import net.oktawia.spatialtoolscmp.util.TemplateUtil;
import org.cyclops.integrateddynamics.IntegratedDynamics;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class IntegratedDynamicsIdRemapper {

    private static final String COUNTER_PART = "part";
    private static final String COUNTER_VARIABLE = "variable";
    private static final String COUNTER_CONNECTOR_GROUP = "omnidir-connectors";

    private static final String KEY_CONNECTOR_GROUP = "omnidir-group-key";

    private static final String FACADE_TYPE_ASPECT = "integrateddynamics:aspect";
    private static final String FACADE_TYPE_OPERATOR = "integrateddynamics:operator";

    private static final String KEY_STACK_ID = "id";
    private static final String KEY_STACK_TAG = "tag";
    private static final String KEY_FACADE_TYPE = "_type";
    private static final String KEY_FACADE_ID = "_id";
    private static final String KEY_PART_ID = "partId";
    private static final String KEY_VARIABLE_IDS = "variableIds";

    private final Map<Integer, Integer> partIds = new HashMap<>();
    private final Map<Integer, Integer> variableIds = new HashMap<>();
    private final Map<Integer, Integer> connectorGroupIds = new HashMap<>();
    private final Map<Integer, Integer> connectorGroupSizes = new HashMap<>();

    void prepare(List<TemplateUtil.BlockInfo> blocks) {
        partIds.clear();
        variableIds.clear();
        connectorGroupIds.clear();
        connectorGroupSizes.clear();

        for (TemplateUtil.BlockInfo block : blocks) {
            CompoundTag beTag = block.blockEntityTag();

            if (beTag == null) {
                continue;
            }

            collectPartIds(beTag);
            collectVariableIds(beTag);
        }

        connectorGroupSizes.forEach((groupId, connectors) -> {
            if (connectors > 1) {
                connectorGroupIds.put(groupId, 0);
            }
        });

        connectorGroupSizes.clear();

        try {
            partIds.replaceAll((sourceId, ignored) -> nextId(COUNTER_PART));
            variableIds.replaceAll((sourceId, ignored) -> nextId(COUNTER_VARIABLE));
            connectorGroupIds.replaceAll((sourceId, ignored) -> nextId(COUNTER_CONNECTOR_GROUP));
        } catch (Throwable ignored) {
            partIds.clear();
            variableIds.clear();
            connectorGroupIds.clear();
        }
    }

    void apply(CompoundTag beTag) {
        if (partIds.isEmpty() && variableIds.isEmpty() && connectorGroupIds.isEmpty()) {
            return;
        }

        for (Tag part : partsList(beTag)) {
            if (part instanceof CompoundTag partTag) {
                remapInt(partTag, KEY_STACK_ID, partIds);
                remapInt(partTag, KEY_CONNECTOR_GROUP, connectorGroupIds);
            }
        }

        remapVariableFacades(beTag);
    }

    private void collectPartIds(CompoundTag beTag) {
        for (Tag part : partsList(beTag)) {
            if (!(part instanceof CompoundTag partTag)) {
                continue;
            }

            if (partTag.contains(KEY_STACK_ID, Tag.TAG_INT)) {
                partIds.put(partTag.getInt(KEY_STACK_ID), 0);
            }

            if (partTag.contains(KEY_CONNECTOR_GROUP, Tag.TAG_INT) && partTag.getInt(KEY_CONNECTOR_GROUP) >= 0) {
                connectorGroupSizes.merge(partTag.getInt(KEY_CONNECTOR_GROUP), 1, Integer::sum);
            }
        }
    }

    private void collectVariableIds(@Nullable Tag tag) {
        CompoundTag facade = readVariableFacade(tag);

        if (facade != null) {
            if (facade.contains(KEY_FACADE_ID, Tag.TAG_INT)) {
                variableIds.put(facade.getInt(KEY_FACADE_ID), 0);
            }

            return;
        }

        if (tag instanceof CompoundTag compound) {
            for (String key : compound.getAllKeys()) {
                collectVariableIds(compound.get(key));
            }

            return;
        }

        if (tag instanceof ListTag list) {
            for (Tag child : list) {
                collectVariableIds(child);
            }
        }
    }

    private void remapVariableFacades(@Nullable Tag tag) {
        CompoundTag facade = readVariableFacade(tag);

        if (facade != null) {
            remapVariableFacade(facade);
            return;
        }

        if (tag instanceof CompoundTag compound) {
            for (String key : compound.getAllKeys()) {
                remapVariableFacades(compound.get(key));
            }

            return;
        }

        if (tag instanceof ListTag list) {
            for (Tag child : list) {
                remapVariableFacades(child);
            }
        }
    }

    private void remapVariableFacade(CompoundTag facade) {
        remapInt(facade, KEY_FACADE_ID, variableIds);

        String facadeType = facade.getString(KEY_FACADE_TYPE);

        if (FACADE_TYPE_ASPECT.equals(facadeType)) {
            remapInt(facade, KEY_PART_ID, partIds);
            return;
        }

        if (!FACADE_TYPE_OPERATOR.equals(facadeType) || !facade.contains(KEY_VARIABLE_IDS, Tag.TAG_INT_ARRAY)) {
            return;
        }

        int[] referenced = facade.getIntArray(KEY_VARIABLE_IDS);
        int[] remapped = new int[referenced.length];

        for (int i = 0; i < referenced.length; i++) {
            remapped[i] = variableIds.getOrDefault(referenced[i], referenced[i]);
        }

        facade.putIntArray(KEY_VARIABLE_IDS, remapped);
    }

    private static void remapInt(CompoundTag tag, String key, Map<Integer, Integer> mapping) {
        if (!tag.contains(key, Tag.TAG_INT)) {
            return;
        }

        Integer remapped = mapping.get(tag.getInt(key));

        if (remapped != null) {
            tag.putInt(key, remapped);
        }
    }

    @Nullable
    private static CompoundTag readVariableFacade(@Nullable Tag tag) {
        if (!(tag instanceof CompoundTag compound)) {
            return null;
        }

        if (!compound.contains(KEY_STACK_ID, Tag.TAG_STRING) || !compound.contains(KEY_STACK_TAG, Tag.TAG_COMPOUND)) {
            return null;
        }

        CompoundTag stackTag = compound.getCompound(KEY_STACK_TAG);

        return stackTag.contains(KEY_FACADE_TYPE, Tag.TAG_STRING) ? stackTag : null;
    }

    private static ListTag partsList(CompoundTag beTag) {
        if (!beTag.contains(StructureToolKeys.INTDYN_KEY_PART_CONTAINER, Tag.TAG_COMPOUND)) {
            return new ListTag();
        }

        return beTag
                .getCompound(StructureToolKeys.INTDYN_KEY_PART_CONTAINER)
                .getList(StructureToolKeys.INTDYN_KEY_PARTS, Tag.TAG_COMPOUND);
    }

    private static int nextId(String counter) {
        return IntegratedDynamics.globalCounters.getNext(counter);
    }
}
