package com.anshi.fairshare.service;

import com.anshi.fairshare.api.ApiException;
import com.anshi.fairshare.api.ApiModels.MediaSignatureResponse;
import com.anshi.fairshare.config.FairShareProperties;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Service
public class MediaService {
    private final FairShareProperties properties;
    public MediaService(FairShareProperties properties) { this.properties = properties; }

    public MediaSignatureResponse signature(UUID userId) {
        FairShareProperties.Cloudinary cloudinary = properties.cloudinary();
        if (cloudinary.cloudName().isBlank() || cloudinary.apiKey().isBlank() || cloudinary.apiSecret().isBlank())
            throw ApiException.conflict("MEDIA_NOT_CONFIGURED", "Profile image storage is not configured");
        long timestamp = Instant.now().getEpochSecond();
        String folder = "fairshare/profiles";
        String publicId = userId.toString();
        String parameters = "folder=" + folder + "&overwrite=true&public_id=" + publicId + "&timestamp=" + timestamp;
        return new MediaSignatureResponse(cloudinary.cloudName(), cloudinary.apiKey(), timestamp,
                sha1(parameters + cloudinary.apiSecret()), folder, publicId);
    }

    public void delete(String publicId) {
        FairShareProperties.Cloudinary cloudinary = properties.cloudinary();
        if (publicId == null || publicId.isBlank() || cloudinary.cloudName().isBlank() || cloudinary.apiKey().isBlank() || cloudinary.apiSecret().isBlank()) return;
        try {
            long timestamp = Instant.now().getEpochSecond();
            String signature = sha1("public_id=" + publicId + "&timestamp=" + timestamp + cloudinary.apiSecret());
            String body = "public_id=" + encode(publicId) + "&timestamp=" + timestamp + "&api_key=" + encode(cloudinary.apiKey()) + "&signature=" + signature;
            HttpRequest request = HttpRequest.newBuilder(URI.create("https://api.cloudinary.com/v1_1/" + cloudinary.cloudName() + "/image/destroy"))
                    .header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) { /* Deletion is best effort and can be retried from Cloudinary. */ }
    }

    private String sha1(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception exception) { throw new IllegalStateException(exception); }
    }
    private String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
