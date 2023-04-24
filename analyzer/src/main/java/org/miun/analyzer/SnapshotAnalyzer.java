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
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

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
        String command1 = String.format("mvn clean test -Dmaven.test.failure.ignore=true -Djacoco.skip=false -Djacoco.dataFile=target/jacoco.exec -DargLine=\"-javaagent:%s=destfile=target/jacoco.exec\"", JACOCO_AGENT_PATH);
        CommandRunner.runCommand(command1, repoDir);

        List<File> modules = findModules(repoDir);
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
                CommandRunner.runCommand(reportCommand, module);
            }
        }
    }

    private static List<File> findModules(File repoDir) {
        List<File> modules = new ArrayList<>();
        File pomFile = new File(repoDir, "pom.xml");
        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(pomFile);
            doc.getDocumentElement().normalize();

            NodeList nodeList = doc.getElementsByTagName("module");

            for (int i = 0; i < nodeList.getLength(); i++) {
                Node node = nodeList.item(i);
                if (node.getNodeType() == Node.ELEMENT_NODE) {
                    Element element = (Element) node;
                    String moduleName = element.getTextContent();
                    File moduleDir = new File(repoDir, moduleName);
                    modules.add(moduleDir);
                }
            }
        } catch (ParserConfigurationException | SAXException | IOException e) {
            e.printStackTrace();
        }
        return modules;
    }
}
