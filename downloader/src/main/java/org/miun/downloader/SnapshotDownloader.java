package org.miun.downloader;
import static org.miun.constants.Constants.*;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.miun.downloader.exceptions.RepositoryAlreadyExistsException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SnapshotDownloader {
    private final List<String> repositories;

    public SnapshotDownloader(List<String> repositories) {
        this.repositories = repositories;
    }

    public void download() throws RepositoryAlreadyExistsException {
        File baseSnapshotDirectory = new File(BASE_SNAPSHOT_DIRECTORY);
        if (!baseSnapshotDirectory.exists()) {
            baseSnapshotDirectory.mkdirs();
        }

        for (String repo : repositories) {
            String repoName = getRepoNameFromUrl(repo);
            File repoOutputDirectory = new File(baseSnapshotDirectory, repoName);
            if (repoOutputDirectory.exists()) {
                throw new RepositoryAlreadyExistsException("Repository " + repoName + " is already exists.");
            }

            repoOutputDirectory.mkdirs();

            File localRepo = cloneRepo(repo);
            List<Date> commitDates = getCommitDates(localRepo, 4);

            for (Date commitDate : commitDates) {
                checkoutCommit(localRepo, commitDate);
                saveSnapshot(localRepo, repoOutputDirectory, commitDate);
            }
        }
    }

    private static void saveSnapshot(File localRepo, File outputDirectory, Date commitDate) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");
        String snapshotDirectoryName = dateFormat.format(commitDate);
        File snapshotDirectory = new File(outputDirectory, snapshotDirectoryName);

        if (!snapshotDirectory.exists()) {
            snapshotDirectory.mkdirs();
        }

        try {
            Files.walk(localRepo.toPath())
                    .forEach(sourcePath -> {
                        Path targetPath = snapshotDirectory.toPath().resolve(localRepo.toPath().relativize(sourcePath));
                        try {
                            if (Files.isDirectory(sourcePath)) {
                                if (!Files.exists(targetPath)) {
                                    Files.createDirectory(targetPath);
                                }
                            } else {
                                Files.copy(sourcePath, targetPath);
                            }
                        } catch (IOException e) {
                            System.err.println("Error saving snapshot: " + e.getMessage());
                            System.err.println("Source path: " + sourcePath);
                            System.err.println("Target path: " + targetPath);
                        }
                    });
        } catch (IOException e) {
            System.err.println("Error walking file tree: " + e.getMessage());
            System.exit(1);
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
        Collections.reverse(commitDates);
        return commitDates;
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

    private static void checkoutCommit(File localRepo, Date commitDate) {
        try (Repository repository = new FileRepositoryBuilder().setGitDir(new File(localRepo, ".git")).build();
             Git git = new Git(repository)) {

            ObjectId commitId = findCommitByDate(repository, commitDate);
            if (commitId != null) {
                git.checkout().setName(commitId.getName()).call();
            } else {
                System.err.println("No commit found for the specified date: " + commitDate);
            }
        } catch (IOException | GitAPIException e) {
            System.err.println("Error checking out commit: " + e.getMessage());
            System.exit(1);
        }
    }


    private static ObjectId findCommitByDate(Repository repository, Date targetDate) {
        try (RevWalk revWalk = new RevWalk(repository)) {
            ObjectId head = repository.resolve("HEAD");
            revWalk.markStart(revWalk.parseCommit(head));

            RevCommit closestCommit = null;
            long closestTimeDifference = Long.MAX_VALUE;

            for (RevCommit commit : revWalk) {
                Date currentCommitDate = Date.from(commit.getAuthorIdent().getWhen().toInstant());
                long timeDifference = Math.abs(currentCommitDate.getTime() - targetDate.getTime());

                if (timeDifference < closestTimeDifference) {
                    closestCommit = commit;
                    closestTimeDifference = timeDifference;
                }
            }

            return closestCommit != null ? closestCommit.getId() : null;
        } catch (IOException e) {
            System.err.println("Error finding commit by date: " + e.getMessage());
            System.exit(1);
        }

        return null;
    }


    private static String getRepoNameFromUrl(String repoUrl) {
        Pattern pattern = Pattern.compile(".*/(.*?)(\\.git)?/?$");
        Matcher matcher = pattern.matcher(repoUrl);

        if (matcher.find()) {
            return matcher.group(1);
        } else {
            throw new IllegalArgumentException("Invalid repository URL: " + repoUrl);
        }
    }
}
