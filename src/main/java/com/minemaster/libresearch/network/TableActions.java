package com.minemaster.libresearch.network;

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
 */
public final class TableActions {
    private TableActions() {
    }

    public static void perform(@Nullable Player player, ModNetwork.TableActionPayload payload) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        if (!(serverPlayer.containerMenu instanceof ResearchTableMenu menu) || !menu.pos().equals(payload.pos())) {
            return;
        }
        if (serverPlayer.distanceToSqr(Vec3.atCenterOf(payload.pos())) > 64.0) {
            return;
        }
        if (!(serverPlayer.serverLevel().getBlockEntity(payload.pos()) instanceof ResearchTableBlockEntity table)) {
            return;
        }

        TableContext context = TableContext.of(serverPlayer, table).orElse(null);
        if (context == null) {
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

        if (!result.message().getString().isBlank()) {
            serverPlayer.displayClientMessage(result.message(), true);
        }
    }
}
