package com.example.taxbackend.controller;
import com.example.taxbackend.models.RevenueRecord;
import com.example.taxbackend.service.AdSenseService;
import com.example.taxbackend.service.YouTubeAnalyticsService;
import com.example.taxbackend.service.YouTubeDataService;
import com.google.api.services.youtube.model.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.example.taxbackend.models.Video;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/youtube")
@Slf4j
@RequiredArgsConstructor
public class YouTubeController {

    private final YouTubeDataService youtubeDataService;
    private final YouTubeAnalyticsService analyticsService;
    private final AdSenseService adsenseService;

    /**
     * Get channel information
     *
     * GET /api/youtube/channel?artistId={artistId}
     */
    @GetMapping("/channel")
    public ResponseEntity<?> getChannel(@RequestParam String artistId) {
        try {
            Channel channel = youtubeDataService.getChannel(artistId);

            Map<String, Object> response = new HashMap<>();
            response.put("channelId", channel.getId());
            response.put("title", channel.getSnippet().getTitle());
            response.put("description", channel.getSnippet().getDescription());
            response.put("subscriberCount", channel.getStatistics().getSubscriberCount());
            response.put("videoCount", channel.getStatistics().getVideoCount());
            response.put("viewCount", channel.getStatistics().getViewCount());

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error fetching channel", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get all videos from channel with statistics
     *
     * GET /api/youtube/videos/{channelId}?artistId={artistId}
     */
    @GetMapping("/videos/{channelId}")
    public ResponseEntity<?> getVideos(
            @PathVariable String channelId,
            @RequestParam String artistId) {

        try {
            List<Video> videos = youtubeDataService.fetchChannelVideos(artistId, channelId);

            Map<String, Object> response = new HashMap<>();
            response.put("channelId", channelId);
            response.put("videoCount", videos.size());
            response.put("videos", videos);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error fetching videos", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get monthly revenue (YouTube Analytics)
     *
     * GET /api/youtube/revenue/{channelId}?artistId={artistId}&startDate=2024-01-01&endDate=2024-12-31
     */
    @GetMapping("/revenue/{channelId}")
    public ResponseEntity<?> getRevenue(
            @PathVariable String channelId,
            @RequestParam String artistId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        try {
            List<RevenueRecord> revenue = analyticsService.fetchMonthlyRevenue(
                    artistId, channelId, startDate, endDate);

            double totalRevenue = revenue.stream()
                    .mapToDouble(r -> r.getTotalRevenue() != null ? r.getTotalRevenue() : 0.0)
                    .sum();

            Map<String, Object> response = new HashMap<>();
            response.put("channelId", channelId);
            response.put("period", Map.of("start", startDate, "end", endDate));
            response.put("totalRevenue", totalRevenue);
            response.put("currency", "USD");
            response.put("source", "YouTube Analytics (Estimated)");
            response.put("records", revenue);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error fetching revenue", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get video-level revenue breakdown
     *
     * GET /api/youtube/revenue/{channelId}/videos?artistId={artistId}&startDate=2024-01-01&endDate=2024-12-31
     */
    @GetMapping("/revenue/{channelId}/videos")
    public ResponseEntity<?> getVideoRevenue(
            @PathVariable String channelId,
            @RequestParam String artistId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String videoId) {

        try {
            List<RevenueRecord> revenue = analyticsService.fetchVideoRevenue(
                    artistId, channelId, videoId, startDate, endDate);

            Map<String, Object> response = new HashMap<>();
            response.put("channelId", channelId);
            response.put("videoId", videoId);
            response.put("period", Map.of("start", startDate, "end", endDate));
            response.put("videoCount", revenue.size());
            response.put("records", revenue);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error fetching video revenue", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get AdSense revenue (exact)
     *
     * GET /api/youtube/adsense/revenue?artistId={artistId}&startDate=2024-01-01&endDate=2024-12-31
     */
    @GetMapping("/adsense/revenue")
    public ResponseEntity<?> getAdSenseRevenue(
            @RequestParam String artistId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        try {
            // Check if AdSense is available
            if (!adsenseService.hasAdSenseAccount(artistId)) {
                return ResponseEntity.ok(Map.of(
                        "status", "unavailable",
                        "message", "AdSense account not linked or not authorized. Using YouTube Analytics estimates instead.",
                        "recommendation", "Link YouTube channel to AdSense for exact revenue data"
                ));
            }

            List<RevenueRecord> revenue = adsenseService.fetchAdSenseRevenue(
                    artistId, startDate, endDate);

            double totalRevenue = revenue.stream()
                    .mapToDouble(r -> r.getAdRevenue() != null ? r.getAdRevenue() : 0.0)
                    .sum();

            Map<String, Object> response = new HashMap<>();
            response.put("period", Map.of("start", startDate, "end", endDate));
            response.put("totalRevenue", totalRevenue);
            response.put("currency", "USD");
            response.put("source", "AdSense (Exact)");
            response.put("verified", true);
            response.put("records", revenue);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error fetching AdSense revenue", e);
            return ResponseEntity.status(500).body(Map.of(
                    "error", e.getMessage(),
                    "note", "If AdSense data is unavailable, use YouTube Analytics endpoint instead"
            ));
        }
    }

    /**
     * Get comprehensive revenue report
     * Combines YouTube Analytics and AdSense data
     *
     * GET /api/youtube/revenue/{channelId}/comprehensive?artistId={artistId}&startDate=2024-01-01&endDate=2024-12-31
     */
    @GetMapping("/revenue/{channelId}/comprehensive")
    public ResponseEntity<?> getComprehensiveRevenue(
            @PathVariable String channelId,
            @RequestParam String artistId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        try {
            Map<String, Object> response = new HashMap<>();

            // Fetch YouTube Analytics data
            List<RevenueRecord> ytRevenue = analyticsService.fetchMonthlyRevenue(
                    artistId, channelId, startDate, endDate);

            double ytTotal = ytRevenue.stream()
                    .mapToDouble(r -> r.getEstimatedRevenue() != null ? r.getEstimatedRevenue() : 0.0)
                    .sum();

            response.put("youtubeAnalytics", Map.of(
                    "totalRevenue", ytTotal,
                    "source", "YouTube Analytics (Estimated)",
                    "records", ytRevenue
            ));

            // Try to fetch AdSense data
            if (adsenseService.hasAdSenseAccount(artistId)) {
                List<RevenueRecord> adsenseRevenue = adsenseService.fetchMonthlyAdSenseRevenue(
                        artistId, startDate, endDate);

                double adsenseTotal = adsenseRevenue.stream()
                        .mapToDouble(r -> r.getAdRevenue() != null ? r.getAdRevenue() : 0.0)
                        .sum();

                response.put("adsense", Map.of(
                        "totalRevenue", adsenseTotal,
                        "source", "AdSense (Exact)",
                        "verified", true,
                        "records", adsenseRevenue
                ));

                response.put("recommendedRevenue", adsenseTotal);
                response.put("note", "Using AdSense data as primary source (most accurate)");

            } else {
                response.put("adsense", Map.of(
                        "status", "unavailable",
                        "message", "AdSense account not linked or not authorized"
                ));

                response.put("recommendedRevenue", ytTotal);
                response.put("note", "Using YouTube Analytics estimates (AdSense unavailable)");
            }

            response.put("period", Map.of("start", startDate, "end", endDate));
            response.put("channelId", channelId);
            response.put("currency", "USD");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Error generating comprehensive revenue report", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", e.getMessage()));
        }
    }
}
