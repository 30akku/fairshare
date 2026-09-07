package com.anshi.fairshare.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class FixedWindowRateLimitFilter extends OncePerRequestFilter {
    private final Map<String, Window> windows = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public FixedWindowRateLimitFilter(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser user) {
            boolean read = request.getMethod().equals("GET");
            int limit = read ? 300 : 60;
            long minute = Instant.now().getEpochSecond() / 60;
            String key = user.id() + ":" + (read ? "read" : "write");
            Window window = windows.compute(key, (ignored, current) -> current == null || current.minute != minute ? new Window(minute) : current);
            if (window.count.incrementAndGet() > limit) {
                response.setStatus(429);
                response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                response.setHeader("Retry-After", "60");
                objectMapper.writeValue(response.getOutputStream(), Map.of("title", "RATE_LIMITED", "status", 429,
                        "detail", "Too many requests; try again in a minute", "code", "RATE_LIMITED"));
                return;
            }
        }
        chain.doFilter(request, response);
    }

    private static final class Window {
        private final long minute;
        private final AtomicInteger count = new AtomicInteger();
        private Window(long minute) { this.minute = minute; }
    }
}

