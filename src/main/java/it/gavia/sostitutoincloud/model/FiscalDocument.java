package it.gavia.sostitutoincloud.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Log4j2
public class FiscalDocument {

    private Integer id;
    private Integer fkTenantId;
    private Integer fkBookingId;
    private Integer fkTipoDocumentoId;
    private Integer fkSdiEsitoId;
    private String documentNumber;
    private LocalDate issueDate;
    private String recipientName;
    private String recipientTaxCode;
    private BigDecimal totalAmount;
    private BigDecimal vatAmount;
    private BigDecimal imponibile;
    private BigDecimal ritenutaAmount;
    private BigDecimal bolloAmount;
    private BigDecimal ivaAmount;
    private Integer fkStatoDocumentoId;
    private String sdiIdentifier;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
