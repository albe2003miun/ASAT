package org.miun.dataextractor;

import java.io.IOException;

/**
 * Hello world!
 *
 */
public class Main {
    public static void main( String[] args ) throws IOException {
        DataExtractor dataExtractor = new DataExtractor();
        dataExtractor.generateOutputFiles();
    }
}
