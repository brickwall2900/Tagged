package io.github.brickwall2900.tagged;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.plaf.synth.SynthListUI;
import java.awt.*;
import java.nio.file.Path;

/// half of the code belongs to {@link DefaultListCellRenderer} lol
public class TaggedFileListCellRenderer extends JLabel implements ListCellRenderer<TaggedHelper.FileTag> {
    private static final Border EMPTY_BORDER = BorderFactory.createEmptyBorder(1, 1, 1, 1);

    private final IconManager iconManager;

    public TaggedFileListCellRenderer(IconManager iconManager) {
        this.iconManager = iconManager;

        setOpaque(true);
        setName("FileListCellRenderer");
        setBorder(EMPTY_BORDER);
        setVerticalTextPosition(BOTTOM);
        setHorizontalTextPosition(CENTER);
        setHorizontalAlignment(CENTER);
        setVerticalAlignment(CENTER);
    }

    @Override
    public Component getListCellRendererComponent(JList<? extends TaggedHelper.FileTag> list,
                                                  TaggedHelper.FileTag value,
                                                  int index,
                                                  boolean isSelected,
                                                  boolean cellHasFocus) {
        setComponentOrientation(list.getComponentOrientation());

        Color bg = null;
        Color fg = null;

        JList.DropLocation dropLocation = list.getDropLocation();
        if (dropLocation != null
                && !dropLocation.isInsert()
                && dropLocation.getIndex() == index) {

            bg = UIManager.getColor("List.dropCellBackground");
            fg = UIManager.getColor("List.dropCellForeground");

            isSelected = true;
        }

        if (isSelected) {
            setBackground(bg == null ? list.getSelectionBackground() : bg);
            setForeground(fg == null ? list.getSelectionForeground() : fg);
        } else {
            setBackground(list.getBackground());
            setForeground(list.getForeground());
        }

        int width = list.getFixedCellHeight();
        int height = list.getFixedCellWidth();
        int listWidth = list.getVisibleRect().width;
        int listHeight = list.getVisibleRect().height;
        iconManager.setThumbnailSize(Math.max(width, height));
        iconManager.setMaxEntries((int) (Math.ceil((double) listWidth / width) * Math.ceil((double) listHeight / height)));
        setIcon(iconManager.getIcon(value.filePath(), list));
        setText(value.filePath().getFileName().toString());

        if (list.getName() == null || !list.getName().equals("ComboBox.list")
                || !(list.getUI() instanceof SynthListUI)) {
            setEnabled(list.isEnabled());
        }

        setFont(list.getFont());

        Border border = null;
        if (cellHasFocus) {
            if (isSelected) {
                border = UIManager.getBorder("List.focusSelectedCellHighlightBorder");
            }
            if (border == null) {
                border = UIManager.getBorder("List.focusCellHighlightBorder");
            }
        } else {
            border = EMPTY_BORDER;
        }
        setBorder(border);

        return this;
    }
}
