package org.miun.downloader;

import static org.miun.constants.Constants.OSS_PROJECTS;
import org.miun.downloader.exceptions.RepositoryAlreadyExistsException;

import java.util.List;

public class Runner {

    public static void main(String[] args) throws RepositoryAlreadyExistsException {
        SnapshotDownloader downloader = new SnapshotDownloader(OSS_PROJECTS);
        downloader.download();
    }
}
