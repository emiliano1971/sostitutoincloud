package it.gavia.sostitutoincloud.controller;

import it.gavia.sostitutoincloud.dao.BookingDAO;
import it.gavia.sostitutoincloud.dao.CanaleOtaDAO;
import it.gavia.sostitutoincloud.dao.CuRecordDAO;
import it.gavia.sostitutoincloud.dao.F24RecordDAO;
import it.gavia.sostitutoincloud.dao.FiscalDocumentDAO;
import it.gavia.sostitutoincloud.dao.SettlementDAO;
import it.gavia.sostitutoincloud.dao.OwnerProfileDAO;
import it.gavia.sostitutoincloud.dao.PropertyDAO;
import it.gavia.sostitutoincloud.dao.PropertyOtaCodeDAO;
import it.gavia.sostitutoincloud.dao.UtenteDAO;
import it.gavia.sostitutoincloud.dao.CodiceTributoDAO;
import it.gavia.sostitutoincloud.dao.RegimeFiscaleDAO;
import it.gavia.sostitutoincloud.dao.RegolaTassaSoggiornoDAO;
import it.gavia.sostitutoincloud.dao.ScenarioFiscaleDAO;
import it.gavia.sostitutoincloud.dao.SdiEsitoDAO;
import it.gavia.sostitutoincloud.dao.StatoDocumentoDAO;
import it.gavia.sostitutoincloud.dao.StatoPrenotazioneDAO;
import it.gavia.sostitutoincloud.dao.TenantDAO;
import it.gavia.sostitutoincloud.dao.TipoDocumentoDAO;
import it.gavia.sostitutoincloud.dao.TipoImmobileDAO;
import it.gavia.sostitutoincloud.model.Booking;
import it.gavia.sostitutoincloud.model.CanaleOta;
import it.gavia.sostitutoincloud.model.CuRecord;
import it.gavia.sostitutoincloud.model.F24Record;
import it.gavia.sostitutoincloud.model.FiscalDocument;
import it.gavia.sostitutoincloud.model.Settlement;
import it.gavia.sostitutoincloud.model.OwnerProfile;
import it.gavia.sostitutoincloud.model.Property;
import it.gavia.sostitutoincloud.model.PropertyOtaCode;
import it.gavia.sostitutoincloud.model.Utente;
import it.gavia.sostitutoincloud.model.CodiceTributo;
import it.gavia.sostitutoincloud.model.RegimeFiscale;
import it.gavia.sostitutoincloud.model.RegolaTassaSoggiorno;
import it.gavia.sostitutoincloud.model.ScenarioFiscale;
import it.gavia.sostitutoincloud.model.SdiEsito;
import it.gavia.sostitutoincloud.model.StatoDocumento;
import it.gavia.sostitutoincloud.model.StatoPrenotazione;
import it.gavia.sostitutoincloud.model.Tenant;
import it.gavia.sostitutoincloud.model.TipoDocumento;
import it.gavia.sostitutoincloud.model.TipoImmobile;
import lombok.extern.log4j.Log4j2;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Log4j2
@Profile("local")
@RestController
@RequestMapping("/api/public/test")
public class TestController {

    private final TenantDAO tenantDAO;
    private final StatoPrenotazioneDAO statoPrenotazioneDAO;
    private final StatoDocumentoDAO statoDocumentoDAO;
    private final RegimeFiscaleDAO regimeFiscaleDAO;
    private final CanaleOtaDAO canaleOtaDAO;
    private final TipoImmobileDAO tipoImmobileDAO;
    private final TipoDocumentoDAO tipoDocumentoDAO;
    private final CodiceTributoDAO codiceTributoDAO;
    private final SdiEsitoDAO sdiEsitoDAO;
    private final ScenarioFiscaleDAO scenarioFiscaleDAO;
    private final RegolaTassaSoggiornoDAO regolaTassaSoggiornoDAO;
    private final UtenteDAO utenteDAO;
    private final OwnerProfileDAO ownerProfileDAO;
    private final PropertyDAO propertyDAO;
    private final PropertyOtaCodeDAO propertyOtaCodeDAO;
    private final BookingDAO bookingDAO;
    private final FiscalDocumentDAO fiscalDocumentDAO;
    private final SettlementDAO settlementDAO;
    private final F24RecordDAO f24RecordDAO;
    private final CuRecordDAO cuRecordDAO;

