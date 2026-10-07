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
public class TenantSettings {

    private Integer id;
    private Integer fkTenantId;

    // Parametri fiscali
    private BigDecimal withholdingRatePrimary;
    private BigDecimal withholdingRateSecondary;
    private String codiceTributoF24;
    private Integer documentWindowDays;
    private Boolean cedolareSeccaEnabled;

    // Parametri bollo e regime PM
    private BigDecimal bolloImporto;
    private BigDecimal bolloSoglia;
    private Boolean bolloAddebitatoCliente;
    private String regimeFiscalePm;
    private String naturaIvaEsente;

    // Dati anagrafici di nascita PM (per F24)
    private LocalDate dataNascita;
    private String sesso;
    private String comuneNascita;
    private String provinciaNascita;

    // Policy documentali
    private Boolean sdiAutoSend;
    private Boolean derogaRicevutaEnabled;
    private Boolean numerazioneAutomatica;

    // Notifiche
    private Boolean alertScadenzeDocumenti;
    private Boolean alertScadenzeF24;
    private Boolean notificheEmail;

    // Import massivo proprietari: canale per le regole commissione_ota (null = nessuno)
    private Integer fkCanaleOtaDefaultId;

    // Codice tributo dei crediti d'imposta da NDC nel modello F24 (migration 027, default 6782)
    private String codiceTributoCreditoImposta;

    // Dimensione pagina delle liste paginate (migration 028, default 50)
    private Integer pageSize;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
