package it.gavia.sostitutoincloud.dao;

import it.gavia.sostitutoincloud.dao.mapper.BookingSplitEconomicoRowMapper;
import it.gavia.sostitutoincloud.model.BookingSplitEconomico;
import lombok.extern.log4j.Log4j2;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@Log4j2
@Repository
public class BookingSplitEconomicoDAO {

    private static final String SELECT_ALL =
            "SELECT id, fk_booking_id, fk_tenant_id, fk_property_contract_rule_id, " +
            "tipo_voce, descrizione, importo, aliquota_iva, include_in_fattura_pm, " +
            "ordinamento, source, deleted_at, created_at, updated_at, created_by, updated_by " +
            "FROM booking_split_economico";

    private final JdbcTemplate jdbcTemplate;
    private final BookingSplitEconomicoRowMapper rowMapper = new BookingSplitEconomicoRowMapper();

    public BookingSplitEconomicoDAO(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Righe attive (non eliminate logicamente) della prenotazione, nell'ordine di visualizzazione. */
    public List<BookingSplitEconomico> findByBookingId(Integer bookingId) {
        List<BookingSplitEconomico> result = jdbcTemplate.query(
                SELECT_ALL + " WHERE fk_booking_id = ? AND deleted_at IS NULL ORDER BY ordinamento, id",
                rowMapper, bookingId);
        log.debug("BookingSplitEconomicoDAO.findByBookingId() - bookingId={} trovate {} righe", bookingId, result.size());
        return result;
    }

    public Optional<BookingSplitEconomico> findById(Integer id) {
        log.debug("BookingSplitEconomicoDAO.findById() - id={}", id);
        List<BookingSplitEconomico> result = jdbcTemplate.query(
                SELECT_ALL + " WHERE id = ? AND deleted_at IS NULL", rowMapper, id);
        return result.isEmpty() ? Optional.empty() : Optional.of(result.get(0));
    }

    public BookingSplitEconomico insert(BookingSplitEconomico riga) {
        String sql = "INSERT INTO booking_split_economico " +
                "(fk_booking_id, fk_tenant_id, fk_property_contract_rule_id, " +
                "tipo_voce, descrizione, importo, " +
                "aliquota_iva, include_in_fattura_pm, " +
                "ordinamento, source, " +
                "created_by, updated_by) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(con -> {
            PreparedStatement ps = con.prepareStatement(sql, new String[]{"id"});
            ps.setInt(1, riga.getFkBookingId());
            ps.setInt(2, riga.getFkTenantId());
            ps.setObject(3, riga.getFkPropertyContractRuleId());
            ps.setString(4, riga.getTipoVoce());
            ps.setString(5, riga.getDescrizione());
            ps.setBigDecimal(6, riga.getImporto());
            // Colonne NOT NULL con default a DB: un null esplicito nell'INSERT non attiva il
            // DEFAULT, quindi i default si replicano qui.
            ps.setBigDecimal(7, riga.getAliquotaIva() != null ? riga.getAliquotaIva() : BigDecimal.ZERO);
            ps.setBoolean(8, !Boolean.FALSE.equals(riga.getIncludeInFatturaPm()));
            ps.setInt(9, riga.getOrdinamento() != null ? riga.getOrdinamento() : 0);
            ps.setString(10, riga.getSource() != null ? riga.getSource() : "calcolato");
            ps.setObject(11, riga.getCreatedBy());
            ps.setObject(12, riga.getUpdatedBy());
            return ps;
        }, keyHolder);
        Integer id = keyHolder.getKey().intValue();
        log.info("BookingSplitEconomicoDAO.insert() - bookingId={} tipoVoce={}", riga.getFkBookingId(), riga.getTipoVoce());
        return findById(id).orElseThrow();
    }

    /**
     * Aggiorna i campi modificabili di una riga attiva. Il filtro sul tenant fa parte della
     * protezione: id di un altro tenant o riga eliminata → nessuna riga aggiornata → eccezione.
     */
    public BookingSplitEconomico update(BookingSplitEconomico riga) {
        String sql = "UPDATE booking_split_economico SET " +
                "descrizione = ?, " +
                "importo = ?, " +
                "aliquota_iva = ?, " +
                "include_in_fattura_pm = ?, " +
                "ordinamento = ?, " +
                "updated_at = NOW(), " +
                "updated_by = ? " +
                "WHERE id = ? AND fk_tenant_id = ? AND deleted_at IS NULL";
        int updated = jdbcTemplate.update(sql,
                riga.getDescrizione(),
                riga.getImporto(),
                riga.getAliquotaIva() != null ? riga.getAliquotaIva() : BigDecimal.ZERO,
                !Boolean.FALSE.equals(riga.getIncludeInFatturaPm()),
                riga.getOrdinamento() != null ? riga.getOrdinamento() : 0,
                riga.getUpdatedBy(),
                riga.getId(), riga.getFkTenantId());
        log.info("BookingSplitEconomicoDAO.update() - id={}", riga.getId());
        if (updated == 0) {
            throw new NoSuchElementException("Riga split non trovata: id=" + riga.getId());
        }
        return findById(riga.getId()).orElseThrow();
    }

    public void softDelete(Integer id, Integer tenantId, Integer updatedBy) {
        String sql = "UPDATE booking_split_economico SET " +
                "deleted_at = NOW(), " +
                "updated_at = NOW(), " +
                "updated_by = ? " +
                "WHERE id = ? AND fk_tenant_id = ? AND deleted_at IS NULL";
        int updated = jdbcTemplate.update(sql, updatedBy, id, tenantId);
        log.info("BookingSplitEconomicoDAO.softDelete() - id={} righe={}", id, updated);
    }

    /** Hard delete: usato solo dal cleanup test e dalla cancellazione della prenotazione. */
    public void deleteByBookingId(Integer bookingId) {
        int deleted = jdbcTemplate.update("DELETE FROM booking_split_economico WHERE fk_booking_id = ?", bookingId);
        log.info("BookingSplitEconomicoDAO.deleteByBookingId() - bookingId={} righe={}", bookingId, deleted);
    }

    /**
     * Hard delete delle sole righe calcolate (tutto tranne tipo_voce='extra'): usato dal
     * ricalcolo dello split, che riscrive le voci dalle regole ma non deve toccare le voci
     * extra inserite a mano dal PM.
     */
    public void deleteCalcolateByBookingId(Integer bookingId) {
        int deleted = jdbcTemplate.update(
                "DELETE FROM booking_split_economico WHERE fk_booking_id = ? AND tipo_voce <> 'extra'",
                bookingId);
        log.info("BookingSplitEconomicoDAO.deleteCalcolateByBookingId() - bookingId={} righe={}", bookingId, deleted);
    }

    /** Somma delle righe attive che entrano nella fattura PM (valore di booking.total_costi_pm). */
    public BigDecimal sumImportoByBookingId(Integer bookingId) {
        BigDecimal sum = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(importo), 0) FROM booking_split_economico " +
                "WHERE fk_booking_id = ? AND include_in_fattura_pm = true AND deleted_at IS NULL",
                BigDecimal.class, bookingId);
        log.debug("BookingSplitEconomicoDAO.sumImportoByBookingId() - bookingId={} totale={}", bookingId, sum);
        return sum;
    }
}
