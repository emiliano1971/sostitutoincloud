package it.gavia.sostitutoincloud.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Log4j2
public class TenantSettings {

    private Integer id;
    private Integer fkTenantId;

    // Parametri fiscali
    private BigDecimal withholdingRatePrimary;
    private BigDecimal withholdingRateSecondary;
    private String codiceTributoF24;
    private Integer documentWindowDays;
    private Boolean cedolareSeccaEnabled;

    // Policy documentali
    private Boolean sdiAutoSend;
    private Boolean derogaRicevutaEnabled;
    private Boolean numerazioneAutomatica;

    // Notifiche
    private Boolean alertScadenzeDocumenti;
    private Boolean alertScadenzeF24;
    private Boolean notificheEmail;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
