package it.gavia.sostitutoincloud.service;

import it.gavia.sostitutoincloud.dao.BookingDAO;
import it.gavia.sostitutoincloud.dao.CanaleOtaDAO;
import it.gavia.sostitutoincloud.dto.importing.BookingImportConfirmDTO;
import it.gavia.sostitutoincloud.dto.importing.BookingImportPreviewDTO;
import it.gavia.sostitutoincloud.dto.importing.BookingImportPreviewRowDTO;
import it.gavia.sostitutoincloud.dto.importing.ImportColumnMappingDTO;
import it.gavia.sostitutoincloud.dto.importing.ImportUploadResponseDTO;
import it.gavia.sostitutoincloud.model.Booking;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifica che l'import di una prenotazione con anagrafica ospite completa scriva
 * tutti i dati anagrafici su booking, compreso il codice fiscale calcolato.
 *
 * I due file Excel sono generati in memoria con un BOOKING_ID univoco per esecuzione:
 * con un id fisso il test diventava "duplicata" appena quella prenotazione finiva a DB
 * (es. dopo un import reale da UI con gli stessi file).
 *
 * @Transactional fa il rollback dopo il test: la prenotazione importata non resta a DB.
 */
@SpringBootTest
@ActiveProfiles("local")
@Transactional
class BookingImportServiceTest {

    private static final String XLSX_MIME =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    /** Tenant di test: 1 = Casa Vacanze Italia SRL. */
    private static final Integer TENANT_ID = 1;
    /** Id prenotazione univoco per esecuzione: rende il test indipendente dai dati a DB. */
    private static final String BOOKING_ID = "TEST-" + System.currentTimeMillis();

    // Intestazioni dei tracciati OTA riconosciute dal mapping automatico dell'import.
    private static final String[] BOOKING_HEADERS = {
            "Id", "Stato", "Struttura", "Importo totale", "Adulti", "Bambini", "Neonati",
            "Arrivo", "Partenza", "Numero di notti", "Origine", "Cliente", "Commissione del canale"
    };
    private static final String[] GUEST_HEADERS = {
            "Id", "Arrivo", "Partenza", "Tipo di ospite", "Nome", "Cognome", "Data di nascita",
            "Sesso", "Documento", "Nº Documento", "Comune emittente", "Comune", "Provincia",
            "Nazione", "Nazionalità"
    };

    @Autowired
    BookingImportService bookingImportService;

    @Autowired
    BookingDAO bookingDAO;

    @Autowired
    CanaleOtaDAO canaleOtaDAO;

    @Autowired
    CodiceFiscaleService codiceFiscaleService;

    /**
     * Id del canale della prenotazione di test (Origine = "Booking.com"): fa parte
     * della chiave di ricerca di findByExternalBookingId. Risolto per codice, non
     * hardcodato, così il test regge un cambio di id sulla lookup.
     */
    private Integer canaleTestId() {
        return canaleOtaDAO.findByCodice("booking").orElseThrow().getId();
    }

