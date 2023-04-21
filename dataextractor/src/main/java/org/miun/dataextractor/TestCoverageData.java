package org.miun.dataextractor;

public record TestCoverageData(String packageName, String className, int instructionCovered, int instructionMissed) {
}
