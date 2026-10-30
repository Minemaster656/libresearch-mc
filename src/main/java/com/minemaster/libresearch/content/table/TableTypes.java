package com.minemaster.libresearch.content.table;

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

/**
 * Загрузчик типов столов из датапака. Работает и на клиенте (в одиночной игре),
 * и на сервере; в мультиплеере клиент получает только те данные, что нужны GUI.
 */
public final class TableTypes extends SimpleJsonResourceReloadListener {
    public static final String DIRECTORY = "libresearch/table_type";
    public static final TableTypes INSTANCE = new TableTypes();

    private volatile Map<ResourceLocation, TableType> byId = Map.of();

    private TableTypes() {
        super(new Gson(), DIRECTORY);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, TableType> parsed = new HashMap<>();
        files.forEach((id, json) -> JsonLoad.parse(TableType.CODEC, json, id, "тип стола")
                .ifPresent(type -> parsed.put(id, type)));
        byId = Map.copyOf(parsed);
        LibResearch.LOGGER.info("Загружено типов столов: {}", byId.size());
    }

    public Optional<TableType> get(ResourceLocation id) {
        return Optional.ofNullable(byId.get(id));
    }

    public TableType getOrDefault(ResourceLocation id) {
        return byId.getOrDefault(id, TableType.EMPTY);
    }

    public Map<ResourceLocation, TableType> all() {
        return byId;
    }
}
