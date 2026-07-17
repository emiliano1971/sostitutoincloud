package it.gavia.sostitutoincloud.service;

import it.gavia.sostitutoincloud.dao.CodiceTributoDAO;
import it.gavia.sostitutoincloud.dao.F24RecordDAO;
import it.gavia.sostitutoincloud.dao.WithholdingLedgerDAO;
import it.gavia.sostitutoincloud.dto.fiscal.F24GenerazioneResultDTO;
import it.gavia.sostitutoincloud.dto.fiscal.F24RecordDTO;
import it.gavia.sostitutoincloud.model.CodiceTributo;
import it.gavia.sostitutoincloud.model.F24Record;
import it.gavia.sostitutoincloud.model.WithholdingLedger;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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

    private final F24RecordDAO f24RecordDAO;
    private final WithholdingLedgerDAO withholdingLedgerDAO;
    private final WithholdingLedgerService withholdingLedgerService;
    private final CodiceTributoDAO codiceTributoDAO;
    private final AuditService auditService;

    public F24Service(F24RecordDAO f24RecordDAO,
                      WithholdingLedgerDAO withholdingLedgerDAO,
                      WithholdingLedgerService withholdingLedgerService,
                      CodiceTributoDAO codiceTributoDAO,
                      AuditService auditService) {
        this.f24RecordDAO = f24RecordDAO;
        this.withholdingLedgerDAO = withholdingLedgerDAO;
        this.withholdingLedgerService = withholdingLedgerService;
        this.codiceTributoDAO = codiceTributoDAO;
        this.auditService = auditService;
    }

    public F24GenerazioneResultDTO generaF24(Integer tenantId, Integer anno, Integer mese) {
        // 1. Un solo F24 per tenant/periodo
        if (!f24RecordDAO.findByTenantAndPeriodo(tenantId, anno, mese).isEmpty()) {
            throw new IllegalStateException("F24 già generato per periodo " + mese + "/" + anno);
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

        // 10. Risultato con dettaglio ritenute collegate
        return F24GenerazioneResultDTO.builder()
                .f24RecordId(saved.getId())
                .periodoMese(mese)
                .periodoAnno(anno)
                .totaleRitenute(totale)
                .numeroRitenute(ritenute.size())
                .scadenza(deadline)
                .stato(saved.getStato())
                .ritenute(withholdingLedgerService.findDettaglioByF24Record(tenantId, saved.getId()))
                .build();
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

        // 7. Audit
        auditService.log("f24.ricalcola", "F24", f24Id,
                "Aggiunte " + nuove.size() + " ritenute al F24 periodo " + mese + "/" + anno
                        + " nuovo totale €" + nuovoTotale);

        log.info("F24Service.ricalcola() - f24Id={} nuoveRitenute={} nuovoTotale={}",
                f24Id, nuove.size(), nuovoTotale);

        // 8. Ricarica F24 aggiornato e restituisce il DTO
        F24Record aggiornato = f24RecordDAO.findById(f24Id)
                .orElseThrow(() -> new java.util.NoSuchElementException("F24 non trovato: id=" + f24Id));
        return F24GenerazioneResultDTO.builder()
                .f24RecordId(aggiornato.getId())
                .periodoMese(aggiornato.getPeriodoMese())
                .periodoAnno(aggiornato.getPeriodoAnno())
                .totaleRitenute(aggiornato.getTotalAmount())
                .numeroRitenute(aggiornato.getWithholdingsCount())
                .scadenza(aggiornato.getDeadlineDate())
                .stato(aggiornato.getStato())
                .ritenute(withholdingLedgerService.findDettaglioByF24Record(tenantId, aggiornato.getId()))
                .build();
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
        return F24GenerazioneResultDTO.builder()
                .f24RecordId(f.getId())
                .periodoMese(f.getPeriodoMese())
                .periodoAnno(f.getPeriodoAnno())
                .totaleRitenute(f.getTotalAmount())
                .numeroRitenute(f.getWithholdingsCount())
                .scadenza(f.getDeadlineDate())
                .stato(f.getStato())
                .ritenute(withholdingLedgerService.findDettaglioByF24Record(tenantId, f.getId()))
                .build();
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
                .build();
    }
}
