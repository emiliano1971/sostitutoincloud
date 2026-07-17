package it.gavia.sostitutoincloud.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configurazione globale di Jackson.
 * - WRITE_BIGDECIMAL_AS_PLAIN disabilitato: i BigDecimal sono serializzati come
 *   numeri decimali semplici (es. 123.45) e non in notazione scientifica.
 * - NON_NULL: i campi null vengono omessi dal JSON.
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer jacksonCustomizer() {
        return builder -> builder
                .featuresToDisable(SerializationFeature.WRITE_BIGDECIMAL_AS_PLAIN)
                .serializationInclusion(JsonInclude.Include.NON_NULL);
    }
}
