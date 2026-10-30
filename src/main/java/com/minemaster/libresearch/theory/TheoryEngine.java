package com.minemaster.libresearch.theory;

import com.minemaster.libresearch.content.card.Card;
import com.minemaster.libresearch.content.card.Cards;
import com.minemaster.libresearch.content.table.TableType;
import com.minemaster.libresearch.core.KnowledgeMath;
import com.minemaster.libresearch.core.WeightedPool;
import com.minemaster.libresearch.item.InkwellDefinitions;
import com.minemaster.libresearch.item.InkwellItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Правила стола: старт теории, добор руки, розыгрыш карты, завершение с выдачей
 * знаний и наград, забор наград. Никакого GUI — команды и будущие пакеты вызывают
 * эти же методы.
 */
public final class TheoryEngine {
    public static final int HAND_SIZE = 2;
    public static final int BONUS_HAND_SIZE = 3;

    private TheoryEngine() {
    }

    public record Result(boolean ok, Component message) {
        public static Result ok(String text) {
            return new Result(true, Component.literal(text).withStyle(ChatFormatting.GREEN));
        }

        public static Result fail(String text) {
            return new Result(false, Component.literal(text).withStyle(ChatFormatting.RED));
        }
    }

    public static Result start(TableContext ctx) {
        TheoryState theory = ctx.theory();
        if (theory.isActive()) {
            return Result.fail("Теория уже идёт.");
        }
        if (theory.hasPendingRewards()) {
            return Result.fail("Сначала заберите награды: у стола есть целые очки.");
        }
        if (ctx.type().disciplines().isEmpty()) {
            return Result.fail("У этого стола нет ни одной дисциплины.");
        }
        if (ctx.paperCount() < 1) {
            return Result.fail("Нужен лист бумаги в слоте.");
        }
        if (ctx.inkCharges() < 1) {
            return Result.fail("Нужна чернильница с чернилами в слоте.");
        }

        int inspiration = KnowledgeMath.startInspiration(0, 0);
        theory.start(inspiration, ctx.type().disciplines().size());
        ctx.markUpdated();
        return Result.ok("Теория начата. Вдохновение " + inspiration + "/" + inspiration + ".");
    }

    public static Result draw(TableContext ctx) {
        TheoryState theory = ctx.theory();
        if (!theory.isActive()) {
            return Result.fail("Сначала начните теорию.");
        }
        if (theory.inspiration() <= 0) {
            return Result.fail("Вдохновение на нуле — завершите теорию.");
        }
        if (!theory.hand().isEmpty()) {
            return Result.fail("Сначала разыграйте карту из руки.");
        }
        if (ctx.paperCount() < 1) {
            return Result.fail("Нужен лист бумаги в слоте.");
        }

        int size = HAND_SIZE;
        if (theory.bonusDraws() > 0) {
            size = BONUS_HAND_SIZE;
            theory.addBonusDraws(-1);
        }

        ctx.consumePaper(1);
        List<DrawnCard> hand = drawHand(ctx, size);
        theory.setHand(hand);
        ctx.markUpdated();

        if (hand.isEmpty()) {
            return Result.ok("В руке не осталось доступных карт. Потрачен 1 лист бумаги.");
        }
        List<String> names = hand.stream().map(card -> describe(ctx, card)).toList();
        return Result.ok("В руке: " + String.join(", ", names) + ".");
    }

    public static Result play(TableContext ctx, int index) {
        TheoryState theory = ctx.theory();
        if (!theory.isActive()) {
            return Result.fail("Сначала начните теорию.");
        }
        if (theory.inspiration() <= 0) {
            return Result.fail("Вдохновение на нуле — теория завершена, осталось её завершить.");
        }

        List<DrawnCard> hand = theory.hand();
        if (index < 0 || index >= hand.size()) {
            return Result.fail("В руке нет карты с номером " + (index + 1) + ".");
        }
        DrawnCard drawn = hand.get(index);
        Optional<Card> optional = Cards.INSTANCE.get(drawn.card());
        if (optional.isEmpty()) {
            return Result.fail("Карта " + drawn.card() + " больше не существует (датапак изменился).");
        }
        Card card = optional.get();

        int inkCost = inkPerPlay(ctx) + CardRoller.extraInk(card);
        int paperCost = CardRoller.extraPaper(card);
        if (ctx.inkCharges() < inkCost) {
            return Result.fail("Не хватает чернил: нужно " + inkCost + ", в чернильнице " + ctx.inkCharges() + ".");
        }
        if (ctx.paperCount() < paperCost) {
            return Result.fail("Не хватает бумаги: нужно " + paperCost + ", в слоте " + ctx.paperCount() + ".");
        }

        RequirementChecker.Result check = RequirementChecker.check(card.requires(), drawn.discipline(), ctx);
        if (!check.ok()) {
            return Result.fail(check.reason());
        }

        RequirementChecker.consume(card.requires(), ctx);
        ctx.drainInk(inkCost);
        ctx.consumePaper(paperCost);

        List<String> log = EffectApplier.newLog();
        EffectApplier.apply(card, drawn, ctx, log);

        theory.setInspiration(theory.inspiration() - drawn.cost());
        theory.setHand(List.of());
        ctx.markUpdated();

        String name = card.display().isBlank() ? drawn.card().toString() : card.display();
        String details = log.isEmpty() ? "" : " (" + String.join(", ", log) + ")";
        String tail = theory.inspiration() <= 0 ? " Вдохновение на нуле — завершите теорию." : "";
        return Result.ok("Разыграна карта " + name + details + "." + tail);
    }

