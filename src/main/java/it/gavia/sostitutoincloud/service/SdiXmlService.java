package it.gavia.sostitutoincloud.service;

import it.gavia.sostitutoincloud.dao.BookingDAO;
import it.gavia.sostitutoincloud.dao.ComuneItalianoDAO;
import it.gavia.sostitutoincloud.dao.FiscalDocumentDAO;
import it.gavia.sostitutoincloud.dao.PropertyDAO;
import it.gavia.sostitutoincloud.dao.SdiProgressivoDAO;
import it.gavia.sostitutoincloud.dao.StatoDocumentoDAO;
import it.gavia.sostitutoincloud.dao.TenantDAO;
import it.gavia.sostitutoincloud.dao.TenantSettingsDAO;
import it.gavia.sostitutoincloud.dao.TipoDocumentoDAO;
import it.gavia.sostitutoincloud.dto.sdi.SdiFileDTO;
import it.gavia.sostitutoincloud.model.Booking;
import it.gavia.sostitutoincloud.model.ComuneItaliano;
import it.gavia.sostitutoincloud.model.FiscalDocument;
import it.gavia.sostitutoincloud.model.Property;
import it.gavia.sostitutoincloud.model.StatoDocumento;
import it.gavia.sostitutoincloud.model.Tenant;
import it.gavia.sostitutoincloud.model.TenantSettings;
import it.gavia.sostitutoincloud.model.TipoDocumento;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Generazione del file XML FatturaPA (FPR12) per le fatture PM da trasmettere allo SDI.
 * Le ricevute owner NON vanno allo SDI: sono documenti interni tra PM e proprietario.
 *
 * Il file viene scritto in app.storage.sdi-outgoing-path; la trasmissione effettiva
 * (canale SDI) non è implementata qui.
 */
@Service
@Log4j2
public class SdiXmlService {

    // Codici della lookup tipo_documento: lo schema usa 'fattura'/'ricevuta',
    // i termini di dominio sono fattura_pm/ricevuta_owner.
    private static final String CODICE_FATTURA = "fattura";
    /**
     * Stati in cui lo SDI ha già preso in carico il file: nessuna ritrasmissione.
     * 'rejected' NON è incluso: una fattura scartata va corretta e ritrasmessa, ed è
     * il "Riprova" offerto dal dettaglio documento.
     */
    private static final Set<String> STATI_GIA_INVIATI = Set.of("sent_sdi", "accepted");

    private static final String VERSIONE = "FPR12";
    private static final String NAMESPACE = "http://ivaservizi.agenziaentrate.gov.it/docs/xsd/fatture/v1.2";
    private static final String CODICE_DEST_ITALIA = "0000000";
    private static final String CODICE_DEST_ESTERO = "XXXXXXX";
    private static final String REGIME_FISCALE_DEFAULT = "RF01";
    private static final String NATURA_ESENTE_DEFAULT = "N2.1";
    private static final String CAP_DEFAULT = "00000";
    private static final String PROVINCIA_DEFAULT = "RM";
    private static final DateTimeFormatter DATA_SDI = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final BigDecimal CENTO = new BigDecimal("100");

    /** "Via Roma 1, 00100 Roma RM" → indirizzo / CAP / comune / provincia. */
    private static final Pattern LOCALITA = Pattern.compile("^(\\d{5})\\s+(.+?)(?:\\s+([A-Za-z]{2}))?$");

    @Value("${app.storage.sdi-outgoing-path}")
    private String sdiOutgoingPath;

    @Value("${app.storage.sdi-elaborati-path}")
    private String sdiElaboratiPath;

    // Previsto dalla configurazione per un futuro template XML esterno: l'XML è
    // attualmente costruito in codice (StringBuilder), come da specifica.
    @Value("${app.storage.templates-path:}")
    private String templatesPath;

    private final FiscalDocumentDAO fiscalDocumentDAO;
    private final BookingDAO bookingDAO;
    private final TenantDAO tenantDAO;
    private final TenantSettingsDAO tenantSettingsDAO;
    private final PropertyDAO propertyDAO;
    private final SdiProgressivoDAO sdiProgressivoDAO;
    // Non previsti dalla specifica ma necessari: i tipi/stati sono lookup per codice,
    // il comune serve per la provincia (né tenant né property la memorizzano).
    private final TipoDocumentoDAO tipoDocumentoDAO;
    private final StatoDocumentoDAO statoDocumentoDAO;
    private final ComuneItalianoDAO comuneItalianoDAO;
    private final AuditService auditService;

