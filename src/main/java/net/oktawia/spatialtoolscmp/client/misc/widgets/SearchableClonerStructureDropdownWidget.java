package net.oktawia.spatialtoolscmp.client.misc.widgets;

import lombok.Getter;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.oktawia.spatialtoolscmp.client.misc.ClonerStructureFileTransferClient;
import net.oktawia.spatialtoolscmp.client.misc.ClonerStructureLibraryClientCache;
import net.oktawia.spatialtoolscmp.defs.LangDefs;
import net.oktawia.spatialtoolscmp.logic.ClonerStructureLibraryStore;
import net.oktawia.spatialtoolscmp.network.NetworkHandler;
import net.oktawia.spatialtoolscmp.network.packets.CreateClonerFolderPacket;
import net.oktawia.spatialtoolscmp.network.packets.DeleteClonerFolderPacket;
import net.oktawia.spatialtoolscmp.network.packets.DeleteClonerStructurePacket;
import net.oktawia.spatialtoolscmp.network.packets.MoveClonerStructureToFolderPacket;
import net.oktawia.spatialtoolscmp.network.packets.RenameClonerStructurePacket;
import net.oktawia.spatialtoolscmp.network.packets.SelectClonerStructurePacket;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.IntSupplier;

public class SearchableClonerStructureDropdownWidget extends AbstractWidget {

    private static final int SEARCH_HEIGHT = 14;
    private static final int SELECTED_HEIGHT = 13;
    private static final int ROW_HEIGHT = 18;
    private static final int RENAME_HEIGHT = 14;
    private static final int BUTTON_HEIGHT = 16;
    private static final int PADDING = 2;
    private static final int SCROLLBAR_WIDTH = 4;
    private static final int DELETE_BUTTON_SIZE = 12;
    private static final int DROPDOWN_HEIGHT = 172;
    private static final int ADD_FOLDER_BTN = 14;

    private enum RowKind { EMPTY, BACK, CANCEL_MOVE, FOLDER, STRUCTURE }

    private final EditBox searchBox;
    private final EditBox renameBox;
    private final IntSupplier containerIdSupplier;

    @Getter
    private boolean open = false;

    private int scrollOffset = 0;
    private String highlightedId = "";
    private String lastSelectedId = null;

    private String currentFolder = "";
    private boolean creatingFolder = false;
    private String movingStructureId = null;

    private boolean hoveringExport = false;
    private boolean hoveringImport = false;
    private boolean hoveringAddFolder = false;

    public SearchableClonerStructureDropdownWidget(
            int x,
            int y,
            int width,
            int height,
            IntSupplier containerIdSupplier
    ) {
        super(x, y, width, height, Component.empty());

        this.containerIdSupplier = containerIdSupplier == null ? () -> -1 : containerIdSupplier;

        this.searchBox = new EditBox(
                Minecraft.getInstance().font,
                x + PADDING,
                y + PADDING,
                Math.max(20, width - PADDING * 2 - ADD_FOLDER_BTN - 2),
                SEARCH_HEIGHT,
                Component.translatable(LangDefs.STRUCTURE_GADGET_CLONER_SEARCH.getTranslationKey())
        );
        this.searchBox.setMaxLength(ClonerStructureLibraryStore.MAX_NAME_LENGTH);
        this.searchBox.setHint(Component.translatable(LangDefs.STRUCTURE_GADGET_CLONER_SEARCH.getTranslationKey()));
        this.searchBox.setResponder(ignored -> this.scrollOffset = 0);

        this.renameBox = new EditBox(
                Minecraft.getInstance().font,
                x + PADDING,
                y,
                Math.max(20, getDropdownWidth() - PADDING * 2),
                RENAME_HEIGHT,
                Component.translatable(LangDefs.STRUCTURE_GADGET_CLONER_NAME.getTranslationKey())
        );
        this.renameBox.setMaxLength(ClonerStructureLibraryStore.MAX_NAME_LENGTH);
        this.renameBox.setHint(Component.translatable(LangDefs.STRUCTURE_GADGET_CLONER_NAME.getTranslationKey()));
    }

    public void refreshFromClientCache() {
        String selectedId = ClonerStructureLibraryClientCache.selectedId();

        if (!Objects.equals(this.lastSelectedId, selectedId)) {
            this.lastSelectedId = selectedId;
            this.highlightedId = selectedId == null ? "" : selectedId;
            syncRenameBoxToHighlighted();
        }

        if (!currentFolder.isEmpty() && !ClonerStructureLibraryClientCache.folders().contains(currentFolder)) {
            currentFolder = "";
        }

        this.scrollOffset = Mth.clamp(this.scrollOffset, 0, getMaxScroll());
    }

    public boolean wantsKeyboardCapture() {
        return this.visible
                && this.active
                && (this.open || this.searchBox.isFocused() || this.renameBox.isFocused());
    }

    public boolean isExpandedMouseOver(double mouseX, double mouseY) {
        if (!this.visible || !this.open) {
            return false;
        }

        int left = getX();
        int top = getY();
        int right = left + getDropdownWidth();
        int bottom = top + height + DROPDOWN_HEIGHT + 1;

        return mouseX >= left && mouseX < right && mouseY >= top && mouseY < bottom;
    }

