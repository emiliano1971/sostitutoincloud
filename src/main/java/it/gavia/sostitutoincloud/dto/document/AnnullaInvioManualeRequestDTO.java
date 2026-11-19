package it.gavia.sostitutoincloud.dto.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Body di POST /api/documents/{id}/annulla-invio-manuale: motivo obbligatorio, 5-500 caratteri. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnnullaInvioManualeRequestDTO {
    private String motivo;
}
