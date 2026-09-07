package com.anshi.fairshare.security;

import com.anshi.fairshare.api.ApiException;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class FirebaseTokenVerifier {
    private volatile boolean initialized;

    public VerifiedIdentity verify(String token) {
        initialize();
        try {
            FirebaseToken decoded = FirebaseAuth.getInstance().verifyIdToken(token, true);
            String email = decoded.getEmail();
            boolean verified = Boolean.TRUE.equals(decoded.isEmailVerified());
            if (email == null || !verified) {
                throw new ApiException(HttpStatus.FORBIDDEN, "EMAIL_NOT_VERIFIED", "Verify your email before using FairShare");
            }
            String name = decoded.getName() == null || decoded.getName().isBlank() ? email.substring(0, email.indexOf('@')) : decoded.getName();
            return new VerifiedIdentity(decoded.getUid(), email, name);
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN", "The authentication token is invalid or expired");
        }
    }

    private synchronized void initialize() {
        if (initialized) return;
        try {
            if (FirebaseApp.getApps().isEmpty()) {
                String encoded = System.getenv("FIREBASE_SERVICE_ACCOUNT_JSON");
                FirebaseOptions.Builder builder = FirebaseOptions.builder();
                if (encoded != null && !encoded.isBlank()) {
                    byte[] json;
                    try { json = Base64.getDecoder().decode(encoded); }
                    catch (IllegalArgumentException ignored) { json = encoded.getBytes(StandardCharsets.UTF_8); }
                    builder.setCredentials(GoogleCredentials.fromStream(new ByteArrayInputStream(json)));
                } else {
                    builder.setCredentials(GoogleCredentials.getApplicationDefault());
                }
                FirebaseApp.initializeApp(builder.build());
            }
            initialized = true;
        } catch (Exception exception) {
            throw new IllegalStateException("Firebase credentials are not configured", exception);
        }
    }

    public record VerifiedIdentity(String uid, String email, String displayName) {}
}

