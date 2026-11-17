package net.krona.politicsmod.statecraft;

import net.krona.politicsmod.politics.NewsEntry;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.List;

/**
 * One line of an explanation built on the server: a requirement ("Cities: at least 3 (now 2)") or an effect
 * ("Treasury: +500$"). The key is a translation key; arguments starting with '#' are translation keys too.
 *
 * @param state  NEUTRAL, MET or UNMET; effects are GOOD or BAD
 * @param indent nesting level, for "one of" groups
 */
public record Line(String key, List<String> args, int state, int indent) {

    public static final int NEUTRAL = 0, MET = 1, UNMET = 2, GOOD = 3, BAD = 4;

    public static Line of(String key, int state, String... args) {
        return new Line(key, List.of(args), state, 0);
    }

    public Line indented(int by) {
        return new Line(key, args, state, indent + by);
    }

    public MutableComponent text() {
        Object[] a = new Object[args.size()];
        for (int i = 0; i < args.size(); i++) {
            String s = args.get(i);
            a[i] = s.startsWith(NewsEntry.TRANSLATE_PREFIX) ? Component.translatable(s.substring(1)) : s;
        }
        return Component.translatable(key, a);
    }

    /** With a ✔/✘ mark for requirements and a colour for effects; for tooltips and chat. */
    public Component styled() {
        String pad = "  ".repeat(indent);
        return switch (state) {
            case MET -> Component.literal(pad + "✔ ").withStyle(ChatFormatting.GREEN).append(text().withStyle(ChatFormatting.GRAY));
            case UNMET -> Component.literal(pad + "✘ ").withStyle(ChatFormatting.RED).append(text().withStyle(ChatFormatting.GRAY));
            case GOOD -> Component.literal(pad + "• ").withStyle(ChatFormatting.GREEN).append(text().withStyle(ChatFormatting.GREEN));
            case BAD -> Component.literal(pad + "• ").withStyle(ChatFormatting.RED).append(text().withStyle(ChatFormatting.RED));
            default -> Component.literal(pad + "• ").withStyle(ChatFormatting.GRAY).append(text().withStyle(ChatFormatting.GRAY));
        };
    }

    public static void write(FriendlyByteBuf buf, Line l) {
        buf.writeUtf(l.key);
        buf.writeCollection(l.args, FriendlyByteBuf::writeUtf);
        buf.writeByte(l.state);
        buf.writeByte(l.indent);
    }

    public static Line read(FriendlyByteBuf buf) {
        return new Line(buf.readUtf(), buf.readList(FriendlyByteBuf::readUtf), buf.readByte(), buf.readByte());
    }
}
