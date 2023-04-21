package org.miun.dataextractor;

public record TypeMetricsData(String packageName, String className, int loc, int fanIn, int fanOut) {
}
