package it.gavia.sostitutoincloud.service;

import it.gavia.sostitutoincloud.dao.PropertyContractRuleDAO;
import it.gavia.sostitutoincloud.dao.PropertyDAO;
import it.gavia.sostitutoincloud.dto.booking.ContrattoCalcoloResult;
import it.gavia.sostitutoincloud.dto.settings.TenantSettingsDTO;
import it.gavia.sostitutoincloud.model.Property;
import it.gavia.sostitutoincloud.model.PropertyContractRule;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Calcola lo split economico di una prenotazione applicando le regole di contratto
 * dell'immobile (property_contract_rule) e i parametri fiscali del tenant.
 */
@Service
@Log4j2
public class ContrattoCalcolatoreService {

    private static final BigDecimal ALIQUOTA_IVA_RF01 = new BigDecimal("0.22");
    private static final BigDecimal CENTO = new BigDecimal("100");

    private final PropertyContractRuleDAO contractRuleDAO;
    private final PropertyDAO propertyDAO;
    private final TenantSettingsService tenantSettingsService;

    public ContrattoCalcolatoreService(PropertyContractRuleDAO contractRuleDAO,
                                       PropertyDAO propertyDAO,
                                       TenantSettingsService tenantSettingsService) {
        this.contractRuleDAO = contractRuleDAO;
        this.propertyDAO = propertyDAO;
        this.tenantSettingsService = tenantSettingsService;
    }

