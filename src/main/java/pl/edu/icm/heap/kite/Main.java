package pl.edu.icm.heap.kite;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import org.pcj.ExecutionBuilder;
import org.pcj.PCJ;

public class Main {
    public static void main(String[] args) throws IOException {
        if (args.length > 0 &&
                ("-v".equals(args[0]) || "-version".equals(args[0]) || "--version".equals(args[0])
                        || "-info".equals(args[0]) || "--info".equals(args[0])
                        || "-h".equals(args[0]) || "-help".equals(args[0]) || "--help".equals(args[0]))) {
            System.err.println("KITE version 1.3.0");

            if ("-info".equals(args[0]) || "--info".equals(args[0])
                    || "-h".equals(args[0]) || "-help".equals(args[0]) || "--help".equals(args[0])) {
                System.err.println("""
                        
                        If you use this software, please cite it using this reference:
                          Marek Nowicki, Magdalena Mroczek, Dhananjay Mukhedkar, Piotr Bala, Ville Nikolai Pimenoff, Laila Sara Arroyo Muhr,
                          HPV-KITE: sequence analysis software for rapid HPV genotype detection,
                          Briefings in Bioinformatics, Volume 26, Issue 2, March 2025, bbaf155,
                          https://doi.org/10.1093/bib/bbaf155
                        
                        For instructions see: https://github.com/hpdcj/KITE
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
                .addProperty("memoryMonitor", System.getProperty("memoryMonitor", "false"))
                .addProperty("threadPoolSize", System.getProperty("threadPoolSize", "" + Runtime.getRuntime().availableProcessors()))
                .addProperty("outputFormat", System.getProperty("outputFormat", "percentage"))
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
}
