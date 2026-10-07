package pl.edu.icm.heap.kite;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Serial;
import java.io.Serializable;
import java.util.*;

public class VirusesDatabase implements Serializable {
    private final int[] shinglesLength;
    private final LinkedHashSet<String> names;
    private final Map<String, Set<String>> viruses;
    transient private final Set<String> superset;

    public VirusesDatabase(int[] shinglesLength) {
        this.shinglesLength = shinglesLength;

        names = new LinkedHashSet<>();
        viruses = new HashMap<>();
        superset = new HashSet<>();
    }

    public VirusesDatabase(VirusesDatabase that) {
        this.shinglesLength = that.shinglesLength;
        this.names = that.names;
        this.viruses = that.viruses;

        superset = new HashSet<>();
        viruses.values().forEach(superset::addAll);
    }

    @Serial
    private Object readResolve() {
        return new VirusesDatabase(this);
    }

    public void loadFromInputStream(InputStream inputStream) throws IOException {
        Map<String, Set<String>> localViruses = new HashMap<>();
        LinkedHashSet<String> localNames = new LinkedHashSet<>();
        int virusCount = viruses.size();

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
                        localNames.add(name);
                        localViruses.merge(name, generator, (s1, s2) -> {
                            s1.addAll(s2);
                            return s1;
                        });

                        virus.setLength(0);
                    }
                    if (line == null) {
                        break;
                    }

                    ++virusCount;
                    name = line.substring(1).strip().split("[\\s|]", 2)[0];
                    if (name.isEmpty()) {
                        name = "Virus-" + virusCount;
                    }
                } else {
                    virus.append(line);
                }
            }
        }

        for (Map.Entry<String, Set<String>> entry : localViruses.entrySet()) {
            viruses.merge(entry.getKey(), entry.getValue(), (s1,s2)->{s1.addAll(s2); return s1;});
        }
        names.addAll(localNames);
        localViruses.values().forEach(superset::addAll);
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

    public List<CrosscheckResult> crosscheck(Set<String> shingles) {
        List<CrosscheckResult> resultList = new ArrayList<>(names.size());
        for (String name : names) {
            Set<String> virusShingles = viruses.get(name);
            long shinglesCount = calculateIntersection(shingles, virusShingles);
            double index = (double) shinglesCount / virusShingles.size();
            resultList.add(new CrosscheckResult(name, shinglesCount, index));
        }
        return resultList;
    }

    public static long calculateIntersection(Set<String> shingles, Set<String> virusShingles) {
        return shingles.stream().filter(virusShingles::contains).count();
    }

    public record CrosscheckResult(String name, long shingles, double percentage) implements Serializable {
    }
}
