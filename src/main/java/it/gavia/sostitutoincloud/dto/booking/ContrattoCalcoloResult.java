package it.gavia.sostitutoincloud.dto.booking;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContrattoCalcoloResult {

    private BigDecimal grossAmount;
    private BigDecimal otaCommissionAmount;
    private BigDecimal cleaningAmount;
    private BigDecimal pmFeeAmount;
    private BigDecimal imponibilePm;        // lordo servizi = ota + cleaning + pmFee (IVA inclusa)
    private BigDecimal imponibileFatturaPm; // lordo servizi / (1 + aliquotaIva) — base imponibile scorporata
    private BigDecimal ivaScorporata;       // lordo servizi - imponibileFatturaPm — IVA scorporata (informativa)
    private BigDecimal fatturaPmTotale;     // = lordo servizi (totale lordo della fattura PM)
    private BigDecimal ownerNetAmount;      // gross - fatturaPmTotale
    private BigDecimal withholdingAmount;   // ownerNet * aliquotaRitenuta
    private BigDecimal aliquotaRitenuta;    // % ritenuta applicata (21.00 o 26.00)
    private BigDecimal liquidazioneOwner;   // ownerNet - withholding
    private String regimeFiscalePm;         // RF01 o RF19
    private Boolean calcoloCompleto;        // true se trovate tutte le regole
    private List<String> warnings;          // messaggi se regole mancanti
    // Descrizione leggibile della regola applicata, per il dettaglio prenotazione.
    // null quando non esiste una regola per quella voce.
    private String pmFeeDescrizione;        // es. "Commissione PM (10% sul netto)"
    private String otaDescrizione;          // es. "Commissione OTA (18%)"
    // Regola di contratto da cui viene l'importo di ciascuna voce, per popolare
    // booking_split_economico.fk_property_contract_rule_id. null se la voce non viene
    // da una regola (nessuna regola, fallback, commissione OTA forzata o dal file).
    // Con più regole sulla stessa voce (es. pulizie + cambio biancheria) è la prima applicata.
    private Integer fkRegolaOtaId;
    private Integer fkRegolaCleaningId;
    private Integer fkRegolaPmId;
}
