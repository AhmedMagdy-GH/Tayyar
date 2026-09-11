package com.tayyar.operations;

import java.net.URI;
import java.util.List;

import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties("tayyar.operations")
@Validated
public record OperationsProperties(
        @NotNull List<String> allowedOrigins,
        @NotNull DataSize maxRequestBody) {
    public OperationsProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }

    public void validate(boolean production) {
        long bytes = maxRequestBody.toBytes();
        if (bytes < DataSize.ofKilobytes(64).toBytes() || bytes > DataSize.ofMegabytes(10).toBytes()) {
            throw new IllegalStateException("Request body limit must be between 64KB and 10MB");
        }
        for (String origin : allowedOrigins) {
            URI value;
            try {
                value = URI.create(origin);
            } catch (IllegalArgumentException error) {
                throw new IllegalStateException("Allowed origins must be valid absolute origins", error);
            }
            boolean safeScheme = "https".equalsIgnoreCase(value.getScheme())
                    || (!production && "http".equalsIgnoreCase(value.getScheme()));
            if (!safeScheme || value.getHost() == null || value.getUserInfo() != null
                    || value.getQuery() != null || value.getFragment() != null
                    || (value.getPath() != null && !value.getPath().isEmpty())) {
                throw new IllegalStateException("Allowed origins must be absolute origins without paths"
                        + (production ? " and must use HTTPS in production" : ""));
            }
        }
    }
}
