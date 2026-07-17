package it.gavia.sostitutoincloud.dto.importing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingImportPreviewRowDTO {

    private Integer rowNumber;
    private String externalBookingId;
    private String guestName;
    private String propertyCode;
    private String propertyName;
    private Integer fkPropertyId;   // id immobile risolto (per dialog contratto inline in preview)
    private String channelCode;
    private String channelName;
    private LocalDate checkinDate;
    private LocalDate checkoutDate;
    private BigDecimal grossAmount;
    private String status;       // "nuova" | "duplicata" | "errore"
    private String errorMessage;
    private List<String> splitWarnings;  // avvisi calcolo split economico
    private BookingImportRowDTO rawData;
}
