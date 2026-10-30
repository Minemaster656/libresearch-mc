package com.minemaster.libresearch.theory;

import com.minemaster.libresearch.data.ModAttachments;
import net.minecraft.world.entity.player.Player;

/** Доступ к запасу знаний игрока через attachment. */
public final class PlayerKnowledge {
    private PlayerKnowledge() {
    }

    public static Knowledge get(Player player) {
        return player.getData(ModAttachments.KNOWLEDGE);
    }

    public static void set(Player player, Knowledge knowledge) {
        player.setData(ModAttachments.KNOWLEDGE, knowledge);
    }
}
