package io.github.brickwall2900.tagged;

import javax.swing.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class TaggedFileListModel extends AbstractListModel<TaggedHelper.FileTag> {
    private final List<TaggedHelper.FileTag> files = new ArrayList<>();

    public void addAll(TaggedHelper.FileTag[] files) {
        Set<TaggedHelper.FileTag> set = new HashSet<>(Set.of(files));
        this.files.removeIf(set::contains);
        int startIndex = Math.max(getSize() - 1, 0);
        this.files.addAll(List.of(files));
        int endIndex = Math.max(getSize() - 1, 0);
        fireIntervalAdded(this, startIndex, endIndex);
    }

    public void clear() {
        int lastIndex = files.size() - 1;
        files.clear();
        fireIntervalRemoved(this, 0, lastIndex);
    }

    @Override
    public int getSize() {
        return files.size();
    }

    @Override
    public TaggedHelper.FileTag getElementAt(int index) {
        return files.get(index);
    }
}
