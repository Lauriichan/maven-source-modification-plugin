package me.lauriichan.maven.sourcemod.api.source;

import java.util.Optional;
import java.util.stream.Stream;

import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster.model.source.JavaAnnotationSource;
import org.jboss.forge.roaster.model.source.JavaClassSource;
import org.jboss.forge.roaster.model.source.JavaEnumSource;
import org.jboss.forge.roaster.model.source.JavaInterfaceSource;
import org.jboss.forge.roaster.model.source.JavaRecordSource;
import org.jboss.forge.roaster.model.source.JavaSource;

import it.unimi.dsi.fastutil.objects.ObjectCollection;

public abstract class SourcePackage implements Iterable<JavaSource<?>> {

    public abstract boolean isRoot();
    
    public abstract SourcePackage root();
    
    public abstract boolean hasParent();
    
    public abstract SourcePackage parent();
    
    public abstract String name();

    public abstract String classpath();
    
    public abstract Stream<JavaSource<?>> stream();
    
    public Stream<JavaClassSource> classStream() {
        return stream().filter(src -> src.isClass()).map(src -> (JavaClassSource) src);
    }
    
    public Stream<JavaRecordSource> recordStream() {
        return stream().filter(src -> src.isRecord()).map(src -> (JavaRecordSource) src);
    }
    
    public Stream<JavaInterfaceSource> interfaceStream() {
        return stream().filter(src -> src.isInterface()).map(src -> (JavaInterfaceSource) src);
    }
    
    public Stream<JavaAnnotationSource> annotationStream() {
        return stream().filter(src -> src.isAnnotation()).map(src -> (JavaAnnotationSource) src);
    }
    
    public Stream<JavaEnumSource> enumStream() {
        return stream().filter(src -> src.isEnum()).map(src -> (JavaEnumSource) src);
    }

    public abstract boolean hasSource(String path);

    public abstract Optional<JavaSource<?>> findSource(String path);

    public Optional<JavaClassSource> findClass(String path) {
        return findSource(path).filter(source -> source.isClass()).map(src -> (JavaClassSource) src);
    }

    public Optional<JavaRecordSource> findRecord(String path) {
        return findSource(path).filter(source -> source.isRecord()).map(src -> (JavaRecordSource) src);
    }

    public Optional<JavaInterfaceSource> findInterface(String path) {
        return findSource(path).filter(source -> source.isInterface()).map(src -> (JavaInterfaceSource) src);
    }

    public Optional<JavaAnnotationSource> findAnnotation(String path) {
        return findSource(path).filter(source -> source.isAnnotation()).map(src -> (JavaAnnotationSource) src);
    }

    public Optional<JavaEnumSource> findEnum(String path) {
        return findSource(path).filter(source -> source.isEnum()).map(src -> (JavaEnumSource) src);
    }
    
    public abstract boolean hasDirectSource(String name);
    
    public abstract JavaSource<?> getDirectSource(String name);

    public abstract ObjectCollection<? extends SourcePackage> getPackages();

    public abstract SourcePackage findPackage(String path);
    
    public abstract SourcePackage getDirectPackage(String name);

    public abstract SourcePackage getOrCreatePackage(String path);

    protected abstract void createSource(JavaSource<?> source, String name) throws IllegalStateException;

    protected final <E extends JavaSource<E>> E createSource(Class<E> type, String name) throws IllegalStateException {
        if (isRoot()) {
            throw new IllegalStateException("Can't create class in root package");
        }
        E source = Roaster.create(type);
        source.setPackage(classpath());
        source.setName(name);
        createSource(source, name);
        return source;
    }

    public final JavaClassSource createClass(String name) throws IllegalStateException {
        return createSource(JavaClassSource.class, name);
    }

    public final JavaRecordSource createRecord(String name) throws IllegalStateException {
        return createSource(JavaRecordSource.class, name);
    }

    public final JavaInterfaceSource createInterface(String name) throws IllegalStateException {
        return createSource(JavaInterfaceSource.class, name);
    }

    public final JavaAnnotationSource createAnnotation(String name) throws IllegalStateException {
        return createSource(JavaAnnotationSource.class, name);
    }

}
