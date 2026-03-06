package com.example.taxbackend.config;

import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.adsense.v2.Adsense;
import com.google.api.services.youtubeAnalytics.v2.YouTubeAnalytics;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Google API Configuration
 * Sets up HTTP transport and JSON factory for Google API clients
 *
 * Academic Note: This configuration establishes the foundation for
 * communicating with Google's REST APIs using their Java client libraries.
 */
@Configuration
public class GoogleApiConfig {

    @Value("${google.api.application-name}")
    private String applicationName;

    /**
     * JSON Factory for parsing Google API responses
     * Uses Gson library for JSON serialization/deserialization
     */
    @Bean
    public JsonFactory jsonFactory() {
        return GsonFactory.getDefaultInstance();
    }

    /**
     * HTTP Transport for making API requests
     * Uses Google's NetHttpTransport with automatic retry and exponential backoff
     *
     * @throws Exception if transport initialization fails
     */
    @Bean
    public HttpTransport httpTransport() throws Exception {
        return GoogleNetHttpTransport.newTrustedTransport();
    }

    /**
     * YouTube Data API v3 Client Builder
     * This is a prototype bean - each request gets a new instance
     * with fresh credentials
     *
     * Usage: Fetch channel info, video lists, video statistics
     *
     * @return YouTube.Builder for creating authenticated YouTube client
     */
    @Bean
    public YouTube.Builder youtubeBuilder(HttpTransport httpTransport, JsonFactory jsonFactory) {
        return new YouTube.Builder(httpTransport, jsonFactory, null)
                .setApplicationName(applicationName);
    }

    /**
     * YouTube Analytics API Client Builder
     * Used for fetching revenue and advanced metrics
     *
     * Usage: Fetch estimatedRevenue, views over time, earnings per video
     *
     * @return YouTubeAnalytics.Builder for creating authenticated client
     */
    @Bean
    public YouTubeAnalytics.Builder youtubeAnalyticsBuilder(
            HttpTransport httpTransport,
            JsonFactory jsonFactory) {
        return new YouTubeAnalytics.Builder(httpTransport, jsonFactory, null)
                .setApplicationName(applicationName);
    }

    /**
     * AdSense API Client Builder
     * Used for fetching exact ad revenue from AdSense account
     *
     * Usage: Fetch adRevenue, impressions, clicks, RPM
     * Note: Requires AdSense account linked to YouTube channel
     *
     * @return Adsense.Builder for creating authenticated client
     */
    @Bean
    public Adsense.Builder adsenseBuilder(
            HttpTransport httpTransport,
            JsonFactory jsonFactory) {
        return new Adsense.Builder(httpTransport, jsonFactory, null)
                .setApplicationName(applicationName);
    }
}