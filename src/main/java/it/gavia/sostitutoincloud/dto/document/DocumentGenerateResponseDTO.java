package it.gavia.sostitutoincloud.dto.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentGenerateResponseDTO {

    private Integer documentId;
    /** es. "RIC-2026-0003" */
    private String documentNumber;
    /** "ricevuta_owner" | "fattura_pm" */
    private String tipoDocumento;
    private LocalDate dataEmissione;
    private BigDecimal importoTotale;
    /** 2.00 se importo > 77.47, altrimenti 0 */
    private BigDecimal importoBollo;
    private BigDecimal imponibile;
    /** solo per fattura_pm */
    private BigDecimal iva;
    private BigDecimal ritenuta;
    /** "draft" */
    private String statoDocumento;
    private String bookingExternalId;
    private String guestName;
    private String ownerName;
    private String propertyName;

    // Auto-invio SDI (tenant_settings.sdi_auto_send): valorizzati solo per la fattura PM
    // e solo se l'auto-invio è attivo. Tutti null quando l'auto-invio non è entrato in gioco.
    /** true se l'XML è stato generato automaticamente */
    private Boolean sdiAutoGenerato;
    /** path del file XML generato */
    private String sdiFilePath;
    /** progressivo assegnato all'invio, es. "EAAAA" */
    private String sdiProgressivo;
    /** true se l'auto-invio è stato saltato per dati ospite incompleti */
    private Boolean sdiDatiIncompleti;
    /** messaggio dell'errore che ha fatto fallire l'auto-invio (l'emissione resta valida) */
    private String sdiAutoSendError;
}
