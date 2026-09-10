package com.tayyar.auth;

import com.tayyar.common.api.*;

import jakarta.servlet.http.*;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

@Component
public class SecurityErrorWriter {
    private final ObjectMapper mapper;

    public SecurityErrorWriter(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public void write(
            HttpServletRequest request,
            HttpServletResponse response,
            int status,
            String code,
            String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Cache-Control", "no-store");
        mapper.writeValue(
                response.getOutputStream(),
                new ApiError(
                        code,
                        message,
                        Instant.now(),
                        request.getRequestURI(),
                        (String) request.getAttribute(RequestCorrelationFilter.ATTRIBUTE),
                        List.of()));
    }
}
