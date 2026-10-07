package pl.edu.icm.heap.kite;

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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import org.pcj.PCJ;
import org.pcj.RegisterStorage;
import org.pcj.StartPoint;
import org.pcj.Storage;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingByConcurrent;

@RegisterStorage
public class PcjMain implements StartPoint {
    private int[] SHINGLES_LENGTH;
    private int GZIP_BUFFER_KB;
    private int READER_BUFFER_KB;
    private int PROCESSING_BUFFER_KB;
    private int OUTPUT_VIRUS_COUNT;
    private OutputFormat OUTPUT_FORMAT;
    private Pattern filesGroupPattern;
    private ExecutorService executor;
    private VirusesDatabase virusesDatabase;
    @SuppressWarnings({"FieldCanBeLocal"})
    private ConcurrentLinkedQueue<String> filenames;
    @SuppressWarnings({"serializable", "FieldCanBeLocal"})
    private ConcurrentMap<String, ShinglesAndCount> shinglesMap;

    enum OutputFormat {
        PERCENTAGE,
        SHINGLES,
        BOTH
    }

    @Storage
    enum Vars {
        filenames,
        shinglesMap
    }

    @Override
    public void main() {
        Instant startTime = Instant.now();
        SHINGLES_LENGTH = Arrays.stream(PCJ.getProperty("shingleLength").split(","))
                .map(String::strip)
                .filter(Utils::isNonNegativeInteger)
                .mapToInt(Integer::parseInt)
                .sorted()
                .toArray();
        GZIP_BUFFER_KB = Integer.parseInt(PCJ.getProperty("gzipBuffer"));
        READER_BUFFER_KB = Integer.parseInt(PCJ.getProperty("readerBuffer"));
        PROCESSING_BUFFER_KB = Integer.parseInt(PCJ.getProperty("processingBuffer"));
        String outputFormat = PCJ.getProperty("outputFormat");
        if (!outputFormat.isBlank()) {
            try {
                OUTPUT_FORMAT = OutputFormat.valueOf(outputFormat.toUpperCase());
            } catch (IllegalArgumentException e) {
                OUTPUT_FORMAT = OutputFormat.PERCENTAGE;
            }
        }
        OUTPUT_VIRUS_COUNT = Integer.parseInt(PCJ.getProperty("outputVirusCount"));
        int threadPoolSize = Integer.parseInt(PCJ.getProperty("threadPoolSize"));
        String databasePaths = PCJ.getProperty("databasePaths");

        String filesGroupPatternString = PCJ.getProperty("filesGroupPattern");
        if (!filesGroupPatternString.isBlank()) {
            filesGroupPattern = Pattern.compile(filesGroupPatternString);
        }

        boolean memoryMonitor = Boolean.parseBoolean(PCJ.getProperty("memoryMonitor", "false"));
        if (memoryMonitor) {
            Utils.startMemoryMonitor();
        }

        if (PCJ.myId() == 0) {
            System.err.printf("[%s] shingleLength = %s%n", getTimeAndDate(), Utils.shinglesLengthToString(SHINGLES_LENGTH));
            System.err.printf("[%s] gzipBuffer = %d%n", getTimeAndDate(), GZIP_BUFFER_KB);
            System.err.printf("[%s] readerBuffer = %d%n", getTimeAndDate(), READER_BUFFER_KB);
            System.err.printf("[%s] processingBuffer = %d%n", getTimeAndDate(), PROCESSING_BUFFER_KB);
            System.err.printf("[%s] memoryMonitor = %s%n", getTimeAndDate(), memoryMonitor);
            System.err.printf("[%s] threadPoolSize = %d%n", getTimeAndDate(), threadPoolSize);
            System.err.printf("[%s] outputFormat = %s%n", getTimeAndDate(), OUTPUT_FORMAT);
            System.err.printf("[%s] outputVirusCount = %d%n", getTimeAndDate(), OUTPUT_VIRUS_COUNT);
            System.err.printf("[%s] databasePaths = %s%n", getTimeAndDate(), databasePaths);

            filenames = new ConcurrentLinkedQueue<>(
                    Arrays.stream(PCJ.getProperty("files", "").split(File.pathSeparator))
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
                        .collect(groupingByConcurrent(groupName -> groupName,
                                collectingAndThen(
                                        counting(),
                                        count -> new ShinglesAndCount(count.intValue())
                                )
                        ));

                System.err.printf("[%s] File groups (%d): %s%n", getTimeAndDate(), shinglesMap.size(), shinglesMap.keySet());
            }
        }
        executor = Executors.newFixedThreadPool(threadPoolSize);

        if (PCJ.myId() == 0) {
            System.err.printf("[%s] Reading virus database files by all threads%n", getTimeAndDate());
        }

        virusesDatabase = new VirusesDatabase(SHINGLES_LENGTH);
        try {
            Instant databasesStartTime = Instant.now();

            for (String databasePath : databasePaths.split(File.pathSeparator)) {
                if (databasePath.isEmpty()) {
                    continue;
                }

                Instant databaseStartTime = Instant.now();
                if (PCJ.myId() == 0) {
                    System.err.printf("[%s] Reading database file: %s...", getTimeAndDate(), databasePath);
                    System.err.flush();
                }
                try (InputStream databaseInputStream = Files.newInputStream(Path.of(databasePath))) {
                    virusesDatabase.loadFromInputStream(databaseInputStream);
                }
                if (PCJ.myId() == 0) {
                    System.err.printf(" takes %s%n", Duration.between(databaseStartTime, Instant.now()).toNanos() / 1e9);
                }
            }

            if (PCJ.myId() == 0) {
                System.err.printf("[%s] Loaded %d viruses in %.6f: %s%n", getTimeAndDate(), virusesDatabase.count(),
                        Duration.between(databasesStartTime, Instant.now()).toNanos() / 1e9,
                        Arrays.stream(virusesDatabase.getNames()).map(name -> name + "(" + virusesDatabase.getShingles(name).size() + ")").collect(Collectors.joining(", ")));
            }
        } catch (IOException e) {
            System.err.printf("[%s] Exception while reading database file by Thread-%d: %s. Exiting!%n",
                    getTimeAndDate(), PCJ.myId(), e);
            System.exit(1);
        }

        PCJ.barrier();

        while (true) {
            String filename = PCJ.at(0, () -> {
                Queue<String> filenames = PCJ.localGet(Vars.filenames);
                return filenames.poll();
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
            PCJ.asyncAt(0, () -> System.out.println(result));

            if (filesGroupPattern != null) {
                Matcher m = filesGroupPattern.matcher(filename);
                String groupName = m.find() ? m.group() : "";

                Set<String> groupShingles = PCJ.at(0, () -> {
                    Map<String, ShinglesAndCount> shinglesMap = PCJ.localGet(Vars.shinglesMap);
                    ShinglesAndCount shinglesAndCount = shinglesMap.get(groupName);
                    shinglesAndCount.addShingles(shingles);
                    if (shinglesAndCount.decrementCount() == 0) {
                        shinglesMap.remove(groupName);
                        return shinglesAndCount.getShingles();
                    } else {
                        return null;
                    }
                });
                if (groupShingles != null) {
                    String groupResult = crosscheckShingles(groupName, groupShingles);
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
//            StringBuilder sb = new StringBuilder((PROCESSING_BUFFER_KB + 1) * 1024);
            while (true) {
                String line = input.readLine();
                if (line != null) {
                    shinglesFutures.add(executor.submit(() -> {
                        Set<String> localShingles = new HashSet<>();
                        for (int index = 0; index <= line.length() - SHINGLES_LENGTH[SHINGLES_LENGTH.length - 1]; ++index) {
                            for (int shingleLength : SHINGLES_LENGTH) {
                                String shingle = line.substring(index, index + shingleLength);

                                if (virusesDatabase.hasShingle(shingle)) {
                                    localShingles.add(shingle);
                                }
                            }
                        }

                        shingles.addAll(localShingles);
                    }));
                } else {
                        break;
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
        List<VirusesDatabase.CrosscheckResult> crosscheckResults = virusesDatabase.crosscheck(shingles);

        Comparator<VirusesDatabase.CrosscheckResult> crosscheckResultComparator = switch (OUTPUT_FORMAT) {
            case PERCENTAGE -> (Comparator<VirusesDatabase.CrosscheckResult> & Serializable) (v1, v2) -> {
                int value = Double.compare(v1.percentage(), v2.percentage());
                if (value == 0) {
                    return v1.name().compareTo(v2.name());
                }
                return -value;
            };
            case SHINGLES -> (Comparator<VirusesDatabase.CrosscheckResult> & Serializable) (v1, v2) -> {
                int value = Double.compare(v1.shingles(), v2.shingles());
                if (value == 0) {
                    return v1.name().compareTo(v2.name());
                }
                return -value;
            };
            case BOTH ->
                    (Comparator<VirusesDatabase.CrosscheckResult> & Serializable) (v1, v2) -> v1.name().compareTo(v2.name());
        };

        PriorityQueue<VirusesDatabase.CrosscheckResult> resultsPQ = new PriorityQueue<>(crosscheckResultComparator);
        resultsPQ.addAll(crosscheckResults);

        StringBuilder result = new StringBuilder();
        for (int i = 0; (OUTPUT_VIRUS_COUNT <= 0 || i < OUTPUT_VIRUS_COUNT) && !resultsPQ.isEmpty(); ++i) {
            VirusesDatabase.CrosscheckResult max = resultsPQ.poll();
            if (max == null) {
                break;
            }
            String resultString = switch (OUTPUT_FORMAT) {
                case PERCENTAGE -> String.format("%s\t%.6f\t", max.name(), max.percentage());
                case SHINGLES -> String.format("%s\t%d\t", max.name(), max.shingles());
                case BOTH -> String.format("%s\t%.6f\t%d\t", max.name(), max.percentage(), max.shingles());
            };
            result.append(resultString);
        }
        return String.format("%s%s", result, filename);
    }

    private static String getTimeAndDate() {
        return java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss,SSS"));
    }

    static class ShinglesAndCount {

        private final Set<String> shingles;
        private final AtomicInteger count;

        public ShinglesAndCount(int count) {
            this.shingles = ConcurrentHashMap.newKeySet();
            this.count = new AtomicInteger(count);
        }

        public void addShingles(Collection<String> newShingles) {
            shingles.addAll(newShingles);
        }

        public Set<String> getShingles() {
            return shingles;
        }

        public int decrementCount() {
            return count.decrementAndGet();
        }
    }
}
