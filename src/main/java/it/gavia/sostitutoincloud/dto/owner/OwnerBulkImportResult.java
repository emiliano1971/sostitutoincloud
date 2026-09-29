package it.gavia.sostitutoincloud.dto.owner;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** Esito dell'importazione massiva proprietari/immobili da Excel (POST /api/owners/import-bulk). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OwnerBulkImportResult {

    private int righeProcessate;
    private int proprietariCreati;
    private int proprietariEsistenti;
    private int immobiliCreati;
    private int immobiliSaltati;
    private int righeInErrore;
    private List<OwnerBulkImportErrore> errori = new ArrayList<>();
}
