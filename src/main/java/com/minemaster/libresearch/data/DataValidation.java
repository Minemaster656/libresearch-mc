package com.minemaster.libresearch.data;

import com.minemaster.libresearch.content.bonus.BonusBlockDefinition;
import com.minemaster.libresearch.content.bonus.TableBonuses;
import com.minemaster.libresearch.content.card.Card;
import com.minemaster.libresearch.content.card.CardEffect;
import com.minemaster.libresearch.content.card.CardRequirement;
import com.minemaster.libresearch.content.card.Cards;
import com.minemaster.libresearch.content.card.IntRange;
import com.minemaster.libresearch.content.table.TableType;
import com.minemaster.libresearch.content.table.TableTypes;
import com.minemaster.libresearch.item.InkwellDefinitions;
import com.minemaster.libresearch.item.InkwellItem;
import com.minemaster.libresearch.item.Papers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Проверка загруженного датапака: ссылки, диапазоны, дубликаты.
 * Вызывается командой {@code /libresearch validate}; ошибки парсинга ловятся
 * ещё в загрузчиках.
 */
public final class DataValidation {
    private DataValidation() {
    }

    public record Problem(String source, String message) {
        @Override
        public String toString() {
            return source + ": " + message;
        }
    }

    public static List<Problem> validate() {
        List<Problem> problems = new ArrayList<>();
        Set<String> disciplines = allDisciplines();

        validateTables(problems, disciplines);
        validateBonuses(problems);
        validateCards(problems, disciplines);
        validateInkwells(problems);
        validatePapers(problems);

        return problems;
    }

    private static Set<String> allDisciplines() {
        Set<String> disciplines = new LinkedHashSet<>();
        TableTypes.INSTANCE.all().values().forEach(type -> disciplines.addAll(type.disciplineIds()));
        return disciplines;
    }

    private static void validateTables(List<Problem> problems, Set<String> disciplines) {
        TableTypes.INSTANCE.all().forEach((id, type) -> {
            String source = "table_type " + id;
            Set<String> seen = new HashSet<>();
            for (TableType.Discipline discipline : type.disciplines()) {
                if (!seen.add(discipline.id())) {
                    problems.add(new Problem(source, "дубликат дисциплины: " + discipline.id()));
                }
                if (discipline.id().isBlank()) {
                    problems.add(new Problem(source, "пустой id дисциплины"));
                }
                requireItem(problems, source, "icon дисциплины " + discipline.id(), discipline.icon());
                requireItem(problems, source, "payout дисциплины " + discipline.id(), discipline.payout());
                if (discipline.countPerPoint() <= 0) {
                    problems.add(new Problem(source, "count_per_point ≤ 0 у " + discipline.id()));
                }
            }
            if (type.disciplines().isEmpty()) {
                problems.add(new Problem(source, "нет ни одной дисциплины"));
            }
            Set<ResourceLocation> bonusSeen = new HashSet<>();
            for (ResourceLocation bonus : type.bonusBlocks()) {
                if (!bonusSeen.add(bonus)) {
                    problems.add(new Problem(source, "дубликат бонусного блока: " + bonus));
                }
                if (!TableBonuses.INSTANCE.contains(bonus)) {
                    problems.add(new Problem(source, "неизвестный бонусный блок: " + bonus));
                }
            }
            if (Papers.INSTANCE.get(type.paper()).isEmpty()) {
                problems.add(new Problem(source, "неизвестное определение бумаги: " + type.paper()));
            }
        });
    }

