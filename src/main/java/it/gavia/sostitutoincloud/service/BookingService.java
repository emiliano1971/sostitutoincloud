package it.gavia.sostitutoincloud.service;

import it.gavia.sostitutoincloud.dao.BookingDAO;
import it.gavia.sostitutoincloud.dao.CanaleOtaDAO;
import it.gavia.sostitutoincloud.dao.FiscalDocumentDAO;
import it.gavia.sostitutoincloud.dao.OwnerProfileDAO;
import it.gavia.sostitutoincloud.dao.PropertyDAO;
import it.gavia.sostitutoincloud.dao.RegimeFiscaleDAO;
import it.gavia.sostitutoincloud.dao.SettlementBookingDAO;
import it.gavia.sostitutoincloud.dao.SettlementDAO;
import it.gavia.sostitutoincloud.dao.StatoDocumentoDAO;
import it.gavia.sostitutoincloud.dao.StatoPrenotazioneDAO;
import it.gavia.sostitutoincloud.dao.TenantDAO;
import it.gavia.sostitutoincloud.dao.TipoDocumentoDAO;
import it.gavia.sostitutoincloud.dao.WithholdingLedgerDAO;
import it.gavia.sostitutoincloud.dto.booking.BookingCreateDTO;
import it.gavia.sostitutoincloud.dto.booking.BookingDetailDTO;
import it.gavia.sostitutoincloud.dto.booking.BookingFilterDTO;
import it.gavia.sostitutoincloud.dto.booking.BookingListDTO;
import it.gavia.sostitutoincloud.dto.booking.ContrattoCalcoloResult;
import it.gavia.sostitutoincloud.dto.booking.GuestUpdateDTO;
import it.gavia.sostitutoincloud.dto.booking.SplitEconomicoDTO;
import it.gavia.sostitutoincloud.dto.document.FiscalDocumentSummaryDTO;
import it.gavia.sostitutoincloud.model.Booking;
import it.gavia.sostitutoincloud.model.CanaleOta;
import it.gavia.sostitutoincloud.model.FiscalDocument;
import it.gavia.sostitutoincloud.model.OwnerProfile;
import it.gavia.sostitutoincloud.model.Property;
import it.gavia.sostitutoincloud.model.RegimeFiscale;
import it.gavia.sostitutoincloud.model.StatoDocumento;
import it.gavia.sostitutoincloud.model.StatoPrenotazione;
import it.gavia.sostitutoincloud.model.Tenant;
import it.gavia.sostitutoincloud.model.TipoDocumento;
import it.gavia.sostitutoincloud.util.NazioneUtils;
import it.gavia.sostitutoincloud.util.TenantAddressUtils;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Log4j2
public class BookingService {

    private static final Set<String> STATI_ESCLUSI_DA_COMPLETARE =
            Set.of("doc_issued", "settled", "cancelled");

    private final BookingDAO bookingDAO;
    private final PropertyDAO propertyDAO;
    private final OwnerProfileDAO ownerProfileDAO;
    private final CanaleOtaDAO canaleOtaDAO;
    private final StatoPrenotazioneDAO statoPrenotazioneDAO;
    private final StatoDocumentoDAO statoDocumentoDAO;
    private final RegimeFiscaleDAO regimeFiscaleDAO;
    private final TenantDAO tenantDAO;
    private final TipoDocumentoDAO tipoDocumentoDAO;
    private final FiscalDocumentDAO fiscalDocumentDAO;
    private final SettlementBookingDAO settlementBookingDAO;
    private final SettlementDAO settlementDAO;
    private final WithholdingLedgerDAO withholdingLedgerDAO;
    private final ContrattoCalcolatoreService contrattoCalcolatore;
    private final CodiceFiscaleService codiceFiscaleService;
    private final TouristTaxService touristTaxService;
    private final AuditService auditService;

    public BookingService(BookingDAO bookingDAO,
                          PropertyDAO propertyDAO,
                          OwnerProfileDAO ownerProfileDAO,
                          CanaleOtaDAO canaleOtaDAO,
                          StatoPrenotazioneDAO statoPrenotazioneDAO,
                          StatoDocumentoDAO statoDocumentoDAO,
                          RegimeFiscaleDAO regimeFiscaleDAO,
                          TenantDAO tenantDAO,
                          TipoDocumentoDAO tipoDocumentoDAO,
                          FiscalDocumentDAO fiscalDocumentDAO,
                          SettlementBookingDAO settlementBookingDAO,
                          SettlementDAO settlementDAO,
                          WithholdingLedgerDAO withholdingLedgerDAO,
                          ContrattoCalcolatoreService contrattoCalcolatore,
                          CodiceFiscaleService codiceFiscaleService,
                          TouristTaxService touristTaxService,
                          AuditService auditService) {
        this.bookingDAO = bookingDAO;
        this.propertyDAO = propertyDAO;
        this.ownerProfileDAO = ownerProfileDAO;
        this.canaleOtaDAO = canaleOtaDAO;
        this.statoPrenotazioneDAO = statoPrenotazioneDAO;
        this.statoDocumentoDAO = statoDocumentoDAO;
        this.regimeFiscaleDAO = regimeFiscaleDAO;
        this.tenantDAO = tenantDAO;
        this.tipoDocumentoDAO = tipoDocumentoDAO;
        this.fiscalDocumentDAO = fiscalDocumentDAO;
        this.settlementBookingDAO = settlementBookingDAO;
        this.settlementDAO = settlementDAO;
        this.withholdingLedgerDAO = withholdingLedgerDAO;
        this.contrattoCalcolatore = contrattoCalcolatore;
        this.codiceFiscaleService = codiceFiscaleService;
        this.touristTaxService = touristTaxService;
        this.auditService = auditService;
    }