    public static Result finish(TableContext ctx) {
        TheoryState theory = ctx.theory();
        if (!theory.isActive()) {
            return Result.fail("Теория не идёт.");
        }
        if (theory.inspiration() > 0) {
            return Result.fail("Вдохновение ещё не на нуле (" + theory.inspiration()
                    + ") — доведите теорию до конца.");
        }

        List<KnowledgeMath.Entry> entries = ctx.type().disciplines().stream()
                .map(discipline -> new KnowledgeMath.Entry(discipline.id(), theory.progress(discipline.id())))
                .toList();
        List<KnowledgeMath.Conversion> conversions = KnowledgeMath.convert(entries, theory.fullValueSlots());

        Knowledge knowledge = PlayerKnowledge.get(ctx.player());
        List<String> summary = new ArrayList<>();
        for (KnowledgeMath.Conversion conversion : conversions) {
            if (conversion.centi() <= 0) {
                continue;
            }
            knowledge = knowledge.add(conversion.discipline(), conversion.centi());
            theory.addReward(conversion.discipline(), conversion.centi());
            summary.add(conversion.discipline() + " " + conversion.percent() + "% → "
                    + conversion.centi() + " санти-очков");
        }

        PlayerKnowledge.set(ctx.player(), knowledge);
        theory.clearTheory();
        ctx.markUpdated();

        if (summary.isEmpty()) {
            return Result.ok("Теория завершена без прогресса: начислять нечего.");
        }
        return Result.ok("Теория завершена. " + String.join("; ", summary) + ". Заберите награды.");
    }

    public static Result discard(TableContext ctx) {
        if (!ctx.theory().isActive()) {
            return Result.fail("Теория не идёт.");
        }
        ctx.theory().clearTheory();
        ctx.markUpdated();
        return Result.ok("Теория сброшена. Прогресс потерян, награды за прошлые теории остались.");
    }

    public static Result claim(TableContext ctx, String disciplineId) {
        Optional<TableType.Discipline> optional = ctx.discipline(disciplineId);
        if (optional.isEmpty()) {
            return Result.fail("У этого стола нет дисциплины " + disciplineId + ".");
        }
        TableType.Discipline discipline = optional.get();

        Knowledge knowledge = PlayerKnowledge.get(ctx.player());
        long granted = ctx.theory().reward(disciplineId);
        long points = KnowledgeMath.claimablePoints(granted, knowledge.centi(disciplineId));
        if (points <= 0) {
            return Result.fail("Нечего забирать: нужно хотя бы 1 целое очко.");
        }

        long requested = points * Math.max(1, discipline.countPerPoint());
        int count = (int) Math.min(Integer.MAX_VALUE, requested);
        Item item = BuiltInRegistries.ITEM.get(discipline.payout());
        String itemName = new ItemStack(item).getHoverName().getString();
        int maxStack = Math.max(1, new ItemStack(item).getMaxStackSize());

        int remaining = count;
        while (remaining > 0) {
            int size = Math.min(remaining, maxStack);
            ItemStack stack = new ItemStack(item, size);
            ctx.player().getInventory().add(stack);
            if (!stack.isEmpty()) {
                ctx.player().drop(stack, false);
            }
            remaining -= size;
        }

        long spent = points * KnowledgeMath.CENTI_PER_POINT;
        PlayerKnowledge.set(ctx.player(), knowledge.subtract(disciplineId, spent));
        ctx.theory().consumeReward(disciplineId, spent);
        ctx.markUpdated();
        return Result.ok("Получено: " + count + " × " + itemName + " (списано " + points + " очк.).");
    }

    public static Result claimAll(TableContext ctx) {
        List<String> messages = new ArrayList<>();
        for (String discipline : List.copyOf(ctx.theory().rewards().keySet())) {
            Result result = claim(ctx, discipline);
            if (result.ok()) {
                messages.add(result.message().getString());
            }
        }
        if (messages.isEmpty()) {
            return Result.fail("Нечего забирать: целых очков нет.");
        }
        return Result.ok(String.join(" ", messages));
    }

