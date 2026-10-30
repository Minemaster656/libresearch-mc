package com.minemaster.libresearch.content.card;

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

/** Загрузчик карт: {@code data/<namespace>/libresearch/card/<id>.json}. */
public final class Cards extends SimpleJsonResourceReloadListener {
    public static final String DIRECTORY = "libresearch/card";
    public static final Cards INSTANCE = new Cards();

    private volatile Map<ResourceLocation, Card> byId = Map.of();

    private Cards() {
        super(new Gson(), DIRECTORY);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, Card> parsed = new HashMap<>();
        files.forEach((id, json) -> {
            if (json.isJsonObject()) {
                var unknown = CardJson.unknownTypes(json.getAsJsonObject());
                if (!unknown.isEmpty()) {
                    unknown.forEach(problem -> LibResearch.LOGGER.error(
                            "Карта {}: неизвестный тип в {}: {}", id, problem.path(), problem.value()));
                    return;
                }
            }
            JsonLoad.parse(Card.CODEC, json, id, "карту")
                    .ifPresent(card -> parsed.put(id, card));
        });
        byId = Map.copyOf(parsed);
        LibResearch.LOGGER.info("Загружено карт: {}", byId.size());
    }

    public Optional<Card> get(ResourceLocation id) {
        return Optional.ofNullable(byId.get(id));
    }

    public boolean contains(ResourceLocation id) {
        return byId.containsKey(id);
    }

    public Map<ResourceLocation, Card> all() {
        return byId;
    }
}
