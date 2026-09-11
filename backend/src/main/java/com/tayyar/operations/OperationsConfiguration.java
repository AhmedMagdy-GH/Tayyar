package com.tayyar.operations;

import java.util.List;

import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OperationsProperties.class)
public class OperationsConfiguration {
    private final OperationsProperties properties;
    private final Environment environment;
    private final boolean secureCookie;
    private final boolean cleanDisabled;
    private final boolean baselineOnMigrate;

    public OperationsConfiguration(
            OperationsProperties properties,
            Environment environment,
            @Value("${server.servlet.session.cookie.secure:true}") boolean secureCookie,
            @Value("${spring.flyway.clean-disabled:true}") boolean cleanDisabled,
            @Value("${spring.flyway.baseline-on-migrate:false}") boolean baselineOnMigrate) {
        this.properties = properties;
        this.environment = environment;
        this.secureCookie = secureCookie;
        this.cleanDisabled = cleanDisabled;
        this.baselineOnMigrate = baselineOnMigrate;
    }

    @PostConstruct
    void validate() {
        boolean production = environment.matchesProfiles("production");
        properties.validate(production);
        if (production && !secureCookie) {
            throw new IllegalStateException("Production requires Secure session cookies");
        }
        if (production && (!cleanDisabled || baselineOnMigrate)) {
            throw new IllegalStateException("Production requires Flyway clean disabled and baseline disabled");
        }
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        var source = new UrlBasedCorsConfigurationSource();
        if (!properties.allowedOrigins().isEmpty()) {
            var cors = new CorsConfiguration();
            cors.setAllowedOrigins(properties.allowedOrigins());
            cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
            cors.setAllowedHeaders(List.of("Content-Type", "X-CSRF-TOKEN", "Idempotency-Key",
                    RequestHeaders.CORRELATION_ID));
            cors.setExposedHeaders(List.of(RequestHeaders.CORRELATION_ID));
            cors.setAllowCredentials(true);
            cors.setMaxAge(3600L);
            source.registerCorsConfiguration("/**", cors);
        }
        return source;
    }

    private static final class RequestHeaders {
        static final String CORRELATION_ID = "X-Correlation-ID";
        private RequestHeaders() {}
    }
}
