package it.gavia.sostitutoincloud.service;

import it.gavia.sostitutoincloud.dao.CodiceTributoDAO;
import it.gavia.sostitutoincloud.dao.F24RecordDAO;
import it.gavia.sostitutoincloud.dao.FiscalDocumentDAO;
import it.gavia.sostitutoincloud.dao.TenantSettingsDAO;
import it.gavia.sostitutoincloud.dao.WithholdingLedgerDAO;
import it.gavia.sostitutoincloud.dto.fiscal.CreditoCompensazioneRequest;
import it.gavia.sostitutoincloud.dto.fiscal.CreditoDisponibileDTO;
import it.gavia.sostitutoincloud.dto.fiscal.F24GenerazioneResultDTO;
import it.gavia.sostitutoincloud.dto.fiscal.F24RecordDTO;
import it.gavia.sostitutoincloud.model.CodiceTributo;
import it.gavia.sostitutoincloud.model.F24Record;
import it.gavia.sostitutoincloud.model.FiscalDocument;
import it.gavia.sostitutoincloud.model.TenantSettings;
import it.gavia.sostitutoincloud.model.WithholdingLedger;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Generazione e gestione dei modelli F24 per il versamento delle ritenute d'acconto.
 * Un F24 aggrega tutte le ritenute "da_versare" di un periodo (mese/anno) sotto il codice tributo 1919,
 * con scadenza il giorno 16 del mese successivo.
 */
@Service
@Log4j2
public class F24Service {

    private static final String CODICE_TRIBUTO_RITENUTE = "1919";
    private static final String STATO_DA_VERSARE = "da_versare";
    private static final String STATO_VERSATA = "versata";
    private static final String STATO_READY = "ready";
    private static final String STATO_PAID = "paid";
    private static final String STATO_SENT = "sent";
    private static final String STATO_CREDITO = "credito_imposta";
    private static final String STATO_COMPENSATO = "compensato";
    private static final String CODICE_TRIBUTO_CREDITO_DEFAULT = "6782";

    private final F24RecordDAO f24RecordDAO;
    private final WithholdingLedgerDAO withholdingLedgerDAO;
    private final WithholdingLedgerService withholdingLedgerService;
    private final CodiceTributoDAO codiceTributoDAO;
    private final AuditService auditService;
    private final FiscalDocumentDAO fiscalDocumentDAO;
    private final TenantSettingsDAO tenantSettingsDAO;

    public F24Service(F24RecordDAO f24RecordDAO,
                      WithholdingLedgerDAO withholdingLedgerDAO,
                      WithholdingLedgerService withholdingLedgerService,
                      CodiceTributoDAO codiceTributoDAO,
                      AuditService auditService,
                      FiscalDocumentDAO fiscalDocumentDAO,
                      TenantSettingsDAO tenantSettingsDAO) {
        this.fiscalDocumentDAO = fiscalDocumentDAO;
        this.tenantSettingsDAO = tenantSettingsDAO;
        this.f24RecordDAO = f24RecordDAO;
        this.withholdingLedgerDAO = withholdingLedgerDAO;
        this.withholdingLedgerService = withholdingLedgerService;
        this.codiceTributoDAO = codiceTributoDAO;
        this.auditService = auditService;
    }

