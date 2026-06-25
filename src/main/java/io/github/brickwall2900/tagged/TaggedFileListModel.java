package io.github.brickwall2900.tagged;

import javax.swing.*;
import java.util.*;
import java.util.stream.Stream;

public class TaggedFileListModel extends AbstractListModel<TaggedHelper.FileTag> {
    private final List<TaggedHelper.FileTag> files = new ArrayList<>();
    private List<TaggedHelper.FileTag> filterList;
    private SearchOption searchOption = SearchOption.LENIENT;
    private String filter;

    public void addAll(TaggedHelper.FileTag[] files) {
        Set<TaggedHelper.FileTag> set = new HashSet<>(Set.of(files));
        this.files.removeIf(set::contains);
        int startIndex = Math.max(getSize() - 1, 0);
        this.files.addAll(List.of(files));
        int endIndex = Math.max(getSize() - 1, 0);

        if (filter == null) {
            fireIntervalAdded(this, startIndex, endIndex);
        } else {
            updateFilter();
        }
    }

    public void clear() {
        int lastIndex = files.size() - 1;
        files.clear();
        fireIntervalRemoved(this, 0, lastIndex);
    }

    public SearchOption getSearchOption() {
        return searchOption;
    }

    public void setSearchOption(SearchOption searchOption) {
       this.searchOption = searchOption != null ? searchOption : SearchOption.LENIENT;
       if (filter != null) {
           updateFilter();
       }
    }

    public String getFilter() {
        return filter;
    }

    public void setFilter(String filter) {
        String oldFilter = this.filter;
        this.filter = filter;
        if (!Objects.equals(oldFilter, filter)) {
            updateFilter();
        }
    }

    private void updateFilter() {
        // remove all
        int fileCount = files.size();
        fireIntervalRemoved(this, 0, fileCount);

        if (filter == null) {
            fireIntervalAdded(this, 0, fileCount);
            return;
        }

        if (!filter.isEmpty() && filter.charAt(0) == '@') {
            return;
        }

        String[] searchKeywords = filter.split("\\s");
        if (searchKeywords.length == 0) {
            return;
        }

        int kwCount = searchKeywords.length;
        for (int i = 0; i < kwCount; i++) {
            searchKeywords[i] = searchKeywords[i].toLowerCase(Locale.ROOT);
        }

        filterList = searchFileTagsAndFilter(new HashSet<>(List.of(searchKeywords)));

    }

    public List<TaggedHelper.FileTag> searchFileTagsAndFilter(Set<String> searchKeywords) {
        // once i've gotten my compsci degree
        // once my thinking gets better
        // i'll look back at this and improve the fuck out of this
        // that is NOT a promise that is a must do brochinatto
        // or else i'll fucking kill myself

        // no AI please
//        return files.parallelStream()
//                .filter(x -> x.tags().length > 0)
//                .filter(x -> {
//                    String[] tags = x.tags();
//                    boolean canInclude = false;
//                    for (String tag : tags) {
//                        for (String keywords : searchKeywords) {
//                            canInclude |= tag.toLowerCase().contains(
//                                    keywords.toLowerCase());
//                        }
//
//                        if (canInclude) {
//                            break;
//                        }
//                    }
//                    return canInclude;
//                })
//                .toList();
        record Meta(TaggedHelper.FileTag fileTag, String joined) {}
        Stream<Meta> stream = files.parallelStream()
                .filter(x -> x.tags().length > 0)
                .map(x -> new Meta(x, String.join("", x.tags()).toLowerCase(Locale.ROOT)));

        stream = switch (searchOption) {
            case STRICT -> {
                yield stream.filter(m -> {
                    boolean canInclude = true;
                    for (String keyword : searchKeywords) {
                        canInclude &= m.joined.contains(keyword);
                    }
                    return canInclude;
                });
            }
            case LENIENT -> {
                yield stream.filter(m -> {
                    boolean canInclude = false;
                    for (String keyword : searchKeywords) {
                        canInclude |= m.joined.contains(keyword);
                    }
                    return canInclude;
                });
            }
            case SCORED -> {
                yield stream;
            }
        };

        return stream.sorted(Comparator.comparingInt(m -> {
            int score = 0;
            for (String keyword : searchKeywords) {
                if (((Meta)m).joined.contains(keyword)) {
                    score++;
                }
            }
            return score;
        }).reversed())
                .map(Meta::fileTag)
                .toList();

    }

    @Override
    public int getSize() {
        return (filter != null && filterList != null)
                ? filterList.size()
                : files.size();
    }

    @Override
    public TaggedHelper.FileTag getElementAt(int index) {
        return (filter != null && filterList != null)
                ? filterList.get(index)
                : files.get(index);
    }

    public void modify(TaggedHelper.FileTag oldFileTag, TaggedHelper.FileTag fileTag) {
        if (files.contains(oldFileTag)) {
            int index = files.indexOf(oldFileTag);
            files.set(index, fileTag);

            if (filter != null) {
                updateFilter();
            }
        }
    }

    public enum SearchOption {
        SCORED, LENIENT, STRICT;
    }
}
