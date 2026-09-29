package it.gavia.sostitutoincloud.util;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;

import java.math.BigDecimal;

/**
 * Lettura delle celle Excel (Apache POI) condivisa dagli import: prenotazioni
 * (BookingImportService) e proprietari/immobili (OwnerBulkImportService).
 */
public final class ExcelCellUtils {

    private ExcelCellUtils() {
    }

    /**
     * Valore grezzo della cella, senza la formattazione di Excel: DataFormatter produceva
     * stringhe dipendenti dal locale (es. "1,234.56" o "€ 380,00" con U+00A0).
     * DataFormatter non è thread-safe: il chiamante ne usa uno per lettura.
     */
    public static String getCellValue(Cell cell, DataFormatter fmt) {
        if (cell == null) return "";
        switch (cell.getCellType()) {
            case NUMERIC:
                // Stringa formattata di sole cifre: si tiene quella, conserva gli zeri
                // iniziali (CAP 00184 con formato "00000") ed è indipendente dal locale.
                String formatted = fmt.formatCellValue(cell);
                if (formatted.matches("[0-9]+")) {
                    return formatted;
                }
                // Altrimenti valore numerico diretto, evita problemi di formattazione.
                // BigDecimal.valueOf (non new BigDecimal) per evitare l'espansione binaria
                // lunga del double; stripTrailingZeros perché gli interi in celle con formato
                // decimale restino interi ("2", non "2.0", che Integer.parseInt rifiuta);
                // toPlainString per evitare la notazione scientifica.
                double d = cell.getNumericCellValue();
                return BigDecimal.valueOf(d).stripTrailingZeros().toPlainString();
            case STRING:
                return cell.getStringCellValue().trim();
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case FORMULA:
                try {
                    return String.valueOf(cell.getNumericCellValue());
                } catch (Exception e) {
                    return cell.getStringCellValue();
                }
            default:
                return "";
        }
    }
}
