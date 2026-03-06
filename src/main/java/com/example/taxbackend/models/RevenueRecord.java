package com.example.taxbackend.models;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

/**
 * RevenueRecord Entity - Stores revenue data per time period
 * This separates revenue from performance data for tax calculation purposes
 */
@Data
@Builder
@Document(collection = "revenue_records")
public class RevenueRecord {
    @Id
    private String id;

    @Indexed
    private String artistId;

    @Indexed
    private String videoId; // Null for channel-level revenue

    // Time Period
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;
    private String periodType; // DAILY, MONTHLY, YEARLY

    // Revenue Data Sources
    private RevenueSource source; // YOUTUBE_ANALYTICS, ADSENSE, ESTIMATED

    // YouTube Analytics Revenue (estimated)
    private Double estimatedRevenue;

    // AdSense Revenue (exact)
    private Double adRevenue;
    private Long adImpressions;
    private Long adClicks;
    private Double rpm; // Revenue per mille (1000 impressions)
    private Double cpm; // Cost per mille

    // Total calculated revenue
    private Double totalRevenue;
    private String currency;

    // Metadata
    private boolean verified; // True if from AdSense, false if estimated
    private String dataQuality; // HIGH, MEDIUM, LOW
    private LocalDateTime fetchedAt;

    public enum RevenueSource {
        YOUTUBE_ANALYTICS, // Estimated revenue from YT Analytics
        ADSENSE,          // Exact revenue from AdSense API
        ESTIMATED,        // Calculated estimate (academic mode)
        UNAVAILABLE       // Data not accessible
    }
}