    public @Nullable Component getHoveredTooltip(double mouseX, double mouseY) {
        if (!this.visible) {
            return null;
        }

        if (this.hoveringAddFolder) {
            return Component.translatable(LangDefs.STRUCTURE_GADGET_CLONER_ADD_FOLDER_TOOLTIP.getTranslationKey());
        }

        if (!this.open) {
            return null;
        }

        if (this.hoveringExport) {
            return Component.translatable(LangDefs.STRUCTURE_GADGET_CLONER_EXPORT_TOOLTIP.getTranslationKey());
        }

        if (this.hoveringImport) {
            return Component.translatable(LangDefs.STRUCTURE_GADGET_CLONER_IMPORT_TOOLTIP.getTranslationKey());
        }

        return null;
    }

    public void move(int x, int y) {
        setX(x);
        setY(y);
        updateEditBoxBounds();
    }

    public void resize(int width, int height) {
        this.width = width;
        this.height = height;
        updateEditBoxBounds();
        this.scrollOffset = Mth.clamp(this.scrollOffset, 0, getMaxScroll());
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!this.visible || !this.active) {
            return false;
        }

        int addFolderLeft = getX() + width - PADDING - ADD_FOLDER_BTN;
        int addFolderTop = getY() + PADDING;

        if (mouseX >= addFolderLeft && mouseX < addFolderLeft + ADD_FOLDER_BTN
                && mouseY >= addFolderTop && mouseY < addFolderTop + ADD_FOLDER_BTN) {
            this.open = true;
            this.creatingFolder = true;
            this.movingStructureId = null;
            this.renameBox.setValue("");
            this.renameBox.setHint(Component.translatable(LangDefs.STRUCTURE_GADGET_CLONER_ADD_FOLDER.getTranslationKey()));
            this.renameBox.setFocused(true);
            this.searchBox.setFocused(false);
            return true;
        }

        boolean insideBase = isMouseOver(mouseX, mouseY);
        boolean insideExpanded = isExpandedMouseOver(mouseX, mouseY);

        if (!insideBase && !insideExpanded) {
            this.open = false;
            this.searchBox.setFocused(false);
            this.renameBox.setFocused(false);
            return false;
        }

        if (this.searchBox.mouseClicked(mouseX, mouseY, button)) {
            this.open = true;
            this.searchBox.setFocused(true);
            this.renameBox.setFocused(false);
            return true;
        }

        if (this.open && this.renameBox.mouseClicked(mouseX, mouseY, button)) {
            this.searchBox.setFocused(false);
            this.renameBox.setFocused(true);
            return true;
        }

        int selectedTop = getY() + PADDING + SEARCH_HEIGHT + 1;
        int selectedBottom = selectedTop + SELECTED_HEIGHT;

        if (mouseX >= getX()
                && mouseX < getX() + width
                && mouseY >= selectedTop
                && mouseY < selectedBottom) {
            this.open = !this.open;
            this.searchBox.setFocused(false);
            this.renameBox.setFocused(false);
            return true;
        }

        if (this.open && insideExpanded) {
            this.searchBox.setFocused(false);
            this.renameBox.setFocused(false);
            return handleDropdownClick(mouseX, mouseY);
        }

