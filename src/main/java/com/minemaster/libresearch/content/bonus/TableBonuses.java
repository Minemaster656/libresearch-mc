package com.minemaster.libresearch.content.bonus;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.minemaster.libresearch.LibResearch;
import com.minemaster.libresearch.data.JsonLoad;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Загрузчик бонусных блоков: {@code data/<namespace>/libresearch/table_bonus/<id>.json}. */
public final class TableBonuses extends SimpleJsonResourceReloadListener {
    public static final String DIRECTORY = "libresearch/table_bonus";
    public static final TableBonuses INSTANCE = new TableBonuses();

    private volatile Map<ResourceLocation, BonusBlockDefinition> byId = Map.of();

    private TableBonuses() {
        super(new Gson(), DIRECTORY);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, BonusBlockDefinition> parsed = new HashMap<>();
        files.forEach((id, json) -> JsonLoad.parse(BonusBlockDefinition.CODEC, json, id, "бонусный блок")
                .ifPresent(bonus -> parsed.put(id, bonus)));
        byId = Map.copyOf(parsed);
        LibResearch.LOGGER.info("Загружено бонусных блоков: {}", byId.size());
    }

    public Optional<BonusBlockDefinition> get(ResourceLocation id) {
        return Optional.ofNullable(byId.get(id));
    }

    public boolean contains(ResourceLocation id) {
        return byId.containsKey(id);
    }

    public Map<ResourceLocation, BonusBlockDefinition> all() {
        return byId;
    }
}
