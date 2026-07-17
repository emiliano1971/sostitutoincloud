package it.gavia.sostitutoincloud.dto.importing;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;
import java.util.Set;

/**
 * Mapping manuale tra campi di sistema e colonne del file caricato.
 * chiave = campo di sistema (es. "BOOKING_ID"), valore = nome colonna nel file (es. "Id").
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImportColumnMappingDTO {

    private Map<String, String> bookingMapping;
    private Map<String, String> guestMapping;
    private Set<String> statiDaEscludere;   // valori STATO scelti dall'utente; null/vuoto = fallback STATI_CANCELLATI
}