        this.open = true;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!this.visible || !this.active || !isExpandedMouseOver(mouseX, mouseY)) {
            return false;
        }

        this.scrollOffset = Mth.clamp(
                this.scrollOffset - (int) Math.signum(delta) * ROW_HEIGHT,
                0,
                getMaxScroll()
        );

        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (!this.visible || !this.active) {
            return false;
        }

        if (this.searchBox.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                this.open = true;
                return true;
            }

            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                this.open = false;
                this.searchBox.setFocused(false);
                return true;
            }

            return this.searchBox.keyPressed(keyCode, scanCode, modifiers);
        }

        if (this.renameBox.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                if (this.creatingFolder) {
                    confirmCreateFolder();
                } else {
                    renameHighlighted();
                }
                return true;
            }

            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                if (this.creatingFolder) {
                    cancelCreateFolder();
                } else {
                    this.renameBox.setFocused(false);
                }
                return true;
            }

            return this.renameBox.keyPressed(keyCode, scanCode, modifiers);
        }

        if (!this.open) {
            return false;
        }

        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            if (this.movingStructureId == null && !this.creatingFolder) {
                selectHighlighted();
            }
            return true;
        }

        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (this.movingStructureId != null) {
                this.movingStructureId = null;
            } else if (this.creatingFolder) {
                cancelCreateFolder();
            } else if (!this.currentFolder.isEmpty()) {
                this.currentFolder = "";
            } else {
                this.open = false;
            }
            return true;
        }

        return false;
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (!this.visible || !this.active) {
            return false;
        }

        if (this.searchBox.isFocused()) {
            return this.searchBox.charTyped(codePoint, modifiers);
        }

        if (this.renameBox.isFocused()) {
            return this.renameBox.charTyped(codePoint, modifiers);
        }

        return false;
    }

    @Override
    protected void renderWidget(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (!this.visible) {
            return;
        }

        updateEditBoxBounds();

        int left = getX();
        int top = getY();
        int right = left + width;
        int bottom = top + height;

        guiGraphics.fill(left, top, right, bottom, 0xFF111111);
        drawBorder(guiGraphics, left, top, right, bottom, 0xFF666666);

        this.searchBox.render(guiGraphics, mouseX, mouseY, partialTick);
        renderAddFolderButton(guiGraphics, mouseX, mouseY);
        renderSelectedRow(guiGraphics, mouseX, mouseY);
    }

    public void renderDropdownOverlay(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        if (!this.visible || !this.open) {
            this.hoveringExport = false;
            this.hoveringImport = false;
            return;
        }

        updateEditBoxBounds();
        renderDropdown(guiGraphics, mouseX, mouseY, partialTick);
    }

    private void renderAddFolderButton(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        Minecraft minecraft = Minecraft.getInstance();
        int btnLeft = getX() + width - PADDING - ADD_FOLDER_BTN;
        int btnTop = getY() + PADDING;
        int btnRight = btnLeft + ADD_FOLDER_BTN;
        int btnBottom = btnTop + ADD_FOLDER_BTN;

        boolean hovered = mouseX >= btnLeft && mouseX < btnRight && mouseY >= btnTop && mouseY < btnBottom;
        this.hoveringAddFolder = hovered;

        guiGraphics.fill(btnLeft, btnTop, btnRight, btnBottom, hovered ? 0xFF1F6F1F : 0xFF223322);
        drawBorder(guiGraphics, btnLeft, btnTop, btnRight, btnBottom, 0xFF448844);
        guiGraphics.drawString(minecraft.font, "+", btnLeft + 4, btnTop + 3, 0xFF55FF55, false);
    }

    private void renderSelectedRow(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        Minecraft minecraft = Minecraft.getInstance();

        int left = getX() + PADDING;
        int top = getY() + PADDING + SEARCH_HEIGHT + 1;
        int right = getX() + width - PADDING;
        int bottom = top + SELECTED_HEIGHT;

        boolean hovered = mouseX >= left && mouseX < right && mouseY >= top && mouseY < bottom;

        guiGraphics.fill(left, top, right, bottom, hovered ? 0xFF1F6F6F : 0xFF222222);

        String text = getSelectedDisplayName();
        text = minecraft.font.plainSubstrByWidth(text, Math.max(1, right - left - 4));

        guiGraphics.drawString(
                minecraft.font,
                text,
                left + 2,
                top + 3,
                0xFF55FFFF,
                false
        );
    }

    private void renderDropdown(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int left = getX();
        int top = getY() + height + 1;
        int dropdownWidth = getDropdownWidth();
        int right = left + dropdownWidth;
        int bottom = top + DROPDOWN_HEIGHT;

        guiGraphics.fill(left, top, right, bottom, 0xFF111111);
        drawBorder(guiGraphics, left, top, right, bottom, 0xFF777777);

        int bottomButtonsTop = bottom - PADDING - BUTTON_HEIGHT;
        int middleButtonsTop = bottomButtonsTop - BUTTON_HEIGHT - 2;
        int topButtonsTop = middleButtonsTop - BUTTON_HEIGHT - 2;
        int renameTop = topButtonsTop - RENAME_HEIGHT - 4;

        int listLeft = left + PADDING;
        int listTop = top + PADDING;
        int listRight = right - PADDING - SCROLLBAR_WIDTH - 1;
        int listBottom = renameTop - 3;

        renderRows(guiGraphics, mouseX, mouseY, listLeft, listTop, listRight, listBottom);
        renderScrollbar(guiGraphics, listTop, listBottom, right);

        this.renameBox.render(guiGraphics, mouseX, mouseY, partialTick);

        renderTopButtons(guiGraphics, mouseX, mouseY, topButtonsTop);
        renderMiddleButtons(guiGraphics, mouseX, mouseY, middleButtonsTop);
        renderBottomButtons(guiGraphics, mouseX, mouseY, bottomButtonsTop);
    }

    private void renderRows(
            GuiGraphics guiGraphics,
            int mouseX,
            int mouseY,
            int listLeft,
            int listTop,
            int listRight,
            int listBottom
    ) {
        Minecraft minecraft = Minecraft.getInstance();

        guiGraphics.enableScissor(listLeft, listTop, listRight, listBottom);

        int startIndex = scrollOffset / ROW_HEIGHT;
        int startOffsetY = -(scrollOffset % ROW_HEIGHT);
        int visibleRows = ((listBottom - listTop) / ROW_HEIGHT) + 2;
        int rowCount = getRowCount();

        for (int i = 0; i < visibleRows; i++) {
            int rowIndex = startIndex + i;

            if (rowIndex >= rowCount) {
                break;
            }

            int rowTop = listTop + startOffsetY + i * ROW_HEIGHT;
            int rowBottom = rowTop + ROW_HEIGHT - 1;

            if (rowBottom < listTop || rowTop > listBottom) {
                continue;
            }

            RowKind kind = getRowKind(rowIndex);
            String rowId = getRowId(rowIndex);
            boolean highlighted = kind == RowKind.STRUCTURE && Objects.equals(rowId, highlightedId);
            boolean hovered = mouseX >= listLeft && mouseX < listRight && mouseY >= rowTop && mouseY < rowBottom;

            boolean isMovingRow = movingStructureId != null && kind == RowKind.STRUCTURE
                    && Objects.equals(rowId, movingStructureId);

            int bgColor = switch (kind) {
                case FOLDER -> highlighted ? 0xFF2A2A5A : hovered ? 0xFF252540 : 0xFF1E1E2E;
                case BACK, CANCEL_MOVE -> hovered ? 0xFF253535 : 0xFF1A2020;
                case STRUCTURE -> isMovingRow
                        ? (hovered ? 0xFF6A5000 : 0xFF4A3800)
                        : highlighted ? 0xFF2F8A8A : hovered ? 0xFF1F6F6F : ((rowIndex & 1) == 0 ? 0xFF222222 : 0xFF2E2E2E);
                default -> hovered ? 0xFF1F6F6F : 0xFF222222;
            };

            guiGraphics.fill(listLeft, rowTop, listRight, rowBottom, bgColor);

            int textX = listLeft + 2;
            int textColor;
            String rowText = getRowDisplayName(rowIndex);

            boolean canDelete = false;
            boolean isFolder = kind == RowKind.FOLDER;

            if (kind == RowKind.FOLDER) {
                textColor = 0xFFAAAAFF;
                String folderName = getRowFolderName(rowIndex);
                canDelete = isFolderEmpty(folderName);
            } else if (kind == RowKind.BACK || kind == RowKind.CANCEL_MOVE) {
                textColor = 0xFF88CCCC;
            } else if (kind == RowKind.EMPTY) {
                textColor = 0xFFAAAAAA;
            } else {
                textColor = isMovingRow ? 0xFFFFCC44 : 0xFFFFFFFF;
                textX = currentFolder.isEmpty() ? listLeft + 2 : listLeft + 10;
            }

            if (kind == RowKind.STRUCTURE && !rowId.isBlank() && movingStructureId == null) {
                int deleteRight = listRight - 2;
                int deleteLeft = deleteRight - DELETE_BUTTON_SIZE;
                int textRight = deleteLeft - 2;
                rowText = minecraft.font.plainSubstrByWidth(rowText, Math.max(1, textRight - textX - 2));
                guiGraphics.drawString(minecraft.font, rowText, textX, rowTop + 5, textColor, false);

                int deleteTop = rowTop + 3;
                int deleteBottom = deleteTop + DELETE_BUTTON_SIZE;
                boolean deleteHovered = mouseX >= deleteLeft && mouseX < deleteRight && mouseY >= deleteTop && mouseY < deleteBottom;
                guiGraphics.fill(deleteLeft, deleteTop, deleteRight, deleteBottom, deleteHovered ? 0xFFFF4040 : 0xFF552222);
                drawBorder(guiGraphics, deleteLeft, deleteTop, deleteRight, deleteBottom, 0xFFAA5555);
                guiGraphics.drawString(minecraft.font, "x", deleteLeft + 3, deleteTop + 2, 0xFFFFFFFF, false);
            } else if (isFolder && canDelete) {
                int deleteRight = listRight - 2;
                int deleteLeft = deleteRight - DELETE_BUTTON_SIZE;
                int textRight = deleteLeft - 2;
                rowText = minecraft.font.plainSubstrByWidth(rowText, Math.max(1, textRight - textX - 2));
                guiGraphics.drawString(minecraft.font, rowText, textX, rowTop + 5, textColor, false);

                int deleteTop = rowTop + 3;
                int deleteBottom = deleteTop + DELETE_BUTTON_SIZE;
                boolean deleteHovered = mouseX >= deleteLeft && mouseX < deleteRight && mouseY >= deleteTop && mouseY < deleteBottom;
                guiGraphics.fill(deleteLeft, deleteTop, deleteRight, deleteBottom, deleteHovered ? 0xFFFF4040 : 0xFF552222);
                drawBorder(guiGraphics, deleteLeft, deleteTop, deleteRight, deleteBottom, 0xFFAA5555);
                guiGraphics.drawString(minecraft.font, "x", deleteLeft + 3, deleteTop + 2, 0xFFFFFFFF, false);
            } else {
                rowText = minecraft.font.plainSubstrByWidth(rowText, Math.max(1, listRight - textX - 4));
                guiGraphics.drawString(minecraft.font, rowText, textX, rowTop + 5, textColor, false);
            }
        }

        guiGraphics.disableScissor();
    }

    private void renderTopButtons(GuiGraphics guiGraphics, int mouseX, int mouseY, int top) {
        int left = getX() + PADDING;
        int right = getX() + getDropdownWidth() - PADDING;
        int middle = left + (right - left) / 2;

        if (this.creatingFolder) {
            renderButton(guiGraphics, mouseX, mouseY, left, top, middle - 1, top + BUTTON_HEIGHT,
                    Component.translatable(LangDefs.STRUCTURE_GADGET_CLONER_CREATE.getTranslationKey()).getString(),
                    0xFF55FFFF);
            renderButton(guiGraphics, mouseX, mouseY, middle + 1, top, right, top + BUTTON_HEIGHT,
                    Component.translatable(LangDefs.STRUCTURE_GADGET_CLONER_CANCEL.getTranslationKey()).getString(),
                    0xFFFFFF55);
        } else {
            renderButton(guiGraphics, mouseX, mouseY, left, top, middle - 1, top + BUTTON_HEIGHT,
                    Component.translatable(LangDefs.STRUCTURE_GADGET_CLONER_SELECT.getTranslationKey()).getString(),
                    0xFF55FFFF);
            renderButton(guiGraphics, mouseX, mouseY, middle + 1, top, right, top + BUTTON_HEIGHT,
                    Component.translatable(LangDefs.STRUCTURE_GADGET_CLONER_RENAME.getTranslationKey()).getString(),
                    highlightedId == null || highlightedId.isBlank() ? 0xFF777777 : 0xFFFFFF55);
        }
    }

    private void renderMiddleButtons(GuiGraphics guiGraphics, int mouseX, int mouseY, int top) {
        int left = getX() + PADDING;
        int right = getX() + getDropdownWidth() - PADDING;

        boolean canMove = !highlightedId.isBlank() && movingStructureId == null && !creatingFolder;
        boolean inFolder = canMove && isHighlightedInFolder();

        LangDefs label = inFolder
                ? LangDefs.STRUCTURE_GADGET_CLONER_REMOVE_FROM_FOLDER
                : LangDefs.STRUCTURE_GADGET_CLONER_MOVE_TO_FOLDER;

        int color = !canMove ? 0xFF777777 : (inFolder ? 0xFF55FF55 : 0xFF55FFFF);

        renderButton(guiGraphics, mouseX, mouseY, left, top, right, top + BUTTON_HEIGHT,
                Component.translatable(label.getTranslationKey()).getString(),
                color);
    }

    private void renderBottomButtons(GuiGraphics guiGraphics, int mouseX, int mouseY, int top) {
        int left = getX() + PADDING;
        int right = getX() + getDropdownWidth() - PADDING;
        int middle = left + (right - left) / 2;

        this.hoveringExport = mouseX >= left && mouseX < middle - 1 && mouseY >= top && mouseY < top + BUTTON_HEIGHT;
        this.hoveringImport = mouseX >= middle + 1 && mouseX < right && mouseY >= top && mouseY < top + BUTTON_HEIGHT;

        renderButton(guiGraphics, mouseX, mouseY, left, top, middle - 1, top + BUTTON_HEIGHT,
                Component.translatable(LangDefs.STRUCTURE_GADGET_CLONER_EXPORT.getTranslationKey()).getString(),
                highlightedId == null || highlightedId.isBlank() ? 0xFF777777 : 0xFF55FF55);

        renderButton(guiGraphics, mouseX, mouseY, middle + 1, top, right, top + BUTTON_HEIGHT,
                Component.translatable(LangDefs.STRUCTURE_GADGET_CLONER_IMPORT.getTranslationKey()).getString(),
                0xFF55FF55);
    }

    private void renderButton(
            GuiGraphics guiGraphics,
            int mouseX,
            int mouseY,
            int left,
            int top,
            int right,
            int bottom,
            String text,
            int textColor
    ) {
        Minecraft minecraft = Minecraft.getInstance();
        boolean hovered = mouseX >= left && mouseX < right && mouseY >= top && mouseY < bottom;

        guiGraphics.fill(left, top, right, bottom, hovered ? 0xFF1F6F6F : 0xFF333333);
        drawBorder(guiGraphics, left, top, right, bottom, 0xFF666666);

        String clipped = minecraft.font.plainSubstrByWidth(text, Math.max(1, right - left - 4));

        guiGraphics.drawString(
                minecraft.font,
                clipped,
                left + 3,
                top + 4,
                textColor,
                false
        );
    }

    private boolean handleDropdownClick(double mouseX, double mouseY) {
        int dropdownLeft = getX();
        int dropdownTop = getY() + height + 1;
        int dropdownRight = dropdownLeft + getDropdownWidth();
        int dropdownBottom = dropdownTop + DROPDOWN_HEIGHT;

        int bottomButtonsTop = dropdownBottom - PADDING - BUTTON_HEIGHT;
        int middleButtonsTop = bottomButtonsTop - BUTTON_HEIGHT - 2;
        int topButtonsTop = middleButtonsTop - BUTTON_HEIGHT - 2;
        int renameTop = topButtonsTop - RENAME_HEIGHT - 4;

        int buttonLeft = dropdownLeft + PADDING;
        int buttonRight = dropdownRight - PADDING;
        int buttonMiddle = buttonLeft + (buttonRight - buttonLeft) / 2;

        if (mouseY >= topButtonsTop && mouseY < topButtonsTop + BUTTON_HEIGHT) {
            if (mouseX >= buttonLeft && mouseX < buttonMiddle - 1) {
                if (this.creatingFolder) {
                    confirmCreateFolder();
                } else {
                    selectHighlighted();
                }
                return true;
            }
            if (mouseX >= buttonMiddle + 1 && mouseX < buttonRight) {
                if (this.creatingFolder) {
                    cancelCreateFolder();
                } else {
                    renameHighlighted();
                }
                return true;
            }
        }

        if (mouseY >= middleButtonsTop && mouseY < middleButtonsTop + BUTTON_HEIGHT) {
            if (mouseX >= buttonLeft && mouseX < buttonRight) {
                if (!highlightedId.isBlank() && movingStructureId == null && !creatingFolder) {
                    if (isHighlightedInFolder()) {
                        NetworkHandler.sendToServer(new MoveClonerStructureToFolderPacket(
                                this.containerIdSupplier.getAsInt(), this.highlightedId, ""));
                        this.movingStructureId = null;
                    } else {
                        this.movingStructureId = this.highlightedId;
                        this.scrollOffset = 0;
                    }
                }
                return true;
            }
        }

        if (mouseY >= bottomButtonsTop && mouseY < bottomButtonsTop + BUTTON_HEIGHT) {
            if (mouseX >= buttonLeft && mouseX < buttonMiddle - 1) {
                exportHighlighted();
                return true;
            }
            if (mouseX >= buttonMiddle + 1 && mouseX < buttonRight) {
                importStructure();
                return true;
            }
        }

        int listLeft = dropdownLeft + PADDING;
        int listTop = dropdownTop + PADDING;
        int listRight = dropdownRight - PADDING - SCROLLBAR_WIDTH - 1;
        int listBottom = renameTop - 3;

        if (mouseX < listLeft || mouseX >= listRight || mouseY < listTop || mouseY >= listBottom) {
            return true;
        }

        int localY = (int) mouseY - listTop + scrollOffset;
        int rowIndex = localY / ROW_HEIGHT;

        if (rowIndex >= 0 && rowIndex < getRowCount()) {
            RowKind kind = getRowKind(rowIndex);

            if (kind == RowKind.BACK) {
                this.currentFolder = "";
                this.scrollOffset = 0;
                return true;
            }

            if (kind == RowKind.CANCEL_MOVE) {
                this.movingStructureId = null;
                return true;
            }

            if (kind == RowKind.FOLDER) {
                String folderName = getRowFolderName(rowIndex);

                if (this.movingStructureId != null) {
                    NetworkHandler.sendToServer(new MoveClonerStructureToFolderPacket(
                            this.containerIdSupplier.getAsInt(), this.movingStructureId, folderName));
                    this.movingStructureId = null;
                    return true;
                }

                if (isFolderEmpty(folderName) && clickedDeleteButtonForRow(rowIndex, mouseX, mouseY, listTop, listRight)) {
                    NetworkHandler.sendToServer(new DeleteClonerFolderPacket(
                            this.containerIdSupplier.getAsInt(), folderName));
                    return true;
                }

                this.currentFolder = folderName;
                this.scrollOffset = 0;
                return true;
            }

            if (kind == RowKind.STRUCTURE) {
                String rowId = getRowId(rowIndex);

                if (!rowId.isBlank() && this.movingStructureId == null
                        && clickedDeleteButtonForRow(rowIndex, mouseX, mouseY, listTop, listRight)) {
                    deleteStructure(rowId);
                    return true;
                }

                this.highlightedId = rowId;
                syncRenameBoxToHighlighted();
                return true;
            }

            if (kind == RowKind.EMPTY) {
                this.highlightedId = "";
                syncRenameBoxToHighlighted();
                return true;
            }
        }

        return true;
    }

    private boolean clickedDeleteButtonForRow(int rowIndex, double mouseX, double mouseY, int listTop, int listRight) {
        int rowTop = listTop - (scrollOffset % ROW_HEIGHT) + (rowIndex - scrollOffset / ROW_HEIGHT) * ROW_HEIGHT;
        int deleteRight = listRight - 2;
        int deleteLeft = deleteRight - DELETE_BUTTON_SIZE;
        int deleteTop = rowTop + 3;
        int deleteBottom = deleteTop + DELETE_BUTTON_SIZE;

        return mouseX >= deleteLeft && mouseX < deleteRight && mouseY >= deleteTop && mouseY < deleteBottom;
    }

    private void selectHighlighted() {
        String id = this.highlightedId == null ? "" : this.highlightedId;

        NetworkHandler.sendToServer(new SelectClonerStructurePacket(
                this.containerIdSupplier.getAsInt(),
                id
        ));

        this.lastSelectedId = null;
        this.open = false;
        this.searchBox.setFocused(false);
        this.renameBox.setFocused(false);
    }

    private void renameHighlighted() {
        if (this.highlightedId == null || this.highlightedId.isBlank()) {
            return;
        }

        String name = this.renameBox.getValue();

        if (name == null || name.isBlank()) {
            return;
        }

        NetworkHandler.sendToServer(new RenameClonerStructurePacket(
                this.containerIdSupplier.getAsInt(),
                this.highlightedId,
                name
        ));
    }

    private void confirmCreateFolder() {
        String name = this.renameBox.getValue();

        if (name != null && !name.isBlank()) {
            NetworkHandler.sendToServer(new CreateClonerFolderPacket(
                    this.containerIdSupplier.getAsInt(), name));
        }

        cancelCreateFolder();
    }

    private void cancelCreateFolder() {
        this.creatingFolder = false;
        this.renameBox.setValue("");
        this.renameBox.setHint(Component.translatable(LangDefs.STRUCTURE_GADGET_CLONER_NAME.getTranslationKey()));
        this.renameBox.setFocused(false);
        syncRenameBoxToHighlighted();
    }

    private void deleteStructure(String id) {
        if (id == null || id.isBlank()) {
            return;
        }

        NetworkHandler.sendToServer(new DeleteClonerStructurePacket(
                this.containerIdSupplier.getAsInt(),
                id
        ));

        if (id.equals(this.highlightedId)) {
            this.highlightedId = "";
            this.renameBox.setValue("");
        }

        this.lastSelectedId = null;
    }

    private void exportHighlighted() {
        if (this.highlightedId == null || this.highlightedId.isBlank()) {
            return;
        }

        ClonerStructureFileTransferClient.beginExport(
                this.containerIdSupplier.getAsInt(),
                this.highlightedId,
                getHighlightedName()
        );
    }

    private void importStructure() {
        ClonerStructureFileTransferClient.beginImport(this.containerIdSupplier.getAsInt());
    }

    private void syncRenameBoxToHighlighted() {
        if (this.creatingFolder) {
            return;
        }

        if (this.highlightedId == null || this.highlightedId.isBlank()) {
            this.renameBox.setValue("");
            return;
        }

        for (ClonerStructureLibraryClientCache.Entry entry : ClonerStructureLibraryClientCache.entries()) {
            if (entry.id().equals(this.highlightedId)) {
                this.renameBox.setValue(entry.name());
                return;
            }
        }

        this.renameBox.setValue("");
    }

    private String getHighlightedName() {
        if (this.highlightedId == null || this.highlightedId.isBlank()) {
            return "";
        }

        for (ClonerStructureLibraryClientCache.Entry entry : ClonerStructureLibraryClientCache.entries()) {
            if (entry.id().equals(this.highlightedId)) {
                return entry.name();
            }
        }

        return "";
    }

    private boolean isHighlightedInFolder() {
        if (this.highlightedId == null || this.highlightedId.isBlank()) {
            return false;
        }

        for (ClonerStructureLibraryClientCache.Entry entry : ClonerStructureLibraryClientCache.entries()) {
            if (entry.id().equals(this.highlightedId)) {
                return entry.folder() != null && !entry.folder().isBlank();
            }
        }

        return false;
    }

    private boolean isFolderEmpty(String folderName) {
        if (folderName == null || folderName.isBlank()) {
            return true;
        }

        for (ClonerStructureLibraryClientCache.Entry entry : ClonerStructureLibraryClientCache.entries()) {
            if (folderName.equals(entry.folder())) {
                return false;
            }
        }

        return true;
    }

    private int getDropdownWidth() {
        return Math.max(this.width, 170);
    }

    private int getRowCount() {
        if (this.movingStructureId != null) {
            return 2 + ClonerStructureLibraryClientCache.folders().size();
        }

        if (!this.currentFolder.isEmpty()) {
            return 1 + getFilteredEntries().size();
        }

        return 1 + getFilteredFolders().size() + getFilteredEntries().size();
    }

    private RowKind getRowKind(int rowIndex) {
        if (this.movingStructureId != null) {
            if (rowIndex == 0) return RowKind.CANCEL_MOVE;
            if (rowIndex == 1) return RowKind.STRUCTURE;
            return RowKind.FOLDER;
        }

        if (!this.currentFolder.isEmpty()) {
            return rowIndex == 0 ? RowKind.BACK : RowKind.STRUCTURE;
        }

        if (rowIndex == 0) {
            return RowKind.EMPTY;
        }

        int folderCount = getFilteredFolders().size();

        if (rowIndex <= folderCount) {
            return RowKind.FOLDER;
        }

        return RowKind.STRUCTURE;
    }

    private String getRowFolderName(int rowIndex) {
        if (this.movingStructureId != null) {
            int folderIndex = rowIndex - 2;
            List<String> folders = ClonerStructureLibraryClientCache.folders();
            if (folderIndex >= 0 && folderIndex < folders.size()) {
                return folders.get(folderIndex);
            }
            return "";
        }

        int folderIndex = rowIndex - 1;
        List<String> folders = getFilteredFolders();
        if (folderIndex >= 0 && folderIndex < folders.size()) {
            return folders.get(folderIndex);
        }

        return "";
    }

    private String getRowId(int rowIndex) {
        RowKind kind = getRowKind(rowIndex);

        if (kind != RowKind.STRUCTURE) {
            return "";
        }

        if (this.movingStructureId != null) {
            return rowIndex == 1 ? this.movingStructureId : "";
        }

        List<ClonerStructureLibraryClientCache.Entry> entries = getFilteredEntries();
        int entryIndex = entryIndexForRow(rowIndex);

        if (entryIndex < 0 || entryIndex >= entries.size()) {
            return "";
        }

        return entries.get(entryIndex).id();
    }

    private int entryIndexForRow(int rowIndex) {
        if (!this.currentFolder.isEmpty()) {
            return rowIndex - 1;
        }

        return rowIndex - 1 - getFilteredFolders().size();
    }

    private String getRowDisplayName(int rowIndex) {
        RowKind kind = getRowKind(rowIndex);

        return switch (kind) {
            case EMPTY -> Component.translatable(LangDefs.STRUCTURE_GADGET_CLONER_EMPTY.getTranslationKey()).getString();
            case BACK -> "◄ " + this.currentFolder;
            case CANCEL_MOVE -> "◄ " + Component.translatable(LangDefs.STRUCTURE_GADGET_CLONER_CANCEL.getTranslationKey()).getString();
            case FOLDER -> "▶ " + getRowFolderName(rowIndex);
            case STRUCTURE -> {
                if (this.movingStructureId != null) {
                    for (ClonerStructureLibraryClientCache.Entry e : ClonerStructureLibraryClientCache.entries()) {
                        if (e.id().equals(this.movingStructureId)) {
                            yield e.name() + " (" + e.blockCount() + ")";
                        }
                    }
                    yield this.movingStructureId;
                }

                List<ClonerStructureLibraryClientCache.Entry> entries = getFilteredEntries();
                int entryIndex = entryIndexForRow(rowIndex);

                if (entryIndex < 0 || entryIndex >= entries.size()) {
                    yield "";
                }

                ClonerStructureLibraryClientCache.Entry entry = entries.get(entryIndex);
                yield entry.name() + " (" + entry.blockCount() + ")";
            }
        };
    }

    private String getSelectedDisplayName() {
        String selectedId = ClonerStructureLibraryClientCache.selectedId();

        if (selectedId == null || selectedId.isBlank()) {
            return Component.translatable(LangDefs.STRUCTURE_GADGET_CLONER_EMPTY.getTranslationKey()).getString();
        }

        for (ClonerStructureLibraryClientCache.Entry entry : ClonerStructureLibraryClientCache.entries()) {
            if (entry.id().equals(selectedId)) {
                return entry.name();
            }
        }

        return Component.translatable(LangDefs.STRUCTURE_GADGET_CLONER_NOT_FOUND.getTranslationKey()).getString();
    }

    private List<ClonerStructureLibraryClientCache.Entry> getFilteredEntries() {
        String folderFilter = this.currentFolder.isEmpty() ? "" : this.currentFolder;
        return ClonerStructureLibraryClientCache.filtered(this.searchBox.getValue(), folderFilter);
    }

    private List<String> getFilteredFolders() {
        String query = this.searchBox.getValue();

        if (query == null || query.isBlank()) {
            return ClonerStructureLibraryClientCache.folders();
        }

        String normalized = query.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();

        for (String folder : ClonerStructureLibraryClientCache.folders()) {
            if (folder.toLowerCase(Locale.ROOT).contains(normalized)) {
                result.add(folder);
            }
        }

        return result;
    }

    private int getMaxScroll() {
        int listHeight = DROPDOWN_HEIGHT - RENAME_HEIGHT - BUTTON_HEIGHT * 3 - PADDING * 2 - 13;
        return Math.max(0, getRowCount() * ROW_HEIGHT - listHeight);
    }

    private void renderScrollbar(GuiGraphics guiGraphics, int listTop, int listBottom, int dropdownRight) {
        int trackLeft = dropdownRight - SCROLLBAR_WIDTH - 1;
        int trackRight = dropdownRight - 1;
        int trackTop = listTop;
        int trackBottom = listBottom;

        guiGraphics.fill(trackLeft, trackTop, trackRight, trackBottom, 0xFF202020);

        int maxScroll = getMaxScroll();

        if (maxScroll <= 0) {
            guiGraphics.fill(trackLeft, trackTop, trackRight, trackBottom, 0xFF505050);
            return;
        }

        int trackHeight = trackBottom - trackTop;
        int contentHeight = getRowCount() * ROW_HEIGHT;
        int thumbHeight = Math.max(10, (int) ((trackHeight * (double) trackHeight) / contentHeight));
        int thumbTravel = trackHeight - thumbHeight;
        int thumbTop = trackTop + (int) ((scrollOffset / (double) maxScroll) * thumbTravel);

        guiGraphics.fill(trackLeft, thumbTop, trackRight, thumbTop + thumbHeight, 0xFF777777);
    }

    private void drawBorder(GuiGraphics guiGraphics, int left, int top, int right, int bottom, int color) {
        guiGraphics.fill(left, top, right, top + 1, color);
        guiGraphics.fill(left, bottom - 1, right, bottom, color);
        guiGraphics.fill(left, top, left + 1, bottom, color);
        guiGraphics.fill(right - 1, top, right, bottom, color);
    }

    private void updateEditBoxBounds() {
        this.searchBox.setX(getX() + PADDING);
        this.searchBox.setY(getY() + PADDING);
        this.searchBox.setWidth(Math.max(20, this.width - PADDING * 2 - ADD_FOLDER_BTN - 2));

        int dropdownBottom = getY() + height + 1 + DROPDOWN_HEIGHT;
        int bottomButtonsTop = dropdownBottom - PADDING - BUTTON_HEIGHT;
        int middleButtonsTop = bottomButtonsTop - BUTTON_HEIGHT - 2;
        int topButtonsTop = middleButtonsTop - BUTTON_HEIGHT - 2;
        int renameTop = topButtonsTop - RENAME_HEIGHT - 4;

        this.renameBox.setX(getX() + PADDING);
        this.renameBox.setY(renameTop);
        this.renameBox.setWidth(Math.max(20, getDropdownWidth() - PADDING * 2));
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
    }
}
