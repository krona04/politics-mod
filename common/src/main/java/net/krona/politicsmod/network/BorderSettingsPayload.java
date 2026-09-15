package net.krona.politicsmod.network;

import dev.architectury.networking.NetworkManager;
import io.netty.buffer.ByteBuf;
import net.krona.politicsmod.Politicsmod;
import net.krona.politicsmod.client.ClientBorderSettings;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server -> client: toggle border rendering or cycle its style.
 * /politicsmod borders is a server command on purpose: a client-side command with the
 * same "politicsmod" root makes Fabric swallow every other /politicsmod command.
 */
public record BorderSettingsPayload(int action) implements CustomPacketPayload {

    public static final int TOGGLE = 0;
    public static final int CYCLE_STYLE = 1;

    public static final Type<BorderSettingsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(Politicsmod.MODID, "border_settings"));

    public static final StreamCodec<ByteBuf, BorderSettingsPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, BorderSettingsPayload::action,
            BorderSettingsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(final BorderSettingsPayload payload, final NetworkManager.PacketContext context) {
        context.queue(() -> {
            if (payload.action() == CYCLE_STYLE) {
                ClientBorderSettings.cycleStyle();
            } else {
                ClientBorderSettings.toggle();
            }
        });
    }
}
