package it.gavia.sostitutoincloud.dto.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Risposta di POST /api/documents/{id}/elimina: documenti eliminati e avvisi confermati. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EliminazioneEsitoDTO {
    private Integer bookingId;
    private List<EliminazioneControlloDTO.DocumentoCoinvolto> eliminati;
    private List<EliminazioneControlloDTO.Avviso> avvisi;
}
