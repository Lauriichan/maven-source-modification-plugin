package me.lauriichan.maven.sourcemod.api;

import org.jboss.forge.roaster.model.source.JavaSource;

public interface ISourceTransformer {
    
    boolean canTransform(JavaSource<?> source);
    
    void transform(JavaSource<?> source);

}
