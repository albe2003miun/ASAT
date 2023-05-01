package org.miun.dataextractor;

import java.io.*;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import static org.miun.constants.Constants.*;

public class DataExtractor {
    // needs to be exactly as in results from used tools
    private static final List<String> ARCHITECTURAL_SMELLS = List.of(
            "Ambiguous Interface",
            "Cyclic Dependency",
            "Dense Structure",
            "Feature Concentration",
            "God Component",
            "Scattered Functionality",
            "Unstable Dependency"
    );
    private static final String ALL_PACKAGES_KEY = "<All packages>";
    private static final String DECOUPLING_LEVEL = "Decoupling Level";
    private static final String PROPAGATION_COST = "Propagation Cost";

    public void generateOutputFiles() throws IOException {
        File snapshotResults = new File(RESULTS_DIRECTORY);

        for (File project : Objects.requireNonNull(snapshotResults.listFiles(File::isDirectory))) {
            for (File snapshot : Objects.requireNonNull(project.listFiles(File::isDirectory))) {
                Map<String, Map<String, Integer>> systemSmells = getSystemSmells(snapshot);
                Map<String, List<TypeMetricsData>> systemFanInFanOutData = getSystemFanInFanOutData(snapshot);
                Map<String, List<TestCoverageData>> systemTestCoverageData = getSystemTestCoverageData(snapshot);
                double decouplingLevel = getSystemMetric(snapshot, DECOUPLING_LEVEL);
                double propagationCost = getSystemMetric(snapshot, PROPAGATION_COST);
                writeToCsv(new File(snapshot, "output.csv"), systemSmells, systemFanInFanOutData, systemTestCoverageData, decouplingLevel, propagationCost);
            }
        }
    }

