package net.krona.politicsmod.politics;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;
import java.util.UUID;

/**
 * A market listing. The item is taken from the seller when listed
 * and stored here until it is bought or withdrawn.
 */
public final class MarketListing {
    public final long id;
    public final String seller;       // selling country
    public final UUID listedBy;       // player who created the listing
    public final ItemStack stack;
    public final int price;           // price for the whole stack, $
    public final long createdAt;

    public MarketListing(long id, String seller, UUID listedBy, ItemStack stack, int price, long createdAt) {
        this.id = id;
        this.seller = seller;
        this.listedBy = listedBy;
        this.stack = stack;
        this.price = price;
        this.createdAt = createdAt;
    }

    public CompoundTag save(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        tag.putLong("id", id);
        tag.putString("seller", seller);
        tag.putUUID("listedBy", listedBy);
        tag.put("item", stack.save(provider));
        tag.putInt("price", price);
        tag.putLong("createdAt", createdAt);
        return tag;
    }

    /** Empty if the item can't be read (for example, its mod was removed). */
    public static Optional<MarketListing> load(CompoundTag tag, HolderLookup.Provider provider) {
        Optional<ItemStack> stack = ItemStack.parse(provider, tag.getCompound("item"));
        if (stack.isEmpty() || stack.get().isEmpty() || !tag.hasUUID("listedBy")) return Optional.empty();
        return Optional.of(new MarketListing(
                tag.getLong("id"),
                tag.getString("seller"),
                tag.getUUID("listedBy"),
                stack.get(),
                tag.getInt("price"),
                tag.getLong("createdAt")));
    }
}
