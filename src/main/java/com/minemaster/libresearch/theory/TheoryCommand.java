package com.minemaster.libresearch.theory;

import com.minemaster.libresearch.block.ResearchTableBlockEntity;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;
import java.util.function.Function;

/**
 * Команды итерации 2: играть без GUI. Применяются к столу, на который смотрит
 * игрок. Когда появится GUI, оно будет вызывать те же методы {@link TheoryEngine}.
 */
public final class TheoryCommand {
    private TheoryCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> tree() {
        return Commands.literal("theory")
                .then(Commands.literal("start")
                        .executes(command -> run(command, TheoryEngine::start)))
                .then(Commands.literal("draw")
                        .executes(command -> run(command, TheoryEngine::draw)))
                .then(Commands.literal("play")
                        .then(Commands.argument("index", IntegerArgumentType.integer(1, 3))
                                .executes(command -> run(command, ctx -> TheoryEngine.play(
                                        ctx, IntegerArgumentType.getInteger(command, "index") - 1)))))
                .then(Commands.literal("finish")
                        .executes(command -> run(command, TheoryEngine::finish)))
                .then(Commands.literal("discard")
                        .executes(command -> run(command, TheoryEngine::discard)))
                .then(Commands.literal("status")
                        .executes(command -> run(command, TheoryEngine::status)))
                .then(Commands.literal("claim")
                        .then(Commands.argument("discipline", StringArgumentType.word())
                                .executes(command -> run(command, ctx -> TheoryEngine.claim(
                                        ctx, StringArgumentType.getString(command, "discipline"))))))
                .then(Commands.literal("claimall")
                        .executes(command -> run(command, TheoryEngine::claimAll)))
                // Отладочные подкоманды для сценариев без GUI.
                .then(Commands.literal("set")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("discipline", StringArgumentType.word())
                                .then(Commands.argument("percent", IntegerArgumentType.integer(0, 10_000))
                                        .executes(command -> run(command, ctx -> TheoryEngine.debugSetProgress(
                                                ctx,
                                                StringArgumentType.getString(command, "discipline"),
                                                IntegerArgumentType.getInteger(command, "percent")))))))
                .then(Commands.literal("inspiration")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("value", IntegerArgumentType.integer(0, 100))
                                .executes(command -> run(command, ctx -> TheoryEngine.debugSetInspiration(
                                        ctx, IntegerArgumentType.getInteger(command, "value"))))));
    }

    private static int run(CommandContext<CommandSourceStack> command,
                           Function<TableContext, TheoryEngine.Result> action) {
        CommandSourceStack source = command.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.literal("Команда работает только для игрока."));
            return 0;
        }

        Optional<ResearchTableBlockEntity> table = Tables.lookedAtOrNearest(player, Tables.REACH);
        if (table.isEmpty()) {
            source.sendFailure(Component.literal("Посмотрите на стол исследований (до 8 блоков)."));
            return 0;
        }

        Optional<TableContext> context = TableContext.of(player, table.get());
        if (context.isEmpty()) {
            source.sendFailure(Component.literal("Неизвестный тип стола: " + table.get().getTableType()));
            return 0;
        }

        TheoryEngine.Result result = action.apply(context.get());
        if (result.ok()) {
            source.sendSuccess(result::message, false);
            return 1;
        }
        source.sendFailure(result.message());
        return 0;
    }

    /** Печатает запас знаний игрока. */
    public static int knowledge(CommandContext<CommandSourceStack> command) {
        ServerPlayer player = command.getSource().getPlayer();
        if (player == null) {
            command.getSource().sendFailure(Component.literal("Команда работает только для игрока."));
            return 0;
        }
        Knowledge knowledge = PlayerKnowledge.get(player);
        if (knowledge.centi().isEmpty()) {
            command.getSource().sendSuccess(() -> Component.literal("Знаний пока нет."), false);
            return 1;
        }
        String text = String.join(", ", knowledge.centi().entrySet().stream()
                .sorted((a, b) -> a.getKey().compareTo(b.getKey()))
                .map(entry -> entry.getKey() + " " + entry.getValue() + " санти ("
                        + knowledge.points(entry.getKey()) + " очк.)")
                .toList());
        command.getSource().sendSuccess(() -> Component.literal("Знания: " + text), false);
        return 1;
    }
}
