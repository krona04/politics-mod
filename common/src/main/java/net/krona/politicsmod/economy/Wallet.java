package net.krona.politicsmod.economy;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * A player's personal money. Mutated only through WalletService, which validates
 * every operation on the server.
 */
public final class Wallet {
    private int balance;
    private final Deque<WalletTransaction> history = new ArrayDeque<>();

    public Wallet(int balance) {
        this.balance = balance;
    }

    public int balance() {
        return balance;
    }

    /** Newest first. */
    public List<WalletTransaction> history() {
        return new ArrayList<>(history);
    }

    void apply(int delta, String type, String party, int historyLimit) {
        balance += delta;
        history.addFirst(new WalletTransaction(System.currentTimeMillis(), delta, type, party));
        while (history.size() > historyLimit) history.removeLast();
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("balance", balance);
        ListTag list = new ListTag();
        for (WalletTransaction t : history) list.add(t.save());
        tag.put("history", list);
        return tag;
    }

    public static Wallet load(CompoundTag tag) {
        Wallet wallet = new Wallet(tag.getInt("balance"));
        ListTag list = tag.getList("history", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) wallet.history.addLast(WalletTransaction.load(list.getCompound(i)));
        return wallet;
    }
}
