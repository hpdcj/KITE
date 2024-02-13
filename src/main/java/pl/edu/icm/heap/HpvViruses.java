package pl.edu.icm.heap;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;

public class HpvViruses {
    private final List<String> hpvNames;
    private final Map<String, Set<String>> hpvViruses;
    private final Set<String> superset;

    public HpvViruses(int shingletonLength) throws IOException {
        hpvNames = new ArrayList<>();
        hpvViruses = new HashMap<>();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(
                        Objects.requireNonNull(
                                HpvViruses.class.getResourceAsStream("/hpv_viruses.fasta"))))) {
            String name = "";
            StringBuilder virus = new StringBuilder();
            while (true) {
                String line = br.readLine();
                if (line == null || line.startsWith(">")) {
                    if (!virus.isEmpty()) {
                        Set<String> generator = new HashSet<>();
                        for (int i = 0; i < virus.length() - shingletonLength; ++i) {
                            generator.add(virus.substring(i, i + shingletonLength));
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

    public boolean hasShinglet(String shinglet) {
        return superset.contains(shinglet);
    }

    public PriorityQueue<CrosscheckResult> crosscheck(Set<String> shinglets) {
        PriorityQueue<CrosscheckResult> priorityQueue = new PriorityQueue<>((v1, v2) -> {
            int value = Double.compare(v1.value(), v2.value());
            if (value == 0) return v1.name().compareTo(v2.name());
            return -value;
        });
        for (String hpvName : hpvNames) {
            Set<String> hpvShinglets = hpvViruses.get(hpvName);
            long intersectionSize = shinglets.stream().filter(hpvShinglets::contains).count();
            double index =  (double) intersectionSize / hpvShinglets.size();
            priorityQueue.add(new CrosscheckResult(hpvName, index));
        }
        return priorityQueue;
    }

    public record CrosscheckResult(String name, double value) {
    }
}
