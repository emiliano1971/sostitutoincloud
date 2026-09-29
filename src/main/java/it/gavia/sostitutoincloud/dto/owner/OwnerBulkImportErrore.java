package it.gavia.sostitutoincloud.dto.owner;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Riga del file Excel saltata per errore durante l'importazione massiva proprietari. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OwnerBulkImportErrore {

    /** Numero di riga come appare in Excel (1-based). */
    private int numeroRiga;
    /** Es. "Rossi Mario - Appartamento Centro". */
    private String descrizioneRiga;
    private String messaggio;
}
