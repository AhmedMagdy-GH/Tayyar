package com.tayyar.auth;

import jakarta.servlet.http.*;

import org.springframework.security.authentication.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;

@Service
public class SessionLoginService {
    private final IdentityAuthenticationManager manager;
    private final SessionAuthenticationStrategy strategy;
    private final SecurityContextRepository contexts;

    public SessionLoginService(
            IdentityAuthenticationManager manager,
            SessionAuthenticationStrategy strategy,
            SecurityContextRepository contexts) {
        this.manager = manager;
        this.strategy = strategy;
        this.contexts = contexts;
    }

    public void login(
            LoginRequest input, HttpServletRequest request, HttpServletResponse response) {
        var authentication =
                manager.authenticate(
                        UsernamePasswordAuthenticationToken.unauthenticated(
                                input.email(), input.password()));
        strategy.onAuthentication(authentication, request, response);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
    }
}
