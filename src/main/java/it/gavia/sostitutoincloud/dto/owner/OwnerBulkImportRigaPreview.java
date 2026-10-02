package it.gavia.sostitutoincloud.dto.owner;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Riga del file analizzata dalla preview dell'import massivo proprietari (nessuna scrittura a DB). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OwnerBulkImportRigaPreview {

    /** Numero di riga come appare in Excel (1-based): è la chiave della selezione. */
    private int numeroRiga;
    private String cognome;
    private String nome;
    private String codFisc;
    private String nomeImmobile;
    private String citta;
    /** 'ok' | 'duplicato_proprietario' | 'duplicato_immobile' | 'errore' */
    private String stato;
    /** Es. "Proprietario già esistente — verrà associato" o "Codice fiscale mancante". */
    private String messaggioStato;
    /** true solo se stato = 'ok'. */
    private boolean selezionabile;
    /** true di default se selezionabile. */
    private boolean selezionato;
}