    public SdiXmlService(FiscalDocumentDAO fiscalDocumentDAO,
                         BookingDAO bookingDAO,
                         TenantDAO tenantDAO,
                         TenantSettingsDAO tenantSettingsDAO,
                         PropertyDAO propertyDAO,
                         SdiProgressivoDAO sdiProgressivoDAO,
                         TipoDocumentoDAO tipoDocumentoDAO,
                         StatoDocumentoDAO statoDocumentoDAO,
                         ComuneItalianoDAO comuneItalianoDAO,
                         AuditService auditService) {
        this.fiscalDocumentDAO = fiscalDocumentDAO;
        this.bookingDAO = bookingDAO;
        this.tenantDAO = tenantDAO;
        this.tenantSettingsDAO = tenantSettingsDAO;
        this.propertyDAO = propertyDAO;
        this.sdiProgressivoDAO = sdiProgressivoDAO;
        this.tipoDocumentoDAO = tipoDocumentoDAO;
        this.statoDocumentoDAO = statoDocumentoDAO;
        this.comuneItalianoDAO = comuneItalianoDAO;
        this.auditService = auditService;
    }

    /**
     * Genera il file XML SDI della fattura PM e ne registra l'invio.
     *
     * @return percorso assoluto del file XML generato
     */
    public String generaEInvia(Integer tenantId, Integer fiscalDocumentId) {
        // 1. Documento + verifica tenant, tipo e stato
        FiscalDocument doc = fiscalDocumentDAO.findById(fiscalDocumentId)
                .filter(d -> tenantId.equals(d.getFkTenantId()))
                .orElseThrow(() -> new NoSuchElementException("Documento non trovato: id=" + fiscalDocumentId));

        String codiceTipo = tipoDocumentoDAO.findById(doc.getFkTipoDocumentoId())
                .map(TipoDocumento::getCodice)
                .orElseThrow(() -> new IllegalStateException(
                        "Tipo documento non risolvibile per il documento id=" + fiscalDocumentId));
        if (!CODICE_FATTURA.equals(codiceTipo)) {
            throw new IllegalStateException(
                    "Solo le fatture PM vanno trasmesse allo SDI: la ricevuta owner è un documento interno");
        }

        String statoCodice = statoDocumentoDAO.findById(doc.getFkStatoDocumentoId())
                .map(StatoDocumento::getCodice)
                .orElse(null);
        if (STATI_GIA_INVIATI.contains(statoCodice)) {
            throw new IllegalStateException("Fattura già inviata allo SDI");
        }

        // 2. Dati collegati
        Tenant tenant = tenantDAO.findById(tenantId)
                .orElseThrow(() -> new NoSuchElementException("Tenant non trovato: id=" + tenantId));
        Booking booking = doc.getFkBookingId() != null
                ? bookingDAO.findById(doc.getFkBookingId()).orElse(null)
                : null;
        if (booking == null) {
            throw new IllegalStateException("Documento senza prenotazione collegata: id=" + fiscalDocumentId);
        }
        Property property = propertyDAO.findById(booking.getFkPropertyId()).orElse(null);
        TenantSettings settings = tenantSettingsDAO.findByTenantId(tenantId).orElse(null);

        String piva = identificativoFiscale(tenant);
        if (piva.isBlank()) {
            throw new IllegalStateException(
                    "Partita IVA o codice fiscale del tenant mancanti: obbligatori per l'invio SDI");
        }

        // 3. Progressivo invio (atomico per tenant/anno)
        String progressivo = sdiProgressivoDAO.getNextProgressivo(tenantId, doc.getIssueDate().getYear());

        // 4. Nome file nel formato AdE: IT{PIVA}_{PROGRESSIVO}.xml
        String nomeFile = "IT" + piva + "_" + progressivo + ".xml";

        try {
            // 5. XML
            String xml = buildXml(doc, booking, tenant, settings, property, piva, progressivo);

            // 6-7. Directory e scrittura file in outgoing/ (lo legge il tunnel SDI)
            Path dir = Path.of(sdiOutgoingPath);
            Files.createDirectories(dir);
            Path filePath = dir.resolve(nomeFile);
            Files.writeString(filePath, xml, StandardCharsets.UTF_8);

            // 7b. Copia di archivio in elaborati/{piva}/{anno}/{progressivo}/: l'anno evita
            // collisioni fra progressivi omonimi di anni diversi (il contatore riparte da 1).
            // La copia in outgoing/ resta finché non arriva la risposta (la rimuove SdiRispostaService).
            String anno = String.valueOf(doc.getIssueDate().getYear());
            Path elaboratiDir = Path.of(sdiElaboratiPath, piva, anno, progressivo);
            Files.createDirectories(elaboratiDir);
            Path elaboratiFile = elaboratiDir.resolve(nomeFile);
            Files.copy(filePath, elaboratiFile, StandardCopyOption.REPLACE_EXISTING);
            log.info("SdiXmlService: fattura copiata in elaborati: {}", elaboratiFile);

            // 8. Stato documento → sent_sdi. sdi_file_path punta all'archivio in elaborati,
            // che è la copia stabile: quella in outgoing/ viene consumata dal tunnel.
            fiscalDocumentDAO.updateSdiInfo(fiscalDocumentId, progressivo, elaboratiFile.toString());

            // 9. Audit
            auditService.log("sdi.genera", "FiscalDocument", fiscalDocumentId,
                    "Generato XML SDI: " + nomeFile);

            // 10.
            log.info("SdiXmlService.generaEInvia() - docId={} file={}", fiscalDocumentId, filePath);
            return filePath.toString();
        } catch (IOException e) {
            // Il documento resta tracciato come 'error' con il motivo, così è possibile il retry.
            String msg = "Errore scrittura file SDI " + nomeFile + ": " + e.getMessage();
            fiscalDocumentDAO.updateSdiError(fiscalDocumentId, troncaErrore(msg));
            log.error("SdiXmlService.generaEInvia() - docId={} errore={}", fiscalDocumentId, msg, e);
            throw new IllegalStateException(msg, e);
        } catch (RuntimeException e) {
            String msg = "Errore generazione XML SDI: " + e.getMessage();
            fiscalDocumentDAO.updateSdiError(fiscalDocumentId, troncaErrore(msg));
            log.error("SdiXmlService.generaEInvia() - docId={} errore={}", fiscalDocumentId, msg, e);
            throw e;
        }
    }

