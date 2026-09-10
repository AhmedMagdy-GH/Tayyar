package com.tayyar.common.api;

import jakarta.validation.Valid;
import jakarta.servlet.RequestDispatcher;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ApiExceptionHandlerTests.ProbeController.class)
@Import({ApiExceptionHandlerTests.ProbeController.class, ApiExceptionHandler.class,
        ApiErrorController.class, RequestCorrelationFilter.class})
class ApiExceptionHandlerTests {
    @Autowired MockMvc mvc;

    @Test
    void reportsFieldErrorsWithoutRejectedValues() throws Exception {
        mvc.perform(post("/test/validation").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"quantity\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(2))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.path").value("/test/validation"))
                .andExpect(jsonPath("$.fieldErrors[0].rejectedValue").doesNotExist());
    }

    @Test
    void sanitizesMalformedJson() throws Exception {
        mvc.perform(post("/test/validation").contentType(MediaType.APPLICATION_JSON)
                        .content("{secret-password"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(content().string(not(containsString("secret-password"))));
    }

    @Test
    void sanitizesUnexpectedFailureAndCorrelatesResponse() throws Exception {
        var result = mvc.perform(get("/test/failure").header("X-Correlation-ID", "untrusted-value"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(content().string(not(containsString("secret-password"))))
                .andExpect(content().string(not(containsString("untrusted-value"))))
                .andReturn();
        String id = result.getResponse().getHeader(RequestCorrelationFilter.HEADER);
        assertThat(id).isNotBlank();
        assertThat(result.getResponse().getContentAsString()).contains(id);
        assertThat(MDC.get("correlationId")).isNull();
    }

    @Test
    void preservesMethodNotAllowedSemantics() throws Exception {
        mvc.perform(delete("/test/validation"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", containsString("POST")))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void sanitizesServletErrorDispatch() throws Exception {
        mvc.perform(get("/error")
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 500)
                        .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/failed")
                        .requestAttr(RequestDispatcher.ERROR_MESSAGE, "secret-password"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.path").value("/failed"))
                .andExpect(content().string(not(containsString("secret-password"))));
    }

    @Test
    void returnsConsistentNotFoundError() throws Exception {
        mvc.perform(get("/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @RestController
    static class ProbeController {
        record Input(@NotBlank String name, @Min(1) int quantity) {}
        @PostMapping("/test/validation")
        Input validate(@Valid @RequestBody Input input) { return input; }
        @GetMapping("/test/failure")
        void failure() { throw new IllegalStateException("secret-password"); }
    }
}
