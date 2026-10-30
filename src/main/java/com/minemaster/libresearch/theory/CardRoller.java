package com.minemaster.libresearch.theory;

import com.minemaster.libresearch.content.card.Card;
import com.minemaster.libresearch.content.card.CardEffect;
import com.minemaster.libresearch.content.card.IntRange;
import com.minemaster.libresearch.content.table.TableType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Создаёт «инстанс» карты: фиксирует все случайные значения и цели эффектов
 * в момент добора. Дальше карта разыгрывается уже без бросков.
 */
public final class CardRoller {
    private CardRoller() {
    }

    public static DrawnCard roll(ResourceLocation id, Card card, TableContext ctx) {
        TheoryState theory = ctx.theory();
        String discipline = resolveDiscipline(card, ctx);

        List<Integer> rolls = new ArrayList<>();
        List<String> targets = new ArrayList<>();
        RandomSource random = ctx.random();

        for (CardEffect effect : card.effects()) {
            switch (effect) {
                case CardEffect.Progress progress -> {
                    rolls.add(rollRange(progress.amount(), random));
                    targets.add(resolveTarget(progress.target(), discipline, ctx));
                }
                case CardEffect.ProgressDrain drain -> {
                    rolls.add(rollRange(drain.amount(), random));
                    targets.add("");
                }
                case CardEffect.Inspiration inspiration -> {
                    rolls.add(rollRange(inspiration.amount(), random));
                    targets.add("");
                }
                case CardEffect.BonusDraws bonusDraws -> {
                    rolls.add(bonusDraws.amount());
                    targets.add("");
                }
                case CardEffect.FullValueSlots slots -> {
                    rolls.add(slots.amount());
                    targets.add("");
                }
                case CardEffect.BlockDiscipline block -> {
                    rolls.add(0);
                    targets.add(resolveBlockTarget(block.target(), theory, ctx));
                }
                case CardEffect.GiveItem giveItem -> {
                    rolls.add(rollRange(giveItem.count(), random));
                    targets.add("");
                }
                case CardEffect.ConsumeXp xp -> {
                    rolls.add(rollRange(xp.amount(), random));
                    targets.add("");
                }
                case CardEffect.Paper paper -> {
                    rolls.add(paper.amount());
                    targets.add("");
                }
                case CardEffect.InkCharges ink -> {
                    rolls.add(ink.amount());
                    targets.add("");
                }
            }
        }

        return new DrawnCard(id, discipline, card.cost(), rolls, targets);
    }

    /** Сколько бумаги/чернил дополнительно требует карта (сверх обычных 1 заряда). */
    public static int extraPaper(Card card) {
        return card.effects().stream()
                .filter(CardEffect.Paper.class::isInstance)
                .mapToInt(effect -> ((CardEffect.Paper) effect).amount())
                .sum();
    }

    public static int extraInk(Card card) {
        return card.effects().stream()
                .filter(CardEffect.InkCharges.class::isInstance)
                .mapToInt(effect -> ((CardEffect.InkCharges) effect).amount())
                .sum();
    }

    @Nullable
    public static String resolveDiscipline(Card card, TableContext ctx) {
        Optional<String> raw = card.discipline();
        if (raw.isEmpty()) {
            return null;
        }
        String value = raw.get();
        if (value.equals("random")) {
            return randomDiscipline(ctx);
        }
        if (value.equals("self")) {
            return null;
        }
        return value;
    }

    private static String resolveTarget(String target, @Nullable String cardDiscipline, TableContext ctx) {
        return switch (target) {
            case "self" -> cardDiscipline != null ? cardDiscipline : randomDiscipline(ctx);
            case "random" -> randomDiscipline(ctx);
            case "highest" -> extreme(ctx, true);
            case "lowest" -> extreme(ctx, false);
            default -> target;
        };
    }

    private static String resolveBlockTarget(String target, TheoryState theory, TableContext ctx) {
        if (!target.equals("random")) {
            return target;
        }
        List<String> candidates = ctx.type().disciplines().stream()
                .map(TableType.Discipline::id)
                .filter(id -> !theory.isBlocked(id))
                .toList();
        if (candidates.isEmpty()) {
            return "";
        }
        return candidates.get(ctx.random().nextInt(candidates.size()));
    }

    @Nullable
    private static String randomDiscipline(TableContext ctx) {
        List<TableType.Discipline> disciplines = ctx.type().disciplines();
        if (disciplines.isEmpty()) {
            return null;
        }
        return disciplines.get(ctx.random().nextInt(disciplines.size())).id();
    }

    /** Дисциплина с наибольшим/наименьшим прогрессом; при равенстве — первая по порядку. */
    private static String extreme(TableContext ctx, boolean highest) {
        String best = null;
        int bestValue = highest ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        for (TableType.Discipline discipline : ctx.type().disciplines()) {
            int value = ctx.theory().progress(discipline.id());
            if (best == null || (highest ? value > bestValue : value < bestValue)) {
                best = discipline.id();
                bestValue = value;
            }
        }
        return best == null ? "" : best;
    }

    public static int rollRange(IntRange range, RandomSource random) {
        IntRange normalized = range.normalized();
        if (normalized.min() == normalized.max()) {
            return normalized.min();
        }
        return normalized.min() + random.nextInt(normalized.max() - normalized.min() + 1);
    }
}
