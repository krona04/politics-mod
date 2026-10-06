package net.krona.politicsmod.client;

import net.krona.politicsmod.client.ui.NamePromptScreen;
import net.krona.politicsmod.network.FoundCityPayload;
import net.krona.politicsmod.network.ModNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/** Naming a new city at a City Stone. */
public class CityFoundationScreen extends NamePromptScreen {
    private final BlockPos pos;

    public CityFoundationScreen(BlockPos pos) {
        super(Component.translatable("gui.politicsmod.founding.title"),
                Component.translatable("gui.politicsmod.founding.header"),
                Component.translatable("gui.politicsmod.founding.name_hint"),
                Component.translatable("gui.politicsmod.founding.name_hint"),
                Component.translatable("gui.politicsmod.founding.build"));
        this.pos = pos;
    }

    @Override
    protected void submit(String name) {
        ModNetworking.toServer(new FoundCityPayload(pos, name));
    }
}
