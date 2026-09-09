package it.gavia.sostitutoincloud.service;

import it.gavia.sostitutoincloud.dao.AuditLogDAO;
import it.gavia.sostitutoincloud.dao.BookingDAO;
import it.gavia.sostitutoincloud.dao.CuRecordDAO;
import it.gavia.sostitutoincloud.dao.F24RecordDAO;
import it.gavia.sostitutoincloud.dao.FiscalDocumentDAO;
import it.gavia.sostitutoincloud.dao.OwnerProfileDAO;
import it.gavia.sostitutoincloud.dao.PropertyContractRuleDAO;
import it.gavia.sostitutoincloud.dao.PropertyDAO;
import it.gavia.sostitutoincloud.dao.PropertyOtaCodeDAO;
import it.gavia.sostitutoincloud.dao.SettlementBookingDAO;
import it.gavia.sostitutoincloud.dao.SettlementDAO;
import it.gavia.sostitutoincloud.dao.WithholdingLedgerDAO;
import it.gavia.sostitutoincloud.model.Booking;
import it.gavia.sostitutoincloud.model.CuRecord;
import it.gavia.sostitutoincloud.model.F24Record;
import it.gavia.sostitutoincloud.model.OwnerProfile;
import it.gavia.sostitutoincloud.model.Property;
import it.gavia.sostitutoincloud.model.Settlement;
import it.gavia.sostitutoincloud.model.SettlementBooking;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;

/**
 * Pulizia dei dati creati dai test E2E. Attivo SOLO nel profilo local: in test e prod
 * il bean non esiste, quindi l'endpoint che lo usa non è nemmeno mappato.
 *
 * Protezione anti-cancellazione dei dati reali: si procede solo se il nome contiene
 * "E2E-" o "TEST-". Le entità operative (booking, documenti, liquidazioni) hanno FK
 * ON DELETE RESTRICT verso property e owner_profile: un record con dati collegati fa
 * fallire la delete a livello DB, seconda rete di sicurezza dopo il controllo sul nome.
 */
@Service
@Profile("local")
@Log4j2
public class TestCleanupService {

    /** Stati usati dai rollback dei test — allineati a F24Service e SettlementService. */
    private static final String STATO_F24_PAID = "paid";
    private static final String STATO_RITENUTA_DA_VERSARE = "da_versare";
    private static final String STATO_SETTLEMENT_PAID = "paid";
    /** Stati di una CU già uscita dal tenant: non si cancella nemmeno nei test. */
    private static final Set<String> STATI_CU_TRASMESSA = Set.of("sent", "delivered");

    private final PropertyDAO propertyDAO;
    private final PropertyContractRuleDAO propertyContractRuleDAO;
    private final PropertyOtaCodeDAO propertyOtaCodeDAO;
    private final OwnerProfileDAO ownerProfileDAO;
    private final BookingDAO bookingDAO;
    private final FiscalDocumentDAO fiscalDocumentDAO;
    private final SettlementBookingDAO settlementBookingDAO;
    private final SettlementDAO settlementDAO;
    private final WithholdingLedgerDAO withholdingLedgerDAO;
    private final F24RecordDAO f24RecordDAO;
    private final CuRecordDAO cuRecordDAO;
    private final AuditLogDAO auditLogDAO;

