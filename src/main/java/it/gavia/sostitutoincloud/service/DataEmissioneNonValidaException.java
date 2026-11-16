package it.gavia.sostitutoincloud.service;

/**
 * Data di emissione di un documento fiscale non ammessa: futura, oppure anteriore all'ultimo
 * documento dello stesso tipo e anno (la numerazione progressiva deve seguire le date).
 * Estende IllegalStateException come gli altri blocchi di emissione; GlobalExceptionHandler
 * la mappa su 422.
 */
public class DataEmissioneNonValidaException extends IllegalStateException {

    public DataEmissioneNonValidaException(String message) {
        super(message);
    }
}
