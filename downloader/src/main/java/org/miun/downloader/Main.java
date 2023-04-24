package org.miun.downloader;

import static org.miun.constants.Constants.OSS_PROJECTS;
import org.miun.downloader.exceptions.RepositoryAlreadyExistsException;

public class Main {

    public static void main(String[] args) throws RepositoryAlreadyExistsException {
        SnapshotDownloader downloader = new SnapshotDownloader(OSS_PROJECTS);
        downloader.download();
    }
}