    public ContrattoCalcoloResult calcola(Integer tenantId,
                                          Integer propertyId,
                                          Integer fkCanaleOtaId,
                                          BigDecimal gross,
                                          BigDecimal otaCommissionOverride,
                                          Integer nights,
                                          Integer guests) {

        List<String> warnings = new ArrayList<>();
        BigDecimal grossAmount = round(orZero(gross));
        int n = nights != null ? nights : 0;
        int g = guests != null ? guests : 1;

        // 1. Settings tenant
        TenantSettingsDTO settings = tenantSettingsService.getSettings(tenantId);
        String regimePm = settings.getRegimeFiscalePm() != null ? settings.getRegimeFiscalePm() : "RF01";

        // 2. Aliquota IVA PM: RF19 → 0, RF01 → 0.22
        BigDecimal aliquotaIvaPm = "RF19".equalsIgnoreCase(regimePm) ? BigDecimal.ZERO : ALIQUOTA_IVA_RF01;

        // 2b. Aliquota ritenuta in base al primo/secondo immobile dell'owner.
        //     primo immobile → ritenuta primaria (es. 21.00), dal secondo → ritenuta secondaria (es. 26.00).
        //     Property assente (o propertyId null) → si applica la ritenuta primaria come fallback.
        Property property = propertyId != null ? propertyDAO.findById(propertyId).orElse(null) : null;
        boolean primoImmobile = property == null || Boolean.TRUE.equals(property.getPrimoImmobile());
        BigDecimal aliquotaRitenuta = primoImmobile
                ? orZero(settings.getWithholdingRatePrimary())
                : orZero(settings.getWithholdingRateSecondary()); // es. 21.00 / 26.00

        // 3. Regole del contratto
        List<PropertyContractRule> rules = propertyId != null
                ? contractRuleDAO.findByPropertyId(propertyId)
                : List.of();

        // 9. Nessuna regola → fallback
        if (rules.isEmpty()) {
            warnings.add("Nessuna regola di contratto trovata per l'immobile: usati valori di fallback");
            BigDecimal ownerNet = grossAmount;
            // Fallback ritenuta: aliquota dell'immobile (primaria se property assente), su base lorda.
            BigDecimal withholding = round(grossAmount.multiply(aliquotaRitenuta).divide(CENTO));
            log.info("ContrattoCalcolatore - tenant={} property={} canale={} gross={} ownerNet={} withholding={} (FALLBACK)",
                    tenantId, propertyId, fkCanaleOtaId, grossAmount, ownerNet, withholding);
            return ContrattoCalcoloResult.builder()
                    .grossAmount(grossAmount)
                    .otaCommissionAmount(BigDecimal.ZERO)
                    .cleaningAmount(BigDecimal.ZERO)
                    .pmFeeAmount(BigDecimal.ZERO)
                    .imponibilePm(BigDecimal.ZERO)
                    .imponibileFatturaPm(BigDecimal.ZERO)
                    .ivaScorporata(BigDecimal.ZERO)
                    .fatturaPmTotale(BigDecimal.ZERO)
                    .ownerNetAmount(ownerNet)
                    .withholdingAmount(withholding)
                    .aliquotaRitenuta(round(aliquotaRitenuta))
                    .liquidazioneOwner(round(ownerNet.subtract(withholding)))
                    .regimeFiscalePm(regimePm)
                    .calcoloCompleto(false)
                    .warnings(warnings)
                    .build();
        }

        // 4. Regole applicabili al canale corrente (canale specifico o generiche = null)
        List<PropertyContractRule> applicabili = rules.stream()
                .filter(r -> r.getFkCanaleOtaId() == null
                        || Objects.equals(r.getFkCanaleOtaId(), fkCanaleOtaId))
                .toList();

        // per commissione_ota: regola del canale corrente se esiste, altrimenti generica
        PropertyContractRule otaRule = applicabili.stream()
                .filter(r -> "commissione_ota".equals(r.getTipo()))
                .filter(r -> Objects.equals(r.getFkCanaleOtaId(), fkCanaleOtaId))
                .findFirst()
                .orElseGet(() -> applicabili.stream()
                        .filter(r -> "commissione_ota".equals(r.getTipo()))
                        .filter(r -> r.getFkCanaleOtaId() == null)
                        .findFirst()
                        .orElse(null));

        // 5. Calcolo delle voci non rimanenza
        BigDecimal otaAmount = BigDecimal.ZERO;
        BigDecimal cleaningAmount = BigDecimal.ZERO;
        BigDecimal pmFeeAmount = BigDecimal.ZERO;
        BigDecimal totalNonRemainder = BigDecimal.ZERO;
        PropertyContractRule remainderRule = null;

        for (PropertyContractRule rule : applicabili) {
            if (Boolean.TRUE.equals(rule.getIsRemainder())) {
                remainderRule = rule;
                continue;
            }
            BigDecimal valore = orZero(rule.getValore());
            switch (rule.getTipo()) {
                case "pulizie" -> {
                    BigDecimal v = round(calcStandard(rule.getCalcMode(), valore, grossAmount, n, g));
                    cleaningAmount = cleaningAmount.add(v);
                    totalNonRemainder = totalNonRemainder.add(v);
                }
                case "cambio_biancheria" -> {
                    BigDecimal v = round(calcStandard(rule.getCalcMode(), valore, grossAmount, n, g));
                    cleaningAmount = cleaningAmount.add(v);
                    totalNonRemainder = totalNonRemainder.add(v);
                }
                case "commissione_ota" -> {
                    // applica solo la regola OTA scelta
                    if (rule != otaRule) continue;
                    BigDecimal v;
                    if (otaCommissionOverride != null) {
                        v = round(otaCommissionOverride); // valore dal CSV / DB
                    } else if ("fisso".equals(rule.getCalcMode())) {
                        v = round(valore);
                    } else { // percentuale / percentuale_lordo
                        v = round(grossAmount.multiply(valore).divide(CENTO));
                    }
                    otaAmount = otaAmount.add(v);
                    totalNonRemainder = totalNonRemainder.add(v);
                }
                case "commissione_pm" -> {
                    BigDecimal v;
                    if ("fisso".equals(rule.getCalcMode())) {
                        v = round(valore);
                    } else if ("fisso_per_notte".equals(rule.getCalcMode())) {
                        v = round(valore.multiply(BigDecimal.valueOf(n)));
                    } else { // percentuale / percentuale_lordo
                        v = round(grossAmount.multiply(valore).divide(CENTO));
                    }
                    pmFeeAmount = pmFeeAmount.add(v);
                    totalNonRemainder = totalNonRemainder.add(v);
                }
                case "provvigione_proprietario" -> {
                    // se non è rimanenza concorre comunque al totale non-rimanenza
                    BigDecimal v = round(calcStandard(rule.getCalcMode(), valore, grossAmount, n, g));
                    totalNonRemainder = totalNonRemainder.add(v);
                }
                default -> { /* tipo non gestito: ignora */ }
            }
        }

        // 6. Rimanenza
        BigDecimal remainderAmount = remainderRule != null
                ? round(grossAmount.subtract(totalNonRemainder))
                : BigDecimal.ZERO;
        if (remainderRule != null && remainderAmount.signum() < 0) {
            warnings.add("I costi superano il lordo della prenotazione");
        }
        if (remainderRule == null) {
            warnings.add("Nessuna voce impostata come rimanenza per l'immobile");
        }

        // 8. Split fiscale — i valori dei servizi (OTA, pulizie, commissione PM) sono GIÀ LORDI,
        //    IVA inclusa. L'IVA va SCORPORATA (lordo / 1.22), non aggiunta sopra.
        //    Il totale della fattura PM coincide quindi con il lordo dei servizi.
        BigDecimal lordoServizi = round(otaAmount.add(cleaningAmount).add(pmFeeAmount));
        BigDecimal imponibilePm = lordoServizi; // alias storico: lordo servizi PM
        BigDecimal imponibileFatturaPm;
        BigDecimal ivaScorporata;
        if (aliquotaIvaPm.signum() == 0) {
            // RF19 forfettario: nessuno scorporo IVA
            imponibileFatturaPm = lordoServizi;
            ivaScorporata = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        } else {
            // RF01 ordinario: scorporo IVA dal lordo
            BigDecimal divisore = BigDecimal.ONE.add(aliquotaIvaPm); // es. 1.22
            imponibileFatturaPm = lordoServizi.divide(divisore, 2, RoundingMode.HALF_UP);
            ivaScorporata = round(lordoServizi.subtract(imponibileFatturaPm));
        }
        BigDecimal fatturaPmTotale = lordoServizi; // totale lordo della fattura PM
        BigDecimal ownerNet = round(grossAmount.subtract(fatturaPmTotale));
        BigDecimal withholding = round(ownerNet.multiply(aliquotaRitenuta).divide(CENTO));
        BigDecimal liquidazione = round(ownerNet.subtract(withholding));

        boolean calcoloCompleto = remainderRule != null;

        // 10. Log
        log.info("ContrattoCalcolatore - tenant={} property={} canale={} gross={} ownerNet={} withholding={}",
                tenantId, propertyId, fkCanaleOtaId, grossAmount, ownerNet, withholding);

        return ContrattoCalcoloResult.builder()
                .grossAmount(grossAmount)
                .otaCommissionAmount(otaAmount)
                .cleaningAmount(cleaningAmount)
                .pmFeeAmount(pmFeeAmount)
                .imponibilePm(imponibilePm)
                .imponibileFatturaPm(imponibileFatturaPm)
                .ivaScorporata(ivaScorporata)
                .fatturaPmTotale(fatturaPmTotale)
                .ownerNetAmount(ownerNet)
                .withholdingAmount(withholding)
                .aliquotaRitenuta(round(aliquotaRitenuta))
                .liquidazioneOwner(liquidazione)
                .regimeFiscalePm(regimePm)
                .calcoloCompleto(calcoloCompleto)
                .warnings(warnings)
                .build();
    }

    /** Calcolo per le modalità comuni (pulizie, cambio_biancheria, provvigione non rimanenza). */
    private BigDecimal calcStandard(String calcMode, BigDecimal valore, BigDecimal gross, int nights, int guests) {
        if (calcMode == null) return valore;
        return switch (calcMode) {
            case "fisso" -> valore;
            case "fisso_per_notte" -> valore.multiply(BigDecimal.valueOf(nights));
            case "fisso_per_persona" -> valore.multiply(BigDecimal.valueOf(guests));
            case "percentuale", "percentuale_lordo" -> gross.multiply(valore).divide(CENTO);
            default -> valore;
        };
    }

    private BigDecimal round(BigDecimal v) {
        return (v != null ? v : BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal orZero(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
