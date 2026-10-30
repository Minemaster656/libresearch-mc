package com.minemaster.libresearch.data;

import com.google.gson.JsonElement;
import com.minemaster.libresearch.LibResearch;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * Общая точка парсинга датапак-файлов.
 *
 * <p>{@code resultOrPartial} недостаточно: record-кодеки с необязательными полями умеют
 * возвращать «частичный» результат, и битый файл молча попадал бы в реестр со значениями
 * по умолчанию. Поэтому запись с любой ошибкой отбрасывается целиком.</p>
 */
public final class JsonLoad {
    private JsonLoad() {
    }

    public static <T> Optional<T> parse(Codec<T> codec, JsonElement json, ResourceLocation id, String what) {
        var result = codec.parse(JsonOps.INSTANCE, json);
        if (result.error().isPresent()) {
            LibResearch.LOGGER.error("Не удалось прочитать {} {}: {}", what, id, result.error().get().message());
            return Optional.empty();
        }
        return result.result();
    }
}
