package me.lauriichan.maven.sourcemod;

import java.io.File;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

import it.unimi.dsi.fastutil.objects.ObjectArrayFIFOQueue;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;

@Mojo(name = "deleteSources", defaultPhase = LifecyclePhase.PREPARE_PACKAGE, threadSafe = true)
public class DeleteModifiedTemplatesMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project}", required = true, readonly = true)
    private MavenProject project;

    @Parameter(defaultValue = "${project.build.outputDirectory}")
    private List<String> roots;

    @Parameter
    private Set<String> includes = new HashSet<>();

    @Parameter
    private Set<String> excludes = new HashSet<>();

    @Parameter
    private boolean excludeByDefault = true;

    @Parameter(property = "sourcemodification.skip-delete")
    private boolean skip = false;

    @Parameter(property = "sourcemodification.purge-empty")
    private boolean purgeEmptyPackages = false;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            List<SourceFile> sources = findSources();
            if (sources.isEmpty()) {
                getLog().info("No sources to delete");
                return;
            }
            ObjectOpenHashSet<File> scanned = new ObjectOpenHashSet<>();
            ObjectArrayFIFOQueue<File> directories = new ObjectArrayFIFOQueue<>();
            File file, parent;
            int amount = 0;
            for (SourceFile sourceFile : sources) {
                file = sourceFile.file();
                if (!file.exists()) {
                    continue;
                }
                if (purgeEmptyPackages) {
                    if ((parent = file.getParentFile()) != null && !scanned.contains(parent)) {
                        directories.enqueue(parent);
                        scanned.add(parent);
                    }
                }
                getLog().info("Deleting '" + sourceFile.relativePath() + "'");
                forceDelete(file);
            }
            getLog().info("Deleted " + amount + " source(s)!");
            if (!purgeEmptyPackages) {
                return;
            }
            amount = 0;
            String[] list;
            while (!directories.isEmpty()) {
                file = directories.dequeue();
                if (!file.exists() || (list = file.list()) != null && list.length != 0) {
                    continue;
                }
                if ((parent = file.getParentFile()) != null && !scanned.contains(parent)) {
                    directories.enqueue(parent);
                    scanned.add(parent);
                }
                amount++;
                file.delete();
            }
            if (amount == 1) {
                getLog().info("Purged " + amount + " directory!");
            } else {
                getLog().info("Purged " + amount + " directories!");
            }
        } catch (Exception e) {
            getLog().error(e);
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    private List<SourceFile> findSources() {
        if (roots == null || roots.isEmpty()) {
            return Collections.emptyList();
        }
        ObjectArrayList<SourceFile> files = new ObjectArrayList<>();
        ObjectArrayFIFOQueue<File> queue = new ObjectArrayFIFOQueue<>();
        SimpleFilter filter = new SimpleFilter(includes, excludes, excludeByDefault);
        File file;
        File[] fileArray;
        String path;
        for (String root : roots) {
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

    private void forceDelete(File file) {
        if(file.isFile()) {
            file.delete();
            return;
        }
        ObjectArrayFIFOQueue<File> queue = new ObjectArrayFIFOQueue<>();
        queue.enqueue(file);
        File[] files;
        while (!queue.isEmpty()) {
            file = queue.dequeue();
            if (file.isFile()) {
                file.delete();
                continue;
            }
            files = file.listFiles();
            if (files == null || files.length == 0) {
                file.delete();
                continue;
            }
            for (File current : files) {
                queue.enqueue(current);
            }
            queue.enqueue(file);
        }
    }

}
