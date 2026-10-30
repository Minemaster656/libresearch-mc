package com.minemaster.libresearch.data;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.List;

/** Команды мода: {@code /libresearch validate}. */
public final class LibCommands {
    private LibCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("libresearch")
                .then(Commands.literal("validate").executes(context -> validate(context.getSource()))));
    }

    private static int validate(CommandSourceStack source) {
        List<DataValidation.Problem> problems = DataValidation.validate();
        if (problems.isEmpty()) {
            source.sendSuccess(() -> Component.literal("LibResearch: OK — " + DataValidation.summary())
                    .withStyle(ChatFormatting.GREEN), false);
            return 1;
        }

        source.sendFailure(Component.literal("LibResearch: найдено проблем — " + problems.size())
                .withStyle(ChatFormatting.RED));
        problems.stream().limit(30)
                .forEach(problem -> source.sendFailure(Component.literal(" • " + problem)));
        if (problems.size() > 30) {
            source.sendFailure(Component.literal(" … и ещё " + (problems.size() - 30)));
        }
        return 0;
    }
}
