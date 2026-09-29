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
    private static final String TIPO_PULIZIE = "pulizie";
    private static final String TIPO_CAMBIO_BIANCHERIA = "cambio_biancheria";
    /**
     * Commissione OTA senza regola di contratto, arrivata come override (file di import o PM).
     * Il frontend riconosce "importo impostato" / "importo forzato" per mostrare il ripristino.
     */
    private static final String DESCR_OTA_IMPOSTATA = "Commissione OTA (importo impostato)";
    /** Provvigione PM impostata a mano (pmFeeOverride): nessuna regola la descrive. */
    private static final String DESCR_PM_IMPOSTATA = "Commissione PM (importo impostato)";

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

    /**
     * Split economico con il modello IVA "Barbagallo": le regole di contratto esprimono importi
     * NETTI (imponibile), il sistema aggiunge l'IVA per ottenere il lordo che va in fattura PM.
     * <pre>
     *   voce_lordo = voce_netto × (1 + IVA)            IVA = 22% in RF01, 0 in RF19 (forfettario)
     *   base_pm    = gross − Σ spese nette             (OTA + pulizie + cambio biancheria + extra)
     *   PM_netto   = base_pm × %                       solo 'percentuale_netto'
     *              = gross × %                         'percentuale' / 'percentuale_lordo'
     *   fattura PM = Σ lordi (extra comprese)
     *   netto proprietario = gross − fattura PM
     * </pre>
     * {@code gross} è già al netto della tassa di soggiorno quando è inclusa (la scorpora il chiamante).
     *
     * <p>Override = importo impostato a mano o arrivato dal file (null = dalle regole); una voce
     * con override non ha regola di riferimento (fkRegola*Id = null).
     * <ul>
     *   <li>pulizieOverride / cambioBiancheriaOverride: ciascuno sostituisce solo la sua voce;</li>
     *   <li>cleaningOverride (legacy): totale pulizie + cambio biancheria, tutto su pulizie;
     *       ignorato se arriva uno dei due override separati;</li>
     *   <li>otaCommissionOverride, pmFeeOverride: OTA e provvigione PM.</li>
     * </ul>
     *
     * @param extraImponibili   imponibili delle voci extra in fattura PM: entrano nella base
     *                          'percentuale_netto' e nel totale fattura. Vuota se non ce ne sono.
     * @param overridesDaImport true = gli override sono LORDI presi dal file di import (se ne
     *                          scorpora l'IVA); false = sono NETTI inseriti dal PM (si aggiunge l'IVA)
     */
    public ContrattoCalcoloResult calcola(Integer tenantId,
                                          Integer propertyId,
                                          Integer fkCanaleOtaId,
                                          BigDecimal gross,
                                          BigDecimal otaCommissionOverride,
                                          BigDecimal cleaningOverride,
                                          BigDecimal pulizieOverride,
                                          BigDecimal cambioBiancheriaOverride,
                                          BigDecimal pmFeeOverride,
                                          Integer nights,
                                          Integer guests,
                                          List<BigDecimal> extraImponibili,
                                          boolean overridesDaImport) {
        return calcola(tenantId, propertyId, fkCanaleOtaId, gross, otaCommissionOverride, cleaningOverride,
                pulizieOverride, cambioBiancheriaOverride, pmFeeOverride, nights, guests,
                extraImponibili, overridesDaImport, false);
    }

    /**
     * Come sopra, con la commissione OTA del file eventualmente NETTA.
     *
     * @param otaOverrideNetto true = la commissione OTA del file è netta (canale con
     *                         commissione_ivata=false, es. Airbnb): si aggiunge l'IVA anche se
     *                         overridesDaImport è true. Vale solo per l'OTA: gli altri importi
     *                         del file (pulizie) restano lordi.
     */
    public ContrattoCalcoloResult calcola(Integer tenantId,
                                          Integer propertyId,
                                          Integer fkCanaleOtaId,
                                          BigDecimal gross,
                                          BigDecimal otaCommissionOverride,
                                          BigDecimal cleaningOverride,
                                          BigDecimal pulizieOverride,
                                          BigDecimal cambioBiancheriaOverride,
                                          BigDecimal pmFeeOverride,
                                          Integer nights,
                                          Integer guests,
                                          List<BigDecimal> extraImponibili,
                                          boolean overridesDaImport,
                                          boolean otaOverrideNetto) {
        // Commissione OTA lorda solo se arriva dal file E il canale la dichiara IVA inclusa
        boolean otaDaImportLorda = overridesDaImport && !otaOverrideNetto;
        boolean overrideSeparati = pulizieOverride != null || cambioBiancheriaOverride != null;
        boolean cleaningLegacy = cleaningOverride != null && !overrideSeparati;

        List<String> warnings = new ArrayList<>();
        BigDecimal grossAmount = round(orZero(gross));
        int n = nights != null ? nights : 0;
        int g = guests != null ? guests : 1;

        // 1. Settings tenant
        TenantSettingsDTO settings = tenantSettingsService.getSettings(tenantId);
        String regimePm = settings.getRegimeFiscalePm() != null ? settings.getRegimeFiscalePm() : "RF01";

        // 2. IVA delle voci PM: RF19 forfettario → 0, RF01 → 22%
        BigDecimal aliquotaIvaPm = "RF19".equalsIgnoreCase(regimePm) ? BigDecimal.ZERO : ALIQUOTA_IVA_RF01;
        BigDecimal unoPiuIva = BigDecimal.ONE.add(aliquotaIvaPm);

        // 2b. Aliquota ritenuta in base al primo/secondo immobile dell'owner.
        //     primo immobile → ritenuta primaria (es. 21.00), dal secondo → ritenuta secondaria (es. 26.00).
        //     Property assente (o propertyId null) → si applica la ritenuta primaria come fallback.
        Property property = propertyId != null ? propertyDAO.findById(propertyId).orElse(null) : null;
        boolean primoImmobile = property == null || Boolean.TRUE.equals(property.getPrimoImmobile());
        BigDecimal aliquotaRitenuta = primoImmobile
                ? orZero(settings.getWithholdingRatePrimary())
                : orZero(settings.getWithholdingRateSecondary()); // es. 21.00 / 26.00

        // 2c. Voci extra in fattura PM: imponibili dal chiamante, lordi riga per riga (ogni riga
        //     extra salva il suo lordo arrotondato: sommare i lordi evita scarti di un centesimo).
        List<BigDecimal> extra = extraImponibili != null ? extraImponibili : List.of();
        BigDecimal extraNetti = round(extra.stream().map(this::orZero).reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal extraLordi = round(extra.stream().map(v -> lordoDaNetto(orZero(v), unoPiuIva))
                .reduce(BigDecimal.ZERO, BigDecimal::add));

        // 3. Regole del contratto
        List<PropertyContractRule> rules = propertyId != null
                ? contractRuleDAO.findByPropertyId(propertyId)
                : List.of();

        Voce ota;
        Voce pulizie;
        Voce cambioBiancheria;
        Voce pm;
        Integer fkRegolaOtaId = null;
        Integer fkRegolaPulizieId = null;
        Integer fkRegolaCambioBiancheriaId = null;
        Integer fkRegolaPmId = null;
        String otaDescrizione;
        String pmFeeDescrizione;
        boolean calcoloCompleto;
        BigDecimal altriNonRimanenza = BigDecimal.ZERO; // provvigione_proprietario non rimanenza
        PropertyContractRule remainderRule = null;

        if (rules.isEmpty()) {
            // 9. Nessuna regola → fallback.
            //    La commissione OTA del file di import, se presente, è un dato reale del canale:
            //    usarla dà un netto proprietario molto più vicino al vero rispetto a ignorarla.
            //    Pulizie, cambio biancheria e provvigione PM valgono solo se impostati a mano:
            //    senza regole non sono deducibili.
            ota = otaCommissionOverride != null && otaCommissionOverride.signum() > 0
                    ? daOverride(otaCommissionOverride, otaDaImportLorda, unoPiuIva) : Voce.ZERO;
            pulizie = cleaningLegacy ? daOverride(cleaningOverride, overridesDaImport, unoPiuIva)
                    : pulizieOverride != null ? daOverride(pulizieOverride, overridesDaImport, unoPiuIva)
                    : Voce.ZERO;
            cambioBiancheria = !cleaningLegacy && cambioBiancheriaOverride != null
                    ? daOverride(cambioBiancheriaOverride, overridesDaImport, unoPiuIva) : Voce.ZERO;
            pm = pmFeeOverride != null ? daOverride(pmFeeOverride, overridesDaImport, unoPiuIva) : Voce.ZERO;

            if (ota.lordo().signum() > 0) {
                warnings.add("Calcolo parziale: usata commissione OTA dal file (€" + ota.lordo()
                        + "). Configurare le regole contratto per il calcolo completo.");
            } else {
                warnings.add("Nessuna regola di contratto trovata per l'immobile: usati valori di fallback");
            }
            // Nessuna regola di contratto: sono descrivibili solo le voci arrivate come
            // override (OTA da file o PM; provvigione PM impostata a mano).
            pmFeeDescrizione = pm.lordo().signum() > 0 ? DESCR_PM_IMPOSTATA : null;
            otaDescrizione = ota.lordo().signum() > 0 ? DESCR_OTA_IMPOSTATA : null;
            calcoloCompleto = false;
        } else {
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

            // 5. PASSAGGIO 1 — importi NETTI dalle regole (il valore della regola è già netto)
            BigDecimal otaNetto = BigDecimal.ZERO;
            BigDecimal pulizieNetto = BigDecimal.ZERO;
            BigDecimal cambioNetto = BigDecimal.ZERO;
            BigDecimal pmNetto = BigDecimal.ZERO;
            // Voci 'percentuale_netto': nel passaggio 2, quando sono note le spese nette.
            List<PropertyContractRule> regolePercentualeNetto = new ArrayList<>();

            for (PropertyContractRule rule : applicabili) {
                if (Boolean.TRUE.equals(rule.getIsRemainder())) {
                    remainderRule = rule;
                    continue;
                }
                // Voce impostata a mano: le sue regole non si applicano (l'importo arriva dopo
                // il ciclo). Per la PM vale anche per 'percentuale_netto'.
                if (pmFeeOverride != null && TIPO_COMMISSIONE_PM.equals(rule.getTipo())) {
                    continue;
                }
                if ((cleaningLegacy || pulizieOverride != null) && TIPO_PULIZIE.equals(rule.getTipo())) {
                    continue;
                }
                if ((cleaningLegacy || cambioBiancheriaOverride != null)
                        && TIPO_CAMBIO_BIANCHERIA.equals(rule.getTipo())) {
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
                    case TIPO_PULIZIE -> {
                        pulizieNetto = pulizieNetto.add(round(calcStandard(rule.getCalcMode(), valore, grossAmount, n, g)));
                        if (fkRegolaPulizieId == null) fkRegolaPulizieId = rule.getId();
                    }
                    case TIPO_CAMBIO_BIANCHERIA -> {
                        cambioNetto = cambioNetto.add(round(calcStandard(rule.getCalcMode(), valore, grossAmount, n, g)));
                        if (fkRegolaCambioBiancheriaId == null) fkRegolaCambioBiancheriaId = rule.getId();
                    }
                    case "commissione_ota" -> {
                        // applica solo la regola OTA scelta; con override l'importo arriva dopo il ciclo
                        if (rule != otaRule || otaCommissionOverride != null) continue;
                        otaNetto = otaNetto.add(importoOtaDaRegola(rule, grossAmount));
                    }
                    case TIPO_COMMISSIONE_PM -> {
                        BigDecimal v;
                        if ("fisso".equals(rule.getCalcMode())) {
                            v = round(valore);
                        } else if ("fisso_per_notte".equals(rule.getCalcMode())) {
                            v = round(valore.multiply(BigDecimal.valueOf(n)));
                        } else { // percentuale / percentuale_lordo: sul lordo
                            v = round(grossAmount.multiply(valore).divide(CENTO));
                        }
                        pmNetto = pmNetto.add(v);
                        if (fkRegolaPmId == null) fkRegolaPmId = rule.getId();
                    }
                    case "provvigione_proprietario" -> {
                        // se non è rimanenza concorre comunque al controllo della rimanenza
                        altriNonRimanenza = altriNonRimanenza.add(
                                round(calcStandard(rule.getCalcMode(), valore, grossAmount, n, g)));
                    }
                    default -> { /* tipo non gestito: ignora */ }
                }
            }

            // 5a. Override: sostituiscono la voce, senza regola di riferimento.
            //     OTA: vale anche senza regola commissione_ota per il canale (dato reale da file o PM).
            ota = otaCommissionOverride != null
                    ? daOverride(otaCommissionOverride, otaDaImportLorda, unoPiuIva)
                    : daNetto(otaNetto, unoPiuIva);
            if (cleaningLegacy) {
                // Legacy: il totale va tutto su pulizie, il cambio biancheria resta a zero
                pulizie = daOverride(cleaningOverride, overridesDaImport, unoPiuIva);
                cambioBiancheria = Voce.ZERO;
                fkRegolaPulizieId = null;
                fkRegolaCambioBiancheriaId = null;
            } else {
                pulizie = pulizieOverride != null
                        ? daOverride(pulizieOverride, overridesDaImport, unoPiuIva)
                        : daNetto(pulizieNetto, unoPiuIva);
                if (pulizieOverride != null) fkRegolaPulizieId = null;
                cambioBiancheria = cambioBiancheriaOverride != null
                        ? daOverride(cambioBiancheriaOverride, overridesDaImport, unoPiuIva)
                        : daNetto(cambioNetto, unoPiuIva);
                if (cambioBiancheriaOverride != null) fkRegolaCambioBiancheriaId = null;
            }

            // 5b. PASSAGGIO 2 — PM 'percentuale_netto' (schema Barbagallo):
            //     base_pm = gross − Σ spese nette (OTA, pulizie, cambio biancheria, extra).
            //     La base si calcola una volta sola: con più regole percentuale_netto tutte
            //     partono dallo stesso importo, altrimenti l'ordine di lettura cambierebbe il totale.
            if (pmFeeOverride != null) {
                pm = daOverride(pmFeeOverride, overridesDaImport, unoPiuIva);
                fkRegolaPmId = null;
            } else {
                if (!regolePercentualeNetto.isEmpty()) {
                    BigDecimal speseNette = ota.netto().add(pulizie.netto())
                            .add(cambioBiancheria.netto()).add(extraNetti);
                    BigDecimal basePm = round(grossAmount.subtract(speseNette));
                    if (basePm.signum() < 0) {
                        basePm = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
                        warnings.add("I costi superano il lordo: commissione PM a zero");
                    }
                    for (PropertyContractRule rule : regolePercentualeNetto) {
                        BigDecimal v = round(basePm.multiply(orZero(rule.getValore())).divide(CENTO));
                        pmNetto = pmNetto.add(v);
                        if (fkRegolaPmId == null) fkRegolaPmId = rule.getId();
                        log.debug("ContrattoCalcolatore - percentuale_netto: base={} valore={}% netto={}",
                                basePm, rule.getValore(), v);
                    }
                }
                pm = daNetto(pmNetto, unoPiuIva);
            }

            // 9b. Descrizioni leggibili delle regole applicate (dettaglio prenotazione), sui NETTI:
            //     solo a questo punto si sa quale regola OTA è stata scelta e se è stata forzata.
            pmFeeDescrizione = pmFeeOverride != null
                    ? DESCR_PM_IMPOSTATA
                    : descrizionePmFee(applicabili, remainderRule);
            otaDescrizione = descrizioneOta(otaRule, ota.netto(), grossAmount, otaCommissionOverride);
            // FK regola OTA solo se il netto è davvero quello della regola: con un importo
            // forzato (PM o file) che diverge, la riga split non viene dalla regola.
            fkRegolaOtaId = otaRule != null
                    && ota.netto().compareTo(importoOtaDaRegola(otaRule, grossAmount)) == 0
                    ? otaRule.getId() : null;
            calcoloCompleto = remainderRule != null;
        }

        // 6. Fattura PM (lordi) e imponibile (netti), extra comprese
        BigDecimal cleaningLordo = round(pulizie.lordo().add(cambioBiancheria.lordo()));
        BigDecimal fatturaPmTotale = round(ota.lordo().add(cleaningLordo).add(pm.lordo()).add(extraLordi));
        BigDecimal imponibileFatturaPm = round(ota.netto().add(pulizie.netto())
                .add(cambioBiancheria.netto()).add(pm.netto()).add(extraNetti));
        BigDecimal ivaFatturaPm = round(fatturaPmTotale.subtract(imponibileFatturaPm));

        // 7. Rimanenza (controllo): il proprietario riceve quel che resta dei lordi in fattura
        if (!rules.isEmpty()) {
            BigDecimal rimanenza = grossAmount.subtract(fatturaPmTotale).subtract(altriNonRimanenza);
            if (remainderRule != null && rimanenza.signum() < 0) {
                warnings.add("I costi superano il lordo della prenotazione");
            }
            if (remainderRule == null) {
                warnings.add("Nessuna voce impostata come rimanenza per l'immobile");
            }
        }

        // 8. Netto proprietario, ritenuta, liquidazione
        BigDecimal ownerNet = round(grossAmount.subtract(fatturaPmTotale));
        // Ritenuta sul netto proprietario, non sul lordo ospite: i servizi PM riaddebitati
        // non sono reddito del proprietario.
        BigDecimal withholding = round(ownerNet.multiply(aliquotaRitenuta).divide(CENTO));
        BigDecimal liquidazione = round(ownerNet.subtract(withholding));

        // Legacy: FK unica della voce aggregata (prima regola applicata, come prima)
        Integer fkRegolaCleaningId = fkRegolaPulizieId != null ? fkRegolaPulizieId : fkRegolaCambioBiancheriaId;

        // 10. Log
        log.info("ContrattoCalcolatore - tenant={} property={} canale={} gross={} fatturaPm={} (imp={} iva={}) ownerNet={} withholding={}{}",
                tenantId, propertyId, fkCanaleOtaId, grossAmount, fatturaPmTotale, imponibileFatturaPm,
                ivaFatturaPm, ownerNet, withholding, rules.isEmpty() ? " (FALLBACK)" : "");

        return ContrattoCalcoloResult.builder()
                .grossAmount(grossAmount)
                .otaCommissionAmount(ota.lordo())
                .cleaningAmount(cleaningLordo)
                .pulizieAmount(pulizie.lordo())
                .cambioBiancheriaAmount(cambioBiancheria.lordo())
                .pmFeeAmount(pm.lordo())
                .otaImponibile(ota.netto())
                .pulizieImponibile(pulizie.netto())
                .cambioBiancheriaImponibile(cambioBiancheria.netto())
                .pmImponibile(pm.netto())
                .aliquotaIvaPm(round(aliquotaIvaPm.multiply(CENTO)))
                .imponibilePm(fatturaPmTotale)
                .imponibileFatturaPm(imponibileFatturaPm)
                .ivaScorporata(ivaFatturaPm)
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
                .fkRegolaOtaId(fkRegolaOtaId)
                .fkRegolaCleaningId(fkRegolaCleaningId)
                .fkRegolaPulizieId(fkRegolaPulizieId)
                .fkRegolaCambioBiancheriaId(fkRegolaCambioBiancheriaId)
                .fkRegolaPmId(fkRegolaPmId)
                .build();
    }

    /** Importo di una voce: netto (imponibile) e lordo (con IVA). */
    private record Voce(BigDecimal netto, BigDecimal lordo) {
        static final Voce ZERO = new Voce(BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP));
    }

    /** Voce dal netto (regola o override del PM): lordo = netto × (1 + IVA). */
    private Voce daNetto(BigDecimal netto, BigDecimal unoPiuIva) {
        BigDecimal nt = round(netto);
        return new Voce(nt, lordoDaNetto(nt, unoPiuIva));
    }

    /**
     * Voce da un override: dal file di import è già un LORDO (se ne scorpora l'IVA), dal PM è
     * un NETTO (si aggiunge l'IVA).
     */
    private Voce daOverride(BigDecimal override, boolean daImport, BigDecimal unoPiuIva) {
        if (!daImport) return daNetto(override, unoPiuIva);
        BigDecimal lordo = round(override);
        BigDecimal netto = unoPiuIva.compareTo(BigDecimal.ONE) == 0
                ? lordo
                : lordo.divide(unoPiuIva, 2, RoundingMode.HALF_UP);
        return new Voce(netto, lordo);
    }

    private BigDecimal lordoDaNetto(BigDecimal netto, BigDecimal unoPiuIva) {
        return round(netto.multiply(unoPiuIva));
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
        BigDecimal daRegola = importoOtaDaRegola(otaRule, grossAmount);
        String dettaglio = fisso ? "€" + euro(v) + " fisso" : pct(v) + "%";
        if (round(otaApplicata).compareTo(daRegola) != 0) {
            dettaglio += " — importo forzato €" + euro(otaApplicata);
        }
        return "Commissione OTA (" + dettaglio + ")";
    }

    /** Commissione OTA come la calcolerebbe la regola, senza override (stessa logica del passaggio 1). */
    private BigDecimal importoOtaDaRegola(PropertyContractRule otaRule, BigDecimal grossAmount) {
        BigDecimal v = orZero(otaRule.getValore());
        return "fisso".equals(otaRule.getCalcMode())
                ? round(v)
                : round(grossAmount.multiply(v).divide(CENTO));
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
