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
    private static final int SHINGLETON_LENGTH = Integer.parseInt(System.getProperty("shingletonLength", "" + (18)));
    private static final int GZIP_BUFFER_KB = Integer.parseInt(System.getProperty("gzipBuffer", "" + (16 * 1024)));
    private static final int READER_BUFFER_KB = Integer.parseInt(System.getProperty("readerBuffer", "" + (32 * 1024)));
    private static final int PROCESSING_BUFFER_KB = Integer.parseInt(System.getProperty("processingBuffer", "" + (16 * 1024)));
    private static final int THREAD_POOL_SIZE = Integer.parseInt(System.getProperty("threadPoolSize", "" + Runtime.getRuntime().availableProcessors()));

    private static final LongAdder totalShingletsTime = new LongAdder();
    private static final LongAdder totalOnlyShingletsTime = new LongAdder();
    private static final LongAdder totalReadingTime = new LongAdder();
    private static final LongAdder totalCrosscheckTime = new LongAdder();

    public static void main(String[] args) throws IOException {
        System.err.println("SHINGLETON_LENGTH = " + SHINGLETON_LENGTH);
        System.err.println("GZIP_BUFFER_KB = " + GZIP_BUFFER_KB);
        System.err.println("READER_BUFFER_KB = " + READER_BUFFER_KB);
        System.err.println("PROCESSING_BUFFER_KB = " + PROCESSING_BUFFER_KB);
        System.err.println("THREAD_POOL_SIZE = " + THREAD_POOL_SIZE);

        Instant startTime = Instant.now();

        System.err.print("Reading HPV virus file...");
        System.err.flush();

        HpvViruses hpvViruses = new HpvViruses(HpvViruses.class.getResourceAsStream("/hpv_viruses.fasta"), SHINGLETON_LENGTH);

        System.err.printf(" takes %.6f\n", Duration.between(startTime, Instant.now()).toNanos() / 1e9);

        try (ExecutorService executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE)) {
            for (int argc = 0; argc < args.length; ++argc) {
                String filename = args[argc];
                System.err.println("Processing (" + (argc + 1) + " of " + args.length + ") '" + filename + "' file...");
                List<Future<?>> shingletsFutures = new ArrayList<>();
                try (BufferedReader input = new BufferedReader(
                        new InputStreamReader(
                                new GZIPInputStream(
                                        new FileInputStream(filename), GZIP_BUFFER_KB * 1024)), READER_BUFFER_KB * 1024)
                ) {
                    Instant readingIstant = Instant.now();
                    Instant shingletsInstant = null;

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
                            if (shingletsInstant == null) {
                                shingletsInstant = Instant.now();
                            }
                            shingletsFutures.add(executor.submit(() -> {
                                Set<String> localShinglets = new HashSet<>();
                                for (int index = 0; index <= _sb.length() - SHINGLETON_LENGTH; ++index) {
                                    String shinglet = _sb.substring(index, index + SHINGLETON_LENGTH);
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
                            String lastChars = sb.substring(sb.length() - SHINGLETON_LENGTH + 1);
                            sb = new StringBuilder(PROCESSING_BUFFER_KB * 1024);
                            sb.append(lastChars);
                        }
                        input.readLine(); // skip line
                        input.readLine(); // skip line
                        input.readLine(); // skip line
                    }
                    long readingTime = Duration.between(readingIstant, Instant.now()).toNanos();
                    System.err.printf(" takes %.9f%n", readingTime / 1e9);

                    Instant onlyShingletsInstant = Instant.now();
                    System.err.print("\tshinglets... ");
                    System.err.flush();
                    for (Future<?> f : shingletsFutures) {
                        f.get();
                    }
                    Instant endShinglesInstant = Instant.now();
                    long onlyShingletsTime = Duration.between(onlyShingletsInstant, endShinglesInstant).toNanos();
                    long shingletsTime = Duration.between(shingletsInstant, endShinglesInstant).toNanos();
                    System.err.printf(" takes %.9f (%.9f)%n", onlyShingletsTime / 1e9,
                            shingletsTime / 1e9);

                    Instant crosscheckInstant = Instant.now();
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
                    long crosscheckTime = Duration.between(crosscheckInstant, Instant.now()).toNanos();
                    System.err.printf(" takes %.9f%n", crosscheckTime / 1e9);

                    System.out.printf("%s%s%n", result, filename);
                    totalReadingTime.add(readingTime);
                    totalShingletsTime.add(shingletsTime);
                    totalOnlyShingletsTime.add(onlyShingletsTime);
                    totalCrosscheckTime.add(crosscheckTime);
                } catch (Exception e) {
                    System.err.println(" exception: " + e);
                    e.printStackTrace(System.err);
                    shingletsFutures.forEach(f -> f.cancel(true));
                }
            }
        }

        long timeElapsed = Duration.between(startTime, Instant.now()).toNanos();
        System.out.printf("Total time: %.9f (reading: %.9f, shinglets: %.9f, only shinglets: %.9f, crosscheck: %.9f)%n",
                timeElapsed / 1e9,
                totalReadingTime.longValue() / 1e9, totalShingletsTime.longValue() / 1e9,
                totalOnlyShingletsTime.longValue() / 1e9, totalCrosscheckTime.longValue() / 1e9
        );
    }
}