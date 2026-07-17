package it.gavia.sostitutoincloud.service;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import it.gavia.sostitutoincloud.config.ImportSessionCache;
import it.gavia.sostitutoincloud.dto.booking.ContrattoCalcoloResult;
import it.gavia.sostitutoincloud.dao.BookingDAO;
import it.gavia.sostitutoincloud.dao.CanaleOtaDAO;
import it.gavia.sostitutoincloud.dao.PropertyDAO;
import it.gavia.sostitutoincloud.dao.PropertyOtaCodeDAO;
import it.gavia.sostitutoincloud.dao.StatoPrenotazioneDAO;
import it.gavia.sostitutoincloud.dto.importing.BookingImportConfirmDTO;
import it.gavia.sostitutoincloud.dto.importing.BookingImportPreviewDTO;
import it.gavia.sostitutoincloud.dto.importing.BookingImportPreviewRowDTO;
import it.gavia.sostitutoincloud.dto.importing.BookingImportResultDTO;
import it.gavia.sostitutoincloud.dto.importing.BookingImportRowDTO;
import it.gavia.sostitutoincloud.dto.importing.ImportColumnMappingDTO;
import it.gavia.sostitutoincloud.dto.importing.ImportUploadResponseDTO;
import it.gavia.sostitutoincloud.model.Booking;
import it.gavia.sostitutoincloud.model.CanaleOta;
import it.gavia.sostitutoincloud.model.Property;
import it.gavia.sostitutoincloud.model.StatoPrenotazione;
import lombok.extern.log4j.Log4j2;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Log4j2
public class BookingImportService {

    private final BookingDAO bookingDAO;
    private final PropertyDAO propertyDAO;
    private final CanaleOtaDAO canaleOtaDAO;
    private final PropertyOtaCodeDAO propertyOtaCodeDAO;
    private final StatoPrenotazioneDAO statoPrenotazioneDAO;
    private final ImportSessionCache importSessionCache;
    private final AuditService auditService;
    private final ContrattoCalcolatoreService contrattoCalcolatore;

    public BookingImportService(BookingDAO bookingDAO,
                                PropertyDAO propertyDAO,
                                CanaleOtaDAO canaleOtaDAO,
                                PropertyOtaCodeDAO propertyOtaCodeDAO,
                                StatoPrenotazioneDAO statoPrenotazioneDAO,
                                ImportSessionCache importSessionCache,
                                AuditService auditService,
                                ContrattoCalcolatoreService contrattoCalcolatore) {
        this.bookingDAO = bookingDAO;
        this.propertyDAO = propertyDAO;
        this.canaleOtaDAO = canaleOtaDAO;
        this.propertyOtaCodeDAO = propertyOtaCodeDAO;
        this.statoPrenotazioneDAO = statoPrenotazioneDAO;
        this.importSessionCache = importSessionCache;
        this.auditService = auditService;
        this.contrattoCalcolatore = contrattoCalcolatore;
    }

    // ── campi di sistema import V2 ─────────────────────────────────────────
    private static final List<String> BOOKING_REQUIRED_FIELDS =
            List.of("BOOKING_ID", "ORIGINE", "STRUTTURA", "CHECKIN", "CHECKOUT", "IMPORTO_TOTALE");

    // Valori STATO (normalizzati) che identificano una prenotazione cancellata/annullata:
    // queste righe vengono escluse dall'anteprima e conteggiate in excludedCount.
    private static final Set<String> STATI_CANCELLATI = Set.of(
            "cancellata", "cancelled", "canceled",
            "annullata", "annullato", "annulled",
            "cancellata/a", "no show",
            "rifiutata", "rejected", "expired");

    // Mapping suggerito: header atteso (normalizzato) → campo di sistema. File prenotazioni.
    private static final Map<String, String> EXPECTED_BOOKING_HEADERS = Map.ofEntries(
            Map.entry("id", "BOOKING_ID"),
            Map.entry("origine", "ORIGINE"),
            Map.entry("struttura", "STRUTTURA"),
            Map.entry("arrivo", "CHECKIN"),
            Map.entry("partenza", "CHECKOUT"),
            Map.entry("importo totale", "IMPORTO_TOTALE"),
            Map.entry("adulti", "ADULTI"),
            Map.entry("bambini", "BAMBINI"),
            Map.entry("neonati", "NEONATI"),
            Map.entry("commissione del canale", "COMMISSIONE"),
            Map.entry("stato", "STATO"),
            Map.entry("cliente", "CLIENTE_NOME")
    );

    // Mapping suggerito: header atteso (normalizzato) → campo di sistema. File ospiti.
    private static final Map<String, String> EXPECTED_GUEST_HEADERS = Map.ofEntries(
            Map.entry("id", "BOOKING_ID"),
            Map.entry("nome", "NOME"),
            Map.entry("cognome", "COGNOME"),
            Map.entry("data di nascita", "DATA_NASCITA"),
            Map.entry("sesso", "SESSO"),
            Map.entry("comune emittente", "COMUNE_NASCITA"),
            Map.entry("documento", "DOCUMENTO"),
            Map.entry("nº documento", "NUM_DOCUMENTO"),
            Map.entry("nazione", "NAZIONE")
    );

