package me.lauriichan.maven.sourcemod;

import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

import org.jboss.forge.roaster.model.source.JavaSource;

import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectCollection;
import it.unimi.dsi.fastutil.objects.ObjectLists;
import me.lauriichan.maven.sourcemod.api.source.SourcePackage;

final class SourcePackageImpl extends SourcePackage {

    private final SourcePackageImpl root, parent;

    private final String name, path;

    private final Object2ObjectArrayMap<String, JavaSource<?>> classMap = new Object2ObjectArrayMap<>();
    private final Object2ObjectArrayMap<String, SourcePackageImpl> packageMap = new Object2ObjectArrayMap<>();
    
    private final ObjectArrayList<JavaSource<?>> generatedSources;

    SourcePackageImpl(List<JavaSource<?>> sources) {
        this.root = this;
        this.parent = null;
        this.name = "";
        this.path = "";
        this.generatedSources = new ObjectArrayList<>();
        for (JavaSource<?> source : sources) {
            add(source);
        }
    }

    private SourcePackageImpl(SourcePackageImpl root, SourcePackageImpl parent, String name, String path) {
        this.root = root;
        this.parent = parent;
        this.name = name;
        this.generatedSources = null;
        this.path = path.isEmpty() ? name : path + '.' + name;
    }
    
    final List<JavaSource<?>> generatedSources() {
        return ObjectLists.unmodifiable(generatedSources);
    }

    @Override
    public boolean isRoot() {
        return parent == null;
    }

    @Override
    public SourcePackage root() {
        return root;
    }

    @Override
    public boolean hasParent() {
        return parent != null;
    }

    @Override
    public SourcePackage parent() {
        return parent;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String classpath() {
        return path;
    }

    @Override
    public Stream<JavaSource<?>> stream() {
        return classMap.values().stream();
    }

    @Override
    public boolean hasSource(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        if (!path.contains(".")) {
            return classMap.containsKey(path);
        }
        return hasSource(path.split("\\."), 0);
    }

    @Override
    public Optional<JavaSource<?>> findSource(String path) {
        return Optional.ofNullable(getSource(path));
    }

    @Override
    public boolean hasDirectSource(String name) {
        return classMap.containsKey(name);
    }

    @Override
    public JavaSource<?> getDirectSource(String name) {
        return classMap.get(name);
    }

    @Override
    public ObjectCollection<? extends SourcePackage> getPackages() {
        return packageMap.values();
    }

    @Override
    public SourcePackage findPackage(String path) {
        return getPackageImplByPath(path);
    }

    @Override
    public SourcePackage getDirectPackage(String name) {
        return packageMap.get(name);
    }

    @Override
    public SourcePackage getOrCreatePackage(String path) {
        return createPackageImplByPath(path);
    }

    @Override
    protected void createSource(JavaSource<?> source, String name) throws IllegalStateException {
        Objects.requireNonNull(name, "Name can't be null");
        if (name.isBlank()) {
            throw new IllegalStateException("Invalid name '" + name + "'");
        }
        if (name.contains(".")) {
            throw new IllegalStateException(
                "Source creation doesn't do package creation at the same time, please use this without packages in the name '" + name
                    + "'.");
        }
        if (classMap.containsKey(name)) {
            throw new IllegalStateException("There is already a source file with the name '" + name + "'!");
        }
        classMap.put(name, source);
        root.generatedSources.add(source);
    }

    @Override
    public Iterator<JavaSource<?>> iterator() {
        return classMap.values().iterator();
    }

    /*
     * Utils
     */

    private void add(JavaSource<?> source) {
        if (root != null) {
            root.add(source);
            return;
        }
        String pkg = source.getPackage();
        if (pkg == null || pkg.isBlank()) {
            classMap.put(source.getName(), source);
            return;
        } else if (!pkg.contains(".")) {
            createPackageImpl(pkg).classMap.put(source.getName(), source);
            return;
        }
        add(source.getPackage().split("\\."), 0, source);
    }

    private void add(String[] path, int depth, JavaSource<?> source) {
        if (path.length == depth) {
            classMap.put(source.getName(), source);
            return;
        }
        createPackageImpl(path[depth]).add(path, depth + 1, source);
    }

    private boolean hasSource(String[] path, int depth) {
        if (path.length == depth + 1) {
            return classMap.containsKey(path[depth]);
        }
        SourcePackageImpl pkg = packageMap.get(path[depth]);
        if (pkg == null) {
            return false;
        }
        return pkg.hasSource(path, depth + 1);
    }

    private JavaSource<?> getSource(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        if (!path.contains(".")) {
            return classMap.get(path);
        }
        return getSource(path.split("\\."), 0);
    }

    private JavaSource<?> getSource(String[] path, int depth) {
        if (path.length == depth + 1) {
            return classMap.get(path[depth]);
        }
        SourcePackageImpl pkg = packageMap.get(path[depth]);
        if (pkg == null) {
            return null;
        }
        return pkg.getSource(path, depth + 1);
    }

    private SourcePackageImpl getPackageImplByPath(String path) {
        if (path == null || path.isBlank()) {
            return this;
        }
        if (!path.contains(".")) {
            return packageMap.get(path);
        }
        return getPackageImplByPath(path.split("\\."), 0);
    }

    private SourcePackageImpl getPackageImplByPath(String[] path, int depth) {
        if (path.length == depth + 1) {
            return packageMap.get(path[depth]);
        }
        SourcePackageImpl pkg = packageMap.get(path[depth]);
        if (pkg == null) {
            return null;
        }
        return pkg.getPackageImplByPath(path, depth + 1);
    }

    private SourcePackageImpl createPackageImplByPath(String path) {
        if (path == null || path.isBlank()) {
            return this;
        }
        if (!path.contains(".")) {
            return createPackageImpl(path);
        }
        return createPackageImplByPath(path.split("\\."), 0);
    }

    private SourcePackageImpl createPackageImplByPath(String[] path, int depth) {
        if (path.length == depth + 1) {
            return createPackageImpl(path[depth]);
        }
        return createPackageImpl(path[depth]).createPackageImplByPath(path, depth + 1);
    }

    private SourcePackageImpl createPackageImpl(String name) {
        SourcePackageImpl pkg = packageMap.get(name);
        if (pkg != null) {
            return pkg;
        }
        if (name == null || name.isBlank()) {
            throw new IllegalStateException("Package name can not be blank.");
        }
        pkg = new SourcePackageImpl(root, this, name, this.path);
        packageMap.put(name, pkg);
        return pkg;
    }

}
