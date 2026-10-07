package it.gavia.sostitutoincloud.dto.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/** Pagina della lista documenti fiscali (GET /api/documents). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentPageDTO {

    private List<DocumentListDTO> content;
    private int page;               // pagina corrente, 0-based
    private int size;               // dimensione pagina effettiva (parametro o tenant_settings.page_size)
    private long totalElements;     // documenti che soddisfano i filtri
    private int totalPages;
}
