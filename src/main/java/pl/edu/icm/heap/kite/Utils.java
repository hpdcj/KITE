package pl.edu.icm.heap.kite;

import java.util.Arrays;

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
}
