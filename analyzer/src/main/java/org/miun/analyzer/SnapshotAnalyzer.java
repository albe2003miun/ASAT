package org.miun.analyzer;

import org.miun.analyzer.exceptions.SnapshotResultDirectoryAlreadyExists;
import org.miun.analyzer.support.CommandRunner;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.miun.constants.Constants.*;

public class SnapshotAnalyzer {
    private static final File DESIGNITE_JAR = new File(DESIGNITE_JAR_PATH);

    public void analyzeSnapshots() {
        File snapshotsFolder = new File(BASE_SNAPSHOT_DIRECTORY);
        if (!snapshotsFolder.isDirectory()) {
            System.err.println("The specified path is not a directory.");
            return;
        }

        File[] projects = snapshotsFolder.listFiles(File::isDirectory);
        if (projects == null) {
            System.err.println("Unable to access project folders.");
            return;
        }

        for (File project : projects) {
            analyzeProjectSnapshots(project);
        }
    }

    private static void analyzeProjectSnapshots(File projectFolder) {
        File projectResultsDirectory = new File(RESULTS_DIRECTORY, projectFolder.getName());
        if (!projectResultsDirectory.exists()) {
            projectResultsDirectory.mkdirs();
        }

        File[] snapshotFolders = projectFolder.listFiles(File::isDirectory);
        if (snapshotFolders == null) {
            System.err.println("Unable to access snapshots in project: " + projectFolder.getName());
            return;
        }

        for (File snapshotFolder : snapshotFolders) {
            try {
                analyzeSnapshot(snapshotFolder, projectResultsDirectory);
            } catch (SnapshotResultDirectoryAlreadyExists e) {
                System.err.printf("Could not analyze snapshot. %s%n", e.getMessage());
            }
        }
    }

    private static void analyzeSnapshot(File snapshot, File projectResultsDirectory) throws SnapshotResultDirectoryAlreadyExists {
        File snapshotResultsDirectory = new File(projectResultsDirectory, snapshot.getName());

        if (snapshotResultsDirectory.exists()) {
            throw new SnapshotResultDirectoryAlreadyExists(String.format("Result directory for snapshot %s in the project %s already exists", snapshot.getName(), projectResultsDirectory.getName()));
        }

        snapshotResultsDirectory.mkdirs();

        analyzeWithDesignite(snapshot, snapshotResultsDirectory);
        buildProjectAndGenerateReport(snapshot, snapshotResultsDirectory);
        analyzeWithDV8(snapshot, snapshotResultsDirectory);
    }

    private static void analyzeWithDesignite(File repoDir, File baseOutputDirectory) {
        File resultsDirectory = new File(baseOutputDirectory, "DesigniteResults");
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "java",
                    "-jar",
                    DESIGNITE_JAR.getAbsolutePath(),
                    "-i",
                    repoDir.getAbsolutePath(),
                    "-o",
                    resultsDirectory.getAbsolutePath(),
                    "-f",
                    "csv"
            );
            Process process = processBuilder.start();
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                System.err.println("Designite exited with an error: " + exitCode);
            } else {
                System.out.println("Designite analysis completed successfully");
            }
        } catch (IOException | InterruptedException e) {
            System.err.println("Error running Designite: " + e.getMessage());
            System.exit(1);
        }
    }

    private static void buildProjectAndGenerateReport(File repoDir, File baseOutputDirectory) {
        String command1 = String.format("mvn clean test -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false -Dmaven.test.failure.ignore=true -Djacoco.skip=false -Djacoco.dataFile=target/jacoco.exec -DargLine=\"-javaagent:%s=destfile=target/jacoco.exec\"", JACOCO_AGENT_PATH);
        CommandRunner.runTestCommand(command1, repoDir, new File(baseOutputDirectory, "testdata.csv"));

        List<File> modules = findModules(repoDir, new ArrayList<>());
        File resultsDirectory = new File(baseOutputDirectory, "JacocoResults");
        if (!resultsDirectory.exists()) {
            resultsDirectory.mkdirs();
        }

        for (File module : modules) {
            File jacocoExecFile = new File(module, "target/jacoco.exec");
            if (jacocoExecFile.exists()) {
                String moduleName = module.getName();
                File moduleReportFile = new File(resultsDirectory, moduleName + ".csv");

                String reportCommand = String.format("java -jar %s report %s --classfiles %s --sourcefiles %s --csv %s", JACOCO_CLI_PATH, jacocoExecFile.getAbsolutePath(), new File(module, "target/classes").getAbsolutePath(), new File(module, "src/main/java").getAbsolutePath(), moduleReportFile.getAbsolutePath());
                CommandRunner.runStandardCommand(reportCommand, module);
            }
        }
    }

    private static List<File> findModules(File repoDir, List<File> modules) {
        File pomFile = new File(repoDir, "pom.xml");
        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();

            if (!pomFile.exists()) return modules;

            Document doc = dBuilder.parse(pomFile);
            doc.getDocumentElement().normalize();

            NodeList nodeList = doc.getElementsByTagName("module");

            for (int i = 0; i < nodeList.getLength(); i++) {
                Node node = nodeList.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    Element element = (Element) node;
                    String moduleName = element.getTextContent();
                    File moduleDir = new File(repoDir, moduleName);

                    if (!moduleDir.exists()) break;
                    if (!modules.contains(moduleDir)) modules.add(moduleDir);

                    // recursively add all submodules
                    findModules(moduleDir, modules);
                }
            }
        } catch (ParserConfigurationException | SAXException | IOException e) {
            e.printStackTrace();
        }
        return modules;
    }

    private static void analyzeWithDV8(File repoDir, File baseOutputDir) {
        Properties properties = new Properties();
        String resultFolder = "DV8Results";

//        properties.setProperty("inputFolder", repoDir.getAbsolutePath());
        properties.setProperty("outputFolder", baseOutputDir.getAbsolutePath());
        properties.setProperty("projectName", resultFolder);
        properties.setProperty("sourceType", "code");
        properties.setProperty("sourceCodePath", repoDir.getAbsolutePath());
        properties.setProperty("sourceCodeLanguage", "java");

        // Create a temporary file with the specified prefix and suffix
        File propertiesFile = null;
        try {
            propertiesFile = File.createTempFile("config", ".properties");
        } catch (IOException e) {
            System.err.println("Error while creating the temporary properties file: " + e.getMessage());
            return;
        }

        // Write the properties to the file
        try (FileOutputStream outputStream = new FileOutputStream(propertiesFile)) {
            properties.store(outputStream, "This is a sample properties file");
            System.out.println("Properties file created successfully.");
        } catch (IOException e) {
            System.err.println("Error while writing to the properties file: " + e.getMessage());
        }

        String command = String.format("%s arch-report -paramsFile %s", DV8_CONSOLE, propertiesFile.getAbsolutePath());

        CommandRunner.runStandardCommand(command, repoDir);
    }
}
