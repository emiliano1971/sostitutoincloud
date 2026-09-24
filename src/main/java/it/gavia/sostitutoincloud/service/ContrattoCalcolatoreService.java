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
import java.util.Locale;
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

    /** Percentuale calcolata sul lordo residuo dopo tutte le altre voci di costo. */
    private static final String CALC_MODE_PERCENTUALE_NETTO = "percentuale_netto";
    /** Unico tipo di voce su cui la modalità 'percentuale_netto' è ammessa. */
    private static final String TIPO_COMMISSIONE_PM = "commissione_pm";
    /**
     * Commissione OTA senza regola di contratto, arrivata come override (file di import o PM).
     * Il frontend riconosce "importo impostato" / "importo forzato" per mostrare il ripristino.
     */
    private static final String DESCR_OTA_IMPOSTATA = "Commissione OTA (importo impostato)";

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

        // 9. Nessuna regola → fallback.
        //    La commissione OTA del file di import, se presente, è un dato reale del canale:
        //    usarla dà un netto proprietario molto più vicino al vero rispetto a ignorarla.
        //    Pulizie e provvigione PM restano a zero: senza regole non sono deducibili.
        if (rules.isEmpty()) {
            BigDecimal otaUsata = otaCommissionOverride != null
                    && otaCommissionOverride.compareTo(BigDecimal.ZERO) > 0
                    ? round(otaCommissionOverride)
                    : BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);

            if (otaUsata.signum() > 0) {
                warnings.add("Calcolo parziale: usata commissione OTA dal file (€" + otaUsata
                        + "). Configurare le regole contratto per il calcolo completo.");
            } else {
                warnings.add("Nessuna regola di contratto trovata per l'immobile: usati valori di fallback");
            }

            // Nel fallback l'unico servizio riaddebitato è la commissione OTA: il totale
            // della fattura PM coincide con essa, con IVA scorporata come nel calcolo completo.
            BigDecimal lordoServizi = otaUsata;
            BigDecimal imponibileFatturaPm;
            BigDecimal ivaScorporata;
            if (aliquotaIvaPm.signum() == 0) {
                imponibileFatturaPm = lordoServizi;
                ivaScorporata = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            } else {
                BigDecimal divisore = BigDecimal.ONE.add(aliquotaIvaPm);
                imponibileFatturaPm = lordoServizi.signum() == 0
                        ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                        : lordoServizi.divide(divisore, 2, RoundingMode.HALF_UP);
                ivaScorporata = round(lordoServizi.subtract(imponibileFatturaPm));
            }

            BigDecimal ownerNet = round(grossAmount.subtract(lordoServizi));
            // Ritenuta sul netto proprietario, non sul lordo ospite: la commissione OTA
            // riaddebitata non è reddito del proprietario.
            BigDecimal withholding = round(ownerNet.multiply(aliquotaRitenuta).divide(CENTO));
            log.info("ContrattoCalcolatore - tenant={} property={} canale={} gross={} ota={} ownerNet={} withholding={} (FALLBACK)",
                    tenantId, propertyId, fkCanaleOtaId, grossAmount, otaUsata, ownerNet, withholding);
            return ContrattoCalcoloResult.builder()
                    .grossAmount(grossAmount)
                    .otaCommissionAmount(otaUsata)
                    .cleaningAmount(BigDecimal.ZERO)
                    .pmFeeAmount(BigDecimal.ZERO)
                    .imponibilePm(lordoServizi)
                    .imponibileFatturaPm(imponibileFatturaPm)
                    .ivaScorporata(ivaScorporata)
                    .fatturaPmTotale(lordoServizi)
                    .ownerNetAmount(ownerNet)
                    .withholdingAmount(withholding)
                    .aliquotaRitenuta(round(aliquotaRitenuta))
                    .liquidazioneOwner(round(ownerNet.subtract(withholding)))
                    .regimeFiscalePm(regimePm)
                    .calcoloCompleto(false)
                    .warnings(warnings)
                    // Nessuna regola di contratto: l'unica voce descrivibile è la commissione
                    // OTA arrivata come override (file di import o PM), la provvigione PM non esiste.
                    .pmFeeDescrizione(null)
                    .otaDescrizione(otaUsata.signum() > 0 ? DESCR_OTA_IMPOSTATA : null)
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
        // Voci 'percentuale_netto': si calcolano in un secondo passaggio, quando è noto
        // il totale delle altre voci (la loro base è il lordo meno tutto il resto).
        List<PropertyContractRule> regolePercentualeNetto = new ArrayList<>();

        for (PropertyContractRule rule : applicabili) {
            if (Boolean.TRUE.equals(rule.getIsRemainder())) {
                remainderRule = rule;
                continue;
            }
            if (CALC_MODE_PERCENTUALE_NETTO.equals(rule.getCalcMode())) {
                if (TIPO_COMMISSIONE_PM.equals(rule.getTipo())) {
                    regolePercentualeNetto.add(rule);
                } else {
                    // Ammessa solo sulla commissione PM: su altre voci il calcolo standard
                    // la tratterebbe come importo fisso, con un addebito inventato.
                    warnings.add("Modalità 'percentuale sul netto' non supportata per la voce "
                            + rule.getTipo() + ": voce ignorata nel calcolo");
                    log.warn("ContrattoCalcolatore - regola {} ignorata: percentuale_netto non ammessa per tipo={}",
                            rule.getId(), rule.getTipo());
                }
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

        // 5a. Nessuna regola commissione_ota per il canale ma override presente (CSV o PM):
        //     l'importo è un dato reale, come nel fallback senza regole. Senza questo passo
        //     l'override veniva scartato e l'OTA restava a zero qualunque valore arrivasse.
        //     Prima del passaggio 2, così la base 'percentuale_netto' ne tiene conto.
        if (otaRule == null && otaCommissionOverride != null) {
            BigDecimal v = round(otaCommissionOverride);
            otaAmount = otaAmount.add(v);
            totalNonRemainder = totalNonRemainder.add(v);
        }

        // 5b. PASSAGGIO 2 — voci 'percentuale_netto' (solo commissione PM).
        //     Base = lordo meno tutte le voci del passaggio 1. Il lordo in ingresso è già
        //     al netto della tassa di soggiorno quando è inclusa (lo scorpora il chiamante).
        //     La base si calcola una volta sola: con più regole percentuale_netto tutte
        //     partono dallo stesso importo, altrimenti l'ordine di lettura cambierebbe il totale.
        if (!regolePercentualeNetto.isEmpty()) {
            BigDecimal baseNetto = round(grossAmount.subtract(totalNonRemainder));
            if (baseNetto.signum() < 0) {
                baseNetto = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                warnings.add("I costi superano il lordo: commissione PM a zero");
            }
            for (PropertyContractRule rule : regolePercentualeNetto) {
                BigDecimal v = round(baseNetto.multiply(orZero(rule.getValore())).divide(CENTO));
                pmFeeAmount = pmFeeAmount.add(v);
                totalNonRemainder = totalNonRemainder.add(v);
                log.debug("ContrattoCalcolatore - percentuale_netto: base={} valore={}% importo={}",
                        baseNetto, rule.getValore(), v);
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

        // 9b. Descrizioni leggibili delle regole applicate (dettaglio prenotazione).
        //     Si costruiscono qui e non nel chiamante perché solo a questo punto si sa
        //     quale regola OTA è stata scelta per il canale e se l'importo è stato forzato.
        String pmFeeDescrizione = descrizionePmFee(applicabili, remainderRule);
        String otaDescrizione = descrizioneOta(otaRule, otaAmount, grossAmount, otaCommissionOverride);

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
                .pmFeeDescrizione(pmFeeDescrizione)
                .otaDescrizione(otaDescrizione)
                .build();
    }

    /**
     * Descrizione della regola di commissione PM applicata, es. "Commissione PM (10% sul netto)".
     * null se l'immobile non ha una regola per questa voce.
     */
    private String descrizionePmFee(List<PropertyContractRule> applicabili, PropertyContractRule remainderRule) {
        PropertyContractRule pm = applicabili.stream()
                .filter(r -> TIPO_COMMISSIONE_PM.equals(r.getTipo()))
                .findFirst()
                .orElse(null);
        if (pm == null) return null;
        if (pm == remainderRule || Boolean.TRUE.equals(pm.getIsRemainder())) {
            return "Commissione PM (rimanenza)";
        }
        BigDecimal v = orZero(pm.getValore());
        String mode = pm.getCalcMode() != null ? pm.getCalcMode() : "";
        String dettaglio = switch (mode) {
            case "percentuale", "percentuale_lordo" -> pct(v) + "% sul lordo";
            case CALC_MODE_PERCENTUALE_NETTO -> pct(v) + "% sul netto";
            case "fisso" -> "€" + euro(v) + " fisso";
            case "fisso_per_notte" -> "€" + euro(v) + " per notte";
            case "fisso_per_persona" -> "€" + euro(v) + " per persona";
            default -> null;
        };
        return dettaglio != null ? "Commissione PM (" + dettaglio + ")" : "Commissione PM";
    }

    /**
     * Descrizione della regola di commissione OTA applicata, es. "Commissione OTA (18%)".
     * L'importo forzato si segnala solo quando diverge davvero da quello della regola:
     * il valore già salvato sul booking viene ripassato come override a ogni rilettura,
     * quindi la sola presenza dell'override non significa che sia stato cambiato a mano.
     */
    private String descrizioneOta(PropertyContractRule otaRule, BigDecimal otaApplicata,
                                  BigDecimal grossAmount, BigDecimal override) {
        if (otaRule == null) {
            return override != null && override.signum() > 0
                    ? DESCR_OTA_IMPOSTATA
                    : null;
        }
        BigDecimal v = orZero(otaRule.getValore());
        boolean fisso = "fisso".equals(otaRule.getCalcMode());
        BigDecimal daRegola = fisso ? round(v) : round(grossAmount.multiply(v).divide(CENTO));
        String dettaglio = fisso ? "€" + euro(v) + " fisso" : pct(v) + "%";
        if (round(otaApplicata).compareTo(daRegola) != 0) {
            dettaglio += " — importo forzato €" + euro(otaApplicata);
        }
        return "Commissione OTA (" + dettaglio + ")";
    }

    /** Percentuale senza zeri inutili: 10.00 → "10", 12.50 → "12.5". */
    private String pct(BigDecimal v) {
        return orZero(v).stripTrailingZeros().toPlainString();
    }

    /** Importo in euro con due decimali e virgola decimale. */
    private String euro(BigDecimal v) {
        return String.format(Locale.ITALY, "%.2f", orZero(v));
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
