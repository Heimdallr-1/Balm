package net.blay09.mods.balm.neoforge.network;

import net.blay09.mods.balm.api.Balm;
import net.blay09.mods.balm.api.menu.BalmMenuProvider;
import net.blay09.mods.balm.api.network.BalmNetworking;
import net.blay09.mods.balm.api.network.ClientboundMessageRegistration;
import net.blay09.mods.balm.api.network.MessageRegistration;
import net.blay09.mods.balm.api.network.ServerboundMessageRegistration;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.neoforge.network.handling.MainThreadPayloadHandler;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Function;

public class NeoForgeBalmNetworking implements BalmNetworking {

    private static final Logger logger = LoggerFactory.getLogger(NeoForgeBalmNetworking.class);

    private static class Registrations {
        private final String modId;
        private final Map<CustomPacketPayload.Type<? extends CustomPacketPayload>, MessageRegistration<RegistryFriendlyByteBuf, ? extends CustomPacketPayload>> playMessagesByType = new ConcurrentHashMap<>();
        private boolean optional;

        private Registrations(String modId) {
            this.modId = modId;
        }

        @SubscribeEvent
        public void registerPayloadHandlers(final RegisterPayloadHandlersEvent event) {
            var registrar = event.registrar(modId);
            if (optional) {
                registrar = registrar.optional();
            }
            for (final var entry : playMessagesByType.entrySet()) {
                final var messageRegistration = entry.getValue();
                if (messageRegistration instanceof ServerboundMessageRegistration<RegistryFriendlyByteBuf, ? extends CustomPacketPayload> serverboundMessageRegistration) {
                    registrar = playToServer(registrar, serverboundMessageRegistration);
                } else if (messageRegistration instanceof ClientboundMessageRegistration<RegistryFriendlyByteBuf, ? extends CustomPacketPayload> clientboundMessageRegistration) {
                    registrar = playToClient(registrar, clientboundMessageRegistration);
                }
            }
        }

        public void allowClientOnly() {
            optional = true;
        }

        public void allowServerOnly() {
            optional = true;
        }

        private <TPayload extends CustomPacketPayload> PayloadRegistrar playToServer(PayloadRegistrar registrar, ServerboundMessageRegistration<RegistryFriendlyByteBuf, TPayload> registration) {
            return registrar.playToServer(registration.getType(), registration.getCodec(), createPayloadHandler(registration));
        }

        private <TPayload extends CustomPacketPayload> PayloadRegistrar playToClient(PayloadRegistrar registrar, ClientboundMessageRegistration<RegistryFriendlyByteBuf, TPayload> registration) {
            return registrar.playToClient(registration.getType(), registration.getCodec(), createPayloadHandler(registration));
        }

        private <TBuffer extends FriendlyByteBuf, TPayload extends CustomPacketPayload> IPayloadHandler<TPayload> createPayloadHandler(ServerboundMessageRegistration<TBuffer, TPayload> serverboundMessageRegistration) {
            return new MainThreadPayloadHandler<>((payload, context) -> {
                replyContext = context;
                serverboundMessageRegistration.getHandler().accept((ServerPlayer) context.player(), payload);
                replyContext = null;
            });
        }

        private <TBuffer extends FriendlyByteBuf, TPayload extends CustomPacketPayload> IPayloadHandler<TPayload> createPayloadHandler(ClientboundMessageRegistration<TBuffer, TPayload> clientboundMessageRegistration) {
            return new MainThreadPayloadHandler<>((payload, context) -> {
                replyContext = context;
                clientboundMessageRegistration.getHandler().accept(context.player(), payload);
                replyContext = null;
            });
        }
    }

    private final Map<String, Registrations> registrations = new ConcurrentHashMap<>();

    private static IPayloadContext replyContext;

    @Override
    public void allowClientOnly(String modId) {
        getRegistrations(modId).allowClientOnly();
    }

    @Override
    public void allowServerOnly(String modId) {
        getRegistrations(modId).allowServerOnly();
    }

    @Override
    public void openGui(Player player, MenuProvider menuProvider) {
        if (player instanceof ServerPlayer serverPlayer) {
            if (menuProvider instanceof BalmMenuProvider<?> balmMenuProvider) {
                openGui(serverPlayer, balmMenuProvider);
            } else {
                serverPlayer.openMenu(menuProvider);
            }
        }
    }

    private <T> void openGui(ServerPlayer player, BalmMenuProvider<T> menuProvider) {
        player.openMenu(menuProvider, buf -> menuProvider.getCodec().encode(buf, menuProvider.getScreenOpeningData(player)));
    }

    @Override
    public <T extends CustomPacketPayload> void reply(T message) {
        if (replyContext == null) {
            throw new IllegalStateException("No context to reply to");
        }

        replyContext.reply(message);
    }

    @Override
    public <T extends CustomPacketPayload> void sendTo(Player player, T message) {
        if (player instanceof ServerPlayer serverPlayer) {
            PacketDistributor.sendToPlayer(serverPlayer, message);
        }
    }

    @Override
    public <T extends CustomPacketPayload> void sendToTracking(ServerLevel level, BlockPos pos, T message) {
        PacketDistributor.sendToPlayersTrackingChunk(level, new ChunkPos(pos), message);
    }

    @Override
    public <T extends CustomPacketPayload> void sendToTracking(Entity entity, T message) {
        PacketDistributor.sendToPlayersTrackingEntity(entity, message);
    }

    @Override
    public <T extends CustomPacketPayload> void sendToAll(MinecraftServer server, T message) {
        PacketDistributor.sendToAllPlayers(message);
    }

    @Override
    public <T extends CustomPacketPayload> void sendToServer(T message) {
        if (!Balm.getProxy().isConnectedToServer()) {
            logger.debug("Skipping message {} because we're not connected to a server", message);
            return;
        }

        PacketDistributor.sendToServer(message);
    }

    @Override
    public <T extends CustomPacketPayload> void registerClientboundPacket(CustomPacketPayload.Type<T> type, Class<T> clazz, BiConsumer<RegistryFriendlyByteBuf, T> encodeFunc, Function<RegistryFriendlyByteBuf, T> decodeFunc, BiConsumer<Player, T> handler) {
        final var messageRegistration = new ClientboundMessageRegistration<>(type, clazz, encodeFunc, decodeFunc, handler);
        final var registrations = getActiveRegistrations();
        registrations.playMessagesByType.put(type, messageRegistration);
    }

    @Override
    public <T extends CustomPacketPayload> void registerServerboundPacket(CustomPacketPayload.Type<T> type, Class<T> clazz, BiConsumer<RegistryFriendlyByteBuf, T> encodeFunc, Function<RegistryFriendlyByteBuf, T> decodeFunc, BiConsumer<ServerPlayer, T> handler) {
        final var messageRegistration = new ServerboundMessageRegistration<>(type, clazz, encodeFunc, decodeFunc, handler);
        final var registrations = getActiveRegistrations();
        registrations.playMessagesByType.put(type, messageRegistration);
    }

    public void register(String modId, IEventBus eventBus) {
        eventBus.register(getRegistrations(modId));
    }

    private Registrations getActiveRegistrations() {
        return getRegistrations(ModLoadingContext.get().getActiveNamespace());
    }

    private Registrations getRegistrations(String modId) {
        return registrations.computeIfAbsent(modId, it -> new Registrations(modId));
    }
}