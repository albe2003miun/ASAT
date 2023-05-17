package org.miun.analyzer.support;

import java.io.*;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CommandRunner {

    public static void runStandardCommand(String command, File workingDir) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder();
            processBuilder.command(command.split(" "));
            processBuilder.directory(workingDir);
            processBuilder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT);
            Process process = processBuilder.start();

            int exitCode = process.waitFor();
            System.out.println("Exited with code: " + exitCode);
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static void runTestCommand(String testCommand, File workingDir, File testAnalysisFile) {
            try {
                ProcessBuilder processBuilder = new ProcessBuilder();
                processBuilder.command(testCommand.split(" "));
                processBuilder.directory(workingDir);
                Process process = processBuilder.start();

                // Create a new thread to consume the standard output stream
                Thread outputThread = new Thread(() -> {
                    try {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                        BufferedWriter writer = new BufferedWriter(new FileWriter(testAnalysisFile));
                        writer.write("SuccessfulTests,FailedTests,ErrorTests,SkippedTests,TotalTests,PercentageOfSuccessfulTests\n");

                        String line;
                        Pattern pattern = Pattern.compile("Tests run: (\\d+), Failures: (\\d+), Errors: (\\d+), Skipped: (\\d+)");
                        int totalTestsRun = 0;
                        int totalTestsFailures = 0;
                        int totalTestsErrors = 0;
                        int totalTestsSkipped = 0;
                        while ((line = reader.readLine()) != null) {
                            System.out.println(line);

                            Matcher matcher = pattern.matcher(line);
                            if (matcher.find()) {
                                totalTestsRun += Integer.parseInt(matcher.group(1));
                                totalTestsFailures += Integer.parseInt(matcher.group(2));
                                totalTestsErrors += Integer.parseInt(matcher.group(3));
                                totalTestsSkipped += Integer.parseInt(matcher.group(4));
                            }
                        }

                        totalTestsRun = totalTestsRun / 2;
                        totalTestsFailures = totalTestsFailures / 2;
                        totalTestsErrors = totalTestsErrors / 2;
                        totalTestsSkipped = totalTestsSkipped / 2;

                        int totalSuccessfulTests = totalTestsRun - totalTestsFailures - totalTestsErrors - totalTestsSkipped;
                        double successPercentage = totalTestsRun != 0 ? ((double) totalSuccessfulTests / totalTestsRun) * 100 : -1.0;
                        writer.write(String.format(Locale.US, "%d,%d,%d,%d,%d,%.2f", totalSuccessfulTests, totalTestsFailures, totalTestsErrors, totalTestsSkipped, totalTestsRun, successPercentage));
                        writer.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });

                // Create a new thread to consume the error stream
                Thread errorThread = new Thread(() -> {
                    try {
                        BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                        String line;
                        while ((line = errorReader.readLine()) != null) {
                            System.err.println(line);
                        }
                        errorReader.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });

                // Start the output and error threads
                outputThread.start();
                errorThread.start();

                // Wait for the threads to finish
                outputThread.join();
                errorThread.join();

                int exitCode = process.waitFor();
                System.out.println("Exited with code: " + exitCode);
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