    public TestController(TenantDAO tenantDAO,
                          StatoPrenotazioneDAO statoPrenotazioneDAO,
                          StatoDocumentoDAO statoDocumentoDAO,
                          RegimeFiscaleDAO regimeFiscaleDAO,
                          CanaleOtaDAO canaleOtaDAO,
                          TipoImmobileDAO tipoImmobileDAO,
                          TipoDocumentoDAO tipoDocumentoDAO,
                          CodiceTributoDAO codiceTributoDAO,
                          SdiEsitoDAO sdiEsitoDAO,
                          ScenarioFiscaleDAO scenarioFiscaleDAO,
                          RegolaTassaSoggiornoDAO regolaTassaSoggiornoDAO,
                          UtenteDAO utenteDAO,
                          OwnerProfileDAO ownerProfileDAO,
                          PropertyDAO propertyDAO,
                          PropertyOtaCodeDAO propertyOtaCodeDAO,
                          BookingDAO bookingDAO,
                          FiscalDocumentDAO fiscalDocumentDAO,
                          SettlementDAO settlementDAO,
                          F24RecordDAO f24RecordDAO,
                          CuRecordDAO cuRecordDAO) {
        this.tenantDAO = tenantDAO;
        this.statoPrenotazioneDAO = statoPrenotazioneDAO;
        this.statoDocumentoDAO = statoDocumentoDAO;
        this.regimeFiscaleDAO = regimeFiscaleDAO;
        this.canaleOtaDAO = canaleOtaDAO;
        this.tipoImmobileDAO = tipoImmobileDAO;
        this.tipoDocumentoDAO = tipoDocumentoDAO;
        this.codiceTributoDAO = codiceTributoDAO;
        this.sdiEsitoDAO = sdiEsitoDAO;
        this.scenarioFiscaleDAO = scenarioFiscaleDAO;
        this.regolaTassaSoggiornoDAO = regolaTassaSoggiornoDAO;
        this.utenteDAO = utenteDAO;
        this.ownerProfileDAO = ownerProfileDAO;
        this.propertyDAO = propertyDAO;
        this.propertyOtaCodeDAO = propertyOtaCodeDAO;
        this.bookingDAO = bookingDAO;
        this.fiscalDocumentDAO = fiscalDocumentDAO;
        this.settlementDAO = settlementDAO;
        this.f24RecordDAO = f24RecordDAO;
        this.cuRecordDAO = cuRecordDAO;
    }

    @GetMapping("/tenants")
    public ResponseEntity<List<Tenant>> getTenants() {
        log.debug("TestController.getTenants() - richiesta ricevuta");
        return ResponseEntity.ok(tenantDAO.findAll());
    }

    @GetMapping("/stato-prenotazione")
    public ResponseEntity<List<StatoPrenotazione>> getStatoPrenotazione() {
        log.debug("TestController.getStatoPrenotazione() - richiesta ricevuta");
        return ResponseEntity.ok(statoPrenotazioneDAO.findAll());
    }

    @GetMapping("/stato-documento")
    public ResponseEntity<List<StatoDocumento>> getStatoDocumento() {
        log.debug("TestController.getStatoDocumento() - richiesta ricevuta");
        return ResponseEntity.ok(statoDocumentoDAO.findAll());
    }

    @GetMapping("/regime-fiscale")
    public ResponseEntity<List<RegimeFiscale>> getRegimeFiscale() {
        log.debug("TestController.getRegimeFiscale() - richiesta ricevuta");
        return ResponseEntity.ok(regimeFiscaleDAO.findAll());
    }

