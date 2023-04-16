package org.miun.downloader;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import static org.miun.constants.Constants.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

public class Main {

    public static void main(String[] args) throws IOException {
        String repoUrl = "https://github.com/apolloconfig/apollo";
        File localRepo = cloneRepo(repoUrl);
        List<Date> commitDates = getCommitDates(localRepo, 4);

        System.out.println(commitDates.size());

        Date commitDate = commitDates.get(40);

        checkoutCommit(localRepo, commitDate);

        File outputDirectory = new File(RESULTS_DIRECTORY);
        if (!outputDirectory.exists()) {
            outputDirectory.mkdirs();
        }

        // Analyze with Designite
        analyzeWithDesignite(new File(DESIGNITE_JAR_PATH), localRepo, outputDirectory);

        String architectureSmellsInputFile = new File(outputDirectory, "ArchitectureSmells.csv").getAbsolutePath();
        Map<String, Integer> smellCounts = getSmellCounts(architectureSmellsInputFile);

        String typeMetricsInputFile = new File(outputDirectory, "TypeMetrics.csv").getAbsolutePath();
        double propagationCost = calculatePropagationCost(typeMetricsInputFile);

        buildProjectAndGenerateReport(localRepo);
    }

    private static void buildProjectAndGenerateReport(File repoDir) {
        String command1 = String.format("mvn clean test -DargLine=\"-javaagent:%s=destfile=target/jacoco.exec\"", JACOCO_AGENT_PATH);
        runCommand(command1, repoDir, false);

        List<File> modules = findModules(repoDir);
        File customReportsDir = new File(RESULTS_DIRECTORY);
        if (!customReportsDir.exists()) {
            customReportsDir.mkdirs();
        }

        for (File module : modules) {
            if (new File(module, "target/jacoco.exec").exists()) {
                String moduleName = module.getName();
                File moduleReportFile = new File(customReportsDir, moduleName + ".csv");

                String reportCommand = String.format("java -jar %s report target/jacoco.exec --classfiles target/classes --sourcefiles src/main/java --csv %s", JACOCO_CLI_PATH, moduleReportFile.getAbsolutePath());
                runCommand(reportCommand, module, true);
            }
        }
    }

    private static void runCommand(String command, File workingDir, boolean printLines) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder();
            processBuilder.command(command.split(" "));
            processBuilder.directory(workingDir);
            Process process = processBuilder.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }

            int exitCode = process.waitFor();
            System.out.println("Exited with code: " + exitCode);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static File cloneRepo(String repoUrl) {
        try {
            Path tempDir = Files.createTempDirectory("repo-");
            Git.cloneRepository()
                    .setURI(repoUrl)
                    .setDirectory(tempDir.toFile())
                    .call();
            return tempDir.toFile();
        } catch (IOException | GitAPIException e) {
            System.err.println("Error cloning repository: " + e.getMessage());
            System.exit(1);
            return null;
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

    private static List<Date> getCommitDates(File localRepo, int intervalWeeks) {
        List<Date> commitDates = new ArrayList<>();
        try (Repository repository = new FileRepositoryBuilder().setGitDir(new File(localRepo, ".git")).build();
             Git git = new Git(repository)) {

            Iterable<RevCommit> commits = git.log().call();
            List<RevCommit> commitList = new ArrayList<>();
            for (RevCommit commit : commits) {
                commitList.add(commit);
            }
            // Reverse the order of commits
            Collections.reverse(commitList);

            LocalDate prevCommitDate = null;

            for (RevCommit commit : commitList) {
                Date commitDate = Date.from(commit.getAuthorIdent().getWhen().toInstant());
                LocalDate localDate = commitDate.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();

                if (prevCommitDate == null || localDate.isAfter(prevCommitDate.plusWeeks(intervalWeeks))) {
                    commitDates.add(commitDate);
                    prevCommitDate = localDate;
                }
            }
        } catch (IOException | GitAPIException e) {
            System.err.println("Error getting commit dates: " + e.getMessage());
            System.exit(1);
        }
        return commitDates;
    }

    private static void checkoutCommit(File localRepo, Date commitDate) {
        try (Repository repository = new FileRepositoryBuilder().setGitDir(new File(localRepo, ".git")).build();
             Git git = new Git(repository)) {

            ObjectId commitId = findCommitByDate(repository, commitDate);
            if (commitId != null) {
                git.checkout().setName(commitId.getName()).call();
            } else {
                System.err.println("No commit found for the specified date: " + commitDate);
                System.exit(1);
            }
        } catch (IOException | GitAPIException e) {
            System.err.println("Error checking out commit: " + e.getMessage());
            System.exit(1);
        }
    }

    private static ObjectId findCommitByDate(Repository repository, Date commitDate) {
        try (RevWalk revWalk = new RevWalk(repository)) {
            ObjectId head = repository.resolve("HEAD");
            revWalk.markStart(revWalk.parseCommit(head));

            for (RevCommit commit : revWalk) {
                Date currentCommitDate = Date.from(commit.getAuthorIdent().getWhen().toInstant());
                if (currentCommitDate.equals(commitDate)) {
                    return commit.getId();
                }
            }
        } catch (IOException e) {
            System.err.println("Error finding commit by date: " + e.getMessage());
            System.exit(1);
        }
        return null;
    }

    private static void analyzeWithDesignite(File designiteJar, File inputDir, File outputDir) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(
                    "java",
                    "-jar",
                    designiteJar.getAbsolutePath(),
                    "-i",
                    inputDir.getAbsolutePath(),
                    "-o",
                    outputDir.getAbsolutePath(),
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

    private static Map<String, Integer> getSmellCounts(String inputFile) {
        try (BufferedReader reader = new BufferedReader(new FileReader(inputFile))) {
            // Skip header
            reader.readLine();

            Map<String, Integer> smellCounts = new HashMap<>();

            String line;
            while ((line = reader.readLine()) != null) {
                String[] columns = line.split(",");
                String smellType = columns[2].trim();

                if (smellCounts.containsKey(smellType)) {
                    smellCounts.put(smellType, smellCounts.get(smellType) + 1);
                } else {
                    smellCounts.put(smellType, 1);
                }
            }

            return smellCounts;
        } catch (IOException e) {
            System.err.println("Error reading or writing CSV files: " + e.getMessage());
            System.exit(1);
            return null;
        }
    }

    private static double calculatePropagationCost(String typeMetricsCsvPath) {
        int totalFanIn = 0;
        int numberOfClasses = 0;

        try (BufferedReader reader = new BufferedReader(new FileReader(typeMetricsCsvPath))) {
            reader.readLine();  // skip header

            String line;
            while ((line = reader.readLine()) != null) {
                String[] columns = line.split(",");
                int fanIn = Integer.parseInt(columns[12]);

                totalFanIn += fanIn;
                numberOfClasses++;
            }
        } catch (IOException e) {
            System.err.println("Error reading TypeMetrics.csv: " + e.getMessage());
            System.exit(1);
        }

        return numberOfClasses != 0 ? (double) totalFanIn / (numberOfClasses * numberOfClasses) : 0.0;  // to avoid division by zero
    }
}

