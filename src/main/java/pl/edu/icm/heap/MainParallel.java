package pl.edu.icm.heap;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.GZIPInputStream;

public class MainParallel {
    private static final int SHINGLETON_LENGTH = Integer.parseInt(System.getProperty("shingletonLength", "" + (18)));
    private static final int GZIP_BUFFER_KB = Integer.parseInt(System.getProperty("gzipBuffer", "" + (16 * 1024)));
    private static final int READER_BUFFER_KB = Integer.parseInt(System.getProperty("readerBuffer", "" + (32 * 1024)));
    private static final int PROCESSING_BUFFER_KB = Integer.parseInt(System.getProperty("processingBuffer", "" + (16 * 1024)));
    private static final int THREAD_POOL_SIZE = Integer.parseInt(System.getProperty("threadPoolSize", "" + Runtime.getRuntime().availableProcessors()));

    public static void main(String[] args) throws IOException {
        System.err.println("SHINGLETON_LENGTH = " + SHINGLETON_LENGTH);
        System.err.println("GZIP_BUFFER_KB = " + GZIP_BUFFER_KB);
        System.err.println("READER_BUFFER_KB = " + READER_BUFFER_KB);
        System.err.println("PROCESSING_BUFFER_KB = " + PROCESSING_BUFFER_KB);
        System.err.println("THREAD_POOL_SIZE = " + THREAD_POOL_SIZE);

        Instant startTime = Instant.now();

        System.err.print("Reading HPV virus file...");
        System.err.flush();

        HpvViruses hpvViruses = new HpvViruses(SHINGLETON_LENGTH);

        System.err.printf(" takes %.6f\n", Duration.between(startTime, Instant.now()).toNanos() / 1e9);

        try (ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE)) {
            for (String filename : args) {
                List<Future<?>> shingletsFutures = new ArrayList<>();
                System.err.println("Processing '" + filename + "' file...");
                try (BufferedReader input = new BufferedReader(
                        new InputStreamReader(
                                new GZIPInputStream(
                                        new FileInputStream(filename), GZIP_BUFFER_KB * 1024)), READER_BUFFER_KB * 1024)
                ) {
                    Instant readingTime = Instant.now();
                    System.err.print("\treading... ");
                    System.err.flush();

                    Set<String> shinglets = new HashSet<>();

                    input.readLine(); // skip line
                    StringBuilder sb = new StringBuilder(PROCESSING_BUFFER_KB * 1024);
                    while (true) {
                        String line = input.readLine();
                        if (line != null) {
                            sb.append(line);
                        }
                        if (line == null || sb.length() >= PROCESSING_BUFFER_KB * 1024) {
                            StringBuilder _sb = sb;
                            shingletsFutures.add(executor.submit(() -> {
                                Set<String> localShinglets = new HashSet<>();
                                for (int i = 0; i <= _sb.length() - SHINGLETON_LENGTH; ++i) {
                                    String shinglet = _sb.substring(i, i + SHINGLETON_LENGTH);
                                    if (hpvViruses.hasShinglet(shinglet)) {
                                        localShinglets.add(shinglet);
                                    }
                                }
                                synchronized (shinglets) {
                                    shinglets.addAll(localShinglets);
                                }
                            }));
                            if (line == null) {
                                break;
                            }
                            sb = new StringBuilder(PROCESSING_BUFFER_KB * 1024);//.delete(0, sb.length() - SHINGLETON_LENGTH + 1);
                        }
                        input.readLine(); // skip line
                        input.readLine(); // skip line
                        input.readLine(); // skip line
                    }
                    System.err.printf(" takes %.9f%n", Duration.between(readingTime, Instant.now()).toNanos() / 1e9);

                    Instant shingletsTime = Instant.now();
                    System.err.print("\tshinglets... ");
                    System.err.flush();
                    for (Future<?> f : shingletsFutures) {
                        f.get();
                    }
                    System.err.printf(" takes %.9f%n", Duration.between(shingletsTime, Instant.now()).toNanos() / 1e9);

                    Instant crosscheckTime = Instant.now();
                    System.err.print("\tcrosscheck... ");
                    System.err.flush();

                    StringBuilder result = new StringBuilder();
                    PriorityQueue<HpvViruses.CrosscheckResult> resultsPQ = hpvViruses.crosscheck(shinglets);
                    for (int i = 0; i < 3; ++i) {
                        HpvViruses.CrosscheckResult max = resultsPQ.poll();
                        if (max == null) {
                            break;
                        }
                        result.append(String.format("%-10s\t%.6f\t", max.name(), max.value()));
                    }
                    System.err.printf(" takes %.9f%n", Duration.between(crosscheckTime, Instant.now()).toNanos() / 1e9);

                    System.out.printf("%s%s%n", result, filename);
                } catch (Exception e) {
                    System.err.println(" exception: " + e);
                    e.printStackTrace(System.err);
                    shingletsFutures.forEach(f -> f.cancel(false));
                }
            }
        }

        long timeElapsed = Duration.between(startTime, Instant.now()).toNanos();
        System.out.printf("Total time: %.9f%n", timeElapsed / 1e9);
    }
}