    public List<BookingListDTO> findByTenantId(Integer tenantId, BookingFilterDTO filter) {
        LookupMaps maps = buildLookupMaps(tenantId);
        List<Booking> all = bookingDAO.findByTenantId(tenantId);

        Stream<Booking> stream = all.stream();
        stream = applyStatusFilter(stream, filter.getStatus(), maps);
        stream = applyChannelFilter(stream, filter.getChannel(), maps);
        stream = applySearchFilter(stream, filter.getQ());

        List<BookingListDTO> result = stream.map(b -> toListDTO(b, maps)).toList();
        log.info("BookingService.findByTenantId() - tenantId={}, filtro=[status={}, channel={}, q={}], {} booking trovati",
                tenantId, filter.getStatus(), filter.getChannel(), filter.getQ(), result.size());
        return result;
    }

    /**
     * Prenotazioni di un singolo proprietario — portale owner.
     *
     * <p>L'ownerId arriva dal token (SecurityUtils.getCurrentOwnerId()), non dal client:
     * il filtro è quindi lato server e un owner non può vedere le prenotazioni di altri.
     * NB l'ordine dei parametri del DAO è (tenantId, ownerId).
     */
    public List<BookingListDTO> findByOwner(Integer tenantId, Integer ownerId) {
        LookupMaps maps = buildLookupMaps(tenantId);
        List<BookingListDTO> result = bookingDAO.findByOwnerAndTenant(tenantId, ownerId).stream()
                .map(b -> toListDTO(b, maps))
                .toList();
        log.info("BookingService.findByOwner() - tenantId={} ownerId={} {} booking trovati",
                tenantId, ownerId, result.size());
        return result;
    }

    public Optional<BookingDetailDTO> findById(Integer tenantId, Integer bookingId) {
        log.info("BookingService.findById() - tenantId={}, bookingId={}", tenantId, bookingId);
        return bookingDAO.findById(bookingId)
                .filter(b -> tenantId.equals(b.getFkTenantId()))
                .map(b -> {
                    LookupMaps maps = buildLookupMaps(tenantId);
                    return toDetailDTO(b, maps);
                });
    }

    // ── workflow stato prenotazione ─────────────────────────────────────────

    /**
     * Determina lo stato corretto del booking dai dati disponibili, in ordine di priorità
     * decrescente: cancelled > settled > doc_issued > ready > enriched > imported.
     */
    private int resolveStatoId(Booking booking) {
        // 1. Cancellato manualmente: non retrocedere
        if (booking.getFkStatoPrenotazioneId() != null
                && booking.getFkStatoPrenotazioneId() == BookingDAO.STATO_CANCELLED) {
            return BookingDAO.STATO_CANCELLED;
        }
        // 2. Liquidazione pagata
        boolean settlementPagato = settlementBookingDAO.findSettlementIdByBookingId(booking.getId())
                .flatMap(settlementDAO::findById)
                .map(s -> "paid".equals(s.getStato()))
                .orElse(false);
        if (settlementPagato) return BookingDAO.STATO_SETTLED;
        // 3. Documento fiscale emesso
        if (!fiscalDocumentDAO.findByBookingId(booking.getId()).isEmpty()) {
            return BookingDAO.STATO_DOC_ISSUED;
        }
        // 4. Split economico calcolato (provvigione PM presente e calcolo senza errori)
        if (booking.getPmFeeAmount() != null && booking.getPmFeeAmount().signum() > 0) {
            try {
                contrattoCalcolatore.calcola(booking.getFkTenantId(), booking.getFkPropertyId(),
                        booking.getFkCanaleOtaId(), booking.getGrossAmount(),
                        booking.getOtaCommissionAmount(), booking.getNights(), booking.getGuests());
                return BookingDAO.STATO_READY;
            } catch (Exception e) {
                log.debug("resolveStatoId - split non calcolabile per booking {}: {}", booking.getId(), e.getMessage());
            }
        }
        // 5. CF ospite valorizzato
        if (booking.getGuestTaxCode() != null && !booking.getGuestTaxCode().isBlank()) {
            return BookingDAO.STATO_ENRICHED;
        }
        // 6. Solo importato
        return BookingDAO.STATO_IMPORTED;
    }

