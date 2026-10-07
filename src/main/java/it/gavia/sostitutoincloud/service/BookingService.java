package it.gavia.sostitutoincloud.service;

import it.gavia.sostitutoincloud.dao.BookingDAO;
import it.gavia.sostitutoincloud.dao.BookingSplitEconomicoDAO;
import it.gavia.sostitutoincloud.dao.CanaleOtaDAO;
import it.gavia.sostitutoincloud.dao.FiscalDocumentDAO;
import it.gavia.sostitutoincloud.dao.FiscalDocumentRigaNdcDAO;
import it.gavia.sostitutoincloud.dao.F24RecordDAO;
import it.gavia.sostitutoincloud.dao.OwnerProfileDAO;
import it.gavia.sostitutoincloud.dao.PropertyContractRuleDAO;
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
import it.gavia.sostitutoincloud.dto.booking.BookingPageDTO;
import it.gavia.sostitutoincloud.dto.booking.BookingListDTO;
import it.gavia.sostitutoincloud.dto.booking.BookingSplitEconomicoDTO;
import it.gavia.sostitutoincloud.dto.booking.BookingUpdateSplitDTO;
import it.gavia.sostitutoincloud.dto.booking.BookingVoceExtraDTO;
import it.gavia.sostitutoincloud.dto.booking.ContrattoCalcoloResult;
import it.gavia.sostitutoincloud.dto.booking.GuestUpdateDTO;
import it.gavia.sostitutoincloud.dto.booking.SplitEconomicoDTO;
import it.gavia.sostitutoincloud.dto.fiscal.RigaNdcDTO;
import it.gavia.sostitutoincloud.dto.document.FiscalDocumentSummaryDTO;
import it.gavia.sostitutoincloud.model.Booking;
import it.gavia.sostitutoincloud.model.BookingSplitEconomico;
import it.gavia.sostitutoincloud.model.CanaleOta;
import it.gavia.sostitutoincloud.model.F24Record;
import it.gavia.sostitutoincloud.model.FiscalDocument;
import it.gavia.sostitutoincloud.model.OwnerProfile;
import it.gavia.sostitutoincloud.model.Property;
import it.gavia.sostitutoincloud.model.RegimeFiscale;
import it.gavia.sostitutoincloud.model.StatoDocumento;
import it.gavia.sostitutoincloud.model.StatoPrenotazione;
import it.gavia.sostitutoincloud.model.Tenant;
import it.gavia.sostitutoincloud.model.TipoDocumento;
import it.gavia.sostitutoincloud.util.NazioneUtils;
import it.gavia.sostitutoincloud.util.SecurityUtils;
import it.gavia.sostitutoincloud.util.TenantAddressUtils;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Collator;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@Log4j2
public class BookingService {

    private static final Set<String> STATI_ESCLUSI_DA_COMPLETARE =
            Set.of("doc_issued", "settled", "cancelled", "stornata");

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
    private final BookingSplitEconomicoDAO splitEconomicoDAO;
    private final TenantSettingsService tenantSettingsService;

