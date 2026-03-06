package com.example.taxbackend.service;


import com.example.taxbackend.models.RevenueRecord;
import com.example.taxbackend.repository.RevenueRepository;
import com.google.api.client.auth.oauth2.Credential;

import com.google.api.services.youtubeAnalytics.v2.YouTubeAnalytics;
import com.google.api.services.youtubeAnalytics.v2.model.QueryResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * YouTube Analytics Service - Fetches estimated revenue data
 * Uses YouTube Analytics API
 *
 * Academic Note: The YouTube Analytics API provides ESTIMATED revenue.
 * This is NOT the same as actual AdSense payments, which come from AdSense API.
 *
 * Key Metrics Available:
 * - estimatedRevenue: Estimated earnings from ads
 * - views: Video views
 * - averageViewDuration: Watch time
 * - subscribersGained/Lost: Channel growth
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class YouTubeAnalyticsService {

    private final YouTubeAnalytics.Builder analyticsBuilder;
    private final OAuthService oauthService;
    private final RevenueRepository revenueRepository;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * Fetch monthly revenue for a channel
     * Returns estimated revenue from YouTube Analytics
     *
     * @param artistId Artist identifier
     * @param channelId YouTube channel ID (format: channel==UCxxxxxx)
     * @param startDate Start date for revenue period
     * @param endDate End date for revenue period
     * @return List of RevenueRecord entities
     */
    public List<RevenueRecord> fetchMonthlyRevenue(
            String artistId,
            String channelId,
            LocalDate startDate,
            LocalDate endDate) throws IOException {

        Credential credential = oauthService.getCredential(artistId);
        YouTubeAnalytics analytics = analyticsBuilder
                .setHttpRequestInitializer(credential)
                .build();

        // Query for channel-level revenue
        // Documentation: https://developers.google.com/youtube/analytics/metrics
        YouTubeAnalytics.Reports.Query request = analytics.reports()
                .query()
                .setIds("channel==" + channelId) // Filter by channel
                .setStartDate(startDate.format(DATE_FORMATTER))
                .setEndDate(endDate.format(DATE_FORMATTER))
                .setMetrics("estimatedRevenue,views,impressions") // Revenue metrics
                .setDimensions("month") // Group by month
                .setSort("month"); // Sort chronologically

        QueryResponse response = request.execute();

        log.info("Fetched revenue data: {} rows",
                response.getRows() != null ? response.getRows().size() : 0);

        return parseRevenueResponse(response, artistId, null);
    }

    /**
     * Fetch video-level revenue
     * Provides revenue breakdown per video
     *
     * @param artistId Artist identifier
     * @param channelId YouTube channel ID
     * @param videoId Specific video ID (optional, null for all videos)
     * @param startDate Start date
     * @param endDate End date
     * @return List of RevenueRecord entities per video
     */
    public List<RevenueRecord> fetchVideoRevenue(
            String artistId,
            String channelId,
            String videoId,
            LocalDate startDate,
            LocalDate endDate) throws IOException {

        Credential credential = oauthService.getCredential(artistId);
        YouTubeAnalytics analytics = analyticsBuilder
                .setHttpRequestInitializer(credential)
                .build();

        // Build query
        YouTubeAnalytics.Reports.Query request = analytics.reports()
                .query()
                .setIds("channel==" + channelId)
                .setStartDate(startDate.format(DATE_FORMATTER))
                .setEndDate(endDate.format(DATE_FORMATTER))
                .setMetrics("estimatedRevenue,views,estimatedMinutesWatched")
                .setDimensions("video") // Group by video
                .setSort("-estimatedRevenue") // Sort by revenue descending
                .setMaxResults(50);

        // Filter by specific video if provided
        if (videoId != null) {
            request.setFilters("video==" + videoId);
        }

        QueryResponse response = request.execute();

        log.info("Fetched video revenue data: {} videos",
                response.getRows() != null ? response.getRows().size() : 0);

        return parseVideoRevenueResponse(response, artistId, startDate, endDate);
    }

    /**
     * Fetch daily revenue for detailed tracking
     * Useful for monitoring recent performance
     */
    public List<RevenueRecord> fetchDailyRevenue(
            String artistId,
            String channelId,
            LocalDate startDate,
            LocalDate endDate) throws IOException {

        Credential credential = oauthService.getCredential(artistId);
        YouTubeAnalytics analytics = analyticsBuilder
                .setHttpRequestInitializer(credential)
                .build();

        YouTubeAnalytics.Reports.Query request = analytics.reports()
                .query()
                .setIds("channel==" + channelId)
                .setStartDate(startDate.format(DATE_FORMATTER))
                .setEndDate(endDate.format(DATE_FORMATTER))
                .setMetrics("estimatedRevenue,views,estimatedMinutesWatched,averageViewDuration")
                .setDimensions("day") // Daily breakdown
                .setSort("day");

        QueryResponse response = request.execute();

        return parseRevenueResponse(response, artistId, null);
    }

    /**
     * Parse channel-level revenue response
     */
    private List<RevenueRecord> parseRevenueResponse(
            QueryResponse response,
            String artistId,
            String videoId) {

        List<RevenueRecord> records = new ArrayList<>();

        if (response.getRows() == null || response.getRows().isEmpty()) {
            log.warn("No revenue data available for artist: {}", artistId);
            return records;
        }

        // Column headers tell us the order of data
        List<String> columnHeaders = new ArrayList<>();
        response.getColumnHeaders().forEach(header ->
                columnHeaders.add(header.getName()));

        int revenueIndex = columnHeaders.indexOf("estimatedRevenue");
        int viewsIndex = columnHeaders.indexOf("views");
        int dateIndex = columnHeaders.indexOf("month") != -1 ?
                columnHeaders.indexOf("month") : columnHeaders.indexOf("day");

        for (List<Object> row : response.getRows()) {
            try {
                // Parse revenue (in micros - divide by 1,000,000)
                Double revenue = row.get(revenueIndex) != null ?
                        ((Number) row.get(revenueIndex)).doubleValue() / 1_000_000.0 : 0.0;

                Long views = row.get(viewsIndex) != null ?
                        ((Number) row.get(viewsIndex)).longValue() : 0L;

                String dateStr = row.get(dateIndex).toString();
                LocalDate date = LocalDate.parse(dateStr);

                RevenueRecord record = RevenueRecord.builder()
                        .artistId(artistId)
                        .videoId(videoId)
                        .periodStart(date.atStartOfDay())
                        .periodEnd(date.plusDays(1).atStartOfDay())
                        .periodType("DAILY")
                        .source(RevenueRecord.RevenueSource.YOUTUBE_ANALYTICS)
                        .estimatedRevenue(revenue)
                        .totalRevenue(revenue)
                        .currency("USD")
                        .verified(false) // Analytics data is estimated
                        .dataQuality("MEDIUM")
                        .fetchedAt(LocalDateTime.now())
                        .build();

                records.add(record);

            } catch (Exception e) {
                log.error("Error parsing revenue row: {}", row, e);
            }
        }

        // Save to database
        revenueRepository.saveAll(records);
        log.info("Saved {} revenue records", records.size());

        return records;
    }

    /**
     * Parse video-level revenue response
     */
    private List<RevenueRecord> parseVideoRevenueResponse(
            QueryResponse response,
            String artistId,
            LocalDate startDate,
            LocalDate endDate) {

        List<RevenueRecord> records = new ArrayList<>();

        if (response.getRows() == null) {
            return records;
        }

        List<String> columnHeaders = new ArrayList<>();
        response.getColumnHeaders().forEach(header ->
                columnHeaders.add(header.getName()));

        int videoIndex = columnHeaders.indexOf("video");
        int revenueIndex = columnHeaders.indexOf("estimatedRevenue");
        int viewsIndex = columnHeaders.indexOf("views");

        for (List<Object> row : response.getRows()) {
            try {
                String videoId = row.get(videoIndex).toString();

                Double revenue = row.get(revenueIndex) != null ?
                        ((Number) row.get(revenueIndex)).doubleValue() / 1_000_000.0 : 0.0;

                Long views = row.get(viewsIndex) != null ?
                        ((Number) row.get(viewsIndex)).longValue() : 0L;

                RevenueRecord record = RevenueRecord.builder()
                        .artistId(artistId)
                        .videoId(videoId)
                        .periodStart(startDate.atStartOfDay())
                        .periodEnd(endDate.plusDays(1).atStartOfDay())
                        .periodType("CUSTOM")
                        .source(RevenueRecord.RevenueSource.YOUTUBE_ANALYTICS)
                        .estimatedRevenue(revenue)
                        .totalRevenue(revenue)
                        .currency("USD")
                        .verified(false)
                        .dataQuality("MEDIUM")
                        .fetchedAt(LocalDateTime.now())
                        .build();

                records.add(record);

            } catch (Exception e) {
                log.error("Error parsing video revenue row: {}", row, e);
            }
        }

        revenueRepository.saveAll(records);
        return records;
    }
}
