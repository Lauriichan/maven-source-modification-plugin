package me.lauriichan.maven.sourcemod;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster.model.source.JavaSource;

import it.unimi.dsi.fastutil.objects.ObjectArrayFIFOQueue;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import me.lauriichan.maven.sourcemod.api.ISourceTransformer;

@Mojo(name = "modifySource", defaultPhase = LifecyclePhase.GENERATE_SOURCES, threadSafe = true, requiresDependencyResolution = ResolutionScope.COMPILE)
public class SourceModificationMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    private MavenProject project;

    @Parameter
    private SourceTransformerConfiguration[] transformers;

    @Parameter
    private ReplacementConfiguration[] replacements;

    @Parameter(defaultValue = "${project.build.outputDirectory}")
    private List<String> classPath;

    @Parameter(defaultValue = "${project.compileSourceRoots}", required = true)
    private List<String> sourceDirectories;

    @Parameter(defaultValue = "${project.build.directory}/project-sources", required = true)
    private File outputDirectory;

    @Parameter(defaultValue = "**/*.java")
    private Set<String> includes = new HashSet<>();

    @Parameter
    private Set<String> excludes = new HashSet<>();

    @Parameter
    private boolean excludeByDefault = true;

    @Parameter(property = "sourcemodification.skip")
    private boolean skip = false;

    @Parameter
    private boolean copyUnmodifiedFiles = true;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        List<SourceFile> sources = findSources();
        if (sources.isEmpty()) {
            getLog().info("No sources to modify");
            return;
        }
        final ClassLoader originalLoader = Thread.currentThread().getContextClassLoader();
        try {
            updateClassLoader();
            List<ISourceTransformer> transformers = findTransformers();
            File output;
            if (transformers.isEmpty()) {
                getLog().info("No source transformers found");
                if (!copyUnmodifiedFiles) {
                    return;
                }
                for (SourceFile file : sources) {
                    output = new File(outputDirectory, file.relativePath());
                    createFile(output);
                    Files.copy(file.file().toPath(), output.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
                return;
            }
            ReplacementConfiguration[] replacements = this.replacements == null ? new ReplacementConfiguration[0] : this.replacements;
            String outputName, originalOutputName;
            for (SourceFile source : sources) {
                JavaSource<?> javaSource = Roaster.parse(JavaSource.class, source.file());
                String sourcePackage = javaSource.getPackage();
                boolean modified = false;
                for (ISourceTransformer transformer : transformers) {
                    if (!transformer.canTransform(javaSource)) {
                        continue;
                    }
                    if (!modified) {
                        getLog().info("Transforming class '" + javaSource.getQualifiedName() + "'");
                        modified = true;
                    }
                    transformer.transform(javaSource);
                }
                outputName = resolveOutputName(source.relativePath());
                originalOutputName = outputName;
                for (ReplacementConfiguration replacement : replacements) {
                    if (replacement.getPattern().isBlank()) {
                        continue;
                    }
                    outputName = replacement.isRegex()
                        ? replacePattern(outputName, replacement.getCompiledPattern(), replacement.getReplace())
                        : outputName.replace(replacement.getPattern(), replacement.getReplace());
                }
                boolean renamed = !outputName.equals(originalOutputName);
                if (modified && !javaSource.getPackage().equals(sourcePackage)) {
                    javaSource.setPackage(sourcePackage);
                }
                if (renamed) {
                    getLog().info("Renaming from '" + originalOutputName + "' to '" + outputName + "'");
                    javaSource.setName(sanatizeName(outputName));
                }
                if (modified || renamed) {
                    output = new File(outputDirectory, resolvePathWithoutName(source.relativePath()) + outputName);
                    createFile(output);
                    try (FileWriter writer = new FileWriter(output)) {
                        writer.write(fixJBossRename(javaSource.toString(), renamed, originalOutputName, outputName));
                    }
                } else if (copyUnmodifiedFiles) {
                    output = new File(outputDirectory, source.relativePath());
                    createFile(output);
                    Files.copy(source.file().toPath(), output.toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (Exception e) {
            getLog().error(e);
            throw new MojoExecutionException(e.getMessage(), e);
        } finally {
            Thread.currentThread().setContextClassLoader(originalLoader);
        }
    }
    
    private String fixJBossRename(String javaSource, boolean renamed, String originalName, String newName) {
        if (!renamed) {
            return javaSource;
        }
        originalName = sanatizeName(originalName);
        newName = sanatizeName(newName);
        return javaSource.replaceAll(String.format("\\b%s\\b", Pattern.quote(originalName)), newName);
    }

    private void createFile(File file) throws IOException {
        if (!file.exists()) {
            File parent;
            if ((parent = file.getParentFile()) != null && !parent.exists()) {
                parent.mkdirs();
            }
            file.createNewFile();
        }
    }

    private void updateClassLoader() throws DependencyResolutionRequiredException {
        ObjectArrayList<URL> urlList = new ObjectArrayList<>();
        for (final String runtimeResource : project.getRuntimeClasspathElements()) {
            urlList.add(resolveUrl(runtimeResource));
        }
        if (classPath != null && !classPath.isEmpty()) {
            for (String path : classPath) {
                urlList.add(resolveUrl(path));
            }
        }
        if (urlList.isEmpty()) {
            return;
        }
        Thread thread = Thread.currentThread();
        thread.setContextClassLoader(URLClassLoader.newInstance(urlList.toArray(URL[]::new), thread.getContextClassLoader()));
    }

    private List<ISourceTransformer> findTransformers() throws MojoExecutionException {
        if (this.transformers == null || this.transformers.length == 0) {
            return Collections.emptyList();
        }
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        ObjectArrayList<ISourceTransformer> transformers = new ObjectArrayList<>();
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        for (SourceTransformerConfiguration configuration : this.transformers) {
            Class<?> clazz;
            try {
                clazz = Class.forName(configuration.getClassName(), true, classLoader);
            } catch (ClassNotFoundException e) {
                throw new MojoExecutionException("Couldn't create transformer class '" + configuration.getClassName() + "'", e);
            }
            if (!ISourceTransformer.class.isAssignableFrom(clazz)) {
                throw new MojoExecutionException(
                    "Class '" + configuration.getClassName() + "' does not implement the ISourceTransformer interface");
            }
            Class<? extends ISourceTransformer> transformerClass = clazz.asSubclass(ISourceTransformer.class);
            try {
                MethodHandles.Lookup privateLookup = MethodHandles.privateLookupIn(transformerClass, lookup);
                MethodHandle handle;
                boolean withProperties = true;
                try {
                    handle = privateLookup.findConstructor(clazz, MethodType.methodType(void.class, Properties.class));
                } catch (NoSuchMethodException e0) {
                    handle = privateLookup.findConstructor(clazz, MethodType.methodType(void.class));
                    withProperties = false;
                }
                ISourceTransformer instance;
                if (withProperties) {
                    instance = transformerClass.cast(handle.invoke(configuration.getProperties()));
                } else {
                    instance = transformerClass.cast(handle.invoke());
                }
                transformers.add(instance);
            } catch (Throwable e) {
                throw new MojoExecutionException("Couldn't create instance of transformer '" + configuration.getClassName() + "'", e);
            }
        }
        return transformers;
    }

    private List<SourceFile> findSources() {
        if (sourceDirectories == null || sourceDirectories.isEmpty()) {
            return Collections.emptyList();
        }
        ObjectArrayList<SourceFile> files = new ObjectArrayList<>();
        ObjectArrayFIFOQueue<File> queue = new ObjectArrayFIFOQueue<>();
        SimpleFilter filter = new SimpleFilter(includes, excludes, excludeByDefault);
        File file;
        File[] fileArray;
        String path;
        for (String root : sourceDirectories) {
            file = new File(root);
            if (!file.exists() || !file.isDirectory()) {
                continue;
            }
            int rootLength = file.getAbsolutePath().length() + 1;
            queue.enqueue(file);
            while (!queue.isEmpty()) {
                file = queue.dequeue();
                fileArray = file.listFiles();
                if (fileArray == null || fileArray.length == 0) {
                    continue;
                }
                for (File current : fileArray) {
                    if (current.isDirectory()) {
                        queue.enqueue(current);
                        continue;
                    }
                    path = current.getAbsolutePath().substring(rootLength).replace('\\', '/');
                    if (filter.isFiltered(path)) {
                        continue;
                    }
                    files.add(new SourceFile(current, path));
                }
            }
        }
        return files;
    }

    private String resolvePathWithoutName(final String path) {
        int index = path.lastIndexOf('/');
        if (index == -1) {
            return path;
        }
        return path.substring(0, index + 1);
    }

    private String resolveOutputName(final String path) {
        int index = path.lastIndexOf('/');
        if (index == -1) {
            return path;
        }
        return path.substring(index + 1);
    }

    private String sanatizeName(final String name) {
        String[] parts = name.split("\\.", 2);
        if (parts.length != 2) {
            return name;
        }
        return name.substring(0, name.length() - parts[parts.length - 1].length() - 1);
    }

    private String replacePattern(String input, Pattern pattern, String replacement) {
        Matcher matcher = pattern.matcher(input);
        if (!matcher.matches()) {
            return input;
        }
        if (matcher.groupCount() >= 1) {
            int length = input.length();
            int start = matcher.start(1);
            int end = matcher.end(1);
            StringBuilder builder = new StringBuilder();
            if (start != 0) {
                builder.append(input.substring(0, start));
            }
            builder.append(replacement);
            if (end != length) {
                builder.append(input.substring(end, length));
            }
            return builder.toString();
        }
        return matcher.replaceAll(Matcher.quoteReplacement(replacement));
    }

    private URL resolveUrl(final String resource) {
        try {
            return new File(resource).toURI().toURL();
        } catch (final MalformedURLException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

}
