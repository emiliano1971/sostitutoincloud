package it.gavia.sostitutoincloud.service;

import it.gavia.sostitutoincloud.dao.FiscalDocumentDAO;
import it.gavia.sostitutoincloud.dto.sdi.SdiElaborazioneResultDTO;
import it.gavia.sostitutoincloud.model.FiscalDocument;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Elaborazione delle risposte SDI depositate in incoming/ dal tunnel.
 *
 * Per ogni risposta: aggiorna lo stato del documento fiscale, archivia la ricevuta in
 * elaborati/{piva}/{progressivo}/, rimuove la fattura da outgoing/ (il ciclo è chiuso)
 * e cancella la risposta da incoming/.
 *
 * La firma digitale NON viene verificata: è già validata dal tunnel SDI a monte.
 */
@Service
@Log4j2
public class SdiRispostaService {

    /** Nome file risposta SDI: IT{PIVA}_{PROGRESSIVO}_{ESITO}_{N}.xml */
    private static final Pattern NOME_RISPOSTA =
            Pattern.compile("^IT([A-Za-z0-9]+)_([A-Za-z0-9]+)_(RC|NS|MC|MT)_([A-Za-z0-9]+)\\.xml$",
                    Pattern.CASE_INSENSITIVE);

    private static final String ESITO_CONSEGNA = "RC";
    private static final String ESITO_SCARTO = "NS";
    private static final String ESITO_MANCATA_CONSEGNA = "MC";
    private static final String ESITO_METADATI = "MT";

    @Value("${app.storage.sdi-incoming-path}")
    private String sdiIncomingPath;

    @Value("${app.storage.sdi-outgoing-path}")
    private String sdiOutgoingPath;

    @Value("${app.storage.sdi-elaborati-path}")
    private String sdiElaboratiPath;

    private final FiscalDocumentDAO fiscalDocumentDAO;
    private final AuditService auditService;

    public SdiRispostaService(FiscalDocumentDAO fiscalDocumentDAO, AuditService auditService) {
        this.fiscalDocumentDAO = fiscalDocumentDAO;
        this.auditService = auditService;
    }

