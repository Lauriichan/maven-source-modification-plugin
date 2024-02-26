package me.lauriichan.maven.sourcemod;

import java.util.Properties;

import org.apache.maven.plugins.annotations.Parameter;

public final class SourceGeneratorConfiguration {

    @Parameter(property = "className", required = true)
    private String className;

    @Parameter(property = "properties", required = false)
    private Properties properties;

    public String getClassName() {
        if (className == null) {
            return "";
        }
        return className;
    }

    public void setClassName(final String className) {
        this.className = className;
    }

    public Properties getProperties() {
        if (properties == null) {
            return properties = new Properties();
        }
        return properties;
    }

    public void setProperties(final Properties properties) {
        this.properties = properties == null ? new Properties() : (Properties) properties.clone();
    }
    
}