    public BookingImportPreviewDTO preview(Integer tenantId, MultipartFile file) throws IOException, CsvException {
        List<String[]> rawRows;
        try (CSVReader reader = new CSVReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            rawRows = reader.readAll();
        }
        if (rawRows.isEmpty()) throw new IllegalArgumentException("File CSV vuoto");
        rawRows.remove(0); // rimuovi header

        // build lookup maps per tenant
        Map<String, Property> propertyByCode = propertyDAO.findByTenantId(tenantId).stream()
                .collect(Collectors.toMap(Property::getInternalCode, p -> p));
        Map<String, CanaleOta> canaleByCode = canaleOtaDAO.findAll().stream()
                .collect(Collectors.toMap(CanaleOta::getCodice, c -> c));

        List<BookingImportPreviewRowDTO> previewRows = new ArrayList<>();
        List<BookingImportRowDTO> nuoveRows = new ArrayList<>();
        int newCount = 0, dupeCount = 0, errorCount = 0;

        for (int i = 0; i < rawRows.size(); i++) {
            String[] cols = rawRows.get(i);
            int rowNum = i + 2; // 1=header, +1 per 1-based
            BookingImportPreviewRowDTO preview = parseRow(cols, rowNum, tenantId, propertyByCode, canaleByCode);
            previewRows.add(preview);
            switch (preview.getStatus()) {
                case "nuova"     -> { newCount++;   nuoveRows.add(preview.getRawData()); }
                case "duplicata" -> dupeCount++;
                case "errore"    -> errorCount++;
            }
        }

        String sessionId = UUID.randomUUID().toString();
        importSessionCache.store(sessionId, nuoveRows);

        log.info("BookingImportService.preview() - tenantId={} file={} rows={} new={} dupe={} err={}",
                tenantId, file.getOriginalFilename(), rawRows.size(), newCount, dupeCount, errorCount);

        return BookingImportPreviewDTO.builder()
                .fileName(file.getOriginalFilename())
                .totalRows(rawRows.size())
                .newCount(newCount)
                .dupeCount(dupeCount)
                .errorCount(errorCount)
                .rows(previewRows)
                .importSessionId(sessionId)
                .build();
    }

    public BookingImportResultDTO confirm(Integer tenantId, BookingImportConfirmDTO dto) {
        List<BookingImportRowDTO> cached = importSessionCache.get(dto.getImportSessionId());
        if (cached == null) throw new RuntimeException("Sessione import non trovata o scaduta");

        Set<String> selectedIds = Set.copyOf(dto.getSelectedExternalIds());
        List<Property> properties = propertyDAO.findByTenantId(tenantId);
        Map<String, Property> propertyByCode = properties.stream()
                .collect(Collectors.toMap(Property::getInternalCode, p -> p, (a, b) -> a));
        Map<Integer, Property> propertyById = properties.stream()
                .collect(Collectors.toMap(Property::getId, p -> p, (a, b) -> a));
        List<CanaleOta> canali = canaleOtaDAO.findAll();
        Map<String, CanaleOta> canaleByCode = canali.stream()
                .collect(Collectors.toMap(CanaleOta::getCodice, c -> c, (a, b) -> a));
        Map<Integer, CanaleOta> canaleById = canali.stream()
                .collect(Collectors.toMap(CanaleOta::getId, c -> c, (a, b) -> a));

        Integer statoImportedId = statoPrenotazioneDAO.findByCodice("imported")
                .map(StatoPrenotazione::getId)
                .orElse(1);

        int imported = 0, skipped = 0, errors = 0;
        List<String> errorMessages = new ArrayList<>();

        for (BookingImportRowDTO row : cached) {
            if (!selectedIds.contains(row.getExternalBookingId())) {
                skipped++;
                continue;
            }
            try {
                // V2: id immobile/canale già risolti in preview; V1: lookup per codice.
                Property property = row.getFkPropertyId() != null
                        ? propertyById.get(row.getFkPropertyId())
                        : propertyByCode.get(row.getPropertyCode());
                CanaleOta canale = row.getFkCanaleOtaId() != null
                        ? canaleById.get(row.getFkCanaleOtaId())
                        : canaleByCode.get(row.getChannelCode());

                Integer fkPropertyId = property != null ? property.getId() : row.getFkPropertyId();
                Integer fkCanaleOtaId = canale != null ? canale.getId() : row.getFkCanaleOtaId();

                // Nome ospite: dal merge con il file ospiti se disponibile, altrimenti CLIENTE_NOME del mapping.
                String guestName = resolveGuestName(row);

                // Calcolo dello split economico tramite le regole del contratto immobile.
                // La commissione OTA dal CSV è passata come override.
                ContrattoCalcoloResult calcolo = contrattoCalcolatore.calcola(
                        tenantId,
                        fkPropertyId,
                        fkCanaleOtaId,
                        row.getGrossAmount(),
                        row.getOtaCommissionAmount(),  // override dal CSV
                        row.getNights(),
                        row.getGuests());

                if (calcolo.getWarnings() != null && !calcolo.getWarnings().isEmpty()) {
                    calcolo.getWarnings().forEach(w ->
                            log.warn("BookingImportService.confirm() - booking {}: {}",
                                    row.getExternalBookingId(), w));
                }

                Booking booking = Booking.builder()
                        .fkTenantId(tenantId)
                        .fkPropertyId(fkPropertyId)
                        .fkOwnerId(property != null ? property.getFkOwnerId() : null)
                        .fkCanaleOtaId(fkCanaleOtaId)
                        .externalBookingId(row.getExternalBookingId())
                        .guestName(guestName)
                        .guestTaxCode(row.getGuestTaxCode())
                        .checkinDate(row.getCheckinDate())
                        .checkoutDate(row.getCheckoutDate())
                        .nights(row.getNights())
                        .guests(row.getGuests())
                        .grossAmount(row.getGrossAmount())
                        .otaCommissionAmount(calcolo.getOtaCommissionAmount())
                        .cleaningAmount(calcolo.getCleaningAmount())
                        .pmFeeAmount(calcolo.getPmFeeAmount())
                        .ownerNetAmount(calcolo.getOwnerNetAmount())
                        .withholdingAmount(calcolo.getWithholdingAmount())
                        .aliquotaRitenuta(calcolo.getAliquotaRitenuta())
                        .touristTaxAmount(orZero(row.getTouristTaxAmount()))
                        .touristTaxIncludedInGross(Boolean.TRUE.equals(row.getTouristTaxIncluded()))
                        .touristTaxCollection(canale != null
                                ? canale.getTouristTaxCollection() : "contanti")
                        .fkStatoPrenotazioneId(statoImportedId)
                        .paymentStatus("pending")
                        .settlementStatus("pending")
                        .build();
                bookingDAO.insert(booking);
                imported++;
            } catch (Exception e) {
                errors++;
                errorMessages.add(row.getExternalBookingId() + ": " + e.getMessage());
                log.error("Errore import booking {}: {}", row.getExternalBookingId(), e.getMessage());
            }
        }

        importSessionCache.remove(dto.getImportSessionId());
        log.info("BookingImportService.confirm() - tenantId={} imported={} errors={}", tenantId, imported, errors);
        auditService.log("booking.import", "Booking", null,
                "Importate " + imported + " prenotazioni da CSV");

        return BookingImportResultDTO.builder()
                .imported(imported)
                .skipped(skipped)
                .errors(errors)
                .errorMessages(errorMessages)
                .build();
    }