    /**
     * @param tenantPiva identificativo fiscale del tenant (P.IVA o, in mancanza, codice
     *                   fiscale — lo stesso usato nel nome file da SdiXmlService): elabora
     *                   solo le risposte con prefisso IT{tenantPiva}_ e ignora in silenzio
     *                   quelle degli altri tenant. Con null elabora tutto (super_admin).
     */
    public SdiElaborazioneResultDTO elaboraRisposte(String tenantPiva) {
        // Stessa normalizzazione applicata da SdiXmlService al nome file.
        String prefisso = tenantPiva != null && !tenantPiva.isBlank()
                ? "IT" + tenantPiva.replaceAll("[^A-Za-z0-9]", "").toUpperCase(Locale.ITALY) + "_"
                : null;
        int elaborati = 0;
        int accettati = 0;
        int scartati = 0;
        int metadati = 0;
        int errori = 0;
        List<String> dettagli = new ArrayList<>();

        Path incoming = Path.of(sdiIncomingPath);
        if (!Files.isDirectory(incoming)) {
            log.warn("SdiRispostaService.elaboraRisposte() - cartella incoming inesistente: {}", incoming);
            return build(0, 0, 0, 0, 0, List.of("Cartella incoming non trovata: " + incoming));
        }

        List<Path> files;
        try (Stream<Path> stream = Files.list(incoming)) {
            files = stream
                    .filter(p -> p.toString().toLowerCase(Locale.ITALY).endsWith(".xml"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
        } catch (Exception e) {
            log.error("SdiRispostaService.elaboraRisposte() - errore lettura incoming", e);
            return build(0, 0, 0, 0, 1, List.of("Errore lettura cartella incoming: " + e.getMessage()));
        }

        for (Path file : files) {
            String nomeRisposta = file.getFileName().toString();

            // Risposte di altri tenant: ignorate senza contarle né segnalarle.
            if (prefisso != null && !nomeRisposta.toUpperCase(Locale.ITALY).startsWith(prefisso)) {
                log.debug("SdiRispostaService - risposta di altro tenant, ignorata: {}", nomeRisposta);
                continue;
            }

            Matcher m = NOME_RISPOSTA.matcher(nomeRisposta);
            if (!m.matches()) {
                log.warn("SdiRispostaService - nome file non riconosciuto, ignorato: {}", nomeRisposta);
                dettagli.add(nomeRisposta + ": nome file non conforme a IT{PIVA}_{PROG}_{ESITO}_{N}.xml, ignorato");
                errori++;
                continue;
            }
            String esito = m.group(3).toUpperCase(Locale.ITALY);

            try {
                Document doc = parse(file);

                // Il riferimento autorevole è il NomeFile della fattura contenuto nella
                // risposta: il nome del file di risposta serve solo per l'esito.
                String nomeFileFattura = testo(doc, "NomeFile");
                if (nomeFileFattura == null || !nomeFileFattura.contains("_")) {
                    log.warn("SdiRispostaService - NomeFile assente o non valido in {}", nomeRisposta);
                    dettagli.add(nomeRisposta + ": elemento NomeFile assente o non valido");
                    errori++;
                    continue;
                }
                String base = nomeFileFattura.replace(".xml", "").replace(".XML", "");
                String[] parti = base.split("_");
                String progFattura = parti.length > 1 ? parti[1] : null;
                String pivaFattura = parti[0].length() > 2 ? parti[0].substring(2) : parti[0];

                Optional<FiscalDocument> opt = progFattura != null
                        ? fiscalDocumentDAO.findBySdiProgressivo(progFattura)
                        : Optional.empty();
                if (opt.isEmpty()) {
                    log.warn("SdiRispostaService - fiscal_document non trovato per progressivo {}", progFattura);
                    dettagli.add(nomeRisposta + ": nessun documento con progressivo " + progFattura);
                    errori++;
                    continue;
                }
                FiscalDocument fd = opt.get();

                switch (esito) {
                    case ESITO_CONSEGNA -> {
                        String dataConsegna = testo(doc, "DataOraConsegna");
                        fiscalDocumentDAO.updateSdiAccepted(fd.getId(), dataConsegna);
                        accettati++;
                        log.info("SdiRispostaService - RC: documento {} accettato (consegna {})",
                                fd.getDocumentNumber(), dataConsegna);
                    }
                    case ESITO_SCARTO -> {
                        String msg = estraiErrori(doc);
                        fiscalDocumentDAO.updateSdiRejected(fd.getId(), tronca(msg));
                        scartati++;
                        dettagli.add(fd.getDocumentNumber() + " scartata: " + msg);
                        log.warn("SdiRispostaService - NS: documento {} scartato: {}",
                                fd.getDocumentNumber(), msg);
                    }
                    case ESITO_MANCATA_CONSEGNA -> {
                        fiscalDocumentDAO.updateSdiError(fd.getId(), "Mancata consegna SDI");
                        errori++;
                        dettagli.add(fd.getDocumentNumber() + ": mancata consegna SDI");
                        log.warn("SdiRispostaService - MC: mancata consegna per {}", fd.getDocumentNumber());
                    }
                    case ESITO_METADATI -> {
                        metadati++;
                        log.info("SdiRispostaService - MT ricevuto per {}", fd.getDocumentNumber());
                    }
                    default -> { /* il regex ammette solo i quattro esiti */ }
                }

                // L'anno viene dalla data di emissione del documento, la stessa usata da
                // SdiXmlService per l'archivio: risposta e fattura finiscono nella stessa cartella.
                String anno = fd.getIssueDate() != null
                        ? String.valueOf(fd.getIssueDate().getYear())
                        : null;
                archivia(file, nomeFileFattura, pivaFattura, anno, progFattura, esito);

                auditService.log("sdi.risposta." + esito.toLowerCase(Locale.ITALY),
                        "FiscalDocument", fd.getId(),
                        "Ricevuto esito SDI " + esito + " per progressivo " + progFattura);
                elaborati++;
            } catch (Exception e) {
                log.error("SdiRispostaService - errore elaborazione {}", nomeRisposta, e);
                dettagli.add(nomeRisposta + ": errore elaborazione — " + e.getMessage());
                errori++;
            }
        }

        log.info("SdiRispostaService.elaboraRisposte() - elaborati={} accettati={} scartati={} errori={}",
                elaborati, accettati, scartati, errori);
        return build(elaborati, accettati, scartati, metadati, errori, dettagli);
    }

    /**
     * Archivia la risposta in elaborati/{piva}/{anno}/{progressivo}/ e chiude il ciclo:
     * la fattura esce da outgoing/ (tranne per i metadati MT, che non concludono l'invio)
     * e la risposta viene rimossa da incoming/.
     */
    private void archivia(Path risposta, String nomeFileFattura, String piva, String anno,
                          String progressivo, String esito) throws java.io.IOException {
        Path elaboratiDir = anno != null
                ? Path.of(sdiElaboratiPath, piva, anno, progressivo)
                : Path.of(sdiElaboratiPath, piva, progressivo);
        Files.createDirectories(elaboratiDir);
        Files.copy(risposta, elaboratiDir.resolve(risposta.getFileName()), StandardCopyOption.REPLACE_EXISTING);

        if (!ESITO_METADATI.equals(esito)) {
            Path outgoingFile = Path.of(sdiOutgoingPath, nomeFileFattura);
            if (Files.exists(outgoingFile)) {
                Files.delete(outgoingFile);
                log.debug("SdiRispostaService - rimossa da outgoing: {}", outgoingFile);
            }
        }
        Files.delete(risposta);
    }

    // ────────────────────────────── parsing XML ──────────────────────────────

    private Document parse(Path file) throws Exception {
        String xml = Files.readString(file, StandardCharsets.UTF_8);
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        // Nessuna entità esterna: i file arrivano dall'esterno, va escluso XXE.
        dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        dbf.setExpandEntityReferences(false);
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(new InputSource(new StringReader(xml)));
        doc.getDocumentElement().normalize();
        return doc;
    }

    /**
     * Primo elemento con questo nome locale. Confronta anche i nomi con prefisso
     * (es. ns3:NomeFile): le ricevute SDI reali usano namespace con prefisso.
     */
    private String testo(Document doc, String localName) {
        List<Element> found = elementi(doc.getDocumentElement(), localName);
        return found.isEmpty() ? null : found.get(0).getTextContent().trim();
    }

    private List<Element> elementi(Element root, String localName) {
        List<Element> out = new ArrayList<>();
        NodeList all = root.getElementsByTagName("*");
        if (matchNome(root, localName)) {
            out.add(root);
        }
        for (int i = 0; i < all.getLength(); i++) {
            Node n = all.item(i);
            if (n instanceof Element el && matchNome(el, localName)) {
                out.add(el);
            }
        }
        return out;
    }

    private boolean matchNome(Element el, String localName) {
        String ln = el.getLocalName() != null ? el.getLocalName() : el.getNodeName();
        return ln.equals(localName) || el.getNodeName().endsWith(":" + localName);
    }

    /** Concatena gli errori di ListaErrori nel formato "[codice] descrizione; ". */
    private String estraiErrori(Document doc) {
        StringBuilder msg = new StringBuilder();
        for (Element errore : elementi(doc.getDocumentElement(), "Errore")) {
            List<Element> codici = elementi(errore, "Codice");
            List<Element> descrizioni = elementi(errore, "Descrizione");
            String codice = codici.isEmpty() ? "?" : codici.get(0).getTextContent().trim();
            String descrizione = descrizioni.isEmpty() ? "" : descrizioni.get(0).getTextContent().trim();
            msg.append("[").append(codice).append("] ").append(descrizione).append("; ");
        }
        return msg.length() > 0 ? msg.toString().trim() : "Scartato dallo SDI senza dettaglio errori";
    }

    private String tronca(String v) {
        if (v == null) return null;
        return v.length() > 500 ? v.substring(0, 500) : v;
    }

    private SdiElaborazioneResultDTO build(int elaborati, int accettati, int scartati,
                                           int metadati, int errori, List<String> dettagli) {
        return SdiElaborazioneResultDTO.builder()
                .elaborati(elaborati)
                .accettati(accettati)
                .scartati(scartati)
                .metadati(metadati)
                .errori(errori)
                .dettagli(dettagli)
                .build();
    }
}
