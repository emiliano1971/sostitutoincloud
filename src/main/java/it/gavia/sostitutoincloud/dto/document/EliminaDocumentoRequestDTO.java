package it.gavia.sostitutoincloud.dto.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Body di POST /api/documents/{id}/elimina: motivo obbligatorio, 5-500 caratteri. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EliminaDocumentoRequestDTO {
    private String motivo;
    /**
     * Solo dalla fattura: true (default se assente) elimina anche la ricevuta della prenotazione,
     * false solo la fattura. Ignorato per la ricevuta e se la prenotazione non ha ricevuta.
     */
    private Boolean eliminaRicevuta;
}