    /** Ricalcola e persiste lo stato del booking in base ai dati correnti. */
    public void aggiornaStato(Integer bookingId) {
        Booking booking = bookingDAO.findById(bookingId)
                .orElseThrow(() -> new NoSuchElementException("Booking non trovato: id=" + bookingId));
        int statoId = resolveStatoId(booking);
        bookingDAO.updateStato(bookingId, statoId);
        log.info("BookingService.aggiornaStato() - bookingId={} → statoId={}", bookingId, statoId);
    }

    /** Aggiorna l'anagrafica ospite di un booking e ricalcola lo stato. */
    public Optional<BookingDetailDTO> updateBookingGuest(Integer tenantId, Integer bookingId, GuestUpdateDTO dto) {
        Booking existing = bookingDAO.findById(bookingId)
                .filter(b -> tenantId.equals(b.getFkTenantId()))
                .orElseThrow(() -> new NoSuchElementException("Booking non trovato: id=" + bookingId));

        LocalDate birthDate = null;
        if (dto.getGuestBirthDate() != null && !dto.getGuestBirthDate().isBlank()) {
            birthDate = LocalDate.parse(dto.getGuestBirthDate().trim());
        }

        // PATCH parziale: updateGuestAnagrafica() riscrive TUTTE le colonne guest, quindi ogni
        // campo assente dal body va riletto dal booking a DB o verrebbe azzerato.
        String guestName = merge(dto.getGuestName(), existing.getGuestName());
        LocalDate guestBirthDate = birthDate != null ? birthDate : existing.getGuestBirthDate();
        String guestSesso = merge(dto.getGuestSesso(), existing.getGuestSesso());
        String guestBirthPlace = merge(dto.getGuestBirthPlace(), existing.getGuestBirthPlace());
        String guestBirthBelfiore = merge(dto.getGuestBirthBelfiore(), existing.getGuestBirthBelfiore());
        String guestDocType = merge(dto.getGuestDocType(), existing.getGuestDocType());
        String guestDocNumber = merge(dto.getGuestDocNumber(), existing.getGuestDocNumber());
        String guestCountry = merge(dto.getGuestCountry(), existing.getGuestCountry());
        String guestAddress = merge(dto.getGuestAddress(), existing.getGuestAddress());
        String guestPhone = merge(dto.getGuestPhone(), existing.getGuestPhone());

        // CF: se manca sia nel body sia a DB, prova a calcolarlo dai dati anagrafici già
        // mergiati (come nel flusso import). Un CF già presente non viene mai ricalcolato.
        String guestTaxCode = merge(dto.getGuestTaxCode(), existing.getGuestTaxCode());
        String comune = emptyToNull(guestBirthPlace);
        if (comune == null) comune = emptyToNull(guestBirthBelfiore);
        if (emptyToNull(guestTaxCode) == null && isNotBlank(guestName)
                && guestBirthDate != null && emptyToNull(guestSesso) != null && comune != null) {
            String full = guestName.trim();
            int sp = full.indexOf(' ');
            String cognome = sp < 0 ? full : full.substring(0, sp);
            String nome = sp < 0 ? full : full.substring(sp + 1).trim();
            guestTaxCode = codiceFiscaleService
                    .calcolaSafe(cognome, nome, guestBirthDate, guestSesso.trim(), comune)
                    .orElse(null);
        }

        // Se straniero generico e CF assente → genera CF fittizio come in
        // createManuale() e confirm().
        if ((guestTaxCode == null || guestTaxCode.isBlank())
                && NazioneUtils.isNazioneEstera(guestCountry)) {
            guestTaxCode = codiceFiscaleService.generaCfEstero(tenantId, LocalDate.now().getYear());
            log.info("BookingService.updateBookingGuest() - CF fittizio generato: {}", guestTaxCode);
        }

        Booking g = Booking.builder()
                .guestName(guestName)
                .guestTaxCode(guestTaxCode)
                .guestBirthDate(guestBirthDate)
                .guestSesso(guestSesso)
                .guestBirthPlace(guestBirthPlace)
                .guestBirthBelfiore(guestBirthBelfiore)
                .guestDocType(guestDocType)
                .guestDocNumber(guestDocNumber)
                .guestCountry(guestCountry)
                .guestAddress(guestAddress)
                .guestPhone(guestPhone)
                .build();

        log.info("BookingService.updateBookingGuest() - id={} merge completato", bookingId);
        bookingDAO.updateGuestAnagrafica(existing.getId(), tenantId, g);
        aggiornaStato(existing.getId());
        log.info("BookingService.updateBookingGuest() - tenantId={} bookingId={}", tenantId, bookingId);
        return findById(tenantId, bookingId);
    }

    private boolean isNotBlank(String s) {
        return s != null && !s.isBlank();
    }

    /** Valore in arrivo se valorizzato, altrimenti quello già a DB: base del merge del PATCH. */
    private String merge(String nuovo, String esistente) {
        return isNotBlank(nuovo) ? nuovo.trim() : esistente;
    }

    private String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    // ── inserimento manuale ─────────────────────────────────────────────────

