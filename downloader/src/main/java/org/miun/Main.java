package org.miun;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

public class Main {

    public static void main(String[] args) {
        String repoUrl = "https://github.com/apolloconfig/apollo";
        File localRepo = cloneRepo(repoUrl);
        List<Date> commitDates = getCommitDates(localRepo, 4);

        System.out.println(commitDates.size());

        for (Date commitDate : commitDates) {
            System.out.println(commitDate);
//            // Checkout the commit
//            checkoutCommit(localRepo, commitDate);
//
//            // Analyze with Designite
//            DesigniteResult designiteResult = analyzeWithDesignite(localRepo);
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
        }
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
}

