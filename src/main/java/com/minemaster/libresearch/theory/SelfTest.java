package com.minemaster.libresearch.theory;

import com.minemaster.libresearch.block.ResearchTableBlockEntity;
import com.minemaster.libresearch.registry.ModBlocks;
import com.minemaster.libresearch.registry.ModItems;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Сквозная проверка логики стола без GUI: ставит временный стол, играет
 * фейковым игроком сценарий «115% → 1 очко + 15% остатка», проверяет блокировку
 * старта до забора награды и забор предметов. Команда {@code /libresearch selftest},
 * только для операторов.
 */
public final class SelfTest {
    private SelfTest() {
    }

    public static int run(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        List<String> failures = new ArrayList<>();
        List<String> passed = new ArrayList<>();

        BlockPos pos = source.getPlayer() != null
                ? source.getPlayer().blockPosition().offset(2, 0, 0)
                : level.getSharedSpawnPos().above();
        for (int i = 0; i < 12 && !level.getBlockState(pos).canBeReplaced(); i++) {
            pos = pos.above();
        }

        level.setBlockAndUpdate(pos, ModBlocks.RESEARCH_TABLE.get().defaultBlockState());
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!(blockEntity instanceof ResearchTableBlockEntity table)) {
            source.sendFailure(Component.literal("SELFTEST: не удалось поставить тестовый стол"));
            return 0;
        }

        FakePlayer player = FakePlayerFactory.getMinecraft(level);
        player.getInventory().clearContent();
        PlayerKnowledge.set(player, Knowledge.EMPTY);
        TheoryState theory = table.getTheory();

        try {
            Optional<TableContext> optional = TableContext.of(player, table);
            if (optional.isEmpty()) {
                source.sendFailure(Component.literal("SELFTEST: тип стола не найден"));
                return 0;
            }
            TableContext ctx = optional.get();

            table.getInventory().setStackInSlot(ResearchTableBlockEntity.SLOT_PAPER, new ItemStack(Items.PAPER, 8));
            table.getInventory().setStackInSlot(ResearchTableBlockEntity.SLOT_INKWELL, new ItemStack(ModItems.INKWELL.get()));

            check(passed, failures, "старт теории", TheoryEngine.start(ctx).ok());
            check(passed, failures, "повторный старт запрещён", !TheoryEngine.start(ctx).ok());
            check(passed, failures, "стартовое вдохновение 5", theory.inspiration() == 5);

            TheoryEngine.Result draw = TheoryEngine.draw(ctx);
            check(passed, failures, "добор руки", draw.ok() && !theory.hand().isEmpty());
            check(passed, failures, "добор списал 1 бумагу", ctx.paperCount() == 7);
            check(passed, failures, "повторный добор при полной руке запрещён", !TheoryEngine.draw(ctx).ok());

            check(passed, failures, "сброс теории", TheoryEngine.discard(ctx).ok());
            check(passed, failures, "бумага после сброса не вернулась", ctx.paperCount() == 7);

            check(passed, failures, "новый старт после сброса", TheoryEngine.start(ctx).ok());
            check(passed, failures, "прогресс 115% задан",
                    TheoryEngine.debugSetProgress(ctx, "copper", 115).ok() && theory.progress("copper") == 115);

            check(passed, failures, "завершение при вдохновении > 0 запрещено", !TheoryEngine.finish(ctx).ok());

            TheoryEngine.debugSetInspiration(ctx, 0);
            check(passed, failures, "завершение при нуле вдохновения", TheoryEngine.finish(ctx).ok());
            check(passed, failures, "начислено 115 санти-очков",
                    PlayerKnowledge.get(player).centi("copper") == 115L);
            check(passed, failures, "теория очищена после завершения", !theory.isActive());

            check(passed, failures, "старт заблокирован незабранной наградой", !TheoryEngine.start(ctx).ok());

            int before = countItem(player, Items.COPPER_INGOT);
            check(passed, failures, "награда забрана", TheoryEngine.claim(ctx, "copper").ok());
            int got = countItem(player, Items.COPPER_INGOT) - before;
            check(passed, failures, "выдано 2 медных слитка (count_per_point = 2)", got == 2);
            check(passed, failures, "в запасе осталось 15 санти-очков",
                    PlayerKnowledge.get(player).centi("copper") == 15L);
            check(passed, failures, "старт снова разрешён", TheoryEngine.start(ctx).ok());

            // Бонусный блок: сканирование, выбор, стоимость, бонусная колода.
            TheoryEngine.discard(ctx);
            BlockPos bonusPos = pos.offset(0, 0, 2);
            var previousState = level.getBlockState(bonusPos);
            level.setBlockAndUpdate(bonusPos, net.minecraft.world.level.block.Blocks.CRAFTING_TABLE.defaultBlockState());
            try {
                var found = BonusScanner.scan(level, pos, ctx.type());
                check(passed, failures, "сканер нашёл верстак рядом",
                        found.contains(com.minemaster.libresearch.LibResearch.id("crafting_table")));
                check(passed, failures, "бонус выбирается",
                        TheoryEngine.toggleBonus(ctx, "libresearch:crafting_table").ok());
                check(passed, failures, "бонус отмечен выбранным",
                        theory.selectedBonuses().contains("libresearch:crafting_table"));
                check(passed, failures, "старт с бонусом", TheoryEngine.start(ctx).ok());
                check(passed, failures, "бонус списал 1 вдохновение",
                        theory.startInspiration() == 4 && theory.inspiration() == 4);
                check(passed, failures, "бонусная колода собрана: 3 карты", theory.bonusDeck().size() == 3);
                check(passed, failures, "выбор бонуса очищен после старта", theory.selectedBonuses().isEmpty());
                TheoryEngine.discard(ctx);
            } finally {
                level.setBlockAndUpdate(bonusPos, previousState);
            }
        } catch (RuntimeException exception) {
            failures.add("исключение: " + exception);
        } finally {
            theory.clearTheory();
            PlayerKnowledge.set(player, Knowledge.EMPTY);
            player.getInventory().clearContent();
            level.removeBlock(pos, false);
        }

        passed.forEach(line -> source.sendSuccess(() -> Component.literal("  ✓ " + line), false));
        failures.forEach(line -> source.sendFailure(Component.literal("  ✗ " + line)));
        if (failures.isEmpty()) {
            source.sendSuccess(() -> Component.literal("SELFTEST: OK — пройдено " + passed.size() + " проверок"), false);
            return 1;
        }
        source.sendFailure(Component.literal("SELFTEST: провалено " + failures.size() + " из "
                + (passed.size() + failures.size()) + " проверок"));
        return 0;
    }

    private static void check(List<String> passed, List<String> failures, String name, boolean condition) {
        if (condition) {
            passed.add(name);
        } else {
            failures.add(name);
        }
    }

    private static int countItem(FakePlayer player, net.minecraft.world.item.Item item) {
        int total = 0;
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is(item)) {
                total += stack.getCount();
            }
        }
        return total;
    }
}
