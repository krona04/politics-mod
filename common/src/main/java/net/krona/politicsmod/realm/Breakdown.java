package net.krona.politicsmod.realm;

import net.krona.politicsmod.politics.NewsEntry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * A number explained by its parts: "+5% stability, −30% martial law". The server builds it, the client shows it
 * when the player hovers the number, the chat commands print it. Parts with a zero value are not kept.
 *
 * Labels are translation keys; arguments starting with '#' are translation keys too (as in the news feed).
 */
public record Breakdown(List<Part> parts) {

    public record Part(String key, List<String> args, int value) {
        public Component label() {
            Object[] a = new Object[args.size()];
            for (int i = 0; i < args.size(); i++) {
                String s = args.get(i);
                a[i] = s.startsWith(NewsEntry.TRANSLATE_PREFIX) ? Component.translatable(s.substring(1)) : s;
            }
            return Component.translatable(key, a);
        }
    }

    public static final Breakdown EMPTY = new Breakdown(List.of());

    public int total() {
        int sum = 0;
        for (Part p : parts) sum += p.value;
        return sum;
    }

    public boolean isEmpty() {
        return parts.isEmpty();
    }

    /**
     * One line per part, "Stability +2: +10%". divisor 10 shows tenths (legitimacy trend); unit follows the number.
     * Positive parts are green and negative ones red when goodIsPositive, the other way round otherwise (unrest).
     */
    public List<Component> lines(String unit, int divisor, boolean goodIsPositive) {
        List<Component> out = new ArrayList<>();
        for (Part p : parts) {
            boolean good = (p.value > 0) == goodIsPositive;
            out.add(p.label().copy().withStyle(net.minecraft.ChatFormatting.GRAY).append(": ")
                    .append(Component.literal(format(p.value, divisor) + unit)
                            .withStyle(good ? net.minecraft.ChatFormatting.GREEN : net.minecraft.ChatFormatting.RED)));
        }
        return out;
    }

    /** "+3", "−0.5" — with a sign, tenths when divisor is 10. */
    public static String format(int value, int divisor) {
        String sign = value > 0 ? "+" : value < 0 ? "−" : "";
        int abs = Math.abs(value);
        if (divisor <= 1) return sign + abs;
        return sign + (abs / divisor) + "." + (abs % divisor);
    }

    /** Collects parts in order. */
    public static final class Builder {
        private final List<Part> parts = new ArrayList<>();

        public Builder add(int value, String key, String... args) {
            if (value != 0) parts.add(new Part(key, List.of(args), value));
            return this;
        }

        public Builder addAll(Breakdown other) {
            parts.addAll(other.parts);
            return this;
        }

        public Breakdown build() {
            return new Breakdown(List.copyOf(parts));
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    // ── Network ──────────────────────────────────────────────────────────

    public static void write(FriendlyByteBuf buf, Breakdown b) {
        buf.writeCollection(b.parts, (out, p) -> {
            out.writeUtf(p.key);
            out.writeCollection(p.args, FriendlyByteBuf::writeUtf);
            out.writeInt(p.value);
        });
    }

    public static Breakdown read(FriendlyByteBuf buf) {
        return new Breakdown(buf.readList(in -> new Part(in.readUtf(), in.readList(FriendlyByteBuf::readUtf), in.readInt())));
    }
}
