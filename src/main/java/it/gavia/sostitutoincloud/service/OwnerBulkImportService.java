package it.gavia.sostitutoincloud.service;

import it.gavia.sostitutoincloud.dao.CanaleOtaDAO;
import it.gavia.sostitutoincloud.dao.OwnerProfileDAO;
import it.gavia.sostitutoincloud.dao.PropertyContractRuleDAO;
import it.gavia.sostitutoincloud.dao.PropertyDAO;
import it.gavia.sostitutoincloud.dao.RegimeFiscaleDAO;
import it.gavia.sostitutoincloud.dao.TipoImmobileDAO;
import it.gavia.sostitutoincloud.dto.owner.OwnerBulkImportErrore;
import it.gavia.sostitutoincloud.dto.owner.OwnerBulkImportPreviewResult;
import it.gavia.sostitutoincloud.dto.owner.OwnerBulkImportResult;
import it.gavia.sostitutoincloud.dto.owner.OwnerBulkImportRigaPreview;
import it.gavia.sostitutoincloud.model.OwnerProfile;
import it.gavia.sostitutoincloud.model.Property;
import it.gavia.sostitutoincloud.model.PropertyContractRule;
import it.gavia.sostitutoincloud.model.TipoImmobile;
import it.gavia.sostitutoincloud.util.ExcelCellUtils;
import lombok.extern.log4j.Log4j2;
import org.apache.poi.poifs.filesystem.FileMagic;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Importazione massiva di proprietari e immobili dal template Excel
 * docs/import/template_importazione_proprietari.xlsx (generato da TemplateProprietariGenerator).
 *
 * Ogni riga = un immobile con i dati del suo proprietario. Deduplicazione:
 * - proprietario per codice fiscale nel tenant: se esiste si riusa senza sovrascriverlo;
 * - immobile per nome + proprietario: se esiste la riga viene saltata.
 * Ogni riga gira in una transazione propria: un errore annulla solo quella riga
 * (niente immobili creati senza le loro regole) e l'import prosegue.
 * Stesso CF con nome/cognome diversi (dal proprietario a DB o da una riga precedente del
 * file) è un errore della riga: l'import non sovrascrive mai i dati del proprietario.
 *
 * preview() fa le stesse verifiche senza scrivere nulla; importa() riceve le righe scelte.
 */
@Service
@Log4j2
public class OwnerBulkImportService {

    // Colonne del template (foglio "Importazione"), 0-based: A..P
    private static final int COL_COGNOME = 0;
    private static final int COL_NOME = 1;
    private static final int COL_CF = 2;
    private static final int COL_NOME_IMMOBILE = 3;
    private static final int COL_CITTA = 4;
    private static final int COL_IBAN = 5;
    private static final int COL_REGIME = 6;
    private static final int COL_EMAIL = 7;
    private static final int COL_TELEFONO = 8;
    private static final int COL_INDIRIZZO = 9;
    private static final int COL_PRIMO_IMMOBILE = 10;
    private static final int COL_COMMISSIONE_OTA = 11;
    private static final int COL_PULIZIE = 12;
    private static final int COL_CAMBIO_BIANCHERIA = 13;
    private static final int COL_COMMISSIONE_PM = 14;
    private static final int COL_TIPO_PM = 15;
    private static final int NUM_COLONNE = 16;

    // Righe esaminate per trovare l'intestazione (titolo e legenda la precedono)
    private static final int MAX_RIGHE_RICERCA_INTESTAZIONE = 20;

    private static final String REGIME_DEFAULT = "cedolare_secca";
    private static final Set<String> REGIMI_VALIDI = Set.of("cedolare_secca", "ordinario", "iva_10");

    private final OwnerProfileDAO ownerProfileDAO;
    private final PropertyDAO propertyDAO;
    private final PropertyContractRuleDAO propertyContractRuleDAO;
    private final TenantSettingsService tenantSettingsService;
    private final CanaleOtaDAO canaleOtaDAO;
    private final RegimeFiscaleDAO regimeFiscaleDAO;
    private final TipoImmobileDAO tipoImmobileDAO;
    private final AuditService auditService;
    private final TransactionTemplate transactionTemplate;

