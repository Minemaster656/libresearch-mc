package com.minemaster.libresearch.client;

import com.minemaster.libresearch.content.ContentText;
import com.minemaster.libresearch.content.card.Card;
import com.minemaster.libresearch.content.card.CardEffect;
import com.minemaster.libresearch.content.card.CardRequirement;
import com.minemaster.libresearch.content.table.TableType;
import com.minemaster.libresearch.theory.DrawnCard;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;

/**
 * Тексты для тултипов карт: требования, эффекты и иконки.
 * Ключи перевода — {@code libresearch.card.*}; строки из данных идут через
 * {@link ContentText}, поэтому работают и как ключ, и как обычный текст.
 */
public final class CardText {
    private CardText() {
    }

    public static List<Component> lines(Card card, DrawnCard drawn, TableType type) {
        List<Component> lines = new ArrayList<>();
        String name = card.display().isBlank() ? drawn.card().toString() : card.display();
        lines.add(ContentText.display(name).copy().withStyle(ChatFormatting.BOLD));
        lines.add(Component.translatable("libresearch.card.price", drawn.cost())
                .withStyle(ChatFormatting.GRAY));

        if (!card.requires().isEmpty()) {
            lines.add(Component.translatable("libresearch.card.requires").withStyle(ChatFormatting.DARK_GRAY));
            for (CardRequirement requirement : card.requires()) {
                lines.add(requirementLine(requirement));
            }
        }

        if (!card.effects().isEmpty()) {
            lines.add(Component.translatable("libresearch.card.effects").withStyle(ChatFormatting.DARK_GRAY));
            for (int index = 0; index < card.effects().size(); index++) {
                lines.add(effectLine(card.effects().get(index), drawn, index, type));
            }
        }
        return lines;
    }

    public static Component requirementLine(CardRequirement requirement) {
        return switch (requirement) {
            case CardRequirement.Item item -> {
                Component name = item.item().map(CardText::itemName)
                        .orElse(Component.literal("#" + item.tag().map(ResourceLocation::toString).orElse("?")));
                yield Component.translatable(item.consume()
                                ? "libresearch.card.req.item_consumed" : "libresearch.card.req.item",
                                item.count(), name)
                        .withStyle(item.consume() ? ChatFormatting.GOLD : ChatFormatting.GRAY);
            }
            case CardRequirement.AnyItem any -> Component.translatable(any.consume()
                            ? "libresearch.card.req.tag_consumed" : "libresearch.card.req.tag",
                            any.count(), any.tag().toString())
                    .withStyle(any.consume() ? ChatFormatting.GOLD : ChatFormatting.GRAY);
            case CardRequirement.XpLevels xp -> Component.translatable(xp.consume()
                            ? "libresearch.card.req.xp_consumed" : "libresearch.card.req.xp",
                            xp.min())
                    .withStyle(xp.consume() ? ChatFormatting.GOLD : ChatFormatting.GRAY);
            case CardRequirement.TableResources resources -> Component.translatable(
                            "libresearch.card.req.resources", resources.paper(), resources.inkCharges())
                    .withStyle(ChatFormatting.GRAY);
            case CardRequirement.Progress progress -> progressLine(progress);
            case CardRequirement.Never ignored -> Component.translatable("libresearch.card.req.never")
                    .withStyle(ChatFormatting.DARK_RED);
        };
    }

    private static Component progressLine(CardRequirement.Progress progress) {
        MutableComponent line = Component.translatable("libresearch.card.req.progress").copy();
        if (progress.minPercent() > 0) {
            line.append(Component.translatable("libresearch.card.req.progress.percent", progress.minPercent()));
        }
        if (progress.minTotal() > 0) {
            line.append(Component.translatable("libresearch.card.req.progress.total", progress.minTotal()));
        }
        if (progress.minDisciplines() > 0) {
            line.append(Component.translatable("libresearch.card.req.progress.disciplines",
                    progress.minDisciplines()));
        }
        progress.discipline().ifPresent(id -> line.append(Component.translatable(
                "libresearch.card.req.progress.in", id)));
        return line.withStyle(ChatFormatting.GRAY);
    }

