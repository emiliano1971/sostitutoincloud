package it.gavia.sostitutoincloud.config;

import it.gavia.sostitutoincloud.config.DatabaseUserDetailsService.CustomUserDetails;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.log4j.Log4j2;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@Log4j2
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final DatabaseUserDetailsService databaseUserDetailsService;

    public JwtAuthFilter(JwtUtils jwtUtils, DatabaseUserDetailsService databaseUserDetailsService) {
        this.jwtUtils = jwtUtils;
        this.databaseUserDetailsService = databaseUserDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            if (jwtUtils.validateToken(token)) {
                String email = jwtUtils.getEmailFromToken(token);
                UserDetails userDetails = databaseUserDetailsService.loadUserByUsername(email);
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
                log.debug("JWT auth OK per: {}", email);

                if (userDetails instanceof CustomUserDetails cud && cud.isMustChangePassword()
                        && !isConsentitoConCambioPendente(request)) {
                    log.warn("JwtAuthFilter - accesso negato a {}: cambio password obbligatorio pendente", email);
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json;charset=UTF-8");
                    response.getWriter().write(
                            "{\"message\":\"Cambio password obbligatorio\",\"mustChangePassword\":true}");
                    return;
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Finché l'utente non ha cambiato la password è ammesso solo il minimo indispensabile
     * per farlo: leggere il proprio profilo e inviare la nuova password. Senza questo blocco
     * il vincolo sarebbe solo un redirect del frontend, aggirabile con una chiamata diretta.
     * Le rotte pubbliche restano libere: non dipendono dall'utente autenticato.
     */
    private boolean isConsentitoConCambioPendente(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return path.startsWith("/api/public/")
                || path.equals("/api/auth/me")
                || path.equals("/api/auth/force-change-password")
                || !path.startsWith("/api/");
    }
}
