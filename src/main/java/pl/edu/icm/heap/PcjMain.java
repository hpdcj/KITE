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
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

@RegisterStorage
public class PcjMain implements StartPoint {
    private int SHINGLE_LENGTH;
    private int GZIP_BUFFER_KB;
    private int READER_BUFFER_KB;
    private int PROCESSING_BUFFER_KB;
    private int OUTPUT_HPV_COUNT;
    private ExecutorService executor;
    private HpvViruses hpvViruses;
    @SuppressWarnings("serializable")
    private Map<String, List<String>> filenameMap;

    @Storage
    enum Vars {
        //        hpvViruses,
        filenameMap
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
        String filesGroupPattern = PCJ.getProperty("filesGroupPattern");

        if (PCJ.myId() == 0) {
            System.err.printf("[%s] shingleLength = %d%n", getTimeAndDate(), SHINGLE_LENGTH);
            System.err.printf("[%s] gzipBuffer = %d%n", getTimeAndDate(), GZIP_BUFFER_KB);
            System.err.printf("[%s] readerBuffer = %d%n", getTimeAndDate(), READER_BUFFER_KB);
            System.err.printf("[%s] processingBuffer = %d%n", getTimeAndDate(), PROCESSING_BUFFER_KB);
            System.err.printf("[%s] threadPoolSize = %d%n", getTimeAndDate(), threadPoolSize);
            System.err.printf("[%s] outputHpvCount = %d%n", getTimeAndDate(), OUTPUT_HPV_COUNT);
            System.err.printf("[%s] hpvVirusesPath = %s%n", getTimeAndDate(), hpvVirusesPath.isEmpty() ? "<provided>" : hpvVirusesPath);
            System.err.printf("[%s] filesGroupPattern = %s%n", getTimeAndDate(), filesGroupPattern);

            String[] files = PCJ.getProperty("files", "").split(File.pathSeparator);
            System.err.printf("[%s] Files to process (%d): %s%n", getTimeAndDate(), files.length, Arrays.toString(files));

            if (!filesGroupPattern.isEmpty()) {

                Pattern filenamesPattern = Pattern.compile(filesGroupPattern);
                filenameMap = Arrays.stream(files)
                        .filter(s -> !s.isEmpty())
                        .collect(groupingBy(filename -> {
                            Matcher m = filenamesPattern.matcher(filename);
                            return m.find() ? m.group() : "";
                        }, LinkedHashMap::new, toList()));
                System.err.printf("[%s] File groups to process (%d):%n", getTimeAndDate(), filenameMap.size());
                for (Map.Entry<String, List<String>> entry : filenameMap.entrySet()) {
                    System.err.printf("[%s]\t- %s (%d) = %s%n", getTimeAndDate(),
                            entry.getKey().isEmpty() ? "<none>" : entry.getKey(),
                            entry.getValue().size(),
                            entry.getValue());
                }
            } else {
                filenameMap = Arrays.stream(files).filter(s -> !s.isEmpty())
                        .collect(groupingBy(filename -> filename, LinkedHashMap::new, toList()));
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
            GroupAndFilenames filenamesEntry = PCJ.at(0, () -> {
                Map<String, List<String>> filenameMap = PCJ.localGet(Vars.filenameMap);
                synchronized (filenameMap) {
                    Iterator<Map.Entry<String, List<String>>> iterator = filenameMap.entrySet().iterator();
                    if (iterator.hasNext()) {
                        Map.Entry<String, List<String>> entry = iterator.next();
                        iterator.remove();
                        return new GroupAndFilenames(entry.getKey(), entry.getValue());
                    } else {
                        return null;
                    }
                }
            });

            if (filenamesEntry == null) {
                break;
            }

            Set<String> shingles = new HashSet<>();
            for (String filename : filenamesEntry.filenames()) {
                Set<String> fileShingle = processFile(filename);
                if (!filesGroupPattern.isEmpty()) {
                    shingles.addAll(fileShingle);
                }
            }
            if (!filesGroupPattern.isEmpty()) {
                String result = crosscheckShingles("Group:" + filenamesEntry.name(), shingles);
                PCJ.asyncAt(0, () -> System.out.print(result));
            }
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


    private Set<String> processFile(String filename) {
        Set<String> shingles = new HashSet<>();

        Instant fileStartTime = Instant.now();
        System.err.printf("[%s] Thread-%d is processing '%s' file...%n",
                getTimeAndDate(), PCJ.myId(), filename);

        List<Future<?>> shinglesFutures = new ArrayList<>();
        try (BufferedReader input = new BufferedReader(
                new InputStreamReader(
                        new GZIPInputStream(
                                new FileInputStream(filename), GZIP_BUFFER_KB * 1024)), READER_BUFFER_KB * 1024)
        ) {
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
                        synchronized (shingles) {
                            shingles.addAll(localShingles);
                        }
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

            String result = crosscheckShingles(filename, shingles);
            PCJ.asyncAt(0, () -> System.out.print(result));
            Instant.now();
            System.err.printf("[%s] Thread-%d finished processing '%s' file after %.9f%n",
                    getTimeAndDate(), PCJ.myId(), filename, Duration.between(fileStartTime, Instant.now()).toNanos() / 1e9);
        } catch (Exception e) {
            System.err.printf("[%s] Exception after %.9f while processing '%s' by Thread-%d: %s%n",
                    getTimeAndDate(),
                    Duration.between(fileStartTime, Instant.now()).toNanos() / 1e9,
                    filename, PCJ.myId(), e);
            e.printStackTrace(System.err);
            shinglesFutures.forEach(f -> f.cancel(false));
        }
        return shingles;
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

    record GroupAndFilenames(String name, List<String> filenames) implements Serializable {
    }
}