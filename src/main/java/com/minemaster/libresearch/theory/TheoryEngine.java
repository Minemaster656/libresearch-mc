package com.minemaster.libresearch.theory;

import com.minemaster.libresearch.content.ContentText;
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
        /** Готовый текст (отладочные команды, статус) — без перевода. */
        public static Result ok(String text) {
            return new Result(true, Component.literal(text).withStyle(ChatFormatting.GREEN));
        }

        public static Result fail(String text) {
            return new Result(false, Component.literal(text).withStyle(ChatFormatting.RED));
        }

        public static Result ok(Component message) {
            return new Result(true, message.copy().withStyle(ChatFormatting.GREEN));
        }

        public static Result fail(Component message) {
            return new Result(false, message.copy().withStyle(ChatFormatting.RED));
        }

        /** Успех с ключом перевода {@code libresearch.msg.*}. */
        public static Result okKey(String key, Object... args) {
            return ok(Component.translatable(key, args));
        }

        /** Отказ с ключом перевода {@code libresearch.reason.*}. */
        public static Result failKey(String key, Object... args) {
            return fail(Component.translatable(key, args));
        }
    }

    public static Result start(TableContext ctx) {
        TheoryState theory = ctx.theory();
        if (theory.isActive()) {
            return Result.failKey("libresearch.reason.already_active");
        }
        if (theory.hasPendingRewards()) {
            return Result.failKey("libresearch.reason.pending_rewards");
        }
        if (ctx.type().disciplines().isEmpty()) {
            return Result.failKey("libresearch.reason.no_disciplines");
        }
        int paperCost = ctx.paperPerDraw();
        if (ctx.paperCount() < paperCost) {
            return Result.failKey("libresearch.reason.not_enough_paper", paperCost, ctx.paperCount());
        }
        if (ctx.inkCharges() < 1) {
            return Result.failKey("libresearch.reason.no_ink");
        }

        int base = KnowledgeMath.startInspiration(0, 0);
        java.util.Set<ResourceLocation> detected = BonusScanner.scan(ctx.player().serverLevel(),
                ctx.table().getBlockPos(), ctx.type());

        int cost = 0;
        List<ResourceLocation> deck = new ArrayList<>();
        for (String selectedId : theory.selectedBonuses()) {
            ResourceLocation id = ResourceLocation.tryParse(selectedId);
            if (id == null || !detected.contains(id)) {
                continue;
            }
            Optional<com.minemaster.libresearch.content.bonus.BonusBlockDefinition> optional =
                    com.minemaster.libresearch.content.bonus.TableBonuses.INSTANCE.get(id);
            if (optional.isEmpty()) {
                continue;
            }
            com.minemaster.libresearch.content.bonus.BonusBlockDefinition bonus = optional.get();
            cost += Math.max(0, bonus.cost());
            bonus.cards().forEach(entry -> {
                for (int copy = 0; copy < Math.max(0, entry.count()); copy++) {
                    deck.add(entry.card());
                }
            });
        }

        int inspiration = base - cost;
        if (inspiration < 1) {
            return Result.failKey("libresearch.reason.too_many_bonuses");
        }

        theory.start(inspiration, ctx.type().disciplines().size());
        theory.setBonusDeck(deck);
        ctx.markUpdated();
        return deck.isEmpty()
                ? Result.okKey("libresearch.msg.started", inspiration, base)
                : Result.okKey("libresearch.msg.started_with_bonus", inspiration, base, deck.size());
    }

    /** Выбор/снятие бонусного блока до старта теории. */
    public static Result toggleBonus(TableContext ctx, String bonusId) {
        TheoryState theory = ctx.theory();
        if (theory.isActive()) {
            return Result.failKey("libresearch.reason.bonus_locked");
        }
        ResourceLocation id = ResourceLocation.tryParse(bonusId);
        if (id == null || !ctx.type().bonusBlocks().contains(id)) {
            return Result.failKey("libresearch.reason.unknown_bonus", bonusId);
        }
        if (com.minemaster.libresearch.content.bonus.TableBonuses.INSTANCE.get(id).isEmpty()) {
            return Result.failKey("libresearch.reason.bonus_not_loaded", bonusId);
        }
        java.util.Set<ResourceLocation> detected = BonusScanner.scan(ctx.player().serverLevel(),
                ctx.table().getBlockPos(), ctx.type());
        if (!detected.contains(id)) {
            return Result.failKey("libresearch.reason.bonus_missing_block");
        }

        boolean selected = theory.toggleBonus(bonusId);
        ctx.markUpdated();
        return Result.okKey(selected ? "libresearch.msg.bonus_selected" : "libresearch.msg.bonus_unselected",
                bonusId);
    }

    public static Result draw(TableContext ctx) {
        TheoryState theory = ctx.theory();
        if (!theory.isActive()) {
            return Result.failKey("libresearch.reason.theory_not_started");
        }
        if (theory.inspiration() <= 0) {
            return Result.failKey("libresearch.reason.inspiration_zero");
        }
        if (!theory.hand().isEmpty()) {
            return Result.failKey("libresearch.reason.play_card_first");
        }
        int paperCost = ctx.paperPerDraw();
        if (ctx.paperCount() < paperCost) {
            return Result.failKey("libresearch.reason.not_enough_paper", paperCost, ctx.paperCount());
        }

        int size = HAND_SIZE;
        if (theory.bonusDraws() > 0) {
            size = BONUS_HAND_SIZE;
            theory.addBonusDraws(-1);
        }

        ctx.consumePaper(paperCost);
        List<DrawnCard> hand = drawHand(ctx, size);
        theory.setHand(hand);
        ctx.markUpdated();

        if (hand.isEmpty()) {
            return Result.okKey("libresearch.msg.hand_empty");
        }
        var names = Component.empty();
        for (int index = 0; index < hand.size(); index++) {
            if (index > 0) {
                names.append(Component.literal(", "));
            }
            names.append(describe(ctx, hand.get(index)));
        }
        return Result.okKey("libresearch.msg.hand", names);
    }

    public static Result play(TableContext ctx, int index) {
        TheoryState theory = ctx.theory();
        if (!theory.isActive()) {
            return Result.failKey("libresearch.reason.theory_not_started");
        }
        if (theory.inspiration() <= 0) {
            return Result.failKey("libresearch.reason.inspiration_zero_play");
        }

        List<DrawnCard> hand = theory.hand();
        if (index < 0 || index >= hand.size()) {
            return Result.failKey("libresearch.reason.no_card_index", index + 1);
        }
        DrawnCard drawn = hand.get(index);
        Optional<Card> optional = Cards.INSTANCE.get(drawn.card());
        if (optional.isEmpty()) {
            return Result.failKey("libresearch.reason.card_gone", drawn.card());
        }
        Card card = optional.get();

        int inkCost = inkPerPlay(ctx) + CardRoller.extraInk(card);
        // Розыгрыш карты бумагу не тратит (только чернила); карта может потребовать
        // доп. бумагу эффектом `paper`.
        int paperCost = CardRoller.extraPaper(card);
        if (ctx.inkCharges() < inkCost) {
            return Result.failKey("libresearch.reason.not_enough_ink", inkCost, ctx.inkCharges());
        }
        if (ctx.paperCount() < paperCost) {
            return Result.failKey("libresearch.reason.not_enough_paper", paperCost, ctx.paperCount());
        }

        RequirementChecker.Result check = RequirementChecker.check(card.requires(), drawn.discipline(), ctx);
        if (!check.ok()) {
            return Result.fail(check.reason());
        }

        RequirementChecker.consume(card.requires(), ctx);
        ctx.drainInk(inkCost);
        ctx.consumePaper(paperCost);

        List<Component> log = EffectApplier.newLog();
        EffectApplier.apply(card, drawn, ctx, log);

        theory.setInspiration(theory.inspiration() - drawn.cost());
        theory.setHand(List.of());
        ctx.markUpdated();

        Component name = ContentText.display(card.display().isBlank()
                ? drawn.card().toString() : card.display());
        var message = Component.empty();
        if (log.isEmpty()) {
            message.append(Component.translatable("libresearch.msg.card_played", name));
        } else {
            var details = Component.empty();
            for (int entry = 0; entry < log.size(); entry++) {
                if (entry > 0) {
                    details.append(Component.literal(", "));
                }
                details.append(log.get(entry));
            }
            message.append(Component.translatable("libresearch.msg.card_played_details", name, details));
        }
        if (theory.inspiration() <= 0) {
            message.append(Component.translatable("libresearch.msg.inspiration_zero_tail"));
        }
        return Result.ok(message);
    }

    public static Result finish(TableContext ctx) {
        TheoryState theory = ctx.theory();
        if (!theory.isActive()) {
            return Result.failKey("libresearch.reason.theory_not_running");
        }
        if (theory.inspiration() > 0) {
            return Result.failKey("libresearch.reason.finish_needs_zero", theory.inspiration());
        }

        List<KnowledgeMath.Entry> entries = ctx.type().disciplines().stream()
                .map(discipline -> new KnowledgeMath.Entry(discipline.id(), theory.progress(discipline.id())))
                .toList();
        List<KnowledgeMath.Conversion> conversions = KnowledgeMath.convert(entries, theory.fullValueSlots());

        Knowledge knowledge = PlayerKnowledge.get(ctx.player());
        var summary = Component.empty();
        boolean any = false;
        for (KnowledgeMath.Conversion conversion : conversions) {
            if (conversion.centi() <= 0) {
                continue;
            }
            knowledge = knowledge.add(conversion.discipline(), conversion.centi());
            theory.addReward(conversion.discipline(), conversion.centi());
            if (any) {
                summary.append(Component.literal("; "));
            }
            summary.append(Component.translatable("libresearch.msg.finished_entry",
                    disciplineName(ctx, conversion.discipline()),
                    conversion.percent(), conversion.centi()));
            any = true;
        }

        PlayerKnowledge.set(ctx.player(), knowledge);
        theory.clearTheory();
        ctx.markUpdated();

        if (!any) {
            return Result.okKey("libresearch.msg.finished_empty");
        }
        return Result.okKey("libresearch.msg.finished", summary);
    }

    public static Result discard(TableContext ctx) {
        if (!ctx.theory().isActive()) {
            return Result.failKey("libresearch.reason.theory_not_running");
        }
        ctx.theory().clearTheory();
        ctx.markUpdated();
        return Result.okKey("libresearch.msg.discarded");
    }

    public static Result claim(TableContext ctx, String disciplineId) {
        Optional<TableType.Discipline> optional = ctx.discipline(disciplineId);
        if (optional.isEmpty()) {
            return Result.failKey("libresearch.reason.unknown_discipline", disciplineId);
        }
        TableType.Discipline discipline = optional.get();

        Knowledge knowledge = PlayerKnowledge.get(ctx.player());
        long granted = ctx.theory().reward(disciplineId);
        long points = KnowledgeMath.claimablePoints(granted, knowledge.centi(disciplineId));
        if (points <= 0) {
            return Result.failKey("libresearch.reason.nothing_to_claim");
        }

        long requested = points * Math.max(1, discipline.countPerPoint());
        int count = (int) Math.min(Integer.MAX_VALUE, requested);
        Item item = BuiltInRegistries.ITEM.get(discipline.payout());
        Component itemName = new ItemStack(item).getHoverName();
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
        return Result.okKey("libresearch.msg.claimed", count, itemName, points);
    }

    public static Result claimAll(TableContext ctx) {
        var messages = Component.empty();
        boolean any = false;
        for (String discipline : List.copyOf(ctx.theory().rewards().keySet())) {
            Result result = claim(ctx, discipline);
            if (result.ok()) {
                if (any) {
                    messages.append(Component.literal(" "));
                }
                messages.append(result.message());
                any = true;
            }
        }
        if (!any) {
            return Result.failKey("libresearch.msg.claim_none_points");
        }
        return new Result(true, messages);
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
            return Result.failKey("libresearch.reason.theory_not_running");
        }
        if (!ctx.type().disciplineIds().contains(discipline)) {
            return Result.failKey("libresearch.reason.unknown_discipline", discipline);
        }
        ctx.theory().setProgress(discipline, percent);
        ctx.markUpdated();
        return Result.okKey("libresearch.msg.debug_progress", discipline, Math.max(0, percent));
    }

    public static Result debugSetInspiration(TableContext ctx, int value) {
        if (!ctx.theory().isActive()) {
            return Result.failKey("libresearch.reason.theory_not_running");
        }
        ctx.theory().setInspiration(value);
        ctx.markUpdated();
        return Result.okKey("libresearch.msg.debug_inspiration", ctx.theory().inspiration());
    }

    private static List<DrawnCard> drawHand(TableContext ctx, int size) {
        List<DrawnCard> hand = new ArrayList<>();
        Set<ResourceLocation> used = new HashSet<>();
        boolean bonusTaken = false;

        for (int slot = 0; slot < size; slot++) {
            if (!bonusTaken && !ctx.theory().bonusDeck().isEmpty() && ctx.random().nextFloat() < 0.25F) {
                DrawnCard bonus = tryDrawBonus(ctx, used);
                if (bonus != null) {
                    hand.add(bonus);
                    used.add(bonus.card());
                    bonusTaken = true;
                    continue;
                }
            }

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

    /**
     * Бонусная ветка добора: с шансом 25% на слот вместо обычной карты берётся
     * запись из конечной бонусной колоды. Невалидная запись не удаляется —
     * просто не подходит на этот раз (как в описании механики).
     */
    private static DrawnCard tryDrawBonus(TableContext ctx, Set<ResourceLocation> used) {
        TheoryState theory = ctx.theory();
        for (int attempt = 0; attempt < 10000; attempt++) {
            List<ResourceLocation> deck = theory.bonusDeck();
            if (deck.isEmpty()) {
                return null;
            }
            int index = ctx.random().nextInt(deck.size());
            ResourceLocation id = deck.get(index);
            Optional<Card> optional = Cards.INSTANCE.get(id);
            if (optional.isEmpty()) {
                theory.removeBonusCard(index);
                continue;
            }
            Card card = optional.get();
            if (used.contains(id) || !isBonusDrawable(card, ctx)) {
                continue;
            }
            theory.removeBonusCard(index);
            return CardRoller.roll(id, card, ctx);
        }
        return null;
    }

    /** Бонусная карта не требует открытой дисциплины — только чтобы она не была заблокирована. */
    private static boolean isBonusDrawable(Card card, TableContext ctx) {
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

    private static Component describe(TableContext ctx, DrawnCard drawn) {
        String name = Cards.INSTANCE.get(drawn.card())
                .map(card -> card.display().isBlank() ? drawn.card().toString() : card.display())
                .orElse(drawn.card().toString());
        Component base = ContentText.display(name);
        if (drawn.discipline() == null) {
            return base;
        }
        return Component.translatable("libresearch.msg.hand_card", base,
                ContentText.display(drawn.discipline()));
    }

    /** Отображаемое имя дисциплины: {@code display} из типа стола либо её id. */
    private static Component disciplineName(TableContext ctx, String disciplineId) {
        return ctx.type().disciplines().stream()
                .filter(discipline -> discipline.id().equals(disciplineId))
                .map(discipline -> ContentText.display(discipline.display()))
                .findFirst()
                .orElse(ContentText.display(disciplineId));
    }
}
