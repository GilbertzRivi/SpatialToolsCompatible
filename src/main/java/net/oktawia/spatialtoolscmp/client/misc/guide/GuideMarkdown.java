package net.oktawia.spatialtoolscmp.client.misc.guide;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

public final class GuideMarkdown {

    private static final Pattern LIST_ITEM = Pattern.compile("^(\\s*)([-*+]|\\d+[.)])\\s+(.*)$");
    private static final Pattern RULE = Pattern.compile("^(-{3,}|\\*{3,}|_{3,})$");

    private static final int SPACES_PER_INDENT = 2;
    private static final int MAX_HEADING_LEVEL = 3;
    private static final int MAX_LIST_INDENT = 3;

    private static final TextColor BOLD_COLOR = TextColor.fromRgb(0x00CDEE);
    private static final TextColor INLINE_CODE_COLOR = TextColor.fromRgb(0x009AB3);

    private GuideMarkdown() {
    }

    public static List<GuideBlock> parse(List<String> lines) {
        List<GuideBlock> blocks = new ArrayList<>();
        List<String> paragraph = new ArrayList<>();

        boolean inCodeFence = false;

        for (String rawLine : lines) {
            String line = rawLine.stripTrailing();
            String trimmed = line.trim();

            if (trimmed.startsWith("```")) {
                flushParagraph(paragraph, blocks);
                inCodeFence = !inCodeFence;
                continue;
            }

            if (inCodeFence) {
                blocks.add(GuideBlock.code(Component.literal(line)));
                continue;
            }

            if (trimmed.isEmpty()) {
                flushParagraph(paragraph, blocks);
                addSpacer(blocks);
                continue;
            }

            if (RULE.matcher(trimmed).matches()) {
                flushParagraph(paragraph, blocks);
                blocks.add(GuideBlock.rule());
                continue;
            }

            if (trimmed.startsWith("#")) {
                flushParagraph(paragraph, blocks);

                int level = 0;
                while (level < trimmed.length() && trimmed.charAt(level) == '#') {
                    level++;
                }

                String title = trimmed.substring(level).trim();

                blocks.add(GuideBlock.heading(
                        Math.min(level, MAX_HEADING_LEVEL),
                        inline(title).withStyle(ChatFormatting.BOLD)));
                continue;
            }

            if (trimmed.startsWith(">")) {
                flushParagraph(paragraph, blocks);
                blocks.add(GuideBlock.quote(inline(trimmed.substring(1).trim())));
                continue;
            }

            Matcher listItem = LIST_ITEM.matcher(line);

            if (listItem.matches()) {
                flushParagraph(paragraph, blocks);

                int indent = Math.min(listItem.group(1).length() / SPACES_PER_INDENT, MAX_LIST_INDENT);
                String marker = listItem.group(2);

                blocks.add(GuideBlock.listItem(
                        indent,
                        marker.length() == 1 ? "-" : marker,
                        inline(listItem.group(3))));
                continue;
            }

            paragraph.add(trimmed);
        }

        flushParagraph(paragraph, blocks);

        while (!blocks.isEmpty() && blocks.get(blocks.size() - 1).type() == GuideBlock.Type.SPACER) {
            blocks.remove(blocks.size() - 1);
        }

        return blocks;
    }

    private static void addSpacer(List<GuideBlock> blocks) {
        if (blocks.isEmpty() || blocks.get(blocks.size() - 1).type() == GuideBlock.Type.SPACER) {
            return;
        }

        blocks.add(GuideBlock.spacer());
    }

    private static void flushParagraph(List<String> paragraph, List<GuideBlock> blocks) {
        if (paragraph.isEmpty()) {
            return;
        }

        blocks.add(GuideBlock.paragraph(inline(String.join(" ", paragraph))));
        paragraph.clear();
    }

    public static MutableComponent inline(String text) {
        return inline(text, Style.EMPTY);
    }

    private static MutableComponent inline(String text, Style style) {
        MutableComponent result = Component.empty();
        StringBuilder plain = new StringBuilder();

        int index = 0;

        while (index < text.length()) {
            char current = text.charAt(index);

            if (current == '\\' && index + 1 < text.length()) {
                plain.append(text.charAt(index + 1));
                index += 2;
                continue;
            }

            String token = tokenAt(text, index);

            if (token != null) {
                int close = text.indexOf(token, index + token.length());

                if (close >= 0) {
                    if (!plain.isEmpty()) {
                        result.append(Component.literal(plain.toString()).setStyle(style));
                        plain.setLength(0);
                    }

                    String inner = text.substring(index + token.length(), close);

                    if (token.equals("`")) {
                        result.append(Component.literal(inner).setStyle(style.withColor(INLINE_CODE_COLOR)));
                    } else {
                        result.append(inline(inner, applyToken(style, token)));
                    }

                    index = close + token.length();
                    continue;
                }
            }

            plain.append(current);
            index++;
        }

        if (!plain.isEmpty()) {
            result.append(Component.literal(plain.toString()).setStyle(style));
        }

        return result;
    }

    private static String tokenAt(String text, int index) {
        if (text.startsWith("**", index)) {
            return "**";
        }
        if (text.startsWith("__", index)) {
            return "__";
        }
        if (text.startsWith("~~", index)) {
            return "~~";
        }
        if (text.startsWith("`", index)) {
            return "`";
        }
        if (text.startsWith("*", index)) {
            return "*";
        }
        if (text.startsWith("_", index)) {
            return "_";
        }

        return null;
    }

    private static Style applyToken(Style style, String token) {
        return switch (token) {
            case "**", "__" -> style.withBold(true).withColor(BOLD_COLOR);
            case "~~" -> style.withStrikethrough(true);
            default -> style.withItalic(true);
        };
    }
}