    public OwnerBulkImportService(OwnerProfileDAO ownerProfileDAO,
                                  PropertyDAO propertyDAO,
                                  PropertyContractRuleDAO propertyContractRuleDAO,
                                  TenantSettingsService tenantSettingsService,
                                  CanaleOtaDAO canaleOtaDAO,
                                  RegimeFiscaleDAO regimeFiscaleDAO,
                                  TipoImmobileDAO tipoImmobileDAO,
                                  AuditService auditService,
                                  PlatformTransactionManager transactionManager) {
        this.ownerProfileDAO = ownerProfileDAO;
        this.propertyDAO = propertyDAO;
        this.propertyContractRuleDAO = propertyContractRuleDAO;
        this.tenantSettingsService = tenantSettingsService;
        this.canaleOtaDAO = canaleOtaDAO;
        this.regimeFiscaleDAO = regimeFiscaleDAO;
        this.tipoImmobileDAO = tipoImmobileDAO;
        this.auditService = auditService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    /** Dati di una riga del file, già normalizzati e validati. */
    private record RigaImport(
            String cognome, String nome, String codiceFiscale, String nomeImmobile, String citta,
            String iban, String regimeFiscale, String email, String telefono, String indirizzo,
            Boolean primoImmobile,
            BigDecimal commissioneOtaPct, BigDecimal pulizieImporto,
            BigDecimal cambioBiancheriaPersona, BigDecimal commissionePmPct, String commissionePmCalcMode) {
    }

    /** Esito di una riga importata senza errori. */
    private record EsitoRiga(boolean proprietarioCreato, boolean immobileCreato) {
    }

    /** Riga non vuota del foglio con il suo numero Excel (1-based). */
    private record RigaFile(int numeroRiga, String[] valori) {
    }

    /** Prima riga del file (valida) che ha introdotto un CF non presente a DB. */
    private record ProprietarioNelFile(int numeroRiga, String cognome, String nome) {
    }

    // ── preview ───────────────────────────────────────────────────────────────

    /**
     * Analizza il file senza scrivere a DB: per ogni riga lo stato che avrà all'import.
     * Simula anche gli effetti delle righe precedenti del file (proprietario creato da una
     * riga sopra, stesso immobile ripetuto).
     *
     * @throws IllegalArgumentException file vuoto, non Excel o senza riga di intestazione
     * @throws IOException              errore di lettura del file
     */
    public OwnerBulkImportPreviewResult preview(Integer tenantId, MultipartFile file) throws IOException {
        log.info("OwnerBulkImportService.preview() - tenant={} file={}", tenantId, file != null ? file.getOriginalFilename() : null);
        OwnerBulkImportPreviewResult result = new OwnerBulkImportPreviewResult();
        Map<String, Optional<OwnerProfile>> ownersDb = new HashMap<>();
        Map<String, ProprietarioNelFile> ownersFile = new HashMap<>();
        Set<String> immobiliFile = new HashSet<>();

        for (RigaFile rf : leggiFile(file)) {
            String[] v = rf.valori();
            OwnerBulkImportRigaPreview.OwnerBulkImportRigaPreviewBuilder p = OwnerBulkImportRigaPreview.builder()
                    .numeroRiga(rf.numeroRiga())
                    .cognome(v[COL_COGNOME])
                    .nome(v[COL_NOME])
                    .codFisc(v[COL_CF].toUpperCase(Locale.ROOT))
                    .nomeImmobile(v[COL_NOME_IMMOBILE])
                    .citta(v[COL_CITTA]);
            String stato;
            String messaggio;
            try {
                RigaImport riga = parseRiga(v);
                String cf = riga.codiceFiscale();
                String chiaveImmobile = cf + "|" + normalizzaNome(riga.nomeImmobile());
                OwnerProfile ownerDb = ownersDb
                        .computeIfAbsent(cf, k -> ownerProfileDAO.findByTaxCodeAndTenant(k, tenantId))
                        .orElse(null);
                ProprietarioNelFile ownerFile = ownersFile.get(cf);

                if (ownerDb != null) {
                    verificaCoerenza(riga, ownerDb.getLastName(), ownerDb.getFirstName(), "proprietario già registrato");
                    boolean giaADb = propertyDAO.findByNameAndOwner(riga.nomeImmobile(), ownerDb.getId(), tenantId).isPresent();
                    if (giaADb || immobiliFile.contains(chiaveImmobile)) {
                        stato = "duplicato_immobile";
                        messaggio = giaADb
                                ? "Immobile già presente per questo proprietario"
                                : "Immobile ripetuto nel file per lo stesso proprietario";
                    } else {
                        stato = "ok";
                        messaggio = "Proprietario esistente — verrà associato";
                    }
                } else if (ownerFile != null) {
                    verificaCoerenza(riga, ownerFile.cognome(), ownerFile.nome(), "riga " + ownerFile.numeroRiga());
                    if (immobiliFile.contains(chiaveImmobile)) {
                        stato = "duplicato_immobile";
                        messaggio = "Immobile ripetuto nel file per lo stesso proprietario";
                    } else {
                        stato = "ok";
                        messaggio = "Stesso proprietario della riga " + ownerFile.numeroRiga() + " — verrà associato";
                    }
                } else {
                    ownersFile.put(cf, new ProprietarioNelFile(rf.numeroRiga(), riga.cognome(), riga.nome()));
                    stato = "ok";
                    messaggio = "Nuovo proprietario";
                }
                if ("ok".equals(stato)) {
                    immobiliFile.add(chiaveImmobile);
                }
            } catch (IllegalArgumentException e) {
                stato = "errore";
                messaggio = e.getMessage();
            }
            boolean ok = "ok".equals(stato);
            result.getRighe().add(p.stato(stato).messaggioStato(messaggio).selezionabile(ok).selezionato(ok).build());
            switch (stato) {
                case "ok" -> result.setRigheOk(result.getRigheOk() + 1);
                case "errore" -> result.setRigheErrore(result.getRigheErrore() + 1);
                default -> result.setRigheDuplicato(result.getRigheDuplicato() + 1);
            }
        }
        log.info("OwnerBulkImportService.preview() - tenant={} righe={} ok={} duplicati={} errori={}",
                tenantId, result.getRighe().size(), result.getRigheOk(), result.getRigheDuplicato(), result.getRigheErrore());
        return result;
    }

    /**
     * @throws IllegalArgumentException file vuoto, non Excel o senza riga di intestazione
     * @throws IOException              errore di lettura del file
     */
    /**
     * @param righeSelezionate numeri di riga Excel da importare; null o vuoto = tutte le righe
     */
    public OwnerBulkImportResult importa(Integer tenantId, Integer utenteId, MultipartFile file,
                                         Set<Integer> righeSelezionate) throws IOException {
        boolean tutte = righeSelezionate == null || righeSelezionate.isEmpty();
        log.info("OwnerBulkImportService.importa() - tenant={} utente={} file={} righe={}",
                tenantId, utenteId, file != null ? file.getOriginalFilename() : null,
                tutte ? "tutte" : righeSelezionate);

        OwnerBulkImportResult result = new OwnerBulkImportResult();
        Integer canaleOtaDefaultId = tenantSettingsService.getCanaleOtaDefaultId(tenantId);
        if (canaleOtaDefaultId != null && canaleOtaDAO.findById(canaleOtaDefaultId).isEmpty()) {
            canaleOtaDefaultId = null;
        }
        boolean warnOtaEmesso = false;

        for (RigaFile rf : leggiFile(file)) {
            int numeroRiga = rf.numeroRiga();
            if (!tutte && !righeSelezionate.contains(numeroRiga)) {
                continue;
            }
            String[] valori = rf.valori();
            String descrizione = descrizioneRiga(valori);
            result.setRigheProcessate(result.getRigheProcessate() + 1);

            RigaImport riga;
            try {
                riga = parseRiga(valori);
            } catch (IllegalArgumentException e) {
                aggiungiErrore(result, numeroRiga, descrizione, e.getMessage());
                continue;
            }

            if (riga.commissioneOtaPct() != null && canaleOtaDefaultId == null && !warnOtaEmesso) {
                log.warn("OwnerBulkImportService - Canale OTA default non configurato per tenant {}: "
                        + "le regole commissione OTA non vengono create", tenantId);
                warnOtaEmesso = true;
            }

            final Integer canaleOta = canaleOtaDefaultId;
            try {
                EsitoRiga esito = transactionTemplate.execute(status -> importaRiga(tenantId, utenteId, riga, canaleOta));
                if (esito.proprietarioCreato()) {
                    result.setProprietariCreati(result.getProprietariCreati() + 1);
                } else {
                    result.setProprietariEsistenti(result.getProprietariEsistenti() + 1);
                }
                if (esito.immobileCreato()) {
                    result.setImmobiliCreati(result.getImmobiliCreati() + 1);
                } else {
                    result.setImmobiliSaltati(result.getImmobiliSaltati() + 1);
                }
            } catch (RuntimeException e) {
                log.warn("OwnerBulkImportService - riga {} annullata: {}", numeroRiga, e.getMessage());
                aggiungiErrore(result, numeroRiga, descrizione, messaggioErrore(e));
            }
        }

        log.info("OwnerBulkImportService - tenant={} processate={} owners={} immobili={} errori={}",
                tenantId, result.getRigheProcessate(), result.getProprietariCreati(),
                result.getImmobiliCreati(), result.getRigheInErrore());
        return result;
    }

    // ── import della singola riga (dentro la transazione) ──────────────────────

    private EsitoRiga importaRiga(Integer tenantId, Integer utenteId, RigaImport riga, Integer canaleOtaDefaultId) {
        boolean proprietarioCreato = false;
        OwnerProfile owner = ownerProfileDAO.findByTaxCodeAndTenant(riga.codiceFiscale(), tenantId).orElse(null);
        if (owner == null) {
            Integer regimeId = regimeFiscaleDAO.findByCodice(riga.regimeFiscale())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Regime fiscale non configurato: " + riga.regimeFiscale()))
                    .getId();
            owner = ownerProfileDAO.insert(OwnerProfile.builder()
                    .fkTenantId(tenantId)
                    .ownerType("persona_fisica")
                    .firstName(riga.nome())
                    .lastName(riga.cognome())
                    .taxCode(riga.codiceFiscale())
                    .fkRegimeFiscaleId(regimeId)
                    .email(riga.email())
                    .phone(riga.telefono())
                    .iban(riga.iban())
                    .attivo(true)
                    .build());
            auditService.log("owner.create", "OwnerProfile", owner.getId(),
                    "Creato proprietario " + owner.getFirstName() + " " + owner.getLastName() + " (import massivo)");
            proprietarioCreato = true;
        } else {
            verificaCoerenza(riga, owner.getLastName(), owner.getFirstName(), "proprietario già registrato");
        }

        if (propertyDAO.findByNameAndOwner(riga.nomeImmobile(), owner.getId(), tenantId).isPresent()) {
            log.debug("OwnerBulkImportService - immobile già presente: '{}' owner={}", riga.nomeImmobile(), owner.getId());
            return new EsitoRiga(proprietarioCreato, false);
        }

        // Primo immobile non indicato: come PropertyService.create(), primo solo se
        // l'owner non ha altri immobili attivi (evita la ritenuta primaria su due immobili)
        boolean primoImmobile = riga.primoImmobile() != null
                ? riga.primoImmobile()
                : propertyDAO.countActiveByOwner(owner.getId(), tenantId) == 0;

        Property property = propertyDAO.insert(Property.builder()
                .fkTenantId(tenantId)
                .fkOwnerId(owner.getId())
                .fkPmUserId(utenteId)
                .fkTipoImmobileId(tipoImmobileDefaultId())
                .internalCode(generaCodiceInterno(tenantId, riga.citta()))
                .displayName(riga.nomeImmobile())
                .address(riga.indirizzo() != null ? riga.indirizzo() : "")
                .city(riga.citta())
                .region("")
                .attivo(true)
                .primoImmobile(primoImmobile)
                .build());
        auditService.log("property.create", "Property", property.getId(),
                "Creato immobile " + property.getDisplayName() + " (" + property.getInternalCode() + ", import massivo)");

        creaRegole(tenantId, property.getId(), riga, canaleOtaDefaultId);
        return new EsitoRiga(proprietarioCreato, true);
    }

    private void creaRegole(Integer tenantId, Integer propertyId, RigaImport riga, Integer canaleOtaDefaultId) {
        if (positivo(riga.commissioneOtaPct()) && canaleOtaDefaultId != null) {
            inserisciRegola(tenantId, propertyId, canaleOtaDefaultId,
                    "commissione_ota", "percentuale_lordo", riga.commissioneOtaPct(), 1);
        }
        if (positivo(riga.pulizieImporto())) {
            inserisciRegola(tenantId, propertyId, null, "pulizie", "fisso", riga.pulizieImporto(), 2);
        }
        if (positivo(riga.cambioBiancheriaPersona())) {
            inserisciRegola(tenantId, propertyId, null,
                    "cambio_biancheria", "fisso_per_persona", riga.cambioBiancheriaPersona(), 3);
        }
        if (positivo(riga.commissionePmPct())) {
            inserisciRegola(tenantId, propertyId, null,
                    "commissione_pm", riga.commissionePmCalcMode(), riga.commissionePmPct(), 4);
        }
    }

    private void inserisciRegola(Integer tenantId, Integer propertyId, Integer canaleOtaId,
                                 String tipo, String calcMode, BigDecimal valore, int ordine) {
        PropertyContractRule saved = propertyContractRuleDAO.insert(PropertyContractRule.builder()
                .fkPropertyId(propertyId)
                .fkTenantId(tenantId)
                .fkCanaleOtaId(canaleOtaId)
                .tipo(tipo)
                .calcMode(calcMode)
                .valore(valore)
                .isRemainder(false)
                .ordine(ordine)
                .attivo(true)
                .build());
        auditService.log("contract.create", "PropertyContractRule", saved.getId(),
                "Aggiunta regola " + tipo + " a immobile " + propertyId + " (import massivo)");
    }

    // ── lettura e validazione della riga ───────────────────────────────────────

    private RigaImport parseRiga(String[] v) {
        String cognome = obbligatorio(v[COL_COGNOME], "Cognome proprietario mancante");
        String nome = obbligatorio(v[COL_NOME], "Nome proprietario mancante");
        String cf = obbligatorio(v[COL_CF], "Codice fiscale mancante").toUpperCase(Locale.ROOT).replace(" ", "");
        if (!cf.matches("[A-Z0-9]{16}")) {
            throw new IllegalArgumentException("Codice fiscale non valido (servono 16 caratteri alfanumerici): " + cf);
        }
        String nomeImmobile = obbligatorio(v[COL_NOME_IMMOBILE], "Nome immobile mancante");
        String citta = obbligatorio(v[COL_CITTA], "Città mancante");

        String regime = facoltativo(v[COL_REGIME]);
        regime = regime == null ? REGIME_DEFAULT : regime.toLowerCase(Locale.ROOT).replace(' ', '_');
        if (!REGIMI_VALIDI.contains(regime)) {
            throw new IllegalArgumentException(
                    "Regime fiscale non valido: " + v[COL_REGIME] + " (ammessi: cedolare_secca, ordinario, iva_10)");
        }

        String iban = facoltativo(v[COL_IBAN]);
        if (iban != null) {
            iban = iban.replace(" ", "").toUpperCase(Locale.ROOT);
        }
        String email = facoltativo(v[COL_EMAIL]);
        String telefono = facoltativo(v[COL_TELEFONO]);
        String indirizzo = facoltativo(v[COL_INDIRIZZO]);

        // Limiti delle colonne DB: errore leggibile invece della violazione SQL
        maxLen(cognome, 80, "Cognome");
        maxLen(nome, 80, "Nome");
        maxLen(nomeImmobile, 150, "Nome immobile");
        maxLen(citta, 80, "Città");
        maxLen(iban, 34, "IBAN");
        maxLen(email, 150, "Email");
        maxLen(telefono, 20, "Telefono");
        maxLen(indirizzo, 200, "Indirizzo");

        String tipoPm = facoltativo(v[COL_TIPO_PM]);
        String calcModePm;
        if (tipoPm == null || tipoPm.equalsIgnoreCase("lordo")) {
            calcModePm = "percentuale_lordo";
        } else if (tipoPm.equalsIgnoreCase("netto")) {
            calcModePm = "percentuale_netto";
        } else {
            throw new IllegalArgumentException("Tipo commissione PM non valido: " + tipoPm + " (ammessi: lordo, netto)");
        }

        return new RigaImport(cognome, nome, cf, nomeImmobile, citta,
                iban, regime, email, telefono, indirizzo,
                parsePrimoImmobile(v[COL_PRIMO_IMMOBILE]),
                parseNumero(v[COL_COMMISSIONE_OTA], "Commissione OTA %"),
                parseNumero(v[COL_PULIZIE], "Pulizie €"),
                parseNumero(v[COL_CAMBIO_BIANCHERIA], "Cambio Biancheria €"),
                parseNumero(v[COL_COMMISSIONE_PM], "Commissione PM %"),
                calcModePm);
    }

    /** "Si"/"Sì"/"SI" → true, "No" → false, vuoto → null (decide importaRiga). */
    private Boolean parsePrimoImmobile(String s) {
        String v = facoltativo(s);
        if (v == null) return null;
        String n = senzaAccenti(v).toLowerCase(Locale.ROOT);
        return switch (n) {
            case "si", "s", "true", "1" -> true;
            case "no", "n", "false", "0" -> false;
            default -> throw new IllegalArgumentException("Primo immobile non valido: " + v + " (ammessi: Si, No)");
        };
    }

    /**
     * Numero da cella: getCellValue restituisce già "15" o "60.5" per le celle numeriche;
     * per le celle di testo si accettano "15%", "€ 60,00", "1.234,56".
     */
    private BigDecimal parseNumero(String s, String colonna) {
        String v = facoltativo(s);
        if (v == null) return null;
        String n = v.replace(" ", "").replace(" ", "").replace("€", "").replace("%", "")
                .replaceAll("(?i)eur", "");
        int virgola = n.lastIndexOf(',');
        int punto = n.lastIndexOf('.');
        if (virgola >= 0 && punto >= 0) {
            // entrambi presenti: l'ultimo è il separatore decimale
            n = virgola > punto ? n.replace(".", "").replace(',', '.') : n.replace(",", "");
        } else if (virgola >= 0) {
            n = n.replace(',', '.');
        }
        try {
            BigDecimal d = new BigDecimal(n);
            if (d.signum() < 0) {
                throw new IllegalArgumentException(colonna + " non può essere negativo: " + v);
            }
            return d;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(colonna + " non numerico: " + v);
        }
    }

    // ── helper ────────────────────────────────────────────────────────────────

    private Workbook apriWorkbook(InputStream is) throws IOException {
        // Formato controllato prima: per un tipo sconosciuto WorkbookFactory lancia IOException,
        // che finirebbe in 500 invece del 400 da file non valido
        InputStream in = FileMagic.prepareToCheckMagic(is);
        FileMagic magic = FileMagic.valueOf(in);
        if (magic != FileMagic.OOXML && magic != FileMagic.OLE2) {
            throw new IllegalArgumentException("Il file non è un Excel valido (.xlsx o .xls)");
        }
        try {
            // WorkbookFactory riconosce da sé .xlsx (OOXML) e .xls (BIFF)
            return WorkbookFactory.create(in);
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Il file non è un Excel valido (.xlsx o .xls)");
        }
    }

    /** Righe non vuote dopo l'intestazione, con il numero di riga Excel. */
    private List<RigaFile> leggiFile(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File mancante o vuoto");
        }
        List<RigaFile> righe = new ArrayList<>();
        try (InputStream is = file.getInputStream(); Workbook wb = apriWorkbook(is)) {
            Sheet sheet = wb.getSheet("Importazione");
            if (sheet == null) {
                sheet = wb.getSheetAt(0);
            }
            // Uno per lettura: DataFormatter non è thread-safe e il service è un singleton
            DataFormatter fmt = new DataFormatter();
            int rigaIntestazione = trovaRigaIntestazione(sheet, fmt);
            if (rigaIntestazione < 0) {
                throw new IllegalArgumentException(
                        "Intestazione non trovata: la prima colonna deve contenere \"Cognome Proprietario\"");
            }
            for (int r = rigaIntestazione + 1; r <= sheet.getLastRowNum(); r++) {
                String[] valori = leggiRiga(sheet.getRow(r), fmt);
                if (!rigaVuota(valori)) {
                    righe.add(new RigaFile(r + 1, valori));
                }
            }
        }
        return righe;
    }

