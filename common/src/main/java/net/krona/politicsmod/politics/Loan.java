package net.krona.politicsmod.politics;

import net.minecraft.nbt.CompoundTag;

/** Money one country lent another; repaid in equal installments every economy cycle. */
public class Loan {
    public final String lender;
    public final String borrower;
    /** Amount still owed, interest included. */
    public int remaining;
    public final int installment;

    public Loan(String lender, String borrower, int remaining, int installment) {
        this.lender = lender;
        this.borrower = borrower;
        this.remaining = remaining;
        this.installment = installment;
    }

    /** A new loan: the amount plus interest, split into equal installments. */
    public static Loan create(String lender, String borrower, int amount, int interestPercent, int cycles) {
        long total = Math.min(Integer.MAX_VALUE, (long) amount * (100 + interestPercent) / 100);
        int installment = (int) Math.ceil(total / (double) Math.max(1, cycles));
        return new Loan(lender, borrower, (int) total, Math.max(1, installment));
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("lender", lender);
        tag.putString("borrower", borrower);
        tag.putInt("remaining", remaining);
        tag.putInt("installment", installment);
        return tag;
    }

    public static Loan load(CompoundTag tag) {
        return new Loan(tag.getString("lender"), tag.getString("borrower"), tag.getInt("remaining"), tag.getInt("installment"));
    }
}
