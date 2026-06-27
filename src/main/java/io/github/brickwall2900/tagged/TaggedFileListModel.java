package io.github.brickwall2900.tagged;

import javax.swing.*;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TaggedFileListModel extends AbstractListModel<TaggedHelper.FileTag> {
    private final List<TaggedHelper.FileTag> files = new ArrayList<>();
    private final List<TaggedHelper.FileTag> filterList = new ArrayList<>();
    private SearchOption searchOption = SearchOption.LENIENT;
    private SortOption sortOption = SortOption.NAME;
    private boolean sortReversed = false;
    private String filter;

    public void addAll(TaggedHelper.FileTag[] files) {
        int oldSize = this.files.size();
        int fileCount = files.length;
        Set<String> merged = new HashSet<>(2);
        for (int i = 0; i < fileCount; i++) {
            TaggedHelper.FileTag currentFile = files[i];
            int indexOfExistingTag = this.files.indexOf(currentFile);
            if (indexOfExistingTag != -1) {
                // what the fuck
                TaggedHelper.FileTag existingTag = this.files.get(indexOfExistingTag);
                String[] tags1 = currentFile.tags();
                String[] tags2 = existingTag.tags();
                if (tags2.length > 0 && tags1.length > 0) {
                    /*String[] merged = new String[tags1.length + tags2.length];
                    System.arraycopy(tags1, 0, merged, 0, tags1.length);
                    System.arraycopy(tags2, 0, merged, tags1.length, tags2.length);*/
                    merged.clear();
                    merged.addAll(List.of(tags1));
                    merged.addAll(List.of(tags2));
                    this.files.set(indexOfExistingTag, new TaggedHelper.FileTag(currentFile.locationPath(),
                            currentFile.fileName(),
                            merged.toArray(String[]::new)));
                } else if (tags2.length == 0 && tags1.length > 0) {
                    this.files.set(indexOfExistingTag, currentFile);
                }
            } else {
                this.files.add(currentFile);
            }
        }
        this.files.sort(sortReversed ? sortOption.getSorter().reversed() : sortOption.getSorter());

        if (filter == null) {
            fireIntervalRemoved(this, 0, oldSize);
            fireIntervalAdded(this, 0, this.files.size());
        } else {
            updateFilter();
        }
    }

    public void clear() {
        int lastIndex = Math.max(files.size() - 1, 0);
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

    public SortOption getSortOption() {
        return sortOption;
    }

    public void setSortOption(SortOption sortOption) {
        this.sortOption = Objects.requireNonNull(sortOption);
        files.sort(sortReversed ? sortOption.getSorter().reversed() : sortOption.getSorter());
    }

    public boolean isSortReversed() {
        return sortReversed;
    }

    public void setSortReversed(boolean sortReversed) {
        this.sortReversed = sortReversed;
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
        fireIntervalRemoved(this, 0, filterList.size());

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

        filterList.clear();

        int kwCount = searchKeywords.length;
        for (int i = 0; i < kwCount; i++) {
            searchKeywords[i] = searchKeywords[i].toLowerCase(Locale.ROOT);
        }

        searchFileTagsAndFilter(new HashSet<>(List.of(searchKeywords)));
    }

    public void searchFileTagsAndFilter(Set<String> searchKeywords) {
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

        stream
                .sorted(Comparator.comparingInt(m -> {
                    int score = 0;
                    for (String keyword : searchKeywords) {
                        if (((Meta) m).joined.contains(keyword)) {
                            score++;
                        }
                    }
                    return score;
                }).reversed())
                .map(Meta::fileTag)
                .collect(Collectors.toCollection(() -> filterList));
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

    public void setTags(TaggedHelper.FileTag selected, String[] tags) {
        if (files.contains(selected)) {
            int index = files.indexOf(selected);
            files.set(index, new TaggedHelper.FileTag(selected.locationPath(), selected.fileName(), tags));

            if (filter != null) {
                updateFilter();
            }
        }
    }

    public enum SearchOption {
        SCORED, LENIENT, STRICT;
    }

    public enum SortOption {
        NAME {
            @Override
            public Comparator<TaggedHelper.FileTag> getSorter() {
                return Comparator.comparing(TaggedHelper.FileTag::fileName);
            }
        },

        LAST_MODIFIED {
            @Override
            public Comparator<TaggedHelper.FileTag> getSorter() {
                return Comparator.comparingLong(f -> {
                    if (f.locationPath() != null) {
                        try {
                            return Files.getLastModifiedTime(f.locationPath()).toMillis();
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    }
                    return 0;
                });
            }
        },

        SIZE {
            @Override
            public Comparator<TaggedHelper.FileTag> getSorter() {
                return Comparator.comparingLong(f -> {
                    if (f.locationPath() != null) {
                        try {
                            return Files.size(f.locationPath());
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    }
                    return 0;
                });
            }
        },

        TAGS {
            @Override
            public Comparator<TaggedHelper.FileTag> getSorter() {
                return (f1, f2) -> {
                    return Arrays.compare(f1.tags(), f2.tags());
                };
            }
        };

        public Comparator<TaggedHelper.FileTag> getSorter() { throw new UnsupportedOperationException(); }
    }
}
