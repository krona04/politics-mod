package net.krona.politicsmod;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.krona.politicsmod.network.SyncChunkPayload;
import net.krona.politicsmod.politics.Country;
import net.krona.politicsmod.politics.CountryRole;
import net.krona.politicsmod.politics.DiplomacyStatus;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = Politicsmod.MODID)
public class ModCommands {

    private static final Map<String, String[]> pendingDiplomacy = new HashMap<>();
    private static final Map<Long, String[]>   pendingBuyOffers = new HashMap<>();

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("politicsmod")

            .then(Commands.literal("claim")
                .executes(ctx -> claimSingleChunk(ctx, null))
                .then(Commands.argument("cityName", StringArgumentType.greedyString())
                    .executes(ctx -> claimSingleChunk(ctx, StringArgumentType.getString(ctx, "cityName")))))

            .then(Commands.literal("claim_radius")
                .then(Commands.argument("radius", IntegerArgumentType.integer(1, 5))
                    .executes(ctx -> claimRadius(ctx, IntegerArgumentType.getInteger(ctx, "radius"), null))
                    .then(Commands.argument("cityName", StringArgumentType.greedyString())
                        .executes(ctx -> claimRadius(ctx, IntegerArgumentType.getInteger(ctx, "radius"),
                                StringArgumentType.getString(ctx, "cityName"))))))

            .then(Commands.literal("rename")
                .then(Commands.literal("country")
                    .then(Commands.argument("newName", StringArgumentType.greedyString())
                        .executes(ctx -> renameCountry(ctx, StringArgumentType.getString(ctx, "newName"))))))

            .then(Commands.literal("invite")
                .then(Commands.argument("target", EntityArgument.player())
                    .executes(ctx -> invitePlayer(ctx))))

            .then(Commands.literal("kick")
                .then(Commands.argument("target", EntityArgument.player())
                    .executes(ctx -> kickPlayer(ctx))))

            .then(Commands.literal("setrole")
                .then(Commands.argument("target", EntityArgument.player())
                    .then(Commands.argument("role", StringArgumentType.word())
                        .executes(ctx -> changePlayerRole(ctx)))))

            .then(Commands.literal("info")
                .executes(ctx -> showCountryInfo(ctx)))