    /**
     * Legge il file XML archiviato di un invio, per il download dal dettaglio documento.
     *
     * @param tenantId    tenant del chiamante; con null (super_admin) nessun vincolo
     * @param progressivo progressivo di invio SDI
     * @throws NoSuchElementException documento non trovato, di altro tenant, oppure file assente
     */
    public SdiFileDTO leggiFileXml(Integer tenantId, String progressivo) {
        FiscalDocument doc = fiscalDocumentDAO.findBySdiProgressivo(progressivo)
                .filter(d -> tenantId == null || tenantId.equals(d.getFkTenantId()))
                .orElseThrow(() -> new NoSuchElementException(
                        "Documento non trovato per progressivo SDI " + progressivo));

        if (doc.getSdiFilePath() == null || doc.getSdiFilePath().isBlank()) {
            throw new NoSuchElementException(
                    "Nessun file XML registrato per il progressivo " + progressivo);
        }
        Path path = Path.of(doc.getSdiFilePath());
        if (!Files.isRegularFile(path)) {
            throw new NoSuchElementException("File XML non presente su disco: " + path);
        }
        try {
            byte[] contenuto = Files.readAllBytes(path);
            log.info("SdiXmlService.leggiFileXml() - progressivo={} file={} bytes={}",
                    progressivo, path.getFileName(), contenuto.length);
            return SdiFileDTO.builder()
                    .nomeFile(path.getFileName().toString())
                    .contenuto(contenuto)
                    .build();
        } catch (IOException e) {
            throw new IllegalStateException("Errore lettura file XML " + path + ": " + e.getMessage(), e);
        }
    }

    // ────────────────────────────── costruzione XML ──────────────────────────────

