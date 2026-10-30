package com.minemaster.libresearch.theory;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * «Инстанс» выпавшей карты: все случайные значения зафиксированы в момент добора,
 * поэтому подсказка не меняется между кадрами и сервер с клиентом видят одно и то же.
 *
 * <p>{@code rolls} и {@code targets} параллельны списку эффектов карты: для каждого
 * эффекта хранится его случайное число и выбранная дисциплина (пустая строка, если
 * эффекту цель не нужна).</p>
 */
public record DrawnCard(ResourceLocation card, @Nullable String discipline, int cost,
                        List<Integer> rolls, List<String> targets) {
    public int roll(int effect) {
        return effect >= 0 && effect < rolls.size() ? rolls.get(effect) : 0;
    }

    public String target(int effect) {
        return effect >= 0 && effect < targets.size() ? targets.get(effect) : "";
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("card", card.toString());
        if (discipline != null) {
            tag.putString("discipline", discipline);
        }
        tag.putInt("cost", cost);
        tag.putIntArray("rolls", rolls.stream().mapToInt(Integer::intValue).toArray());
        ListTag targetList = new ListTag();
        targets.forEach(target -> targetList.add(StringTag.valueOf(target)));
        tag.put("targets", targetList);
        return tag;
    }

    /** Возвращает пустой Optional, если id карты в сохранении битый. */
    public static java.util.Optional<DrawnCard> load(CompoundTag tag) {
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("card"));
        if (id == null) {
            return java.util.Optional.empty();
        }

        List<Integer> rolls = new ArrayList<>();
        for (int value : tag.getIntArray("rolls")) {
            rolls.add(value);
        }
        List<String> targets = new ArrayList<>();
        ListTag targetList = tag.getList("targets", Tag.TAG_STRING);
        for (int i = 0; i < targetList.size(); i++) {
            targets.add(targetList.getString(i));
        }
        String discipline = tag.contains("discipline") ? tag.getString("discipline") : null;
        return java.util.Optional.of(new DrawnCard(id, discipline, tag.getInt("cost"), rolls, targets));
    }
}
