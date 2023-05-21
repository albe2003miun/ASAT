package org.miun.constants;

public enum Platform {
    LINUX("mvn"),
    WINDOWS("mvn.cmd");

    private final String mvnCommand;

    Platform(String mvnCommand) {
        this.mvnCommand = mvnCommand;
    }

    public String getMvnCommand() {
        return mvnCommand;
    }
}
