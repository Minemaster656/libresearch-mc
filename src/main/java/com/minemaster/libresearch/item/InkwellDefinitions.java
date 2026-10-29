package com.minemaster.libresearch.item;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.minemaster.libresearch.LibResearch;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Загрузчик определений чернильниц: {@code libresearch/inkwell/<id>.json}. */
public final class InkwellDefinitions extends SimpleJsonResourceReloadListener {
    public static final String DIRECTORY = "libresearch/inkwell";
    public static final InkwellDefinitions INSTANCE = new InkwellDefinitions();

    private static final int FALLBACK_COLOR = 0x1E1B18;

    private volatile Map<ResourceLocation, InkwellDefinition> byItem = Map.of();

    private InkwellDefinitions() {
        super(new Gson(), DIRECTORY);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, InkwellDefinition> parsed = new HashMap<>();
        files.forEach((id, json) -> InkwellDefinition.CODEC.parse(JsonOps.INSTANCE, json)
                .resultOrPartial(error -> LibResearch.LOGGER.error("Не удалось прочитать чернильницу {}: {}", id, error))
                .ifPresent(definition -> parsed.put(definition.item(), definition)));
        byItem = Map.copyOf(parsed);
        LibResearch.LOGGER.info("Загружено определений чернильниц: {}", byItem.size());
    }

    public Optional<InkwellDefinition> get(Item item) {
        ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item);
        return Optional.ofNullable(byItem.get(id));
    }

    public int colorOf(Item item) {
        return get(item).map(InkwellDefinition::color).orElse(FALLBACK_COLOR);
    }

    public int capacityOf(Item item) {
        return get(item).map(InkwellDefinition::capacity).orElse(InkwellItem.DEFAULT_CAPACITY);
    }

    public int chargesPerDrawOf(Item item) {
        return get(item).map(InkwellDefinition::chargesPerDraw).orElse(1);
    }

    public Map<ResourceLocation, InkwellDefinition> all() {
        return byItem;
    }
}
