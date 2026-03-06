package com.example.taxbackend.models;

import lombok.Data;
import lombok.Builder;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Video Entity - Represents a YouTube video with performance metrics
 */
@Data
@Builder
@Document(collection = "videos")
public class Video {
    @Id
    private String id;

    @Indexed
    private String artistId;

    @Indexed
    private String videoId; // YouTube video ID

    private String title;
    private String description;
    private String thumbnailUrl;

    private LocalDateTime publishedAt;

    // Performance Metrics (from YouTube Data API)
    private Long viewCount;
    private Long likeCount;
    private Long commentCount;
    private String duration; // ISO 8601 duration

    // Metadata
    private List<String> tags;
    private String category;

    private LocalDateTime lastUpdated;
}
