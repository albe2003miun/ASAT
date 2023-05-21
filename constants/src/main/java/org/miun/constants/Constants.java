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
    public static boolean ANALYZE_ARCHITECTURAL_SMELLS;
    public static boolean ANALYZE_TESTABILITY;
    public static boolean ANALYZE_MODULARITY;
    public static int WEEK_INTERVAL;
    public static Platform PLATFORM;
    public static String FAIL_IF_NO_TESTS_FOUND;
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
            ANALYZE_ARCHITECTURAL_SMELLS = config.getProperty("analyzeArchitecturalSmells").equalsIgnoreCase("true");
            ANALYZE_TESTABILITY = !config.getProperty("analyzeTestability").equalsIgnoreCase("false");
            ANALYZE_MODULARITY = !config.getProperty("analyzeModularity").equalsIgnoreCase("false");
            WEEK_INTERVAL = config.getProperty("weekInterval") == null ? Integer.parseInt(config.getProperty("weekInterval")) : 4;
            PLATFORM = config.getProperty("platform").equalsIgnoreCase("linux") ? Platform.LINUX : Platform.WINDOWS;
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