    public TestCleanupService(PropertyDAO propertyDAO,
                              PropertyContractRuleDAO propertyContractRuleDAO,
                              PropertyOtaCodeDAO propertyOtaCodeDAO,
                              OwnerProfileDAO ownerProfileDAO,
                              BookingDAO bookingDAO,
                              FiscalDocumentDAO fiscalDocumentDAO,
                              SettlementBookingDAO settlementBookingDAO,
                              SettlementDAO settlementDAO,
                              WithholdingLedgerDAO withholdingLedgerDAO,
                              F24RecordDAO f24RecordDAO,
                              CuRecordDAO cuRecordDAO,
                              AuditLogDAO auditLogDAO) {
        this.propertyDAO = propertyDAO;
        this.propertyContractRuleDAO = propertyContractRuleDAO;
        this.propertyOtaCodeDAO = propertyOtaCodeDAO;
        this.ownerProfileDAO = ownerProfileDAO;
        this.bookingDAO = bookingDAO;
        this.fiscalDocumentDAO = fiscalDocumentDAO;
        this.settlementBookingDAO = settlementBookingDAO;
        this.settlementDAO = settlementDAO;
        this.withholdingLedgerDAO = withholdingLedgerDAO;
        this.f24RecordDAO = f24RecordDAO;
        this.cuRecordDAO = cuRecordDAO;
        this.auditLogDAO = auditLogDAO;
    }

    private boolean nomeDiTest(String nome) {
        return nome != null && (nome.contains("E2E-") || nome.contains("TEST-"));
    }

    /**
     * Rimuove i documenti fiscali emessi da un test su un booking e riporta la
     * prenotazione allo stato 'ready', com'era prima dell'emissione.
     * <p>
     * A differenza degli altri cleanup non c'è un filtro sui nomi di test: qui si opera
     * su booking reali, perché la fase 04 emette documenti su una prenotazione esistente.
     * La protezione è il vincolo di tenant più il @Profile("local") del bean.
     * <p>
     * NB: sdi_progressivo non viene toccato — è un contatore fiscale, non si riavvolge.
     * La numerazione dei documenti invece si riallinea da sé, perché il progressivo è
     * calcolato sui documenti esistenti.
     */
    public Map<String, Object> cleanupDocumenti(Integer tenantId, Integer bookingId) {
        Booking booking = bookingDAO.findById(bookingId)
                .orElseThrow(() -> new NoSuchElementException("Booking non trovato: id=" + bookingId));
        if (!tenantId.equals(booking.getFkTenantId())) {
            throw new IllegalArgumentException(
                    "Booking " + bookingId + " non appartiene al tenant " + tenantId);
        }

        int documenti = fiscalDocumentDAO.findByBookingId(bookingId).size();
        withholdingLedgerDAO.deleteByBookingId(bookingId);
        fiscalDocumentDAO.deleteByBookingId(bookingId);
        bookingDAO.updateStato(bookingId, BookingDAO.STATO_READY);

        Map<String, Object> esito = new LinkedHashMap<>();
        esito.put("fiscalDocuments", documenti);
        esito.put("statoBooking", "ready");
        log.info("TestCleanupService.cleanupDocumenti() - tenantId={} bookingId={} esito={}",
                tenantId, bookingId, esito);
        return esito;
    }

    /**
     * Annulla la generazione di un F24 di test: sgancia le ritenute collegate riportandole
     * a 'da_versare' — com'erano prima della generazione — ed elimina il record F24.
     * <p>
     * Le ritenute NON vengono cancellate: appartengono alle ricevute owner emesse, che
     * questa fase non tocca. Senza il ripristino dello stato resterebbero 'versata' e
     * nessuna generazione futura potrebbe più agganciarle.
     * <p>
     * Un F24 pagato è di norma intoccabile. Il flag {@code forzaSePagato} esiste solo per
     * il test della fase 05, che marca come pagato l'F24 appena generato da lui e deve
     * poterlo rimuovere: va passato true solo su un F24 creato dal test stesso.
     */
    public Map<String, Object> cleanupF24(Integer tenantId, Integer f24Id, boolean forzaSePagato) {
        F24Record f24 = f24RecordDAO.findById(f24Id)
                .orElseThrow(() -> new NoSuchElementException("F24 non trovato: id=" + f24Id));
        if (!tenantId.equals(f24.getFkTenantId())) {
            throw new IllegalArgumentException(
                    "F24 " + f24Id + " non appartiene al tenant " + tenantId);
        }
        if (STATO_F24_PAID.equals(f24.getStato()) && !forzaSePagato) {
            throw new IllegalArgumentException("F24 " + f24Id + " è pagato: eliminazione rifiutata "
                    + "(usare forzaSePagato solo su un F24 generato dal test)");
        }

        int ritenute = withholdingLedgerDAO.resetF24Record(f24Id, STATO_RITENUTA_DA_VERSARE);
        int eliminati = f24RecordDAO.deleteById(f24Id);

        Map<String, Object> esito = new LinkedHashMap<>();
        esito.put("f24Record", eliminati);
        esito.put("ritenuteSganciate", ritenute);
        esito.put("statoRitenute", STATO_RITENUTA_DA_VERSARE);
        esito.put("periodo", f24.getPeriodoMese() + "/" + f24.getPeriodoAnno());
        log.info("TestCleanupService.cleanupF24() - tenantId={} f24Id={} forzaSePagato={} esito={}",
                tenantId, f24Id, forzaSePagato, esito);
        return esito;
    }

