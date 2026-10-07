package it.gavia.sostitutoincloud.util;

import lombok.extern.log4j.Log4j2;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.*;

import java.io.FileOutputStream;

/**
 * Genera il template Excel per l'importazione massiva di proprietari e immobili
 * (docs/import/template_importazione_proprietari.xlsx, prompt SETUP-IMPORT-MASSIVO-PROPRIETARI).
 *
 * <p>Utility da riga di comando, come {@link PasswordHashGenerator}: non è usata
 * dall'applicazione. Per cambiare il template si modifica questa classe e la si riesegue.
 *
 * <p>Esecuzione dalla root del progetto, dopo {@code mvn -Plocal -DskipTests clean package}
 * (classi e JAR, POI compreso, sono già in WEB-INF/):
 * <pre>
 *   java -Dcatalina.home=../.. -cp "WEB-INF/classes:WEB-INF/lib/*" \
 *        it.gavia.sostitutoincloud.util.TemplateProprietariGenerator \
 *        [percorso-output.xlsx]
 * </pre>
 * Senza argomento scrive {@value #OUTPUT_DEFAULT} (relativo alla directory corrente).
 * {@code -Dcatalina.home=../..}: log4j2.xml scrive in {@code ${sys:catalina.home}/logs}; fuori
 * da Tomcat la proprietà non esiste e i log finirebbero in una directory chiamata letteralmente
 * "${sys:catalina.home}" nella root del progetto.
 *
 * <p>Note di layout: intestazioni (riga 3) grigie come da specifica, i colori di categoria
 * (obbligatorio / facoltativo / regole contratto) sono sulle celle dati 4..1000, così la
 * legenda della riga 2 corrisponde a ciò che si compila. POI non calcola l'altezza delle
 * righe a capo: nelle Istruzioni è stimata dalla lunghezza del testo.
 */
@Log4j2
public final class TemplateProprietariGenerator {

    static final String OUTPUT_DEFAULT = "docs/import/template_importazione_proprietari.xlsx";
    static final String FONT = "Arial";
    static final int ULTIMA_RIGA = 1000;       // righe dati formattate (indice 1-based)

    private TemplateProprietariGenerator() {}

