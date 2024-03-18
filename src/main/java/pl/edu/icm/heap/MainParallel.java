package pl.edu.icm.heap;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.LongAdder;
import java.util.zip.GZIPInputStream;

public class MainParallel {
    private static final int SHINGLE_LENGTH = Integer.parseInt(System.getProperty("shingleLength", "" + (18)));
    private static final int GZIP_BUFFER_KB = Integer.parseInt(System.getProperty("gzipBuffer", "" + (16 * 1024)));
    private static final int READER_BUFFER_KB = Integer.parseInt(System.getProperty("readerBuffer", "" + (32 * 1024)));
    private static final int PROCESSING_BUFFER_KB = Integer.parseInt(System.getProperty("processingBuffer", "" + (16 * 1024)));
    private static final int THREAD_POOL_SIZE = Integer.parseInt(System.getProperty("threadPoolSize", "" + Runtime.getRuntime().availableProcessors()));

    private static final LongAdder totalShinglesTime = new LongAdder();
    private static final LongAdder totalOnlyShinglesTime = new LongAdder();
    private static final LongAdder totalReadingTime = new LongAdder();
    private static final LongAdder totalCrosscheckTime = new LongAdder();

    public static void main(String[] args) throws IOException {
        System.err.println("SHINGLE_LENGTH = " + SHINGLE_LENGTH);
        System.err.println("GZIP_BUFFER_KB = " + GZIP_BUFFER_KB);
        System.err.println("READER_BUFFER_KB = " + READER_BUFFER_KB);
        System.err.println("PROCESSING_BUFFER_KB = " + PROCESSING_BUFFER_KB);
        System.err.println("THREAD_POOL_SIZE = " + THREAD_POOL_SIZE);

        Instant startTime = Instant.now();

        System.err.print("Reading HPV virus file...");
        System.err.flush();

        HpvViruses hpvViruses = new HpvViruses(HpvViruses.class.getResourceAsStream("/61HF7T14MD27_2024-02-23T090442.fa"), SHINGLE_LENGTH);

        System.err.printf(" takes %.6f\n", Duration.between(startTime, Instant.now()).toNanos() / 1e9);

        try (ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE)) {
            for (int argc = 0; argc < args.length; ++argc) {
                String filename = args[argc];
                System.err.println("Processing (" + (argc + 1) + " of " + args.length + ") '" + filename + "' file...");
                List<Future<?>> shinglesFutures = new ArrayList<>();
                Instant fileStartTime = Instant.now();
                try (BufferedReader input = new BufferedReader(
                        new InputStreamReader(
                                new GZIPInputStream(
                                        new FileInputStream(filename), GZIP_BUFFER_KB * 1024)), READER_BUFFER_KB * 1024)
                ) {
                    Instant readingIstant = Instant.now();
                    Instant shinglesInstant = null;

                    System.err.print("\treading... ");
                    System.err.flush();

                    Set<String> shingles = new HashSet<>();

                    input.readLine(); // skip line
                    StringBuilder sb = new StringBuilder((PROCESSING_BUFFER_KB + 1) * 1024);
                    while (true) {
                        String line = input.readLine();
                        if (line != null) {
                            sb.append(line);
                        }
                        if (line == null || sb.length() >= PROCESSING_BUFFER_KB * 1024) {
                            StringBuilder _sb = sb;
                            if (shinglesInstant == null) {
                                shinglesInstant = Instant.now();
                            }
                            shinglesFutures.add(executor.submit(() -> {
                                Set<String> localShingles = new HashSet<>();
                                for (int index = 0; index <= _sb.length() - SHINGLE_LENGTH; ++index) {
                                    String shingle = _sb.substring(index, index + SHINGLE_LENGTH);
                                    if (hpvViruses.hasShingle(shingle)) {
                                        localShingles.add(shingle);
                                    }
                                }
                                synchronized (shingles) {
                                    shingles.addAll(localShingles);
                                }
                            }));
                            if (line == null) {
                                break;
                            }
                            String lastChars = sb.substring(sb.length() - SHINGLE_LENGTH + 1);
                            sb = new StringBuilder((PROCESSING_BUFFER_KB + 1) * 1024);
                            sb.append(lastChars);
                        }
                        input.readLine(); // skip line
                        input.readLine(); // skip line
                        input.readLine(); // skip line
                    }
                    long readingTime = Duration.between(readingIstant, Instant.now()).toNanos();
                    System.err.printf(" takes %.9f%n", readingTime / 1e9);

                    Instant onlyShinglesInstant = Instant.now();
                    System.err.print("\tshingles... ");
                    System.err.flush();
                    for (Future<?> f : shinglesFutures) {
                        f.get();
                    }
                    Instant endShinglesInstant = Instant.now();
                    long onlyShinglesTime = Duration.between(onlyShinglesInstant, endShinglesInstant).toNanos();
                    long shinglesTime = Duration.between(shinglesInstant, endShinglesInstant).toNanos();
                    System.err.printf(" takes %.9f (%.9f)%n", onlyShinglesTime / 1e9,
                            shinglesTime / 1e9);

                    Instant crosscheckInstant = Instant.now();
                    System.err.print("\tcrosscheck... ");
                    System.err.flush();

                    StringBuilder result = new StringBuilder();
                    PriorityQueue<HpvViruses.CrosscheckResult> resultsPQ = hpvViruses.crosscheck(shingles);
                    for (int i = 0; i < 3; ++i) {
                        HpvViruses.CrosscheckResult max = resultsPQ.poll();
                        if (max == null) {
                            break;
                        }
                        result.append(String.format("%-10s\t%.6f\t", max.name(), max.value()));
                    }
                    long crosscheckTime = Duration.between(crosscheckInstant, Instant.now()).toNanos();
                    System.err.printf(" takes %.9f%n", crosscheckTime / 1e9);

                    System.out.printf("%s%s%n", result, filename);
                    totalReadingTime.add(readingTime);
                    totalShinglesTime.add(shinglesTime);
                    totalOnlyShinglesTime.add(onlyShinglesTime);
                    totalCrosscheckTime.add(crosscheckTime);
                } catch (Exception e) {
                    System.err.println(" exception: " + e);
                    e.printStackTrace(System.err);
                    shinglesFutures.forEach(f -> f.cancel(true));
                }
                System.err.printf("Finished (%d of %d) '%s' after %s%n",
                        argc + 1, args.length, filename, Duration.between(fileStartTime, Instant.now()).toNanos() / 1e9);
            }
        }

        long timeElapsed = Duration.between(startTime, Instant.now()).toNanos();
        System.out.printf("Total time: %.9f (reading: %.9f, shingles: %.9f, only shingles: %.9f, crosscheck: %.9f)%n",
                timeElapsed / 1e9,
                totalReadingTime.longValue() / 1e9, totalShinglesTime.longValue() / 1e9,
                totalOnlyShinglesTime.longValue() / 1e9, totalCrosscheckTime.longValue() / 1e9
        );
    }
}