package com.minemaster.libresearch.client;

import com.minemaster.libresearch.content.card.Card;
import com.minemaster.libresearch.content.card.CardEffect;
import com.minemaster.libresearch.content.card.CardRequirement;
import com.minemaster.libresearch.content.table.TableType;
import com.minemaster.libresearch.theory.DrawnCard;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;

/** Тексты для тултипов карт: требования, эффекты и иконки. */
public final class CardText {
    private CardText() {
    }

    public static List<Component> lines(Card card, DrawnCard drawn, TableType type) {
        List<Component> lines = new ArrayList<>();
        String name = card.display().isBlank() ? drawn.card().toString() : card.display();
        lines.add(Component.literal(name).withStyle(ChatFormatting.BOLD));
        lines.add(Component.literal("Цена: " + drawn.cost() + " вдохновения")
                .withStyle(ChatFormatting.GRAY));

        if (!card.requires().isEmpty()) {
            lines.add(Component.literal("Требуется:").withStyle(ChatFormatting.DARK_GRAY));
            for (CardRequirement requirement : card.requires()) {
                lines.add(requirementLine(requirement));
            }
        }

        if (!card.effects().isEmpty()) {
            lines.add(Component.literal("Эффект:").withStyle(ChatFormatting.DARK_GRAY));
            for (int index = 0; index < card.effects().size(); index++) {
                lines.add(effectLine(card.effects().get(index), drawn, index, type));
            }
        }
        return lines;
    }

    public static Component requirementLine(CardRequirement requirement) {
        return switch (requirement) {
            case CardRequirement.Item item -> {
                String name = item.item().map(CardText::itemName)
                        .orElse("#" + item.tag().map(ResourceLocation::toString).orElse("?"));
                yield Component.literal("• " + item.count() + " × " + name
                                + (item.consume() ? " (тратится)" : ""))
                        .withStyle(item.consume() ? ChatFormatting.GOLD : ChatFormatting.GRAY);
            }
            case CardRequirement.AnyItem any -> Component.literal("• " + any.count() + " × любой #"
                            + any.tag() + (any.consume() ? " (тратится)" : ""))
                    .withStyle(any.consume() ? ChatFormatting.GOLD : ChatFormatting.GRAY);
            case CardRequirement.XpLevels xp -> Component.literal("• опыт: " + xp.min() + " ур."
                            + (xp.consume() ? " (списывается)" : ""))
                    .withStyle(xp.consume() ? ChatFormatting.GOLD : ChatFormatting.GRAY);
            case CardRequirement.TableResources resources -> Component.literal("• бумага: "
                            + resources.paper() + ", чернила: " + resources.inkCharges())
                    .withStyle(ChatFormatting.GRAY);
            case CardRequirement.Progress progress -> Component.literal("• прогресс: "
                            + (progress.minPercent() > 0 ? "≥" + progress.minPercent() + "% " : "")
                            + (progress.minTotal() > 0 ? "сумма ≥" + progress.minTotal() + " " : "")
                            + (progress.minDisciplines() > 0 ? "дисциплин ≥" + progress.minDisciplines() : ""))
                    .withStyle(ChatFormatting.GRAY);
            case CardRequirement.Never ignored -> Component.literal("• недоступно")
                    .withStyle(ChatFormatting.DARK_RED);
        };
    }

    public static Component effectLine(CardEffect effect, DrawnCard drawn, int index, TableType type) {
        return switch (effect) {
            case CardEffect.Progress progress -> Component.literal("• +" + drawn.roll(index)
                            + "% → " + targetLabel(drawn.target(index), type))
                    .withStyle(ChatFormatting.GREEN);
            case CardEffect.ProgressDrain drain -> Component.literal("• −" + drawn.roll(index)
                            + "% из " + (drain.from().isEmpty() ? "всех" : String.join(", ", drain.from())))
                    .withStyle(ChatFormatting.RED);
            case CardEffect.Inspiration ignored -> Component.literal("• +" + drawn.roll(index)
                            + " вдохновения")
                    .withStyle(ChatFormatting.AQUA);
            case CardEffect.BonusDraws ignored -> Component.literal("• +" + drawn.roll(index)
                            + " бонусный добор")
                    .withStyle(ChatFormatting.AQUA);
            case CardEffect.FullValueSlots ignored -> Component.literal("• +" + drawn.roll(index)
                            + " к полному зачёту")
                    .withStyle(ChatFormatting.AQUA);
            case CardEffect.BlockDiscipline ignored -> Component.literal("• блокирует: "
                            + targetLabel(drawn.target(index), type))
                    .withStyle(ChatFormatting.DARK_RED);
            case CardEffect.GiveItem giveItem -> Component.literal("• выдаёт " + drawn.roll(index)
                            + " × " + itemName(giveItem.item()))
                    .withStyle(ChatFormatting.GOLD);
            case CardEffect.ConsumeXp xp -> Component.literal("• тратит опыт: "
                            + (xp.all() ? "все уровни" : drawn.roll(index) + " ур."))
                    .withStyle(ChatFormatting.GRAY);
            case CardEffect.Paper ignored -> Component.literal("• тратит " + drawn.roll(index)
                            + " бумаги")
                    .withStyle(ChatFormatting.GRAY);
            case CardEffect.InkCharges ignored -> Component.literal("• тратит " + drawn.roll(index)
                            + " чернил")
                    .withStyle(ChatFormatting.GRAY);
        };
    }

    /** Иконка требования для карточки; пустой стек, если показывать нечего. */
    public static ItemStack icon(CardRequirement requirement) {
        return switch (requirement) {
            case CardRequirement.Item item -> item.item().map(CardText::stack)
                    .orElse(ItemStack.EMPTY);
            case CardRequirement.AnyItem any -> firstInTag(any.tag());
            case CardRequirement.XpLevels ignored -> new ItemStack(Items.EXPERIENCE_BOTTLE);
            case CardRequirement.TableResources resources -> new ItemStack(
                    resources.paper() > 0 ? Items.PAPER : Items.AIR);
            case CardRequirement.Progress ignored -> new ItemStack(Items.BOOK);
            case CardRequirement.Never ignored -> new ItemStack(Items.BARRIER);
        };
    }

    public static String targetLabel(String target, TableType type) {
        if (target == null || target.isBlank()) {
            return "—";
        }
        return switch (target) {
            case "random" -> "случайная дисциплина";
            case "highest" -> "наибольшая дисциплина";
            case "lowest" -> "наименьшая дисциплина";
            case "all_equalize" -> "все дисциплины (выравнивание)";
            default -> type.disciplines().stream()
                    .filter(discipline -> discipline.id().equals(target))
                    .map(TableType.Discipline::display)
                    .findFirst().orElse(target);
        };
    }

    private static String itemName(ResourceLocation location) {
        return new ItemStack(BuiltInRegistries.ITEM.get(location)).getHoverName().getString();
    }

    private static ItemStack stack(ResourceLocation location) {
        var item = BuiltInRegistries.ITEM.get(location);
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
    }

    private static ItemStack firstInTag(ResourceLocation tagId) {
        var tag = BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, tagId));
        if (tag.isEmpty() || tag.get().size() == 0) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(tag.get().get(0).value());
    }

    /** Иконка блока по id (для списка бонусов). */
    public static ItemStack blockIcon(ResourceLocation id) {
        Block block = BuiltInRegistries.BLOCK.get(id);
        return new ItemStack(block.asItem());
    }
}