    public static void main(String[] args) throws Exception {
        String output = args.length > 0 ? args[0] : OUTPUT_DEFAULT;
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            XSSFSheet sh = wb.createSheet("Importazione");
            XSSFDataFormat df = wb.createDataFormat();

            // ── stili ─────────────────────────────────────────────────────
            XSSFCellStyle titolo = stile(wb, "1F4E79", true, 14, "FFFFFF");
            titolo.setAlignment(HorizontalAlignment.CENTER);
            titolo.setVerticalAlignment(VerticalAlignment.CENTER);

            XSSFCellStyle legObbl = stile(wb, "FFF2CC", true, 10, null);
            XSSFCellStyle legFac  = stile(wb, "DDEEFF", false, 10, null);
            XSSFCellStyle legReg  = stile(wb, "E2EFDA", false, 10, null);

            XSSFCellStyle intest = stile(wb, "D9D9D9", true, 10, null);
            bordi(intest);
            intest.setWrapText(true);
            intest.setVerticalAlignment(VerticalAlignment.CENTER);

            // Celle dati per categoria: obbligatorie in grassetto, testo per CF/IBAN/telefono
            // (niente conversione in numero: si perderebbero zeri iniziali e cifre dell'IBAN)
            XSSFCellStyle datObblTesto = stile(wb, "FFF2CC", true, 10, null);
            datObblTesto.setDataFormat(df.getFormat("@"));
            XSSFCellStyle datFacTesto = stile(wb, "DDEEFF", false, 10, null);
            datFacTesto.setDataFormat(df.getFormat("@"));
            XSSFCellStyle datRegNum = stile(wb, "E2EFDA", false, 10, null);
            datRegNum.setDataFormat(df.getFormat("0.00"));
            XSSFCellStyle datRegTesto = stile(wb, "E2EFDA", false, 10, null);

            // ── colonne ───────────────────────────────────────────────────
            String[] header = {
                "Cognome Proprietario *", "Nome Proprietario *", "Codice Fiscale *",
                "Nome Immobile *", "Città *",
                "CIN (Codice Identificativo Nazionale)",
                "IBAN", "Regime Fiscale", "Email Proprietario", "Telefono Proprietario",
                "Indirizzo Immobile", "Primo Immobile",
                "Commissione OTA %", "Pulizie € (fisso)", "Cambio Biancheria € (per persona)",
                "Commissione PM %", "Tipo Commissione PM"
            };
            // 0 = obbligatoria, 1 = facoltativa, 2 = regola contratto
            int[] categoria = {0,0,0,0,0, 1,1,1,1,1,1,1, 2,2,2,2,2};
            int[] larghezza = {20,20,18,25,15, 22,28,18,25,15,25,14, 12,12,12,12,16};
            final int numColonne = header.length;   // 17: A..Q
            for (int c = 0; c < larghezza.length; c++) sh.setColumnWidth(c, larghezza[c] * 256);

            // Riga 1: titolo su tutta la riga (A1:Q1)
            Row r1 = sh.createRow(0);
            r1.setHeightInPoints(26);
            for (int c = 0; c < numColonne; c++) r1.createCell(c).setCellStyle(titolo);
            r1.getCell(0).setCellValue("Template Importazione Proprietari e Immobili");
            sh.addMergedRegion(new CellRangeAddress(0, 0, 0, numColonne - 1));

            // Riga 2: legenda colori
            Row r2 = sh.createRow(1);
            cella(r2, 0, "■ Obbligatorio", legObbl);
            cella(r2, 2, "■ Facoltativo", legFac);
            cella(r2, 4, "■ Regole Contratto", legReg);

            // Riga 3: intestazioni
            Row r3 = sh.createRow(2);
            r3.setHeightInPoints(42);   // tre righe per "Cambio Biancheria € (per persona)"
            for (int c = 0; c < header.length; c++) cella(r3, c, header[c], intest);

            // Commenti sulle intestazioni: default e significato (visibili al passaggio del mouse)
            XSSFDrawing draw = sh.createDrawingPatriarch();
            commento(wb, draw, r3.getCell(2), "16 caratteri. Identifica il proprietario: se esiste già, l'immobile viene associato a lui senza sovrascriverlo.");
            commento(wb, draw, r3.getCell(5), "Formato: IT + 6 cifre + 1 lettera + 9 caratteri alfanumerici (18 caratteri). Es. IT058091C1A2B3C4D5. Facoltativo, ma necessario per la CU.");
            commento(wb, draw, r3.getCell(7), "Valori: cedolare_secca / ordinario / iva_10. Se vuoto: cedolare_secca.");
            commento(wb, draw, r3.getCell(11), "Valori: Si / No. Se vuoto: Si.");
            commento(wb, draw, r3.getCell(12), "Percentuale sul lordo ospite, applicata al canale OTA di default del tenant. Es. 15 = 15%.");
            commento(wb, draw, r3.getCell(13), "Importo fisso netto (senza IVA) per prenotazione.");
            commento(wb, draw, r3.getCell(14), "Importo netto (senza IVA) per ospite.");
            commento(wb, draw, r3.getCell(15), "Percentuale della commissione PM. Es. 10 = 10%.");
            commento(wb, draw, r3.getCell(16), "lordo = sul lordo ospite; netto = sul lordo meno le spese.");

            // Righe dati 4..1000: celle vuote già colorate per categoria
            for (int r = 3; r < ULTIMA_RIGA; r++) {
                Row row = sh.createRow(r);
                for (int c = 0; c < numColonne; c++) {
                    Cell cell = row.createCell(c);
                    boolean numerica = c >= 12 && c <= 15;
                    cell.setCellStyle(switch (categoria[c]) {
                        case 0 -> datObblTesto;
                        case 1 -> datFacTesto;
                        default -> numerica ? datRegNum : datRegTesto;
                    });
                }
            }

            // Riga 4: esempio con dati fittizi
            Row r4 = sh.getRow(3);
            String[] esempioTesto = {"Rossi", "Mario", "RSSMRA80A01H501Z", "Appartamento Centro", "Roma",
                    "IT058091C1A2B3C4D5", "IT60X0542811101000000123456", "cedolare_secca", "mario.rossi@email.it",
                    "3331234567", "Via Roma 1", "Si"};
            for (int c = 0; c < esempioTesto.length; c++) r4.getCell(c).setCellValue(esempioTesto[c]);
            r4.getCell(12).setCellValue(15);
            r4.getCell(13).setCellValue(60);
            r4.getCell(14).setCellValue(20);
            r4.getCell(15).setCellValue(10);
            r4.getCell(16).setCellValue("netto");
            commento(wb, draw, r4.getCell(0), "Riga di esempio con dati fittizi: cancellarla o sostituirla prima dell'importazione.");

            // Dropdown su H, L, Q per le righe 5:1000
            DataValidationHelper dvh = sh.getDataValidationHelper();
            dropdown(sh, dvh, 7,  new String[]{"cedolare_secca", "ordinario", "iva_10"});
            dropdown(sh, dvh, 11, new String[]{"Si", "No"});
            dropdown(sh, dvh, 16, new String[]{"lordo", "netto"});

            // Blocca le prime tre righe (titolo, legenda, intestazioni)
            sh.createFreezePane(0, 3);

            // Stampa: orizzontale, tutte le colonne in una pagina di larghezza,
            // intestazioni ripetute su ogni pagina
            sh.getPrintSetup().setLandscape(true);
            sh.getPrintSetup().setPaperSize(PrintSetup.A4_PAPERSIZE);
            sh.setFitToPage(true);
            sh.getPrintSetup().setFitWidth((short) 1);
            sh.getPrintSetup().setFitHeight((short) 0);
            sh.setRepeatingRows(CellRangeAddress.valueOf("1:3"));

            // ── Foglio 2: istruzioni ─────────────────────────────────────
            XSSFSheet ist = wb.createSheet("Istruzioni");
            final int CARATTERI_RIGA = 72;   // larghezza colonna: sta nell'area stampabile di un A4 verticale
            ist.setColumnWidth(0, CARATTERI_RIGA * 256);
            XSSFCellStyle istTitolo = stile(wb, null, true, 14, "1F4E79");
            XSSFCellStyle istSezione = stile(wb, null, true, 11, null);
            XSSFCellStyle istTesto = stile(wb, null, false, 10, null);
            istTesto.setWrapText(true);

            String[][] righe = {
                {"T", "ISTRUZIONI PER L'IMPORTAZIONE"},
                {"", ""},
                {"S", "1. CAMPI OBBLIGATORI (sfondo giallo):"},
                {"", "- Cognome e Nome: del proprietario"},
                {"", "- Codice Fiscale: 16 caratteri, usato per identificare il proprietario. Se già esiste nel sistema, l'immobile viene associato al proprietario esistente senza sovrascriverlo."},
                {"", "- Nome Immobile: nome display dell'immobile"},
                {"", "- Città: comune dell'immobile"},
                {"", ""},
                {"S", "2. CIN (sfondo azzurro, facoltativo):"},
                {"", "CIN: Codice Identificativo Nazionale obbligatorio per locazioni brevi dal 2024. Formato: IT + 6 cifre + 1 lettera + 9 caratteri alfanumerici (18 caratteri totali). Facoltativo nel template ma necessario per la CU."},
                {"", "Un CIN in formato diverso viene importato comunque e segnalato come avviso."},
                {"", ""},
                {"S", "3. PROPRIETARIO CON PIÙ IMMOBILI:"},
                {"", "Ripetere le colonne del proprietario (A-C, G-J) su ogni riga, una per immobile. Il sistema riconosce lo stesso proprietario tramite il Codice Fiscale."},
                {"", ""},
                {"S", "4. DUPLICATI:"},
                {"", "Se un proprietario o un immobile esiste già nel sistema, la riga viene saltata senza errori."},
                {"", ""},
                {"S", "5. REGIME FISCALE:"},
                {"", "- cedolare_secca: default, locazioni brevi persone fisiche"},
                {"", "- ordinario: con IVA"},
                {"", "- iva_10: IVA agevolata"},
                {"", ""},
                {"S", "6. REGOLE CONTRATTO (sfondo verde):"},
                {"", "- Commissione OTA %: percentuale applicata al canale OTA default configurato nel sistema"},
                {"", "- Pulizie €: importo fisso netto (senza IVA)"},
                {"", "- Cambio Biancheria €/persona: importo per ospite netto (senza IVA)"},
                {"", "- Commissione PM %: percentuale netto o sul lordo"},
                {"", "- Tipo PM: 'lordo' = sul lordo ospite; 'netto' = sul lordo meno le spese"},
                {"", "- Rimanenza: se la riga ha almeno una regola contratto, il sistema aggiunge automaticamente la voce 'Provvigione proprietario' come rimanenza: al proprietario va quanto resta del lordo ospite dopo le altre voci. Non va indicata nel file. Senza regole contratto l'immobile viene creato senza regole (nemmeno la rimanenza) e le regole si configurano poi dalla scheda dell'immobile."},
                {"", ""},
                {"S", "7. ERRORI:"},
                {"", "Le righe con errori vengono saltate. Al termine dell'import viene mostrato un report con le righe importate, saltate e gli errori."},
            };
            for (int i = 0; i < righe.length; i++) {
                Row row = ist.createRow(i);
                Cell cell = row.createCell(0);
                cell.setCellValue(righe[i][1]);
                // POI non calcola l'altezza delle righe a capo: senza, le righe lunghe si
                // sovrapporrebbero (altezza di default). ~1 riga ogni CARATTERI_RIGA caratteri.
                // Arial 10 è più stretto del carattere di riferimento della larghezza: ~1.5 caratteri per unità (misurato)
                int linee = Math.max(1, (int) Math.ceil(righe[i][1].length() / (CARATTERI_RIGA * 1.5)));
                if (linee > 1) row.setHeightInPoints(linee * 13.5f);
                cell.setCellStyle(switch (righe[i][0]) {
                    case "T" -> istTitolo;
                    case "S" -> istSezione;
                    default -> istTesto;
                });
            }

            // Istruzioni in stampa: A4 verticale, tutto il testo in una pagina di larghezza
            ist.getPrintSetup().setPaperSize(PrintSetup.A4_PAPERSIZE);
            ist.setFitToPage(true);
            ist.getPrintSetup().setFitWidth((short) 1);
            ist.getPrintSetup().setFitHeight((short) 0);

            wb.setActiveSheet(0);
            try (FileOutputStream out = new FileOutputStream(output)) {
                wb.write(out);
            }
            log.info("TemplateProprietariGenerator - template scritto in {}", output);
        }
    }

    static XSSFCellStyle stile(XSSFWorkbook wb, String sfondo, boolean grassetto, int size, String coloreTesto) {
        XSSFCellStyle st = wb.createCellStyle();
        XSSFFont f = wb.createFont();
        f.setFontName(FONT);
        f.setFontHeightInPoints((short) size);
        f.setBold(grassetto);
        if (coloreTesto != null) f.setColor(new XSSFColor(hex(coloreTesto), null));
        st.setFont(f);
        if (sfondo != null) {
            st.setFillForegroundColor(new XSSFColor(hex(sfondo), null));
            st.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        }
        return st;
    }

    static void bordi(XSSFCellStyle st) {
        st.setBorderTop(BorderStyle.THIN);
        st.setBorderBottom(BorderStyle.THIN);
        st.setBorderLeft(BorderStyle.THIN);
        st.setBorderRight(BorderStyle.THIN);
    }

    static byte[] hex(String h) {
        return new byte[]{(byte) Integer.parseInt(h.substring(0, 2), 16),
                (byte) Integer.parseInt(h.substring(2, 4), 16),
                (byte) Integer.parseInt(h.substring(4, 6), 16)};
    }

    static void cella(Row row, int c, String v, CellStyle st) {
        Cell cell = row.createCell(c);
        cell.setCellValue(v);
        cell.setCellStyle(st);
    }

    static void commento(XSSFWorkbook wb, XSSFDrawing draw, Cell cell, String testo) {
        XSSFClientAnchor a = wb.getCreationHelper().createClientAnchor();
        a.setCol1(cell.getColumnIndex());
        a.setCol2(cell.getColumnIndex() + 3);
        a.setRow1(cell.getRowIndex());
        a.setRow2(cell.getRowIndex() + 4);
        Comment cm = draw.createCellComment(a);
        cm.setString(wb.getCreationHelper().createRichTextString(testo));
        cm.setAuthor("Sostituto in Cloud");
        cell.setCellComment(cm);
    }

    static void dropdown(XSSFSheet sh, DataValidationHelper dvh, int col, String[] valori) {
        DataValidationConstraint dvc = dvh.createExplicitListConstraint(valori);
        CellRangeAddressList range = new CellRangeAddressList(4, ULTIMA_RIGA - 1, col, col); // righe 5..1000
        DataValidation dv = dvh.createValidation(dvc, range);
        dv.setSuppressDropDownArrow(true);   // in XSSF true = freccia visibile
        dv.setShowErrorBox(true);
        dv.createErrorBox("Valore non valido", "Scegliere un valore dall'elenco: " + String.join(", ", valori));
        sh.addValidationData(dv);
    }
}
