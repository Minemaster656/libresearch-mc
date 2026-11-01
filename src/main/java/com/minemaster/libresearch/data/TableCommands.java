package com.minemaster.libresearch.data;

import com.minemaster.libresearch.LibResearch;
import com.minemaster.libresearch.block.ResearchTableBlockEntity;
import com.minemaster.libresearch.item.Papers;
import com.minemaster.libresearch.menu.ResearchTableMenu;
import com.minemaster.libresearch.registry.ModBlocks;
import com.minemaster.libresearch.registry.ModItems;
import com.minemaster.libresearch.theory.Tables;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Отладочные команды для быстрой проверки в игре:
 * {@code /libresearch open} открывает ближайший стол,
 * {@code /libresearch prepare [debug]} ставит готовый стол рядом с игроком.
 */
public final class TableCommands {
    private TableCommands() {
    }

    public static int open(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        ResearchTableBlockEntity table = Tables.lookedAtOrNearest(player, Tables.REACH)
                .orElse(null);
        if (table == null) {
            source.sendFailure(Component.literal("Рядом нет стола исследований"));
            return 0;
        }
        openMenu(player, table);
        return 1;
    }

    public static int prepare(CommandContext<CommandSourceStack> context, boolean debug)
            throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        Level level = player.level();

        BlockPos pos = player.blockPosition().relative(player.getDirection(), 2);
        var block = debug ? ModBlocks.DEBUG_TABLE.get() : ModBlocks.RESEARCH_TABLE.get();
        level.setBlockAndUpdate(pos, block.defaultBlockState());
        if (!(level.getBlockEntity(pos) instanceof ResearchTableBlockEntity table)) {
            source.sendFailure(Component.literal("Не удалось поставить стол"));
            return 0;
        }
        table.setTableType(LibResearch.id(debug ? "debug" : "research"));
        table.getInventory().setStackInSlot(ResearchTableBlockEntity.SLOT_INKWELL,
                new ItemStack(ModItems.INKWELL.get()));
        table.getInventory().setStackInSlot(ResearchTableBlockEntity.SLOT_PAPER,
                new ItemStack(Papers.INSTANCE.itemForType(table.getTableType()), 32));
        table.markUpdated();

        source.sendSuccess(() -> Component.literal("Стол готов: " + pos.toShortString()
                + " (тип " + table.getTableType() + ")"), false);

        // Меню открываем через два тика: клиент должен успеть получить BlockEntity,
        // иначе экран откроется с пустым (старым) состоянием.
        ServerPlayer viewer = player;
        player.server.tell(new net.minecraft.server.TickTask(
                player.server.getTickCount() + 2, () -> openMenu(viewer, table)));
        return 1;
    }

    private static void openMenu(ServerPlayer player, ResearchTableBlockEntity table) {
        player.openMenu(new SimpleMenuProvider(
                        (id, inventory, p) -> new ResearchTableMenu(id, inventory, table),
                        Component.translatable("container.libresearch.research_table")),
                buffer -> buffer.writeBlockPos(table.getBlockPos()));
    }
}