    private String buildXml(FiscalDocument doc, Booking booking, Tenant tenant,
                            TenantSettings settings, Property property,
                            String piva, String progressivo) {
        boolean ospiteItaliano = isOspiteItaliano(booking);
        BigDecimal aliquota = nz(doc.getAliquotaIva());
        String natura = settings != null && settings.getNaturaIvaEsente() != null
                ? settings.getNaturaIvaEsente() : NATURA_ESENTE_DEFAULT;
        String regime = settings != null && settings.getRegimeFiscalePm() != null
                ? settings.getRegimeFiscalePm() : REGIME_FISCALE_DEFAULT;
        Indirizzo sedeTenant = sedeTenant(tenant);
        List<Linea> linee = buildLinee(doc, booking, aliquota);

        BigDecimal totImponibile = linee.stream().map(l -> l.imponibile)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        BigDecimal totImposta = linee.stream().map(l -> l.imposta)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(2, RoundingMode.HALF_UP);
        if (doc.getImponibile() != null && totImponibile.compareTo(doc.getImponibile()) != 0) {
            log.warn("SdiXmlService - imponibile righe {} != imponibile documento {} (docId={})",
                    totImponibile, doc.getImponibile(), doc.getId());
        }

        StringBuilder x = new StringBuilder(4096);
        x.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        x.append("<FatturaElettronica versione=\"").append(VERSIONE)
                .append("\" xmlns=\"").append(NAMESPACE).append("\">\n");

        // ── HEADER ──
        x.append("  <FatturaElettronicaHeader>\n");
        x.append("    <DatiTrasmissione>\n");
        x.append("      <IdTrasmittente>\n");
        x.append("        <IdPaese>IT</IdPaese>\n");
        x.append("        <IdCodice>").append(esc(piva)).append("</IdCodice>\n");
        x.append("      </IdTrasmittente>\n");
        x.append("      <ProgressivoInvio>").append(esc(progressivo)).append("</ProgressivoInvio>\n");
        x.append("      <FormatoTrasmissione>").append(VERSIONE).append("</FormatoTrasmissione>\n");
        x.append("      <CodiceDestinatario>")
                .append(ospiteItaliano ? CODICE_DEST_ITALIA : CODICE_DEST_ESTERO)
                .append("</CodiceDestinatario>\n");
        if (notBlank(tenant.getPec())) {
            x.append("      <PECDestinatario>").append(esc(tenant.getPec())).append("</PECDestinatario>\n");
        }
        x.append("    </DatiTrasmissione>\n");

        // ── CEDENTE/PRESTATORE = PM (tenant) ──
        x.append("    <CedentePrestatore>\n");
        x.append("      <DatiAnagrafici>\n");
        if (notBlank(tenant.getVatNumber())) {
            x.append("        <IdFiscaleIVA>\n");
            x.append("          <IdPaese>IT</IdPaese>\n");
            x.append("          <IdCodice>").append(esc(soloAlfanumerico(tenant.getVatNumber()))).append("</IdCodice>\n");
            x.append("        </IdFiscaleIVA>\n");
        }
        if (notBlank(tenant.getTaxCode())) {
            x.append("        <CodiceFiscale>").append(esc(soloAlfanumerico(tenant.getTaxCode()))).append("</CodiceFiscale>\n");
        }
        x.append("        <Anagrafica>\n");
        if (notBlank(tenant.getLegalName())) {
            x.append("          <Denominazione>").append(esc(tenant.getLegalName())).append("</Denominazione>\n");
        } else {
            String[] nc = splitNomeCognome(tenant.getDisplayName());
            x.append("          <Nome>").append(esc(nc[0])).append("</Nome>\n");
            x.append("          <Cognome>").append(esc(nc[1])).append("</Cognome>\n");
        }
        x.append("        </Anagrafica>\n");
        x.append("        <RegimeFiscale>").append(esc(regime)).append("</RegimeFiscale>\n");
        x.append("      </DatiAnagrafici>\n");
        x.append("      <Sede>\n");
        x.append("        <Indirizzo>").append(esc(sedeTenant.indirizzo)).append("</Indirizzo>\n");
        x.append("        <CAP>").append(esc(sedeTenant.cap)).append("</CAP>\n");
        x.append("        <Comune>").append(esc(sedeTenant.comune)).append("</Comune>\n");
        if (notBlank(sedeTenant.provincia)) {
            x.append("        <Provincia>").append(esc(sedeTenant.provincia)).append("</Provincia>\n");
        }
        x.append("        <Nazione>IT</Nazione>\n");
        x.append("      </Sede>\n");
        x.append("    </CedentePrestatore>\n");

        // ── CESSIONARIO/COMMITTENTE = ospite ──
        String cfOspite = booking.getGuestTaxCode() != null ? booking.getGuestTaxCode().trim() : "";
        x.append("    <CessionarioCommittente>\n");
        x.append("      <DatiAnagrafici>\n");
        if (ospiteItaliano) {
            if (notBlank(cfOspite)) {
                x.append("        <CodiceFiscale>").append(esc(soloAlfanumerico(cfOspite))).append("</CodiceFiscale>\n");
            }
        } else if (notBlank(cfOspite) && !cfOspite.toUpperCase(Locale.ITALY).startsWith("EST")) {
            // Straniero con identificativo fiscale reale. Il paese non è memorizzato → XX.
            x.append("        <IdFiscaleIVA>\n");
            x.append("          <IdPaese>XX</IdPaese>\n");
            x.append("          <IdCodice>").append(esc(soloAlfanumerico(cfOspite))).append("</IdCodice>\n");
            x.append("        </IdFiscaleIVA>\n");
        }
        // CF fittizio EST...: nessun identificativo fiscale (ospite straniero senza CF)
        x.append("        <Anagrafica>\n");
        String nomeOspite = notBlank(booking.getGuestName()) ? booking.getGuestName().trim() : "Cliente";
        if (nomeOspite.contains(" ")) {
            String[] nc = splitNomeCognome(nomeOspite);
            x.append("          <Nome>").append(esc(nc[0])).append("</Nome>\n");
            x.append("          <Cognome>").append(esc(nc[1])).append("</Cognome>\n");
        } else {
            x.append("          <Denominazione>").append(esc(nomeOspite)).append("</Denominazione>\n");
        }
        x.append("        </Anagrafica>\n");
        x.append("      </DatiAnagrafici>\n");
        // Sede obbligatoria: l'indirizzo dell'ospite non è tra i dati raccolti,
        // si usa il comune dell'immobile con indirizzo/CAP generici.
        String comuneOspite = property != null && notBlank(property.getCity()) ? property.getCity() : "N.D.";
        x.append("      <Sede>\n");
        x.append("        <Indirizzo>Via generica 1</Indirizzo>\n");
        x.append("        <CAP>").append(CAP_DEFAULT).append("</CAP>\n");
        x.append("        <Comune>").append(esc(comuneOspite)).append("</Comune>\n");
        if (ospiteItaliano) {
            x.append("        <Provincia>").append(esc(provinciaDaComune(comuneOspite))).append("</Provincia>\n");
        }
        x.append("        <Nazione>").append(ospiteItaliano ? "IT" : "XX").append("</Nazione>\n");
        x.append("      </Sede>\n");
        x.append("    </CessionarioCommittente>\n");
        x.append("  </FatturaElettronicaHeader>\n");

        // ── BODY ──
        x.append("  <FatturaElettronicaBody>\n");
        x.append("    <DatiGenerali>\n");
        x.append("      <DatiGeneraliDocumento>\n");
        x.append("        <TipoDocumento>TD01</TipoDocumento>\n");
        x.append("        <Divisa>EUR</Divisa>\n");
        x.append("        <Data>").append(doc.getIssueDate().format(DATA_SDI)).append("</Data>\n");
        x.append("        <Numero>").append(esc(doc.getDocumentNumber())).append("</Numero>\n");
        if (nz(doc.getBolloAmount()).compareTo(BigDecimal.ZERO) > 0) {
            x.append("        <DatiBollo>\n");
            x.append("          <BolloVirtuale>SI</BolloVirtuale>\n");
            x.append("          <ImportoBollo>").append(imp(doc.getBolloAmount())).append("</ImportoBollo>\n");
            x.append("        </DatiBollo>\n");
        }
        x.append("        <ImportoTotaleDocumento>").append(imp(doc.getTotalAmount()))
                .append("</ImportoTotaleDocumento>\n");
        x.append("      </DatiGeneraliDocumento>\n");
        x.append("    </DatiGenerali>\n");

        x.append("    <DatiBeniServizi>\n");
        int n = 1;
        for (Linea l : linee) {
            x.append("      <DettaglioLinee>\n");
            x.append("        <NumeroLinea>").append(n++).append("</NumeroLinea>\n");
            x.append("        <Descrizione>").append(esc(l.descrizione)).append("</Descrizione>\n");
            x.append("        <Quantita>1.00</Quantita>\n");
            x.append("        <PrezzoUnitario>").append(imp(l.imponibile)).append("</PrezzoUnitario>\n");
            x.append("        <PrezzoTotale>").append(imp(l.imponibile)).append("</PrezzoTotale>\n");
            x.append("        <AliquotaIVA>").append(imp(aliquota)).append("</AliquotaIVA>\n");
            if (aliquota.compareTo(BigDecimal.ZERO) == 0) {
                x.append("        <Natura>").append(esc(natura)).append("</Natura>\n");
            }
            x.append("      </DettaglioLinee>\n");
        }
        x.append("      <DatiRiepilogo>\n");
        x.append("        <AliquotaIVA>").append(imp(aliquota)).append("</AliquotaIVA>\n");
        if (aliquota.compareTo(BigDecimal.ZERO) == 0) {
            x.append("        <Natura>").append(esc(natura)).append("</Natura>\n");
        }
        x.append("        <ImponibileImporto>").append(imp(totImponibile)).append("</ImponibileImporto>\n");
        x.append("        <Imposta>").append(imp(totImposta)).append("</Imposta>\n");
        x.append("        <EsigibilitaIVA>I</EsigibilitaIVA>\n");
        x.append("      </DatiRiepilogo>\n");
        x.append("    </DatiBeniServizi>\n");

        x.append("    <DatiPagamento>\n");
        x.append("      <CondizioniPagamento>TP02</CondizioniPagamento>\n");
        x.append("      <DettaglioPagamento>\n");
        x.append("        <ModalitaPagamento>MP05</ModalitaPagamento>\n");
        x.append("        <ImportoPagamento>").append(imp(doc.getTotalAmount())).append("</ImportoPagamento>\n");
        x.append("      </DettaglioPagamento>\n");
        x.append("    </DatiPagamento>\n");
        x.append("  </FatturaElettronicaBody>\n");
        x.append("</FatturaElettronica>\n");
        return x.toString();
    }

