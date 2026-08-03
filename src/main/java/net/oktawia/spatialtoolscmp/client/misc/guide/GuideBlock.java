package net.oktawia.spatialtoolscmp.client.misc.guide;

import net.minecraft.network.chat.Component;

public record GuideBlock(Type type, int level, String marker, Component text) {

    public enum Type {
        HEADING,
        PARAGRAPH,
        LIST_ITEM,
        QUOTE,
        CODE,
        RULE,
        SPACER
    }

    public static GuideBlock heading(int level, Component text) {
        return new GuideBlock(Type.HEADING, level, "", text);
    }

    public static GuideBlock paragraph(Component text) {
        return new GuideBlock(Type.PARAGRAPH, 0, "", text);
    }

    public static GuideBlock listItem(int indent, String marker, Component text) {
        return new GuideBlock(Type.LIST_ITEM, indent, marker, text);
    }

    public static GuideBlock quote(Component text) {
        return new GuideBlock(Type.QUOTE, 0, "", text);
    }

    public static GuideBlock code(Component text) {
        return new GuideBlock(Type.CODE, 0, "", text);
    }

    public static GuideBlock rule() {
        return new GuideBlock(Type.RULE, 0, "", Component.empty());
    }

    public static GuideBlock spacer() {
        return new GuideBlock(Type.SPACER, 0, "", Component.empty());
    }
}
