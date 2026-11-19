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
}
