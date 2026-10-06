package net.krona.politicsmod.politics;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * One line of the news feed. Arguments are shown as they are, except those starting with '#',
 * which are translation keys (law names and the like), so every player reads the news in their language.
 */
public record NewsEntry(long time, String key, List<String> args, List<String> countries) {

    public static final String TRANSLATE_PREFIX = "#";

    public Component text() {
        Object[] parts = new Object[args.size()];
        for (int i = 0; i < args.size(); i++) {
            String a = args.get(i);
            parts[i] = a.startsWith(TRANSLATE_PREFIX) ? Component.translatable(a.substring(1)) : a;
        }
        return Component.translatable(key, parts);
    }

    public boolean involves(String country) {
        return country != null && countries.contains(country);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong("time", time);
        tag.putString("key", key);
        tag.put("args", strings(args));
        tag.put("countries", strings(countries));
        return tag;
    }

    public static NewsEntry load(CompoundTag tag) {
        return new NewsEntry(tag.getLong("time"), tag.getString("key"),
                strings(tag.getList("args", Tag.TAG_STRING)), strings(tag.getList("countries", Tag.TAG_STRING)));
    }

    private static ListTag strings(List<String> values) {
        ListTag list = new ListTag();
        for (String v : values) list.add(StringTag.valueOf(v));
        return list;
    }

    private static List<String> strings(ListTag list) {
        List<String> values = new ArrayList<>(list.size());
        for (int i = 0; i < list.size(); i++) values.add(list.getString(i));
        return values;
    }
}
