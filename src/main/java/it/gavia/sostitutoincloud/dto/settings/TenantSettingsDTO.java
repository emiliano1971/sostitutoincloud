package it.gavia.sostitutoincloud.dto.settings;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TenantSettingsDTO {

    // Dati aziendali tenant
    private String legalName;
    private String displayName;
    private String taxCode;
    private String vatNumber;
    private String administrativeEmail;
    private String pec;
    private String phone;
    private String legalAddress;

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
}
