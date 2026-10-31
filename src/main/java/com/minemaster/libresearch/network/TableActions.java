package com.minemaster.libresearch.network;

import com.minemaster.libresearch.LibResearch;
import com.minemaster.libresearch.block.ResearchTableBlockEntity;
import com.minemaster.libresearch.menu.ResearchTableMenu;
import com.minemaster.libresearch.theory.TableContext;
import com.minemaster.libresearch.theory.TheoryEngine;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Выполнение действий из GUI на сервере. Проверяет, что игрок действительно стоит
 * у стола с открытым меню этого стола, и только потом вызывает движок.
 *
 * <p>Каждое действие пишется в лог: это единственный способ отличить «клик не дошёл»
 * от «сервер отказал» при отладке интерфейса.</p>
 */
public final class TableActions {
    private TableActions() {
    }

    public static void perform(@Nullable Player player, ModNetwork.TableActionPayload payload) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (!(serverPlayer.containerMenu instanceof ResearchTableMenu menu) || !menu.pos().equals(payload.pos())) {
            LibResearch.LOGGER.info("UI action {} отклонено: меню не от этого стола ({})",
                    payload.action(), payload.pos());
            return;
        }
        if (serverPlayer.distanceToSqr(Vec3.atCenterOf(payload.pos())) > 64.0) {
            LibResearch.LOGGER.info("UI action {} отклонено: игрок далеко от {}", payload.action(), payload.pos());
            return;
        }
        if (!(serverPlayer.serverLevel().getBlockEntity(payload.pos()) instanceof ResearchTableBlockEntity table)) {
            LibResearch.LOGGER.info("UI action {} отклонено: нет BlockEntity в {}", payload.action(), payload.pos());
            return;
        }

        TableContext context = TableContext.of(serverPlayer, table).orElse(null);
        if (context == null) {
            LibResearch.LOGGER.info("UI action {} отклонено: неизвестный тип стола {}", payload.action(),
                    table.getTableType());
            return;
        }

        TheoryEngine.Result result = switch (payload.action()) {
            case START -> TheoryEngine.start(context);
            case TOGGLE_BONUS -> TheoryEngine.toggleBonus(context, payload.arg());
            case DRAW -> TheoryEngine.draw(context);
            case PLAY -> TheoryEngine.play(context, payload.index());
            case DISCARD -> TheoryEngine.discard(context);
            case FINISH -> TheoryEngine.finish(context);
            case CLAIM -> TheoryEngine.claim(context, payload.arg());
            case CLAIM_ALL -> TheoryEngine.claimAll(context);
        };

        LibResearch.LOGGER.info("UI action {} {} -> {} ({})", payload.action(),
                payload.arg().isBlank() ? "" : payload.arg(),
                result.ok() ? "OK" : "ОТКАЗ", result.message().getString());

        if (!result.message().getString().isBlank()) {
            serverPlayer.displayClientMessage(result.message(), true);
        }
    }
}