    @GetMapping("/canale-ota")
    public ResponseEntity<List<CanaleOta>> getCanaleOta() {
        log.debug("TestController.getCanaleOta() - richiesta ricevuta");
        return ResponseEntity.ok(canaleOtaDAO.findAll());
    }

    @GetMapping("/tipo-immobile")
    public ResponseEntity<List<TipoImmobile>> getTipoImmobile() {
        log.debug("TestController.getTipoImmobile() - richiesta ricevuta");
        return ResponseEntity.ok(tipoImmobileDAO.findAll());
    }

    @GetMapping("/tipo-documento")
    public ResponseEntity<List<TipoDocumento>> getTipoDocumento() {
        log.debug("TestController.getTipoDocumento() - richiesta ricevuta");
        return ResponseEntity.ok(tipoDocumentoDAO.findAll());
    }

    @GetMapping("/codice-tributo")
    public ResponseEntity<List<CodiceTributo>> getCodiceTributo() {
        log.debug("TestController.getCodiceTributo() - richiesta ricevuta");
        return ResponseEntity.ok(codiceTributoDAO.findAll());
    }

    @GetMapping("/sdi-esito")
    public ResponseEntity<List<SdiEsito>> getSdiEsito() {
        log.debug("TestController.getSdiEsito() - richiesta ricevuta");
        return ResponseEntity.ok(sdiEsitoDAO.findAll());
    }

    @GetMapping("/scenario-fiscale")
    public ResponseEntity<List<ScenarioFiscale>> getScenarioFiscale() {
        log.debug("TestController.getScenarioFiscale() - richiesta ricevuta");
        return ResponseEntity.ok(scenarioFiscaleDAO.findAll());
    }

    @GetMapping("/regole-tassa-soggiorno")
    public ResponseEntity<List<RegolaTassaSoggiorno>> getRegoleTassaSoggiorno() {
        log.debug("TestController.getRegoleTassaSoggiorno() - richiesta ricevuta");
        return ResponseEntity.ok(regolaTassaSoggiornoDAO.findAll());
    }

    @GetMapping("/utenti")
    public ResponseEntity<List<Utente>> getUtenti() {
        log.debug("TestController.getUtenti() - richiesta ricevuta");
        return ResponseEntity.ok(utenteDAO.findAll());
    }

