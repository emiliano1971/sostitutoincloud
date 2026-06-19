package it.gavia.sostitutoincloud.service;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import it.gavia.sostitutoincloud.config.ImportSessionCache;
import it.gavia.sostitutoincloud.dao.BookingDAO;
import it.gavia.sostitutoincloud.dao.CanaleOtaDAO;
import it.gavia.sostitutoincloud.dao.PropertyDAO;
import it.gavia.sostitutoincloud.dao.StatoPrenotazioneDAO;
import it.gavia.sostitutoincloud.dto.importing.BookingImportConfirmDTO;
import it.gavia.sostitutoincloud.dto.importing.BookingImportPreviewDTO;
import it.gavia.sostitutoincloud.dto.importing.BookingImportPreviewRowDTO;
import it.gavia.sostitutoincloud.dto.importing.BookingImportResultDTO;
import it.gavia.sostitutoincloud.dto.importing.BookingImportRowDTO;
import it.gavia.sostitutoincloud.model.Booking;
import it.gavia.sostitutoincloud.model.CanaleOta;
import it.gavia.sostitutoincloud.model.Property;
import it.gavia.sostitutoincloud.model.StatoPrenotazione;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
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
    private final StatoPrenotazioneDAO statoPrenotazioneDAO;
    private final ImportSessionCache importSessionCache;
    private final AuditService auditService;

    public BookingImportService(BookingDAO bookingDAO,
                                PropertyDAO propertyDAO,
                                CanaleOtaDAO canaleOtaDAO,
                                StatoPrenotazioneDAO statoPrenotazioneDAO,
                                ImportSessionCache importSessionCache,
                                AuditService auditService) {
        this.bookingDAO = bookingDAO;
        this.propertyDAO = propertyDAO;
        this.canaleOtaDAO = canaleOtaDAO;
        this.statoPrenotazioneDAO = statoPrenotazioneDAO;
        this.importSessionCache = importSessionCache;
        this.auditService = auditService;
    }

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
        Map<String, Property> propertyByCode = propertyDAO.findByTenantId(tenantId).stream()
                .collect(Collectors.toMap(Property::getInternalCode, p -> p));
        Map<String, CanaleOta> canaleByCode = canaleOtaDAO.findAll().stream()
                .collect(Collectors.toMap(CanaleOta::getCodice, c -> c));

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
                Property property = propertyByCode.get(row.getPropertyCode());
                CanaleOta canale = canaleByCode.get(row.getChannelCode());

                Booking booking = Booking.builder()
                        .fkTenantId(tenantId)
                        .fkPropertyId(property != null ? property.getId() : null)
                        .fkCanaleOtaId(canale != null ? canale.getId() : null)
                        .externalBookingId(row.getExternalBookingId())
                        .guestName(row.getGuestName())
                        .guestTaxCode(row.getGuestTaxCode())
                        .checkinDate(row.getCheckinDate())
                        .checkoutDate(row.getCheckoutDate())
                        .nights(row.getNights())
                        .guests(row.getGuests())
                        .grossAmount(row.getGrossAmount())
                        .otaCommissionAmount(orZero(row.getOtaCommissionAmount()))
                        .cleaningAmount(orZero(row.getCleaningAmount()))
                        .pmFeeAmount(BigDecimal.ZERO)
                        .ownerNetAmount(BigDecimal.ZERO)
                        .withholdingAmount(BigDecimal.ZERO)
                        .touristTaxAmount(orZero(row.getTouristTaxAmount()))
                        .touristTaxIncludedInGross(Boolean.TRUE.equals(row.getTouristTaxIncluded()))
                        .touristTaxCollection(canale != null
                                ? canale.getTouristTaxCollection() : "contanti")
                        .fkStatoPrenotazioneId(statoImportedId)
                        .paymentStatus("pending")
                        .fkStatoDocumentoId(1)  // 1 = draft
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