    // Limite di sicurezza sul parametro size della lista prenotazioni
    private static final int MAX_PAGE_SIZE = 1000;
    private final PropertyContractRuleDAO contractRuleDAO;
    private final FiscalDocumentRigaNdcDAO rigaNdcDAO;
    private final F24RecordDAO f24RecordDAO;

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
                          AuditService auditService,
                          BookingSplitEconomicoDAO splitEconomicoDAO,
                          TenantSettingsService tenantSettingsService,
                          PropertyContractRuleDAO contractRuleDAO,
                          FiscalDocumentRigaNdcDAO rigaNdcDAO,
                          F24RecordDAO f24RecordDAO) {
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
        this.splitEconomicoDAO = splitEconomicoDAO;
        this.tenantSettingsService = tenantSettingsService;
        this.contractRuleDAO = contractRuleDAO;
        this.rigaNdcDAO = rigaNdcDAO;
        this.f24RecordDAO = f24RecordDAO;
    }

    /**
     * Lista prenotazioni paginata (GET /api/bookings), stesso schema della lista documenti:
     * filtri, ordinamento e paginazione in memoria sui booking del tenant; totalElements conta
     * le prenotazioni filtrate, non la sola pagina. size <= 0 → tenant_settings.page_size.
     */
    public BookingPageDTO findPage(Integer tenantId, BookingFilterDTO filter, int page, int size) {
        int effectiveSize = size > 0 ? Math.min(size, MAX_PAGE_SIZE) : tenantSettingsService.getPageSize(tenantId);
        int pageNum = Math.max(page, 0);
        LookupMaps maps = buildLookupMaps(tenantId);

        Stream<Booking> stream = bookingDAO.findByTenantId(tenantId).stream();
        stream = applyStatusFilter(stream, filter.getStatus(), maps);
        stream = applyChannelFilter(stream, filter.getChannel(), maps);
        if (filter.getPropertyId() != null) {
            stream = stream.filter(b -> filter.getPropertyId().equals(b.getFkPropertyId()));
        }
        if (filter.getOwnerId() != null) {
            stream = stream.filter(b -> filter.getOwnerId().equals(b.getFkOwnerId()));
        }
        // Filtro date sul solo check-in (come la UI): il booking rientra se l'arrivo cade nel range
        if (filter.getDataFrom() != null) {
            stream = stream.filter(b -> b.getCheckinDate() != null && !b.getCheckinDate().isBefore(filter.getDataFrom()));
        }
        if (filter.getDataTo() != null) {
            stream = stream.filter(b -> b.getCheckinDate() != null && !b.getCheckinDate().isAfter(filter.getDataTo()));
        }

        Map<Integer, List<FiscalDocument>> docsByBooking = documentiPerBooking(tenantId);
        // Ricerca sui campi mostrati in lista (nomi risolti): dopo il mapping in DTO
        List<BookingListDTO> tutti = stream
                .map(b -> toListDTO(b, maps, docsByBooking))
                .filter(dto -> corrispondeRicerca(dto, filter.getQ()))
                .sorted(ordinamento(filter.getSort(), filter.getDir()))
                .toList();

        int totalPages = (int) Math.ceil(tutti.size() / (double) effectiveSize);
        int from = (int) Math.min((long) pageNum * effectiveSize, tutti.size());
        int to = Math.min(from + effectiveSize, tutti.size());
        log.info("BookingService.findPage() - tenantId={} filtro={} page={} size={} totale={}",
                tenantId, filter, pageNum, effectiveSize, tutti.size());
        return BookingPageDTO.builder()
                .content(new ArrayList<>(tutti.subList(from, to)))
                .page(pageNum)
                .size(effectiveSize)
                .totalElements(tutti.size())
                .totalPages(totalPages)
                .build();
    }

    private boolean corrispondeRicerca(BookingListDTO b, String search) {
        if (search == null || search.isBlank()) return true;
        String q = search.trim().toLowerCase(Locale.ROOT);
        return Stream.of(b.getExternalBookingId(), b.getGuestName(), b.getPropertyName(),
                        b.getOwnerName(), b.getChannelName())
                .anyMatch(v -> v != null && v.toLowerCase(Locale.ROOT).contains(q));
    }

    /**
     * Ordinamento: campo richiesto, valori nulli sempre in fondo, poi id decrescente per un
     * ordine stabile tra le pagine. Campo sconosciuto → data check-in (default decrescente).
     */
    private Comparator<BookingListDTO> ordinamento(String sort, String dir) {
        boolean asc = "asc".equalsIgnoreCase(dir);
        Collator collator = Collator.getInstance(Locale.ITALIAN);
        collator.setStrength(Collator.SECONDARY);   // ignora maiuscole/minuscole
        Comparator<String> testo = asc ? collator::compare : (a, b) -> collator.compare(b, a);
        Comparator<LocalDate> data = asc ? Comparator.naturalOrder() : Comparator.reverseOrder();
        Comparator<BookingListDTO> primario = switch (sort == null ? "" : sort) {
            case "externalBookingId" -> Comparator.comparing(BookingListDTO::getExternalBookingId, Comparator.nullsLast(testo));
            case "guestName" -> Comparator.comparing(BookingListDTO::getGuestName, Comparator.nullsLast(testo));
            case "propertyName" -> Comparator.comparing(BookingListDTO::getPropertyName, Comparator.nullsLast(testo));
            case "ownerName" -> Comparator.comparing(BookingListDTO::getOwnerName, Comparator.nullsLast(testo));
            case "channelName" -> Comparator.comparing(BookingListDTO::getChannelName, Comparator.nullsLast(testo));
            case "statoPrenotazione" -> Comparator.comparing(BookingListDTO::getStatoPrenotazione, Comparator.nullsLast(testo));
            case "checkoutDate" -> Comparator.comparing(BookingListDTO::getCheckoutDate, Comparator.nullsLast(data));
            case "nights" -> Comparator.comparing(BookingListDTO::getNights,
                    Comparator.nullsLast(asc ? Comparator.<Integer>naturalOrder() : Comparator.<Integer>reverseOrder()));
            case "grossAmount" -> Comparator.comparing(BookingListDTO::getGrossAmount,
                    Comparator.nullsLast(asc ? Comparator.<BigDecimal>naturalOrder() : Comparator.<BigDecimal>reverseOrder()));
            default -> Comparator.comparing(BookingListDTO::getCheckinDate, Comparator.nullsLast(data));
        };
        return primario.thenComparing(BookingListDTO::getId, Comparator.nullsLast(Comparator.<Integer>reverseOrder()));
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
        Map<Integer, List<FiscalDocument>> docsByBooking = documentiPerBooking(tenantId);
        List<BookingListDTO> result = bookingDAO.findByOwnerAndTenant(tenantId, ownerId).stream()
                .map(b -> toListDTO(b, maps, docsByBooking))
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
                        booking.getOtaCommissionAmount(), null, null, null, null,
                        booking.getNights(), booking.getGuests(), List.of(), false);
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

    /**
     * Modifica gli input dello split economico e lo ricalcola: flag "tassa inclusa nel lordo"
     * e override della commissione OTA.
     *
     * <p>PATCH parziale sul flag (null = invariato), ma NON sull'override: null significa
     * "nessun override", cioè ritorno alla commissione delle regole di contratto. È
     * l'unico modo per annullare un override già applicato.
     *
     * <p>Bloccato se esistono documenti fiscali emessi: gli importi sono già stampati su
     * fattura/ricevuta e cambiarli renderebbe il documento incoerente col DB.
     */
    @Transactional
    public BookingDetailDTO updateSplit(Integer tenantId, Integer bookingId, BookingUpdateSplitDTO dto) {
        Booking booking = bookingDAO.findById(bookingId)
                .filter(b -> tenantId.equals(b.getFkTenantId()))
                .orElseThrow(() -> new NoSuchElementException("Booking non trovato: id=" + bookingId));

        if (!fiscalDocumentDAO.findByBookingId(bookingId).isEmpty()) {
            throw new IllegalStateException(
                    "Impossibile modificare: esistono documenti fiscali emessi per questa prenotazione");
        }

        boolean taxIncluded = dto.getTouristTaxIncludedInGross() != null
                ? dto.getTouristTaxIncludedInGross()
                : Boolean.TRUE.equals(booking.getTouristTaxIncludedInGross());
        BigDecimal otaOverride = dto.getOtaCommissionOverride();

        // Tassa: quella impostata (dal PM o dal file, ripassata dal frontend a ogni PATCH) se
        // presente, altrimenti la regola del comune. Il flag dice dove si trova l'importo
        // (dentro o fuori dal lordo), non se esiste.
        BigDecimal nuovaTassa;
        if (dto.getTouristTaxOverride() != null) {
            nuovaTassa = dto.getTouristTaxOverride().setScale(2, RoundingMode.HALF_UP);
        } else {
            String comune = propertyDAO.findById(booking.getFkPropertyId())
                    .map(Property::getCity).orElse(null);
            nuovaTassa = safeVal(touristTaxService.calcolaPerBooking(
                    tenantId, comune, booking.getCheckinDate(), booking.getNights(), booking.getGuests()));
        }

        // Tassa inclusa → va scorporata dalla base: è incassata per conto del Comune, non è
        // reddito del proprietario e non deve entrare nella base della ritenuta.
        BigDecimal grossPerCalcolo = taxIncluded
                ? safeVal(booking.getGrossAmount()).subtract(nuovaTassa)
                : booking.getGrossAmount();

        // Righe attuali: le voci extra in fattura PM entrano nella base 'percentuale_netto' e
        // nel totale fattura; servono anche per il source delle voci ricalcolate (sotto).
        List<BookingSplitEconomico> righeEsistenti = splitEconomicoDAO.findByBookingId(bookingId);

        // Override NETTI: il frontend ripassa l'imponibile delle righe (overridesDaImport=false)
        ContrattoCalcoloResult calcolo = contrattoCalcolatore.calcola(
                tenantId,
                booking.getFkPropertyId(),
                booking.getFkCanaleOtaId(),
                grossPerCalcolo,
                otaOverride,
                dto.getCleaningOverride(),
                dto.getPulizieOverride(),
                dto.getCambioBiancheriaOverride(),
                dto.getPmFeeOverride(),
                booking.getNights(),
                booking.getGuests(),
                extraImponibiliInFattura(righeEsistenti),
                false);

        bookingDAO.updateSplit(bookingId, tenantId,
                taxIncluded,
                nuovaTassa,
                calcolo.getOtaCommissionAmount(),
                calcolo.getCleaningAmount(),
                calcolo.getPmFeeAmount(),
                calcolo.getOwnerNetAmount(),
                calcolo.getWithholdingAmount());

        // Righe split riallineate al ricalcolo, con il source di ogni voce che può essere
        // impostata a mano (OTA, pulizie, PM): vedi sourceVoce().
        String otaSource = sourceVoce(righeEsistenti, "commissione_ota",
                calcolo.getOtaCommissionAmount(), otaOverride, calcolo.getFkRegolaOtaId());
        String pulizieSource = sourceVoceConRegola(righeEsistenti, "pulizie",
                calcolo.getPulizieAmount(), calcolo.getFkRegolaPulizieId());
        String cambioBiancheriaSource = sourceVoceConRegola(righeEsistenti, "cambio_biancheria",
                calcolo.getCambioBiancheriaAmount(), calcolo.getFkRegolaCambioBiancheriaId());
        String pmSource = sourceVoce(righeEsistenti, "commissione_pm",
                calcolo.getPmFeeAmount(), dto.getPmFeeOverride(), calcolo.getFkRegolaPmId());
        // Tassa: nessuna FK di contratto, decide solo l'override (dalla regola del comune = 'calcolato')
        String tassaSource = dto.getTouristTaxOverride() == null
                ? "calcolato"
                : sourceVoceConRegola(righeEsistenti, "tassa_soggiorno", nuovaTassa, null);
        String canaleName = booking.getFkCanaleOtaId() != null
                ? canaleOtaDAO.findById(booking.getFkCanaleOtaId()).map(CanaleOta::getNome).orElse("OTA")
                : null;
        // Commissione originale dal file: valore storico dell'import. Le righe calcolate vengono
        // riscritte qui sotto, quindi si riporta quello già salvato sulla riga OTA.
        BigDecimal otaOriginaleFile = righeEsistenti.stream()
                .filter(r -> "commissione_ota".equals(r.getTipoVoce()))
                .map(BookingSplitEconomico::getImportoOriginaleFile)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
        popolaSplitEconomico(bookingId, tenantId, calcolo, nuovaTassa, taxIncluded,
                canaleName, SecurityUtils.getCurrentUtenteId(), otaSource, pulizieSource, cambioBiancheriaSource, pmSource,
                tassaSource, otaOriginaleFile);

        aggiornaStato(bookingId);
        log.info("BookingService.updateSplit() - id={} taxIncluded={} otaOverride={} cleaningOverride={} pulizieOverride={} cambioBiancheriaOverride={} pmFeeOverride={}",
                bookingId, taxIncluded, otaOverride, dto.getCleaningOverride(), dto.getPulizieOverride(),
                dto.getCambioBiancheriaOverride(), dto.getPmFeeOverride());
        return findById(tenantId, bookingId)
                .orElseThrow(() -> new NoSuchElementException("Booking non trovato: id=" + bookingId));
    }

    /**
     * Source della riga split di una voce ricalcolata. Se l'importo non cambia si tiene quello
     * della riga esistente: il frontend ripassa gli importi manuali attuali a ogni PATCH (per
     * l'OTA sempre, anche al solo cambio del flag tassa), e un importo da file ('import') o già
     * 'manuale' non deve cambiare di stato. Altrimenti 'manuale' se l'importo arriva da un
     * override senza regola di riferimento, 'calcolato' se viene dalle regole.
     */
    private String sourceVoce(List<BookingSplitEconomico> righeEsistenti, String tipoVoce,
                              BigDecimal nuovoImporto, BigDecimal override, Integer fkRegola) {
        return righeEsistenti.stream()
                .filter(r -> tipoVoce.equals(r.getTipoVoce()))
                .filter(r -> r.getImporto() != null && nuovoImporto != null
                        && r.getImporto().compareTo(nuovoImporto) == 0)
                .map(BookingSplitEconomico::getSource)
                .findFirst()
                .orElse(override != null && fkRegola == null ? "manuale" : "calcolato");
    }

    /**
     * Source della riga pulizie / cambio biancheria: 'calcolato' se l'importo viene dalla regola
     * (FK valorizzata). Senza regola è un override: si tiene il source della riga esistente se
     * l'importo non cambia ('import' da file o 'manuale' ripassato dal frontend a ogni PATCH),
     * altrimenti 'manuale'. Diversamente da sourceVoce(), la FK decide per prima: ripristinando
     * la regola la riga torna 'calcolato' anche se l'importo coincide con quello manuale.
     */
    private String sourceVoceConRegola(List<BookingSplitEconomico> righeEsistenti, String tipoVoce,
                                       BigDecimal nuovoImporto, Integer fkRegola) {
        if (fkRegola != null) return "calcolato";
        return righeEsistenti.stream()
                .filter(r -> tipoVoce.equals(r.getTipoVoce()))
                .filter(r -> r.getImporto() != null && nuovoImporto != null
                        && r.getImporto().compareTo(nuovoImporto) == 0)
                .map(BookingSplitEconomico::getSource)
                .filter(s -> !"calcolato".equals(s))
                .findFirst()
                .orElse("manuale");
    }

    /** Regime fiscale del PM dai settings del tenant (default RF01, come il calcolatore). */
    private String regimeFiscalePm(Integer tenantId) {
        String r = tenantSettingsService.getSettings(tenantId).getRegimeFiscalePm();
        return r != null ? r : "RF01";
    }

    /**
     * Imponibile (netto) della riga split di quel tipo se è stata impostata a mano ('manuale'),
     * altrimenti null. Netto perché il calcolatore tratta gli override (non da import) come netti.
     */
    private BigDecimal importoSeManuale(List<BookingSplitEconomico> righe, String tipoVoce) {
        return righe.stream()
                .filter(r -> tipoVoce.equals(r.getTipoVoce()) && "manuale".equals(r.getSource()))
                .map(this::nettoRiga)
                .findFirst()
                .orElse(null);
    }

    /** Come importoSeManuale(), ma vale anche per l'importo arrivato dal file ('import'). */
    private BigDecimal importoSeImpostato(List<BookingSplitEconomico> righe, String tipoVoce) {
        return righe.stream()
                .filter(r -> tipoVoce.equals(r.getTipoVoce())
                        && ("manuale".equals(r.getSource()) || "import".equals(r.getSource())))
                .map(this::nettoRiga)
                .findFirst()
                .orElse(null);
    }

    /**
     * Imponibile della riga. Le righe create prima della migration 020 hanno imponibile NULL:
     * lo si ricava scorporando l'IVA dal lordo, importo / (1 + aliquota_iva/100).
     */
    private BigDecimal nettoRiga(BookingSplitEconomico r) {
        if (r.getImponibile() != null) return r.getImponibile();
        BigDecimal importo = safeVal(r.getImporto());
        BigDecimal aliquota = safeVal(r.getAliquotaIva());
        if (aliquota.signum() == 0) return importo;
        return importo.divide(BigDecimal.ONE.add(aliquota.divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP)),
                2, RoundingMode.HALF_UP);
    }

    /** Imponibili delle voci extra in fattura PM, da passare al calcolatore. */
    private List<BigDecimal> extraImponibiliInFattura(List<BookingSplitEconomico> righe) {
        return righe.stream()
                .filter(r -> TIPO_VOCE_EXTRA.equals(r.getTipoVoce()) && Boolean.TRUE.equals(r.getIncludeInFatturaPm()))
                .map(this::nettoRiga)
                .collect(Collectors.toList());
    }

    /** % IVA delle voci PM dal regime del tenant: 0 in RF19 (forfettario), 22 altrimenti. */
    private BigDecimal aliquotaIvaPm(Integer tenantId) {
        return "RF19".equalsIgnoreCase(regimeFiscalePm(tenantId)) ? BigDecimal.ZERO : new BigDecimal("22.00");
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

        // 4. Tassa di soggiorno: calcolata PRIMA dello split, perché se è inclusa nel lordo
        //    va scorporata dalla base di calcolo.
        boolean tassaInclusa = Boolean.TRUE.equals(dto.getTouristTaxIncludedInGross());
        BigDecimal touristTax = touristTaxService.calcolaPerBooking(
                tenantId, property.getCity(), dto.getCheckinDate(), nights, dto.getGuests());

        // 5. Split economico dalle regole del contratto. Nessun override di commissione OTA:
        //    l'inserimento manuale non ha un dato reale del canale da cui partire.
        //    La tassa di soggiorno è incassata per conto del Comune: non è reddito del
        //    proprietario e non deve finire nella base della ritenuta.
        BigDecimal grossPerCalcolo = tassaInclusa
                ? dto.getGrossAmount().subtract(safeVal(touristTax))
                : dto.getGrossAmount();

        ContrattoCalcoloResult calcolo = contrattoCalcolatore.calcola(
                tenantId,
                dto.getFkPropertyId(),
                dto.getFkCanaleOtaId(),
                grossPerCalcolo,
                null,
                null,
                null,
                null,
                null,
                nights,
                dto.getGuests(),
                List.of(),   // nessuna voce extra alla creazione
                false);

        if (calcolo.getWarnings() != null && !calcolo.getWarnings().isEmpty()) {
            calcolo.getWarnings().forEach(w ->
                    log.warn("BookingService.createManuale() - booking {}: {}", extId, w));
        }

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
                .touristTaxIncludedInGross(tassaInclusa)
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

        // 8. Righe dello split economico (booking_split_economico) + total_costi_pm.
        String canaleName = dto.getFkCanaleOtaId() != null
                ? canaleOtaDAO.findById(dto.getFkCanaleOtaId())
                        .map(CanaleOta::getNome)
                        .orElse("OTA")
                : null;
        popolaSplitEconomico(saved.getId(), tenantId, calcolo, touristTax, tassaInclusa,
                canaleName, SecurityUtils.getCurrentUtenteId(), "calcolato");

        auditService.log("booking.create.manual", "Booking", saved.getId(),
                "Inserimento manuale: " + extId);

        BookingDetailDTO result = findById(tenantId, saved.getId())
                .orElseThrow(() -> new NoSuchElementException("Booking non trovato dopo l'inserimento: id=" + saved.getId()));
        log.info("BookingService.createManuale() - id={} stato={}", result.getId(), result.getStatoPrenotazione());
        return result;
    }

    /**
     * Riscrive da zero le righe booking_split_economico di una prenotazione a partire dal
     * risultato del calcolatore (che resta stateless: scrivere le righe è compito di chi
     * chiama calcola()), poi aggiorna booking.total_costi_pm.
     *
     * <p>Righe create solo per le voci con importo &gt; 0: commissione OTA, pulizie, cambio
     * biancheria, commissione PM (in fattura PM, IVA 22%) e tassa di soggiorno (fuori fattura,
     * IVA 0). Pulizie e cambio biancheria hanno una riga anche a importo 0 se vengono da una
     * regola (FK valorizzata). Netto proprietario e ritenuta non sono voci di costo.
     *
     * @param otaSource source della riga OTA ('calcolato' | 'import' | 'manuale'): può arrivare
     *                  da fuori (file di import o importo forzato dal PM).
     *                  Pulizie e cambio biancheria: 'calcolato' se vengono da una regola,
     *                  altrimenti 'manuale'. PM 'calcolato' (per le voci impostate a mano o da
     *                  file si usa l'overload completo); la tassa è sempre 'calcolato'.
     */
    public void popolaSplitEconomico(Integer bookingId,
                                     Integer tenantId,
                                     ContrattoCalcoloResult calcolo,
                                     BigDecimal touristTaxAmount,
                                     Boolean touristTaxIncludedInGross,
                                     String canaleName,
                                     Integer utenteId,
                                     String otaSource) {
        popolaSplitEconomico(bookingId, tenantId, calcolo, touristTaxAmount, touristTaxIncludedInGross,
                canaleName, utenteId, otaSource,
                calcolo.getFkRegolaPulizieId() != null ? "calcolato" : "manuale",
                calcolo.getFkRegolaCambioBiancheriaId() != null ? "calcolato" : "manuale",
                "calcolato",
                "calcolato",
                null);   // nessun valore dal file: non è un import
    }

    /**
     * Come sopra, con il source di ogni voce impostabile a mano: 'manuale' quando l'importo è
     * stato impostato dal PM (override di updateSplit()), 'import' quando arriva dal file.
     * Vale anche per la tassa di soggiorno (tassaSource).
     *
     * @param importoOriginaleFile commissione OTA grezza dal file di import (prima della
     *                             trasformazione IVA), salvata sulla riga OTA; null se non da import.
     *                             Le righe calcolate si riscrivono a ogni ricalcolo: chi ricalcola
     *                             un booking importato deve ripassare il valore già salvato.
     */
    public void popolaSplitEconomico(Integer bookingId,
                                     Integer tenantId,
                                     ContrattoCalcoloResult calcolo,
                                     BigDecimal touristTaxAmount,
                                     Boolean touristTaxIncludedInGross,
                                     String canaleName,
                                     Integer utenteId,
                                     String otaSource,
                                     String pulizieSource,
                                     String cambioBiancheriaSource,
                                     String pmSource,
                                     String tassaSource,
                                     BigDecimal importoOriginaleFile) {
        // 1. Reset delle sole righe calcolate: le voci 'extra' inserite a mano dal PM restano
        //    (con un reset completo sparirebbero a ogni Ricalcola / cambio flag / override OTA).
        splitEconomicoDAO.deleteCalcolateByBookingId(bookingId);

        // IVA delle voci PM dal calcolatore: 22 in RF01, 0 in RF19 (forfettario).
        // Ogni riga salva netto (imponibile) e lordo (importo = imponibile × (1 + IVA)).
        BigDecimal ivaPm = calcolo.getAliquotaIvaPm() != null ? calcolo.getAliquotaIvaPm() : new BigDecimal("22.00");
        int righe = 0;

        // 2. Commissione OTA
        if (positivo(calcolo.getOtaCommissionAmount())) {
            BookingSplitEconomico rigaOta = rigaSplit(bookingId, tenantId, calcolo.getFkRegolaOtaId(),
                    // "Riaddebito": costo del canale girato al cliente. La descrizione finisce
                    // così com'è nelle righe di fattura PM (dettaglio, PDF, XML SDI).
                    "commissione_ota", "Riaddebito commissione " + (canaleName != null ? canaleName : "OTA"),
                    calcolo.getOtaCommissionAmount(), calcolo.getOtaImponibile(), ivaPm, true, 10, otaSource, utenteId);
            // Valore grezzo della commissione dal file (riconciliazione): solo sulla riga OTA
            rigaOta.setImportoOriginaleFile(importoOriginaleFile);
            splitEconomicoDAO.insert(rigaOta);
            righe++;
        }
        // 3. Pulizie e cambio biancheria: due righe separate, ognuna con la sua regola.
        //    Con una regola la riga c'è anche a importo 0 (es. regola a valore 0).
        if (positivo(calcolo.getPulizieAmount()) || calcolo.getFkRegolaPulizieId() != null) {
            splitEconomicoDAO.insert(rigaSplit(bookingId, tenantId, calcolo.getFkRegolaPulizieId(),
                    "pulizie", "Riaddebito pulizie",
                    safeVal(calcolo.getPulizieAmount()), safeVal(calcolo.getPulizieImponibile()), ivaPm, true, 20,
                    pulizieSource, utenteId));
            righe++;
        }
        if (positivo(calcolo.getCambioBiancheriaAmount()) || calcolo.getFkRegolaCambioBiancheriaId() != null) {
            splitEconomicoDAO.insert(rigaSplit(bookingId, tenantId, calcolo.getFkRegolaCambioBiancheriaId(),
                    "cambio_biancheria", "Cambio biancheria",
                    safeVal(calcolo.getCambioBiancheriaAmount()), safeVal(calcolo.getCambioBiancheriaImponibile()),
                    ivaPm, true, 25, cambioBiancheriaSource, utenteId));
            righe++;
        }
        // 4. Commissione PM
        if (positivo(calcolo.getPmFeeAmount())) {
            splitEconomicoDAO.insert(rigaSplit(bookingId, tenantId, calcolo.getFkRegolaPmId(),
                    // Nessun "Riaddebito": è il compenso del PM, non un costo girato al cliente.
                    "commissione_pm", "Provvigione PM",
                    calcolo.getPmFeeAmount(), calcolo.getPmImponibile(), ivaPm, true, ORDINE_PM, pmSource, utenteId));
            righe++;
        }
        // 5. Tassa di soggiorno: incassata per conto del Comune, non entra nella fattura PM.
        //    Impostata a mano o da file la riga c'è anche a 0 (es. ospiti esenti): è il segno
        //    che toDetailDTO() non deve ricalcolarla dalla regola del comune.
        if (positivo(touristTaxAmount) || !"calcolato".equals(tassaSource)) {
            splitEconomicoDAO.insert(rigaSplit(bookingId, tenantId, null,
                    "tassa_soggiorno", "Tassa di soggiorno",
                    // IVA 0: netto = lordo
                    safeVal(touristTaxAmount), safeVal(touristTaxAmount), BigDecimal.ZERO, false, 40,
                    tassaSource, utenteId));
            righe++;
        }

        // 6. total_costi_pm, netto proprietario e ritenuta dalle righe (comprese le voci extra
        //    già presenti, che il reset del punto 1 non tocca)
        BigDecimal totale = ricalcolaNettoDaSplit(bookingId, tenantId);

        // 7. Log
        log.info("BookingService.popolaSplitEconomico() - bookingId={} righe={} totale={} tassaInclusa={}",
                bookingId, righe, totale, touristTaxIncludedInGross);
    }

    /**
     * Quadratura dello split sulle righe di booking_split_economico:
     * lordo (al netto della tassa se inclusa) = fattura PM (Σ righe in fattura) + netto proprietario.
     * Netto e ritenuta vengono riscritti sul booking, così le voci extra in fattura PM
     * riducono il netto proprietario e la ritenuta.
     *
     * @return total_costi_pm scritto sul booking
     */
    private BigDecimal ricalcolaNettoDaSplit(Integer bookingId, Integer tenantId) {
        Booking b = bookingDAO.findById(bookingId)
                .filter(x -> tenantId.equals(x.getFkTenantId()))
                .orElseThrow(() -> new NoSuchElementException("Booking non trovato: id=" + bookingId));

        BigDecimal grossBase = Boolean.TRUE.equals(b.getTouristTaxIncludedInGross())
                ? safeVal(b.getGrossAmount()).subtract(safeVal(b.getTouristTaxAmount()))
                : safeVal(b.getGrossAmount());

        BigDecimal totalCostiPm = splitEconomicoDAO.sumImportoByBookingId(bookingId);
        BigDecimal ownerNet = grossBase.subtract(totalCostiPm).setScale(2, RoundingMode.HALF_UP);
        if (ownerNet.signum() < 0) {
            ownerNet = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            log.warn("BookingService.ricalcolaNettoDaSplit() - bookingId={} costi superano il lordo", bookingId);
        }

        BigDecimal aliquota = safeVal(b.getAliquotaRitenuta());
        BigDecimal withholding = ownerNet
                .multiply(aliquota.divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP))
                .setScale(2, RoundingMode.HALF_UP);

        bookingDAO.updateNettoEritenuta(bookingId, tenantId, ownerNet, withholding, totalCostiPm);
        log.info("BookingService.ricalcolaNettoDaSplit() - bookingId={} grossBase={} totalCostiPm={} ownerNet={} withholding={}",
                bookingId, grossBase, totalCostiPm, ownerNet, withholding);
        return totalCostiPm;
    }

    // ── Voci extra dello split (inserite a mano dal PM) ─────────────────────────────

    private static final String TIPO_VOCE_EXTRA = "extra";

    /**
     * Ordinamento della provvigione PM: fisso e alto, così resta dopo tutte le altre voci
     * (OTA 10, pulizie 20, cambio biancheria 25, tassa 40, extra da 50 in su) ed è l'ultima
     * voce dello split prima di "Aggiungi voce". Le extra si numerano escludendo la PM
     * (aggiungiVoceExtra), quindi non la superano.
     */
    private static final int ORDINE_PM = 1000;

    /**
     * Aggiunge una voce extra in fondo allo split (es. "Parcheggio"). Bloccata se esistono
     * documenti fiscali emessi, come ogni altra modifica allo split.
     */
    @Transactional
    public BookingDetailDTO aggiungiVoceExtra(Integer tenantId, Integer bookingId, BookingVoceExtraDTO dto) {
        verificaSplitModificabile(tenantId, bookingId, "Impossibile aggiungere voci: documenti fiscali già emessi");
        validaVoceExtra(dto);

        // In fondo alle righe esistenti, ma prima della provvigione PM (ORDINE_PM): la PM è
        // esclusa dal massimo, altrimenti la nuova extra finirebbe dopo di lei
        int ordine = splitEconomicoDAO.findByBookingId(bookingId).stream()
                .filter(r -> !"commissione_pm".equals(r.getTipoVoce()))
                .mapToInt(BookingSplitEconomico::getOrdinamento)
                .max()
                .orElse(0) + 10;

        Integer utenteId = SecurityUtils.getCurrentUtenteId();
        ImportiVoceExtra importi = importiVoceExtra(tenantId, dto);
        splitEconomicoDAO.insert(BookingSplitEconomico.builder()
                .fkBookingId(bookingId)
                .fkTenantId(tenantId)
                .fkPropertyContractRuleId(null)
                .tipoVoce(TIPO_VOCE_EXTRA)
                .descrizione(dto.getDescrizione().trim())
                .importo(importi.importo())
                .imponibile(importi.imponibile())
                .aliquotaIva(importi.aliquotaIva())
                .includeInFatturaPm(dto.getIncludeInFatturaPm() != null ? dto.getIncludeInFatturaPm() : true)
                .ordinamento(ordine)
                .source("manuale")
                .createdBy(utenteId)
                .updatedBy(utenteId)
                .build());

        ricalcolaNettoDaSplit(bookingId, tenantId);
        log.info("BookingService.aggiungiVoceExtra() - bookingId={} descrizione={}", bookingId, dto.getDescrizione().trim());
        return ricalcoloDopoVoceExtra(tenantId, bookingId);
    }

    /** Modifica descrizione, importo e (se passato) include_in_fattura_pm di una voce extra. */
    @Transactional
    public BookingDetailDTO aggiornaVoceExtra(Integer tenantId, Integer bookingId, Integer rigaId,
                                              BookingVoceExtraDTO dto) {
        verificaSplitModificabile(tenantId, bookingId, "Impossibile modificare voci: documenti fiscali già emessi");
        validaVoceExtra(dto);
        BookingSplitEconomico riga = caricaVoceExtra(bookingId, rigaId);

        riga.setDescrizione(dto.getDescrizione().trim());
        ImportiVoceExtra importi = importiVoceExtra(tenantId, dto);
        riga.setImporto(importi.importo());
        riga.setImponibile(importi.imponibile());
        riga.setAliquotaIva(importi.aliquotaIva());
        if (dto.getIncludeInFatturaPm() != null) {
            riga.setIncludeInFatturaPm(dto.getIncludeInFatturaPm());
        }
        riga.setUpdatedBy(SecurityUtils.getCurrentUtenteId());
        splitEconomicoDAO.update(riga);

        ricalcolaNettoDaSplit(bookingId, tenantId);
        log.info("BookingService.aggiornaVoceExtra() - bookingId={} rigaId={} descrizione={} imponibile={} importo={}",
                bookingId, rigaId, riga.getDescrizione(), riga.getImponibile(), riga.getImporto());
        return ricalcoloDopoVoceExtra(tenantId, bookingId);
    }

    /** Elimina (soft delete) una voce extra. */
    @Transactional
    public BookingDetailDTO eliminaVoceExtra(Integer tenantId, Integer bookingId, Integer rigaId) {
        verificaSplitModificabile(tenantId, bookingId, "Impossibile eliminare voci: documenti fiscali già emessi");
        caricaVoceExtra(bookingId, rigaId);

        splitEconomicoDAO.softDelete(rigaId, tenantId, SecurityUtils.getCurrentUtenteId());

        ricalcolaNettoDaSplit(bookingId, tenantId);
        log.info("BookingService.eliminaVoceExtra() - bookingId={} rigaId={}", bookingId, rigaId);
        return ricalcoloDopoVoceExtra(tenantId, bookingId);
    }

    /**
     * Dopo una modifica alle voci extra: se la provvigione PM viene da una regola
     * 'percentuale_netto', la sua base (lordo − spese nette, extra in fattura comprese) è
     * cambiata e lo split va ricalcolato. ricalcolaNettoDaSplit() aggiorna solo netto e
     * ritenuta, non la provvigione. Altrimenti si restituisce il dettaglio così com'è.
     *
     * <p>Il ricalcolo passa da updateSplit() con gli stessi override che ripassa il frontend
     * (overrideCorrenti in BookingDetail.tsx), tutti NETTI: OTA sempre, pulizie e cambio
     * biancheria se impostati a mano o da file, PM se manuale, tassa se manuale o da file. Con
     * i soli override 'manuale' l'OTA importata, le pulizie da file e la tassa impostata
     * tornerebbero alle regole.
     */
    private BookingDetailDTO ricalcoloDopoVoceExtra(Integer tenantId, Integer bookingId) {
        List<BookingSplitEconomico> righe = splitEconomicoDAO.findByBookingId(bookingId);
        if (!pmDaPercentualeNetto(righe)) {
            return findById(tenantId, bookingId)
                    .orElseThrow(() -> new NoSuchElementException("Booking non trovato: id=" + bookingId));
        }

        BookingUpdateSplitDTO dto = new BookingUpdateSplitDTO();
        // touristTaxIncludedInGross null = flag invariato
        for (BookingSplitEconomico r : righe) {
            boolean impostata = "manuale".equals(r.getSource()) || "import".equals(r.getSource());
            switch (r.getTipoVoce()) {
                // OTA sempre: con la sola regola il netto non cambia, ma l'importo dal file sì
                case "commissione_ota" -> dto.setOtaCommissionOverride(nettoRiga(r));
                case "pulizie" -> { if (impostata) dto.setPulizieOverride(nettoRiga(r)); }
                case "cambio_biancheria" -> { if (impostata) dto.setCambioBiancheriaOverride(nettoRiga(r)); }
                case "commissione_pm" -> { if ("manuale".equals(r.getSource())) dto.setPmFeeOverride(nettoRiga(r)); }
                case "tassa_soggiorno" -> { if (impostata) dto.setTouristTaxOverride(r.getImporto()); }
                default -> { /* extra: le legge updateSplit() dalle righe */ }
            }
        }
        log.info("BookingService - voce extra modificata, ricalcolo automatico split per bookingId={}", bookingId);
        return updateSplit(tenantId, bookingId, dto);
    }

    /**
     * true se la riga della provvigione PM viene da una regola 'percentuale_netto'. La sola FK
     * non basta: anche 'percentuale_lordo' e 'fisso' hanno una regola collegata, ma la loro
     * base non dipende dalle voci extra.
     */
    private boolean pmDaPercentualeNetto(List<BookingSplitEconomico> righe) {
        return righe.stream()
                .filter(r -> "commissione_pm".equals(r.getTipoVoce()) && r.getFkPropertyContractRuleId() != null)
                .anyMatch(r -> {
                    try {
                        return "percentuale_netto".equals(contractRuleDAO.findById(r.getFkPropertyContractRuleId()).getCalcMode());
                    } catch (RuntimeException e) {
                        // regola cancellata nel frattempo (FK ON DELETE SET NULL non ancora applicata)
                        return false;
                    }
                });
    }

    /**
     * Booking del tenant (altrimenti 404) e senza documenti fiscali emessi (altrimenti 400):
     * gli importi sono già stampati su fattura/ricevuta, stessa regola di updateSplit().
     */
    private void verificaSplitModificabile(Integer tenantId, Integer bookingId, String messaggioDocumenti) {
        bookingDAO.findById(bookingId)
                .filter(b -> tenantId.equals(b.getFkTenantId()))
                .orElseThrow(() -> new NoSuchElementException("Booking non trovato: id=" + bookingId));
        if (!fiscalDocumentDAO.findByBookingId(bookingId).isEmpty()) {
            throw new IllegalStateException(messaggioDocumenti);
        }
    }

    private void validaVoceExtra(BookingVoceExtraDTO dto) {
        if (dto == null || !isNotBlank(dto.getDescrizione())) {
            throw new IllegalArgumentException("Descrizione della voce obbligatoria");
        }
        // imponibile (nuovo) o, per compatibilità, importo lordo (deprecato)
        BigDecimal valore = dto.getImponibile() != null ? dto.getImponibile() : dto.getImporto();
        if (valore == null || valore.signum() <= 0) {
            throw new IllegalArgumentException("L'imponibile della voce deve essere maggiore di zero");
        }
    }

    /** Netto, lordo e % IVA di una voce extra. */
    private record ImportiVoceExtra(BigDecimal imponibile, BigDecimal importo, BigDecimal aliquotaIva) {}

    /**
     * Il PM inserisce l'imponibile (netto): il lordo in fattura è imponibile × (1 + IVA), con
     * l'IVA del regime PM (22% RF01, 0 RF19). Client non ancora aggiornati mandano solo il
     * lordo (importo, deprecato): se ne scorpora l'IVA per ricavare l'imponibile.
     */
    private ImportiVoceExtra importiVoceExtra(Integer tenantId, BookingVoceExtraDTO dto) {
        BigDecimal aliquota = aliquotaIvaPm(tenantId);
        BigDecimal unoPiuIva = BigDecimal.ONE.add(aliquota.divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP));
        if (dto.getImponibile() != null) {
            BigDecimal imponibile = dto.getImponibile().setScale(2, RoundingMode.HALF_UP);
            return new ImportiVoceExtra(imponibile,
                    imponibile.multiply(unoPiuIva).setScale(2, RoundingMode.HALF_UP), aliquota);
        }
        BigDecimal importo = dto.getImporto().setScale(2, RoundingMode.HALF_UP);
        return new ImportiVoceExtra(importo.divide(unoPiuIva, 2, RoundingMode.HALF_UP), importo, aliquota);
    }

    /**
     * Riga attiva della prenotazione (404 se inesistente, eliminata o di un'altra prenotazione)
     * e di tipo 'extra' (400 altrimenti): le voci calcolate si aggiornano solo con Ricalcola.
     */
    private BookingSplitEconomico caricaVoceExtra(Integer bookingId, Integer rigaId) {
        BookingSplitEconomico riga = splitEconomicoDAO.findById(rigaId)
                .filter(r -> bookingId.equals(r.getFkBookingId()))
                .orElseThrow(() -> new NoSuchElementException(
                        "Riga split non trovata: id=" + rigaId + " per la prenotazione " + bookingId));
        if (!TIPO_VOCE_EXTRA.equals(riga.getTipoVoce())) {
            throw new IllegalArgumentException(
                    "Solo le voci extra sono modificabili a mano: le voci calcolate si aggiornano con Ricalcola");
        }
        return riga;
    }

    private BookingSplitEconomico rigaSplit(Integer bookingId, Integer tenantId, Integer fkRegolaId,
                                            String tipoVoce, String descrizione, BigDecimal importo,
                                            BigDecimal imponibile, BigDecimal aliquotaIva, boolean inFatturaPm, int ordinamento,
                                            String source, Integer utenteId) {
        return BookingSplitEconomico.builder()
                .fkBookingId(bookingId)
                .fkTenantId(tenantId)
                .fkPropertyContractRuleId(fkRegolaId)
                .tipoVoce(tipoVoce)
                .descrizione(descrizione)
                .importo(importo)
                .imponibile(imponibile)
                .aliquotaIva(aliquotaIva)
                .includeInFatturaPm(inFatturaPm)
                .ordinamento(ordinamento)
                .source(source)
                .createdBy(utenteId)
                .updatedBy(utenteId)
                .build();
    }

    private boolean positivo(BigDecimal v) {
        return v != null && v.signum() > 0;
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

    /** status: uno o più codici separati da virgola; 'da_completare' vince sugli altri. */
    private Stream<Booking> applyStatusFilter(Stream<Booking> stream, String status, LookupMaps maps) {
        if (status == null || status.isBlank()) return stream;
        Set<String> stati = Arrays.stream(status.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
        if (stati.contains("da_completare")) {
            LocalDate oggi = LocalDate.now();
            return stream.filter(b -> {
                boolean scaduto = b.getCheckoutDate() != null && !b.getCheckoutDate().isAfter(oggi);
                String codice = statoCodiceDa(b.getFkStatoPrenotazioneId(), maps.statiPrenotazioneById);
                return scaduto && !STATI_ESCLUSI_DA_COMPLETARE.contains(codice);
            });
        }
        return stream.filter(b ->
                stati.contains(statoCodiceDa(b.getFkStatoPrenotazioneId(), maps.statiPrenotazioneById)));
    }

    private Stream<Booking> applyChannelFilter(Stream<Booking> stream, String channel, LookupMaps maps) {
        if (channel == null) return stream;
        return stream.filter(b -> {
            CanaleOta c = maps.canaliById.get(b.getFkCanaleOtaId());
            return c != null && channel.equals(c.getCodice());
        });
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
        return computeDocumentStatus(fiscalDocumentDAO.findByBookingId(bookingId), maps);
    }

    private String computeDocumentStatus(List<FiscalDocument> docs, LookupMaps maps) {
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

    /** "Cognome Nome" del proprietario; per le società la ragione sociale (come DocumentPdfService). */
    private String cognomeNomeOwner(OwnerProfile owner) {
        if (owner == null) return null;
        if (owner.getLegalName() != null && !owner.getLegalName().isBlank()) {
            return owner.getLegalName();
        }
        String nome = ((owner.getLastName() != null ? owner.getLastName() : "") + " "
                + (owner.getFirstName() != null ? owner.getFirstName() : "")).trim();
        return nome.isEmpty() ? null : nome;
    }

    private BigDecimal safeVal(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    // ── mapping ─────────────────────────────────────────────────────────────

    /** Documenti del tenant per booking, in una query sola (la lista non interroga per riga). */
    private Map<Integer, List<FiscalDocument>> documentiPerBooking(Integer tenantId) {
        return fiscalDocumentDAO.findByTenantId(tenantId).stream()
                .filter(d -> d.getFkBookingId() != null)
                .collect(Collectors.groupingBy(FiscalDocument::getFkBookingId));
    }

    /** Primo documento del tipo indicato (codice lookup) non annullato. */
    private Optional<FiscalDocument> documentoAttivo(List<FiscalDocument> docs, String codiceTipo, LookupMaps maps) {
        return docs.stream()
                .filter(d -> {
                    TipoDocumento t = d.getFkTipoDocumentoId() != null
                            ? maps.tipiDocumentoById.get(d.getFkTipoDocumentoId()) : null;
                    return t != null && codiceTipo.equals(t.getCodice());
                })
                .filter(d -> !"annullata".equals(statoDocCodiceDa(d.getFkStatoDocumentoId(), maps.statiDocumentoById)))
                .min(Comparator.comparing(FiscalDocument::getId));
    }

    private BookingListDTO toListDTO(Booking b, LookupMaps maps, Map<Integer, List<FiscalDocument>> docsByBooking) {
        Property prop = maps.propertiesById.get(b.getFkPropertyId());
        CanaleOta canale = maps.canaliById.get(b.getFkCanaleOtaId());
        List<FiscalDocument> docs = docsByBooking.getOrDefault(b.getId(), List.of());
        // Codici lookup tipo_documento: fattura (PM), ricevuta (owner), nota_credito
        FiscalDocument fattura = documentoAttivo(docs, "fattura", maps).orElse(null);
        FiscalDocument ricevuta = documentoAttivo(docs, "ricevuta", maps).orElse(null);
        FiscalDocument ndc = documentoAttivo(docs, "nota_credito", maps).orElse(null);
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
                .touristTaxIncludedInGross(b.getTouristTaxIncludedInGross())
                .touristTaxAmount(b.getTouristTaxAmount())
                .statoPrenotazione(statoCodiceDa(b.getFkStatoPrenotazioneId(), maps.statiPrenotazioneById))
                .paymentStatus(b.getPaymentStatus())
                .documentStatus(computeDocumentStatus(docs, maps))
                .settlementStatus(b.getSettlementStatus())
                .createdAt(b.getCreatedAt())
                .fatturaId(fattura != null ? fattura.getId() : null)
                .fatturaNumber(fattura != null ? fattura.getDocumentNumber() : null)
                .ricevutaId(ricevuta != null ? ricevuta.getId() : null)
                .ricevutaNumber(ricevuta != null ? ricevuta.getDocumentNumber() : null)
                .ndcId(ndc != null ? ndc.getId() : null)
                .ndcNumber(ndc != null ? ndc.getDocumentNumber() : null)
                .build();
    }

    private BookingDetailDTO toDetailDTO(Booking b, LookupMaps maps) {
        Property prop = maps.propertiesById.get(b.getFkPropertyId());
        CanaleOta canale = maps.canaliById.get(b.getFkCanaleOtaId());
        RegimeFiscale regime = maps.regimiById.get(b.getFkRegimeFiscaleId());
        OwnerProfile owner = b.getFkOwnerId() != null
                ? maps.ownersById.get(b.getFkOwnerId()) : null;
        Tenant tenant = maps.tenant;

        // Righe split lette una volta: decidono la fonte degli importi (ramo senza documenti),
        // popolano righeSplit del DTO e dicono se la tassa è stata impostata a mano.
        List<BookingSplitEconomico> righeSplit = splitEconomicoDAO.findByBookingId(b.getId());
        boolean tassaImpostata = importoSeImpostato(righeSplit, "tassa_soggiorno") != null;

        // Ricalcolo tassa di soggiorno per i booking importati senza tassa (comune con regola
        // attiva). Va PRIMA dello split: se la tassa è inclusa nel lordo, lo scorporo qui sotto
        // ha bisogno del valore aggiornato. Vale anche per i booking con tassa inclusa, che
        // fino alla migration del calcolo restavano a zero.
        // Non per la tassa impostata a mano o da file: lo 0 è voluto (es. ospiti esenti).
        if (safeVal(b.getTouristTaxAmount()).signum() == 0 && !tassaImpostata) {
            BigDecimal tassa = touristTaxService.calcolaPerBooking(
                    b.getFkTenantId(),
                    prop != null ? prop.getCity() : null,
                    b.getCheckinDate(),
                    b.getNights(),
                    b.getGuests());
            if (tassa != null && tassa.signum() > 0) {
                bookingDAO.updateTouristTax(b.getId(), tassa);
                b.setTouristTaxAmount(tassa);
            }
        }

        // Documenti fiscali della prenotazione: letti una volta sola, servono sia a decidere
        // come costruire lo split sia a popolare la lista del DTO.
        List<FiscalDocument> documentiBooking = fiscalDocumentDAO.findByBookingId(b.getId());

        // Split economico: se esistono documenti fiscali emessi si mostrano i valori
        // STORICI salvati sul booking, che sono quelli con cui i documenti sono stati
        // emessi. Ricalcolarli dalle regole di contratto correnti farebbe divergere il
        // dettaglio dalla ricevuta e dalla fattura ogni volta che una regola cambia.
        // Coerente con updateSplit(), che sui booking con documenti rifiuta le modifiche.
        SplitEconomicoDTO split;
        if (!documentiBooking.isEmpty()) {
            BigDecimal ownerNet = safeVal(b.getOwnerNetAmount());
            BigDecimal ritenuta = safeVal(b.getWithholdingAmount());
            // Lordo dei servizi PM dai valori storici, usato se la fattura PM non è stata emessa.
            // Con righe split è total_costi_pm (voci extra in fattura comprese), cioè quello che
            // DocumentGenerationService fatturerà; senza righe la somma dei campi flat.
            BigDecimal lordoServizi = !righeSplit.isEmpty()
                    ? safeVal(b.getTotalCostiPm())
                    : safeVal(b.getOtaCommissionAmount())
                            .add(safeVal(b.getCleaningAmount()))
                            .add(safeVal(b.getPmFeeAmount()));
            // Imponibile e IVA della fattura PM già emessa: è il dato storico reale, non
            // una riproduzione del calcolo. ("fattura" è il codice della lookup tipo_documento.)
            Optional<FiscalDocument> fatturaPm = documentiBooking.stream()
                    .filter(d -> {
                        TipoDocumento t = d.getFkTipoDocumentoId() != null
                                ? maps.tipiDocumentoById.get(d.getFkTipoDocumentoId()) : null;
                        return t != null && "fattura".equals(t.getCodice());
                    })
                    .findFirst();

            split = SplitEconomicoDTO.builder()
                    .grossAmount(b.getGrossAmount())
                    .otaCommissionAmount(safeVal(b.getOtaCommissionAmount()))
                    .cleaningAmount(safeVal(b.getCleaningAmount()))
                    .pmFeeAmount(safeVal(b.getPmFeeAmount()))
                    .ownerNetAmount(ownerNet)
                    .withholdingAmount(ritenuta)
                    .aliquotaRitenuta(b.getAliquotaRitenuta())
                    .liquidazioneOwner(ownerNet.subtract(ritenuta))
                    .imponibileFatturaPm(fatturaPm.map(FiscalDocument::getImponibile).orElse(null))
                    .ivaScorporataPm(fatturaPm.map(FiscalDocument::getVatAmount).orElse(null))
                    .fatturaPmTotale(fatturaPm.map(FiscalDocument::getTotalAmount).orElse(lordoServizi))
                    .warnings(List.of())
                    .calcoloCompleto(true)
                    .regimeFiscalePm(regimeFiscalePm(b.getFkTenantId()))
                    .touristTaxAmount(b.getTouristTaxAmount())
                    .touristTaxIncludedInGross(b.getTouristTaxIncludedInGross())
                    .build();
            log.debug("BookingService.toDetailDTO() - id={} split da valori storici ({} documenti emessi)",
                    b.getId(), documentiBooking.size());
        } else {
            // Nessun documento emesso: lo split è ancora un'anteprima, si ricalcola dalle
            // regole di contratto correnti.
            // L'otaCommission già presente nel DB è usato come override (il valore importato dal CSV).
            // Se la tassa di soggiorno è inclusa nel lordo va scorporata: è incassata per conto del
            // Comune, non è reddito del proprietario e non deve entrare nella base della ritenuta.
            BigDecimal grossPerCalcolo = Boolean.TRUE.equals(b.getTouristTaxIncludedInGross())
                    ? safeVal(b.getGrossAmount()).subtract(safeVal(b.getTouristTaxAmount()))
                    : b.getGrossAmount();

            // OTA salvata ripassata come override NETTO (overridesDaImport=false): l'imponibile
            // della riga split, oppure — senza riga (booking pre-migrazione 018) — il lordo del
            // booking con l'IVA scorporata. Passare il lordo la gonfierebbe di un'altra IVA.
            BigDecimal otaNetta = righeSplit.stream()
                    .filter(r -> "commissione_ota".equals(r.getTipoVoce()))
                    .map(this::nettoRiga)
                    .findFirst()
                    .orElseGet(() -> {
                        if (b.getOtaCommissionAmount() == null) return null;
                        BigDecimal unoPiuIva = BigDecimal.ONE.add(
                                aliquotaIvaPm(b.getFkTenantId()).divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP));
                        return b.getOtaCommissionAmount().divide(unoPiuIva, 2, RoundingMode.HALF_UP);
                    });
            ContrattoCalcoloResult calcolo = contrattoCalcolatore.calcola(
                    b.getFkTenantId(),
                    b.getFkPropertyId(),
                    b.getFkCanaleOtaId(),
                    grossPerCalcolo,
                    otaNetta,
                    // Pulizie / cambio biancheria / PM impostati a mano o da file: ripassati
                    // come override (come l'OTA), così descrizioni e anteprima non tornano a
                    // quelle della regola. Pulizie e cambio biancheria separati, niente legacy.
                    null,
                    importoSeImpostato(righeSplit, "pulizie"),
                    importoSeImpostato(righeSplit, "cambio_biancheria"),
                    importoSeManuale(righeSplit, "commissione_pm"),
                    b.getNights(),
                    b.getGuests(),
                    extraImponibiliInFattura(righeSplit),
                    false);

            // Con righe split (booking post-migrazione 018) gli importi sono quelli salvati da
            // ricalcolaNettoDaSplit(): comprendono le voci extra in fattura PM (ora passate anche
            // al calcolatore, ma le righe restano il dato persistito). Sono anche i valori che DocumentGenerationService usa per fattura
            // (Σ righe) e ricevuta (netto proprietario): mostrarne altri romperebbe la quadratura
            // lordo = fattura PM + netto. Senza righe resta l'anteprima dalle regole correnti.
            boolean daRigheSplit = !righeSplit.isEmpty();
            BigDecimal fatturaPmTotale = daRigheSplit ? safeVal(b.getTotalCostiPm()) : calcolo.getFatturaPmTotale();
            BigDecimal ownerNet = daRigheSplit ? safeVal(b.getOwnerNetAmount()) : calcolo.getOwnerNetAmount();
            BigDecimal imponibileFatturaPm = calcolo.getImponibileFatturaPm();
            BigDecimal ivaScorporata = calcolo.getIvaScorporata();
            if (daRigheSplit) {
                // Stesso calcolo di DocumentGenerationService (fattura PM): RF19 senza IVA;
                // RF01 Σ imponibili delle righe in fattura, con scorporo lordo / 1.22 per le
                // righe senza imponibile (create prima della migration 020).
                boolean forfettario = "RF19".equalsIgnoreCase(calcolo.getRegimeFiscalePm());
                imponibileFatturaPm = forfettario
                        ? fatturaPmTotale
                        : righeSplit.stream()
                                .filter(r -> Boolean.TRUE.equals(r.getIncludeInFatturaPm()))
                                .map(r -> r.getImponibile() != null
                                        ? r.getImponibile()
                                        : safeVal(r.getImporto()).divide(new BigDecimal("1.22"), 2, RoundingMode.HALF_UP))
                                .reduce(BigDecimal.ZERO, BigDecimal::add)
                                .setScale(2, RoundingMode.HALF_UP);
                ivaScorporata = fatturaPmTotale.subtract(imponibileFatturaPm).setScale(2, RoundingMode.HALF_UP);
            }

            split = SplitEconomicoDTO.builder()
                    .grossAmount(b.getGrossAmount())
                    .otaCommissionAmount(daRigheSplit ? safeVal(b.getOtaCommissionAmount()) : calcolo.getOtaCommissionAmount())
                    .cleaningAmount(daRigheSplit ? safeVal(b.getCleaningAmount()) : calcolo.getCleaningAmount())
                    .pmFeeAmount(daRigheSplit ? safeVal(b.getPmFeeAmount()) : calcolo.getPmFeeAmount())
                    .ownerNetAmount(ownerNet)
                    .withholdingAmount(b.getWithholdingAmount())   // mantieni il valore del DB
                    .aliquotaRitenuta(b.getAliquotaRitenuta())     // % storicizzata sul booking
                    .liquidazioneOwner(daRigheSplit
                            ? ownerNet.subtract(safeVal(b.getWithholdingAmount()))
                            : calcolo.getLiquidazioneOwner())
                    .imponibileFatturaPm(imponibileFatturaPm)
                    .ivaScorporataPm(ivaScorporata)
                    .fatturaPmTotale(fatturaPmTotale)
                    .warnings(calcolo.getWarnings())
                    .calcoloCompleto(calcolo.getCalcoloCompleto())
                    .regimeFiscalePm(calcolo.getRegimeFiscalePm())
                    .touristTaxAmount(b.getTouristTaxAmount())
                    .touristTaxIncludedInGross(b.getTouristTaxIncludedInGross())
                    // Descrizioni solo qui: nel ramo storico sopra lo split non viene dalle
                    // regole correnti, quindi descriverle mentirebbe sull'importo mostrato.
                    .pmFeeDescrizione(calcolo.getPmFeeDescrizione())
                    .otaDescrizione(calcolo.getOtaDescrizione())
                    .build();
            log.debug("BookingService.toDetailDTO() - id={} split ricalcolato (nessun documento)", b.getId());
        }

        // Righe split sempre lette da DB, in entrambi i rami sopra (con o senza documenti):
        // sono il dato persistito, non una riproduzione del calcolo.
        List<BookingSplitEconomicoDTO> righeSplitDTO = righeSplit.stream()
                .map(r -> BookingSplitEconomicoDTO.builder()
                        .id(r.getId())
                        .fkBookingId(r.getFkBookingId())
                        .fkPropertyContractRuleId(r.getFkPropertyContractRuleId())
                        .tipoVoce(r.getTipoVoce())
                        .descrizione(r.getDescrizione())
                        .importo(r.getImporto())
                        .imponibile(r.getImponibile())
                        .importoOriginaleFile(r.getImportoOriginaleFile())
                        .aliquotaIva(r.getAliquotaIva())
                        .includeInFatturaPm(r.getIncludeInFatturaPm())
                        .ordinamento(r.getOrdinamento())
                        .source(r.getSource())
                        .createdAt(r.getCreatedAt())
                        .updatedAt(r.getUpdatedAt())
                        .build())
                .collect(Collectors.toList());

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
                .totalCostiPm(b.getTotalCostiPm())
                .touristTaxIncludedInGross(b.getTouristTaxIncludedInGross())
                .touristTaxCollection(b.getTouristTaxCollection())
                .statoPrenotazione(statoCodiceDa(b.getFkStatoPrenotazioneId(), maps.statiPrenotazioneById))
                .paymentStatus(b.getPaymentStatus())
                .documentStatus(computeDocumentStatus(b.getId(), maps))
                .settlementStatus(b.getSettlementStatus())
                .createdAt(b.getCreatedAt())
                .updatedAt(b.getUpdatedAt())
                .splitEconomico(split)
                .righeSplit(righeSplitDTO)
                // dati immobile (per dialog)
                .propertyAddress(prop != null ? prop.getAddress() : null)
                .propertyCity(prop != null ? prop.getCity() : null)
                .propertyInternalCode(prop != null ? prop.getInternalCode() : null)
                // dati proprietario (per dialog)
                .ownerTaxCode(owner != null ? owner.getTaxCode() : null)
                .ownerIban(owner != null ? owner.getIban() : null)
                .ownerCognomeNome(cognomeNomeOwner(owner))
                .ownerEmail(owner != null ? owner.getEmail() : null)
                // dati tenant (per dialog fattura PM)
                .tenantLegalName(tenant != null ? tenant.getLegalName() : null)
                .tenantVatNumber(tenant != null ? tenant.getVatNumber() : null)
                .tenantTaxCode(tenant != null ? tenant.getTaxCode() : null)
                // Indirizzo completo (via + CAP/comune/provincia) per l'anteprima fattura PM
                .tenantLegalAddress(TenantAddressUtils.indirizzoCompleto(tenant))
                .tenantPec(tenant != null ? tenant.getPec() : null)
                // documenti fiscali associati alla prenotazione
                .documenti(mapDocumenti(documentiBooking, maps))
                // righe delle note di credito attive: solo visualizzate in coda allo split,
                // non ricalcolano costi PM né netto proprietario
                .righeNdc(righeNdc(documentiBooking, b.getId()))
                .build();

        // settlementStato/settlementId derivati dal settlement reale associato al booking
        settlementBookingDAO.findSettlementIdByBookingId(b.getId())
                .flatMap(settlementDAO::findById)
                .ifPresent(s -> {
                    dto.setSettlementStato(s.getStato());
                    dto.setSettlementId(s.getId());
                });
        log.debug("BookingService: settlementStato={} per bookingId={}", dto.getSettlementStato(), b.getId());

        // Copia di un booking stornato (migration 026): link bidirezionale origine ↔ copia
        if (b.getFkBookingOrigineId() != null) {
            dto.setFkBookingOrigineId(b.getFkBookingOrigineId());
            bookingDAO.findById(b.getFkBookingOrigineId())
                    .ifPresent(o -> dto.setBookingOrigineCodice(o.getExternalBookingId()));
        }
        bookingDAO.findByOrigine(b.getId(), b.getFkTenantId()).ifPresent(c -> {
            dto.setFkBookingCopiaId(c.getId());
            dto.setBookingCopiaCodice(c.getExternalBookingId());
        });
        // Ritenuta già versata (F24 pagato o inviato): lo storno con NDC genera un credito d'imposta
        dto.setRitenutaVersata(withholdingLedgerDAO.findByBookingId(b.getId()).stream()
                .filter(w -> w.getFkNdcId() == null && w.getFkF24RecordId() != null)
                .map(w -> f24RecordDAO.findById(w.getFkF24RecordId()).map(F24Record::getStato).orElse(null))
                .anyMatch(s -> "paid".equals(s) || "sent".equals(s)));

        return dto;
    }

    /**
     * Copia un booking stornato con NDC totale per riemetterne i documenti, preservandone gli
     * importi: campi del booking e righe di booking_split_economico sono duplicati 1:1
     * (commissione OTA dal file con source e importo_originale_file, override manuali, voci
     * extra, tassa di soggiorno). Nessun ricalcolo dalle regole del contratto: la NDC non
     * tocca le righe split dell'originale, che restano il dato fatturato.
     * Nuovo ID esterno MAN-{timestamp}; netto e ritenuta da ricalcolaNettoDaSplit(), stato da
     * aggiornaStato().
     *
     * @throws NoSuchElementException booking inesistente o di altro tenant (404)
     * @throws IllegalStateException  booking non stornato o copia attiva già presente (400)
     */
    @Transactional
    public BookingDetailDTO copiaBooking(Integer tenantId, Integer bookingId, Integer utenteId) {
        Booking origine = bookingDAO.findById(bookingId)
                .filter(b -> tenantId.equals(b.getFkTenantId()))
                .orElseThrow(() -> new NoSuchElementException("Prenotazione non trovata: id=" + bookingId));
        String statoOrigine = statoPrenotazioneDAO.findById(origine.getFkStatoPrenotazioneId())
                .map(StatoPrenotazione::getCodice).orElse(null);
        if (!"stornata".equals(statoOrigine)) {
            throw new IllegalStateException("Solo i booking stornati possono essere copiati");
        }
        bookingDAO.findByOrigine(bookingId, tenantId).ifPresent(c -> {
            throw new IllegalStateException("Esiste già una copia attiva: " + c.getExternalBookingId());
        });

        // 1. Booking con gli stessi dati e importi dell'originale
        String extId = "MAN-" + System.currentTimeMillis();
        Booking saved = bookingDAO.insert(Booking.builder()
                .fkTenantId(tenantId)
                .fkPropertyId(origine.getFkPropertyId())
                .fkCanaleOtaId(origine.getFkCanaleOtaId())
                .fkOwnerId(origine.getFkOwnerId())
                .fkRegimeFiscaleId(origine.getFkRegimeFiscaleId())
                .externalBookingId(extId)
                .checkinDate(origine.getCheckinDate())
                .checkoutDate(origine.getCheckoutDate())
                .nights(origine.getNights())
                .guests(origine.getGuests())
                .grossAmount(origine.getGrossAmount())
                .otaCommissionAmount(origine.getOtaCommissionAmount())
                .cleaningAmount(origine.getCleaningAmount())
                .pmFeeAmount(origine.getPmFeeAmount())
                .ownerNetAmount(origine.getOwnerNetAmount())
                .withholdingAmount(origine.getWithholdingAmount())
                .aliquotaRitenuta(origine.getAliquotaRitenuta())
                .touristTaxAmount(origine.getTouristTaxAmount())
                .touristTaxIncludedInGross(origine.getTouristTaxIncludedInGross())
                .touristTaxCollection(origine.getTouristTaxCollection())
                .totalCostiPm(origine.getTotalCostiPm())
                .guestName(origine.getGuestName())
                .guestTaxCode(origine.getGuestTaxCode())
                .guestBirthDate(origine.getGuestBirthDate())
                .guestSesso(origine.getGuestSesso())
                .guestBirthPlace(origine.getGuestBirthPlace())
                .guestBirthBelfiore(origine.getGuestBirthBelfiore())
                .guestDocType(origine.getGuestDocType())
                .guestDocNumber(origine.getGuestDocNumber())
                .guestCountry(origine.getGuestCountry())
                .guestAddress(origine.getGuestAddress())
                .guestPhone(origine.getGuestPhone())
                .fkStatoPrenotazioneId(BookingDAO.STATO_IMPORTED)
                .paymentStatus("pending")
                .settlementStatus("pending")
                .build());
        bookingDAO.updateBookingOrigine(saved.getId(), bookingId);

        // 2. Righe split duplicate 1:1 (stessi importi, source e valore originale dal file)
        List<BookingSplitEconomico> righeOrigine = splitEconomicoDAO.findByBookingId(bookingId);
        for (BookingSplitEconomico r : righeOrigine) {
            splitEconomicoDAO.insert(BookingSplitEconomico.builder()
                    .fkBookingId(saved.getId())
                    .fkTenantId(tenantId)
                    .fkPropertyContractRuleId(r.getFkPropertyContractRuleId())
                    .tipoVoce(r.getTipoVoce())
                    .descrizione(r.getDescrizione())
                    .importo(r.getImporto())
                    .imponibile(r.getImponibile())
                    .importoOriginaleFile(r.getImportoOriginaleFile())
                    .aliquotaIva(r.getAliquotaIva())
                    .includeInFatturaPm(r.getIncludeInFatturaPm())
                    .ordinamento(r.getOrdinamento())
                    .source(r.getSource())
                    .createdBy(utenteId)
                    .updatedBy(utenteId)
                    .build());
        }

        // 3-4. Netto/ritenuta/total_costi_pm dalle righe, poi stato reale dai dati
        ricalcolaNettoDaSplit(saved.getId(), tenantId);
        aggiornaStato(saved.getId());

        auditService.log("booking.copy", "Booking", saved.getId(),
                "Copia della prenotazione stornata " + origine.getExternalBookingId() + " → " + extId);
        log.info("BookingService.copiaBooking() - origine={} nuovoId={} righeSplit={} utente={}",
                bookingId, saved.getId(), righeOrigine.size(), utenteId);
        return findById(tenantId, saved.getId()).orElseThrow();
    }

    /** Righe NDC non annullate del booking, con il numero della nota di credito. */
    private List<RigaNdcDTO> righeNdc(List<FiscalDocument> documentiBooking, Integer bookingId) {
        Map<Integer, String> numeri = documentiBooking.stream()
                .collect(Collectors.toMap(FiscalDocument::getId, FiscalDocument::getDocumentNumber, (a, c) -> a));
        return rigaNdcDAO.findByBookingId(bookingId).stream()
                .map(r -> RigaNdcDTO.builder()
                        .id(r.getId())
                        .fkFiscalDocumentId(r.getFkFiscalDocumentId())
                        .documentNumber(numeri.get(r.getFkFiscalDocumentId()))
                        .fkSplitEconomicoId(r.getFkSplitEconomicoId())
                        .descrizione(r.getDescrizione())
                        .importoStornato(r.getImportoStornato())
                        .imponibileStornato(r.getImponibileStornato())
                        .aliquotaIva(r.getAliquotaIva())
                        .ordinamento(r.getOrdinamento())
                        .build())
                .toList();
    }

    /** I documenti sono già stati letti da toDetailDTO(): qui si mappano soltanto. */
    private List<FiscalDocumentSummaryDTO> mapDocumenti(List<FiscalDocument> documenti, LookupMaps maps) {
        return documenti.stream()
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
