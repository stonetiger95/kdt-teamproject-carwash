package kdt.washPrj.global.vehicle;

import java.util.Locale;
import java.util.regex.Pattern;

public final class PlateNumberNormalizer {
    private static final Pattern SEPARATORS = Pattern.compile("[\\s-]+");

    private PlateNumberNormalizer() {
    }

    public static String normalize(String plateNumber) {
        if (plateNumber == null) {
            return null;
        }

        return SEPARATORS
                .matcher(plateNumber)
                .replaceAll("")
                .toUpperCase(Locale.ROOT);
    }
}
