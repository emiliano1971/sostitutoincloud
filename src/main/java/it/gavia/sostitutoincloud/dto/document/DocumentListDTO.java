package it.gavia.sostitutoincloud.dto.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentListDTO {

    private Integer id;
    private String documentNumber;
    private String documentType;
    private LocalDate issueDate;
    private String recipientName;
    private String recipientTaxCode;
    private BigDecimal totalAmount;
    private BigDecimal vatAmount;
    private String statoDocumento;
    private String sdiIdentifier;
    private String sdiEsito;
    private String propertyName;
    private String channelName;
    private Integer fkBookingId;
    private LocalDateTime createdAt;
}