    /**
     * Crea una prenotazione digitata a mano dall'operatore (POST /api/bookings).
     *
     * <p>Segue lo stesso percorso dell'import da file: split economico dalle regole del
     * contratto, tassa di soggiorno dal comune dell'immobile, stato risolto da
     * {@link #aggiornaStato(Integer)}. Dal client arrivano solo i dati digitati —
     * ogni importo derivato è calcolato qui.
     */
    @Transactional
    public BookingDetailDTO createManuale(Integer tenantId, BookingCreateDTO dto) {
        // 1. Validazioni
        if (dto.getFkPropertyId() == null) {
            throw new IllegalArgumentException("Immobile obbligatorio");
        }
        if (dto.getCheckinDate() == null || dto.getCheckoutDate() == null) {
            throw new IllegalArgumentException("Check-in e check-out sono obbligatori");
        }
        if (!dto.getCheckinDate().isBefore(dto.getCheckoutDate())) {
            throw new IllegalArgumentException("Check-out deve essere dopo check-in");
        }
        if (dto.getGrossAmount() == null || dto.getGrossAmount().signum() <= 0) {
            throw new IllegalArgumentException("Il lordo ospite deve essere maggiore di zero");
        }
        if (dto.getGuests() == null || dto.getGuests() < 1) {
            throw new IllegalArgumentException("Il numero di ospiti deve essere almeno 1");
        }
        if (!isNotBlank(dto.getGuestName())) {
            throw new IllegalArgumentException("Nome ospite obbligatorio");
        }

        Property property = propertyDAO.findById(dto.getFkPropertyId())
                .filter(p -> tenantId.equals(p.getFkTenantId()))
                .orElseThrow(() -> new IllegalArgumentException("Immobile non trovato"));

        int nights = (int) ChronoUnit.DAYS.between(dto.getCheckinDate(), dto.getCheckoutDate());

        // 2. ID esterno: quello digitato oppure generato
        String extId = isNotBlank(dto.getExternalBookingId())
                ? dto.getExternalBookingId().trim()
                : "MAN-" + System.currentTimeMillis();

        // Il vincolo uq_external_booking (tenant, canale, external_id) scatterebbe come
        // errore SQL opaco: intercettiamo prima per restituire un 400 leggibile.
        if (bookingDAO.findByExternalBookingId(extId, tenantId, dto.getFkCanaleOtaId()).isPresent()) {
            throw new IllegalArgumentException("Esiste già una prenotazione con ID " + extId + " su questo canale");
        }

        // 3. CF ospite: mai ricalcolato se già digitato. Il nome completo è "Cognome Nome",
        //    stessa convenzione di updateBookingGuest() e di GuestEditDialog lato frontend.
        String cf = emptyToNull(dto.getGuestTaxCode());
        if (cf == null && dto.getGuestBirthDate() != null
                && emptyToNull(dto.getGuestSesso()) != null
                && emptyToNull(dto.getGuestBirthPlace()) != null) {
            String full = dto.getGuestName().trim();
            int sp = full.indexOf(' ');
            String cognome = sp < 0 ? full : full.substring(0, sp);
            String nome = sp < 0 ? full : full.substring(sp + 1).trim();
            cf = codiceFiscaleService.calcolaSafe(cognome, nome, dto.getGuestBirthDate(),
                    dto.getGuestSesso().trim(), dto.getGuestBirthPlace().trim()).orElse(null);
        }

        // Ospite straniero (opzione "Straniero" del form) senza CF: si genera il codice
        // fittizio EST+anno+progressivo, come fa l'import alla conferma.
        if ((cf == null || cf.isBlank()) && NazioneUtils.isNazioneEstera(dto.getGuestCountry())) {
            cf = codiceFiscaleService.generaCfEstero(tenantId, LocalDate.now().getYear());
            log.info("BookingService.createManuale() - CF fittizio generato: {}", cf);
        }

        // 4. Split economico dalle regole del contratto. Nessun override di commissione OTA:
        //    l'inserimento manuale non ha un dato reale del canale da cui partire.
        ContrattoCalcoloResult calcolo = contrattoCalcolatore.calcola(
                tenantId,
                dto.getFkPropertyId(),
                dto.getFkCanaleOtaId(),
                dto.getGrossAmount(),
                null,
                nights,
                dto.getGuests());

        if (calcolo.getWarnings() != null && !calcolo.getWarnings().isEmpty()) {
            calcolo.getWarnings().forEach(w ->
                    log.warn("BookingService.createManuale() - booking {}: {}", extId, w));
        }

        // 5. Tassa di soggiorno: non inclusa nel lordo digitato dall'operatore.
        BigDecimal touristTax = touristTaxService.calcolaPerBooking(
                tenantId, property.getCity(), dto.getCheckinDate(), nights, dto.getGuests(), false);

        // 6. Modalità di riscossione della tassa: dal canale se indicato, altrimenti contanti
        //    (stesso fallback dell'import). La colonna è NOT NULL.
        String touristTaxCollection = dto.getFkCanaleOtaId() != null
                ? canaleOtaDAO.findById(dto.getFkCanaleOtaId())
                        .map(CanaleOta::getTouristTaxCollection)
                        .filter(this::isNotBlank)
                        .orElse("contanti")
                : "contanti";

        // Regime fiscale fotografato dal proprietario dell'immobile: l'owner può cambiarlo
        // in seguito, la prenotazione deve restare legata a quello in vigore oggi.
        Integer fkRegimeFiscaleId = property.getFkOwnerId() != null
                ? ownerProfileDAO.findById(property.getFkOwnerId())
                        .map(OwnerProfile::getFkRegimeFiscaleId)
                        .orElse(null)
                : null;

        Booking booking = Booking.builder()
                .fkTenantId(tenantId)
                .fkPropertyId(dto.getFkPropertyId())
                .fkCanaleOtaId(dto.getFkCanaleOtaId())
                .fkOwnerId(property.getFkOwnerId())
                .fkRegimeFiscaleId(fkRegimeFiscaleId)
                .externalBookingId(extId)
                .checkinDate(dto.getCheckinDate())
                .checkoutDate(dto.getCheckoutDate())
                .nights(nights)
                .guests(dto.getGuests())
                .grossAmount(dto.getGrossAmount())
                .otaCommissionAmount(calcolo.getOtaCommissionAmount())
                .cleaningAmount(calcolo.getCleaningAmount())
                .pmFeeAmount(calcolo.getPmFeeAmount())
                .ownerNetAmount(calcolo.getOwnerNetAmount())
                .withholdingAmount(calcolo.getWithholdingAmount())
                .aliquotaRitenuta(calcolo.getAliquotaRitenuta())
                .touristTaxAmount(touristTax)
                .touristTaxIncludedInGross(false)
                .touristTaxCollection(touristTaxCollection)
                .guestName(dto.getGuestName().trim())
                .guestTaxCode(cf)
                .guestBirthDate(dto.getGuestBirthDate())
                .guestSesso(emptyToNull(dto.getGuestSesso()))
                .guestBirthPlace(emptyToNull(dto.getGuestBirthPlace()))
                .guestDocType(emptyToNull(dto.getGuestDocType()))
                .guestDocNumber(emptyToNull(dto.getGuestDocNumber()))
                .guestCountry(emptyToNull(dto.getGuestCountry()))
                .guestAddress(emptyToNull(dto.getGuestAddress()))
                .guestPhone(emptyToNull(dto.getGuestPhone()))
                // Colonne NOT NULL senza valore derivato: l'INSERT le scrive sempre,
                // quindi il DEFAULT dello schema non entrerebbe mai in gioco.
                .fkStatoPrenotazioneId(BookingDAO.STATO_IMPORTED)
                .paymentStatus("pending")
                .settlementStatus("pending")
                .build();

        Booking saved = bookingDAO.insert(booking);

        // 7. Stato reale dai dati disponibili (imported / enriched / ready), come all'import.
        aggiornaStato(saved.getId());

        auditService.log("booking.create.manual", "Booking", saved.getId(),
                "Inserimento manuale: " + extId);

        BookingDetailDTO result = findById(tenantId, saved.getId())
                .orElseThrow(() -> new NoSuchElementException("Booking non trovato dopo l'inserimento: id=" + saved.getId()));
        log.info("BookingService.createManuale() - id={} stato={}", result.getId(), result.getStatoPrenotazione());
        return result;
    }

