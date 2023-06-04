package me.lauriichan.maven.sourcemod;

import java.util.regex.Pattern;

import org.apache.maven.plugins.annotations.Parameter;

public final class ReplacementConfiguration {

    @Parameter(property = "regex")
    private boolean regex = false;

    @Parameter(property = "pattern", required = true)
    private String pattern;

    @Parameter(property = "replace", required = true)
    private String replace;

    private volatile Pattern compiledPattern;

    public boolean isRegex() {
        return regex;
    }

    public void setRegex(boolean regex) {
        this.regex = regex;
    }

    public Pattern getCompiledPattern() {
        if (compiledPattern == null) {
            return compiledPattern = Pattern.compile(getPattern());
        }
        return compiledPattern;
    }

    public String getPattern() {
        if (pattern == null) {
            return "";
        }
        return pattern;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
        this.compiledPattern = null;
    }

    public String getReplace() {
        if (replace == null) {
            return "";
        }
        return replace;
    }

    public void setReplace(String replace) {
        this.replace = replace;
    }

}
