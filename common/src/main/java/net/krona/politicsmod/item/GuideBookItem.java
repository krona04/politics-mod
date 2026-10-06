package net.krona.politicsmod.item;

import net.krona.politicsmod.registry.ModItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * The in-game guide. Its pages are translation keys, so each player reads it in their own language;
 * nothing is stored in the item itself.
 */
public class GuideBookItem extends Item {

    /** Pages are guide.politicsmod.page.1 … page.PAGES in the language files. */
    public static final int PAGES = 16;

    public GuideBookItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) {
            net.krona.politicsmod.client.ClientScreens.openGuide();
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    public static void give(ServerPlayer player) {
        ItemStack book = new ItemStack(ModItems.GUIDE_BOOK.get());
        if (!player.getInventory().add(book)) player.drop(book, false);
    }
}
