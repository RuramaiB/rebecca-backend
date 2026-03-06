package com.example.taxbackend.controller;

import com.example.taxbackend.models.GoogleUserInfo;
import com.example.taxbackend.service.OAuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * OAuth Controller - Google OAuth2 Authentication
 * Handles the complete OAuth flow with automatic artist registration
 */
@RestController
@RequestMapping("/oauth2")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "http://localhost:3000") // Adjust to match your frontend URL
public class AuthController {

    private final OAuthService oAuthService;


    @GetMapping("/login")
    public ResponseEntity<Map<String, String>> initiateLogin() {
        try {
            String authorizationUrl = oAuthService.getAuthorizationUrl();

            Map<String, String> response = new HashMap<>();
            response.put("instructions", "User will be automatically registered or logged in");
            response.put("authorizationUrl", authorizationUrl);
            response.put("message", "Redirect user to authorizationUrl to sign in with Google");
            log.info("Authorization URL generated successfully");

            return ResponseEntity.ok(response);

        } catch (IOException e) {
            log.error("Error generating authorization URL: {}", e.getMessage());

            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", "Failed to initiate OAuth flow");
            errorResponse.put("message", e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    @GetMapping("/callback/google")
    public ResponseEntity<OAuthService.AuthResult> handleCallback(
            @RequestParam(name = "code", required = false) String code,
            @RequestParam(name = "error", required = false) String error,
            @RequestParam(name = "scope", required = false) String scope,
            HttpServletResponse response
    ) {
        // Handle OAuth error (user denied access)
        if (error != null) {
            log.warn("OAuth authorization denied by user: {}", error);
            return ResponseEntity.ok(OAuthService.AuthResult
                    .builder()
                            .redirectUrl("http://localhost:3000/login?error=access_denied")
                    .build()
            );
        }

        // Validate authorization code
        if (code == null || code.isEmpty()) {
            log.error("No authorization code received in callback");
            return ResponseEntity.ok(OAuthService.AuthResult
                    .builder()
                    .redirectUrl("http://localhost:3000/login?error=invalid-callback")
                    .build()
            );
        }

        try {
            // Exchange code and auto-register/login artist
            OAuthService.AuthResult authResult = oAuthService.exchangeCodeAndRegister(code);

            log.info("Authentication successful for artist: {} (ID: {})",
                    authResult.getArtist().getName(),
                    authResult.getArtist().getId());
            if (authResult.getToken() != null) {
                response.sendRedirect("http://localhost:3000/auth/login?token=" + authResult.getToken() + "&role=" +authResult.getArtist().getRole());
                return ResponseEntity.ok(
                        OAuthService.AuthResult
                                .builder()
                                .token(authResult.getToken())
                                .redirectUrl("http://localhost:3000/admin/")
                                .message(authResult.getMessage())
                                .artist(authResult.getArtist())
                                .isNewRegistration(authResult.isNewRegistration())

                                .build());
            } else {
                response.sendRedirect("http://localhost:3000");
                return ResponseEntity.ok(
                        OAuthService.AuthResult
                                .builder()
                                .redirectUrl("http://localhost:3000")
                                .message("Authentication failed")
                                .build());
            }



        } catch (Exception e) {
            log.error("Unexpected error during authentication: {}", e.getMessage(), e);
            return ResponseEntity.ok(OAuthService.AuthResult
                    .builder()
                            .message("Unexpected error")
                    .redirectUrl("http://localhost:3000/login?error=invalid-callback")
                    .build()
            );
        }
    }

    @GetMapping("/status/{artistId}")
    public ResponseEntity<Map<String, Object>> checkAuthStatus(@PathVariable String artistId) {
        boolean isAuthenticated = oAuthService.isAuthenticated(artistId);

        Map<String, Object> response = new HashMap<>();
        response.put("authenticated", isAuthenticated);
        response.put("artistId", artistId);
        response.put("message", isAuthenticated ?
                "Artist is authenticated" :
                "Artist is not authenticated or token expired");

        return ResponseEntity.ok(response);
    }

    /**
     * Logout endpoint
     * Revokes OAuth tokens and logs out artist
     *
     * POST /api/auth/logout/{artistId}
     *
     * Response:
     * {
     *   "success": true,
     *   "message": "Logged out successfully"
     * }
     */
    @PostMapping("/logout/{artistId}")
    public ResponseEntity<Map<String, Object>> logout(@PathVariable String artistId) {
        try {
            oAuthService.logout(artistId);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Logged out successfully");

            log.info("Artist logged out: {}", artistId);
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error during logout: {}", e.getMessage());

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", "Logout failed");
            errorResponse.put("message", e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * Get artist profile by Google account ID
     * Useful for frontend to retrieve artist info after OAuth
     *
     * GET /api/auth/artist?googleId=xxx
     *
     * Response:
     * {
     *   "found": true,
     *   "artist": { ... artist object ... }
     * }
     */
    @GetMapping("/artist")
    public ResponseEntity<Map<String, Object>> getArtistByGoogleId(
            @RequestParam String googleId
    ) {
        return oAuthService.getArtistByGoogleId(googleId)
                .map(artist -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("found", true);
                    response.put("artist", artist);
                    return ResponseEntity.ok(response);
                })
                .orElseGet(() -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("found", false);
                    response.put("message", "Artist not found");
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
                });
    }
    @GetMapping("/get-profile-by-/{artistID}")
    public GoogleUserInfo getAuthenticatedUserInfoByEmail(@PathVariable String artistID) throws IOException {
        return oAuthService.getAuthenticatedUserInfoByEmail(artistID);
    }
}