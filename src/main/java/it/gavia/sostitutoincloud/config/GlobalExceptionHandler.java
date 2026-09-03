package it.gavia.sostitutoincloud.config;

import lombok.extern.log4j.Log4j2;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
@Log4j2
public class GlobalExceptionHandler {

    /**
     * Errori di accesso al database → 500, con stack trace nei log.
     *
     * <p>Handler dedicato perché DataAccessException è una RuntimeException e senza di esso
     * finiva su handleNotFound(), che risponde 404: un errore SQL (query malformata, tipo
     * sbagliato, vincolo violato) arrivava al frontend travestito da "risorsa non trovata",
     * senza stack trace nei log. Spring sceglie l'handler più specifico, quindi questo vince
     * su handleNotFound() per tutta la gerarchia DataAccessException — incluso
     * BadSqlGrammarException — e i "X non trovato" continuano a rispondere 404.
     */
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, Object>> handleDataAccess(DataAccessException ex) {
        log.error("Errore di accesso ai dati", ex);
        return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Errore di accesso ai dati");
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(RuntimeException ex) {
        log.warn("RuntimeException: {}", ex.getMessage());
        return errorResponse(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    /**
     * Parametro di richiesta obbligatorio mancante → 400.
     *
     * <p>MissingServletRequestParameterException deriva da ServletException e non da
     * RuntimeException, quindi finiva su handleGeneric() e rispondeva 500: una richiesta
     * incompleta del client sembrava un errore del server.
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParam(MissingServletRequestParameterException ex) {
        log.warn("Parametro obbligatorio mancante: {}", ex.getParameterName());
        return errorResponse(HttpStatus.BAD_REQUEST,
                "Parametro obbligatorio mancante: " + ex.getParameterName());
    }

    /**
     * Valore non convertibile nel tipo atteso → 400. Vale sia per i path variable
     * (es. /api/bookings/abc) sia per i parametri di query (es. ?anno=x).
     *
     * <p>È una RuntimeException, quindi senza questo handler finiva su handleNotFound()
     * e una richiesta malformata rispondeva 404 "risorsa non trovata".
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("Valore non valido per '{}': {}", ex.getName(), ex.getValue());
        return errorResponse(HttpStatus.BAD_REQUEST,
                "Valore non valido per il parametro '" + ex.getName() + "': " + ex.getValue());
    }

    /**
     * Corpo della richiesta illeggibile (JSON malformato, tipo non deserializzabile) → 400.
     * Come sopra è una RuntimeException e finiva su handleNotFound() con un 404.
     * Il messaggio dell'eccezione NON viene rimandato al client: contiene dettagli del
     * parser e nomi di classi interne. Resta nei log.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleNotReadable(HttpMessageNotReadableException ex) {
        log.warn("Corpo della richiesta non leggibile: {}", ex.getMessage());
        return errorResponse(HttpStatus.BAD_REQUEST, "Corpo della richiesta non valido o JSON malformato");
    }

    /**
     * Metodo HTTP non ammesso per il path → 405. Come la mancanza di un parametro
     * obbligatorio deriva da ServletException, per cui rispondeva 500.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMethodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        log.warn("Metodo non ammesso: {}", ex.getMessage());
        return errorResponse(HttpStatus.METHOD_NOT_ALLOWED, "Metodo non ammesso: " + ex.getMethod());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {
        log.warn("IllegalArgumentException: {}", ex.getMessage());
        return errorResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(IllegalStateException ex) {
        log.warn("IllegalStateException: {}", ex.getMessage());
        return errorResponse(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(UnsupportedOperationException.class)
    public ResponseEntity<Map<String, Object>> handleNotImplemented(UnsupportedOperationException ex) {
        log.warn("UnsupportedOperationException: {}", ex.getMessage());
        return errorResponse(HttpStatus.NOT_IMPLEMENTED, ex.getMessage());
    }

    /**
     * Path inesistente: la richiesta arriva al resource handler degli static,
     * che solleva NoResourceFoundException. Senza questo handler finiva su
     * handleGeneric(Exception) e rispondeva 500 invece di 404.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoResourceFound(NoResourceFoundException ex) {
        log.warn("NoResourceFoundException: {}", ex.getResourcePath());
        return errorResponse(HttpStatus.NOT_FOUND, "Risorsa non trovata: " + ex.getResourcePath());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        log.error("Errore non gestito", ex);
        return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "Errore interno del server");
    }

    private ResponseEntity<Map<String, Object>> errorResponse(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        body.put("timestamp", LocalDateTime.now());
        return ResponseEntity.status(status).body(body);
    }
}
