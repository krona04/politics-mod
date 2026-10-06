package net.krona.politicsmod.economy;

import net.minecraft.nbt.CompoundTag;

/**
 * One line of a wallet's history.
 *
 * @param amount signed change of the balance: positive for income, negative for spending
 * @param type   one of the TYPE_* keys; used to build the translation key for the history screen
 * @param party  the other side of the operation (player or country name), "" if none
 */
public record WalletTransaction(long time, int amount, String type, String party) {

    public static final String TYPE_SALARY = "salary";
    public static final String TYPE_INCOME_TAX = "income_tax";
    public static final String TYPE_TRANSFER_IN = "transfer_in";
    public static final String TYPE_TRANSFER_OUT = "transfer_out";
    public static final String TYPE_DONATION = "donation";
    public static final String TYPE_ADMIN = "admin";
    public static final String TYPE_EXCHANGE_IN = "exchange_in";
    public static final String TYPE_EXCHANGE_OUT = "exchange_out";
    public static final String TYPE_PURCHASE = "purchase";
    public static final String TYPE_SALE = "sale";
    public static final String TYPE_PLOT = "plot";
    public static final String TYPE_RENT = "rent";

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("time", time);
        tag.putInt("amount", amount);
        tag.putString("type", type);
        tag.putString("party", party);
        return tag;
    }

    public static WalletTransaction load(CompoundTag tag) {
        return new WalletTransaction(tag.getLong("time"), tag.getInt("amount"), tag.getString("type"), tag.getString("party"));
    }
}
