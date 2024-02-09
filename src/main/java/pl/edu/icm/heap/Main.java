package pl.edu.icm.heap;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.GZIPInputStream;

public class Main {
    private static final int shingletonLength = 18;

    public static void main(String[] args) throws IOException {
        String filename = "sample_08414.fq.gz";

        Instant start = Instant.now();
        int count = 0;

        Set<String> shinglets = new HashSet<>();
        try (BufferedReader input = new BufferedReader(new InputStreamReader(
//                new BufferedInputStream(
//                        new FileInputStream(filename)),
                new GZIPInputStream(new FileInputStream(filename), 16 * 1024 * 1024)), 32 * 1024 * 1024)) {

            String line;
            input.readLine(); // skip line

            StringBuilder sb = new StringBuilder();

            char[] sample = new char[shingletonLength * 100_000];
            while ((line = input.readLine()) != null) {
                sb.append(line);
                while (sb.length() >= sample.length) {
                    sb.getChars(0, sample.length, sample, 0);
                    for (int i = 0; i < sample.length - shingletonLength; ++i) {
                        String shinglet = new String(sample, i, shingletonLength);
//                        shinglets.add(shinglet);
                        count += shinglet.length();
                    }
                    sb.delete(0, sample.length - shingletonLength);
                }
                input.readLine(); // skip line
                input.readLine(); // skip line
                input.readLine(); // skip line
            }
            sample = new char[sb.length()];
            sb.getChars(0, sample.length, sample, 0);
            for (int i = 0; i < sample.length - shingletonLength; ++i) {
                String shinglet = new String(sample, i, shingletonLength);
//                shinglets.add(shinglet);
                count += shinglet.length();
            }

        }
        System.out.println("Size: " + count);
        Instant finish = Instant.now();

        long timeElapsed = Duration.between(start, finish).toMillis();
        System.out.println("Time: " + timeElapsed / 1e3);
    }
}
