package pl.edu.icm.heap.kite;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

public class Utils {
    public static boolean isNonNegativeInteger(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        return str.chars().allMatch(c -> c >= '0' && c <= '9');
    }

    public static String shinglesLengthToString(int[] shinglesLength) {
        if (shinglesLength == null || shinglesLength.length == 0) {
            return "-";
        } else if (shinglesLength.length == 1) {
            return String.valueOf(shinglesLength[0]);
        } else {
            return Arrays.toString(shinglesLength);
        }
    }

    public static void startMemoryMonitor() {
        Function<Long, String> memToText = size -> {
            if (size < 1024L) return size + "B";
            if (size < 1024L * 1024L) return String.format("%.2fKB", size / 1024.);
            if (size < 1024L * 1024L * 1024L) return String.format("%.2fMB", size / 1024. / 1024.);
            return String.format("%.2fGB", size / 1024. / 1024. / 1024.);
        };
        MemoryMXBean memBean = ManagementFactory.getMemoryMXBean();
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1, r -> {
            Thread thread = new Thread(r);
            thread.setDaemon(true);
            return thread;
        });
        AtomicInteger iteration = new AtomicInteger(0);
        AtomicBoolean changed = new AtomicBoolean(false);
        AtomicReference<MemoryUsage> maxHeap = new AtomicReference<>(memBean.getHeapMemoryUsage());
        AtomicReference<MemoryUsage> maxNonheap = new AtomicReference<>(memBean.getNonHeapMemoryUsage());
        scheduler.scheduleAtFixedRate(() -> {
            LocalDateTime now = LocalDateTime.now();
            MemoryUsage heap = memBean.getHeapMemoryUsage();
            MemoryUsage nonheap = memBean.getNonHeapMemoryUsage();

            if (heap.getUsed() > maxHeap.get().getUsed()) {
                maxHeap.set(heap);
                changed.set(true);
            }
            if (nonheap.getUsed() > maxNonheap.get().getUsed()) {
                maxNonheap.set(nonheap);
                changed.set(true);
            }

            if (iteration.getAndIncrement() % 1000 == 0) {
                memBean.gc();
                System.err.println(now + ": MEM_USAGE:\tHEAP: " + memToText.apply(heap.getUsed()) + ",\tNONHEAP: " + memToText.apply(nonheap.getUsed()));
                if (changed.getAndSet(false)) {
                    System.err.println(now + ": MAX_MEM_USAGE:\tHEAP: " + memToText.apply(maxHeap.get().getUsed()) + ",\tNONHEAP: " + memToText.apply(maxNonheap.get().getUsed()));
                }
            }
        }, 0L, 10L, TimeUnit.MILLISECONDS);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            scheduler.shutdown();
            System.err.println("MAX HEAP: " + maxHeap.get());
            System.err.println("MAX NONHEAP: " + maxNonheap.get());
        }));
    }
}