    /**
     * Cancella un booking e, a cascata, tutti i dati collegati (liquidazioni,
     * ritenute, documenti fiscali). Le DELETE seguono un ordine preciso per
     * rispettare i vincoli FK. Disponibile solo in local/test (vedi controller).
     */
    @Transactional
    public void deleteWithCascade(Integer tenantId, Integer bookingId) {
        Booking booking = bookingDAO.findById(bookingId)
                .filter(b -> tenantId.equals(b.getFkTenantId()))
                .orElseThrow(() -> new NoSuchElementException("Booking non trovato: id=" + bookingId));

        // Blocca la cancellazione se esistono documenti fiscali emessi, PRIMA di toccare qualsiasi dato.
        int docCount = fiscalDocumentDAO.countByBookingId(booking.getId());
        if (docCount > 0) {
            throw new IllegalStateException("Impossibile eliminare: il booking ha " + docCount
                    + " documento/i fiscale/i emesso/i. Eliminare prima i documenti dal DB.");
        }

        settlementBookingDAO.deleteByBookingId(booking.getId());
        withholdingLedgerDAO.deleteByBookingId(booking.getId());
        fiscalDocumentDAO.deleteByBookingId(booking.getId());
        bookingDAO.deleteById(booking.getId());

        auditService.log("booking.delete", "Booking", bookingId,
                "Cancellazione cascata booking id=" + bookingId);
        log.info("BookingService.deleteWithCascade() - id={}", bookingId);
    }

    // ── filtri ──────────────────────────────────────────────────────────────

