package it.gavia.sostitutoincloud.dto.owner;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/** Esito della preview dell'import massivo proprietari (POST /api/owners/import-bulk/preview). */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OwnerBulkImportPreviewResult {

    private List<OwnerBulkImportRigaPreview> righe = new ArrayList<>();
    private int righeOk;
    private int righeDuplicato;
    private int righeErrore;
}
