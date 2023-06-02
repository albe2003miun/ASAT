# ASAT (Architectural Smell Analysis Tool)

## Prerequisites

Before running the tool, ensure that you have the following software installed on your machine:
- [Java JDK 11 or later](https://adoptopenjdk.net/)
- [Maven](https://maven.apache.org/download.cgi)
- [Designite](http://www.designite-tools.com/designitejava/) (Enterprise or Academic License)
- [JaCoCo](https://www.jacoco.org/jacoco/trunk/doc/)
- [DV8](https://archdia.com/) (Standard Edition)

## Installation Instructions

1. Install Java JDK 11 or later from [here](https://adoptopenjdk.net/).
2. Install Maven from [here](https://maven.apache.org/download.cgi).
3. Install Designite from [here](http://www.designite-tools.com/designitejava/). You will need either the Enterprise or Academic License.
4. Install JaCoCo from [here](https://www.eclemma.org/jacoco/).
5. Install DV8 from [here](https://archdia.com/#shopify-section-1555640000024). You will need the Standard Edition.

## Configuration

Before running the tool, you need to set the paths in the `config.properties` file located in `src/constants/src/main/resources/config.properties`.

Here's a description of each property:

- `designiteJarPath`: The path to the Designite jar file.
- `jacocoCliPath`: The path to the JaCoCo CLI (Command Line Interface).
- `jacocoAgentPath`: The path to the JaCoCo agent jar file.
- `dv8ConsolePath`: The path to the DV8 console executable.
- `snapshotsDirectory`: The directory where the tool will store the downloaded repository snapshots.
- `resultsDirectory`: The directory where the tool will store the results.
- `platform`: The os platform which the tool is running on. Possible values are Windows (default) or Linux.
- `projectUrls`: A list of the GitHub project URLs to analyze. Each URL should be separated by a comma.
- `weekInterval`: Week interval at which project snapshots are taken. Default value is 4.
- `analyzeArchitecturalSmells`: Dictates if analyzer module should analyze architectural smells of each snapshot. Default value is true.
- `analyzeModularity`: Dictates if analyzer module should analyze modularity of each snapshot. Default value is true.
- `analyzeTestability`: Dictates if analyzer module should analyze testability of each snapshot. Default value is true.

## How to Run the Tool

To run the tool, you need to execute the main methods of the three modules sequentially.

1. Clone this repository to your local machine.
2. Navigate to the root directory of the project in your terminal.
3. Run `mvn clean install` to build the project.

Then:

1. Run the Downloader module to download all necessary snapshots:
    - Navigate to the Downloader module directory.
    - Run the main method in the Main class.

2. Run the Analyzer module to analyze all snapshots and generate CSV files:
    - Navigate to the Analyzer module directory.
    - Run the main method in the Main class.

3. Run the DataExtractor module to extract the relevant data:
    - Navigate to the DataExtractor module directory.
    - Run the main method in the Main class.


Please note that depending on the computational resources available and the number of projects to analyze, the process of analyzing the projects can be time-consuming.
It may be suitable to download and analyze the projects one by one to avoid overwhelming system resources.
