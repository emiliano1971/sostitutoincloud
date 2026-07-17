package it.gavia.sostitutoincloud.dto.importing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingImportPreviewDTO {

    private String fileName;
    private Integer totalRows;
    private Integer newCount;
    private Integer dupeCount;
    private Integer errorCount;
    private Integer warningCount;
    private Integer excludedCount;   // righe escluse perché cancellate (STATO)
    private List<BookingImportPreviewRowDTO> rows;
    private String importSessionId;
}
