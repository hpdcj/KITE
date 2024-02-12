package pl.edu.icm.heap;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.zip.GZIPInputStream;

public class Main {
    public static final int SHINGLETON_LENGTH = Integer.parseInt(System.getProperty("shingletonLength", "18"));

    public static void main(String[] args) throws IOException {
        Instant startTime = Instant.now();

        System.err.print("Reading HPV virus file...");
        System.err.flush();

        HpvViruses hpvViruses = new HpvViruses();
        System.err.printf(" takes %.6f\n", Duration.between(startTime, Instant.now()).toNanos() / 1e9);

        for (String filename : args) {
            try (BufferedReader input = new BufferedReader(
                    new InputStreamReader(
                            new GZIPInputStream(
                                    new FileInputStream(filename), 16 * 1024 * 1024)), 32 * 1024 * 1024)) {
                Instant readingTime = Instant.now();
                System.err.println("Processing '" + filename + "' file...");

                System.err.print("\treading... ");
                System.err.flush();

                Set<String> shinglets = new HashSet<>();
                input.readLine(); // skip line
                StringBuilder sb = new StringBuilder();
                while (true) {
                    String line = input.readLine();
                    if (line != null) {
                        sb.append(line);
                    }
                    if (line == null || sb.length() >= 16 * 1024 * 1024) {
                        for (int i = 0; i <= sb.length() - SHINGLETON_LENGTH; ++i) {
                            String shinglet = sb.substring(i, i + SHINGLETON_LENGTH);
                            if (hpvViruses.hasShinglet(shinglet)) {
                                shinglets.add(shinglet);
                            }
                        }
                        if (line == null) {
                            break;
                        }
                        sb.delete(0, sb.length() - SHINGLETON_LENGTH + 1);
                    }
                    input.readLine(); // skip line
                    input.readLine(); // skip line
                    input.readLine(); // skip line
                }
                System.err.printf(" takes %.9f%n", Duration.between(readingTime, Instant.now()).toNanos() / 1e9);


                System.err.print("\tcrosscheck... ");
                System.err.flush();
                Instant crosscheckTime = Instant.now();

                StringBuilder result = new StringBuilder();
                PriorityQueue<HpvViruses.CrosscheckResult> resultsPQ = hpvViruses.crosscheck(shinglets);
                for (int i = 0; i < 3; ++i) {
                    HpvViruses.CrosscheckResult max =resultsPQ.poll();
                    if (max == null) {
                        break;
                    }
                    result.append(String.format("%-10s\t%.6f\t", max.name(), max.value()));
                }
                System.err.printf(" takes %.9f%n", Duration.between(crosscheckTime, Instant.now()).toNanos() / 1e9);
                System.out.printf("%s%s%n", result, filename);
            }
        }

        long timeElapsed = Duration.between(startTime, Instant.now()).toNanos();
        System.out.printf("Total time: %.9f%n", timeElapsed / 1e9);
    }
}