    public F24GenerazioneResultDTO generaF24(Integer tenantId, Integer anno, Integer mese) {
        // 1. Un solo F24 per tenant/periodo. Se esiste già:
        //    - pagato   → intoccabile
        //    - altrimenti → si ricalcola, agganciando le ritenute del periodo non ancora incluse
        //      (es. prenotazioni importate dopo la prima generazione), senza crearne un secondo.
        List<F24Record> esistenti = f24RecordDAO.findByTenantAndPeriodo(tenantId, anno, mese);
        if (!esistenti.isEmpty()) {
            F24Record esistente = esistenti.get(0);
            if (STATO_PAID.equals(esistente.getStato())) {
                throw new IllegalStateException("F24 già pagato per periodo " + mese + "/" + anno
                        + " — impossibile modificare");
            }
            log.info("F24Service.generaF24() - F24 {} già presente per {}/{} (stato={}): ricalcolo",
                    esistente.getId(), mese, anno, esistente.getStato());
            return ricalcola(tenantId, esistente.getId());
        }

        // 2. Ritenute da versare del periodo
        List<WithholdingLedger> ritenute = withholdingLedgerDAO.findByTenantAndPeriodo(tenantId, anno, mese).stream()
                .filter(w -> STATO_DA_VERSARE.equals(w.getStato()))
                .collect(Collectors.toList());

        // 3. Nessuna ritenuta → niente da versare
        if (ritenute.isEmpty()) {
            throw new IllegalArgumentException("Nessuna ritenuta da versare per il periodo " + mese + "/" + anno);
        }

        // 4. Totale
        BigDecimal totale = ritenute.stream()
                .map(WithholdingLedger::getRitenutaAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // 5. Scadenza: giorno 16 del mese successivo
        LocalDate deadline = LocalDate.of(anno, mese, 16).plusMonths(1);

        // 6. Codice tributo 1919
        CodiceTributo codiceTributo = codiceTributoDAO.findByCodice(CODICE_TRIBUTO_RITENUTE)
                .orElseThrow(() -> new IllegalStateException(
                        "Codice tributo " + CODICE_TRIBUTO_RITENUTE + " non configurato"));

        // 7. Crea e salva F24.
        // NB: period rispetta il formato e il CHECK constraint dello schema (YYYY-MM), es. "2026-06".
        F24Record record = F24Record.builder()
                .fkTenantId(tenantId)
                .fkCodiceTributoId(codiceTributo.getId())
                .periodoMese(mese)
                .periodoAnno(anno)
                .referenceYear(anno)
                .totalAmount(totale)
                .withholdingsCount(ritenute.size())
                .stato(STATO_READY)
                .deadlineDate(deadline)
                .period(String.format("%d-%02d", anno, mese))
                .build();
        F24Record saved = f24RecordDAO.insert(record);
        // Nessun credito compensato alla creazione: saldo = totale ritenute
        f24RecordDAO.updateCredito(saved.getId(), BigDecimal.ZERO, null, null, totale);

        // 8. Marca le ritenute come versate e collega l'F24
        for (WithholdingLedger riga : ritenute) {
            withholdingLedgerDAO.updateF24Record(riga.getId(), saved.getId());
            withholdingLedgerDAO.updateStato(riga.getId(), STATO_VERSATA);
        }

        // 9. Audit
        auditService.log("f24.generate", "F24", saved.getId(),
                "Generato F24 periodo " + mese + "/" + anno
                        + " importo €" + totale
                        + " (" + ritenute.size() + " ritenute)");

        log.info("F24Service.generaF24() - tenantId={} periodo={}/{} importo={} ritenute={}",
                tenantId, mese, anno, totale, ritenute.size());

        // 10. Risultato con dettaglio ritenute collegate e crediti compensabili
        return dettaglio(tenantId, saved.getId());
    }

    /**
     * Aggancia all'F24 esistente le ritenute 'da_versare' dello stesso periodo non ancora incluse
     * e ne ricalcola il totale. Consentito solo se l'F24 non è pagato.
     */
    public F24GenerazioneResultDTO ricalcola(Integer tenantId, Integer f24Id) {
        // 1-2. Carica F24 e verifica appartenenza al tenant
        F24Record f24 = f24RecordDAO.findById(f24Id)
                .filter(r -> tenantId.equals(r.getFkTenantId()))
                .orElseThrow(() -> new java.util.NoSuchElementException("F24 non trovato: id=" + f24Id));

        // 3. Non modificabile se già pagato
        if (STATO_PAID.equals(f24.getStato())) {
            throw new IllegalStateException("F24 già pagato — impossibile modificare");
        }

        Integer mese = f24.getPeriodoMese();
        Integer anno = f24.getPeriodoAnno();

        // 4. Ritenute nuove del periodo non ancora agganciate
        List<WithholdingLedger> nuove = withholdingLedgerDAO.findDaVersareByPeriodo(tenantId, mese, anno);
        if (nuove.isEmpty()) {
            throw new IllegalArgumentException(
                    "Nessuna ritenuta nuova da aggiungere per il periodo " + mese + "/" + anno);
        }

        // 5. Aggancia e marca come versate
        for (WithholdingLedger riga : nuove) {
            withholdingLedgerDAO.updateF24Record(riga.getId(), f24Id);
            withholdingLedgerDAO.updateStato(riga.getId(), STATO_VERSATA);
        }

        // 6. Ricalcola totale su TUTTE le ritenute agganciate all'F24
        List<WithholdingLedger> agganciate = withholdingLedgerDAO.findByF24Record(f24Id).stream()
                .filter(w -> STATO_VERSATA.equals(w.getStato()))
                .collect(Collectors.toList());
        BigDecimal nuovoTotale = agganciate.stream()
                .map(WithholdingLedger::getRitenutaAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int nuovoCount = agganciate.size();
        f24RecordDAO.updateTotale(f24Id, nuovoTotale, nuovoCount);
        riallineaSaldo(f24Id);

        // 7. Audit
        auditService.log("f24.ricalcola", "F24", f24Id,
                "Aggiunte " + nuove.size() + " ritenute al F24 periodo " + mese + "/" + anno
                        + " nuovo totale €" + nuovoTotale);

        log.info("F24Service.ricalcola() - f24Id={} nuoveRitenute={} nuovoTotale={}",
                f24Id, nuove.size(), nuovoTotale);

        // 8. Ricarica F24 aggiornato e restituisce il DTO
        return dettaglio(tenantId, f24Id);
    }

    /** Totale dell'F24 dalle ritenute ancora agganciate (usato dallo storno da NDC). */
    public void ricalcolaTotale(Integer f24Id) {
        f24RecordDAO.ricalcolaTotale(f24Id);
        riallineaSaldo(f24Id);
        log.info("F24Service.ricalcolaTotale() - f24Id={}", f24Id);
    }

    // ── crediti d'imposta da NDC (migration 027) ──────────────────────────────

    /**
     * Compensa crediti d'imposta da NDC nell'F24 (riga 2 del modello, codice tributo dai
     * tenant_settings). Sostituisce eventuali compensazioni precedenti dello stesso F24:
     * lista vuota = toglie i crediti. Compensazione totale → la riga diventa 'compensato';
     * parziale → la riga di credito resta col residuo e una nuova riga 'compensato'
     * (fk_ledger_origine_id) registra l'importo usato. Saldo netto mai negativo.
     *
     * @throws NoSuchElementException   F24 inesistente o di altro tenant (404)
     * @throws IllegalStateException    F24 pagato o inviato (400)
     * @throws IllegalArgumentException compensazione non valida (400)
     */
    @Transactional
    public F24GenerazioneResultDTO applicaCrediti(Integer tenantId, Integer utenteId, Integer f24Id,
                                                  List<CreditoCompensazioneRequest> compensazioni) {
        F24Record f24 = f24RecordDAO.findById(f24Id)
                .filter(r -> tenantId.equals(r.getFkTenantId()))
                .orElseThrow(() -> new NoSuchElementException("F24 non trovato: id=" + f24Id));
        if (STATO_PAID.equals(f24.getStato()) || STATO_SENT.equals(f24.getStato())) {
            throw new IllegalStateException("F24 già pagato: impossibile modificare i crediti");
        }

        // Le compensazioni precedenti tornano disponibili prima di applicare le nuove
        rilasciaCompensazioni(f24Id);

        List<CreditoCompensazioneRequest> richieste = compensazioni != null ? compensazioni : List.of();
        Map<Integer, WithholdingLedger> righe = new HashMap<>();
        Set<Integer> visti = new HashSet<>();
        BigDecimal totaleCompensato = BigDecimal.ZERO;
        for (CreditoCompensazioneRequest c : richieste) {
            if (c.getLedgerId() == null || !visti.add(c.getLedgerId())) {
                throw new IllegalArgumentException("Credito non valido o ripetuto: " + c.getLedgerId());
            }
            WithholdingLedger riga = withholdingLedgerDAO.findById(c.getLedgerId())
                    .filter(w -> tenantId.equals(w.getFkTenantId()))
                    .orElseThrow(() -> new IllegalArgumentException("Credito non trovato: id=" + c.getLedgerId()));
            if (!STATO_CREDITO.equals(riga.getStato())) {
                throw new IllegalArgumentException("La riga " + riga.getId() + " non è un credito disponibile");
            }
            BigDecimal importo = c.getImportoUsato() != null
                    ? c.getImportoUsato().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO;
            BigDecimal disponibile = nz(riga.getRitenutaAmount()).abs();
            if (importo.signum() <= 0) {
                throw new IllegalArgumentException("Importo da compensare deve essere maggiore di zero");
            }
            if (importo.compareTo(disponibile) > 0) {
                throw new IllegalArgumentException("Importo " + importo + " superiore al credito disponibile ("
                        + disponibile + ")");
            }
            righe.put(riga.getId(), riga);
            totaleCompensato = totaleCompensato.add(importo);
        }
        BigDecimal totaleRitenute = nz(f24.getTotalAmount());
        if (totaleCompensato.compareTo(totaleRitenute) > 0) {
            throw new IllegalArgumentException("Il credito compensato (€" + totaleCompensato
                    + ") supera le ritenute del periodo (€" + totaleRitenute + ")");
        }

        Integer annoCredito = null;
        for (CreditoCompensazioneRequest c : richieste) {
            WithholdingLedger riga = righe.get(c.getLedgerId());
            BigDecimal importo = c.getImportoUsato().setScale(2, RoundingMode.HALF_UP);
            if (importo.compareTo(nz(riga.getRitenutaAmount()).abs()) == 0) {
                withholdingLedgerDAO.compensaTotale(riga.getId(), f24Id);
            } else {
                // Spezzamento: residuo sulla riga di credito, nuova riga per la parte usata
                withholdingLedgerDAO.updateRitenutaAmount(riga.getId(), riga.getRitenutaAmount().add(importo));
                withholdingLedgerDAO.insert(WithholdingLedger.builder()
                        .fkTenantId(tenantId)
                        .fkOwnerId(riga.getFkOwnerId())
                        .fkBookingId(riga.getFkBookingId())
                        .fkFiscalDocumentId(riga.getFkFiscalDocumentId())
                        .periodoMese(f24.getPeriodoMese())
                        .periodoAnno(f24.getPeriodoAnno())
                        .canoneLocazione(BigDecimal.ZERO)
                        .aliquotaRitenuta(riga.getAliquotaRitenuta())
                        .ritenutaAmount(importo.negate())
                        .dataEvento(LocalDate.now())
                        .stato(STATO_COMPENSATO)
                        .fkF24RecordId(f24Id)
                        .fkNdcId(riga.getFkNdcId())
                        .fkLedgerOrigineId(riga.getId())
                        .build());
            }
            Integer anno = annoNdc(riga);
            if (anno != null && (annoCredito == null || anno > annoCredito)) {
                annoCredito = anno;
            }
        }

        boolean conCrediti = totaleCompensato.signum() > 0;
        String codice = conCrediti ? codiceTributoCredito(tenantId) : null;
        if (conCrediti && annoCredito == null) {
            annoCredito = LocalDate.now().getYear();
        }
        BigDecimal saldo = totaleRitenute.subtract(totaleCompensato);
        f24RecordDAO.updateCredito(f24Id, totaleCompensato, codice, conCrediti ? annoCredito : null, saldo);

        auditService.log("f24.crediti", "F24", f24Id, "Compensati crediti d'imposta €" + totaleCompensato
                + " su F24 " + f24.getPeriod() + " (saldo €" + saldo + ")");
        log.info("F24Service.applicaCrediti() - f24={} totaleCompensato={} saldoNetto={} utente={}",
                f24Id, totaleCompensato, saldo, utenteId);
        return dettaglio(tenantId, f24Id);
    }

    /** Crediti d'imposta da NDC ancora compensabili del tenant. */
    public List<CreditoDisponibileDTO> creditiDisponibili(Integer tenantId) {
        return withholdingLedgerDAO.findCreditiDisponibili(tenantId).stream()
                .map(r -> {
                    FiscalDocument ndc = r.getFkNdcId() != null
                            ? fiscalDocumentDAO.findById(r.getFkNdcId()).orElse(null) : null;
                    return CreditoDisponibileDTO.builder()
                            .ledgerId(r.getId())
                            .ndcDocumentNumber(ndc != null ? ndc.getDocumentNumber() : null)
                            .ndcDataEmissione(ndc != null ? ndc.getIssueDate() : null)
                            .importoCredito(nz(r.getRitenutaAmount()).abs())
                            .annoRiferimento(ndc != null && ndc.getIssueDate() != null
                                    ? ndc.getIssueDate().getYear() : null)
                            .build();
                })
                .collect(Collectors.toList());
    }

    /**
     * Annulla le compensazioni dell'F24: le righe spezzate si ricongiungono alla riga di
     * credito d'origine (se ancora credito), le compensazioni totali tornano 'credito_imposta'.
     */
    private void rilasciaCompensazioni(Integer f24Id) {
        for (WithholdingLedger c : withholdingLedgerDAO.findCompensatiByF24(f24Id)) {
            WithholdingLedger origine = c.getFkLedgerOrigineId() != null
                    ? withholdingLedgerDAO.findById(c.getFkLedgerOrigineId()).orElse(null) : null;
            if (origine != null && STATO_CREDITO.equals(origine.getStato())) {
                withholdingLedgerDAO.updateRitenutaAmount(origine.getId(),
                        nz(origine.getRitenutaAmount()).add(nz(c.getRitenutaAmount())));
                withholdingLedgerDAO.deleteById(c.getId());
            } else {
                // compensazione totale, oppure origine già usata altrove: la riga resta un credito a sé
                withholdingLedgerDAO.ripristinaCredito(c.getId());
            }
        }
    }

    /**
     * Saldo netto dopo una variazione del totale ritenute. Se le ritenute scendono sotto il
     * credito compensato, i crediti vengono rilasciati (saldo mai negativo).
     */
    private void riallineaSaldo(Integer f24Id) {
        F24Record f = f24RecordDAO.findById(f24Id).orElse(null);
        if (f == null) return;
        BigDecimal totale = nz(f.getTotalAmount());
        BigDecimal credito = nz(f.getImportoCredito());
        if (credito.compareTo(totale) > 0) {
            rilasciaCompensazioni(f24Id);
            f24RecordDAO.updateCredito(f24Id, BigDecimal.ZERO, null, null, totale);
            log.warn("F24Service - F24 {}: ritenute (€{}) sotto il credito compensato (€{}), crediti rilasciati",
                    f24Id, totale, credito);
        } else {
            f24RecordDAO.updateCredito(f24Id, credito, f.getCodiceTributoCreditoImposta(), f.getAnnoCredito(),
                    totale.subtract(credito));
        }
    }

    private Integer annoNdc(WithholdingLedger riga) {
        return riga.getFkNdcId() == null ? null : fiscalDocumentDAO.findById(riga.getFkNdcId())
                .map(FiscalDocument::getIssueDate).map(LocalDate::getYear).orElse(null);
    }

    private String codiceTributoCredito(Integer tenantId) {
        return tenantSettingsDAO.findByTenantId(tenantId)
                .map(TenantSettings::getCodiceTributoCreditoImposta)
                .filter(c -> !c.isBlank())
                .orElse(CODICE_TRIBUTO_CREDITO_DEFAULT);
    }

    /** Dettaglio F24 con ritenute, crediti compensati e crediti ancora disponibili. */
    private F24GenerazioneResultDTO dettaglio(Integer tenantId, Integer f24Id) {
        F24Record f = f24RecordDAO.findById(f24Id)
                .orElseThrow(() -> new NoSuchElementException("F24 non trovato: id=" + f24Id));
        return F24GenerazioneResultDTO.builder()
                .f24RecordId(f.getId())
                .periodoMese(f.getPeriodoMese())
                .periodoAnno(f.getPeriodoAnno())
                .totaleRitenute(f.getTotalAmount())
                .numeroRitenute(f.getWithholdingsCount())
                .scadenza(f.getDeadlineDate())
                .stato(f.getStato())
                .ritenute(withholdingLedgerService.findDettaglioByF24Record(tenantId, f.getId()))
                .crediti(creditiDisponibili(tenantId))
                .importoCredito(nz(f.getImportoCredito()))
                .codiceTributoCreditoImposta(f.getCodiceTributoCreditoImposta())
                .annoCredito(f.getAnnoCredito())
                .saldoNetto(f.getSaldoNetto() != null ? f.getSaldoNetto()
                        : nz(f.getTotalAmount()).subtract(nz(f.getImportoCredito())))
                .build();
    }

    private BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    public List<F24RecordDTO> findByTenant(Integer tenantId) {
        Map<Integer, String> codiciById = codiceTributoDAO.findAll().stream()
                .collect(Collectors.toMap(CodiceTributo::getId, CodiceTributo::getCodice));
        List<F24RecordDTO> result = f24RecordDAO.findByTenant(tenantId).stream()
                .map(f -> toDTO(f, codiciById.get(f.getFkCodiceTributoId())))
                .collect(Collectors.toList());
        log.info("F24Service.findByTenant() - tenantId={} risultati={}", tenantId, result.size());
        return result;
    }

    public F24GenerazioneResultDTO findDettaglio(Integer tenantId, Integer f24Id) {
        F24Record f = f24RecordDAO.findById(f24Id)
                .filter(r -> tenantId.equals(r.getFkTenantId()))
                .orElseThrow(() -> new RuntimeException("F24 non trovato: id=" + f24Id));
        return dettaglio(tenantId, f.getId());
    }

    public F24RecordDTO marcaPagato(Integer tenantId, Integer f24Id) {
        F24Record f = f24RecordDAO.findById(f24Id)
                .filter(r -> tenantId.equals(r.getFkTenantId()))
                .orElseThrow(() -> new RuntimeException("F24 non trovato: id=" + f24Id));
        F24Record updated = f24RecordDAO.updateStato(f.getId(), STATO_PAID, LocalDate.now());
        auditService.log("f24.paid", "F24", updated.getId(),
                "F24 periodo " + updated.getPeriodoMese() + "/" + updated.getPeriodoAnno() + " segnato come pagato");
        String codiceTributo = codiceTributoDAO.findById(updated.getFkCodiceTributoId())
                .map(CodiceTributo::getCodice).orElse(null);
        return toDTO(updated, codiceTributo);
    }

    private F24RecordDTO toDTO(F24Record f, String codiceTributo) {
        return F24RecordDTO.builder()
                .id(f.getId())
                .periodoMese(f.getPeriodoMese())
                .periodoAnno(f.getPeriodoAnno())
                .totalAmount(f.getTotalAmount())
                .withholdingsCount(f.getWithholdingsCount())
                .stato(f.getStato())
                .deadlineDate(f.getDeadlineDate())
                .paymentDate(f.getPaymentDate())
                .codiceTributo(codiceTributo)
                .importoCredito(f.getImportoCredito())
                .codiceTributoCreditoImposta(f.getCodiceTributoCreditoImposta())
                .annoCredito(f.getAnnoCredito())
                .saldoNetto(f.getSaldoNetto() != null ? f.getSaldoNetto() : f.getTotalAmount())
                .build();
    }
}
