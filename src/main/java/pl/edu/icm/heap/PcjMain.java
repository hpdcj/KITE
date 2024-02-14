package pl.edu.icm.heap;

import org.pcj.ExecutionBuilder;
import org.pcj.PCJ;
import org.pcj.RegisterStorage;
import org.pcj.StartPoint;
import org.pcj.Storage;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.zip.GZIPInputStream;

@RegisterStorage
public class PcjMain implements StartPoint {
    private int SHINGLETON_LENGTH = Integer.parseInt(System.getProperty("shingletonLength", "" + (18)));
    private int GZIP_BUFFER_KB = Integer.parseInt(System.getProperty("gzipBuffer", "" + (16 * 1024)));
    private int READER_BUFFER_KB = Integer.parseInt(System.getProperty("readerBuffer", "" + (32 * 1024)));
    private int PROCESSING_BUFFER_KB = Integer.parseInt(System.getProperty("processingBuffer", "" + (16 * 1024)));
    private ExecutorService executor;
    private HpvViruses hpvViruses;
    @SuppressWarnings("serializable")
    private Queue<String> filenames;

    @Storage
    enum Vars {
        filenames
    }

    public static void main(String[] args) throws IOException {
        ExecutionBuilder builder = PCJ.executionBuilder(PcjMain.class)
                .addProperty("shingletonLength", System.getProperty("shingletonLength", "" + (18)))
                .addProperty("gzipBuffer", System.getProperty("gzipBuffer", "" + (16 * 1024)))
                .addProperty("readerBuffer", System.getProperty("readerBuffer", "" + (32 * 1024)))
                .addProperty("processingBuffer", System.getProperty("processingBuffer", "" + (16 * 1024)))
                .addProperty("threadPoolSize", System.getProperty("threadPoolSize", "" + Runtime.getRuntime().availableProcessors()))
                .addProperty("files", String.join(File.pathSeparator, args))
                .addNodes(new File(System.getProperty("nodesFile", "nodes.txt")));

        if (Boolean.parseBoolean(System.getProperty("deploy", "false"))) {
            builder.deploy();
        } else {
            builder.start();
        }
    }

    @Override
    public void main() throws Throwable {
        Instant startTime = Instant.now();
        SHINGLETON_LENGTH = Integer.parseInt(PCJ.getProperty("shingletonLength"));
        GZIP_BUFFER_KB = Integer.parseInt(PCJ.getProperty("gzipBuffer"));
        READER_BUFFER_KB = Integer.parseInt(PCJ.getProperty("readerBuffer"));
        PROCESSING_BUFFER_KB = Integer.parseInt(PCJ.getProperty("processingBuffer"));
        int THREAD_POOL_SIZE = Integer.parseInt(PCJ.getProperty("threadPoolSize"));

        if (PCJ.myId() == 0) {
            System.err.printf("[%s] shingletonLength = %d%n", getTimeAndDate(), SHINGLETON_LENGTH);
            System.err.printf("[%s] gzipBuffer = %d%n", getTimeAndDate(), GZIP_BUFFER_KB);
            System.err.printf("[%s] readerBuffer = %d%n", getTimeAndDate(), READER_BUFFER_KB);
            System.err.printf("[%s] processingBuffer = %d%n", getTimeAndDate(), PROCESSING_BUFFER_KB);
            System.err.printf("[%s] threadPoolSize = %d%n", getTimeAndDate(), THREAD_POOL_SIZE);

            filenames = new ArrayDeque<>();
            filenames.addAll(Arrays.asList(PCJ.getProperty("files", "").split(File.pathSeparator)));

            System.err.printf("[%s] Files to process (%d): %s%n", getTimeAndDate(), filenames.size(), filenames);

            System.err.printf("[%s] Reading HPV virus file...", getTimeAndDate());
            System.err.flush();
        }

        hpvViruses = new HpvViruses(SHINGLETON_LENGTH);
        if (PCJ.myId() == 0) {
            System.err.printf(" takes %.6f\n", Duration.between(startTime, Instant.now()).toNanos() / 1e9);
            System.err.printf("[%s] Loaded %d HPV viruses: %s%n", getTimeAndDate(), hpvViruses.count(), Arrays.toString(hpvViruses.getNames()));
        }

        executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);

        while (true) {
            String filename = PCJ.at(0, () -> {
                Queue<String> filenames = PCJ.localGet(Vars.filenames);
                synchronized (filenames) {
                    return filenames.poll();
                }
            });

            if (filename == null) {
                break;
            }

            processFile(filename);
        }
        executor.shutdown();

        PCJ.barrier();
        if (PCJ.myId() == 0) {
            long timeElapsed = Duration.between(startTime, Instant.now()).toNanos();
            System.err.printf("[%s] Total time: %.9f%n", getTimeAndDate(), timeElapsed / 1e9);
        }
    }


    private void processFile(String filename) {
        System.err.printf("[%s] Thread %d is processing '%s' file...%n",
                getTimeAndDate(), PCJ.myId(), filename);

        List<Future<?>> shingletsFutures = new ArrayList<>();
        try (BufferedReader input = new BufferedReader(
                new InputStreamReader(
                        new GZIPInputStream(
                                new FileInputStream(filename), GZIP_BUFFER_KB * 1024)), READER_BUFFER_KB * 1024)
        ) {
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

            for (Future<?> f : shingletsFutures) {
                f.get();
            }

            StringBuilder result = new StringBuilder();
            PriorityQueue<HpvViruses.CrosscheckResult> resultsPQ = hpvViruses.crosscheck(shinglets);
            for (int i = 0; i < 3; ++i) {
                HpvViruses.CrosscheckResult max = resultsPQ.poll();
                if (max == null) {
                    break;
                }
                result.append(String.format("%-10s\t%.6f\t", max.name(), max.value()));
            }
            PCJ.asyncAt(0, () -> System.out.printf("%s%s%n", result, filename));
        } catch (Exception e) {
            System.err.printf("[%s] Exception while processing '%s': %s%n", getTimeAndDate(), filename, e);
            e.printStackTrace(System.err);
            shingletsFutures.forEach(f -> f.cancel(false));
        }
    }

    private static String getTimeAndDate() {
        return java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss,SSS"));
    }
}