    /** Foglio xlsx a due righe (intestazioni + valori) generato in memoria. */
    private static byte[] xlsx(String[] headers, String[] valori) throws IOException {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Foglio1");
            Row intestazioni = sheet.createRow(0);
            for (int i = 0; i < headers.length; i++) {
                intestazioni.createCell(i).setCellValue(headers[i]);
            }
            Row valoriRow = sheet.createRow(1);
            for (int i = 0; i < valori.length; i++) {
                valoriRow.createCell(i).setCellValue(valori[i]);
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    private MockMultipartFile bookingFile() throws IOException {
        byte[] bytes = xlsx(BOOKING_HEADERS, new String[]{
                BOOKING_ID, "CONFIRMADA", "Ca' Serenella", "520.00", "2", "0", "0",
                "2026-09-01", "2026-09-08", "7", "Booking.com", "Marco Bianchi", "78.00"
        });
        return new MockMultipartFile("bookingFile", "booking-" + BOOKING_ID + ".xlsx", XLSX_MIME, bytes);
    }

    private MockMultipartFile guestFile() throws IOException {
        byte[] bytes = xlsx(GUEST_HEADERS, new String[]{
                BOOKING_ID, "01/09/2026", "08/09/2026", "Ospite individuale", "Marco", "Bianchi",
                "15/06/1980", "Uomo", "Carta di identità", "AX4521367", "Milano", "Milano", "MI",
                "100000100", "100000100"
        });
        return new MockMultipartFile("guestFile", "ospiti-" + BOOKING_ID + ".xlsx", XLSX_MIME, bytes);
    }

    @Test
    @DisplayName("Import Marco Bianchi: anagrafica completa scritta su booking")
    void importMarcoBianchi_anagraficaCompleta() throws Exception {
        // ARRANGE — nessuna prenotazione con questo id può esistere: l'id è generato ora
        assertTrue(bookingDAO.findByExternalBookingId(BOOKING_ID, TENANT_ID, canaleTestId()).isEmpty(),
                "L'id di test deve essere nuovo: " + BOOKING_ID);

        // ACT - Step 1: upload
        ImportUploadResponseDTO upload =
                bookingImportService.uploadFiles(TENANT_ID, bookingFile(), guestFile(), 0);

        assertNotNull(upload.getBookingSessionId());
        assertNotNull(upload.getGuestSessionId());

        // Step 2: mapping suggerito dagli header dei file
        ImportColumnMappingDTO mapping = new ImportColumnMappingDTO();
        mapping.setBookingMapping(upload.getSuggestedBookingMapping());
        mapping.setGuestMapping(upload.getSuggestedGuestMapping());

        // Step 3: preview
        BookingImportPreviewDTO preview = bookingImportService.previewWithMapping(
                TENANT_ID, upload.getBookingSessionId(), upload.getGuestSessionId(), mapping);

        BookingImportPreviewRowDTO row = preview.getRows().get(0);
        assertEquals(0, preview.getErrorCount(),
                "Non devono esserci errori. Messaggio riga: " + row.getErrorMessage());
        assertEquals(1, preview.getNewCount(),
                "Deve esserci 1 prenotazione nuova, stato riga: " + row.getStatus());
        assertEquals(BOOKING_ID, row.getExternalBookingId());
        assertEquals("Marco Bianchi", row.getGuestName());

        // Step 4: confirm
        BookingImportConfirmDTO confirm = new BookingImportConfirmDTO();
        confirm.setImportSessionId(preview.getImportSessionId());
        confirm.setSelectedRowNumbers(List.of(row.getRowNumber()));

        bookingImportService.confirm(TENANT_ID, confirm);

        // ASSERT - booking su DB
        Optional<Booking> saved = bookingDAO.findByExternalBookingId(BOOKING_ID, TENANT_ID, canaleTestId());
        assertTrue(saved.isPresent(), "Il booking deve essere presente nel DB");
        Booking b = saved.get();

        // Dati prenotazione
        assertEquals(BOOKING_ID, b.getExternalBookingId());
        assertEquals(TENANT_ID, b.getFkTenantId());
        assertEquals(LocalDate.of(2026, 9, 1), b.getCheckinDate());
        assertEquals(LocalDate.of(2026, 9, 8), b.getCheckoutDate());
        assertEquals(7, b.getNights());
        assertEquals(2, b.getGuests());

        // Dati anagrafici ospite — il punto critico
        assertEquals("Marco Bianchi", b.getGuestName(), "guestName deve essere nome + cognome");

        assertNotNull(b.getGuestBirthDate(), "Data di nascita non deve essere null");
        assertEquals(LocalDate.of(1980, 6, 15), b.getGuestBirthDate(),
                "Data di nascita deve essere 15/06/1980");

        assertNotNull(b.getGuestBirthPlace(), "Comune di nascita non deve essere null");
        assertEquals("Milano", b.getGuestBirthPlace(), "Comune di nascita deve essere Milano");

        assertNotNull(b.getGuestSesso(), "Sesso non deve essere null");
        assertEquals("M", b.getGuestSesso(), "Sesso deve essere M (Uomo)");

        assertNotNull(b.getGuestDocNumber(), "Numero documento non deve essere null");
        assertEquals("AX4521367", b.getGuestDocNumber());

        // CF calcolato automaticamente
        assertNotNull(b.getGuestTaxCode(), "CF non deve essere null");
        assertEquals(16, b.getGuestTaxCode().length(), "CF deve essere lungo 16 caratteri");

        String cfAtteso = codiceFiscaleService.calcola(
                "Bianchi", "Marco", LocalDate.of(1980, 6, 15), "M", "Milano");
        assertEquals(cfAtteso, b.getGuestTaxCode(), "CF deve essere " + cfAtteso);
    }
}
