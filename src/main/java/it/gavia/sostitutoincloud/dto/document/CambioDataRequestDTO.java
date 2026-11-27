package it.gavia.sostitutoincloud.dto.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/** Body di POST /api/documents/{id}/cambio-data: nuova data e motivo (5-500 caratteri). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CambioDataRequestDTO {
    private LocalDate data;
    private String motivo;
    /**
     * Solo ricevuta: conferma esplicita degli avvisi di scadenza F24 superata (periodo di origine
     * o di destinazione). Senza, se gli avvisi si applicano, il cambio è rifiutato (422).
     */
    private Boolean confermaScadenza;
}
