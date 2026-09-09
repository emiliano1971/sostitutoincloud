package it.gavia.sostitutoincloud.dto.test;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Richiesta di cleanup dei dati di anagrafica creati dai test E2E.
 * Entrambi i campi sono opzionali: si passa quello che serve ripulire.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CleanupAnagraficaDTO {

    private Integer ownerId;
    private Integer propertyId;
    /**
     * Alternativa agli id: rimuove tutti i proprietari di test il cui cognome contiene
     * questo pattern, con i loro immobili. Il pattern deve contenere "E2E-" o "TEST-".
     */
    private String lastNamePattern;
}
