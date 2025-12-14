package me.lauriichan.maven.sourcemod.api;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.BodyDeclaration;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.FieldDeclaration;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.RecordDeclaration;
import org.jboss.forge.roaster._shade.org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.jboss.forge.roaster.model.impl.FieldImpl;
import org.jboss.forge.roaster.model.impl.JavaRecordImpl;
import org.jboss.forge.roaster.model.source.AnnotationSource;
import org.jboss.forge.roaster.model.source.AnnotationTargetSource;
import org.jboss.forge.roaster.model.source.FieldSource;
import org.jboss.forge.roaster.model.source.JavaClassSource;
import org.jboss.forge.roaster.model.source.JavaRecordSource;
import org.jboss.forge.roaster.model.source.JavaSource;
import org.jboss.forge.roaster.model.source.MethodHolderSource;
import org.jboss.forge.roaster.model.source.MethodSource;

@SuppressWarnings({
    "unchecked",
    "rawtypes"
})
public final class SourceTransformerUtils {
    
    private static final Method RECORD_GET_DECLARATION;
    
    static {
        Method getDeclaration = null;
        try {
            getDeclaration = JavaRecordImpl.class.getDeclaredMethod("getDeclaration");
            getDeclaration.setAccessible(true);
        } catch(Throwable thrw) {
        }
        RECORD_GET_DECLARATION = getDeclaration;
    }
    
    private static RecordDeclaration getDeclaration(JavaRecordSource source) {
        if (RECORD_GET_DECLARATION == null) {
            throw new IllegalStateException("Couldn't retrieve access to JavaRecordImpl#getDeclaration");
        }
        try {
            return (RecordDeclaration) RECORD_GET_DECLARATION.invoke(source);
        } catch (Throwable thrw) {
            throw new IllegalStateException("Couldn't access to JavaRecordImpl#getDeclaration");
        }
    }

    private SourceTransformerUtils() {
        throw new UnsupportedOperationException();
    }

    public static void removeMethod(final MethodHolderSource<?> source, final String name) {
        final MethodSource method = source.getMethod(name);
        if (method == null) {
            return;
        }
        source.removeMethod(method);
    }

    public static void removeMethod(final MethodHolderSource<?> source, final String name, final Class<?>... types) {
        final MethodSource method = source.getMethod(name, types);
        if (method == null) {
            return;
        }
        source.removeMethod(method);
    }

    public static void removeMethod(final MethodHolderSource<?> source, final String name, final String... types) {
        final MethodSource method = source.getMethod(name, types);
        if (method == null) {
            return;
        }
        source.removeMethod(method);
    }

    public static void importClass(final JavaSource<?> source, final Class<?> clazz) {
        if (source.hasImport(clazz)) {
            return;
        }
        source.addImport(clazz);
    }

    public static void importClass(final JavaSource<?> source, final String className) {
        if (source.hasImport(className)) {
            return;
        }
        source.addImport(className);
    }

    public static <E extends JavaSource<E>> void removeAnnotation(final AnnotationTargetSource<E, ?> source,
        final Class<? extends Annotation> annotation) {
        final AnnotationSource<E> annotationSource = source.getAnnotation(annotation);
        if (annotationSource != null) {
            source.removeAnnotation(annotationSource);
        }
    }

    public static boolean hasSuperType(final JavaClassSource source, final Class<?> clazz) {
        return source.getSuperType() != null && source.getSuperType().equals(clazz.getName());
    }

    public static FieldSource<JavaRecordSource> addField(final JavaRecordSource source) {
        FieldSource<JavaRecordSource> field = new FieldImpl<>(source);
        addField(source, field);
        return field;
    }

    private static void addField(JavaRecordSource source, FieldSource<JavaRecordSource> field) {
        List<Object> bodyDeclarations = getDeclaration(source).bodyDeclarations();
        int idx = 0;
        for (Object object : bodyDeclarations) {
            if (!(object instanceof FieldDeclaration)) {
                break;
            }
            idx++;
        }
        bodyDeclarations.add(idx, ((VariableDeclarationFragment) field.getInternal()).getParent());
    }

    public static List<FieldSource<JavaRecordSource>> getFields(final JavaRecordSource source) {
        List<FieldSource<JavaRecordSource>> result = new ArrayList<>();
        List<BodyDeclaration> bodyDeclarations = getDeclaration(source).bodyDeclarations();
        for (BodyDeclaration bodyDeclaration : bodyDeclarations) {
            if (bodyDeclaration instanceof FieldDeclaration field) {
                List<VariableDeclarationFragment> fragments = field.fragments();
                for (VariableDeclarationFragment fragment : fragments) {
                    result.add(new FieldImpl<>(source, fragment));
                }
            }
        }
        return Collections.unmodifiableList(result);
    }

}
