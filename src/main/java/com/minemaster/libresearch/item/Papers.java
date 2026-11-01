package com.minemaster.libresearch.item;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.minemaster.libresearch.LibResearch;
import com.minemaster.libresearch.content.table.TableType;
import com.minemaster.libresearch.content.table.TableTypes;
import com.minemaster.libresearch.data.JsonLoad;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Загрузчик определений бумаги: {@code libresearch/paper/<id>.json}.
 *
 * <p>Определение выбирается полем {@code paper} в table_type. Если датапак его не задал
 * (или клиент ещё не получил серверные данные — см. B11), берётся {@link #FALLBACK}:
 * обычная бумага, 1 штука за добор.</p>
 */
public final class Papers extends SimpleJsonResourceReloadListener {
    public static final String DIRECTORY = "libresearch/paper";
    public static final Papers INSTANCE = new Papers();

    /** Id определения по умолчанию — на него ссылается {@link TableType#DEFAULT_PAPER}. */
    public static final ResourceLocation DEFAULT_ID =
            ResourceLocation.fromNamespaceAndPath("libresearch", "paper");
    private static final PaperDefinition FALLBACK =
            new PaperDefinition(ResourceLocation.withDefaultNamespace("paper"), 1);

    private volatile Map<ResourceLocation, PaperDefinition> byId = Map.of();

    private Papers() {
        super(new Gson(), DIRECTORY);
    }

    @Override
    protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, PaperDefinition> parsed = new HashMap<>();
        files.forEach((id, json) -> JsonLoad.parse(PaperDefinition.CODEC, json, id, "бумагу")
                .ifPresent(definition -> parsed.put(id, definition)));
        byId = Map.copyOf(parsed);
        LibResearch.LOGGER.info("Загружено определений бумаги: {}", byId.size());
    }

    public Optional<PaperDefinition> get(ResourceLocation id) {
        return Optional.ofNullable(byId.get(id));
    }

    public PaperDefinition getOrDefault(ResourceLocation id) {
        return byId.getOrDefault(id, FALLBACK);
    }

    /** Определение бумаги, на которое ссылается тип стола. */
    public PaperDefinition forType(ResourceLocation tableTypeId) {
        TableType type = TableTypes.INSTANCE.get(tableTypeId).orElse(null);
        return getOrDefault(type != null ? type.paper() : DEFAULT_ID);
    }

    public Item itemForType(ResourceLocation tableTypeId) {
        return item(forType(tableTypeId));
    }

    /** Предмет из определения; отсутствующий в реестре предмет заменяется на обычную бумагу. */
    public static Item item(PaperDefinition definition) {
        Item item = BuiltInRegistries.ITEM.get(definition.item());
        return item == Items.AIR ? Items.PAPER : item;
    }

    public Map<ResourceLocation, PaperDefinition> all() {
        return byId;
    }
}
