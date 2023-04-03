package org.miun;

import java.util.Map;

public class DesigniteResults {
    private final Map<String, Integer> smellCounts;

    public DesigniteResults(Map<String, Integer> smellCounts) {
        System.out.println(smellCounts);
        this.smellCounts = smellCounts;
    }
}