    public static Result status(TableContext ctx) {
        TheoryState theory = ctx.theory();
        StringBuilder text = new StringBuilder();
        text.append("Стол ").append(ctx.table().getTableType()).append(": ");

        if (!theory.isActive()) {
            text.append("теория не идёт");
        } else {
            text.append("вдохновение ").append(theory.inspiration()).append("/").append(theory.startInspiration());
            text.append(", бонусных доборов ").append(theory.bonusDraws());
            text.append(", в руке карт ").append(theory.hand().size());
            if (!theory.progressMap().isEmpty()) {
                text.append(", прогресс: ");
                text.append(String.join(", ", theory.progressMap().entrySet().stream()
                        .map(entry -> entry.getKey() + " " + entry.getValue() + "%")
                        .toList()));
            }
            if (!theory.blockedSet().isEmpty()) {
                text.append(", заблокировано: ").append(String.join(", ", theory.blockedSet()));
            }
        }

        if (!theory.rewards().isEmpty()) {
            text.append("; награды: ");
            text.append(String.join(", ", theory.rewards().entrySet().stream()
                    .map(entry -> entry.getKey() + " " + entry.getValue() + " санти")
                    .toList()));
        }
        text.append("; бумага: ").append(ctx.paperCount());
        text.append("; чернила: ").append(ctx.inkCharges());
        return Result.ok(text.toString());
    }

    public static Result debugSetProgress(TableContext ctx, String discipline, int percent) {
        if (!ctx.theory().isActive()) {
            return Result.fail("Теория не идёт.");
        }
        if (!ctx.type().disciplineIds().contains(discipline)) {
            return Result.fail("У стола нет дисциплины " + discipline + ".");
        }
        ctx.theory().setProgress(discipline, percent);
        ctx.markUpdated();
        return Result.ok(discipline + " → " + Math.max(0, percent) + "%.");
    }

    public static Result debugSetInspiration(TableContext ctx, int value) {
        if (!ctx.theory().isActive()) {
            return Result.fail("Теория не идёт.");
        }
        ctx.theory().setInspiration(value);
        ctx.markUpdated();
        return Result.ok("Вдохновение → " + ctx.theory().inspiration() + ".");
    }

    private static List<DrawnCard> drawHand(TableContext ctx, int size) {
        List<DrawnCard> hand = new ArrayList<>();
        Set<ResourceLocation> used = new HashSet<>();

        for (int slot = 0; slot < size; slot++) {
            List<WeightedPool.Option<ResourceLocation>> options = new ArrayList<>();
            for (Map.Entry<ResourceLocation, Card> entry : Cards.INSTANCE.all().entrySet()) {
                if (used.contains(entry.getKey()) || !isDrawable(entry.getValue(), ctx)) {
                    continue;
                }
                options.add(new WeightedPool.Option<>(entry.getKey(), entry.getValue().weight()));
            }
            if (options.isEmpty()) {
                break;
            }

            Optional<ResourceLocation> picked = WeightedPool.pick(options, ctx.random()::nextInt);
            if (picked.isEmpty()) {
                break;
            }
            ResourceLocation id = picked.get();
            Cards.INSTANCE.get(id).ifPresent(card -> hand.add(CardRoller.roll(id, card, ctx)));
            used.add(id);
        }
        return hand;
    }

    private static boolean isDrawable(Card card, TableContext ctx) {
        if (card.weight() <= 0 || card.bonusOnly()) {
            return false;
        }
        if (card.cost() > ctx.theory().inspiration()) {
            return false;
        }
        Optional<String> discipline = card.discipline();
        if (discipline.isPresent()) {
            String id = discipline.get();
            if (!id.equals("random") && !id.equals("self")) {
                if (!ctx.type().disciplineIds().contains(id)) {
                    return false;
                }
                if (ctx.theory().isBlocked(id)) {
                    return false;
                }
            }
        }
        return RequirementChecker.check(card.spawn().requirements(), null, ctx).ok();
    }

    private static int inkPerPlay(TableContext ctx) {
        ItemStack stack = ctx.inkStack();
        if (stack.getItem() instanceof InkwellItem) {
            return Math.max(1, InkwellDefinitions.INSTANCE.chargesPerDrawOf(stack.getItem()));
        }
        return 1;
    }

    private static String describe(TableContext ctx, DrawnCard drawn) {
        String name = Cards.INSTANCE.get(drawn.card())
                .map(card -> card.display().isBlank() ? drawn.card().toString() : card.display())
                .orElse(drawn.card().toString());
        if (drawn.discipline() != null) {
            name += " (" + drawn.discipline() + ")";
        }
        return name;
    }
}
