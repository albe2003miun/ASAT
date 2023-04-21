package org.miun.dataextractor;

import java.io.*;
import java.nio.file.Paths;
import java.util.*;

import static org.miun.constants.Constants.*;

public class DataExtractor {

    public void generateOutputFiles() {
        File snapshotResults = new File(RESULTS_DIRECTORY);

        for (File project : Objects.requireNonNull(snapshotResults.listFiles(File::isDirectory))) {
            for (File snapshot : Objects.requireNonNull(project.listFiles(File::isDirectory))) {
                Map<String, Map<String, Integer>> systemSmells = getSystemSmells(snapshot);
                Map<String, List<TypeMetricsData>> systemFanInFanOutData = getSystemFanInFanOutData(snapshot);
                Map<String, List<TestCoverageData>> systemTestCoverageData = getSystemTestCoverageData(snapshot);
                writeToCsv(new File(snapshot, "output.csv"), systemSmells, systemFanInFanOutData, systemTestCoverageData);
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
                    systemSmells.put(packageName, new HashMap<>(Map.of(architectureSmell, 1)));
                } else {
                    Map<String, Integer> smellCounts = systemSmells.get(packageName);
                    if (!smellCounts.containsKey(architectureSmell)) {
                        smellCounts.put(architectureSmell, 1);
                    } else {
                        smellCounts.replace(architectureSmell, smellCounts.get(architectureSmell) + 1);
                    }
                }
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

        return numberOfClasses != 0 ? (double) (totalFanIn + totalFanOut) / 2 / (numberOfClasses * numberOfClasses) : 0.0;  // to avoid division by zero
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

    private static void writeToCsv(File outputFile, Map<String, Map<String, Integer>> systemSmells, Map<String,
            List<TypeMetricsData>> systemFanInFanOutData, Map<String, List<TestCoverageData>> systemTestCoverageData) {
        String filePath = outputFile.getAbsolutePath();

        try (BufferedWriter bw = new BufferedWriter(new FileWriter(filePath))) {
            List<String> header = new ArrayList<>();
            header.add("namespace");
            List<String> uniqueSmells = getUniqueSmells(systemSmells);
            header.addAll(uniqueSmells);
            header.add("total smells");
            header.add("pc");
            header.add("code coverage");
            header.add("loc");
            header.add("nr. of classes");
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
                List<String> smellData = new ArrayList<>();
                Map<String, Integer> smellCounts = systemSmells.get(entry.getKey());
                for (String smell : uniqueSmells) {
                    if (smellCounts == null) {
                        smellData.add(Integer.toString(0));
                    } else {
                        int count = smellCounts.getOrDefault(smell, 0);
                        systemSmellCounts.put(smell, systemSmellCounts.get(smell) + count);
                        smellData.add(Integer.toString(count));
                    }
                }
                rowData.addAll(smellData);
                int totalPackageSmells = smellData.stream().mapToInt(Integer::parseInt).sum();
                rowData.add(Integer.toString(totalPackageSmells));
                rowData.add(String.format("%.1f%%", calculatePackagePropagationCost(entry.getValue())));
                List<TestCoverageData> packageTestCoverageData = systemTestCoverageData.get(entry.getKey());
                if (packageTestCoverageData == null) {
                    rowData.add("null");
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
                    rowData.add(String.format("%.1f%%", codeCoverage));
                }

                int loc = entry.getValue().stream().mapToInt(TypeMetricsData::loc).sum();
                totalLoc += loc;
                rowData.add(Integer.toString(loc));
                rowData.add(Integer.toString(entry.getValue().size()));
                bw.write(String.join(",", rowData));
                bw.newLine();
            }

            List<String> systemData = new ArrayList<>();
            systemData.add("all");
            List<String> systemSmellData = new ArrayList<>();
            for (String uniqueSmell : uniqueSmells) {
                systemSmellData.add(Integer.toString(systemSmellCounts.get(uniqueSmell)));
            }
            systemData.addAll(systemSmellData);
            systemData.add(Integer.toString(systemSmellCounts.values().stream().mapToInt(Integer::intValue).sum()));
            systemData.add(String.format("%.1f%%", calculateSystemPropagationCost(systemFanInFanOutData) * 100));
            double codeCoverage = (double) (totalInstructionCovered * 100) / (totalInstructionCovered + totalInstructionMissed);
            systemData.add(String.format("%.1f%%", codeCoverage));
            systemData.add(Integer.toString(totalLoc));
            int numOfClasses = systemFanInFanOutData.values().stream().mapToInt(List::size).sum();
            systemData.add(Integer.toString(numOfClasses));
            bw.write(String.join(",", systemData));
            bw.newLine();
        } catch (IOException e) {
            e.printStackTrace();
        }
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
