package net.krona.politicsmod.client;

import net.krona.politicsmod.network.EventNoticePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/** A toast and a bell when an event of the player's country waits for an answer (0.5.1). */
public final class ClientEventNotice {

    private ClientEventNotice() {
    }

    public static void show(EventNoticePayload p) {
        Minecraft mc = Minecraft.getInstance();
        Component title = Component.translatable("gui.politicsmod.event_toast.title");
        Component message = Component.translatable("gui.politicsmod.event_toast.message",
                Component.translatable(p.title(), p.country(), p.city()), ModKeyMappings.OPEN_COUNTRY_MENU.getTranslatedKeyMessage());
        SystemToast.addOrUpdate(mc.getToasts(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION, title, message);
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL.value(), 1.0f, 0.8f));
        ClientPoliticsData.pendingEvents = Math.max(ClientPoliticsData.pendingEvents, p.count());
    }
}
