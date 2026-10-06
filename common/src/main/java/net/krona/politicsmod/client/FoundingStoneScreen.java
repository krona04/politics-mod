package net.krona.politicsmod.client;

import net.krona.politicsmod.client.ui.NamePromptScreen;
import net.krona.politicsmod.network.CreateCountryPayload;
import net.krona.politicsmod.network.ModNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

/** Naming a new country at a Founding Stone. */
public class FoundingStoneScreen extends NamePromptScreen {
    private final BlockPos blockPos;

    public FoundingStoneScreen(BlockPos pos) {
        super(Component.translatable("gui.politicsmod.founding_state.title"),
                Component.translatable("gui.politicsmod.founding_state.header"),
                Component.translatable("gui.politicsmod.founding_state.name_label"),
                Component.translatable("gui.politicsmod.founding_state.name_hint"),
                Component.translatable("gui.politicsmod.founding_state.create"));
        this.blockPos = pos;
    }

    @Override
    protected void submit(String name) {
        // The server sends the real borders; nothing is drawn until it accepts the name and the land
        ModNetworking.toServer(new CreateCountryPayload(blockPos, name));
    }
}
