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

    /** Ближайший стол в радиусе; используется командами, когда игрок никуда не смотрит. */
    public static Optional<ResearchTableBlockEntity> nearest(ServerPlayer player, double radius) {
        BlockPos center = player.blockPosition();
        int r = (int) Math.ceil(radius);
        double best = Double.MAX_VALUE;
        ResearchTableBlockEntity found = null;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -4, -r), center.offset(r, 4, r))) {
            if (player.level().getBlockEntity(pos) instanceof ResearchTableBlockEntity table) {
                double distance = player.distanceToSqr(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
                if (distance <= radius * radius && distance < best) {
                    best = distance;
                    found = table;
                }
            }
        }
        return Optional.ofNullable(found);
    }

    /** Сначала стол, на который смотрит игрок, иначе — ближайший. */
    public static Optional<ResearchTableBlockEntity> lookedAtOrNearest(ServerPlayer player, double radius) {
        return lookedAt(player).or(() -> nearest(player, radius));
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