    @GetMapping("/utenti/{id}")
    public ResponseEntity<Utente> getUtenteById(@PathVariable Integer id) {
        log.debug("TestController.getUtenteById() - id={}", id);
        return utenteDAO.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/owner-profiles")
    public ResponseEntity<List<OwnerProfile>> getOwnerProfiles() {
        log.debug("TestController.getOwnerProfiles() - richiesta ricevuta");
        return ResponseEntity.ok(ownerProfileDAO.findAll());
    }

    @GetMapping("/owner-profiles/{id}")
    public ResponseEntity<OwnerProfile> getOwnerProfileById(@PathVariable Integer id) {
        log.debug("TestController.getOwnerProfileById() - id={}", id);
        return ownerProfileDAO.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/properties")
    public ResponseEntity<List<Property>> getProperties() {
        log.debug("TestController.getProperties() - richiesta ricevuta");
        return ResponseEntity.ok(propertyDAO.findAll());
    }

    @GetMapping("/properties/{id}")
    public ResponseEntity<Property> getPropertyById(@PathVariable Integer id) {
        log.debug("TestController.getPropertyById() - id={}", id);
        return propertyDAO.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/properties/owner/{ownerId}")
    public ResponseEntity<List<Property>> getPropertiesByOwner(@PathVariable Integer ownerId) {
        log.debug("TestController.getPropertiesByOwner() - ownerId={}", ownerId);
        return ResponseEntity.ok(propertyDAO.findByOwnerId(ownerId));
    }

    @GetMapping("/property-ota-codes/{propertyId}")
    public ResponseEntity<List<PropertyOtaCode>> getPropertyOtaCodes(@PathVariable Integer propertyId) {
        log.debug("TestController.getPropertyOtaCodes() - propertyId={}", propertyId);
        return ResponseEntity.ok(propertyOtaCodeDAO.findByPropertyId(propertyId));
    }

    @GetMapping("/bookings")
    public ResponseEntity<List<Booking>> getBookings() {
        log.debug("TestController.getBookings() - richiesta ricevuta");
        return ResponseEntity.ok(bookingDAO.findAll());
    }

    @GetMapping("/bookings/{id}")
    public ResponseEntity<Booking> getBookingById(@PathVariable Integer id) {
        log.debug("TestController.getBookingById() - id={}", id);
        return bookingDAO.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/bookings/tenant/{tenantId}")
    public ResponseEntity<List<Booking>> getBookingsByTenant(@PathVariable Integer tenantId) {
        log.debug("TestController.getBookingsByTenant() - tenantId={}", tenantId);
        return ResponseEntity.ok(bookingDAO.findByTenantId(tenantId));
    }

    @GetMapping("/bookings/property/{propertyId}")
    public ResponseEntity<List<Booking>> getBookingsByProperty(@PathVariable Integer propertyId) {
        log.debug("TestController.getBookingsByProperty() - propertyId={}", propertyId);
        return ResponseEntity.ok(bookingDAO.findByPropertyId(propertyId));
    }

    @GetMapping("/fiscal-documents")
    public ResponseEntity<List<FiscalDocument>> getFiscalDocuments() {
        log.debug("TestController.getFiscalDocuments() - richiesta ricevuta");
        return ResponseEntity.ok(fiscalDocumentDAO.findAll());
    }

    @GetMapping("/fiscal-documents/{id}")
    public ResponseEntity<FiscalDocument> getFiscalDocumentById(@PathVariable Integer id) {
        log.debug("TestController.getFiscalDocumentById() - id={}", id);
        return fiscalDocumentDAO.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/fiscal-documents/booking/{bookingId}")
    public ResponseEntity<List<FiscalDocument>> getFiscalDocumentsByBooking(@PathVariable Integer bookingId) {
        log.debug("TestController.getFiscalDocumentsByBooking() - bookingId={}", bookingId);
        return ResponseEntity.ok(fiscalDocumentDAO.findByBookingId(bookingId));
    }

    @GetMapping("/settlements")
    public ResponseEntity<List<Settlement>> getSettlements() {
        log.debug("TestController.getSettlements() - richiesta ricevuta");
        return ResponseEntity.ok(settlementDAO.findAll());
    }

    @GetMapping("/settlements/{id}")
    public ResponseEntity<Settlement> getSettlementById(@PathVariable Integer id) {
        log.debug("TestController.getSettlementById() - id={}", id);
        return settlementDAO.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/settlements/owner/{ownerId}")
    public ResponseEntity<List<Settlement>> getSettlementsByOwner(@PathVariable Integer ownerId) {
        log.debug("TestController.getSettlementsByOwner() - ownerId={}", ownerId);
        return ResponseEntity.ok(settlementDAO.findByOwnerId(ownerId));
    }

    @GetMapping("/f24-records/tenant/{tenantId}")
    public ResponseEntity<List<F24Record>> getF24RecordsByTenant(@PathVariable Integer tenantId) {
        log.debug("TestController.getF24RecordsByTenant() - tenantId={}", tenantId);
        return ResponseEntity.ok(f24RecordDAO.findByTenantId(tenantId));
    }

    @GetMapping("/cu-records/tenant/{tenantId}")
    public ResponseEntity<List<CuRecord>> getCuRecordsByTenant(@PathVariable Integer tenantId) {
        log.debug("TestController.getCuRecordsByTenant() - tenantId={}", tenantId);
        return ResponseEntity.ok(cuRecordDAO.findByTenantId(tenantId));
    }
}
