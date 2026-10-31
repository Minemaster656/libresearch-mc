package com.minemaster.libresearch.network;

import com.minemaster.libresearch.LibResearch;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/** Сетевые пакеты: только клиент → сервер, состояние стола идёт через BlockEntity. */
public final class ModNetwork {
    private ModNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(TableActionPayload.TYPE, TableActionPayload.STREAM_CODEC, TableActionPayload::handle);
    }

    /** Действие игрока со столом. {@code arg} — дисциплина или id бонуса. */
    public record TableActionPayload(BlockPos pos, Action action, int index, String arg)
            implements CustomPacketPayload {
        public static final Type<TableActionPayload> TYPE = new Type<>(LibResearch.id("table_action"));

        public static final StreamCodec<RegistryFriendlyByteBuf, TableActionPayload> STREAM_CODEC =
                StreamCodec.composite(
                        BlockPos.STREAM_CODEC, TableActionPayload::pos,
                        ByteBufCodecs.VAR_INT, payload -> payload.action().ordinal(),
                        ByteBufCodecs.VAR_INT, TableActionPayload::index,
                        ByteBufCodecs.STRING_UTF8, TableActionPayload::arg,
                        (pos, action, index, arg) -> new TableActionPayload(pos,
                                Action.values()[Math.floorMod(action, Action.values().length)], index, arg));

        public TableActionPayload(BlockPos pos, Action action) {
            this(pos, action, 0, "");
        }

        public TableActionPayload(BlockPos pos, Action action, int index) {
            this(pos, action, index, "");
        }

        public TableActionPayload(BlockPos pos, Action action, String arg) {
            this(pos, action, 0, arg);
        }

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }

        @SuppressWarnings("unused")
        public static void handle(TableActionPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext context) {
            context.enqueueWork(() -> TableActions.perform(context.player(), payload));
        }
    }

    public enum Action {
        START,
        TOGGLE_BONUS,
        DRAW,
        PLAY,
        DISCARD,
        FINISH,
        CLAIM,
        CLAIM_ALL
    }
}
