package com.minemaster.libresearch.theory;

import com.minemaster.libresearch.block.ResearchTableBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.Optional;

/** Поиск стола, на который смотрит игрок (для команд итерации 2, позже — для пакетов GUI). */
public final class Tables {
    public static final double REACH = 8.0;

    private Tables() {
    }

    public static Optional<ResearchTableBlockEntity> lookedAt(ServerPlayer player) {
        HitResult hit = player.pick(REACH, 0.0F, false);
        if (!(hit instanceof BlockHitResult blockHit)) {
            return Optional.empty();
        }
        BlockPos pos = blockHit.getBlockPos();
        BlockEntity blockEntity = player.level().getBlockEntity(pos);
        if (blockEntity instanceof ResearchTableBlockEntity table) {
            return Optional.of(table);
        }
        return Optional.empty();
    }
}