    // ══════════════════════════════════════════════════════════════════════
    // IMPORT V2 — doppio file (prenotazioni + ospiti), mapping colonne manuale
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Step upload: legge gli header dei file (CSV o XLSX), salva i file raw in cache
     * con sessioni separate e genera un mapping colonne suggerito automaticamente.
     */
    public ImportUploadResponseDTO uploadFiles(Integer tenantId, MultipartFile bookingFile, MultipartFile guestFile,
                                               Integer headerRow)
            throws IOException, CsvException {
        if (bookingFile == null || bookingFile.isEmpty()) {
            throw new IllegalArgumentException("File prenotazioni obbligatorio");
        }

        int hRow = headerRow != null ? headerRow : 0;

        byte[] bookingBytes = bookingFile.getBytes();
        ParsedTable bookingTable = readTable(bookingBytes, bookingFile.getOriginalFilename(), hRow);
        String bookingSessionId = UUID.randomUUID().toString();
        importSessionCache.storeRawFile(bookingSessionId, bookingBytes, bookingFile.getOriginalFilename());
        importSessionCache.storeHeaderRow(bookingSessionId, hRow);

        String guestSessionId = null;
        List<String> guestColumns = null;
        Map<String, String> suggestedGuestMapping = null;
        if (guestFile != null && !guestFile.isEmpty()) {
            byte[] guestBytes = guestFile.getBytes();
            ParsedTable guestTable = readTable(guestBytes, guestFile.getOriginalFilename(), hRow);
            guestSessionId = UUID.randomUUID().toString();
            importSessionCache.storeRawFile(guestSessionId, guestBytes, guestFile.getOriginalFilename());
            importSessionCache.storeHeaderRow(guestSessionId, hRow);
            guestColumns = guestTable.headers();
            suggestedGuestMapping = buildSuggestedMapping(guestTable.headers(), EXPECTED_GUEST_HEADERS);
        }

        Map<String, String> suggestedBookingMapping =
                buildSuggestedMapping(bookingTable.headers(), EXPECTED_BOOKING_HEADERS);

        // valori distinti della colonna STATO (se rilevata dal mapping suggerito):
        // servono al frontend per far scegliere all'utente quali escludere. null se non rilevata.
        List<String> statoColumnValues = null;
        String statoCol = suggestedBookingMapping.get("STATO");
        if (statoCol != null && !statoCol.isBlank()) {
            statoColumnValues = bookingTable.rows().stream()
                    .map(r -> r.get(statoCol))
                    .filter(v -> v != null && !v.trim().isEmpty())
                    .map(String::trim)
                    .distinct()
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
        }

        log.info("BookingImportService.uploadFiles() - tenantId={} bookingFile={} headerRow={} cols={} guestFile={} statoValues={}",
                tenantId, bookingFile.getOriginalFilename(), hRow, bookingTable.headers().size(),
                guestFile != null ? guestFile.getOriginalFilename() : null,
                statoColumnValues != null ? statoColumnValues.size() : null);

        return ImportUploadResponseDTO.builder()
                .bookingSessionId(bookingSessionId)
                .guestSessionId(guestSessionId)
                .bookingColumns(bookingTable.headers())
                .guestColumns(guestColumns)
                .suggestedBookingMapping(suggestedBookingMapping)
                .suggestedGuestMapping(suggestedGuestMapping)
                .statoColumnValues(statoColumnValues)
                .build();
    }