    /** Riga di dettaglio con imponibile scorporato dal lordo. */
    private record Linea(String descrizione, BigDecimal imponibile, BigDecimal imposta) { }

    /**
     * Righe della fattura PM. Non esiste una tabella fiscal_document_line: le voci sono
     * ricavate dal booking con la stessa logica di DocumentGenerationService e del PDF —
     * i valori dei servizi sono LORDI e l'IVA va SCORPORATA.
     */
    private List<Linea> buildLinee(FiscalDocument doc, Booking booking, BigDecimal aliquota) {
        List<Linea> linee = new ArrayList<>();
        aggiungiLinea(linee, "Riaddebito commissione OTA", nz(booking.getOtaCommissionAmount()), aliquota);
        aggiungiLinea(linee, "Riaddebito pulizia finale", nz(booking.getCleaningAmount()), aliquota);
        aggiungiLinea(linee, "Provvigione gestione immobiliare", nz(booking.getPmFeeAmount()), aliquota);
        if (linee.isEmpty()) {
            // Nessun importo dettagliabile: riga sintetica sull'imponibile del documento.
            BigDecimal imponibile = nz(doc.getImponibile()).setScale(2, RoundingMode.HALF_UP);
            BigDecimal imposta = nz(doc.getVatAmount()).setScale(2, RoundingMode.HALF_UP);
            linee.add(new Linea("Servizi di gestione locazione turistica breve", imponibile, imposta));
        }
        return linee;
    }

