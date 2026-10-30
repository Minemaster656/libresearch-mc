package com.minemaster.libresearch.theory;

import com.minemaster.libresearch.content.card.CardRequirement;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Predicate;

/**
 * Проверка и списание требований карты. Требования «мягкие»: карту можно
 * предложить, но нельзя разыграть, пока они не выполнены; причина возвращается
 * текстом, чтобы GUI её показал.
 */
public final class RequirementChecker {
    private RequirementChecker() {
    }

    public record Result(boolean ok, String reason) {
        public static final Result OK = new Result(true, "");

        public static Result fail(String reason) {
            return new Result(false, reason);
        }
    }

    public static Result check(List<CardRequirement> requirements, @Nullable String cardDiscipline, TableContext ctx) {
        ServerPlayer player = ctx.player();

        for (CardRequirement requirement : requirements) {
            switch (requirement) {
                case CardRequirement.Item item -> {
                    Predicate<ItemStack> matcher = matcher(item);
                    int available = count(player, matcher);
                    if (available < item.count()) {
                        return Result.fail("Нужно " + item.count() + " × " + describe(item) + " (есть " + available + ")");
                    }
                }
                case CardRequirement.AnyItem anyItem -> {
                    Predicate<ItemStack> matcher = tagMatcher(anyItem.tag());
                    int available = count(player, matcher);
                    if (available < anyItem.count()) {
                        return Result.fail("Нужно " + anyItem.count() + " × любой предмет из #" + anyItem.tag()
                                + " (есть " + available + ")");
                    }
                }
                case CardRequirement.XpLevels xp -> {
                    if (player.experienceLevel < xp.min()) {
                        return Result.fail("Нужно уровней опыта: " + xp.min() + " (есть " + player.experienceLevel + ")");
                    }
                }
                case CardRequirement.TableResources resources -> {
                    if (ctx.paperCount() < resources.paper()) {
                        return Result.fail("Нужно бумаги: " + resources.paper() + " (в слоте " + ctx.paperCount() + ")");
                    }
                    if (ctx.inkCharges() < resources.inkCharges()) {
                        return Result.fail("Нужно чернил: " + resources.inkCharges() + " (в чернильнице " + ctx.inkCharges() + ")");
                    }
                }
                case CardRequirement.Progress progress -> {
                    String reason = checkProgress(progress, cardDiscipline, ctx);
                    if (reason != null) {
                        return Result.fail(reason);
                    }
                }
                case CardRequirement.Never ignored -> {
                    return Result.fail("Карта отключена (never)");
                }
            }
        }
        return Result.OK;
    }

    /** Списывает всё, что помечено как расходуемое. Вызывать только после успешной проверки. */
    public static void consume(List<CardRequirement> requirements, TableContext ctx) {
        ServerPlayer player = ctx.player();

        for (CardRequirement requirement : requirements) {
            if (requirement instanceof CardRequirement.Item item && item.consume()) {
                remove(player, matcher(item), item.count());
            } else if (requirement instanceof CardRequirement.AnyItem anyItem && anyItem.consume()) {
                remove(player, tagMatcher(anyItem.tag()), anyItem.count());
            } else if (requirement instanceof CardRequirement.XpLevels xp && xp.consume()) {
                player.giveExperienceLevels(-xp.min());
            } else if (requirement instanceof CardRequirement.TableResources resources) {
                ctx.consumePaper(resources.paper());
                ctx.drainInk(resources.inkCharges());
            }
        }
    }

    @Nullable
    private static String checkProgress(CardRequirement.Progress progress, @Nullable String cardDiscipline,
                                        TableContext ctx) {
        TheoryState theory = ctx.theory();

        if (progress.minDisciplines() > 0) {
            long counted = ctx.type().disciplines().stream()
                    .filter(discipline -> theory.progress(discipline.id()) > 0)
                    .count();
            if (counted < progress.minDisciplines()) {
                return "Нужно дисциплин с прогрессом: " + progress.minDisciplines() + " (есть " + counted + ")";
            }
        }

        if (progress.minTotal() > 0) {
            int total = ctx.type().disciplines().stream()
                    .mapToInt(discipline -> theory.progress(discipline.id()))
                    .sum();
            if (total < progress.minTotal()) {
                return "Нужно суммарного прогресса: " + progress.minTotal() + "% (есть " + total + "%)";
            }
        }

        if (progress.minPercent() > 0) {
            String id = progress.discipline().orElse("");
            if (id.equals("self")) {
                id = cardDiscipline == null ? "" : cardDiscipline;
            }
            if (id.equals("random") || id.isEmpty()) {
                return null;
            }
            int current = theory.progress(id);
            if (current < progress.minPercent()) {
                return "Нужно прогресса в " + id + ": " + progress.minPercent() + "% (есть " + current + "%)";
            }
        }
        return null;
    }

    private static Predicate<ItemStack> matcher(CardRequirement.Item item) {
        if (item.item().isPresent()) {
            Item required = BuiltInRegistries.ITEM.get(item.item().get());
            return stack -> stack.is(required);
        }
        if (item.tag().isPresent()) {
            return tagMatcher(item.tag().get());
        }
        return stack -> false;
    }

    private static Predicate<ItemStack> tagMatcher(ResourceLocation tag) {
        TagKey<Item> key = TagKey.create(Registries.ITEM, tag);
        return stack -> stack.is(key);
    }

    private static String describe(CardRequirement.Item item) {
        if (item.item().isPresent()) {
            return new ItemStack(BuiltInRegistries.ITEM.get(item.item().get())).getHoverName().getString();
        }
        return item.tag().map(tag -> "#" + tag).orElse("предмет");
    }

    private static int count(ServerPlayer player, Predicate<ItemStack> matcher) {
        int total = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty() && matcher.test(stack)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static void remove(ServerPlayer player, Predicate<ItemStack> matcher, int count) {
        int left = count;
        for (ItemStack stack : player.getInventory().items) {
            if (left <= 0) {
                break;
            }
            if (stack.isEmpty() || !matcher.test(stack)) {
                continue;
            }
            int taken = Math.min(left, stack.getCount());
            stack.shrink(taken);
            left -= taken;
        }
        player.getInventory().setChanged();
    }
}
