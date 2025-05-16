package pl.edu.icm.heap.kite;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serial;
import java.io.Serializable;
import java.util.*;

public class VirusesDatabase implements Serializable {
    private final List<String> names;
    private final Map<String, Set<String>> viruses;
    transient private final Set<String> superset;

    public VirusesDatabase(InputStream inputStream, int[] shinglesLength) throws IOException {
        names = new ArrayList<>();
        viruses = new HashMap<>();
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
                        names.add(name);
                        viruses.put(name, Collections.unmodifiableSet(generator));

                        virus.setLength(0);
                    }
                    if (line == null) {
                        break;
                    }

                    int index = line.indexOf(" ");
                    name = line.substring(1, index == -1 ? line.length() : index);
                } else {
                    virus.append(line);
                }
            }
        }

        superset = new HashSet<>();
        viruses.values().forEach(superset::addAll);
    }

    public VirusesDatabase(VirusesDatabase that) {
        this.names = that.names;
        this.viruses = that.viruses;

        superset = new HashSet<>();
        viruses.values().forEach(superset::addAll);
    }

    @Serial
    private Object readResolve() {
        return new VirusesDatabase(this);
    }

    public int count() {
        return names.size();
    }

    public String[] getNames() {
        return names.toArray(new String[0]);
    }

    public boolean hasShingle(String shingle) {
        return superset.contains(shingle);
    }

    public Set<String> getShingles(String virusName) throws NoSuchElementException {
        if (!names.contains(virusName)) {
            throw new NoSuchElementException("Not found: " + virusName);
        }
        return Collections.unmodifiableSet(viruses.get(virusName));
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
        for (String name : names) {
            Set<String> virusShingles = viruses.get(name);
            double index = calculateIndex(shingles, virusShingles);
            priorityQueue.add(new CrosscheckResult(name, index));
        }
        return priorityQueue;
    }

    public static double calculateIndex(Set<String> shingles, Set<String> virusShingles) {
        long intersectionSize = shingles.stream().filter(virusShingles::contains).count();
        return (double) intersectionSize / virusShingles.size();
    }

    public record CrosscheckResult(String name, double value) implements Serializable {
    }
}