    public static Component effectLine(CardEffect effect, DrawnCard drawn, int index, TableType type) {
        return switch (effect) {
            case CardEffect.Progress ignored -> Component.translatable("libresearch.card.effect.progress",
                            drawn.roll(index), targetLabel(drawn.target(index), type))
                    .withStyle(ChatFormatting.GREEN);
            case CardEffect.ProgressDrain drain -> Component.translatable("libresearch.card.effect.drain",
                            drawn.roll(index),
                            drain.from().isEmpty()
                                    ? Component.translatable("libresearch.card.target.all")
                                    : Component.literal(String.join(", ", drain.from())))
                    .withStyle(ChatFormatting.RED);
            case CardEffect.Inspiration ignored -> Component.translatable("libresearch.card.effect.inspiration",
                            drawn.roll(index))
                    .withStyle(ChatFormatting.AQUA);
            case CardEffect.BonusDraws ignored -> Component.translatable("libresearch.card.effect.bonus_draws",
                            drawn.roll(index))
                    .withStyle(ChatFormatting.AQUA);
            case CardEffect.FullValueSlots ignored -> Component.translatable(
                            "libresearch.card.effect.full_value", drawn.roll(index))
                    .withStyle(ChatFormatting.AQUA);
            case CardEffect.BlockDiscipline ignored -> Component.translatable("libresearch.card.effect.block",
                            targetLabel(drawn.target(index), type))
                    .withStyle(ChatFormatting.DARK_RED);
            case CardEffect.GiveItem giveItem -> Component.translatable("libresearch.card.effect.give_item",
                            drawn.roll(index), itemName(giveItem.item()))
                    .withStyle(ChatFormatting.GOLD);
            case CardEffect.ConsumeXp xp -> Component.translatable(xp.all()
                            ? "libresearch.card.effect.xp_all" : "libresearch.card.effect.xp",
                            drawn.roll(index))
                    .withStyle(ChatFormatting.GRAY);
            case CardEffect.Paper ignored -> Component.translatable("libresearch.card.effect.paper",
                            drawn.roll(index))
                    .withStyle(ChatFormatting.GRAY);
            case CardEffect.InkCharges ignored -> Component.translatable("libresearch.card.effect.ink",
                            drawn.roll(index))
                    .withStyle(ChatFormatting.GRAY);
        };
    }

    /** Иконка требования для карточки; пустой стек, если показывать нечего. */
    public static ItemStack icon(CardRequirement requirement, Item paper) {
        return switch (requirement) {
            case CardRequirement.Item item -> item.item().map(CardText::stack)
                    .orElse(ItemStack.EMPTY);
            case CardRequirement.AnyItem any -> firstInTag(any.tag());
            case CardRequirement.XpLevels ignored -> new ItemStack(Items.EXPERIENCE_BOTTLE);
            case CardRequirement.TableResources resources -> new ItemStack(
                    resources.paper() > 0 ? paper : Items.AIR);
            case CardRequirement.Progress ignored -> new ItemStack(Items.BOOK);
            case CardRequirement.Never ignored -> new ItemStack(Items.BARRIER);
        };
    }

    /** Подпись цели эффекта: спец-значения переводятся, дисциплина — из данных. */
    public static Component targetLabel(String target, TableType type) {
        if (target == null || target.isBlank()) {
            return Component.translatable("libresearch.card.target.none");
        }
        return switch (target) {
            case "random" -> Component.translatable("libresearch.card.target.random");
            case "highest" -> Component.translatable("libresearch.card.target.highest");
            case "lowest" -> Component.translatable("libresearch.card.target.lowest");
            case "all_equalize" -> Component.translatable("libresearch.card.target.equalize");
            default -> type.disciplines().stream()
                    .filter(discipline -> discipline.id().equals(target))
                    .map(discipline -> ContentText.display(discipline.display()))
                    .findFirst().orElse(ContentText.display(target));
        };
    }

    private static Component itemName(ResourceLocation location) {
        return new ItemStack(BuiltInRegistries.ITEM.get(location)).getHoverName();
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