    /**
     * Annulla una liquidazione di test: scollega le prenotazioni ed elimina il settlement.
     * <p>
     * Le prenotazioni non si toccano, tranne quelle che il pagamento aveva portato a
     * 'settled' (vedi SettlementService.updateStatus): tornano a 'doc_issued', lo stato da
     * cui erano partite. Senza questo ripristino resterebbero fuori dall'elenco
     * "da liquidare" e nessun calcolo successivo le raccoglierebbe più.
     * <p>
     * Una liquidazione pagata è di norma intoccabile: {@code forzaSePagato} va passato solo
     * su una liquidazione calcolata dal test stesso.
     */
    public Map<String, Object> cleanupSettlement(Integer tenantId, Integer settlementId, boolean forzaSePagato) {
        Settlement settlement = settlementDAO.findById(settlementId)
                .orElseThrow(() -> new NoSuchElementException("Settlement non trovato: id=" + settlementId));
        if (!tenantId.equals(settlement.getFkTenantId())) {
            throw new IllegalArgumentException(
                    "Settlement " + settlementId + " non appartiene al tenant " + tenantId);
        }
        if (STATO_SETTLEMENT_PAID.equals(settlement.getStato()) && !forzaSePagato) {
            throw new IllegalArgumentException("Settlement " + settlementId + " è pagato: eliminazione "
                    + "rifiutata (usare forzaSePagato solo su una liquidazione creata dal test)");
        }

        List<SettlementBooking> collegate = settlementBookingDAO.findBySettlementId(settlementId);
        int ripristinati = 0;
        for (SettlementBooking sb : collegate) {
            Booking b = bookingDAO.findById(sb.getFkBookingId()).orElse(null);
            if (b != null && Integer.valueOf(BookingDAO.STATO_SETTLED).equals(b.getFkStatoPrenotazioneId())) {
                bookingDAO.updateStato(b.getId(), BookingDAO.STATO_DOC_ISSUED);
                ripristinati++;
            }
        }
        settlementBookingDAO.deleteBySettlementId(settlementId);
        int eliminati = settlementDAO.deleteById(settlementId);

        Map<String, Object> esito = new LinkedHashMap<>();
        esito.put("settlement", eliminati);
        esito.put("settlementBookings", collegate.size());
        esito.put("bookingRipristinati", ripristinati);
        esito.put("periodo", settlement.getPeriod());
        log.info("TestCleanupService.cleanupSettlement() - tenantId={} settlementId={} forzaSePagato={} esito={}",
                tenantId, settlementId, forzaSePagato, esito);
        return esito;
    }

