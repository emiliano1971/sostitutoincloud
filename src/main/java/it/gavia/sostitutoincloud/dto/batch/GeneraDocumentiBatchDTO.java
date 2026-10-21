package it.gavia.sostitutoincloud.dto.batch;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/** Body di POST /api/documents/genera-batch: documenti mancanti per più prenotazioni. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GeneraDocumentiBatchDTO {
    private List<Integer> bookingIds;
    private LocalDate dataEmissione;
}
