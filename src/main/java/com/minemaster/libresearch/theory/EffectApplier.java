package com.minemaster.libresearch.theory;

import com.minemaster.libresearch.content.card.Card;
import com.minemaster.libresearch.content.card.CardEffect;
import com.minemaster.libresearch.content.table.TableType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * Применение эффектов уже разыгранной карты. Дополнительный расход бумаги/чернил
 * ({@code paper}, {@code ink_charges}) здесь пропускается: он проверяется и
 * списывается движком до применения эффектов, вместе с обычной платой за розыгрыш.
 *
 * <p>Лог возвращается компонентами с ключами {@code libresearch.log.*}, чтобы
 * сообщение о розыгрыше переводилось на клиенте.</p>
 */
public final class EffectApplier {
    private EffectApplier() {
    }

    public static void apply(Card card, DrawnCard drawn, TableContext ctx, List<Component> log) {
        TheoryState theory = ctx.theory();

        for (int index = 0; index < card.effects().size(); index++) {
            CardEffect effect = card.effects().get(index);
            int roll = drawn.roll(index);
            String target = drawn.target(index);

            switch (effect) {
                case CardEffect.Progress progress -> {
                    if (target.equals("all_equalize")) {
                        equalize(theory, ctx.type());
                        log.add(Component.translatable("libresearch.log.equalize"));
                    } else if (!target.isBlank()) {
                        theory.addProgress(target, roll);
                        log.add(Component.translatable("libresearch.log.progress", target, roll));
                    }
                }
                case CardEffect.ProgressDrain drain -> {
                    List<String> from = drain.from().isEmpty()
                            ? ctx.type().disciplines().stream().map(TableType.Discipline::id).toList()
                            : drain.from();
                    int drained = drainRoundRobin(theory, from, roll);
                    log.add(Component.translatable("libresearch.log.drain", drained));
                }
                case CardEffect.Inspiration inspiration -> {
                    theory.setInspiration(theory.inspiration() + roll);
                    log.add(Component.translatable("libresearch.log.inspiration",
                            (roll >= 0 ? "+" : "") + roll));
                }
                case CardEffect.BonusDraws bonusDraws -> {
                    theory.addBonusDraws(roll);
                    log.add(Component.translatable("libresearch.log.bonus_draws", roll));
                }
                case CardEffect.FullValueSlots slots -> {
                    theory.addFullValueSlots(roll);
                    log.add(Component.translatable("libresearch.log.full_value", roll));
                }
                case CardEffect.BlockDiscipline ignored -> {
                    if (!target.isBlank()) {
                        theory.block(target);
                        log.add(Component.translatable("libresearch.log.blocked", target));
                    }
                }
                case CardEffect.GiveItem giveItem -> giveItem(ctx.player(), giveItem, roll, log);
                case CardEffect.ConsumeXp xp -> {
                    int levels = xp.all() ? ctx.player().experienceLevel : roll;
                    ctx.player().giveExperienceLevels(-Math.max(0, levels));
                    log.add(Component.translatable("libresearch.log.xp", levels));
                }
                case CardEffect.Paper ignored -> {
                    // Расход учтён движком до применения эффектов.
                }
                case CardEffect.InkCharges ignored -> {
                    // Расход учтён движком до применения эффектов.
                }
            }
        }
    }

    private static void equalize(TheoryState theory, TableType type) {
        List<TableType.Discipline> active = type.disciplines().stream()
                .filter(discipline -> !theory.isBlocked(discipline.id()))
                .toList();
        if (active.isEmpty()) {
            return;
        }
        int total = active.stream().mapToInt(discipline -> theory.progress(discipline.id())).sum();
        int share = total / active.size();
        active.forEach(discipline -> theory.setProgress(discipline.id(), share));
    }

    /** Снимает до {@code amount} процентов по кругу; в минус не уходит. */
    private static int drainRoundRobin(TheoryState theory, List<String> disciplines, int amount) {
        if (disciplines.isEmpty()) {
            return 0;
        }
        int remaining = amount;
        int drained = 0;
        while (remaining > 0) {
            boolean stepped = false;
            for (String discipline : disciplines) {
                if (remaining <= 0) {
                    break;
                }
                int current = theory.progress(discipline);
                if (current > 0) {
                    theory.setProgress(discipline, current - 1);
                    remaining--;
                    drained++;
                    stepped = true;
                }
            }
            if (!stepped) {
                break;
            }
        }
        return drained;
    }

    private static void giveItem(ServerPlayer player, CardEffect.GiveItem effect, int count, List<Component> log) {
        if (count <= 0) {
            return;
        }
        Item item = BuiltInRegistries.ITEM.get(effect.item());
        int maxStack = Math.max(1, new ItemStack(item).getMaxStackSize());
        int remaining = count;
        while (remaining > 0) {
            int size = Math.min(remaining, maxStack);
            ItemStack stack = new ItemStack(item, size);
            player.getInventory().add(stack);
            if (!stack.isEmpty()) {
                player.drop(stack, false);
            }
            remaining -= size;
        }
        log.add(Component.translatable("libresearch.log.give_item",
                count, new ItemStack(item).getHoverName()));
    }

    /** Пустой список для лога — удобно, когда сообщение не нужно. */
    public static List<Component> newLog() {
        return new ArrayList<>();
    }
}
