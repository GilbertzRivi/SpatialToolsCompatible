package net.oktawia.spatialtoolscmp.client.misc.guide;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

public record GuideLayout(List<GuideLine> lines, int height, int sectionCount) {

    public static final int SECTION_HEADING_LEVEL = 2;

    public enum Decoration {
        NONE,
        CODE,
        QUOTE,
        RULE,
        SECTION
    }

    public record GuideLine(
            FormattedCharSequence text,
            int x,
            int y,
            float scale,
            int color,
            Decoration decoration,
            int sectionIndex) {

        public GuideLine(FormattedCharSequence text, int x, int y, float scale, int color, Decoration decoration) {
            this(text, x, y, scale, color, decoration, -1);
        }
    }

    public static int sectionRowHeight(Font font) {
        return lineHeight(font, HEADING_SCALES[SECTION_HEADING_LEVEL - 1]) + SECTION_ROW_PADDING * 2;
    }

    public static int countSections(List<GuideBlock> blocks) {
        int count = 0;

        for (GuideBlock block : blocks) {
            if (block.type() == GuideBlock.Type.HEADING && block.level() == SECTION_HEADING_LEVEL) {
                count++;
            }
        }

        return count;
    }

    private static final float[] HEADING_SCALES = { 1.6F, 1.3F, 1.15F };

    private static final int HEADING_COLOR = 0xFFFFFFFF;
    private static final int TEXT_COLOR = 0xFFCFCFCF;
    private static final int QUOTE_COLOR = 0xFFA0A0A0;
    private static final int CODE_COLOR = 0xFF9CDCFE;

    private static final int LINE_SPACING = 2;
    private static final int BLOCK_SPACING = 5;
    private static final int HEADING_SPACING_ABOVE = 9;
    private static final int RULE_HEIGHT = 11;

    private static final int LIST_INDENT = 10;
    private static final int LIST_MARKER_GAP = 4;
    private static final int QUOTE_INDENT = 8;
    private static final int QUOTE_SPACING = 7;
    private static final int CODE_INDENT = 4;
    public static final int SECTION_ROW_PADDING = 3;

    public static GuideLayout build(Font font, List<GuideBlock> blocks, int width, Set<Integer> collapsedSections) {
        List<GuideLine> lines = new ArrayList<>();
        int y = 0;
        int trailingSpacing = 0;

        int sectionIndex = -1;
        boolean hidden = false;

        for (GuideBlock block : blocks) {
            boolean isSectionHeading = block.type() == GuideBlock.Type.HEADING
                    && block.level() == SECTION_HEADING_LEVEL;

            if (isSectionHeading) {
                sectionIndex++;
                hidden = collapsedSections.contains(sectionIndex);
            } else if (block.type() == GuideBlock.Type.HEADING && block.level() < SECTION_HEADING_LEVEL) {
                hidden = false;
            } else if (hidden) {
                continue;
            }

            switch (block.type()) {
                case HEADING -> {
                    float scale = HEADING_SCALES[Math.min(block.level(), HEADING_SCALES.length) - 1];

                    if (y > 0) {
                        y += HEADING_SPACING_ABOVE;
                    }

                    boolean firstLine = true;

                    for (FormattedCharSequence text : font.split(block.text(), (int) (width / scale))) {
                        boolean sectionRow = isSectionHeading && firstLine;

                        lines.add(new GuideLine(
                                text,
                                sectionRow ? SECTION_ROW_PADDING : 0,
                                sectionRow ? y + SECTION_ROW_PADDING : y,
                                scale,
                                HEADING_COLOR,
                                sectionRow ? Decoration.SECTION : Decoration.NONE,
                                sectionRow ? sectionIndex : -1));

                        y += sectionRow ? sectionRowHeight(font) : lineHeight(font, scale);
                        firstLine = false;
                    }

                    y += BLOCK_SPACING;
                    trailingSpacing = BLOCK_SPACING;
                }
                case PARAGRAPH -> {
                    for (FormattedCharSequence text : font.split(block.text(), width)) {
                        lines.add(new GuideLine(text, 0, y, 1.0F, TEXT_COLOR, Decoration.NONE));
                        y += lineHeight(font, 1.0F);
                    }

                    y += BLOCK_SPACING;
                    trailingSpacing = BLOCK_SPACING;
                }
                case LIST_ITEM -> {
                    int markerX = LIST_INDENT * block.level();
                    int textX = markerX + font.width(block.marker()) + LIST_MARKER_GAP;

                    lines.add(new GuideLine(
                            Component.literal(block.marker()).getVisualOrderText(),
                            markerX,
                            y,
                            1.0F,
                            TEXT_COLOR,
                            Decoration.NONE));

                    for (FormattedCharSequence text : font.split(block.text(), width - textX)) {
                        lines.add(new GuideLine(text, textX, y, 1.0F, TEXT_COLOR, Decoration.NONE));
                        y += lineHeight(font, 1.0F);
                    }

                    y += LINE_SPACING;
                    trailingSpacing = LINE_SPACING;
                }
                case QUOTE -> {
                    y += Math.max(0, QUOTE_SPACING - trailingSpacing);

                    for (FormattedCharSequence text : font.split(block.text(), width - QUOTE_INDENT)) {
                        lines.add(new GuideLine(text, QUOTE_INDENT, y, 1.0F, QUOTE_COLOR, Decoration.QUOTE));
                        y += lineHeight(font, 1.0F);
                    }

                    y += QUOTE_SPACING;
                    trailingSpacing = QUOTE_SPACING;
                }
                case CODE -> {
                    for (FormattedCharSequence text : font.split(block.text(), width - CODE_INDENT * 2)) {
                        lines.add(new GuideLine(text, CODE_INDENT, y, 1.0F, CODE_COLOR, Decoration.CODE));
                        y += lineHeight(font, 1.0F);
                    }

                    trailingSpacing = 0;
                }
                case RULE -> {
                    lines.add(new GuideLine(null, 0, y, 1.0F, TEXT_COLOR, Decoration.RULE));
                    y += RULE_HEIGHT;
                    trailingSpacing = RULE_HEIGHT;
                }
                case SPACER -> {
                    int missing = Math.max(0, BLOCK_SPACING - trailingSpacing);

                    y += missing;
                    trailingSpacing += missing;
                }
            }
        }

        return new GuideLayout(lines, y, sectionIndex + 1);
    }

    private static int lineHeight(Font font, float scale) {
        return Math.round(font.lineHeight * scale) + LINE_SPACING;
    }
}