    private void aggiungiLinea(List<Linea> linee, String descrizione, BigDecimal lordo, BigDecimal aliquota) {
        if (lordo.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        BigDecimal imponibile;
        BigDecimal imposta;
        if (aliquota.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal divisore = BigDecimal.ONE.add(aliquota.divide(CENTO, 4, RoundingMode.HALF_UP));
            imponibile = lordo.divide(divisore, 2, RoundingMode.HALF_UP);
            imposta = lordo.subtract(imponibile).setScale(2, RoundingMode.HALF_UP);
        } else {
            imponibile = lordo.setScale(2, RoundingMode.HALF_UP);
            imposta = BigDecimal.ZERO.setScale(2);
        }
        linee.add(new Linea(descrizione, imponibile, imposta));
    }

    // ────────────────────────────── helper ──────────────────────────────

    /**
     * Ospite considerato italiano quando guest_country è 'Italia' oppure non è valorizzato:
     * su molte prenotazioni il campo è vuoto pur essendoci un CF italiano valido, e trattarle
     * come estere produrrebbe CodiceDestinatario XXXXXXX e Nazione XX su una fattura interna.
     */
    private boolean isOspiteItaliano(Booking booking) {
        String paese = booking.getGuestCountry();
        return paese == null || paese.isBlank() || "Italia".equalsIgnoreCase(paese.trim());
    }

    /** Identificativo per il nome file e IdTrasmittente: partita IVA, altrimenti codice fiscale. */
    private String identificativoFiscale(Tenant tenant) {
        if (notBlank(tenant.getVatNumber())) {
            return soloAlfanumerico(tenant.getVatNumber());
        }
        return notBlank(tenant.getTaxCode()) ? soloAlfanumerico(tenant.getTaxCode()) : "";
    }

    private String provinciaDaComune(String comune) {
        if (!notBlank(comune)) {
            return PROVINCIA_DEFAULT;
        }
        return comuneItalianoDAO.findByNomeEsatto(comune.trim())
                .map(ComuneItaliano::getSiglaProvincia)
                .filter(this::notBlank)
                .orElse(PROVINCIA_DEFAULT);
    }

    /**
     * Sede del cedente dai campi dedicati cap/comune/provincia (migration 011).
     * Se non sono valorizzati (tenant creati prima della migration) si ripiega sul
     * parsing di legal_address, mantenendo la retrocompatibilità.
     */
    private Indirizzo sedeTenant(Tenant tenant) {
        boolean campiPresenti = notBlank(tenant.getCap()) && notBlank(tenant.getComune());
        if (!campiPresenti) {
            log.warn("SdiXmlService - sede legale non scomposta per tenantId={}: fallback sul parsing di legal_address",
                    tenant.getId());
            return parseIndirizzo(tenant.getLegalAddress());
        }
        Indirizzo out = new Indirizzo();
        // legal_address contiene solo via e civico: CAP, comune e provincia sono campi dedicati.
        out.indirizzo = notBlank(tenant.getLegalAddress()) ? tenant.getLegalAddress().trim() : "N.D.";
        out.cap = tenant.getCap().trim();
        out.comune = tenant.getComune().trim();
        out.provincia = notBlank(tenant.getProvincia())
                ? tenant.getProvincia().trim().toUpperCase(Locale.ITALY)
                : provinciaDaComune(out.comune);
        return out;
    }

    /** Fallback: il tenant ha solo legal_address come stringa unica, va scomposto. */
    private Indirizzo parseIndirizzo(String legalAddress) {
        Indirizzo out = new Indirizzo();
        out.indirizzo = "N.D.";
        out.cap = CAP_DEFAULT;
        out.comune = "N.D.";
        out.provincia = null;
        if (!notBlank(legalAddress)) {
            return out;
        }
        String[] parti = legalAddress.split(",", 2);
        out.indirizzo = parti[0].trim();
        if (parti.length < 2) {
            return out;
        }
        Matcher m = LOCALITA.matcher(parti[1].trim());
        if (m.matches()) {
            out.cap = m.group(1);
            out.comune = m.group(2).trim();
            out.provincia = m.group(3) != null
                    ? m.group(3).toUpperCase(Locale.ITALY)
                    : provinciaDaComune(out.comune);
        } else {
            out.comune = parti[1].trim();
            out.provincia = provinciaDaComune(out.comune);
        }
        return out;
    }

    private static class Indirizzo {
        String indirizzo;
        String cap;
        String comune;
        String provincia;
    }

    /** Split su primo spazio: prima parola = nome, resto = cognome. */
    private String[] splitNomeCognome(String nominativo) {
        String v = notBlank(nominativo) ? nominativo.trim() : "N.D.";
        int i = v.indexOf(' ');
        if (i <= 0) {
            return new String[]{v, v};
        }
        return new String[]{v.substring(0, i).trim(), v.substring(i + 1).trim()};
    }

    /** Importi SDI: punto come separatore decimale, 2 decimali. */
    private String imp(BigDecimal v) {
        return String.format(Locale.US, "%.2f", nz(v).setScale(2, RoundingMode.HALF_UP));
    }

    private String soloAlfanumerico(String v) {
        return v == null ? "" : v.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ITALY);
    }

    private String troncaErrore(String msg) {
        if (msg == null) return null;
        return msg.length() > 500 ? msg.substring(0, 500) : msg;
    }

    private boolean notBlank(String v) {
        return v != null && !v.isBlank();
    }

    private BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private String esc(String v) {
        if (v == null) {
            return "";
        }
        return v.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
