package net.krona.politicsmod.events;

import net.krona.politicsmod.Politicsmod;
import net.krona.politicsmod.PoliticsManager;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

@EventBusSubscriber(modid = Politicsmod.MODID, bus = EventBusSubscriber.Bus.GAME)
public class ServerGameEvents {


    private static boolean isOurModBlock(BlockState state) {
        if (state == null) return false;
        ResourceLocation key = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return key.getNamespace().equals(Politicsmod.MODID);
    }

    private static void sendCreativeError(ServerPlayer player) {
        player.displayClientMessage(
                Component.translatable("message.politicsmod.only_creative").withStyle(ChatFormatting.RED),
                true
        );
    }

    @SubscribeEvent
    public static void onPlayerJoin(net.neoforged.neoforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (!player.getPersistentData().contains("politicsmod_welcome")) {

                player.sendSystemMessage(Component.translatable("message.politicsmod.welcome").withStyle(ChatFormatting.AQUA));
                player.sendSystemMessage(Component.translatable("message.politicsmod.welcome_hint").withStyle(ChatFormatting.GRAY));

                player.getPersistentData().putBoolean("politicsmod_welcome", true);
            }
        }
    }

    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        if (isOurModBlock(event.getLevel().getBlockState(event.getPos()))) {
            if (!player.isCreative()) {
                sendCreativeError(player);
                event.setCanceled(true);
                return;
            }
        }

        if (player.isCreative()) return;

        handleTerritoryProtection(event, player, event.getPos());
    }

    @SubscribeEvent
    public static void onLeftClick(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getLevel().isClientSide) return;
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        if (isOurModBlock(event.getLevel().getBlockState(event.getPos()))) {
            if (!player.isCreative()) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public static void onRegisterCommands(net.neoforged.neoforge.event.RegisterCommandsEvent event) {
        net.krona.politicsmod.commands.ModCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!(event.getPlayer() instanceof ServerPlayer player)) return;

        if (isOurModBlock(event.getState())) {
            if (!player.isCreative()) {
                sendCreativeError(player);
                event.setCanceled(true);
                return;
            }
        }

        if (player.isCreative()) return;

        PoliticsManager manager = PoliticsManager.get(player.level());
        if (manager == null) return;

        ChunkPos chunkPos = new ChunkPos(event.getPos());
        String chunkCountry = manager.getCountryAt(chunkPos);

        if (chunkCountry == null) return;

        String playerCountry = manager.getCountryByOwner(player.getUUID());

        if (!chunkCountry.equals(playerCountry)) {
            player.displayClientMessage(Component.translatable("message.politicsmod.cant_break").withStyle(ChatFormatting.RED), true);
            event.setCanceled(true);
        }
    }

    private static void handleTerritoryProtection(PlayerInteractEvent.RightClickBlock event, ServerPlayer player, net.minecraft.core.BlockPos pos) {
        PoliticsManager manager = PoliticsManager.get(player.level());
        if (manager == null) return;

        ChunkPos chunkPos = new ChunkPos(pos);
        String chunkCountry = manager.getCountryAt(chunkPos);

        if (chunkCountry == null) return;

        String playerCountry = manager.getCountryByOwner(player.getUUID());

        if (!chunkCountry.equals(playerCountry)) {
            player.displayClientMessage(Component.translatable("message.politicsmod.territory_owned", chunkCountry).withStyle(ChatFormatting.RED), true);
            event.setCanceled(true);
        }
    }
}