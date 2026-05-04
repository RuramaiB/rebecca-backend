package com.example.taxbackend.service;

import com.example.taxbackend.repository.OAuthTokenRepository;
import com.example.taxbackend.repository.ArtistRepository;
import com.example.taxbackend.models.Artist;
import com.example.taxbackend.token.OAuthToken;
import com.example.taxbackend.user.Role;
import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeRequestUrl;
import com.google.api.client.googleapis.auth.oauth2.GoogleRefreshTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.util.store.MemoryDataStoreFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.Channel;
import com.google.api.services.youtube.model.ChannelListResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.taxbackend.enums.AuthStatus;
import com.example.taxbackend.models.GoogleUserInfo;
import com.google.api.client.json.JsonObjectParser;
import com.google.api.client.http.HttpRequestInitializer;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * Enhanced OAuth Service - Automatic Artist Registration
 *
 * Academic Note: This service automatically creates artist profiles
 * on successful OAuth authorization, eliminating manual registration
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OAuthService {

    private final HttpTransport httpTransport;
    private final JsonFactory jsonFactory;
    private final OAuthTokenRepository tokenRepository;
    private final ArtistRepository artistRepository;
    private final YouTube.Builder youtubeBuilder;
    private final TaxService taxService;

    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String clientId;

    @Value("${spring.security.oauth2.client.registration.google.client-secret}")
    private String clientSecret;

    private static final String REDIRECT_URI = "http://localhost:8080/oauth2/callback/google";

    // Required scopes for full functionality
    private static final List<String> SCOPES = Arrays.asList(
            "https://www.googleapis.com/auth/youtube.readonly",
            "https://www.googleapis.com/auth/yt-analytics.readonly",
            "https://www.googleapis.com/auth/adsense.readonly",
            "openid",
            "profile",
            "email");

    /**
     * Generate OAuth authorization URL
     * Step 1 of OAuth flow: Redirect user to Google consent screen
     *
     * @return Authorization URL for user to visit
     */
    public String getAuthorizationUrl() throws IOException {
        GoogleAuthorizationCodeFlow flow = createFlow();

        GoogleAuthorizationCodeRequestUrl authorizationUrl = flow
                .newAuthorizationUrl()
                .setRedirectUri(REDIRECT_URI)
                .setAccessType("offline") // Request refresh token
                .setApprovalPrompt("force"); // Force consent screen

        log.info("Generated authorization URL");
        return authorizationUrl.build();
    }

    /**
     * Exchange authorization code and auto-register/login artist
     * Step 2 of OAuth flow: Exchange code and create/update artist profile
     *
     * @param code Authorization code from Google
     * @return AuthResult with artist and token info
     */
    @Transactional
    public AuthResult exchangeCodeAndRegister(String code) {
        try {
            GoogleAuthorizationCodeFlow flow = createFlow();

            GoogleTokenResponse response = flow
                    .newTokenRequest(code)
                    .setRedirectUri(REDIRECT_URI)
                    .execute();

            GoogleIdToken idToken = response.parseIdToken();
            GoogleIdToken.Payload payload = idToken.getPayload();

            String googleAccountId = payload.getSubject();
            String email = payload.getEmail();
            String name = (String) payload.get("name");

            Optional<Artist> existingArtist = artistRepository.findByGoogleAccountId(googleAccountId);

            Artist artist;
            boolean isNewArtist;

            if (existingArtist.isPresent()) {
                // 🔹 LOGIN FLOW
                artist = existingArtist.get();
                artist.setAuthStatus(AuthStatus.AUTHENTICATED);
                artist.setLastSyncedAt(LocalDateTime.now());

                // Update channel info if missing or on every login to keep it fresh
                YouTubeChannelInfo channelInfo = fetchYouTubeChannelInfo(response.getAccessToken());
                if (channelInfo.getChannelId() != null) {
                    artist.setYoutubeChannelId(channelInfo.getChannelId());
                    artist.setYoutubeChannelTitle(channelInfo.getChannelTitle());
                    artist.setYoutubeChannelPublishedAt(channelInfo.getChannelPublishedAt());
                }

                isNewArtist = false;

            } else {
                // 🔹 REGISTRATION FLOW
                YouTubeChannelInfo channelInfo = fetchYouTubeChannelInfo(response.getAccessToken());

                artist = Artist.builder()
                        .googleAccountId(googleAccountId)
                        .email(email)
                        .name(name)
                        .youtubeChannelId(channelInfo.getChannelId())
                        .youtubeChannelTitle(channelInfo.getChannelTitle())
                        .youtubeChannelPublishedAt(channelInfo.getChannelPublishedAt())
                        .authorizedAt(LocalDateTime.now())
                        .lastSyncedAt(LocalDateTime.now())
                        .youtubeAuthorized(true)
                        .analyticsAuthorized(true)
                        .adsenseAuthorized(false)
                        .role(Role.ARTIST)
                        .authStatus(AuthStatus.AUTHENTICATED)
                        .build();

                isNewArtist = true;
            }

            artist = artistRepository.save(artist);

            // 🔹 TRIGGER DST REGISTRATION & COMPLIANCE
            taxService.updateComplianceStatus(artist.getId());

            OAuthToken token = tokenRepository.findByArtistId(artist.getId())
                    .orElse(OAuthToken.builder().artistId(artist.getId()).build());

            token.setAccessToken(response.getAccessToken());
            if (response.getRefreshToken() != null && !response.getRefreshToken().isBlank()) {
                token.setRefreshToken(response.getRefreshToken());
            }
            token.setTokenType(response.getTokenType());
            token.setIssuedAt(LocalDateTime.now());
            token.setExpiresAt(LocalDateTime.now().plusSeconds(response.getExpiresInSeconds()));
            token.setScopes(SCOPES);

            tokenRepository.save(token);

            return AuthResult.builder()
                    .artist(artist)
                    .token(token)
                    .isNewRegistration(isNewArtist)
                    .message(isNewArtist
                            ? "Artist registration successful"
                            : "Login successful")
                    .build();

        } catch (Exception e) {
            log.error("OAuth authentication failed", e);
            return AuthResult.builder()
                    .message("Authentication failed")
                    .build();
        }
    }

    /**
     * Fetch YouTube channel information
     * Called during auto-registration to populate channel details
     */
    private YouTubeChannelInfo fetchYouTubeChannelInfo(String accessToken) {
        try {
            // Create temporary credential
            Credential credential = new Credential.Builder(BearerToken.authorizationHeaderAccessMethod())
                    .setTransport(httpTransport)
                    .setJsonFactory(jsonFactory)
                    .build()
                    .setAccessToken(accessToken);

            YouTube youtube = youtubeBuilder.setHttpRequestInitializer(credential).build();

            // Fetch channel owned by authenticated user
            YouTube.Channels.List request = youtube.channels()
                    .list(Arrays.asList("snippet", "contentDetails"))
                    .setMine(true);

            ChannelListResponse channelResponse = request.execute();

            if (channelResponse.getItems() != null && !channelResponse.getItems().isEmpty()) {
                Channel channel = channelResponse.getItems().get(0);

                String channelId = channel.getId();
                String channelTitle = channel.getSnippet().getTitle();

                log.info("Fetched YouTube channel: {} (ID: {})", channelTitle, channelId);

                return YouTubeChannelInfo.builder()
                        .channelId(channelId)
                        .channelTitle(channelTitle)
                        .channelPublishedAt(java.time.LocalDateTime.ofInstant(
                                java.time.Instant.ofEpochMilli(channel.getSnippet().getPublishedAt().getValue()),
                                java.time.ZoneId.systemDefault()))
                        .build();
            } else {
                log.warn("No YouTube channel found for user");
                return YouTubeChannelInfo.builder()
                        .channelId(null)
                        .channelTitle("No Channel")
                        .build();
            }

        } catch (IOException e) {
            log.error("Error fetching YouTube channel info: {}", e.getMessage());
            return YouTubeChannelInfo.builder()
                    .channelId(null)
                    .channelTitle("Error Fetching Channel")
                    .build();
        }
    }

    /**
     * Get valid credential for API calls
     * Automatically refreshes token if expired
     *
     * @param artistId Artist identifier
     * @return Google Credential for API authentication
     */
    public Credential getCredential(String artistId) throws IOException {
        OAuthToken token = tokenRepository.findByArtistId(artistId)
                .orElseThrow(() -> new IllegalStateException("Artist not authorized: " + artistId));

        // Check if token is expired
        if (LocalDateTime.now().isAfter(token.getExpiresAt().minusMinutes(5))) {
            log.info("Token expired, refreshing for artist: {}", artistId);
            token = refreshToken(token);
        }

        // Create credential from stored token
        GoogleAuthorizationCodeFlow flow = createFlow();
        Credential credential = flow.loadCredential(artistId);

        if (credential == null) {
            // Create new credential from stored token
            credential = new Credential.Builder(BearerToken.authorizationHeaderAccessMethod())
                    .setTransport(httpTransport)
                    .setJsonFactory(jsonFactory)
                    .setTokenServerUrl(new GenericUrl(flow.getTokenServerEncodedUrl()))
                    .setClientAuthentication(flow.getClientAuthentication())
                    .build()
                    .setAccessToken(token.getAccessToken())
                    .setRefreshToken(token.getRefreshToken())
                    .setExpirationTimeMilliseconds(
                            token.getExpiresAt().atZone(java.time.ZoneId.systemDefault())
                                    .toInstant().toEpochMilli());
        }

        return credential;
    }

    /**
     * Refresh expired access token using refresh token
     *
     * @param token Current token (may be expired)
     * @return Updated token with new access token
     */
    private OAuthToken refreshToken(OAuthToken token) throws IOException {
        if (token.getRefreshToken() == null || token.getRefreshToken().isBlank()) {
            throw new IllegalStateException("No refresh token available");
        }

        GoogleTokenResponse response = new GoogleRefreshTokenRequest(
                httpTransport,
                jsonFactory,
                token.getRefreshToken(),
                clientId,
                clientSecret)
                .execute();

        // Update token
        token.setAccessToken(response.getAccessToken());
        token.setIssuedAt(LocalDateTime.now());
        token.setExpiresAt(LocalDateTime.now().plusSeconds(response.getExpiresInSeconds()));

        // Refresh token may be rotated
        if (response.getRefreshToken() != null) {
            token.setRefreshToken(response.getRefreshToken());
        }

        tokenRepository.save(token);
        log.info("Refreshed token for artist: {}", token.getArtistId());

        return token;
    }

    /**
     * Create Google Authorization Code Flow
     * Configures OAuth client with credentials and scopes
     */
    private GoogleAuthorizationCodeFlow createFlow() throws IOException {
        return new GoogleAuthorizationCodeFlow.Builder(
                httpTransport,
                jsonFactory,
                clientId,
                clientSecret,
                SCOPES)
                .setDataStoreFactory(new MemoryDataStoreFactory())
                .setAccessType("offline")
                .build();
    }

    /**
     * Revoke artist authorization and logout
     * Removes stored tokens
     *
     * @param artistId Artist identifier
     */
    public void logout(String artistId) {
        tokenRepository.deleteByArtistId(artistId);
        log.info("Logged out artist: {}", artistId);
    }

    /**
     * Check if artist is currently authenticated
     */
    public boolean isAuthenticated(String artistId) {
        Optional<OAuthToken> token = tokenRepository.findByArtistId(artistId);
        return token.isPresent() &&
                LocalDateTime.now().isBefore(token.get().getExpiresAt());
    }

    /**
     * Get artist by Google account ID
     */
    public Optional<Artist> getArtistByGoogleId(String googleAccountId) {
        return artistRepository.findByGoogleAccountId(googleAccountId);
    }

    /**
     * Get authenticated Google user information by email
     * Resolves artist internally and validates ownership
     *
     * @param email authenticated user's email
     * @return GoogleUserInfo
     */

    /**
     * Get authenticated Google user information by email
     */
    public GoogleUserInfo getAuthenticatedUserInfoByEmail(String identifier) throws IOException {
        String artistID = identifier;

        // If identifier is an email, look up the artist ID
        if (identifier.contains("@")) {
            Artist artist = artistRepository.findByEmail(identifier)
                    .orElseThrow(() -> new IllegalArgumentException("Artist not found for email: " + identifier));
            artistID = artist.getId();
        }

        // 2️⃣ Get credential (handles refresh automatically)
        Credential credential = getCredential(artistID);

        // 3️⃣ Create request factory WITH JSON parser ✅
        HttpRequestInitializer initializer = request -> {
            credential.initialize(request);
            request.setParser(new JsonObjectParser(jsonFactory));
        };

        var requestFactory = httpTransport.createRequestFactory(initializer);

        GenericUrl userInfoUrl = new GenericUrl("https://openidconnect.googleapis.com/v1/userinfo");

        var request = requestFactory.buildGetRequest(userInfoUrl);

        var response = request.execute();

        @SuppressWarnings("unchecked")
        var userInfo = response.parseAs(java.util.Map.class);

        String returnedEmail = (String) userInfo.get("email");

        String role = "User";
        Optional<Artist> artist = artistRepository.findById(artistID);
        if (artist.isPresent()) {
            role = artist.get().getRole().name();
        }

        return GoogleUserInfo.builder()
                .googleAccountId((String) userInfo.get("sub"))
                .email(returnedEmail)
                .name((String) userInfo.get("name"))
                .picture((String) userInfo.get("picture"))
                .emailVerified(Boolean.TRUE.equals(userInfo.get("email_verified")))
                .role(role)
                .build();
    }

    // ========================================================================
    // Inner Classes
    // ========================================================================

    /**
     * YouTube Channel Info DTO
     */
    @lombok.Data
    @lombok.Builder
    private static class YouTubeChannelInfo {
        private String channelId;
        private String channelTitle;
        private java.time.LocalDateTime channelPublishedAt;
    }

    /**
     * Authentication Result DTO
     * Contains artist info and authentication status
     */
    @lombok.Data
    @lombok.Builder
    public static class AuthResult {
        private Artist artist;
        private OAuthToken token;
        private boolean isNewRegistration;
        private String message;
        private String redirectUrl;
    }
}