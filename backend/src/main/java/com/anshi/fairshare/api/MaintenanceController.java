package com.anshi.fairshare.api;

import com.anshi.fairshare.api.ApiException;
import com.anshi.fairshare.config.FairShareProperties;
import com.anshi.fairshare.service.RecurringExpenseService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.ZoneOffset;

@RestController
@RequestMapping("/internal/maintenance")
public class MaintenanceController {
    private final FairShareProperties properties;
    private final RecurringExpenseService recurring;
    public MaintenanceController(FairShareProperties properties, RecurringExpenseService recurring) {
        this.properties = properties; this.recurring = recurring;
    }

    @PostMapping("/recurrences")
    RecurringExpenseService.ProcessResult process(@RequestHeader("X-Maintenance-Key") String key) {
        if (!constantTimeEquals(properties.maintenanceKey(), key))
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_MAINTENANCE_KEY", "Invalid maintenance key");
        return recurring.processDue(LocalDate.now(ZoneOffset.UTC));
    }

    private boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) return false;
        return java.security.MessageDigest.isEqual(expected.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                actual.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}

