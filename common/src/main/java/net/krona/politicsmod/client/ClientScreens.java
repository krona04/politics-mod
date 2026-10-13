package net.krona.politicsmod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.List;
import net.krona.politicsmod.item.GuideBookItem;
import net.minecraft.client.gui.screens.inventory.BookViewScreen;
import net.minecraft.network.chat.Component;

/**
 * Opens mod screens on behalf of blocks and packets.
 *
 * Kept out of block classes on purpose: blocks are loaded on dedicated servers too,
 * and a direct reference to Minecraft/Screen there crashes the server
 * ("Cannot load class ...Screen in environment type SERVER").
 * Call only when level.isClientSide is true.
 */
public final class ClientScreens {

    private ClientScreens() {
    }

    public static void openFoundingStone(BlockPos pos) {
        Minecraft.getInstance().setScreen(new FoundingStoneScreen(pos));
    }

    public static void openCityFoundation(BlockPos pos) {
        Minecraft.getInstance().setScreen(new CityFoundationScreen(pos));
    }

    public static void openResidential(BlockPos pos, int population) {
        Minecraft.getInstance().setScreen(new ResidentialScreen(pos, population));
    }

    /** Text area of a vanilla book page: 114 pixels wide, 128 high = 14 lines of 9 pixels. */
    private static final int BOOK_TEXT_WIDTH = 114, BOOK_LINES = 14;

    public static void openGuide() {
        net.minecraft.client.gui.Font font = Minecraft.getInstance().font;
        List<Component> pages = new ArrayList<>();
        for (int i = 1; i <= GuideBookItem.PAGES; i++) {
            List<net.minecraft.network.chat.FormattedText> lines = font.getSplitter().splitLines(
                    Component.translatable("guide.politicsmod.page." + i), BOOK_TEXT_WIDTH, net.minecraft.network.chat.Style.EMPTY);
            // A page too long for one book page (it depends on the language) continues on the next one
            // instead of being cut off at the bottom
            int from = 0;
            while (from < lines.size()) {
                net.minecraft.network.chat.MutableComponent page = Component.empty();
                int to = Math.min(lines.size(), from + BOOK_LINES);
                for (int l = from; l < to; l++) {
                    if (l > from) page.append("\n");
                    lines.get(l).visit((style, text) -> {
                        page.append(Component.literal(text).withStyle(style));
                        return java.util.Optional.empty();
                    }, net.minecraft.network.chat.Style.EMPTY);
                }
                pages.add(page);
                from = to;
                while (from < lines.size() && lines.get(from).getString().isBlank()) from++;
            }
        }
        Minecraft.getInstance().setScreen(new BookViewScreen(new BookViewScreen.BookAccess(pages)));
    }

    public static void openMarket() {
        Minecraft.getInstance().setScreen(new MarketScreen());
    }

    public static void openCountryDashboard(String countryName, int balance, List<String> cities,
                                            String flagUrl, String playerRole) {
        Minecraft.getInstance().setScreen(
                new CountryDashboardScreen(countryName, balance, cities, flagUrl, playerRole));
    }
}