    /**
     * Elimina una Certificazione Unica generata da un test.
     * <p>
     * Nessun dato collaterale da ripulire: cu_record è un'aggregazione di
     * withholding_ledger, che non viene toccato — la CU si rigenera quando serve.
     * <p>
     * Una CU 'sent' o 'delivered' è già uscita verso l'Agenzia delle Entrate o verso il
     * proprietario: non si cancella, senza eccezioni nemmeno per i test.
     */
    public Map<String, Object> cleanupCu(Integer tenantId, Integer cuId) {
        CuRecord cu = cuRecordDAO.findById(cuId)
                .orElseThrow(() -> new NoSuchElementException("CU non trovata: id=" + cuId));
        if (!tenantId.equals(cu.getFkTenantId())) {
            throw new IllegalArgumentException(
                    "CU " + cuId + " non appartiene al tenant " + tenantId);
        }
        if (STATI_CU_TRASMESSA.contains(cu.getStato())) {
            throw new IllegalArgumentException("CU " + cuId + " è in stato '" + cu.getStato()
                    + "': eliminazione rifiutata, è già stata trasmessa");
        }

        int eliminati = cuRecordDAO.deleteById(cuId);

        Map<String, Object> esito = new LinkedHashMap<>();
        esito.put("cuRecord", eliminati);
        esito.put("annoFiscale", cu.getTaxYear());
        log.info("TestCleanupService.cleanupCu() - tenantId={} cuId={} esito={}", tenantId, cuId, esito);
        return esito;
    }

    /**
     * Cancella i booking di test del tenant chiamante, con tutto ciò che vi pende.
     * <p>
     * Ordine vincolante: fiscal_document, settlement_booking e withholding_ledger hanno
     * FK ON DELETE RESTRICT verso booking, quindi vanno prima. audit_log invece non ha
     * FK su booking (entity_id è generico): va pulito per non lasciare righe che puntano
     * a record inesistenti.
     * <p>
     * Doppia protezione: il pattern deve contenere "E2E-" o "TEST-", e la ricerca è
     * comunque limitata ai booking del tenant passato.
     *
     * @param externalIdPattern pattern LIKE su external_booking_id, es. "E2E-%"
     */
    public Map<String, Object> cleanupBookings(Integer tenantId, String externalIdPattern) {
        if (!nomeDiTest(externalIdPattern)) {
            throw new IllegalArgumentException("Pattern ammesso solo per i dati di test: deve "
                    + "contenere 'E2E-' o 'TEST-' (ricevuto: '" + externalIdPattern + "')");
        }

        List<Integer> bookingIds = bookingDAO.findIdsByExternalIdPattern(tenantId, externalIdPattern);
        Map<String, Object> esito = new LinkedHashMap<>();
        if (bookingIds.isEmpty()) {
            esito.put("bookings", 0);
            log.info("TestCleanupService.cleanupBookings() - tenantId={} pattern={} nessun booking",
                    tenantId, externalIdPattern);
            return esito;
        }

        int audit = auditLogDAO.deleteByEntity("Booking", bookingIds);
        for (Integer id : bookingIds) {
            withholdingLedgerDAO.deleteByBookingId(id);
            settlementBookingDAO.deleteByBookingId(id);
            fiscalDocumentDAO.deleteByBookingId(id);
            bookingDAO.deleteById(id);
        }

        esito.put("bookings", bookingIds.size());
        esito.put("auditLog", audit);
        log.info("TestCleanupService.cleanupBookings() - tenantId={} pattern={} esito={}",
                tenantId, externalIdPattern, esito);
        return esito;
    }

    private int intOrZero(Object v) {
        return v instanceof Integer ? (Integer) v : 0;
    }

