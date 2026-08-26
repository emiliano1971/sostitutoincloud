Leggi il file CLAUDE.md prima di procedere.

Crea il primo test JUnit per verificare
che l'import di una prenotazione con
anagrafica completa (Marco Bianchi)
scriva correttamente tutti i dati
anagrafici su booking.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
1. DIPENDENZE TEST IN pom.xml
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Verifica che esistano in pom.xml
nella sezione <dependencies>:

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>

Se mancano aggiungile.
Verifica anche che esista la cartella
src/test/java/ — se non esiste creala.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
2. FILE EXCEL DI TEST
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea i file Excel di test in
src/test/resources/import/:

booking-marco-bianchi.xlsx
con una sola riga:
Id: BC001AA2026
Stato: CONFIRMADA
Struttura: Ca' Serenella
Importo totale: 520.00
Adulti: 2
Bambini: 0
Neonati: 0
Arrivo: 2026-09-01
Partenza: 2026-09-08
Numero di notti: 7
Origine: Booking.com
Cliente: Marco Bianchi
Commissione del canale: 78.00

ospiti-marco-bianchi.xlsx
con una sola riga:
Id: BC001AA2026
Arrivo: 01/09/2026
Partenza: 08/09/2026
Tipo di ospite: Ospite individuale
Nome: Marco
Cognome: Bianchi
Data di nascita: 15/06/1980
Sesso: Uomo
Documento: Carta di identità
Nº Documento: AX4521367
Comune emittente: Milano
Comune: Milano
Provincia: MI
Nazione: 100000100
Nazionalità: 100000100

Crea i file con Apache POI
(già nel classpath) in un metodo
@BeforeAll o direttamente come
risorse statiche.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
3. TEST CLASS
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Crea src/test/java/it/gavia/
sostitutoincloud/service/
BookingImportServiceTest.java:

@SpringBootTest
@ActiveProfiles("local")
@Transactional  ← rollback automatico
dopo ogni test
class BookingImportServiceTest {

    @Autowired
    BookingImportService bookingImportService;

    @Autowired
    BookingDAO bookingDAO;

    @Autowired
    CodiceFiscaleService codiceFiscaleService;

    // tenantId del tenant di test
    // (tenant 1 = Casa Vacanze Italia SRL)
    private static final Integer TENANT_ID = 1;

