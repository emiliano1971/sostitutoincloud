package it.gavia.sostitutoincloud.dto.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Body di POST /api/documents/elimina-batch-controllo e /elimina-batch: documenti selezionati
 * (massimo 50) e motivo unico, 5-500 caratteri (ignorato dal controllo).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EliminazioneBatchRequestDTO {
    private List<Integer> documentIds;
    private String motivo;
}
