package com.minemaster.libresearch.theory;

import com.minemaster.libresearch.core.KnowledgeMath;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Состояние теории конкретного стола. Переживает закрытие интерфейса и перезаход,
 * пропадает при сломе блока (вместе с BlockEntity).
 *
 * <p>Награды ({@link #rewards}) остаются и после завершения теории: это снимок
 * начисленных санти-очков, который стол «помнит», чтобы игрок мог забрать целые
 * очки. Остаток санти-очков остаётся в запасе игрока и на столе.</p>
 */
public final class TheoryState {
    private boolean active;
    private int inspiration;
    private int startInspiration;
    private int bonusDraws;
    private int fullValueSlots;

    private final Map<String, Integer> progress = new LinkedHashMap<>();
    private final Set<String> blocked = new LinkedHashSet<>();
    private final List<DrawnCard> hand = new ArrayList<>();
    private final Map<String, Long> rewards = new LinkedHashMap<>();
    private final Set<String> selectedBonuses = new LinkedHashSet<>();
    private final List<net.minecraft.resources.ResourceLocation> bonusDeck = new ArrayList<>();

    public boolean isActive() {
        return active;
    }

    public void start(int startInspiration, int fullValueSlots) {
        this.active = true;
        this.startInspiration = startInspiration;
        this.inspiration = startInspiration;
        this.bonusDraws = 0;
        this.fullValueSlots = fullValueSlots;
        this.progress.clear();
        this.blocked.clear();
        this.hand.clear();
        this.bonusDeck.clear();
        this.selectedBonuses.clear();
    }

    /** Завершение или добровольный сброс: теория стирается, награды остаются. */
    public void clearTheory() {
        active = false;
        inspiration = 0;
        startInspiration = 0;
        bonusDraws = 0;
        fullValueSlots = 0;
        progress.clear();
        blocked.clear();
        hand.clear();
        bonusDeck.clear();
        selectedBonuses.clear();
    }

    public int inspiration() {
        return inspiration;
    }

    public void setInspiration(int value) {
        inspiration = Math.max(0, Math.min(startInspiration, value));
    }

    public int startInspiration() {
        return startInspiration;
    }

    public int bonusDraws() {
        return bonusDraws;
    }

    public void addBonusDraws(int amount) {
        bonusDraws = Math.max(0, bonusDraws + amount);
    }

    public int fullValueSlots() {
        return fullValueSlots;
    }

    public void addFullValueSlots(int amount) {
        fullValueSlots = Math.max(0, fullValueSlots + amount);
    }

    public int progress(String discipline) {
        return progress.getOrDefault(discipline, 0);
    }

    public void setProgress(String discipline, int percent) {
        if (percent <= 0) {
            progress.remove(discipline);
        } else {
            progress.put(discipline, percent);
        }
    }

    public void addProgress(String discipline, int amount) {
        setProgress(discipline, progress(discipline) + amount);
    }

    public Map<String, Integer> progressMap() {
        return Map.copyOf(progress);
    }

    public boolean isBlocked(String discipline) {
        return blocked.contains(discipline);
    }

    public void block(String discipline) {
        if (discipline != null && !discipline.isBlank()) {
            blocked.add(discipline);
        }
    }

    public Set<String> blockedSet() {
        return Set.copyOf(blocked);
    }

    public List<DrawnCard> hand() {
        return List.copyOf(hand);
    }

    public void setHand(List<DrawnCard> cards) {
        hand.clear();
        hand.addAll(cards);
    }

    public Map<String, Long> rewards() {
        return Map.copyOf(rewards);
    }

    public long reward(String discipline) {
        return rewards.getOrDefault(discipline, 0L);
    }

    public void addReward(String discipline, long centi) {
        if (centi > 0) {
            rewards.merge(discipline, centi, Long::sum);
        }
    }

    public void consumeReward(String discipline, long centi) {
        long left = reward(discipline) - centi;
        if (left <= 0) {
            rewards.remove(discipline);
        } else {
            rewards.put(discipline, left);
        }
    }

    /** Есть целое незабранное очко — новая теория не начинается. */
    public boolean hasPendingRewards() {
        return rewards.values().stream().anyMatch(KnowledgeMath::hasPendingReward);
    }

    // --- бонусные блоки (выбираются до старта) ---

    public Set<String> selectedBonuses() {
        return Set.copyOf(selectedBonuses);
    }

    /** @return {@code true}, если бонус теперь выбран */
    public boolean toggleBonus(String bonusId) {
        if (selectedBonuses.remove(bonusId)) {
            return false;
        }
        selectedBonuses.add(bonusId);
        return true;
    }

    public void clearSelectedBonuses() {
        selectedBonuses.clear();
    }

    /** Конечная колода бонусных карт: записи удаляются по мере выпадения. */
    public List<net.minecraft.resources.ResourceLocation> bonusDeck() {
        return List.copyOf(bonusDeck);
    }

    public void setBonusDeck(List<net.minecraft.resources.ResourceLocation> cards) {
        bonusDeck.clear();
        bonusDeck.addAll(cards);
    }

    public void removeBonusCard(int index) {
        if (index >= 0 && index < bonusDeck.size()) {
            bonusDeck.remove(index);
        }
    }

    public void save(CompoundTag tag) {
        tag.putBoolean("active", active);
        tag.putInt("inspiration", inspiration);
        tag.putInt("start_inspiration", startInspiration);
        tag.putInt("bonus_draws", bonusDraws);
        tag.putInt("full_value_slots", fullValueSlots);

        CompoundTag progressTag = new CompoundTag();
        progress.forEach(progressTag::putInt);
        tag.put("progress", progressTag);

        ListTag blockedTag = new ListTag();
        blocked.forEach(discipline -> blockedTag.add(StringTag.valueOf(discipline)));
        tag.put("blocked", blockedTag);

        ListTag handTag = new ListTag();
        hand.forEach(card -> handTag.add(card.save()));
        tag.put("hand", handTag);

        CompoundTag rewardsTag = new CompoundTag();
        rewards.forEach(rewardsTag::putLong);
        tag.put("rewards", rewardsTag);

        ListTag selectedTag = new ListTag();
        selectedBonuses.forEach(bonus -> selectedTag.add(StringTag.valueOf(bonus)));
        tag.put("selected_bonuses", selectedTag);

        ListTag deckTag = new ListTag();
        bonusDeck.forEach(card -> deckTag.add(StringTag.valueOf(card.toString())));
        tag.put("bonus_deck", deckTag);
    }

    public void load(CompoundTag tag) {
        active = tag.getBoolean("active");
        inspiration = tag.getInt("inspiration");
        startInspiration = tag.getInt("start_inspiration");
        bonusDraws = tag.getInt("bonus_draws");
        fullValueSlots = tag.getInt("full_value_slots");

        progress.clear();
        CompoundTag progressTag = tag.getCompound("progress");
        progressTag.getAllKeys().forEach(discipline -> progress.put(discipline, progressTag.getInt(discipline)));

        blocked.clear();
        ListTag blockedTag = tag.getList("blocked", Tag.TAG_STRING);
        for (int i = 0; i < blockedTag.size(); i++) {
            blocked.add(blockedTag.getString(i));
        }

        hand.clear();
        ListTag handTag = tag.getList("hand", Tag.TAG_COMPOUND);
        for (int i = 0; i < handTag.size(); i++) {
            DrawnCard.load(handTag.getCompound(i)).ifPresent(hand::add);
        }

        rewards.clear();
        CompoundTag rewardsTag = tag.getCompound("rewards");
        rewardsTag.getAllKeys().forEach(discipline -> rewards.put(discipline, rewardsTag.getLong(discipline)));

        selectedBonuses.clear();
        ListTag selectedTag = tag.getList("selected_bonuses", Tag.TAG_STRING);
        for (int i = 0; i < selectedTag.size(); i++) {
            selectedBonuses.add(selectedTag.getString(i));
        }

        bonusDeck.clear();
        ListTag deckTag = tag.getList("bonus_deck", Tag.TAG_STRING);
        for (int i = 0; i < deckTag.size(); i++) {
            net.minecraft.resources.ResourceLocation id =
                    net.minecraft.resources.ResourceLocation.tryParse(deckTag.getString(i));
            if (id != null) {
                bonusDeck.add(id);
            }
        }
    }
}