    /**
     * Step anteprima: rilegge i file raw dalla cache applicando il mapping colonne,
     * merge con il file ospiti per BOOKING_ID, match immobile e calcolo split.
     */
    public BookingImportPreviewDTO previewWithMapping(Integer tenantId, String bookingSessionId,
                                                      String guestSessionId, ImportColumnMappingDTO mapping)
            throws IOException, CsvException {
        byte[] bookingBytes = importSessionCache.getRawFile(bookingSessionId);
        if (bookingBytes == null) throw new RuntimeException("Sessione file prenotazioni non trovata o scaduta");
        if (mapping == null || mapping.getBookingMapping() == null) {
            throw new IllegalArgumentException("Mapping colonne prenotazioni mancante");
        }
        Map<String, String> bMap = mapping.getBookingMapping();
        for (String req : BOOKING_REQUIRED_FIELDS) {
            String col = bMap.get(req);
            if (col == null || col.isBlank()) {
                throw new IllegalArgumentException("Campo obbligatorio non mappato: " + req);
            }
        }

        String fileName = importSessionCache.getFileName(bookingSessionId);
        ParsedTable bookingTable = readTable(bookingBytes, fileName, importSessionCache.getHeaderRow(bookingSessionId));

        // merge ospiti per BOOKING_ID
        Map<String, GuestData> guestByBookingId = new LinkedHashMap<>();
        if (guestSessionId != null && !guestSessionId.isBlank()
                && mapping.getGuestMapping() != null && !mapping.getGuestMapping().isEmpty()) {
            byte[] guestBytes = importSessionCache.getRawFile(guestSessionId);
            if (guestBytes != null) {
                ParsedTable guestTable = readTable(guestBytes, importSessionCache.getFileName(guestSessionId),
                        importSessionCache.getHeaderRow(guestSessionId));
                Map<String, String> gMap = mapping.getGuestMapping();
                for (Map<String, String> gRow : guestTable.rows()) {
                    String bookingId = mapVal(gRow, gMap, "BOOKING_ID");
                    if (bookingId.isBlank()) continue;
                    guestByBookingId.put(bookingId, GuestData.builder()
                            .firstName(mapVal(gRow, gMap, "NOME"))
                            .lastName(mapVal(gRow, gMap, "COGNOME"))
                            .birthDate(mapVal(gRow, gMap, "DATA_NASCITA"))
                            .gender(mapVal(gRow, gMap, "SESSO"))
                            .birthPlace(mapVal(gRow, gMap, "COMUNE_NASCITA"))
                            .docType(mapVal(gRow, gMap, "DOCUMENTO"))
                            .docNumber(mapVal(gRow, gMap, "NUM_DOCUMENTO"))
                            .country(mapVal(gRow, gMap, "NAZIONE"))
                            .build());
                }
            }
        }

        List<BookingImportPreviewRowDTO> previewRows = new ArrayList<>();
        List<BookingImportRowDTO> nuoveRows = new ArrayList<>();
        int newCount = 0, dupeCount = 0, errorCount = 0, warningCount = 0, excludedCount = 0;

        boolean statoMappato = bMap.get("STATO") != null && !bMap.get("STATO").isBlank();

        // Insieme degli stati da escludere: se l'utente ne ha scelti espressamente li usa,
        // altrimenti fallback alla lista hardcodata STATI_CANCELLATI. Confronto normalizzato lowercase.
        Set<String> statiEsclusi;
        if (mapping.getStatiDaEscludere() != null && !mapping.getStatiDaEscludere().isEmpty()) {
            statiEsclusi = mapping.getStatiDaEscludere().stream()
                    .filter(s -> s != null)
                    .map(s -> s.trim().toLowerCase())
                    .collect(Collectors.toSet());
        } else {
            statiEsclusi = STATI_CANCELLATI;
        }

        for (int i = 0; i < bookingTable.rows().size(); i++) {
            Map<String, String> row = bookingTable.rows().get(i);
            int rowNum = i + 2; // 1=header, +1 per 1-based

            // A) filtro prenotazioni da escludere: se STATO è mappato ed è tra gli stati esclusi,
            //    scarta la riga (non la processo, non la aggiungo alla preview).
            if (statoMappato) {
                String stato = mapVal(row, bMap, "STATO").toLowerCase();
                if (statiEsclusi.contains(stato)) {
                    excludedCount++;
                    continue;
                }
            }

            BookingImportPreviewRowDTO preview = parseRowV2(row, rowNum, tenantId, bMap, guestByBookingId);
            previewRows.add(preview);
            if (preview.getSplitWarnings() != null && !preview.getSplitWarnings().isEmpty()) warningCount++;
            switch (preview.getStatus()) {
                case "nuova"     -> { newCount++; nuoveRows.add(preview.getRawData()); }
                case "duplicata" -> dupeCount++;
                case "errore"    -> errorCount++;
            }
        }

        String sessionId = UUID.randomUUID().toString();
        importSessionCache.store(sessionId, nuoveRows);

        log.info("BookingImportService.previewWithMapping() - tenantId={} file={} rows={} new={} dupe={} err={} warn={} excluded={}",
                tenantId, fileName, bookingTable.rows().size(), newCount, dupeCount, errorCount, warningCount, excludedCount);

        return BookingImportPreviewDTO.builder()
                .fileName(fileName)
                .totalRows(bookingTable.rows().size())
                .newCount(newCount)
                .dupeCount(dupeCount)
                .errorCount(errorCount)
                .warningCount(warningCount)
                .excludedCount(excludedCount)
                .rows(previewRows)
                .importSessionId(sessionId)
                .build();
    }