    @Test
    @DisplayName("Import Marco Bianchi: " +
      "anagrafica completa scritta su booking")
    void importMarcoBianchi_anagraficaCompleta()
        throws Exception {

      // ARRANGE
      // Carica i file Excel di test
      // come MockMultipartFile
      byte[] bookingBytes = getClass()
        .getResourceAsStream(
          "/import/booking-marco-bianchi.xlsx")
        .readAllBytes();
      byte[] guestsBytes = getClass()
        .getResourceAsStream(
          "/import/ospiti-marco-bianchi.xlsx")
        .readAllBytes();

      MockMultipartFile bookingFile =
        new MockMultipartFile(
          "bookingFile",
          "booking-marco-bianchi.xlsx",
          "application/vnd.openxmlformats-" +
          "officedocument.spreadsheetml.sheet",
          bookingBytes);

      MockMultipartFile guestFile =
        new MockMultipartFile(
          "guestFile",
          "ospiti-marco-bianchi.xlsx",
          "application/vnd.openxmlformats-" +
          "officedocument.spreadsheetml.sheet",
          guestsBytes);

      // ACT - Step 1: upload
      ImportUploadResponseDTO upload =
        bookingImportService.uploadFiles(
          TENANT_ID, bookingFile, guestFile,
          0); // headerRow = 0

      assertNotNull(upload.getBookingSessionId());
      assertNotNull(upload.getGuestSessionId());

      // Step 2: mapping (usa suggestedMapping)
      ImportColumnMappingDTO mapping =
        new ImportColumnMappingDTO();
      mapping.setBookingMapping(
        upload.getSuggestedBookingMapping());
      mapping.setGuestMapping(
        upload.getSuggestedGuestMapping());

      // Step 3: preview
      BookingImportPreviewDTO preview =
        bookingImportService.previewWithMapping(
          TENANT_ID,
          upload.getBookingSessionId(),
          upload.getGuestSessionId(),
          mapping);

      // Verifica preview
      assertEquals(1, preview.getNewCount(),
        "Deve esserci 1 prenotazione nuova");
      assertEquals(0, preview.getErrorCount(),
        "Non devono esserci errori");

      BookingImportPreviewRowDTO row =
        preview.getRows().get(0);
      assertEquals("BC001AA2026",
        row.getExternalBookingId());
      assertEquals("Marco Bianchi",
        row.getGuestName());

      // Step 4: confirm
      ImportConfirmRequestDTO confirm =
        new ImportConfirmRequestDTO();
      confirm.setBookingSessionId(
        upload.getBookingSessionId());
      confirm.setGuestSessionId(
        upload.getGuestSessionId());
      confirm.setSelectedRowNumbers(
        List.of(row.getRowNumber()));

      bookingImportService.confirmImport(
        TENANT_ID, confirm);

      // ASSERT - verifica booking su DB
      Optional<Booking> saved =
        bookingDAO.findByExternalBookingId(
          "BC001AA2026");

      assertTrue(saved.isPresent(),
        "Il booking deve essere presente nel DB");

      Booking b = saved.get();

      // Dati prenotazione
      assertEquals("BC001AA2026",
        b.getExternalBookingId());
      assertEquals(TENANT_ID, b.getFkTenantId());
      assertEquals(
        LocalDate.of(2026, 9, 1),
        b.getCheckinDate());
      assertEquals(
        LocalDate.of(2026, 9, 8),
        b.getCheckoutDate());
      assertEquals(7, b.getNights());
      assertEquals(2, b.getGuests());

      // Dati anagrafici ospite ← il punto
      // critico che vuoi testare
      assertEquals("Marco Bianchi",
        b.getGuestName(),
        "guestName deve essere nome + cognome");

      assertNotNull(b.getGuestBirthDate(),
        "Data di nascita non deve essere null");
      assertEquals(
        LocalDate.of(1980, 6, 15),
        b.getGuestBirthDate(),
        "Data di nascita deve essere 15/06/1980");

      assertNotNull(b.getGuestBirthPlace(),
        "Comune di nascita non deve essere null");
      assertEquals("Milano",
        b.getGuestBirthPlace(),
        "Comune di nascita deve essere Milano");

      assertNotNull(b.getGuestSesso(),
        "Sesso non deve essere null");
      assertEquals("M", b.getGuestSesso(),
        "Sesso deve essere M (Uomo)");

      assertNotNull(b.getGuestDocNumber(),
        "Numero documento non deve essere null");
      assertEquals("AX4521367",
        b.getGuestDocNumber());

      // CF calcolato automaticamente
      assertNotNull(b.getGuestTaxCode(),
        "CF non deve essere null");
      assertEquals(16, b.getGuestTaxCode()
        .length(),
        "CF deve essere lungo 16 caratteri");

      // Verifica CF corretto
      String cfAtteso = codiceFiscaleService
        .calcola("Bianchi", "Marco",
          LocalDate.of(1980, 6, 15),
          "M", "Milano");
      assertEquals(cfAtteso,
        b.getGuestTaxCode(),
        "CF deve essere " + cfAtteso);
    }
}

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
4. ESEGUI IL TEST
   ━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

mvn -Plocal test -Dtest=BookingImportServiceTest

Se il test fallisce riporta:
- Il messaggio di errore esatto
- Il valore atteso vs quello trovato
- Il punto del codice dove fallisce

Se il test passa riporta:
- Output mvn test con BUILD SUCCESS
- Conferma che tutti gli assert
  sono stati verificati

Non correggere il codice di produzione
senza prima riportare il risultato
del test — vogliamo vedere cosa
fallisce esattamente.