package com.minemaster.libresearch.content.card;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Проверка «сырого» JSON карты до Codec-парсинга: неизвестный тип эффекта или
 * требования должен давать понятную ошибку с полем, а не исключение из dispatch.
 */
public final class CardJson {
    private CardJson() {
    }

    public record UnknownType(String path, String value) {
    }

    public static List<UnknownType> unknownTypes(JsonObject card) {
        List<UnknownType> problems = new ArrayList<>();
        checkArray(card, "effects", CardEffect.TYPES, problems);
        checkArray(card, "requires", CardRequirement.TYPES, problems);
        if (card.has("spawn") && card.get("spawn").isJsonObject()) {
            checkArray(card.getAsJsonObject("spawn"), "requirements", CardRequirement.TYPES, problems);
        }
        return problems;
    }

    private static void checkArray(JsonObject parent, String key, java.util.Set<String> known,
                                   List<UnknownType> problems) {
        if (!parent.has(key)) {
            return;
        }
        JsonElement element = parent.get(key);
        if (!element.isJsonArray()) {
            problems.add(new UnknownType(key, "<не массив>"));
            return;
        }
        JsonArray array = element.getAsJsonArray();
        for (int i = 0; i < array.size(); i++) {
            JsonElement entry = array.get(i);
            if (!entry.isJsonObject() || !entry.getAsJsonObject().has("type")) {
                problems.add(new UnknownType(key + "[" + i + "]", "<нет type>"));
                continue;
            }
            JsonElement type = entry.getAsJsonObject().get("type");
            String value = type.isJsonPrimitive() ? type.getAsString() : "<не строка>";
            if (!known.contains(value)) {
                problems.add(new UnknownType(key + "[" + i + "]", value));
            }
        }
    }
}