    private BookingImportPreviewRowDTO parseRowV2(Map<String, String> row, int rowNum, Integer tenantId,
            Map<String, String> bMap, Map<String, GuestData> guestByBookingId) {
        String externalId = mapVal(row, bMap, "BOOKING_ID");
        String origine    = mapVal(row, bMap, "ORIGINE");
        String struttura  = mapVal(row, bMap, "STRUTTURA");
        try {
            if (externalId.isBlank()) return errorRowV2(rowNum, externalId, null, null, "BOOKING_ID mancante");

            LocalDate checkin, checkout;
            try { checkin = parseFlexibleDate(mapVal(row, bMap, "CHECKIN")); }
            catch (Exception e) { return errorRowV2(rowNum, externalId, null, origine, "CHECKIN non valida"); }
            try { checkout = parseFlexibleDate(mapVal(row, bMap, "CHECKOUT")); }
            catch (Exception e) { return errorRowV2(rowNum, externalId, null, origine, "CHECKOUT non valida"); }
            if (checkin == null || checkout == null) return errorRowV2(rowNum, externalId, null, origine, "Date check-in/out mancanti");

            BigDecimal gross;
            try { gross = parseAmount(mapVal(row, bMap, "IMPORTO_TOTALE")); }
            catch (Exception e) { return errorRowV2(rowNum, externalId, null, origine, "IMPORTO_TOTALE non valido"); }

            // match immobile: canale_ota.nome (ORIGINE) + property_ota_code.external_id (STRUTTURA)
            Optional<PropertyOtaCodeDAO.PropertyOtaMatch> match =
                    propertyOtaCodeDAO.matchByCanaleNomeAndExternalId(origine, struttura, tenantId);
            if (match.isEmpty()) {
                return errorRowV2(rowNum, externalId, struttura, origine,
                        "Immobile non trovato per ORIGINE='" + origine + "' STRUTTURA='" + struttura + "'");
            }
            PropertyOtaCodeDAO.PropertyOtaMatch m = match.get();

            int guests = parseIntOr(mapVal(row, bMap, "ADULTI"), 1);
            if (guests <= 0) guests = 1;
            int nights = (int) Math.max(0, ChronoUnit.DAYS.between(checkin, checkout));
            BigDecimal commissione = parseAmountOr(mapVal(row, bMap, "COMMISSIONE"), null);

            // dati ospite dal merge (se presenti), altrimenti CLIENTE_NOME/COGNOME dal file prenotazioni
            GuestData guest = guestByBookingId.get(externalId);
            String firstName = guest != null && !blank(guest.getFirstName()) ? guest.getFirstName() : mapVal(row, bMap, "CLIENTE_NOME");
            String lastName  = guest != null && !blank(guest.getLastName())  ? guest.getLastName()  : mapVal(row, bMap, "CLIENTE_COGNOME");
            String guestName = (orEmpty(firstName) + " " + orEmpty(lastName)).trim();
            boolean nomeMancante = guestName.isBlank();
            if (guestName.isBlank()) guestName = externalId;

            // calcolo split economico
            ContrattoCalcoloResult calcolo = contrattoCalcolatore.calcola(
                    tenantId, m.propertyId(), m.canaleId(), gross, commissione, nights, guests);

            // warning informativi (non cambiano lo stato riga): split + dati anagrafici incompleti
            List<String> warnings = new ArrayList<>(calcolo.getWarnings() != null ? calcolo.getWarnings() : List.of());
            String comuneNascita = guest != null ? guest.getBirthPlace() : null;
            String dataNascita   = guest != null ? guest.getBirthDate()  : null;
            String numDocumento  = guest != null ? guest.getDocNumber()  : null;
            if (blank(comuneNascita)) warnings.add("CF non calcolabile: comune di nascita mancante");
            if (blank(dataNascita))   warnings.add("CF non calcolabile: data di nascita mancante");
            if (blank(numDocumento))  warnings.add("Documento identificativo mancante");
            if (nomeMancante)         warnings.add("Nome ospite mancante");

            boolean duplicata = bookingDAO.findByExternalBookingId(externalId).isPresent();

            BookingImportRowDTO raw = BookingImportRowDTO.builder()
                    .externalBookingId(externalId)
                    .fkPropertyId(m.propertyId())
                    .fkCanaleOtaId(m.canaleId())
                    .guestName(guestName)
                    .guestFirstName(firstName)
                    .guestLastName(lastName)
                    .guestBirthDate(guest != null ? guest.getBirthDate() : null)
                    .guestGender(guest != null ? guest.getGender() : null)
                    .guestBirthPlace(guest != null ? guest.getBirthPlace() : null)
                    .guestDocType(guest != null ? guest.getDocType() : null)
                    .guestDocNumber(guest != null ? guest.getDocNumber() : null)
                    .guestCountry(guest != null ? guest.getCountry() : null)
                    .checkinDate(checkin)
                    .checkoutDate(checkout)
                    .nights(nights)
                    .guests(guests)
                    .grossAmount(gross)
                    .otaCommissionAmount(commissione)
                    .status(mapVal(row, bMap, "STATO"))
                    .touristTaxIncluded(Boolean.FALSE)
                    .currency("EUR")
                    .splitWarnings(warnings)
                    .build();

            return BookingImportPreviewRowDTO.builder()
                    .rowNumber(rowNum)
                    .externalBookingId(externalId)
                    .guestName(guestName)
                    .propertyCode(struttura)
                    .propertyName(m.propertyName())
                    .fkPropertyId(m.propertyId())
                    .channelCode(origine)
                    .channelName(m.canaleNome())
                    .checkinDate(checkin)
                    .checkoutDate(checkout)
                    .grossAmount(gross)
                    .status(duplicata ? "duplicata" : "nuova")
                    .splitWarnings(warnings)
                    .rawData(raw)
                    .build();

        } catch (Exception e) {
            return errorRowV2(rowNum, externalId, struttura, origine, "Errore parsing: " + e.getMessage());
        }
    }

