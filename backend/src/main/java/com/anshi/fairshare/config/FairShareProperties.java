package com.anshi.fairshare.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fairshare")
public record FairShareProperties(
        Auth auth,
        String publicBaseUrl,
        String maintenanceKey,
        Cloudinary cloudinary
) {
    public FairShareProperties {
        auth = auth == null ? new Auth(false) : auth;
        publicBaseUrl = publicBaseUrl == null ? "http://localhost:8080" : publicBaseUrl;
        maintenanceKey = maintenanceKey == null ? "change-me" : maintenanceKey;
        cloudinary = cloudinary == null ? new Cloudinary("", "", "") : cloudinary;
    }

    public record Auth(boolean devMode) {}
    public record Cloudinary(String cloudName, String apiKey, String apiSecret) {}
}