    /**
     * Stesso CF, nome o cognome diversi → errore (confronto senza maiuscole, accenti e spazi
     * doppi). Proprietari a DB senza nome/cognome (società con ragione sociale) non si confrontano.
     */
    private void verificaCoerenza(RigaImport riga, String cognomeAtteso, String nomeAtteso, String fonte) {
        if (cognomeAtteso == null && nomeAtteso == null) {
            return;
        }
        boolean coerente = normalizzaNome(riga.cognome()).equals(normalizzaNome(cognomeAtteso))
                && normalizzaNome(riga.nome()).equals(normalizzaNome(nomeAtteso));
        if (!coerente) {
            throw new IllegalArgumentException("Codice fiscale " + riga.codiceFiscale() + " già associato a "
                    + ((cognomeAtteso != null ? cognomeAtteso : "") + " " + (nomeAtteso != null ? nomeAtteso : "")).trim()
                    + " (" + fonte + "): nome o cognome diversi");
        }
    }

    private String normalizzaNome(String s) {
        return s == null ? "" : senzaAccenti(s).trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private int trovaRigaIntestazione(Sheet sheet, DataFormatter fmt) {
        int ultima = Math.min(sheet.getLastRowNum(), MAX_RIGHE_RICERCA_INTESTAZIONE);
        for (int r = sheet.getFirstRowNum(); r <= ultima; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            String prima = ExcelCellUtils.getCellValue(row.getCell(0), fmt).toLowerCase(Locale.ROOT);
            if (prima.startsWith("cognome")) {
                return r;
            }
        }
        return -1;
    }

    private String[] leggiRiga(Row row, DataFormatter fmt) {
        String[] valori = new String[NUM_COLONNE];
        for (int c = 0; c < NUM_COLONNE; c++) {
            valori[c] = row == null ? "" : ExcelCellUtils.getCellValue(row.getCell(c), fmt).trim();
        }
        return valori;
    }

    private boolean rigaVuota(String[] valori) {
        for (String v : valori) {
            if (v != null && !v.isBlank()) return false;
        }
        return true;
    }

    private String descrizioneRiga(String[] v) {
        String persona = (v[COL_COGNOME] + " " + v[COL_NOME]).trim();
        String immobile = v[COL_NOME_IMMOBILE];
        if (persona.isEmpty()) return immobile;
        return immobile.isEmpty() ? persona : persona + " - " + immobile;
    }

    private void aggiungiErrore(OwnerBulkImportResult result, int numeroRiga, String descrizione, String messaggio) {
        result.setRigheInErrore(result.getRigheInErrore() + 1);
        result.getErrori().add(OwnerBulkImportErrore.builder()
                .numeroRiga(numeroRiga)
                .descrizioneRiga(descrizione)
                .messaggio(messaggio)
                .build());
    }

    /** Messaggio per il report: quello di validazione così com'è, per gli errori DB la causa più interna. */
    private String messaggioErrore(RuntimeException e) {
        if (e instanceof IllegalArgumentException) {
            return e.getMessage();
        }
        Throwable t = e;
        while (t.getCause() != null) {
            t = t.getCause();
        }
        return "Errore durante il salvataggio: " + t.getMessage();
    }

    private String obbligatorio(String s, String messaggio) {
        String v = facoltativo(s);
        if (v == null) {
            throw new IllegalArgumentException(messaggio);
        }
        return v;
    }

    private String facoltativo(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private void maxLen(String s, int max, String campo) {
        if (s != null && s.length() > max) {
            throw new IllegalArgumentException(campo + " troppo lungo (max " + max + " caratteri)");
        }
    }

    private boolean positivo(BigDecimal d) {
        return d != null && d.signum() > 0;
    }

    private Integer tipoImmobileDefaultId() {
        // Stesso fallback di PropertyService.create(): il primo tipo della lookup
        return tipoImmobileDAO.findAll().stream().findFirst().map(TipoImmobile::getId).orElse(null);
    }

    /**
     * Codice interno univoco nel tenant: prime 3 lettere della città + progressivo,
     * come i codici esistenti (Roma → ROM-003 se esistono ROM-001 e ROM-002).
     */
    private String generaCodiceInterno(Integer tenantId, String citta) {
        String lettere = senzaAccenti(citta).toUpperCase(Locale.ROOT).replaceAll("[^A-Z]", "");
        String prefisso = (lettere + "XXX").substring(0, 3) + "-";
        int max = 0;
        for (String codice : propertyDAO.findInternalCodesByPrefix(tenantId, prefisso)) {
            String suffisso = codice.substring(prefisso.length());
            if (suffisso.matches("[0-9]{1,9}")) {
                max = Math.max(max, Integer.parseInt(suffisso));
            }
        }
        return prefisso + String.format("%03d", max + 1);
    }

    private String senzaAccenti(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
}
