package pl.edu.icm.heap;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serial;
import java.io.Serializable;
import java.util.*;

public class HpvViruses implements Serializable {
    private final List<String> hpvNames;
    private final Map<String, Set<String>> hpvViruses;
    transient private final Set<String> superset;

    public HpvViruses(InputStream inputStream, int[] shinglesLength) throws IOException {
        hpvNames = new ArrayList<>();
        hpvViruses = new HashMap<>();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(inputStream))) {
            String name = "";
            StringBuilder virus = new StringBuilder();
            while (true) {
                String line = br.readLine();
                if (line == null || line.startsWith(">")) {
                    if (!virus.isEmpty()) {
                        Set<String> generator = new HashSet<>();
                        for (int i = 0; i < virus.length() - shinglesLength[shinglesLength.length - 1]; ++i) {
                            for (int shingleLength : shinglesLength) {
                                generator.add(virus.substring(i, i + shingleLength));
                            }
                        }
                        hpvNames.add(name);
                        hpvViruses.put(name, Collections.unmodifiableSet(generator));

                        virus.setLength(0);
                    }
                    if (line == null) {
                        break;
                    }

                    int index = line.indexOf("|");
                    name = line.substring(1, index == -1 ? line.length() : index);
                } else {
                    virus.append(line);
                }
            }
        }

        superset = new HashSet<>();
        hpvViruses.values().forEach(superset::addAll);
    }

    public HpvViruses(HpvViruses that) {
        this.hpvNames = that.hpvNames;
        this.hpvViruses = that.hpvViruses;

        superset = new HashSet<>();
        hpvViruses.values().forEach(superset::addAll);
    }

    @Serial
    private Object readResolve() {
        return new HpvViruses(this);
    }

    public int count() {
        return hpvNames.size();
    }

    public String[] getNames() {
        return hpvNames.toArray(new String[0]);
    }

    public boolean hasShingle(String shingle) {
        return superset.contains(shingle);
    }

    public Set<String> getShingles(String hpvVirusName) throws NoSuchElementException {
        if (!hpvNames.contains(hpvVirusName)) {
            throw new NoSuchElementException("Not found: " + hpvVirusName);
        }
        return Collections.unmodifiableSet(hpvViruses.get(hpvVirusName));
    }

    public PriorityQueue<CrosscheckResult> crosscheck(Set<String> shingles) {
        Comparator<CrosscheckResult> crosscheckResultComparator
                = (Comparator<CrosscheckResult> & Serializable) (v1, v2) -> {
            int value = Double.compare(v1.value(), v2.value());
            if (value == 0) {
                return v1.name().compareTo(v2.name());
            }
            return -value;
        };
        PriorityQueue<CrosscheckResult> priorityQueue = new PriorityQueue<>(crosscheckResultComparator);
        for (String hpvName : hpvNames) {
            Set<String> hpvShingles = hpvViruses.get(hpvName);
            double index = calculateIndex(shingles, hpvShingles);
            priorityQueue.add(new CrosscheckResult(hpvName, index));
        }
        return priorityQueue;
    }

    public static double calculateIndex(Set<String> shingles, Set<String> hpvShingles) {
        long intersectionSize = shingles.stream().filter(hpvShingles::contains).count();
        return (double) intersectionSize / hpvShingles.size();
    }

    public record CrosscheckResult(String name, double value) implements Serializable {
    }
}
