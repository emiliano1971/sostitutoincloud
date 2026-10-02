package it.gavia.sostitutoincloud.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Registro analitico delle ritenute d'acconto operate: una riga per documento fiscale con ritenuta.
 * Collega la singola ritenuta (booking + documento) al versamento F24 in cui confluisce.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Log4j2
public class WithholdingLedger {

    private Integer id;
    private Integer fkTenantId;
    private Integer fkOwnerId;
    private Integer fkBookingId;
    private Integer fkFiscalDocumentId;
    private Integer periodoMese;
    private Integer periodoAnno;
    private BigDecimal canoneLocazione;
    private BigDecimal aliquotaRitenuta;
    private BigDecimal ritenutaAmount;
    private LocalDate dataEvento;
    /** Stato: 'da_versare' / 'versata' (in un F24) / 'stornata' / 'credito_imposta' (NDC, 026) / 'compensato' (027) */
    private String stato;
    private Integer fkF24RecordId;
    /** NDC che ha stornato la ritenuta o generato il credito (migration 026); null = ordinaria. */
    private Integer fkNdcId;
    /** Riga di credito da cui è stata spezzata questa compensazione parziale (migration 027). */
    private Integer fkLedgerOrigineId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
