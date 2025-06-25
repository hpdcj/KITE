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
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
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
    private int[] SHINGLES_LENGTH;
    private int GZIP_BUFFER_KB;
    private int READER_BUFFER_KB;
    private int PROCESSING_BUFFER_KB;
    private int OUTPUT_VIRUS_COUNT;
    private Pattern filesGroupPattern;
    private ExecutorService executor;
    private VirusesDatabase virusesDatabase;
    @SuppressWarnings({"FieldCanBeLocal"})
    private ConcurrentLinkedQueue<String> filenames;
    @SuppressWarnings({"serializable", "FieldCanBeLocal"})
    private Map<String, ShinglesAndCount> shinglesMap;

    @Storage
    enum Vars {
        filenames,
        shinglesMap
    }

    public static void main(String[] args) throws IOException {
        if (args.length > 0 &&
                ("-v".equals(args[0]) || "-version".equals(args[0]) || "--version".equals(args[0])
                        || "-info".equals(args[0]) || "--info".equals(args[0])
                        || "-h".equals(args[0]) || "-help".equals(args[0]) || "--help".equals(args[0]))) {
            System.err.println("HPV-KITE version 1.0.4");

            if ("-info".equals(args[0]) || "--info".equals(args[0])
                    || "-h".equals(args[0]) || "-help".equals(args[0]) || "--help".equals(args[0])) {
                System.err.println("""
                        
                        If you use this software, please cite it using this reference:
                          Marek Nowicki, Magdalena Mroczek, Dhananjay Mukhedkar, Piotr Bala, Ville Nikolai Pimenoff, Laila Sara Arroyo Muhr,
                          HPV-KITE: sequence analysis software for rapid HPV genotype detection,
                          Briefings in Bioinformatics, Volume 26, Issue 2, March 2025, bbaf155,
                          https://doi.org/10.1093/bib/bbaf155
                        
                        For instructions see: https://github.com/hpdcj/HPV-KITE
                        """);
            }
            args = Arrays.copyOfRange(args, 1, args.length);
        }
        if (args.length == 0) {
            System.err.println("Give filenames (type: .fq.gz) as arguments!");
        }

        ExecutionBuilder builder = PCJ.executionBuilder(PcjMain.class)
                .addProperty("shingleLength", System.getProperty("shingleLength", "" + (31)))
                .addProperty("gzipBuffer", System.getProperty("gzipBuffer", "" + (512)))
                .addProperty("readerBuffer", System.getProperty("readerBuffer", "" + (512)))
                .addProperty("processingBuffer", System.getProperty("processingBuffer", "" + (64)))
                .addProperty("threadPoolSize", System.getProperty("threadPoolSize", "" + Runtime.getRuntime().availableProcessors()))
                .addProperty("outputVirusCount", System.getProperty("outputVirusCount",
                        System.getProperty("outputHpvCount", "" + (0))))
                .addProperty("databasePaths", System.getProperty("databasePaths",
                        System.getProperty("databasePath", System.getProperty("hpvVirusesPath", ""))))
                .addProperty("filesGroupPattern", System.getProperty("filesGroupPattern", ""))
                .addProperty("files", String.join(File.pathSeparator, args));

        File nodesFile = new File(System.getProperty("nodesFile", "nodes.txt"));
        if (nodesFile.isFile()) {
            builder.addNodes(nodesFile);
        }

        if (Boolean.parseBoolean(System.getProperty("deploy", "false"))) {
            builder.deploy();
        } else {
            builder.start();
        }
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
        OUTPUT_VIRUS_COUNT = Integer.parseInt(PCJ.getProperty("outputVirusCount"));
        int threadPoolSize = Integer.parseInt(PCJ.getProperty("threadPoolSize"));
        String databasePaths = PCJ.getProperty("databasePaths");

        String filesGroupPatternString = PCJ.getProperty("filesGroupPattern");
        if (!filesGroupPatternString.isBlank()) {
            filesGroupPattern = Pattern.compile(filesGroupPatternString);
        }

        if (PCJ.myId() == 0) {
            System.err.printf("[%s] shingleLength = %s%n", getTimeAndDate(), Utils.shinglesLengthToString(SHINGLES_LENGTH));
            System.err.printf("[%s] gzipBuffer = %d%n", getTimeAndDate(), GZIP_BUFFER_KB);
            System.err.printf("[%s] readerBuffer = %d%n", getTimeAndDate(), READER_BUFFER_KB);
            System.err.printf("[%s] processingBuffer = %d%n", getTimeAndDate(), PROCESSING_BUFFER_KB);
            System.err.printf("[%s] threadPoolSize = %d%n", getTimeAndDate(), threadPoolSize);
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
                        .collect(groupingBy(groupName -> groupName,
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
            StringBuilder sb = new StringBuilder((PROCESSING_BUFFER_KB + 1) * 1024);
            while (true) {
                String line = input.readLine();
                if (line != null) {
                    sb.append(line);
                }
                if (line == null || sb.length() >= PROCESSING_BUFFER_KB * 1024) {
                    StringBuilder _sb = sb;
                    shinglesFutures.add(executor.submit(() -> {
                        Set<String> localShingles = new HashSet<>();
                        for (int index = 0; index <= _sb.length() - SHINGLES_LENGTH[SHINGLES_LENGTH.length - 1]; ++index) {
                            for (int shingleLength : SHINGLES_LENGTH) {
                                String shingle = _sb.substring(index, index + shingleLength);

                                if (virusesDatabase.hasShingle(shingle)) {
                                    localShingles.add(shingle);
                                }
                            }
                        }

                        shingles.addAll(localShingles);
                    }));
                    if (line == null) {
                        break;
                    }
                    String lastChars = sb.substring(sb.length() - SHINGLES_LENGTH[SHINGLES_LENGTH.length - 1] + 1);
                    sb = new StringBuilder((PROCESSING_BUFFER_KB + 1) * 1024);
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
        PriorityQueue<VirusesDatabase.CrosscheckResult> resultsPQ = virusesDatabase.crosscheck(shingles);
        for (int i = 0; (OUTPUT_VIRUS_COUNT <= 0 || i < OUTPUT_VIRUS_COUNT) && !resultsPQ.isEmpty(); ++i) {
            VirusesDatabase.CrosscheckResult max = resultsPQ.poll();
            if (max == null) {
                break;
            }
            result.append(String.format("%-10s\t%.6f\t", max.name(), max.value()));
        }
        return String.format("%s%s", result, filename);
    }

    private static String getTimeAndDate() {
        return java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss,SSS"));
    }

    static class ShinglesAndCount implements Serializable {
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
