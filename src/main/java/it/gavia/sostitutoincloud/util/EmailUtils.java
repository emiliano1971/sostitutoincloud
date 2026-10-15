package it.gavia.sostitutoincloud.util;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Elenchi di indirizzi email nel formato di booking.guest_email: più indirizzi separati da ";".
 */
public final class EmailUtils {

    private EmailUtils() {
    }

    /**
     * Normalizza un elenco di indirizzi separati da spazio, virgola o punto e virgola
     * nel formato "a@x.it;b@y.it" (trim, senza vuoti né duplicati, ordine preservato).
     * null o blank → null.
     */
    public static String normalizza(String raw) {
        List<String> indirizzi = elenco(raw);
        return indirizzi.isEmpty() ? null : String.join(";", indirizzi);
    }

    /** Unisce due elenchi: gli indirizzi di {@code aggiunte} già presenti in {@code base} non sono ripetuti. */
    public static String unisci(String base, String aggiunte) {
        Set<String> tutti = new LinkedHashSet<>(elenco(base));
        for (String a : elenco(aggiunte)) {
            if (tutti.stream().noneMatch(e -> e.equalsIgnoreCase(a))) {
                tutti.add(a);
            }
        }
        return tutti.isEmpty() ? null : String.join(";", tutti);
    }

    /** Indirizzi singoli dell'elenco (vuoto se null/blank). */
    public static List<String> elenco(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        Set<String> out = new LinkedHashSet<>();
        Arrays.stream(raw.trim().split("[;,\\s]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .forEach(s -> {
                    if (out.stream().noneMatch(e -> e.equalsIgnoreCase(s))) {
                        out.add(s);
                    }
                });
        return List.copyOf(out);
    }
}