            .then(Commands.literal("pay")
                .then(Commands.argument("country", StringArgumentType.word())
                    .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                        .executes(ctx -> payCountry(ctx,
                                StringArgumentType.getString(ctx, "country"),
                                IntegerArgumentType.getInteger(ctx, "amount"))))))

            .then(Commands.literal("tax")
                .then(Commands.literal("federal")
                    .then(Commands.argument("rate", IntegerArgumentType.integer(0, 99))
                        .executes(ctx -> setFederalTax(ctx, IntegerArgumentType.getInteger(ctx, "rate")))))
                .then(Commands.literal("info")
                    .executes(ctx -> showTaxInfo(ctx))))

            .then(Commands.literal("city")
                .then(Commands.literal("setmayor")
                    .then(Commands.argument("cityName", StringArgumentType.word())
                        .then(Commands.argument("player", EntityArgument.player())
                            .executes(ctx -> setCityMayorCmd(ctx,
                                    StringArgumentType.getString(ctx, "cityName"))))))
                .then(Commands.literal("info")
                    .executes(ctx -> showCityInfo(ctx, null))
                    .then(Commands.argument("cityName", StringArgumentType.greedyString())
                        .executes(ctx -> showCityInfo(ctx, StringArgumentType.getString(ctx, "cityName")))))
                .then(Commands.literal("secede")
                    .executes(ctx -> declareSecession(ctx))))

            .then(Commands.literal("territory")
                .then(Commands.literal("offer")
                    .then(Commands.argument("price", IntegerArgumentType.integer(1))
                        .executes(ctx -> offerBuyChunk(ctx, IntegerArgumentType.getInteger(ctx, "price")))))
                .then(Commands.literal("accept")
                    .executes(ctx -> respondToBuyOffer(ctx, true)))
                .then(Commands.literal("reject")
                    .executes(ctx -> respondToBuyOffer(ctx, false)))
                .then(Commands.literal("offers")
                    .executes(ctx -> listBuyOffers(ctx))))

            .then(Commands.literal("embassy")
                .then(Commands.literal("link")
                    .then(Commands.argument("country", StringArgumentType.greedyString())
                        .executes(ctx -> linkEmbassy(ctx, StringArgumentType.getString(ctx, "country")))))
                .then(Commands.literal("unlink")
                    .executes(ctx -> unlinkEmbassy(ctx)))
                .then(Commands.literal("info")
                    .executes(ctx -> showEmbassyInfo(ctx))))

            .then(Commands.literal("diplomacy")
                .then(Commands.literal("war")
                    .then(Commands.argument("country", StringArgumentType.greedyString())
                        .executes(ctx -> declareWar(ctx, StringArgumentType.getString(ctx, "country")))))
                .then(Commands.literal("alliance")
                    .then(Commands.argument("country", StringArgumentType.greedyString())
                        .executes(ctx -> proposeAlliance(ctx, StringArgumentType.getString(ctx, "country")))))
                .then(Commands.literal("peace")
                    .then(Commands.argument("country", StringArgumentType.greedyString())
                        .executes(ctx -> proposePeace(ctx, StringArgumentType.getString(ctx, "country")))))
                .then(Commands.literal("surrender")
                    .then(Commands.argument("country", StringArgumentType.greedyString())
                        .executes(ctx -> declareSurrender(ctx, StringArgumentType.getString(ctx, "country")))))
                .then(Commands.literal("accept")
                    .executes(ctx -> respondToDiplomacy(ctx, true)))
                .then(Commands.literal("reject")
                    .executes(ctx -> respondToDiplomacy(ctx, false)))
                .then(Commands.literal("status")
                    .executes(ctx -> showDiplomacyStatus(ctx, null))
                    .then(Commands.argument("country", StringArgumentType.greedyString())
                        .executes(ctx -> showDiplomacyStatus(ctx, StringArgumentType.getString(ctx, "country")))))
                .then(Commands.literal("pay")
                    .then(Commands.argument("country", StringArgumentType.word())
                        .then(Commands.argument("amount", IntegerArgumentType.integer(1))
                            .executes(ctx -> payCountry(ctx,
                                    StringArgumentType.getString(ctx, "country"),
                                    IntegerArgumentType.getInteger(ctx, "amount"))))))
                .then(Commands.literal("tribute")
                    .executes(ctx -> showTribute(ctx))
                    .then(Commands.literal("cancel")
                        .then(Commands.argument("country", StringArgumentType.greedyString())
                            .executes(ctx -> cancelTribute(ctx, StringArgumentType.getString(ctx, "country")))))))
        );
    }

    // ═══════════════════════════════════════════════════════════════════
    // TERRITORY TRADING
    // ═══════════════════════════════════════════════════════════════════

    private static int offerBuyChunk(CommandContext<CommandSourceStack> ctx, int price) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            PoliticsManager mgr = PoliticsManager.get(player.level());
            if (mgr == null) return 0;

            String myCountry = mgr.getPlayerCountry(player.getUUID());
            if (myCountry == null) {
                player.sendSystemMessage(Component.translatable("message.politicsmod.no_state").withStyle(ChatFormatting.RED));
                return 0;
            }
            Country myObj = mgr.getCountry(myCountry);
            CountryRole role = myObj.getRole(player.getUUID());
            if (role != CountryRole.LEADER && role != CountryRole.MAYOR) {
                player.sendSystemMessage(Component.translatable("message.politicsmod.territory.offer_role").withStyle(ChatFormatting.RED));
                return 0;
            }
            ChunkPos pos = player.chunkPosition();
            String seller = mgr.getCountryNameAt(pos);
            if (seller == null) {
                player.sendSystemMessage(Component.translatable("message.politicsmod.territory.offer_wilderness").withStyle(ChatFormatting.RED));
                return 0;
            }
            if (seller.equals(myCountry)) {
                player.sendSystemMessage(Component.translatable("message.politicsmod.territory.offer_own").withStyle(ChatFormatting.RED));
                return 0;
            }
            if (myObj.balance < price) {
                player.sendSystemMessage(Component.translatable("message.politicsmod.territory.offer_no_funds", price).withStyle(ChatFormatting.RED));
                return 0;
            }
            pendingBuyOffers.put(pos.toLong(), new String[]{myCountry, String.valueOf(price)});
            notifyLeader(mgr, seller, Component.translatable("message.politicsmod.territory.offer_received",
                    myCountry, pos.x, pos.z, price).withStyle(ChatFormatting.GOLD));
            player.sendSystemMessage(Component.translatable("message.politicsmod.territory.offer_sent",
                    seller, pos.x, pos.z, price).withStyle(ChatFormatting.GREEN));
            return 1;
        } catch (Exception e) { e.printStackTrace(); return 0; }
    }

    private static int respondToBuyOffer(CommandContext<CommandSourceStack> ctx, boolean accept) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            PoliticsManager mgr = PoliticsManager.get(player.level());
            if (mgr == null) return 0;
            ChunkPos pos = player.chunkPosition();
            String myCountry = mgr.getCountryNameAt(pos);
            if (myCountry == null || !myCountry.equals(mgr.getCountryByOwner(player.getUUID()))) {
                player.sendSystemMessage(Component.translatable("message.politicsmod.territory.respond_not_owner").withStyle(ChatFormatting.RED));
                return 0;
            }
            String[] offer = pendingBuyOffers.get(pos.toLong());
            if (offer == null) {
                player.sendSystemMessage(Component.translatable("message.politicsmod.territory.no_offer").withStyle(ChatFormatting.RED));
                return 0;
            }
            String buyer = offer[0];
            int price = Integer.parseInt(offer[1]);
            pendingBuyOffers.remove(pos.toLong());
            if (!accept) {
                notifyLeader(mgr, buyer, Component.translatable("message.politicsmod.territory.offer_rejected",
                        myCountry, pos.x, pos.z).withStyle(ChatFormatting.RED));
                player.sendSystemMessage(Component.translatable("message.politicsmod.territory.rejected").withStyle(ChatFormatting.YELLOW));
                return 1;
            }
            Country buyerObj  = mgr.getCountry(buyer);
            Country sellerObj = mgr.getCountry(myCountry);
            if (buyerObj == null || sellerObj == null) return 0;
            if (buyerObj.balance < price) {
                player.sendSystemMessage(Component.translatable("message.politicsmod.territory.buyer_no_funds", buyer).withStyle(ChatFormatting.RED));
                return 0;
            }
            buyerObj.balance  -= price;
            sellerObj.balance += price;
            mgr.transferChunk(pos, buyer);
            PacketDistributor.sendToAllPlayers(new SyncChunkPayload(pos, mgr.getColorForChunk(pos)));
            notifyLeader(mgr, buyer, Component.translatable("message.politicsmod.territory.sold_to_you",
                    myCountry, pos.x, pos.z, price).withStyle(ChatFormatting.GREEN));
            player.sendSystemMessage(Component.translatable("message.politicsmod.territory.sold",
                    pos.x, pos.z, buyer, price).withStyle(ChatFormatting.GREEN));
            return 1;
        } catch (Exception e) { e.printStackTrace(); return 0; }
    }

    private static int listBuyOffers(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            PoliticsManager mgr = PoliticsManager.get(player.level());
            if (mgr == null) return 0;
            String myCountry = requireLeader(player, mgr);
            if (myCountry == null) return 0;
            List<Component> found = new ArrayList<>();
            for (Map.Entry<Long, String[]> e : pendingBuyOffers.entrySet()) {
                ChunkPos p = new ChunkPos(e.getKey());
                if (myCountry.equals(mgr.getCountryNameAt(p)))
                    found.add(Component.translatable("gui.politicsmod.territory.offer_entry",
                            p.x, p.z, e.getValue()[0], e.getValue()[1]).withStyle(ChatFormatting.YELLOW));
            }
            player.sendSystemMessage(Component.translatable("gui.politicsmod.territory.offers_header").withStyle(ChatFormatting.GOLD));
            if (found.isEmpty()) {
                player.sendSystemMessage(Component.translatable("gui.politicsmod.territory.no_active_offers").withStyle(ChatFormatting.GRAY));
            } else {
                found.forEach(player::sendSystemMessage);
                player.sendSystemMessage(Component.translatable("gui.politicsmod.territory.offers_hint").withStyle(ChatFormatting.GRAY));
            }
            return 1;
        } catch (Exception e) { e.printStackTrace(); return 0; }
    }

    // ═══════════════════════════════════════════════════════════════════
    // SURRENDER AND TRIBUTES
    // ═══════════════════════════════════════════════════════════════════

    private static int declareSurrender(CommandContext<CommandSourceStack> ctx, String target) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            PoliticsManager mgr = PoliticsManager.get(player.level());
            if (mgr == null) return 0;
            String mine = requireLeader(player, mgr);
            if (mine == null) return 0;
            if (!countryExists(mgr, target, player)) return 0;
            if (sameCountry(mine, target, player)) return 0;
            if (!mgr.isAtWar(mine, target)) {
                player.sendSystemMessage(Component.translatable("message.politicsmod.diplomacy.not_at_war", target).withStyle(ChatFormatting.RED));
                return 0;
            }
            mgr.setDiplomacy(mine, target, DiplomacyStatus.NEUTRAL);
            mgr.setTribute(mine, target, PoliticsManager.TRIBUTE_RATE_DEFAULT);
            pendingDiplomacy.remove(target);
            pendingDiplomacy.remove(mine);
            MinecraftServer srv = ServerLifecycleHooks.getCurrentServer();
            if (srv != null) {
                srv.getPlayerList().broadcastSystemMessage(
                    Component.translatable("message.politicsmod.diplomacy.surrendered",
                            mine, target, PoliticsManager.TRIBUTE_RATE_DEFAULT)
                            .withStyle(ChatFormatting.YELLOW), false);
            }
            return 1;
        } catch (Exception e) { e.printStackTrace(); return 0; }
    }

    private static int showTribute(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            PoliticsManager mgr = PoliticsManager.get(player.level());
            if (mgr == null) return 0;
            String mine = mgr.getPlayerCountry(player.getUUID());
            if (mine == null) {
                player.sendSystemMessage(Component.translatable("message.politicsmod.no_state").withStyle(ChatFormatting.RED));
                return 0;
            }
            player.sendSystemMessage(Component.translatable("gui.politicsmod.tribute.header").withStyle(ChatFormatting.GOLD));
            String[] owed = mgr.getTribute(mine);
            if (owed != null) {
                player.sendSystemMessage(Component.translatable("gui.politicsmod.tribute.paying", owed[1], owed[0]).withStyle(ChatFormatting.RED));
            } else {
                player.sendSystemMessage(Component.translatable("gui.politicsmod.tribute.not_paying").withStyle(ChatFormatting.GRAY));
            }
            boolean any = false;
            for (Map.Entry<String, String[]> e : mgr.getAllTributes().entrySet()) {
                if (e.getValue()[0].equals(mine)) {
                    if (!any) { player.sendSystemMessage(Component.translatable("gui.politicsmod.tribute.receiving").withStyle(ChatFormatting.GREEN)); any = true; }
                    player.sendSystemMessage(Component.translatable("gui.politicsmod.tribute.entry", e.getValue()[1], e.getKey()).withStyle(ChatFormatting.WHITE));
                }
            }
            if (!any && owed == null)
                player.sendSystemMessage(Component.translatable("gui.politicsmod.tribute.none").withStyle(ChatFormatting.GRAY));
            return 1;
        } catch (Exception e) { e.printStackTrace(); return 0; }
    }

    private static int cancelTribute(CommandContext<CommandSourceStack> ctx, String payer) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            PoliticsManager mgr = PoliticsManager.get(player.level());
            if (mgr == null) return 0;
            String mine = requireLeader(player, mgr);
            if (mine == null) return 0;
            String[] tribute = mgr.getTribute(payer);
            if (tribute == null || !tribute[0].equals(mine)) {
                player.sendSystemMessage(Component.translatable("message.politicsmod.tribute.not_receiver", payer).withStyle(ChatFormatting.RED));
                return 0;
            }
            mgr.removeTribute(payer);
            notifyLeader(mgr, payer, Component.translatable("message.politicsmod.tribute.cancelled_notif", mine).withStyle(ChatFormatting.GREEN));
            player.sendSystemMessage(Component.translatable("message.politicsmod.tribute.cancelled", payer).withStyle(ChatFormatting.GREEN));
            return 1;
        } catch (Exception e) { e.printStackTrace(); return 0; }
    }

    // ═══════════════════════════════════════════════════════════════════
    // DIPLOMACY
    // ═══════════════════════════════════════════════════════════════════

    private static int declareWar(CommandContext<CommandSourceStack> ctx, String target) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            PoliticsManager mgr = PoliticsManager.get(player.level());
            if (mgr == null) return 0;
            String mine = requireLeader(player, mgr);
            if (mine == null) return 0;
            Country myObj = mgr.getCountry(mine);
            if (!countryExists(mgr, target, player)) return 0;
            if (sameCountry(mine, target, player)) return 0;
            if (mgr.isAtWar(mine, target)) {
                player.sendSystemMessage(Component.translatable("message.politicsmod.diplomacy.already_war").withStyle(ChatFormatting.RED)); return 0;
            }
            if (!mgr.canDeclareWar(mine)) {
                long rem = (mgr.getWarCooldownTimestamp(mine) + PoliticsManager.WAR_COOLDOWN_MS - System.currentTimeMillis()) / 1000;
                player.sendSystemMessage(Component.translatable("message.politicsmod.diplomacy.war_cooldown", rem).withStyle(ChatFormatting.RED)); return 0;
            }
            if (myObj.balance < PoliticsManager.WAR_COST) {
                player.sendSystemMessage(Component.translatable("message.politicsmod.diplomacy.war_no_funds", PoliticsManager.WAR_COST).withStyle(ChatFormatting.RED)); return 0;
            }
            myObj.balance -= PoliticsManager.WAR_COST;
            mgr.recordWarDeclaration(mine);
            mgr.setDiplomacy(mine, target, DiplomacyStatus.WAR);
            pendingDiplomacy.remove(target); pendingDiplomacy.remove(mine);
            MinecraftServer srv = ServerLifecycleHooks.getCurrentServer();
            if (srv != null) srv.getPlayerList().broadcastSystemMessage(
                Component.translatable("message.politicsmod.diplomacy.war_declared", mine, target).withStyle(ChatFormatting.DARK_RED), false);
            return 1;
        } catch (Exception e) { e.printStackTrace(); return 0; }
    }

    private static int proposeAlliance(CommandContext<CommandSourceStack> ctx, String target) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            PoliticsManager mgr = PoliticsManager.get(player.level());
            if (mgr == null) return 0;
            String mine = requireLeader(player, mgr);
            if (mine == null) return 0;
            if (!countryExists(mgr, target, player)) return 0;
            if (sameCountry(mine, target, player)) return 0;
            DiplomacyStatus cur = mgr.getDiplomacy(mine, target);
            if (cur == DiplomacyStatus.WAR) {
                player.sendSystemMessage(Component.translatable("message.politicsmod.diplomacy.make_peace_first").withStyle(ChatFormatting.RED)); return 0;
            }
            if (cur == DiplomacyStatus.ALLIANCE) {
                player.sendSystemMessage(Component.translatable("message.politicsmod.diplomacy.already_allied").withStyle(ChatFormatting.YELLOW)); return 0;
            }
            pendingDiplomacy.put(target, new String[]{mine, "ALLIANCE"});
            notifyLeader(mgr, target, Component.translatable("message.politicsmod.diplomacy.alliance_proposal", mine).withStyle(ChatFormatting.GOLD));
            player.sendSystemMessage(Component.translatable("message.politicsmod.diplomacy.proposal_sent", target).withStyle(ChatFormatting.GREEN));
            return 1;
        } catch (Exception e) { e.printStackTrace(); return 0; }
    }

    private static int proposePeace(CommandContext<CommandSourceStack> ctx, String target) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            PoliticsManager mgr = PoliticsManager.get(player.level());
            if (mgr == null) return 0;
            String mine = requireLeader(player, mgr);
            if (mine == null) return 0;
            if (!countryExists(mgr, target, player)) return 0;
            if (sameCountry(mine, target, player)) return 0;
            if (!mgr.isAtWar(mine, target)) {
                player.sendSystemMessage(Component.translatable("message.politicsmod.diplomacy.not_at_war", target).withStyle(ChatFormatting.RED)); return 0;
            }
            pendingDiplomacy.put(target, new String[]{mine, "NEUTRAL"});
            notifyLeader(mgr, target, Component.translatable("message.politicsmod.diplomacy.peace_proposal", mine).withStyle(ChatFormatting.AQUA));
            player.sendSystemMessage(Component.translatable("message.politicsmod.diplomacy.proposal_sent", target).withStyle(ChatFormatting.GREEN));
            return 1;
        } catch (Exception e) { e.printStackTrace(); return 0; }
    }

    private static int respondToDiplomacy(CommandContext<CommandSourceStack> ctx, boolean accept) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            PoliticsManager mgr = PoliticsManager.get(player.level());
            if (mgr == null) return 0;
            String mine = requireLeader(player, mgr);
            if (mine == null) return 0;
            String[] pending = pendingDiplomacy.get(mine);
            if (pending == null) {
                player.sendSystemMessage(Component.translatable("message.politicsmod.diplomacy.no_pending").withStyle(ChatFormatting.RED)); return 0;
            }
            String requester = pending[0];
            DiplomacyStatus req = DiplomacyStatus.valueOf(pending[1]);
            pendingDiplomacy.remove(mine);
            if (!accept) {
                notifyLeader(mgr, requester, Component.translatable("message.politicsmod.diplomacy.proposal_rejected", mine).withStyle(ChatFormatting.RED));
                player.sendSystemMessage(Component.translatable("message.politicsmod.diplomacy.rejected").withStyle(ChatFormatting.YELLOW));
                return 1;
            }
            mgr.setDiplomacy(mine, requester, req);
            MinecraftServer srv = ServerLifecycleHooks.getCurrentServer();
            if (srv != null) {
                if (req == DiplomacyStatus.ALLIANCE)
                    srv.getPlayerList().broadcastSystemMessage(Component.translatable("message.politicsmod.diplomacy.alliance_formed", mine, requester).withStyle(ChatFormatting.GREEN), false);
                else if (req == DiplomacyStatus.NEUTRAL)
                    srv.getPlayerList().broadcastSystemMessage(Component.translatable("message.politicsmod.diplomacy.peace_made", mine, requester).withStyle(ChatFormatting.AQUA), false);
            }
            return 1;
        } catch (Exception e) { e.printStackTrace(); return 0; }
    }

    private static int showDiplomacyStatus(CommandContext<CommandSourceStack> ctx, String target) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            PoliticsManager mgr = PoliticsManager.get(player.level());
            if (mgr == null) return 0;
            String mine = mgr.getPlayerCountry(player.getUUID());
            if (mine == null) {
                player.sendSystemMessage(Component.translatable("message.politicsmod.no_state").withStyle(ChatFormatting.RED)); return 0;
            }
            if (target != null) {
                if (mgr.getCountry(target) == null) {
                    player.sendSystemMessage(Component.translatable("message.politicsmod.country_not_found", target).withStyle(ChatFormatting.RED)); return 0;
                }
                DiplomacyStatus s = mgr.getDiplomacy(mine, target);
                player.sendSystemMessage(Component.translatable("gui.politicsmod.diplomacy.single_relation", target,
                    Component.translatable(statusKey(s)).withStyle(statusColor(s))).withStyle(ChatFormatting.YELLOW));
            } else {
                player.sendSystemMessage(Component.translatable("gui.politicsmod.diplomacy.header", mine).withStyle(ChatFormatting.GOLD));
                boolean any = false;
                for (String other : mgr.getCountries().keySet()) {
                    if (other.equals(mine)) continue;
                    DiplomacyStatus s = mgr.getDiplomacy(mine, other);
                    if (s != DiplomacyStatus.NEUTRAL) {
                        player.sendSystemMessage(Component.translatable("gui.politicsmod.diplomacy.relation_entry", other,
                            Component.translatable(statusKey(s)).withStyle(statusColor(s))).withStyle(ChatFormatting.WHITE));
                        any = true;
                    }
                }
                if (!any) player.sendSystemMessage(Component.translatable("gui.politicsmod.diplomacy.no_relations").withStyle(ChatFormatting.GRAY));
            }
            return 1;
        } catch (Exception e) { e.printStackTrace(); return 0; }
    }

    private static int payCountry(CommandContext<CommandSourceStack> ctx, String target, int amount) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            PoliticsManager mgr = PoliticsManager.get(player.level());
            if (mgr == null) return 0;
            String mine = requireLeader(player, mgr);
            if (mine == null) return 0;
            Country myObj = mgr.getCountry(mine);
            Country tgt = mgr.getCountry(target);
            if (tgt == null) {
                player.sendSystemMessage(Component.translatable("message.politicsmod.country_not_found", target).withStyle(ChatFormatting.RED)); return 0;
            }
            if (myObj.balance < amount) {
                player.sendSystemMessage(Component.translatable("message.politicsmod.diplomacy.pay_no_funds", amount).withStyle(ChatFormatting.RED)); return 0;
            }
            myObj.balance -= amount; tgt.balance += amount;
            mgr.saveData();
            player.sendSystemMessage(Component.translatable("message.politicsmod.diplomacy.pay_sent", amount, target).withStyle(ChatFormatting.GREEN));
            notifyLeader(mgr, target, Component.translatable("message.politicsmod.diplomacy.pay_received", amount, mine).withStyle(ChatFormatting.GOLD));
            return 1;
        } catch (Exception e) { e.printStackTrace(); return 0; }
    }

    // ═══════════════════════════════════════════════════════════════════
    // ROLES AND MEMBERS
    // ═══════════════════════════════════════════════════════════════════

    private static int showCountryInfo(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            PoliticsManager mgr = PoliticsManager.get(player.level());
            if (mgr == null) return 0;
            String cn = mgr.getPlayerCountry(player.getUUID());
            if (cn == null) {
                player.sendSystemMessage(Component.translatable("message.politicsmod.no_state").withStyle(ChatFormatting.RED)); return 0;
            }
            Country c = mgr.getCountry(cn);
            int[] cnt = {0};
            mgr.forEachClaim((pos, col) -> { if (cn.equals(mgr.getCountryNameAt(pos))) cnt[0]++; });
            int income = c.taxBlocks.size() * PoliticsManager.INCOME_PER_TAX_BLOCK;
            int upkeep = cnt[0] * PoliticsManager.UPKEEP_PER_CHUNK;
            int net = income - upkeep;
            String[] tribute = mgr.getTribute(cn);
            int tribAmt = 0;
            if (tribute != null) { tribAmt = income * Integer.parseInt(tribute[1]) / 100; net -= tribAmt; }
            String netStr = (net >= 0 ? "+" : "") + net + "$";

            player.sendSystemMessage(Component.literal("===========================").withStyle(ChatFormatting.GOLD));
            player.sendSystemMessage(Component.translatable("gui.politicsmod.info.country", c.getName()).withStyle(ChatFormatting.YELLOW));
            player.sendSystemMessage(Component.translatable("gui.politicsmod.info.role", c.getRole(player.getUUID()).name()).withStyle(ChatFormatting.AQUA));
            player.sendSystemMessage(Component.translatable("gui.politicsmod.info.treasury", c.balance).withStyle(ChatFormatting.GREEN));
            player.sendSystemMessage(Component.translatable("gui.politicsmod.info.economy_header").withStyle(ChatFormatting.GRAY));
            player.sendSystemMessage(Component.translatable("gui.politicsmod.info.income", income).withStyle(ChatFormatting.GREEN));
            player.sendSystemMessage(Component.translatable("gui.politicsmod.info.upkeep", upkeep).withStyle(ChatFormatting.RED));
            if (tribAmt > 0) player.sendSystemMessage(Component.translatable("gui.politicsmod.info.tribute_to", tribute[0], tribAmt).withStyle(ChatFormatting.DARK_RED));
            player.sendSystemMessage(Component.translatable("gui.politicsmod.info.net", netStr).withStyle(net >= 0 ? ChatFormatting.GREEN : ChatFormatting.RED));
            int maxBal = PoliticsManager.BASE_MAX_BALANCE + c.vaultBlocks.size() * PoliticsManager.VAULT_BALANCE_BONUS;
            player.sendSystemMessage(Component.translatable("gui.politicsmod.info.vaults", c.vaultBlocks.size(), maxBal).withStyle(ChatFormatting.GRAY));
            long activeEmbassies = c.embassyLinks.values().stream()
                .filter(p -> mgr.getCountry(p) != null && !mgr.isAtWar(cn, p)).count();
            if (!c.embassyBlocks.isEmpty()) {
                player.sendSystemMessage(Component.translatable("gui.politicsmod.info.embassies", c.embassyBlocks.size(), (int) activeEmbassies).withStyle(ChatFormatting.GRAY));
            }
            player.sendSystemMessage(Component.literal("===========================").withStyle(ChatFormatting.GOLD));
            return 1;
        } catch (Exception e) { return 0; }
    }

    private static int invitePlayer(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer sender = ctx.getSource().getPlayerOrException();
            ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
            PoliticsManager mgr = PoliticsManager.get(sender.level());
            if (mgr == null) return 0;
            String cn = mgr.getPlayerCountry(sender.getUUID());
            if (cn == null) { sender.sendSystemMessage(Component.translatable("message.politicsmod.no_state").withStyle(ChatFormatting.RED)); return 0; }
            Country c = mgr.getCountry(cn);
            if (!c.getRole(sender.getUUID()).canManageCitizens()) {
                sender.sendSystemMessage(Component.translatable("message.politicsmod.invite.no_permission").withStyle(ChatFormatting.RED)); return 0;
            }
            if (mgr.getPlayerCountry(target.getUUID()) != null) {
                sender.sendSystemMessage(Component.translatable("message.politicsmod.invite.already_member").withStyle(ChatFormatting.RED)); return 0;
            }
            c.setRole(target.getUUID(), CountryRole.CITIZEN); mgr.setDirty();
            sender.sendSystemMessage(Component.translatable("message.politicsmod.invite.accepted", target.getName().getString()).withStyle(ChatFormatting.GREEN));
            target.sendSystemMessage(Component.translatable("message.politicsmod.invite.welcome", c.getName()).withStyle(ChatFormatting.GOLD));
            return 1;
        } catch (Exception e) { return 0; }
    }

    private static int kickPlayer(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer sender = ctx.getSource().getPlayerOrException();
            ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
            PoliticsManager mgr = PoliticsManager.get(sender.level());
            if (mgr == null) return 0;
            String cn = mgr.getPlayerCountry(sender.getUUID());
            if (cn == null) return 0;
            Country c = mgr.getCountry(cn);
            if (!c.getRole(sender.getUUID()).canManageCitizens()) {
                sender.sendSystemMessage(Component.translatable("message.politicsmod.kick.no_permission").withStyle(ChatFormatting.RED)); return 0;
            }
            if (c.getRole(target.getUUID()) == CountryRole.LEADER) {
                sender.sendSystemMessage(Component.translatable("message.politicsmod.kick.cant_kick_leader").withStyle(ChatFormatting.RED)); return 0;
            }
            if (c.getRole(target.getUUID()) != CountryRole.GUEST) {
                c.setRole(target.getUUID(), CountryRole.GUEST); mgr.setDirty();
                sender.sendSystemMessage(Component.translatable("message.politicsmod.kick.success", target.getName().getString()).withStyle(ChatFormatting.YELLOW));
                target.sendSystemMessage(Component.translatable("message.politicsmod.kick.you_were_kicked", c.getName()).withStyle(ChatFormatting.RED));
            }
            return 1;
        } catch (Exception e) { return 0; }
    }

    private static int changePlayerRole(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer sender = ctx.getSource().getPlayerOrException();
            ServerPlayer target = EntityArgument.getPlayer(ctx, "target");
            String roleName = StringArgumentType.getString(ctx, "role").toUpperCase();
            PoliticsManager mgr = PoliticsManager.get(sender.level());
            if (mgr == null) return 0;
            String cn = mgr.getPlayerCountry(sender.getUUID());
            if (cn == null) return 0;
            Country c = mgr.getCountry(cn);
            if (c.getRole(sender.getUUID()) != CountryRole.LEADER) {
                sender.sendSystemMessage(Component.translatable("message.politicsmod.setrole.leader_only").withStyle(ChatFormatting.RED)); return 0;
            }
            if (!cn.equals(mgr.getPlayerCountry(target.getUUID()))) {
                sender.sendSystemMessage(Component.translatable("message.politicsmod.setrole.not_your_citizen").withStyle(ChatFormatting.RED)); return 0;
            }
            if (c.getRole(target.getUUID()) == CountryRole.LEADER) {
                sender.sendSystemMessage(Component.translatable("message.politicsmod.setrole.cant_change_leader").withStyle(ChatFormatting.RED)); return 0;
            }
            CountryRole newRole = switch (roleName) {
                case "MAYOR"   -> CountryRole.MAYOR;
                case "CITIZEN" -> CountryRole.CITIZEN;
                default        -> null;
            };
            if (newRole == null) {
                sender.sendSystemMessage(Component.translatable("message.politicsmod.setrole.invalid_role").withStyle(ChatFormatting.YELLOW)); return 0;
            }
            c.setRole(target.getUUID(), newRole); mgr.setDirty();
            sender.sendSystemMessage(Component.translatable("message.politicsmod.setrole.success", target.getName().getString(), newRole.name()).withStyle(ChatFormatting.GREEN));
            target.sendSystemMessage(Component.translatable("message.politicsmod.setrole.your_role", newRole.name()).withStyle(ChatFormatting.GOLD));
            return 1;
        } catch (Exception e) { return 0; }
    }

    private static int renameCountry(CommandContext<CommandSourceStack> ctx, String newName) {
        try {
            ServerPlayer p = ctx.getSource().getPlayerOrException();
            PoliticsManager mgr = PoliticsManager.get(p.level());
            if (mgr == null) return 0;
            String cur = mgr.getCountryByOwner(p.getUUID());
            if (cur == null) { ctx.getSource().sendFailure(Component.translatable("message.politicsmod.no_state")); return 0; }
            if (mgr.renameCountry(cur, newName)) {
                ctx.getSource().sendSuccess(() -> Component.translatable("message.politicsmod.country_renamed", newName), true); return 1;
            }
            ctx.getSource().sendFailure(Component.translatable("message.politicsmod.error.name_taken")); return 0;
        } catch (Exception e) { return 0; }
    }

    private static int claimSingleChunk(CommandContext<CommandSourceStack> ctx, String city) {
        try {
            ServerPlayer p = ctx.getSource().getPlayerOrException();
            return processClaim(ctx, p, p.chunkPosition(), city);
        } catch (Exception e) { return 0; }
    }

    private static int claimRadius(CommandContext<CommandSourceStack> ctx, int r, String city) {
        try {
            ServerPlayer p = ctx.getSource().getPlayerOrException();
            ChunkPos c = p.chunkPosition();
            int count = 0;
            for (int x = -r; x <= r; x++) for (int z = -r; z <= r; z++)
                if (processClaim(ctx, p, new ChunkPos(c.x+x, c.z+z), city) == 1) count++;
            final int fc = count;
            ctx.getSource().sendSuccess(() -> Component.translatable("message.politicsmod.command.claimed", fc), true);
            return count;
        } catch (Exception e) { return 0; }
    }

    private static int processClaim(CommandContext<CommandSourceStack> ctx, ServerPlayer player, ChunkPos pos, String city) {
        PoliticsManager mgr = PoliticsManager.get(player.level());
        if (mgr == null) return 0;
        String cn = mgr.getPlayerCountry(player.getUUID());
        if (cn == null) {
            ctx.getSource().sendFailure(Component.translatable("message.politicsmod.no_state").withStyle(ChatFormatting.RED)); return 0;
        }
        Country c = mgr.getCountry(cn);
        CountryRole role = c.getRole(player.getUUID());
        String curOwner = mgr.getCountryNameAt(pos);
        if (city == null) {
            if (role != CountryRole.LEADER) {
                ctx.getSource().sendFailure(Component.translatable("message.politicsmod.claim.leader_only").withStyle(ChatFormatting.RED)); return 0;
            }
            if (curOwner != null) {
                ctx.getSource().sendFailure(Component.translatable("message.politicsmod.claim.chunk_taken").withStyle(ChatFormatting.RED)); return 0;
            }
            if (c.balance < 500) {
                ctx.getSource().sendFailure(Component.translatable("message.politicsmod.claim.need_funds_500").withStyle(ChatFormatting.RED)); return 0;
            }
            c.balance -= 500; mgr.claimChunk(pos, 0, cn, null);
            ctx.getSource().sendSuccess(() -> Component.translatable("message.politicsmod.claim.expanded_country").withStyle(ChatFormatting.GREEN), true);
        } else {
            if (role != CountryRole.LEADER && role != CountryRole.MAYOR) {
                ctx.getSource().sendFailure(Component.translatable("message.politicsmod.claim.leader_mayor_only").withStyle(ChatFormatting.RED)); return 0;
            }
            if (!c.cities.contains(city)) {
                ctx.getSource().sendFailure(Component.translatable("message.politicsmod.claim.city_not_exists", city).withStyle(ChatFormatting.RED)); return 0;
            }
            if (curOwner == null || !curOwner.equals(cn)) {
                ctx.getSource().sendFailure(Component.translatable("message.politicsmod.claim.not_your_territory").withStyle(ChatFormatting.RED)); return 0;
            }
            if (mgr.getCityAt(pos) != null) {
                ctx.getSource().sendFailure(Component.translatable("message.politicsmod.claim.chunk_in_city", mgr.getCityAt(pos)).withStyle(ChatFormatting.RED)); return 0;
            }
            String mayorCity = mgr.getMayorCity(cn, player.getUUID());
            boolean isMayorOfCity = city.equals(mayorCity);
            if (role == CountryRole.MAYOR && isMayorOfCity) {
                int cityBal = c.cityBalances.getOrDefault(city, 0);
                if (cityBal < 100) {
                    ctx.getSource().sendFailure(Component.translatable("message.politicsmod.city.no_city_funds").withStyle(ChatFormatting.RED)); return 0;
                }
                c.cityBalances.put(city, cityBal - 100);
            } else {
                if (c.balance < 100) {
                    ctx.getSource().sendFailure(Component.translatable("message.politicsmod.claim.need_funds_100").withStyle(ChatFormatting.RED)); return 0;
                }
                c.balance -= 100;
            }
            mgr.claimChunk(pos, 0, cn, city);
            ctx.getSource().sendSuccess(() -> Component.translatable("message.politicsmod.claim.expanded_city", city).withStyle(ChatFormatting.GREEN), true);
        }
        PacketDistributor.sendToPlayer(player, new SyncChunkPayload(pos, mgr.getColorForChunk(pos)));
        return 1;
    }

    // ═══════════════════════════════════════════════════════════════════
    // PILLAR 3 — CITY TAX AND AUTONOMY
    // ═══════════════════════════════════════════════════════════════════

    private static int setFederalTax(CommandContext<CommandSourceStack> ctx, int rate) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            PoliticsManager mgr = PoliticsManager.get(player.level());
            if (mgr == null) return 0;
            String mine = requireLeader(player, mgr);
            if (mine == null) return 0;
            mgr.getCountry(mine).federalTaxRate = rate;
            mgr.saveData();
            player.sendSystemMessage(Component.translatable("message.politicsmod.tax.federal_set", rate).withStyle(ChatFormatting.GREEN));
            if (rate >= PoliticsManager.SECEDE_THRESHOLD)
                player.sendSystemMessage(Component.translatable("message.politicsmod.tax.secede_warning", PoliticsManager.SECEDE_THRESHOLD).withStyle(ChatFormatting.YELLOW));
            return 1;
        } catch (Exception e) { e.printStackTrace(); return 0; }
    }

    private static int showTaxInfo(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            PoliticsManager mgr = PoliticsManager.get(player.level());
            if (mgr == null) return 0;
            String mine = mgr.getPlayerCountry(player.getUUID());
            if (mine == null) { player.sendSystemMessage(Component.translatable("message.politicsmod.no_state").withStyle(ChatFormatting.RED)); return 0; }
            net.krona.politicsmod.politics.Country country = mgr.getCountry(mine);
            player.sendSystemMessage(Component.translatable("gui.politicsmod.tax_info.header", mine).withStyle(ChatFormatting.GOLD));
            player.sendSystemMessage(Component.translatable("gui.politicsmod.tax_info.federal_rate", country.federalTaxRate).withStyle(ChatFormatting.YELLOW));
            int total = 0;
            for (String city : country.cities) { int b = country.cityBalances.getOrDefault(city, 0); total += b; }
            player.sendSystemMessage(Component.translatable("gui.politicsmod.tax_info.city_total", total).withStyle(ChatFormatting.WHITE));
            if (country.federalTaxRate >= PoliticsManager.SECEDE_THRESHOLD)
                player.sendSystemMessage(Component.translatable("gui.politicsmod.tax_info.danger").withStyle(ChatFormatting.RED));
            return 1;
        } catch (Exception e) { e.printStackTrace(); return 0; }
    }

    private static int setCityMayorCmd(CommandContext<CommandSourceStack> ctx, String cityName) {
        try {
            ServerPlayer sender = ctx.getSource().getPlayerOrException();
            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
            PoliticsManager mgr = PoliticsManager.get(sender.level());
            if (mgr == null) return 0;
            String mine = requireLeader(sender, mgr);
            if (mine == null) return 0;
            net.krona.politicsmod.politics.Country country = mgr.getCountry(mine);
            if (!country.cities.contains(cityName)) {
                sender.sendSystemMessage(Component.translatable("message.politicsmod.city.not_found", cityName).withStyle(ChatFormatting.RED)); return 0;
            }
            if (country.getRole(target.getUUID()) == CountryRole.GUEST)
                country.setRole(target.getUUID(), CountryRole.MAYOR);
            mgr.setCityMayor(mine, cityName, target.getUUID());
            sender.sendSystemMessage(Component.translatable("message.politicsmod.city.mayor_set",
                    target.getName().getString(), cityName).withStyle(ChatFormatting.GREEN));
            target.sendSystemMessage(Component.translatable("message.politicsmod.city.you_are_mayor", cityName).withStyle(ChatFormatting.GOLD));
            return 1;
        } catch (Exception e) { e.printStackTrace(); return 0; }
    }

    private static int showCityInfo(CommandContext<CommandSourceStack> ctx, String cityName) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            PoliticsManager mgr = PoliticsManager.get(player.level());
            if (mgr == null) return 0;
            String mine = mgr.getPlayerCountry(player.getUUID());
            if (mine == null) { player.sendSystemMessage(Component.translatable("message.politicsmod.no_state").withStyle(ChatFormatting.RED)); return 0; }
            net.krona.politicsmod.politics.Country country = mgr.getCountry(mine);

            if (cityName != null) {
                if (!country.cities.contains(cityName)) {
                    player.sendSystemMessage(Component.translatable("message.politicsmod.city.not_found", cityName).withStyle(ChatFormatting.RED)); return 0;
                }
                player.sendSystemMessage(Component.translatable("gui.politicsmod.city_info.header", cityName).withStyle(ChatFormatting.GOLD));
                player.sendSystemMessage(Component.translatable("gui.politicsmod.city_info.treasury", country.cityBalances.getOrDefault(cityName, 0)).withStyle(ChatFormatting.YELLOW));
                java.util.UUID mayorUUID = country.cityMayors.get(cityName);
                String mayorName = Component.translatable("gui.politicsmod.city_info.mayor_none").getString();
                if (mayorUUID != null) {
                    MinecraftServer srv = ServerLifecycleHooks.getCurrentServer();
                    if (srv != null) {
                        ServerPlayer onlineMayor = srv.getPlayerList().getPlayer(mayorUUID);
                        if (onlineMayor != null) mayorName = onlineMayor.getName().getString();
                        else { java.util.Optional<GameProfile> gp = srv.getProfileCache().get(mayorUUID); mayorName = gp.map(GameProfile::getName).orElse(mayorUUID.toString().substring(0, 8)); }
                    }
                }
                player.sendSystemMessage(Component.translatable("gui.politicsmod.city_info.mayor", mayorName).withStyle(ChatFormatting.AQUA));
            } else {
                player.sendSystemMessage(Component.translatable("gui.politicsmod.city_info.list_header", mine).withStyle(ChatFormatting.GOLD));
                if (country.federalTaxRate >= PoliticsManager.SECEDE_THRESHOLD) {
                    player.sendSystemMessage(Component.translatable("gui.politicsmod.city_info.federal_rate_danger", country.federalTaxRate).withStyle(ChatFormatting.YELLOW));
                } else {
                    player.sendSystemMessage(Component.translatable("gui.politicsmod.city_info.federal_rate", country.federalTaxRate).withStyle(ChatFormatting.YELLOW));
                }
                if (country.cities.isEmpty()) {
                    player.sendSystemMessage(Component.translatable("gui.politicsmod.city_info.no_cities").withStyle(ChatFormatting.GRAY));
                } else {
                    for (String city : country.cities) {
                        int bal = country.cityBalances.getOrDefault(city, 0);
                        boolean hasMayor = country.cityMayors.containsKey(city);
                        MutableComponent msg = hasMayor
                            ? Component.translatable("gui.politicsmod.city_info.entry_mayor", city, bal)
                            : Component.translatable("gui.politicsmod.city_info.entry", city, bal);
                        player.sendSystemMessage(msg.withStyle(ChatFormatting.WHITE));
                    }
                }
            }
            return 1;
        } catch (Exception e) { e.printStackTrace(); return 0; }
    }

    private static int declareSecession(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            PoliticsManager mgr = PoliticsManager.get(player.level());
            if (mgr == null) return 0;
            String countryName = mgr.getPlayerCountry(player.getUUID());
            if (countryName == null) { player.sendSystemMessage(Component.translatable("message.politicsmod.no_state").withStyle(ChatFormatting.RED)); return 0; }
            String cityName = mgr.getMayorCity(countryName, player.getUUID());
            if (cityName == null) {
                player.sendSystemMessage(Component.translatable("message.politicsmod.city.not_a_mayor").withStyle(ChatFormatting.RED)); return 0;
            }
            net.krona.politicsmod.politics.Country country = mgr.getCountry(countryName);
            if (country.federalTaxRate < PoliticsManager.SECEDE_THRESHOLD) {
                player.sendSystemMessage(Component.translatable("message.politicsmod.city.secede_threshold",
                        country.federalTaxRate, PoliticsManager.SECEDE_THRESHOLD).withStyle(ChatFormatting.RED)); return 0;
            }
            if (mgr.getCountry(cityName) != null) {
                player.sendSystemMessage(Component.translatable("message.politicsmod.city.secede_name_taken", cityName).withStyle(ChatFormatting.RED)); return 0;
            }
            boolean ok = mgr.secede(countryName, cityName);
            if (ok) {
                MinecraftServer srv = ServerLifecycleHooks.getCurrentServer();
                if (srv != null) srv.getPlayerList().broadcastSystemMessage(
                    Component.translatable("message.politicsmod.city.seceded", cityName, countryName)
                            .withStyle(ChatFormatting.LIGHT_PURPLE), false);
            }
            return ok ? 1 : 0;
        } catch (Exception e) { e.printStackTrace(); return 0; }
    }

    // ═══════════════════════════════════════════════════════════════════
    // PILLAR 4 — EMBASSIES
    // ═══════════════════════════════════════════════════════════════════

    private static int linkEmbassy(CommandContext<CommandSourceStack> ctx, String targetCountry) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            PoliticsManager mgr = PoliticsManager.get(player.level());
            if (mgr == null) return 0;
            String mine = mgr.getPlayerCountry(player.getUUID());
            if (mine == null) { player.sendSystemMessage(Component.translatable("message.politicsmod.no_state").withStyle(ChatFormatting.RED)); return 0; }
            net.krona.politicsmod.politics.Country myObj = mgr.getCountry(mine);
            CountryRole role = myObj.getRole(player.getUUID());
            if (role != CountryRole.LEADER && role != CountryRole.MAYOR) {
                player.sendSystemMessage(Component.translatable("message.politicsmod.embassy.no_permission").withStyle(ChatFormatting.RED)); return 0;
            }
            if (mgr.getCountry(targetCountry) == null) {
                player.sendSystemMessage(Component.translatable("message.politicsmod.country_not_found", targetCountry).withStyle(ChatFormatting.RED)); return 0;
            }
            if (mine.equals(targetCountry)) {
                player.sendSystemMessage(Component.translatable("message.politicsmod.embassy.cant_link_self").withStyle(ChatFormatting.RED)); return 0;
            }
            Long embassyPos = mgr.findEmbassyInChunk(mine, player.chunkPosition());
            if (embassyPos == null) {
                player.sendSystemMessage(Component.translatable("message.politicsmod.embassy.no_embassy").withStyle(ChatFormatting.RED)); return 0;
            }
            mgr.linkEmbassy(mine, embassyPos, targetCountry);
            net.minecraft.core.BlockPos embassyBp = net.minecraft.core.BlockPos.of(embassyPos);
            if (player.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                if (sl.getBlockEntity(embassyBp) instanceof net.krona.politicsmod.block.entity.EmbassyEntity be) {
                    be.setLinkedCountry(targetCountry);
                }
            }
            player.sendSystemMessage(Component.translatable("message.politicsmod.embassy.linked", targetCountry).withStyle(ChatFormatting.GREEN));
            return 1;
        } catch (Exception e) { e.printStackTrace(); return 0; }
    }

    private static int unlinkEmbassy(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            PoliticsManager mgr = PoliticsManager.get(player.level());
            if (mgr == null) return 0;
            String mine = mgr.getPlayerCountry(player.getUUID());
            if (mine == null) return 0;
            Long embassyPos = mgr.findEmbassyInChunk(mine, player.chunkPosition());
            if (embassyPos == null) {
                player.sendSystemMessage(Component.translatable("message.politicsmod.embassy.no_embassy").withStyle(ChatFormatting.RED)); return 0;
            }
            boolean ok = mgr.unlinkEmbassy(mine, embassyPos);
            if (ok) {
                net.minecraft.core.BlockPos embassyBp = net.minecraft.core.BlockPos.of(embassyPos);
                if (player.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                    if (sl.getBlockEntity(embassyBp) instanceof net.krona.politicsmod.block.entity.EmbassyEntity be) {
                        be.setLinkedCountry("");
                    }
                }
            }
            player.sendSystemMessage(ok
                ? Component.translatable("message.politicsmod.embassy.unlinked").withStyle(ChatFormatting.YELLOW)
                : Component.translatable("message.politicsmod.embassy.no_embassy").withStyle(ChatFormatting.RED));
            return ok ? 1 : 0;
        } catch (Exception e) { e.printStackTrace(); return 0; }
    }

    private static int showEmbassyInfo(CommandContext<CommandSourceStack> ctx) {
        try {
            ServerPlayer player = ctx.getSource().getPlayerOrException();
            PoliticsManager mgr = PoliticsManager.get(player.level());
            if (mgr == null) return 0;
            String mine = mgr.getPlayerCountry(player.getUUID());
            if (mine == null) { player.sendSystemMessage(Component.translatable("message.politicsmod.no_state").withStyle(ChatFormatting.RED)); return 0; }
            net.krona.politicsmod.politics.Country country = mgr.getCountry(mine);
            player.sendSystemMessage(Component.translatable("gui.politicsmod.embassy_info.header", mine).withStyle(ChatFormatting.GOLD));
            if (country.embassyBlocks.isEmpty()) {
                player.sendSystemMessage(Component.translatable("gui.politicsmod.embassy_info.none").withStyle(ChatFormatting.GRAY));
            } else {
                for (Long pos : country.embassyBlocks) {
                    String linked = country.embassyLinks.get(pos);
                    net.minecraft.core.BlockPos bp = net.minecraft.core.BlockPos.of(pos);
                    MutableComponent msg;
                    if (linked != null) {
                        if (mgr.isAtWar(mine, linked)) {
                            msg = Component.translatable("gui.politicsmod.embassy_info.entry_war", bp.getX(), bp.getZ(), linked);
                        } else {
                            msg = Component.translatable("gui.politicsmod.embassy_info.entry_active", bp.getX(), bp.getZ(), linked, PoliticsManager.EMBASSY_INCOME_BONUS);
                        }
                    } else {
                        msg = Component.translatable("gui.politicsmod.embassy_info.entry_unlinked", bp.getX(), bp.getZ());
                    }
                    player.sendSystemMessage(msg.withStyle(ChatFormatting.WHITE));
                }
            }
            return 1;
        } catch (Exception e) { e.printStackTrace(); return 0; }
    }

    // ═══════════════════════════════════════════════════════════════════
    // UTILITIES
    // ═══════════════════════════════════════════════════════════════════

    private static String requireLeader(ServerPlayer p, PoliticsManager mgr) {
        String c = mgr.getCountryByOwner(p.getUUID());
        if (c == null) p.sendSystemMessage(Component.translatable("message.politicsmod.diplomacy.leader_only").withStyle(ChatFormatting.RED));
        return c;
    }

    private static boolean countryExists(PoliticsManager mgr, String name, ServerPlayer p) {
        if (mgr.getCountry(name) == null) {
            p.sendSystemMessage(Component.translatable("message.politicsmod.country_not_found", name).withStyle(ChatFormatting.RED));
            return false;
        }
        return true;
    }

    private static boolean sameCountry(String a, String b, ServerPlayer p) {
        if (a.equals(b)) {
            p.sendSystemMessage(Component.translatable("message.politicsmod.cant_manage_own_country").withStyle(ChatFormatting.RED));
            return true;
        }
        return false;
    }

    private static void notifyLeader(PoliticsManager mgr, String cn, Component msg) {
        Country c = mgr.getCountry(cn);
        if (c == null) return;
        MinecraftServer srv = ServerLifecycleHooks.getCurrentServer();
        if (srv == null) return;
        c.getMembers().entrySet().stream().filter(e -> e.getValue() == CountryRole.LEADER).forEach(e -> {
            ServerPlayer ldr = srv.getPlayerList().getPlayer(e.getKey());
            if (ldr != null) ldr.sendSystemMessage(msg);
        });
    }

    private static ChatFormatting statusColor(DiplomacyStatus s) {
        return switch (s) { case ALLIANCE -> ChatFormatting.GREEN; case WAR -> ChatFormatting.RED; default -> ChatFormatting.GRAY; };
    }

    private static String statusKey(DiplomacyStatus s) {
        return switch (s) {
            case ALLIANCE -> "gui.politicsmod.diplomacy.status.alliance";
            case WAR      -> "gui.politicsmod.diplomacy.status.war";
            default       -> "gui.politicsmod.diplomacy.status.neutral";
        };
    }
}
