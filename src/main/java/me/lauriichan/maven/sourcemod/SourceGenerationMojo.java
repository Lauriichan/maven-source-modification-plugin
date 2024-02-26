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
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

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
import me.lauriichan.maven.sourcemod.api.ISourceGenerator;

@Mojo(name = "generateSource", defaultPhase = LifecyclePhase.GENERATE_SOURCES, threadSafe = true, requiresDependencyResolution = ResolutionScope.COMPILE)
public class SourceGenerationMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    private MavenProject project;

    @Parameter
    private SourceGeneratorConfiguration[] generators;

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
    private boolean excludeByDefault = false;

    @Parameter(property = "sourcegeneration.skip")
    private boolean skip = false;

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
            List<ISourceGenerator> generators = findTransformers();
            if (generators.isEmpty()) {
                getLog().info("No source generators found");
                return;
            }
            ObjectArrayList<JavaSource<?>> sourceList = new ObjectArrayList<>();
            for (SourceFile source : sources) {
                sourceList.add(Roaster.parse(JavaSource.class, source.file()));
            }
            SourcePackageImpl pkgImpl = new SourcePackageImpl(sourceList);
            for (ISourceGenerator generator : generators) {
                generator.generateSources(pkgImpl);
            }
            String pkg;
            File outputFile;
            for (JavaSource<?> source : pkgImpl.generatedSources()) {
                if ((pkg = source.getPackage()) == null || pkg.isBlank()) {
                    outputFile = new File(outputDirectory, source.getName() + ".java");
                    pkg = "";
                } else {
                    outputFile = new File(outputDirectory, pkg.replace('.', '/') + '/' + source.getName() + ".java");
                    pkg = pkg + '.';
                }
                getLog().info("Generated class '" + pkg + source.getName() + "'");
                try {
                    createFile(outputFile);
                    FileWriter writer = new FileWriter(outputFile);
                    writer.write(source.toString());
                    writer.flush();
                    writer.close();
                } catch(IOException e) {
                    getLog().error("Failed to save class '" + pkg + source.getName() + "' to file.", e);
                }
            }
        } catch (Exception e) {
            getLog().error(e);
            throw new MojoExecutionException(e.getMessage(), e);
        } finally {
            Thread.currentThread().setContextClassLoader(originalLoader);
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

    private List<ISourceGenerator> findTransformers() throws MojoExecutionException {
        if (this.generators == null || this.generators.length == 0) {
            return Collections.emptyList();
        }
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        ObjectArrayList<ISourceGenerator> generators = new ObjectArrayList<>();
        MethodHandles.Lookup lookup = MethodHandles.lookup();
        for (SourceGeneratorConfiguration configuration : this.generators) {
            Class<?> clazz;
            try {
                clazz = Class.forName(configuration.getClassName(), true, classLoader);
            } catch (ClassNotFoundException e) {
                throw new MojoExecutionException("Couldn't create generator class '" + configuration.getClassName() + "'", e);
            }
            if (!ISourceGenerator.class.isAssignableFrom(clazz)) {
                throw new MojoExecutionException(
                    "Class '" + configuration.getClassName() + "' does not implement the ISourceGenerator interface");
            }
            Class<? extends ISourceGenerator> generatorClass = clazz.asSubclass(ISourceGenerator.class);
            try {
                MethodHandles.Lookup privateLookup = MethodHandles.privateLookupIn(generatorClass, lookup);
                MethodHandle handle;
                boolean withProperties = true;
                try {
                    handle = privateLookup.findConstructor(clazz, MethodType.methodType(void.class, Properties.class));
                } catch (NoSuchMethodException e0) {
                    handle = privateLookup.findConstructor(clazz, MethodType.methodType(void.class));
                    withProperties = false;
                }
                ISourceGenerator instance;
                if (withProperties) {
                    instance = generatorClass.cast(handle.invoke(configuration.getProperties()));
                } else {
                    instance = generatorClass.cast(handle.invoke());
                }
                generators.add(instance);
            } catch (Throwable e) {
                throw new MojoExecutionException("Couldn't create instance of generator '" + configuration.getClassName() + "'", e);
            }
        }
        return generators;
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

    private void createFile(File file) throws IOException {
        if (!file.exists()) {
            File parent;
            if ((parent = file.getParentFile()) != null && !parent.exists()) {
                parent.mkdirs();
            }
        } else {
            file.delete();
        }
        file.createNewFile();
    }

    private URL resolveUrl(final String resource) {
        try {
            return new File(resource).toURI().toURL();
        } catch (final MalformedURLException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

}