    private static void validateBonuses(List<Problem> problems) {
        TableBonuses.INSTANCE.all().forEach((id, bonus) -> {
            String source = "table_bonus " + id;
            BonusBlockDefinition.Match match = bonus.match();
            if (match.blocks().isEmpty() && match.tags().isEmpty() && match.entities().isEmpty()) {
                problems.add(new Problem(source, "match пуст: блок никогда не найдётся"));
            }
            match.blocks().forEach(block -> {
                if (!BuiltInRegistries.BLOCK.containsKey(block)) {
                    problems.add(new Problem(source, "неизвестный блок: " + block));
                }
            });
            match.entities().forEach(entity -> {
                if (!BuiltInRegistries.ENTITY_TYPE.containsKey(entity)) {
                    problems.add(new Problem(source, "неизвестная сущность: " + entity));
                }
            });
            if (bonus.cost() < 0) {
                problems.add(new Problem(source, "cost < 0"));
            }
            bonus.cards().forEach(entry -> {
                if (!Cards.INSTANCE.contains(entry.card())) {
                    problems.add(new Problem(source, "неизвестная карта: " + entry.card()));
                }
                if (entry.count() <= 0) {
                    problems.add(new Problem(source, "count ≤ 0 у карты " + entry.card()));
                }
            });
        });
    }

    private static void validateCards(List<Problem> problems, Set<String> disciplines) {
        Cards.INSTANCE.all().forEach((id, card) -> {
            String source = "card " + id;
            if (card.cost() < -5 || card.cost() > 10) {
                problems.add(new Problem(source, "подозрительная цена: " + card.cost()));
            }
            if (card.weight() < 0) {
                problems.add(new Problem(source, "weight < 0"));
            }
            card.discipline().ifPresent(discipline -> {
                if (!discipline.equals("self") && !discipline.equals("random")
                        && !disciplines.contains(discipline)) {
                    problems.add(new Problem(source, "неизвестная дисциплина: " + discipline));
                }
            });
            if (card.effects().isEmpty() && card.requires().isEmpty()) {
                problems.add(new Problem(source, "карта ничего не делает"));
            }
            for (CardRequirement requirement : card.requires()) {
                validateRequirement(problems, source, requirement);
            }
            for (CardRequirement requirement : card.spawn().requirements()) {
                validateRequirement(problems, source, requirement);
            }
            for (CardEffect effect : card.effects()) {
                validateEffect(problems, source, card, effect, disciplines);
            }
        });
    }

    private static void validateRequirement(List<Problem> problems, String source, CardRequirement requirement) {
        if (requirement instanceof CardRequirement.Item item) {
            if (item.item().isPresent() == item.tag().isPresent()) {
                problems.add(new Problem(source, "item-требование: нужно ровно одно из item/tag"));
            }
            item.item().ifPresent(itemId -> requireItem(problems, source, "требование", itemId));
            if (item.count() <= 0) {
                problems.add(new Problem(source, "item-требование: count ≤ 0"));
            }
        } else if (requirement instanceof CardRequirement.AnyItem anyItem) {
            if (anyItem.count() <= 0) {
                problems.add(new Problem(source, "any_item: count ≤ 0"));
            }
        } else if (requirement instanceof CardRequirement.Progress progress) {
            progress.discipline().ifPresent(discipline -> {
                if (!discipline.equals("self") && !discipline.equals("random")
                        && !allDisciplines().contains(discipline)) {
                    problems.add(new Problem(source, "progress-требование: неизвестная дисциплина " + discipline));
                }
            });
        }
    }

