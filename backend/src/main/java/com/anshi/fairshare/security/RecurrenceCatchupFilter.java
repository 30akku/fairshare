package com.anshi.fairshare.security;

import com.anshi.fairshare.service.RecurringExpenseService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class RecurrenceCatchupFilter extends OncePerRequestFilter {
    private final RecurringExpenseService recurringExpenseService;
    public RecurrenceCatchupFilter(RecurringExpenseService recurringExpenseService) { this.recurringExpenseService = recurringExpenseService; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        recurringExpenseService.catchUpOncePerDay();
        chain.doFilter(request, response);
    }
}

