package net.blay09.mods.balm.api.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.BiConsumer;
import java.util.function.Function;

public class ServerboundMessageRegistration<TBuffer extends FriendlyByteBuf, TPayload extends CustomPacketPayload> extends MessageRegistration<TBuffer, TPayload> {

    private final BiConsumer<ServerPlayer, TPayload> handler;

    public ServerboundMessageRegistration(CustomPacketPayload.Type<TPayload> type, Class<TPayload> clazz, BiConsumer<TBuffer, TPayload> encodeFunc, Function<TBuffer, TPayload> decodeFunc, BiConsumer<ServerPlayer, TPayload> handler) {
        super(type, clazz, encodeFunc, decodeFunc);
        this.handler = handler;
    }

    public BiConsumer<ServerPlayer, TPayload> getHandler() {
        return handler;
    }
}