    private Stream<Booking> applyStatusFilter(Stream<Booking> stream, String status, LookupMaps maps) {
        if (status == null) return stream;
        if ("da_completare".equals(status)) {
            LocalDate oggi = LocalDate.now();
            return stream.filter(b -> {
                boolean scaduto = b.getCheckoutDate() != null && !b.getCheckoutDate().isAfter(oggi);
                String codice = statoCodiceDa(b.getFkStatoPrenotazioneId(), maps.statiPrenotazioneById);
                return scaduto && !STATI_ESCLUSI_DA_COMPLETARE.contains(codice);
            });
        }
        return stream.filter(b ->
                status.equals(statoCodiceDa(b.getFkStatoPrenotazioneId(), maps.statiPrenotazioneById)));
    }

    private Stream<Booking> applyChannelFilter(Stream<Booking> stream, String channel, LookupMaps maps) {
        if (channel == null) return stream;
        return stream.filter(b -> {
            CanaleOta c = maps.canaliById.get(b.getFkCanaleOtaId());
            return c != null && channel.equals(c.getCodice());
        });
    }

    private Stream<Booking> applySearchFilter(Stream<Booking> stream, String q) {
        if (q == null || q.isBlank()) return stream;
        String lower = q.toLowerCase();
        return stream.filter(b ->
                (b.getGuestName() != null && b.getGuestName().toLowerCase().contains(lower))
                || (b.getExternalBookingId() != null && b.getExternalBookingId().toLowerCase().contains(lower)));
    }

    // ── lookup helpers ───────────────────────────────────────────────────────

    private LookupMaps buildLookupMaps(Integer tenantId) {
        Map<Integer, Property> propertiesById = propertyDAO.findByTenantId(tenantId).stream()
                .collect(Collectors.toMap(Property::getId, p -> p));
        Map<Integer, OwnerProfile> ownersById = ownerProfileDAO.findByTenantId(tenantId).stream()
                .collect(Collectors.toMap(OwnerProfile::getId, o -> o));
        Map<Integer, CanaleOta> canaliById = canaleOtaDAO.findAll().stream()
                .collect(Collectors.toMap(CanaleOta::getId, c -> c));
        Map<Integer, StatoPrenotazione> statiPrenotazioneById = statoPrenotazioneDAO.findAll().stream()
                .collect(Collectors.toMap(StatoPrenotazione::getId, s -> s));
        Map<Integer, StatoDocumento> statiDocumentoById = statoDocumentoDAO.findAll().stream()
                .collect(Collectors.toMap(StatoDocumento::getId, s -> s));
        Map<Integer, RegimeFiscale> regimiById = regimeFiscaleDAO.findAll().stream()
                .collect(Collectors.toMap(RegimeFiscale::getId, s -> s));
        Map<Integer, TipoDocumento> tipiDocumentoById = tipoDocumentoDAO.findAll().stream()
                .collect(Collectors.toMap(TipoDocumento::getId, t -> t));
        Tenant tenant = tenantDAO.findById(tenantId).orElse(null);
        return new LookupMaps(propertiesById, ownersById, canaliById,
                statiPrenotazioneById, statiDocumentoById, regimiById, tipiDocumentoById, tenant);
    }

    private String statoCodiceDa(Integer id, Map<Integer, StatoPrenotazione> map) {
        if (id == null) return null;
        StatoPrenotazione s = map.get(id);
        return s != null ? s.getCodice() : null;
    }

    private String statoDocCodiceDa(Integer id, Map<Integer, StatoDocumento> map) {
        if (id == null) return null;
        StatoDocumento s = map.get(id);
        return s != null ? s.getCodice() : null;
    }

    /**
     * Calcola dinamicamente lo stato documento della prenotazione a partire dai fiscal_document
     * associati: lo stato NON è più persistito sul booking.
     * Priorità: accepted > sent_sdi > ready > draft. Nessun documento → "nessuno".
     */
    private String computeDocumentStatus(Integer bookingId, LookupMaps maps) {
        List<FiscalDocument> docs = fiscalDocumentDAO.findByBookingId(bookingId);
        if (docs.isEmpty()) {
            return "nessuno";
        }
        boolean hasAccepted = docs.stream()
                .anyMatch(d -> "accepted".equals(statoDocCodiceDa(d.getFkStatoDocumentoId(), maps.statiDocumentoById)));
        boolean hasSentSdi = docs.stream()
                .anyMatch(d -> "sent_sdi".equals(statoDocCodiceDa(d.getFkStatoDocumentoId(), maps.statiDocumentoById)));
        boolean hasReady = docs.stream()
                .anyMatch(d -> "ready".equals(statoDocCodiceDa(d.getFkStatoDocumentoId(), maps.statiDocumentoById)));
        if (hasAccepted) return "accepted";
        if (hasSentSdi) return "sent_sdi";
        if (hasReady) return "ready";
        return "draft";
    }

    /**
     * Risolve il nome del proprietario direttamente dal fk_owner_id denormalizzato sul booking,
     * senza risalire la catena booking→property→owner (stesso pattern di FiscalDocumentService).
     */
    private String resolveOwnerName(Integer fkOwnerId, Map<Integer, OwnerProfile> ownersById) {
        if (fkOwnerId == null) return null;
        OwnerProfile o = ownersById.get(fkOwnerId);
        return o != null ? o.getFirstName() + " " + o.getLastName() : null;
    }

