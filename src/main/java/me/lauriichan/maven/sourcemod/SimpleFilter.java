package me.lauriichan.maven.sourcemod;

import java.io.File;
import java.util.Set;

import org.codehaus.plexus.util.SelectorUtils;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

public final class SimpleFilter {

    private final String[] includes;
    private final String[] excludes;
    private final boolean excludeByDefault;

    public SimpleFilter(Set<String> includes, Set<String> excludes, boolean excludeByDefault) {
        this.includes = normalize(includes);
        this.excludes = normalize(excludes);
        this.excludeByDefault = excludeByDefault;
    }
    
    public boolean isFiltered(String path) {
        path = normalize(path);
        return (excludeByDefault && !matches(path, includes)) || matches(path, excludes);
    }
    
    private boolean matches(String path, String[] filters) {
        if (filters.length == 0) {
            return false;
        }
        for (String filter : filters) {
            if (SelectorUtils.match(filter, path)) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String path) {
        return (path != null) ? path.replace(File.separatorChar == '/' ? '\\' : '/', File.separatorChar) : null;
    }

    private String[] normalize(Set<String> filters) {
        if (filters == null || filters.isEmpty()) {
            return new String[0];
        }
        ObjectArrayList<String> list = new ObjectArrayList<>();
        for (String filter : filters) {
            filter = normalize(filter);
            if (filter.endsWith(File.separator)) {
                filter += "**";
            }
            list.add(filter);
        }
        return list.toArray(String[]::new);
    }

}
