package it.gavia.sostitutoincloud.dto.importing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Risposta all'upload dei file import V2: id di sessione dei file raw,
 * colonne rilevate e mapping suggerito automaticamente.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportUploadResponseDTO {

    private String bookingSessionId;
    private String guestSessionId;                       // nullable
    private List<String> bookingColumns;
    private List<String> guestColumns;                   // nullable
    private Map<String, String> suggestedBookingMapping;
    private Map<String, String> suggestedGuestMapping;   // nullable
    private List<String> statoColumnValues;              // valori distinti colonna STATO, null se non rilevata
}