    /**
     * Elimina immobile e/o proprietario di test. L'ordine è vincolante: prima
     * l'immobile, perché property → owner_profile è ON DELETE RESTRICT.
     *
     * @return righe eliminate per tabella; le voci "…Skipped" indicano un id ignorato
     *         perché il nome non è riconosciuto come dato di test
     */
    public Map<String, Object> cleanupAnagrafica(Integer ownerId, Integer propertyId) {
        Map<String, Object> esito = new LinkedHashMap<>();

        if (propertyId != null) {
            Optional<Property> opt = propertyDAO.findById(propertyId);
            if (opt.isEmpty()) {
                esito.put("propertyNotFound", propertyId);
            } else if (!nomeDiTest(opt.get().getDisplayName())) {
                log.warn("TestCleanupService: immobile {} ignorato, displayName='{}' non è di test",
                        propertyId, opt.get().getDisplayName());
                esito.put("propertySkipped", opt.get().getDisplayName());
            } else {
                esito.put("contractRules", propertyContractRuleDAO.deleteByPropertyId(propertyId));
                propertyOtaCodeDAO.deleteByPropertyId(propertyId);
                esito.put("property", propertyDAO.delete(propertyId));
            }
        }

        if (ownerId != null) {
            Optional<OwnerProfile> opt = ownerProfileDAO.findById(ownerId);
            if (opt.isEmpty()) {
                esito.put("ownerNotFound", ownerId);
            } else if (!nomeDiTest(opt.get().getLastName())) {
                log.warn("TestCleanupService: proprietario {} ignorato, lastName='{}' non è di test",
                        ownerId, opt.get().getLastName());
                esito.put("ownerSkipped", opt.get().getLastName());
            } else {
                esito.put("owner", ownerProfileDAO.delete(ownerId));
            }
        }

        log.info("TestCleanupService.cleanupAnagrafica() - ownerId={} propertyId={} esito={}",
                ownerId, propertyId, esito);
        return esito;
    }

    /**
     * Pre-cleanup dei test: rimuove TUTTI i proprietari di test il cui cognome contiene
     * il pattern, con i loro immobili. Serve a ripartire puliti dopo una run interrotta,
     * dove il residuo bloccherebbe la successiva su UNIQUE (fk_tenant_id, tax_code).
     * <p>
     * Il pattern stesso deve essere un marcatore di test ("E2E-" o "TEST-"): impedisce
     * di passare un pattern generico che spazzerebbe via i proprietari reali.
     */
    public Map<String, Object> cleanupByLastNamePattern(Integer tenantId, String pattern) {
        if (!nomeDiTest(pattern)) {
            throw new IllegalArgumentException("Pattern ammesso solo per i dati di test: deve "
                    + "contenere 'E2E-' o 'TEST-' (ricevuto: '" + pattern + "')");
        }

        int ownerEliminati = 0;
        int immobiliEliminati = 0;
        int regoleEliminate = 0;
        List<String> saltati = new ArrayList<>();

        for (OwnerProfile owner : ownerProfileDAO.findByLastNamePattern(tenantId, pattern)) {
            boolean tuttiDiTest = true;
            for (Property p : propertyDAO.findByOwnerId(owner.getId())) {
                Map<String, Object> parziale = cleanupAnagrafica(null, p.getId());
                if (parziale.containsKey("propertySkipped")) {
                    tuttiDiTest = false;
                    saltati.add("immobile " + p.getId() + " '" + p.getDisplayName() + "'");
                } else {
                    immobiliEliminati += intOrZero(parziale.get("property"));
                    regoleEliminate += intOrZero(parziale.get("contractRules"));
                }
            }
            if (!tuttiDiTest) {
                // property → owner_profile è ON DELETE RESTRICT: senza aver rimosso tutti
                // gli immobili la delete del proprietario fallirebbe a livello DB.
                saltati.add("proprietario " + owner.getId() + " (immobili non di test collegati)");
                continue;
            }
            ownerEliminati += intOrZero(cleanupAnagrafica(owner.getId(), null).get("owner"));
        }

        Map<String, Object> esito = new LinkedHashMap<>();
        esito.put("owners", ownerEliminati);
        esito.put("properties", immobiliEliminati);
        esito.put("contractRules", regoleEliminate);
        if (!saltati.isEmpty()) {
            esito.put("skipped", saltati);
        }
        log.info("TestCleanupService.cleanupByLastNamePattern() - tenantId={} pattern={} esito={}",
                tenantId, pattern, esito);
        return esito;
    }
}