    private BookingImportPreviewRowDTO errorRowV2(int rowNum, String externalId, String struttura,
                                                  String origine, String msg) {
        return BookingImportPreviewRowDTO.builder()
                .rowNumber(rowNum)
                .externalBookingId(blank(externalId) ? "?" : externalId)
                .propertyCode(struttura)
                .channelCode(origine)
                .status("errore")
                .errorMessage(msg)
                .build();
    }

    /** Nome ospite per la fase di confirm: firstName+lastName se presenti, altrimenti guestName precalcolato. */
    private String resolveGuestName(BookingImportRowDTO row) {
        if (!blank(row.getGuestFirstName()) || !blank(row.getGuestLastName())) {
            String name = (orEmpty(row.getGuestFirstName()) + " " + orEmpty(row.getGuestLastName())).trim();
            if (!name.isBlank()) return name;
        }
        return row.getGuestName();
    }

    // ── lettura tabellare CSV / XLSX ──────────────────────────────────────────

    private record ParsedTable(List<String> headers, List<Map<String, String>> rows) {}

    private ParsedTable readTable(byte[] content, String fileName, int headerRow) throws IOException, CsvException {
        boolean xlsx = fileName != null && fileName.toLowerCase().endsWith(".xlsx");
        return xlsx ? readXlsx(content, headerRow) : readCsv(content, headerRow);
    }

    /**
     * @param headerRow numero di righe da saltare prima dell'header (0 = l'header è la prima riga).
     */
    private ParsedTable readCsv(byte[] content, int headerRow) throws IOException, CsvException {
        try (CSVReader reader = new CSVReader(new InputStreamReader(new ByteArrayInputStream(content), StandardCharsets.UTF_8))) {
            List<String[]> raw = reader.readAll();
            int headerIdx = Math.max(0, headerRow);
            if (raw.size() <= headerIdx) return new ParsedTable(List.of(), List.of());
            List<String> headers = new ArrayList<>();
            for (String h : raw.get(headerIdx)) headers.add(h != null ? h.trim() : "");
            List<Map<String, String>> rows = new ArrayList<>();
            for (int i = headerIdx + 1; i < raw.size(); i++) {
                String[] cols = raw.get(i);
                Map<String, String> m = new LinkedHashMap<>();
                for (int c = 0; c < headers.size(); c++) {
                    m.put(headers.get(c), (c < cols.length && cols[c] != null) ? cols[c].trim() : "");
                }
                rows.add(m);
            }
            return new ParsedTable(headers, rows);
        }
    }

