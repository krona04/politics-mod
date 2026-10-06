package net.krona.politicsmod.politics;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;
import java.util.UUID;

/**
 * A market listing. The item is taken from the seller when listed
 * and stored here until it is bought or withdrawn.
 *
 * A listing is either a state listing (the money goes to the country's treasury)
 * or a personal one (the money goes to the wallet of the player who listed it).
 */
public final class MarketListing {
    public final long id;
    public final String seller;       // selling country (for personal listings: the seller's country at listing time)
    public final UUID listedBy;       // player who created the listing
    public final String sellerName;   // display name of that player
    public final boolean personal;    // true = player sale, false = state sale
    public final ItemStack stack;
    public final int price;           // price for the whole stack, $
    public final long createdAt;

    public MarketListing(long id, String seller, UUID listedBy, String sellerName, boolean personal,
                         ItemStack stack, int price, long createdAt) {
        this.id = id;
        this.seller = seller;
        this.listedBy = listedBy;
        this.sellerName = sellerName;
        this.personal = personal;
        this.stack = stack;
        this.price = price;
        this.createdAt = createdAt;
    }

    public CompoundTag save(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("id", id);
        tag.putString("seller", seller);
        tag.putUUID("listedBy", listedBy);
        tag.putString("sellerName", sellerName);
        tag.putBoolean("personal", personal);
        tag.put("item", stack.save(provider));
        tag.putInt("price", price);
        tag.putLong("createdAt", createdAt);
        return tag;
    }

    /** Empty if the item can't be read (for example, its mod was removed). Listings from 0.3 load as state listings. */
    public static Optional<MarketListing> load(CompoundTag tag, HolderLookup.Provider provider) {
        Optional<ItemStack> stack = ItemStack.parse(provider, tag.getCompound("item"));
        if (stack.isEmpty() || stack.get().isEmpty() || !tag.hasUUID("listedBy")) return Optional.empty();
        return Optional.of(new MarketListing(
                tag.getLong("id"),
                tag.getString("seller"),
                tag.getUUID("listedBy"),
                tag.getString("sellerName"),
                tag.getBoolean("personal"),
                stack.get(),
                tag.getInt("price"),
                tag.getLong("createdAt")));
    }
}