    private BigDecimal safeVal(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    // ── mapping ─────────────────────────────────────────────────────────────

    private BookingListDTO toListDTO(Booking b, LookupMaps maps) {
        Property prop = maps.propertiesById.get(b.getFkPropertyId());
        CanaleOta canale = maps.canaliById.get(b.getFkCanaleOtaId());
        return BookingListDTO.builder()
                .id(b.getId())
                .externalBookingId(b.getExternalBookingId())
                .guestName(b.getGuestName())
                .propertyName(prop != null ? prop.getDisplayName() : null)
                .fkOwnerId(b.getFkOwnerId())
                .ownerName(resolveOwnerName(b.getFkOwnerId(), maps.ownersById))
                .channelName(canale != null ? canale.getNome() : null)
                .checkinDate(b.getCheckinDate())
                .checkoutDate(b.getCheckoutDate())
                .nights(b.getNights())
                .guests(b.getGuests())
                .grossAmount(b.getGrossAmount())
                .ownerNetAmount(b.getOwnerNetAmount())
                .statoPrenotazione(statoCodiceDa(b.getFkStatoPrenotazioneId(), maps.statiPrenotazioneById))
                .paymentStatus(b.getPaymentStatus())
                .documentStatus(computeDocumentStatus(b.getId(), maps))
                .settlementStatus(b.getSettlementStatus())
                .createdAt(b.getCreatedAt())
                .build();
    }

    private BookingDetailDTO toDetailDTO(Booking b, LookupMaps maps) {
        Property prop = maps.propertiesById.get(b.getFkPropertyId());
        CanaleOta canale = maps.canaliById.get(b.getFkCanaleOtaId());
        RegimeFiscale regime = maps.regimiById.get(b.getFkRegimeFiscaleId());
        OwnerProfile owner = b.getFkOwnerId() != null
                ? maps.ownersById.get(b.getFkOwnerId()) : null;
        Tenant tenant = maps.tenant;

        // Ricalcolo dello split economico tramite le regole del contratto immobile.
        // L'otaCommission già presente nel DB è usato come override (il valore importato dal CSV).
        ContrattoCalcoloResult calcolo = contrattoCalcolatore.calcola(
                b.getFkTenantId(),
                b.getFkPropertyId(),
                b.getFkCanaleOtaId(),
                b.getGrossAmount(),
                b.getOtaCommissionAmount(),
                b.getNights(),
                b.getGuests());

        // Ricalcolo tassa di soggiorno per booking importati senza tassa (comune con regola attiva).
        if (safeVal(b.getTouristTaxAmount()).signum() == 0
                && !Boolean.TRUE.equals(b.getTouristTaxIncludedInGross())) {
            BigDecimal tassa = touristTaxService.calcolaPerBooking(
                    b.getFkTenantId(),
                    prop != null ? prop.getCity() : null,
                    b.getCheckinDate(),
                    b.getNights(),
                    b.getGuests(),
                    b.getTouristTaxIncludedInGross());
            if (tassa != null && tassa.signum() > 0) {
                bookingDAO.updateTouristTax(b.getId(), tassa);
                b.setTouristTaxAmount(tassa);
            }
        }

        SplitEconomicoDTO split = SplitEconomicoDTO.builder()
                .grossAmount(b.getGrossAmount())
                .otaCommissionAmount(calcolo.getOtaCommissionAmount())
                .cleaningAmount(calcolo.getCleaningAmount())
                .pmFeeAmount(calcolo.getPmFeeAmount())
                .ownerNetAmount(calcolo.getOwnerNetAmount())
                .withholdingAmount(b.getWithholdingAmount())   // mantieni il valore del DB
                .aliquotaRitenuta(b.getAliquotaRitenuta())     // % storicizzata sul booking
                .liquidazioneOwner(calcolo.getLiquidazioneOwner())
                .imponibileFatturaPm(calcolo.getImponibileFatturaPm())
                .ivaScorporataPm(calcolo.getIvaScorporata())
                .fatturaPmTotale(calcolo.getFatturaPmTotale())
                .warnings(calcolo.getWarnings())
                .calcoloCompleto(calcolo.getCalcoloCompleto())
                .touristTaxAmount(b.getTouristTaxAmount())
                .touristTaxIncludedInGross(b.getTouristTaxIncludedInGross())
                .build();

        BookingDetailDTO dto = BookingDetailDTO.builder()
                .id(b.getId())
                .fkTenantId(b.getFkTenantId())
                .fkPropertyId(b.getFkPropertyId())
                .fkOwnerId(b.getFkOwnerId())
                .externalBookingId(b.getExternalBookingId())
                .guestName(b.getGuestName())
                .guestTaxCode(b.getGuestTaxCode())
                .guestBirthDate(b.getGuestBirthDate())
                .guestSesso(b.getGuestSesso())
                .guestBirthPlace(b.getGuestBirthPlace())
                .guestBirthBelfiore(b.getGuestBirthBelfiore())
                .guestDocType(b.getGuestDocType())
                .guestDocNumber(b.getGuestDocNumber())
                .guestCountry(b.getGuestCountry())
                .guestAddress(b.getGuestAddress())
                .guestPhone(b.getGuestPhone())
                .propertyName(prop != null ? prop.getDisplayName() : null)
                .ownerName(resolveOwnerName(b.getFkOwnerId(), maps.ownersById))
                .channelName(canale != null ? canale.getNome() : null)
                .regimeFiscaleCodice(regime != null ? regime.getCodice() : null)
                .checkinDate(b.getCheckinDate())
                .checkoutDate(b.getCheckoutDate())
                .nights(b.getNights())
                .guests(b.getGuests())
                .grossAmount(b.getGrossAmount())
                .otaCommissionAmount(b.getOtaCommissionAmount())
                .cleaningAmount(b.getCleaningAmount())
                .pmFeeAmount(b.getPmFeeAmount())
                .ownerNetAmount(b.getOwnerNetAmount())
                .withholdingAmount(b.getWithholdingAmount())
                .touristTaxAmount(b.getTouristTaxAmount())
                .touristTaxIncludedInGross(b.getTouristTaxIncludedInGross())
                .touristTaxCollection(b.getTouristTaxCollection())
                .statoPrenotazione(statoCodiceDa(b.getFkStatoPrenotazioneId(), maps.statiPrenotazioneById))
                .paymentStatus(b.getPaymentStatus())
                .documentStatus(computeDocumentStatus(b.getId(), maps))
                .settlementStatus(b.getSettlementStatus())
                .createdAt(b.getCreatedAt())
                .updatedAt(b.getUpdatedAt())
                .splitEconomico(split)
                // dati immobile (per dialog)
                .propertyAddress(prop != null ? prop.getAddress() : null)
                .propertyCity(prop != null ? prop.getCity() : null)
                .propertyInternalCode(prop != null ? prop.getInternalCode() : null)
                // dati proprietario (per dialog)
                .ownerTaxCode(owner != null ? owner.getTaxCode() : null)
                .ownerIban(owner != null ? owner.getIban() : null)
                .ownerEmail(owner != null ? owner.getEmail() : null)
                // dati tenant (per dialog fattura PM)
                .tenantLegalName(tenant != null ? tenant.getLegalName() : null)
                .tenantVatNumber(tenant != null ? tenant.getVatNumber() : null)
                .tenantTaxCode(tenant != null ? tenant.getTaxCode() : null)
                // Indirizzo completo (via + CAP/comune/provincia) per l'anteprima fattura PM
                .tenantLegalAddress(TenantAddressUtils.indirizzoCompleto(tenant))
                .tenantPec(tenant != null ? tenant.getPec() : null)
                // documenti fiscali associati alla prenotazione
                .documenti(mapDocumenti(b.getId(), maps))
                .build();

        // settlementStato/settlementId derivati dal settlement reale associato al booking
        settlementBookingDAO.findSettlementIdByBookingId(b.getId())
                .flatMap(settlementDAO::findById)
                .ifPresent(s -> {
                    dto.setSettlementStato(s.getStato());
                    dto.setSettlementId(s.getId());
                });
        log.debug("BookingService: settlementStato={} per bookingId={}", dto.getSettlementStato(), b.getId());

        return dto;
    }

    private List<FiscalDocumentSummaryDTO> mapDocumenti(Integer bookingId, LookupMaps maps) {
        return fiscalDocumentDAO.findByBookingId(bookingId).stream()
                .map(d -> toDocumentSummaryDTO(d, maps))
                .toList();
    }

    private FiscalDocumentSummaryDTO toDocumentSummaryDTO(FiscalDocument d, LookupMaps maps) {
        TipoDocumento tipo = d.getFkTipoDocumentoId() != null
                ? maps.tipiDocumentoById.get(d.getFkTipoDocumentoId()) : null;
        return FiscalDocumentSummaryDTO.builder()
                .id(d.getId())
                .documentNumber(d.getDocumentNumber())
                .tipoDocumento(tipo != null ? tipo.getCodice() : null)
                .statoDocumento(statoDocCodiceDa(d.getFkStatoDocumentoId(), maps.statiDocumentoById))
                .dataEmissione(d.getIssueDate())
                .importoTotale(d.getTotalAmount())
                .imponibile(d.getImponibile())
                .ritenutaAmount(d.getRitenutaAmount())
                .bolloAmount(d.getBolloAmount())
                .aliquotaIva(d.getAliquotaIva())
                .canoneLocazione(d.getCanoneLocazione())
                .fkDocumentoCollegatoId(d.getFkDocumentoCollegatoId())
                .build();
    }

    private record LookupMaps(
            Map<Integer, Property> propertiesById,
            Map<Integer, OwnerProfile> ownersById,
            Map<Integer, CanaleOta> canaliById,
            Map<Integer, StatoPrenotazione> statiPrenotazioneById,
            Map<Integer, StatoDocumento> statiDocumentoById,
            Map<Integer, RegimeFiscale> regimiById,
            Map<Integer, TipoDocumento> tipiDocumentoById,
            Tenant tenant) {
    }
}
