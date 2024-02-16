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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
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
        hpvViruses,
        filenames
    }

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.err.println("Give filenames as arguments!");
        }

        ExecutionBuilder builder = PCJ.executionBuilder(PcjMain.class)
                .addProperty("shingletonLength", System.getProperty("shingletonLength", "" + (18)))
                .addProperty("gzipBuffer", System.getProperty("gzipBuffer", "" + (16 * 1024)))
                .addProperty("readerBuffer", System.getProperty("readerBuffer", "" + (32 * 1024)))
                .addProperty("processingBuffer", System.getProperty("processingBuffer", "" + (16 * 1024)))
                .addProperty("threadPoolSize", System.getProperty("threadPoolSize", "" + Runtime.getRuntime().availableProcessors()))
                .addProperty("hpvVirusesPath", System.getProperty("hpvVirusesPath", ""))
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
        int threadPoolSize = Integer.parseInt(PCJ.getProperty("threadPoolSize"));
        String hpvVirusesPath = PCJ.getProperty("hpvVirusesPath");

        if (PCJ.myId() == 0) {
            System.err.printf("[%s] shingletonLength = %d%n", getTimeAndDate(), SHINGLETON_LENGTH);
            System.err.printf("[%s] gzipBuffer = %d%n", getTimeAndDate(), GZIP_BUFFER_KB);
            System.err.printf("[%s] readerBuffer = %d%n", getTimeAndDate(), READER_BUFFER_KB);
            System.err.printf("[%s] processingBuffer = %d%n", getTimeAndDate(), PROCESSING_BUFFER_KB);
            System.err.printf("[%s] threadPoolSize = %d%n", getTimeAndDate(), threadPoolSize);
            System.err.printf("[%s] hpvVirusesPath = %s%n", getTimeAndDate(), hpvVirusesPath.isEmpty() ? "<provided>" : hpvVirusesPath);

            System.err.printf("[%s] Reading HPV viruses file...", getTimeAndDate());
            System.err.flush();

            try (InputStream hpvVirusesInputStream = hpvVirusesPath.isEmpty()
                    ? HpvViruses.class.getResourceAsStream("/hpv_viruses.fasta")
                    : Files.newInputStream(Path.of(hpvVirusesPath))) {
                hpvViruses = new HpvViruses(hpvVirusesInputStream, SHINGLETON_LENGTH);
            } catch (IOException e) {
                System.err.printf("[%s] Exception while reading HPV viruses file by Thread-%d: %s. Exiting!%n",
                        getTimeAndDate(), PCJ.myId(), e);
                e.printStackTrace(System.err);
                System.exit(1);
            }
            PCJ.asyncBroadcast(hpvViruses, Vars.hpvViruses);

            if (PCJ.myId() == 0) {
                System.err.printf(" takes %.6f\n", Duration.between(startTime, Instant.now()).toNanos() / 1e9);
                System.err.printf("[%s] Loaded %d HPV viruses: %s%n", getTimeAndDate(), hpvViruses.count(), Arrays.toString(hpvViruses.getNames()));
            }

            filenames = new ArrayDeque<>();
            filenames.addAll(Arrays.stream(PCJ.getProperty("files", "").split(File.pathSeparator))
                    .filter(s -> !s.isEmpty())
                    .toList());
            System.err.printf("[%s] Files to process (%d): %s%n", getTimeAndDate(), filenames.size(), filenames);
        }
        PCJ.waitFor(Vars.hpvViruses);
        PCJ.barrier();

        executor = Executors.newFixedThreadPool(threadPoolSize);

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
        System.err.printf("[%s] Thread-%d finished processing all its files after %.9f%n",
                getTimeAndDate(), PCJ.myId(), Duration.between(startTime, Instant.now()).toNanos() / 1e9);

        PCJ.barrier();
        if (PCJ.myId() == 0) {
            long timeElapsed = Duration.between(startTime, Instant.now()).toNanos();
            System.err.printf("[%s] Total time: %.9f%n", getTimeAndDate(), timeElapsed / 1e9);
        }
    }


    private void processFile(String filename) {
        Instant fileStartTime = Instant.now();
        System.err.printf("[%s] Thread-%d is processing '%s' file...%n",
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
            Instant.now();
            System.err.printf("[%s] Thread-%d finished processing '%s' file after %.9f%n",
                    getTimeAndDate(), PCJ.myId(), filename, Duration.between(fileStartTime, Instant.now()).toNanos() / 1e9);
        } catch (Exception e) {
            System.err.printf("[%s] Exception after %.9f while processing '%s' by Thread-%d: %s%n",
                    getTimeAndDate(),
                    Duration.between(fileStartTime, Instant.now()).toNanos() / 1e9,
                    filename, PCJ.myId(), e);
            e.printStackTrace(System.err);
            shingletsFutures.forEach(f -> f.cancel(false));
        }
    }

    private static String getTimeAndDate() {
        return java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss,SSS"));
    }
}