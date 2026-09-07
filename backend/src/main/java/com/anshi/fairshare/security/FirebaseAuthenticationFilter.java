package com.anshi.fairshare.security;

import com.anshi.fairshare.api.ApiException;
import com.anshi.fairshare.config.FairShareProperties;
import com.anshi.fairshare.domain.AppUser;
import com.anshi.fairshare.security.FirebaseTokenVerifier.VerifiedIdentity;
import com.anshi.fairshare.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class FirebaseAuthenticationFilter extends OncePerRequestFilter {
    private final FairShareProperties properties;
    private final FirebaseTokenVerifier verifier;
    private final UserService userService;
    private final ObjectMapper objectMapper;

    public FirebaseAuthenticationFilter(FairShareProperties properties, FirebaseTokenVerifier verifier,
                                        UserService userService, ObjectMapper objectMapper) {
        this.properties = properties;
        this.verifier = verifier;
        this.userService = userService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator/") || path.startsWith("/v3/api-docs") || path.startsWith("/swagger-ui")
                || path.startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            VerifiedIdentity identity = properties.auth().devMode() ? devIdentity(request) : firebaseIdentity(request);
            AppUser user = userService.provision(identity);
            AuthenticatedUser principal = AuthenticatedUser.from(user);
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(principal, null, List.of()));
            chain.doFilter(request, response);
        } catch (ApiException exception) {
            response.setStatus(exception.status().value());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(), Map.of(
                    "type", "https://fairshare.app/problems/" + exception.code().toLowerCase(Locale.ROOT),
                    "title", exception.code(), "status", exception.status().value(),
                    "detail", exception.getMessage(), "code", exception.code()));
        }
    }

    private VerifiedIdentity devIdentity(HttpServletRequest request) {
        String email = request.getHeader("X-Dev-User");
        if (email == null || !email.contains("@")) {
            throw new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED, "DEV_USER_REQUIRED", "Set X-Dev-User to an email address");
        }
        String name = request.getHeader("X-Dev-Name");
        if (name == null || name.isBlank()) name = email.substring(0, email.indexOf('@'));
        return new VerifiedIdentity("dev:" + email.toLowerCase(Locale.ROOT), email, name);
    }

    private VerifiedIdentity firebaseIdentity(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new ApiException(org.springframework.http.HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "A Firebase bearer token is required");
        }
        return verifier.verify(authorization.substring(7));
    }
}

