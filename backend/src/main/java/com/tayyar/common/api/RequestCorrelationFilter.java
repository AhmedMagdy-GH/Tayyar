package com.tayyar.common.api;

import java.io.IOException;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestCorrelationFilter extends OncePerRequestFilter {
    private static final Logger LOG = LoggerFactory.getLogger(RequestCorrelationFilter.class);
    public static final String ATTRIBUTE = RequestCorrelationFilter.class.getName() + ".id";
    public static final String ACTOR_ATTRIBUTE = RequestCorrelationFilter.class.getName() + ".actor";
    public static final String HEADER = "X-Correlation-ID";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String id = UUID.randomUUID().toString();
        request.setAttribute(ATTRIBUTE, id);
        response.setHeader(HEADER, id);
        String previous = MDC.get("correlationId");
        MDC.put("correlationId", id);
        MDC.put("httpMethod", request.getMethod());
        MDC.put("httpPath", request.getRequestURI());
        long started = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            long durationMillis = (System.nanoTime() - started) / 1_000_000;
            Object actor = request.getAttribute(ACTOR_ATTRIBUTE);
            String actorId = actor == null ? "anonymous" : actor.toString();
            LOG.info("http_request method={} path={} status={} durationMs={} actorId={}",
                    request.getMethod(), request.getRequestURI(), response.getStatus(), durationMillis, actorId);
            MDC.remove("httpMethod");
            MDC.remove("httpPath");
            if (previous == null) {
                MDC.remove("correlationId");
            } else {
                MDC.put("correlationId", previous);
            }
        }
    }
}
