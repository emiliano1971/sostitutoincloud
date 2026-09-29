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

    // Modello IVA (migration 020): le regole esprimono importi NETTI (imponibile), il lordo in
    // fattura PM è imponibile × (1 + aliquotaIvaPm/100). Gli *Amount sono i LORDI.
    private BigDecimal grossAmount;
    private BigDecimal otaCommissionAmount;     // lordo
    private BigDecimal cleaningAmount;          // = pulizieAmount + cambioBiancheriaAmount (backward compat)
    private BigDecimal pulizieAmount;           // lordo solo pulizie
    private BigDecimal cambioBiancheriaAmount;  // lordo solo cambio biancheria
    private BigDecimal pmFeeAmount;             // lordo
    private BigDecimal otaImponibile;               // netto OTA (regola, override manuale o lordo da file / (1+IVA))
    private BigDecimal pulizieImponibile;           // netto pulizie
    private BigDecimal cambioBiancheriaImponibile;  // netto cambio biancheria
    private BigDecimal pmImponibile;                // netto PM
    private BigDecimal aliquotaIvaPm;               // % IVA applicata alle voci PM: 22.00 (RF01) o 0.00 (RF19)
    private BigDecimal imponibilePm;        // alias storico: lordo servizi (ota + pulizie + cambio + pm + extra)
    private BigDecimal imponibileFatturaPm; // Σ imponibili delle voci in fattura PM (extra comprese)
    private BigDecimal ivaScorporata;       // fatturaPmTotale - imponibileFatturaPm (IVA della fattura PM)
    private BigDecimal fatturaPmTotale;     // Σ lordi delle voci in fattura PM (extra comprese)
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
    // Con più regole dello stesso tipo è la prima applicata.
    private Integer fkRegolaOtaId;
    private Integer fkRegolaCleaningId;             // legacy: = fkRegolaPulizieId, altrimenti cambio biancheria
    private Integer fkRegolaPulizieId;              // null se override o regola assente
    private Integer fkRegolaCambioBiancheriaId;     // null se override o regola assente
    private Integer fkRegolaPmId;
}
