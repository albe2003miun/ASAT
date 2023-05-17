package org.miun.constants;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

public class Constants {
    public static String DESIGNITE_JAR_PATH;
    public static String JACOCO_CLI_PATH;
    public static String JACOCO_AGENT_PATH;
    public static String DV8_CONSOLE;
    public static String BASE_SNAPSHOT_DIRECTORY;
    public static String RESULTS_DIRECTORY;
    public static List<String> OSS_PROJECTS;

    static {
        String configFilename = "config.properties";

        try (InputStream configInput =
                     Constants.class.getClassLoader().getResourceAsStream(configFilename)) {
            Properties config = new Properties();

            if (configInput == null) {
                throw new IOException("Unable to find config.properties.");
            }

            config.load(configInput);

            if (config.values().isEmpty()) {
                throw new IllegalArgumentException("config.properties is empty.");
            }

            DESIGNITE_JAR_PATH = config.getProperty("designiteJarPath");
            JACOCO_CLI_PATH = config.getProperty("jacocoCliPath");
            JACOCO_AGENT_PATH = config.getProperty("jacocoAgentPath");
            DV8_CONSOLE = config.getProperty("dv8ConsolePath");
            BASE_SNAPSHOT_DIRECTORY = config.getProperty("snapshotsDirectory");
            RESULTS_DIRECTORY = config.getProperty("resultsDirectory");
            OSS_PROJECTS = Arrays.asList(config.getProperty("projectUrls").split(","));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