    private static void validateEffect(List<Problem> problems, String source, Card card,
                                       CardEffect effect, Set<String> disciplines) {
        if (effect instanceof CardEffect.Progress progress) {
            validateTarget(problems, source, card, progress.target(), disciplines);
            validateRange(problems, source, progress.amount());
        } else if (effect instanceof CardEffect.ProgressDrain drain) {
            validateRange(problems, source, drain.amount());
            drain.from().forEach(discipline -> {
                if (!disciplines.contains(discipline)) {
                    problems.add(new Problem(source, "progress_drain: неизвестная дисциплина " + discipline));
                }
            });
        } else if (effect instanceof CardEffect.Inspiration inspiration) {
            validateRange(problems, source, inspiration.amount());
        } else if (effect instanceof CardEffect.BlockDiscipline block) {
            validateTarget(problems, source, card, block.target(), disciplines);
            if (block.target().equals("drain") || block.target().equals("all_equalize")) {
                problems.add(new Problem(source, "block_discipline: недопустимая цель " + block.target()));
            }
        } else if (effect instanceof CardEffect.GiveItem giveItem) {
            requireItem(problems, source, "give_item", giveItem.item());
            validateRange(problems, source, giveItem.count());
        } else if (effect instanceof CardEffect.ConsumeXp consumeXp) {
            validateRange(problems, source, consumeXp.amount());
        } else if (effect instanceof CardEffect.BonusDraws bonusDraws && bonusDraws.amount() <= 0) {
            problems.add(new Problem(source, "bonus_draws: amount ≤ 0"));
        } else if (effect instanceof CardEffect.FullValueSlots slots && slots.amount() <= 0) {
            problems.add(new Problem(source, "full_value_slots: amount ≤ 0"));
        } else if (effect instanceof CardEffect.Paper paper && paper.amount() <= 0) {
            problems.add(new Problem(source, "paper: amount ≤ 0"));
        } else if (effect instanceof CardEffect.InkCharges ink && ink.amount() <= 0) {
            problems.add(new Problem(source, "ink_charges: amount ≤ 0"));
        }
    }

    private static void validateTarget(List<Problem> problems, String source, Card card, String target,
                                       Set<String> disciplines) {
        if (target.equals("self")) {
            if (card.discipline().isEmpty()) {
                problems.add(new Problem(source, "цель self, но у карты нет discipline"));
            }
            return;
        }
        if (!CardEffect.TARGETS.contains(target) && !disciplines.contains(target)) {
            problems.add(new Problem(source, "неизвестная цель эффекта: " + target));
        }
    }

    private static void validateRange(List<Problem> problems, String source, IntRange range) {
        if (range.max() < range.min()) {
            problems.add(new Problem(source, "диапазон перевёрнут: " + range.min() + ".." + range.max()));
        }
    }

    private static void validateInkwells(List<Problem> problems) {
        InkwellDefinitions.INSTANCE.all().forEach((itemId, definition) -> {
            String source = "inkwell " + itemId;
            if (!BuiltInRegistries.ITEM.containsKey(itemId)) {
                problems.add(new Problem(source, "неизвестный предмет: " + itemId));
                return;
            }
            Item item = BuiltInRegistries.ITEM.get(itemId);
            if (!(item instanceof InkwellItem)) {
                problems.add(new Problem(source, "предмет не является чернильницей: " + itemId));
            }
            if (definition.capacity() <= 0) {
                problems.add(new Problem(source, "capacity ≤ 0"));
            }
            if (definition.chargesPerDraw() <= 0) {
                problems.add(new Problem(source, "charges_per_draw ≤ 0"));
            }
        });
    }

    private static void validatePapers(List<Problem> problems) {
        Papers.INSTANCE.all().forEach((id, definition) -> {
            String source = "paper " + id;
            if (!BuiltInRegistries.ITEM.containsKey(definition.item())) {
                problems.add(new Problem(source, "неизвестный предмет: " + definition.item()));
            }
            if (definition.perDraw() < 1) {
                problems.add(new Problem(source, "per_draw < 1"));
            }
        });
    }

    private static void requireItem(List<Problem> problems, String source, String what, ResourceLocation id) {
        if (!BuiltInRegistries.ITEM.containsKey(id)) {
            problems.add(new Problem(source, what + ": неизвестный предмет " + id));
        }
    }

    /** Краткая сводка для команды. */
    public static String summary() {
        return "типов столов: " + TableTypes.INSTANCE.all().size()
                + ", карт: " + Cards.INSTANCE.all().size()
                + ", бонусных блоков: " + TableBonuses.INSTANCE.all().size()
                + ", чернильниц: " + InkwellDefinitions.INSTANCE.all().size();
    }
}
