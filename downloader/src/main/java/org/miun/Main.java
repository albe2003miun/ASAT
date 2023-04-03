package org.miun;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import static org.miun.support.Constants.*;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

public class Main {

    public static void main(String[] args) {
        String repoUrl = "https://github.com/apolloconfig/apollo";
        File localRepo = cloneRepo(repoUrl);
        List<Date> commitDates = getCommitDates(localRepo, 4);

        System.out.println(commitDates.size());


        Date commitDate = commitDates.get(40);

        checkoutCommit(localRepo, commitDate);

        File outputDirectory = new File(OUTPUT_DIRECTORY_PATH);
        if (!outputDirectory.exists()) {
            outputDirectory.mkdirs();
        }

        // Analyze with Designite
        analyzeWithDesignite(new File(DESIGNITE_JAR_PATH), localRepo, outputDirectory);

        String architectureSmellsInputFile = new File(outputDirectory, "ArchitectureSmells.csv").getAbsolutePath();
        DesigniteResults designiteResults = createSummary(architectureSmellsInputFile);

//        for (Date commitDate : commitDates) {
//            // Checkout the commit
//            checkoutCommit(localRepo, commitDate);
//
//            File outputDirectory = new File(OUTPUT_DIRECTORY_PATH);
//            if (!outputDirectory.exists()) {
//                outputDirectory.mkdirs();
//            }
//
//            // Analyze with Designite
//            analyzeWithDesignite(new File(DESIGNITE_JAR_PATH), localRepo, outputDirectory);
//
//            // Calculate propagation cost
//            double propagationCost = calculatePropagationCost(designiteResult);
//
//            // Get test coverage data
//            JacocoResult jacocoResult = getJacocoCoverageData(localRepo);
//
//            // Calculate average test coverage
//            TestCoverageData testCoverageData = calculateTestCoverageData(jacocoResult, designiteResult);
//
//            // Write CSV output
//            writeSnapshotToCSV(commitDate, designiteResult, propagationCost, testCoverageData);
//        }
    }


    private static File cloneRepo(String repoUrl) {
        try {
            Path tempDir = Files.createTempDirectory("repo-");
            Git.cloneRepository()
                    .setURI(repoUrl)
                    .setDirectory(tempDir.toFile())
                    .setCredentialsProvider(new UsernamePasswordCredentialsProvider("<username>", "<password>")) // Optional, if the repository is private
                    .call();
            return tempDir.toFile();
        } catch (IOException | GitAPIException e) {
            System.err.println("Error cloning repository: " + e.getMessage());
            System.exit(1);
            return null;
        }
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

    private static DesigniteResults createSummary(String inputFile) {
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

            return new DesigniteResults(smellCounts);
        } catch (IOException e) {
            System.err.println("Error reading or writing CSV files: " + e.getMessage());
            System.exit(1);
            return null;
        }
    }
}

