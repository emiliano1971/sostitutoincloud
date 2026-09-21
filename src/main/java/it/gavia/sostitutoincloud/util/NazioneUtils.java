package it.gavia.sostitutoincloud.util;

import java.util.Set;

/**
 * Riconoscimento della nazione dell'ospite. Il valore arriva come testo grezzo
 * (codici del gestionale di origine nei file di import, codici del select nel
 * frontend, testo libero nei dati storici): non esiste una lookup a DB.
 */
public final class NazioneUtils {

    private NazioneUtils() {}

    /** Codici e stringhe che identificano l'Italia in tutti i percorsi. */
    private static final Set<String> NAZIONI_ITALIA = Set.of(
            "100000100", "italia", "it", "ita", "italy");
    // NB: "999999999" (straniero generico, opzione del select) resta volutamente FUORI
    // dalla whitelist: è riconosciuto come estero e fa generare il CF fittizio.

    /**
     * Nazione estera = valorizzata e non riconducibile all'Italia. Una nazione assente non
     * è considerata estera: senza il dato non si genera alcun CF fittizio.
     */
    public static boolean isNazioneEstera(String country) {
        if (country == null || country.isBlank()) return false;
        return !NAZIONI_ITALIA.contains(country.trim().toLowerCase());
    }
}