    /**
     * @param headerRow indice (0-based) della riga del foglio da usare come header;
     *                  le righe precedenti vengono ignorate, i dati partono dalla successiva.
     */
    private ParsedTable readXlsx(byte[] content, int headerRow) throws IOException {
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(content))) {
            Sheet sheet = wb.getSheetAt(0);
            DataFormatter fmt = new DataFormatter();
            int headerIdx = Math.max(0, headerRow);
            Row headerRowObj = sheet.getRow(headerIdx);
            if (headerRowObj == null) return new ParsedTable(List.of(), List.of());
            List<String> headers = new ArrayList<>();
            int lastCol = headerRowObj.getLastCellNum();
            for (int c = 0; c < lastCol; c++) {
                Cell cell = headerRowObj.getCell(c);
                headers.add(cell != null ? fmt.formatCellValue(cell).trim() : "");
            }
            List<Map<String, String>> rows = new ArrayList<>();
            for (int r = headerIdx + 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                Map<String, String> m = new LinkedHashMap<>();
                boolean allEmpty = true;
                for (int c = 0; c < headers.size(); c++) {
                    String val = cellToString(row.getCell(c), fmt);
                    if (!val.isEmpty()) allEmpty = false;
                    m.put(headers.get(c), val);
                }
                if (!allEmpty) rows.add(m);
            }
            return new ParsedTable(headers, rows);
        }
    }

    private String cellToString(Cell cell, DataFormatter fmt) {
        if (cell == null) return "";
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate().toString(); // ISO yyyy-MM-dd
        }
        return fmt.formatCellValue(cell).trim();
    }

    // ── mapping suggerito ──────────────────────────────────────────────────────

    private Map<String, String> buildSuggestedMapping(List<String> fileColumns, Map<String, String> expectedHeaders) {
        Map<String, String> suggested = new LinkedHashMap<>();
        for (String col : fileColumns) {
            String norm = normalize(col);
            String field = expectedHeaders.get(norm);
            if (field == null) {
                // match parziale: l'header atteso è contenuto nel nome colonna (o viceversa)
                for (Map.Entry<String, String> e : expectedHeaders.entrySet()) {
                    if (norm.equals(e.getKey()) || norm.contains(e.getKey()) || e.getKey().contains(norm)) {
                        field = e.getValue();
                        break;
                    }
                }
            }
            if (field != null && !suggested.containsKey(field)) {
                suggested.put(field, col);
            }
        }
        return suggested;
    }

    private String normalize(String s) {
        return s == null ? "" : s.trim().toLowerCase().replaceAll("\\s+", " ");
    }

    private String mapVal(Map<String, String> row, Map<String, String> mapping, String field) {
        String col = mapping.get(field);
        if (col == null || col.isBlank()) return "";
        String v = row.get(col);
        return v != null ? v.trim() : "";
    }

    private static final DateTimeFormatter[] DATE_FORMATS = {
            DateTimeFormatter.ISO_LOCAL_DATE,
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd")
    };

    private LocalDate parseFlexibleDate(String s) {
        if (s == null || s.isBlank()) return null;
        String v = s.trim();
        for (DateTimeFormatter f : DATE_FORMATS) {
            try { return LocalDate.parse(v, f); } catch (DateTimeParseException ignored) { /* prova il prossimo */ }
        }
        throw new IllegalArgumentException("Formato data non riconosciuto: " + s);
    }

    private BigDecimal parseAmount(String s) {
        if (s == null || s.isBlank()) throw new IllegalArgumentException("Importo vuoto");
        String v = s.trim().replace("€", "").replace(" ", "");
        if (v.contains(",") && v.contains(".")) {
            // formato italiano: punto migliaia, virgola decimali → 1.234,56
            v = v.replace(".", "").replace(",", ".");
        } else if (v.contains(",")) {
            v = v.replace(",", ".");
        }
        return new BigDecimal(v);
    }

    private BigDecimal parseAmountOr(String s, BigDecimal def) {
        try { return parseAmount(s); } catch (Exception e) { return def; }
    }

    private boolean blank(String s) { return s == null || s.isBlank(); }

    private String orEmpty(String s) { return s != null ? s : ""; }

    @lombok.Data
    @lombok.Builder
    private static class GuestData {
        private String firstName;
        private String lastName;
        private String birthDate;
        private String gender;
        private String birthPlace;
        private String docType;
        private String docNumber;
        private String country;
    }

    // ── parsing helpers ──────────────────────────────────────────────────────

    private BookingImportPreviewRowDTO parseRow(String[] cols, int rowNum, Integer tenantId,
            Map<String, Property> propertyByCode, Map<String, CanaleOta> canaleByCode) {
        try {
            if (cols.length < 13) return errorRow(rowNum, cols, "Numero colonne insufficiente");

            String externalId  = col(cols, 0);
            String channelCode = col(cols, 1);
            String propCode    = col(cols, 2);
            String guestName   = col(cols, 3);
            String guestEmail  = col(cols, 4);
            String guestPhone  = col(cols, 5);
            String guestTaxCode= col(cols, 6);
            String checkinStr  = col(cols, 7);
            String checkoutStr = col(cols, 8);
            String nightsStr   = col(cols, 9);
            String guestsStr   = col(cols, 10);
            String status      = col(cols, 11);
            String grossStr    = col(cols, 12);

            if (externalId.isBlank())  return errorRow(rowNum, cols, "external_booking_id mancante");
            if (channelCode.isBlank()) return errorRow(rowNum, cols, "channel_code mancante");
            if (propCode.isBlank())    return errorRow(rowNum, cols, "property_code mancante");
            if (guestName.isBlank())   return errorRow(rowNum, cols, "guest_name mancante");
            if (checkinStr.isBlank())  return errorRow(rowNum, cols, "checkin_date mancante");
            if (checkoutStr.isBlank()) return errorRow(rowNum, cols, "checkout_date mancante");
            if (grossStr.isBlank())    return errorRow(rowNum, cols, "gross_amount mancante");

            LocalDate checkin, checkout;
            try { checkin  = LocalDate.parse(checkinStr);  } catch (DateTimeParseException e) { return errorRow(rowNum, cols, "checkin_date non valida"); }
            try { checkout = LocalDate.parse(checkoutStr); } catch (DateTimeParseException e) { return errorRow(rowNum, cols, "checkout_date non valida"); }

            int nights = nightsStr.isBlank() ? 0 : Integer.parseInt(nightsStr);
            int guests = guestsStr.isBlank() ? 1 : Integer.parseInt(guestsStr);
            BigDecimal gross = new BigDecimal(grossStr);

            // lookup property
            Property property = propertyByCode.get(propCode);
            if (property == null) return errorRow(rowNum, cols, "Immobile '" + propCode + "' non trovato per questo tenant");

            // lookup canale (non bloccante — segnala ma non è errore fatale)
            CanaleOta canale = canaleByCode.get(channelCode.toLowerCase());

            // duplicato?
            if (bookingDAO.findByExternalBookingId(externalId).isPresent()) {
                return BookingImportPreviewRowDTO.builder()
                        .rowNumber(rowNum)
                        .externalBookingId(externalId)
                        .guestName(guestName)
                        .propertyCode(propCode)
                        .propertyName(property.getDisplayName())
                        .channelCode(channelCode)
                        .channelName(canale != null ? canale.getNome() : channelCode)
                        .checkinDate(checkin)
                        .checkoutDate(checkout)
                        .grossAmount(gross)
                        .status("duplicata")
                        .rawData(buildRawDTO(cols))
                        .build();
            }

            return BookingImportPreviewRowDTO.builder()
                    .rowNumber(rowNum)
                    .externalBookingId(externalId)
                    .guestName(guestName)
                    .propertyCode(propCode)
                    .propertyName(property.getDisplayName())
                    .channelCode(channelCode)
                    .channelName(canale != null ? canale.getNome() : channelCode)
                    .checkinDate(checkin)
                    .checkoutDate(checkout)
                    .grossAmount(gross)
                    .status("nuova")
                    .rawData(buildRawDTO(cols))
                    .build();

        } catch (Exception e) {
            return errorRow(rowNum, cols, "Errore parsing: " + e.getMessage());
        }
    }

    private BookingImportRowDTO buildRawDTO(String[] cols) {
        return BookingImportRowDTO.builder()
                .externalBookingId(col(cols, 0))
                .channelCode(col(cols, 1).toLowerCase())
                .propertyCode(col(cols, 2))
                .guestName(col(cols, 3))
                .guestEmail(col(cols, 4))
                .guestPhone(col(cols, 5))
                .guestTaxCode(col(cols, 6))
                .checkinDate(parseDate(col(cols, 7)))
                .checkoutDate(parseDate(col(cols, 8)))
                .nights(parseIntOr(col(cols, 9), 0))
                .guests(parseIntOr(col(cols, 10), 1))
                .status(col(cols, 11))
                .grossAmount(parseDecimalOr(col(cols, 12), BigDecimal.ZERO))
                .otaCommissionAmount(parseDecimalOr(col(cols, 13), BigDecimal.ZERO))
                .cleaningAmount(parseDecimalOr(col(cols, 14), BigDecimal.ZERO))
                .touristTaxAmount(parseDecimalOr(col(cols, 15), BigDecimal.ZERO))
                .touristTaxIncluded("true".equalsIgnoreCase(col(cols, 16)))
                .currency(col(cols, 17).isBlank() ? "EUR" : col(cols, 17))
                .build();
    }

    private BookingImportPreviewRowDTO errorRow(int rowNum, String[] cols, String msg) {
        return BookingImportPreviewRowDTO.builder()
                .rowNumber(rowNum)
                .externalBookingId(cols.length > 0 ? col(cols, 0) : "?")
                .guestName(cols.length > 3 ? col(cols, 3) : "?")
                .propertyCode(cols.length > 2 ? col(cols, 2) : "?")
                .channelCode(cols.length > 1 ? col(cols, 1) : "?")
                .status("errore")
                .errorMessage(msg)
                .build();
    }

    private String col(String[] cols, int i) {
        return (i < cols.length && cols[i] != null) ? cols[i].trim() : "";
    }

    private LocalDate parseDate(String s) {
        try { return s.isBlank() ? null : LocalDate.parse(s); } catch (Exception e) { return null; }
    }

    private int parseIntOr(String s, int def) {
        try { return s.isBlank() ? def : Integer.parseInt(s); } catch (Exception e) { return def; }
    }

    private BigDecimal parseDecimalOr(String s, BigDecimal def) {
        try { return s.isBlank() ? def : new BigDecimal(s); } catch (Exception e) { return def; }
    }

    private BigDecimal orZero(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }
}
