package pl.edu.icm.heap;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serializable;
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
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import org.pcj.ExecutionBuilder;
import org.pcj.PCJ;
import org.pcj.RegisterStorage;
import org.pcj.StartPoint;
import org.pcj.Storage;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;

@RegisterStorage
public class PcjMain implements StartPoint {
    private int SHINGLE_LENGTH;
    private int GZIP_BUFFER_KB;
    private int READER_BUFFER_KB;
    private int PROCESSING_BUFFER_KB;
    private int OUTPUT_HPV_COUNT;
    private Pattern filesGroupPattern;
    private ExecutorService executor;
    private HpvViruses hpvViruses;
    @SuppressWarnings({"serializable", "FieldCanBeLocal"})
    private Queue<String> filenames;
    @SuppressWarnings({"serializable", "FieldCanBeLocal"})
    private Map<String, ShingleSetAndCount> shinglesMap;

    @Storage
    enum Vars {
        //        hpvViruses,
        filenames,
        shinglesMap
    }

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.err.println("Give filenames (type: .fq.gz) as arguments!");
        }

        ExecutionBuilder builder = PCJ.executionBuilder(PcjMain.class)
                .addProperty("shingleLength", System.getProperty("shingleLength", "" + (18)))
                .addProperty("gzipBuffer", System.getProperty("gzipBuffer", "" + (16 * 1024)))
                .addProperty("readerBuffer", System.getProperty("readerBuffer", "" + (32 * 1024)))
                .addProperty("processingBuffer", System.getProperty("processingBuffer", "" + (16 * 1024)))
                .addProperty("threadPoolSize", System.getProperty("threadPoolSize", "" + Runtime.getRuntime().availableProcessors()))
                .addProperty("outputHpvCount", System.getProperty("outputHpvCount", "" + (3)))
                .addProperty("hpvVirusesPath", System.getProperty("hpvVirusesPath", ""))
                .addProperty("filesGroupPattern", System.getProperty("filesGroupPattern", ""))
                .addProperty("files", String.join(File.pathSeparator, args))
                .addNodes(new File(System.getProperty("nodesFile", "nodes.txt")));

        if (Boolean.parseBoolean(System.getProperty("deploy", "false"))) {
            builder.deploy();
        } else {
            builder.start();
        }
    }

    @Override
    public void main() {
        Instant startTime = Instant.now();
        SHINGLE_LENGTH = Integer.parseInt(PCJ.getProperty("shingleLength"));
        GZIP_BUFFER_KB = Integer.parseInt(PCJ.getProperty("gzipBuffer"));
        READER_BUFFER_KB = Integer.parseInt(PCJ.getProperty("readerBuffer"));
        PROCESSING_BUFFER_KB = Integer.parseInt(PCJ.getProperty("processingBuffer"));
        OUTPUT_HPV_COUNT = Integer.parseInt(PCJ.getProperty("outputHpvCount"));
        int threadPoolSize = Integer.parseInt(PCJ.getProperty("threadPoolSize"));
        String hpvVirusesPath = PCJ.getProperty("hpvVirusesPath");

        String filesGroupPatternString = PCJ.getProperty("filesGroupPattern");
        if (!filesGroupPatternString.isBlank()) {
            filesGroupPattern = Pattern.compile(filesGroupPatternString);
        }

        if (PCJ.myId() == 0) {
            System.err.printf("[%s] shingleLength = %d%n", getTimeAndDate(), SHINGLE_LENGTH);
            System.err.printf("[%s] gzipBuffer = %d%n", getTimeAndDate(), GZIP_BUFFER_KB);
            System.err.printf("[%s] readerBuffer = %d%n", getTimeAndDate(), READER_BUFFER_KB);
            System.err.printf("[%s] processingBuffer = %d%n", getTimeAndDate(), PROCESSING_BUFFER_KB);
            System.err.printf("[%s] threadPoolSize = %d%n", getTimeAndDate(), threadPoolSize);
            System.err.printf("[%s] outputHpvCount = %d%n", getTimeAndDate(), OUTPUT_HPV_COUNT);
            System.err.printf("[%s] hpvVirusesPath = %s%n", getTimeAndDate(),
                    hpvVirusesPath.isEmpty() ? "<bundled>" : hpvVirusesPath);

            filenames = new ArrayDeque<>();
            filenames.addAll(Arrays.stream(PCJ.getProperty("files", "").split(File.pathSeparator))
                    .filter(s -> !s.isBlank())
                    .toList());
            System.err.printf("[%s] Files to process (%d): %s%n", getTimeAndDate(), filenames.size(), filenames);
            System.err.printf("[%s] filesGroupPattern = %s%n", getTimeAndDate(),
                    filesGroupPattern == null ? "<none>" : filesGroupPattern.pattern());

            if (filesGroupPattern != null) {
                shinglesMap = filenames.stream()
                        .map(filename -> {
                            Matcher m = filesGroupPattern.matcher(filename);
                            return m.find() ? m.group() : "";
                        })
                        .collect(groupingBy(groupName -> groupName,
                                collectingAndThen(
                                        counting(),
                                        count -> new ShingleSetAndCount(
                                                ConcurrentHashMap.newKeySet(),
                                                new AtomicInteger(count.intValue())
                                        )
                                )
                        ));

                System.err.printf("[%s] File groups (%d): %s%n", getTimeAndDate(), shinglesMap.size(), shinglesMap.keySet());
            }

            System.err.printf("[%s] Reading HPV viruses file by all threads...", getTimeAndDate());
            System.err.flush();
        }

        try (InputStream hpvVirusesInputStream = hpvVirusesPath.isEmpty()
                ? HpvViruses.class.getResourceAsStream("/61HF7T14MD27_2024-02-23T090442.fa")
                : Files.newInputStream(Path.of(hpvVirusesPath))) {
            hpvViruses = new HpvViruses(hpvVirusesInputStream, SHINGLE_LENGTH);
        } catch (IOException e) {
            System.err.printf("[%s] Exception while reading HPV viruses file by Thread-%d: %s. Exiting!%n",
                    getTimeAndDate(), PCJ.myId(), e);
            System.exit(1);
        }

        if (PCJ.myId() == 0) {
            System.err.printf(" takes %.6f\n", Duration.between(startTime, Instant.now()).toNanos() / 1e9);
            System.err.printf("[%s] Loaded %d HPV viruses: %s%n", getTimeAndDate(), hpvViruses.count(), Arrays.toString(hpvViruses.getNames()));
        }

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
        try {
            Set<String> shingles = readShinglesFromFile(filename);

            String result = crosscheckShingles(filename, shingles);
            PCJ.asyncAt(0, () -> System.out.print(result));

            if (filesGroupPattern != null) {
                Matcher m = filesGroupPattern.matcher(filename);
                String groupName = m.find() ? m.group() : "";

                Set<String> groupShingles = PCJ.at(0, () -> {
                    Map<String, ShingleSetAndCount> shinglesMap = PCJ.localGet(Vars.shinglesMap);
                    ShingleSetAndCount shingleSetAndCount = shinglesMap.get(groupName);
                    shingleSetAndCount.shingleSet().addAll(shingles);
                    if (shingleSetAndCount.count().decrementAndGet() == 0) {
                        return shingleSetAndCount.shingleSet();
                    } else {
                        return null;
                    }
                });
                if (groupShingles != null) {
                    String groupResult = crosscheckShingles(groupName, shingles);
                    PCJ.asyncAt(0, () -> System.out.println(groupResult));
                }
            }

            Instant.now();
            System.err.printf("[%s] Thread-%d finished processing '%s' file after %.9f%n",
                    getTimeAndDate(), PCJ.myId(), filename, Duration.between(fileStartTime, Instant.now()).toNanos() / 1e9);
        } catch (Exception e) {
            System.err.printf("[%s] Exception after %.9f while processing '%s' by Thread-%d: %s%n",
                    getTimeAndDate(),
                    Duration.between(fileStartTime, Instant.now()).toNanos() / 1e9,
                    filename, PCJ.myId(), e);
            e.printStackTrace(System.err);
        }
    }


    private Set<String> readShinglesFromFile(String filename) throws IOException, ExecutionException, InterruptedException {
        List<Future<?>> shinglesFutures = new ArrayList<>();
        try (BufferedReader input = new BufferedReader(
                new InputStreamReader(
                        new GZIPInputStream(
                                new FileInputStream(filename), GZIP_BUFFER_KB * 1024)), READER_BUFFER_KB * 1024)
        ) {
            Set<String> shingles = ConcurrentHashMap.newKeySet();

            input.readLine(); // skip line
            StringBuilder sb = new StringBuilder(PROCESSING_BUFFER_KB * 1024);
            while (true) {
                String line = input.readLine();
                if (line != null) {
                    sb.append(line);
                }
                if (line == null || sb.length() >= PROCESSING_BUFFER_KB * 1024) {
                    StringBuilder _sb = sb;
                    shinglesFutures.add(executor.submit(() -> {
                        Set<String> localShingles = new HashSet<>();
                        for (int index = 0; index <= _sb.length() - SHINGLE_LENGTH; ++index) {
                            String shingle = _sb.substring(index, index + SHINGLE_LENGTH);
                            if (hpvViruses.hasShingle(shingle)) {
                                localShingles.add(shingle);
                            }
                        }

                        shingles.addAll(localShingles);
                    }));
                    if (line == null) {
                        break;
                    }
                    String lastChars = sb.substring(sb.length() - SHINGLE_LENGTH + 1);
                    sb = new StringBuilder(PROCESSING_BUFFER_KB * 1024);
                    sb.append(lastChars);
                }
                input.readLine(); // skip line
                input.readLine(); // skip line
                input.readLine(); // skip line
            }

            for (Future<?> f : shinglesFutures) {
                f.get();
            }

            return shingles;
        } catch (Exception e) {
            shinglesFutures.forEach(f -> f.cancel(false));
            throw e;
        }
    }

    private String crosscheckShingles(String filename, Set<String> shingles) {
        StringBuilder result = new StringBuilder();
        PriorityQueue<HpvViruses.CrosscheckResult> resultsPQ = hpvViruses.crosscheck(shingles);
        for (int i = 0; (OUTPUT_HPV_COUNT <= 0 || i < OUTPUT_HPV_COUNT) && !resultsPQ.isEmpty(); ++i) {
            HpvViruses.CrosscheckResult max = resultsPQ.poll();
            if (max == null) {
                break;
            }
            result.append(String.format("%-10s\t%.6f\t", max.name(), max.value()));
        }
        return String.format("%s%s%n", result, filename);
    }

    private static String getTimeAndDate() {
        return java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss,SSS"));
    }

    record ShingleSetAndCount(Set<String> shingleSet, AtomicInteger count) implements Serializable {
    }
}