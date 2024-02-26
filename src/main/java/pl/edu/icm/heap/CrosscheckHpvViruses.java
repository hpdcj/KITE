package pl.edu.icm.heap;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CrosscheckHpvViruses {
    public static void main(String[] args) throws IOException {
        int shingleLength = Integer.parseInt(System.getProperty("shingleLength", "" + (18)));

        try (InputStream hpvVirusesInputStream = args.length == 0
                ? HpvViruses.class.getResourceAsStream("/61HF7T14MD27_2024-02-23T090442.fa")
                : Files.newInputStream(Path.of(args[0]))) {
            HpvViruses hpvViruses = new HpvViruses(hpvVirusesInputStream, shingleLength);
            for (String name : hpvViruses.getNames()) {
                System.out.printf("\t%s", name);
            }
            System.out.println();
            Pattern pattern = Pattern.compile("(\\D*)(\\d*)");

            String[] hpvVirusesNames = hpvViruses.getNames();
            Arrays.sort(hpvVirusesNames, (o1, o2) -> {
                // https://codereview.stackexchange.com/a/37249
                Matcher m1 = pattern.matcher(o1);
                Matcher m2 = pattern.matcher(o2);

                // The only way find() could fail is at the end of a string
                while (m1.find() && m2.find()) {
                    // matcher.group(1) fetches any non-digits captured by the
                    // first parentheses in PATTERN.
                    int nonDigitCompare = m1.group(1).compareTo(m2.group(1));
                    if (nonDigitCompare != 0) {
                        return nonDigitCompare;
                    }

                    // matcher.group(2) fetches any digits captured by the
                    // second parentheses in PATTERN.
                    if (m1.group(2).isEmpty()) {
                        return m2.group(2).isEmpty() ? 0 : -1;
                    } else if (m2.group(2).isEmpty()) {
                        return 1;
                    }

                    // Integer wystarczy ze wzgledu na 3 cyfry na numer wirusa
                    int n1 = Integer.parseInt(m1.group(2));
                    int n2 = Integer.parseInt(m2.group(2));

                    if (n1 != n2) {
                        return n1 < n2 ? -1 : 1;
                    }
                }

                // Handle if one string is a prefix of the other.
                // Nothing comes before something.
                return m1.hitEnd() && m2.hitEnd() ? 0
                        : m1.hitEnd() ? -1 : 1;
            });

            for (String name : hpvVirusesNames) {
                System.out.print(name);
                Set<String> shingles = hpvViruses.getShingles(name);
                for (String hpvName : hpvVirusesNames) {
                    Set<String> hpvShingles = hpvViruses.getShingles(hpvName);
                    double index = HpvViruses.calculateIndex(shingles, hpvShingles);

                    System.out.printf("\t%.6f", index);
                }
                System.out.println();
            }

        }
    }
}
