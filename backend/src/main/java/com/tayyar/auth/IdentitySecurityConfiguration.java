package com.tayyar.auth;

import com.tayyar.user.UserRepository;

import jakarta.servlet.DispatcherType;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.*;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.*;
import org.springframework.security.web.context.*;
import org.springframework.security.web.csrf.*;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

import java.time.Clock;
import java.util.*;

@Configuration
@EnableMethodSecurity
@EnableScheduling
@EnableConfigurationProperties(IdentityProperties.class)
public class IdentitySecurityConfiguration {
    @Bean
    Clock identityClock() {
        return Clock.systemUTC();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new DelegatingPasswordEncoder(
                "bcrypt", Map.of("bcrypt", new BCryptPasswordEncoder(12)));
    }

    @Bean
    CsrfTokenRepository csrfRepository() {
        return new HttpSessionCsrfTokenRepository();
    }

    @Bean
    SecurityContextRepository securityContexts() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    SessionAuthenticationStrategy sessionAuthenticationStrategy(CsrfTokenRepository csrf) {
        return new CompositeSessionAuthenticationStrategy(
                List.of(
                        new ChangeSessionIdAuthenticationStrategy(),
                        new CsrfAuthenticationStrategy(csrf)));
    }

    @Bean
    SecurityFilterChain identitySecurity(
            HttpSecurity http,
            UserRepository users,
            IdentityProperties properties,
            Clock clock,
            SecurityErrorWriter errors,
            SecurityContextRepository contexts,
            CsrfTokenRepository csrf)
            throws Exception {
        http.securityContext(
                        context ->
                                context.securityContextRepository(contexts)
                                        .requireExplicitSave(true))
                .csrf(config -> config.csrfTokenRepository(csrf))
                .requestCache(cache -> cache.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .authorizeHttpRequests(
                        rules ->
                                rules.dispatcherTypeMatchers(DispatcherType.ERROR)
                                        .permitAll()
                                        .requestMatchers(
                                                HttpMethod.GET,
                                                "/api/v1/health",
                                                "/api/v1/auth/csrf")
                                        .permitAll()
                                        .requestMatchers(
                                                HttpMethod.POST,
                                                "/api/v1/auth/registrations",
                                                "/api/v1/auth/session")
                                        .permitAll()
                                        .requestMatchers(HttpMethod.GET, "/api/v1/users/me")
                                        .authenticated()
                                        .requestMatchers("/api/v1/admin/restaurant-applications/**", "/api/v1/admin/restaurants/**")
                                        .hasRole("ADMIN")
                                        .requestMatchers("/api/v1/restaurant-applications/**")
                                        .hasAnyRole("CUSTOMER", "ADMIN")
                                        .requestMatchers("/api/v1/restaurants/**")
                                        .hasAnyRole("RESTAURANT_OWNER", "ADMIN")
                                        .anyRequest()
                                        .denyAll())
                .exceptionHandling(
                        errorsConfig ->
                                errorsConfig
                                        .authenticationEntryPoint(
                                                (request, response, error) ->
                                                        errors.write(
                                                                request,
                                                                response,
                                                                401,
                                                                "AUTHENTICATION_REQUIRED",
                                                                "Authentication is required"))
                                        .accessDeniedHandler(
                                                (request, response, error) ->
                                                        errors.write(
                                                                request,
                                                                response,
                                                                403,
                                                                error instanceof CsrfException
                                                                        ? "CSRF_INVALID"
                                                                        : "ACCESS_DENIED",
                                                                error instanceof CsrfException
                                                                        ? "CSRF token is missing or"
                                                                              + " invalid"
                                                                        : "Access denied")))
                .logout(
                        logout ->
                                logout.logoutRequestMatcher(
                                                PathPatternRequestMatcher.withDefaults()
                                                        .matcher(
                                                                HttpMethod.DELETE,
                                                                "/api/v1/auth/session"))
                                        .invalidateHttpSession(true)
                                        .clearAuthentication(true)
                                        .deleteCookies("SESSION")
                                        .logoutSuccessHandler(
                                                (request, response, auth) ->
                                                        response.setStatus(204)))
                .addFilterAfter(
                        new SessionValidityFilter(users, properties, clock, errors),
                        SecurityContextHolderFilter.class);
        return http.build();
    }
}
