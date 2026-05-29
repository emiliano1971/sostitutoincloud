package it.gavia.sostitutoincloud.config;

import lombok.extern.log4j.Log4j2;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

@Service
@Log4j2
public class DatabaseUserDetailsService implements UserDetailsService {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseUserDetailsService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        String sql = "SELECT id, email, password_hash, ruolo, fk_tenant_id, fk_owner_id, attivo " +
                     "FROM utente WHERE email = ?";

        List<CustomUserDetails> results = jdbcTemplate.query(sql, (rs, rowNum) -> {
            Integer id = rs.getInt("id");
            String pwd = rs.getString("password_hash");
            String ruolo = rs.getString("ruolo");
            Integer fkTenantId = rs.getObject("fk_tenant_id", Integer.class);
            Integer fkOwnerId = rs.getObject("fk_owner_id", Integer.class);
            boolean attivo = rs.getBoolean("attivo");

            GrantedAuthority authority = new SimpleGrantedAuthority(mapRuoloToRole(ruolo));

            return new CustomUserDetails(email, pwd, List.of(authority), fkTenantId, id, fkOwnerId, attivo);
        }, email);

        if (results.isEmpty()) {
            log.warn("Utente non trovato: {}", email);
            throw new UsernameNotFoundException("Utente non trovato: " + email);
        }

        CustomUserDetails user = results.get(0);
        if (!user.isEnabled()) {
            log.warn("Utente disabilitato: {}", email);
            throw new DisabledException("Utente disabilitato: " + email);
        }

        log.debug("Utente autenticato: email={}, ruolo={}", email, user.getAuthorities());
        return user;
    }

    private String mapRuoloToRole(String ruolo) {
        return switch (ruolo) {
            case "super_admin"  -> "ROLE_SUPER_ADMIN";
            case "tenant_admin" -> "ROLE_TENANT_ADMIN";
            case "pm_user"      -> "ROLE_PM_USER";
            case "owner_user"   -> "ROLE_OWNER_USER";
            default             -> "ROLE_" + ruolo.toUpperCase();
        };
    }

    public static class CustomUserDetails extends org.springframework.security.core.userdetails.User {

        private final Integer tenantId;
        private final Integer utenteId;
        private final Integer ownerId;

        public CustomUserDetails(String username, String password,
                                  Collection<? extends GrantedAuthority> authorities,
                                  Integer tenantId, Integer utenteId, Integer ownerId, boolean enabled) {
            super(username, password, enabled, true, true, true, authorities);
            this.tenantId = tenantId;
            this.utenteId = utenteId;
            this.ownerId = ownerId;
        }

        public Integer getTenantId() {
            return tenantId;
        }

        public Integer getUtenteId() {
            return utenteId;
        }

        public Integer getOwnerId() {
            return ownerId;
        }
    }
}