    private Map<String, Map<String, Integer>> getSystemSmells(File snapshot) {
        String architectureSmellsCsvPath = Paths.get(snapshot.getPath(), "DesigniteResults", "ArchitectureSmells.csv").toString();

        Map<String, Map<String, Integer>> systemSmells = new HashMap<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(architectureSmellsCsvPath))) {
            reader.readLine();  // skip header

            String line;
            while ((line = reader.readLine()) != null) {
                String[] columns = line.split(",");
                String packageName = columns[1];
                String architectureSmell = columns[2];

                if (!systemSmells.containsKey(packageName)) {
                    systemSmells.put(
                            packageName,
                            ARCHITECTURAL_SMELLS.stream()
                                    .collect(Collectors.toMap(Object::toString, s -> 0))
                    );
                }

                Map<String, Integer> smellCounts = systemSmells.get(packageName);
                smellCounts.replace(architectureSmell, smellCounts.get(architectureSmell) + 1);
            }
        } catch (IOException e) {
            System.err.println("Error reading ArchitectureSmells.csv: " + e.getMessage());
            System.exit(1);
        }

        return systemSmells;
    }

    private Map<String, List<TypeMetricsData>> getSystemFanInFanOutData(File snapshot) {
        String typeMetricsCsvPath = Paths.get(snapshot.getPath(), "DesigniteResults", "TypeMetrics.csv").toString();

        Map<String, List<TypeMetricsData>> systemFanInFanOutData = new HashMap<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(typeMetricsCsvPath))) {
            reader.readLine();  // skip header

            String line;
            while ((line = reader.readLine()) != null) {
                String[] columns = line.split(",");
                String packageName = columns[1];
                String className = columns[2];
                int loc = Integer.parseInt(columns[7]);
                int fanIn = Integer.parseInt(columns[12]);
                int fanOut = Integer.parseInt(columns[13]);
                TypeMetricsData typeMetricsData = new TypeMetricsData(packageName, className, loc, fanIn, fanOut);
                if (!systemFanInFanOutData.containsKey(packageName)) {
                    List<TypeMetricsData> typeMetricsDataList = new ArrayList<>();
                    typeMetricsDataList.add(typeMetricsData);
                    systemFanInFanOutData.put(packageName, typeMetricsDataList);
                } else {
                    List<TypeMetricsData> typeMetricsDataList = systemFanInFanOutData.get(packageName);
                    typeMetricsDataList.add(typeMetricsData);
                }
            }
        } catch (IOException e) {
            System.err.println("Error reading TypeMetrics.csv: " + e.getMessage());
            System.exit(1);
        }

        return systemFanInFanOutData;
    }

    private static double calculateSystemPropagationCost(Map<String, List<TypeMetricsData>> systemFanInFanOutData) {
        int totalFanIn = 0;
        int numberOfClasses = 0;

        for (List<TypeMetricsData> typeMetricsDataList : systemFanInFanOutData.values()) {
            for (TypeMetricsData typeMetricsData : typeMetricsDataList) {
                totalFanIn += typeMetricsData.fanIn();
                numberOfClasses++;
            }
        }

        return numberOfClasses != 0 ? (double) totalFanIn / (numberOfClasses * numberOfClasses) : 0.0;  // to avoid division by zero
    }

    private static double calculatePackagePropagationCost(List<TypeMetricsData> fanInFanOutData) {
        int totalFanIn = 0;
        int totalFanOut = 0;
        int numberOfClasses = 0;

        for (TypeMetricsData typeMetricsData : fanInFanOutData) {
            totalFanIn += typeMetricsData.fanIn();
            totalFanOut += typeMetricsData.fanOut();
            numberOfClasses++;
        }

        return numberOfClasses != 0 ? (double) ((totalFanIn + totalFanOut) / 2) / (numberOfClasses * numberOfClasses) : 0.0;  // to avoid division by zero
    }

    private static Map<String, List<TestCoverageData>> getSystemTestCoverageData(File snapshot) {
        Map<String, List<TestCoverageData>> systemTestCoverageData = new HashMap<>();
        File jacocoResults = new File(snapshot, "JacocoResults");
        if (jacocoResults.exists() && jacocoResults.isDirectory()) {
            for (File csvFile : Objects.requireNonNull(jacocoResults.listFiles((dir, name) -> name.toLowerCase().endsWith(".csv")))) {
                try (BufferedReader reader = new BufferedReader(new FileReader(csvFile.getAbsolutePath()))) {
                    reader.readLine();  // skip header

                    String line;
                    while ((line = reader.readLine()) != null) {
                        String[] columns = line.split(",");
                        String packageName = columns[1];
                        String className = columns[2];
                        int instructionMissed = Integer.parseInt(columns[3]);
                        int instructionCovered = Integer.parseInt(columns[4]);
                        TestCoverageData testCoverageData = new TestCoverageData(packageName, className, instructionCovered, instructionMissed);
                        if (!systemTestCoverageData.containsKey(packageName)) {
                            List<TestCoverageData> testCoverageDataList = new ArrayList<>();
                            testCoverageDataList.add(testCoverageData);
                            systemTestCoverageData.put(packageName, testCoverageDataList);
                         } else {
                            List<TestCoverageData> testCoverageDataList = systemTestCoverageData.get(packageName);
                            testCoverageDataList.add(testCoverageData);
                        }
                    }
                } catch (IOException e) {
                    System.err.println("Error reading ArchitectureSmells.csv: " + e.getMessage());
                    System.exit(1);
                }
            }
        }

        return systemTestCoverageData;
    }

    private static double getSystemMetric(File snapshot, String metric) throws IOException {
        File analysisSummary = new File(snapshot.getAbsolutePath() + "/DV8Results/dv8-analysis-result/analysis-summary.html");
        Document document = Jsoup.parse(analysisSummary, "UTF-8");
        Elements listItems = document.select("li");
        for (Element listItem: listItems) {
            String listItemText = listItem.text();
            if (listItemText.contains(metric)) {
                String match = metric + " is";
                int startIndex = listItemText.indexOf(match) + match.length();
                int relativeEndIndex = listItemText.substring(startIndex).indexOf("%");
                int endIndex = startIndex + relativeEndIndex;
                String metricText = listItemText.substring(startIndex, endIndex).trim();
                return Double.parseDouble(metricText.replace(",","."));
            }
        }
        throw new IllegalStateException("Metric not found in the HTML content");
    }

    private static void writeToCsv(File outputFile, Map<String, Map<String, Integer>> systemSmells,
            Map<String, List<TypeMetricsData>> systemFanInFanOutData,
            Map<String, List<TestCoverageData>> systemTestCoverageData,
            double decouplingLevel, double propagationCost) {
        String filePath = outputFile.getAbsolutePath();

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(filePath))) {
            List<String> header = new ArrayList<>();
            header.add("Namespace");
            List<String> uniqueSmells = getUniqueSmells(systemSmells);
            header.addAll(uniqueSmells);
            header.add("Total Smells");
            header.add("PC");
            header.add("DL");
            header.add("Code Coverage");
            header.add("LOC");
            header.add("Nr. of Classes");
            bw.write(String.join(",", header));
            bw.newLine();

            Map<String, Integer> systemSmellCounts = new HashMap<>();
            for (String uniqueSmell : uniqueSmells) {
                systemSmellCounts.put(uniqueSmell, 0);
            }
            int totalInstructionCovered = 0;
            int totalInstructionMissed = 0;
            int totalLoc = 0;

            for (Map.Entry<String, List<TypeMetricsData>> entry : systemFanInFanOutData.entrySet()) {
                List<String> rowData = new ArrayList<>();
                rowData.add(entry.getKey());  // package name
                List<String> smellData = getPackageSmells(systemSmells, uniqueSmells, systemSmellCounts, entry.getKey());
                rowData.addAll(smellData);
                int totalPackageSmells = smellData.stream().mapToInt(Integer::parseInt).sum();
                rowData.add(Integer.toString(totalPackageSmells));
                rowData.add("");
                rowData.add("");
                List<TestCoverageData> packageTestCoverageData = systemTestCoverageData.get(entry.getKey());
                if (packageTestCoverageData == null) {
                    rowData.add("");
                } else {
                    int instructionsCovered = 0;
                    int instructionsMissed = 0;
                    for (TestCoverageData testCoverageData : packageTestCoverageData) {
                        instructionsCovered += testCoverageData.instructionCovered();
                        instructionsMissed += testCoverageData.instructionMissed();
                        totalInstructionCovered += testCoverageData.instructionCovered();
                        totalInstructionMissed += testCoverageData.instructionMissed();
                    }
                    double codeCoverage = (double) (instructionsCovered * 100) / (instructionsCovered + instructionsMissed);
                    rowData.add(String.format(Locale.US, "%.2f%%", codeCoverage));
                }

                int loc = entry.getValue().stream().mapToInt(TypeMetricsData::loc).sum();
                totalLoc += loc;
                rowData.add(Integer.toString(loc));
                rowData.add(Integer.toString(entry.getValue().size()));
                bw.write(String.join(",", rowData));
                bw.newLine();
            }

            List<String> systemData = new ArrayList<>();
            systemData.add("all (includes smells in all packages)");
            List<String> systemSmellData = new ArrayList<>();

            // add smells detected for all packages here since they are not included elsewhere
            if (systemSmells.containsKey(ALL_PACKAGES_KEY)) {
                systemSmellData.addAll(getPackageSmells(
                        systemSmells, uniqueSmells, systemSmellCounts, ALL_PACKAGES_KEY));
            }

            for (int i = 0; i < uniqueSmells.size(); i++) {
                systemSmellData.set(i, String.valueOf(
                        systemSmellCounts.get(uniqueSmells.get(i))
                ));
            }
            systemData.addAll(systemSmellData);
            systemData.add(Integer.toString(systemSmellCounts.values().stream().mapToInt(Integer::intValue).sum()));
            systemData.add(String.format(Locale.US, "%.2f%%", propagationCost));
            systemData.add(String.format(Locale.US, "%.2f%%", decouplingLevel));
            double codeCoverage = (double) (totalInstructionCovered * 100) / (totalInstructionCovered + totalInstructionMissed);
            systemData.add(String.format(Locale.US, "%.2f%%", codeCoverage));
            systemData.add(Integer.toString(totalLoc));
            int numOfClasses = systemFanInFanOutData.values().stream().mapToInt(List::size).sum();
            systemData.add(Integer.toString(numOfClasses));
            bw.write(String.join(",", systemData));
            bw.newLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // also puts the smells into SystemSmellCounts
    private static List<String> getPackageSmells(
            Map<String, Map<String, Integer>> systemSmells,
            List<String> uniqueSmells,
            Map<String, Integer> systemSmellCounts,
            String entry
    ) {
        List<String> smellData = new ArrayList<>();
        Map<String, Integer> smellCounts = systemSmells.get(entry);

        for (String smell : uniqueSmells) {
            if (smellCounts == null) {
                smellData.add(Integer.toString(0));
            } else {
                int count = smellCounts.getOrDefault(smell, 0);
                systemSmellCounts.put(smell, systemSmellCounts.get(smell) + count);
                smellData.add(Integer.toString(count));
            }
        }

        return smellData;
    }

    private static List<String> getUniqueSmells(Map<String, Map<String, Integer>> systemSmells) {
        Set<String> uniqueSmells = new HashSet<>();
        for (Map<String, Integer> smellMap : systemSmells.values()) {
            uniqueSmells.addAll(smellMap.keySet());
        }
        List<String> header = new ArrayList<>(uniqueSmells);
        Collections.sort(header); // Optional: sort header alphabetically
        return header;
    }
}
