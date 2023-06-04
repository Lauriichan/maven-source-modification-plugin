package me.lauriichan.maven.sourcemod;

import java.io.File;

final class SourceFile {
    
    private final File file;
    private final String relativePath;
    
    public SourceFile(final File file, final String relativePath) {
        this.file = file;
        this.relativePath = relativePath;
    }
    
    public File file() {
        return file;
    }
    
    public String relativePath() {
        return relativePath;
    }

}
