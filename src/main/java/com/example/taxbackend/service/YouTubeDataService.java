package com.example.taxbackend.service;

import com.example.taxbackend.repository.VideoRepository;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.*;
import  com.example.taxbackend.models.Video;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * YouTube Data Service - Fetches channel and video information
 * Uses YouTube Data API v3
 *
 * Academic Note: This service demonstrates how to fetch public video metadata
 * and statistics from YouTube. The API returns views, likes, comments but NOT revenue.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class YouTubeDataService {

    private final YouTube.Builder youtubeBuilder;
    private final OAuthService oauthService;
    private final VideoRepository videoRepository;

    @Value("${youtube.api.max-results:50}")
    private long maxResults;

    /**
     * Get channel information for authenticated artist
     *
     * @param artistId Artist identifier
     * @return Channel object with channel details
     */
    public Channel getChannel(String artistId) throws IOException {
        Credential credential = oauthService.getCredential(artistId);
        YouTube youtube = youtubeBuilder.setHttpRequestInitializer(credential).build();

        // Fetch channel owned by authenticated user
        YouTube.Channels.List request = youtube.channels()
                .list(List.of("snippet", "contentDetails", "statistics"))
                .setMine(true); // Get channel for authenticated user

        ChannelListResponse response = request.execute();

        if (response.getItems().isEmpty()) {
            throw new IllegalStateException("No YouTube channel found for artist: " + artistId);
        }

        Channel channel = response.getItems().get(0);
        log.info("Fetched channel: {} (ID: {})",
                channel.getSnippet().getTitle(),
                channel.getId());

        return channel;
    }

    /**
     * Fetch all videos from a channel
     * Uses pagination to retrieve complete list
     *
     * @param artistId Artist identifier
     * @param channelId YouTube channel ID
     * @return List of Video entities with statistics
     */
    public List<Video> fetchChannelVideos(String artistId, String channelId) throws IOException {
        Credential credential = oauthService.getCredential(artistId);
        YouTube youtube = youtubeBuilder.setHttpRequestInitializer(credential).build();

        List<String> videoIds = getAllVideoIds(youtube, channelId);
        log.info("Found {} videos in channel {}", videoIds.size(), channelId);

        // Fetch video details in batches (API allows max 50 per request)
        List<Video> videos = new ArrayList<>();
        for (int i = 0; i < videoIds.size(); i += 50) {
            int end = Math.min(i + 50, videoIds.size());
            List<String> batch = videoIds.subList(i, end);
            videos.addAll(fetchVideoDetails(youtube, batch, artistId));
        }

        // Save to database
        videoRepository.saveAll(videos);
        log.info("Saved {} videos to database", videos.size());

        return videos;
    }

    /**
     * Get all video IDs from channel
     * Handles pagination to retrieve complete list
     */
    private List<String> getAllVideoIds(YouTube youtube, String channelId) throws IOException {
        List<String> videoIds = new ArrayList<>();
        String pageToken = null;

        do {
            // Get uploads playlist ID (contains all channel videos)
            YouTube.Channels.List channelRequest = youtube.channels()
                    .list(List.of("contentDetails"))
                    .setId(List.of(channelId));

            ChannelListResponse channelResponse = channelRequest.execute();
            String uploadsPlaylistId = channelResponse.getItems().get(0)
                    .getContentDetails()
                    .getRelatedPlaylists()
                    .getUploads();

            // Fetch videos from uploads playlist
            YouTube.PlaylistItems.List playlistRequest = youtube.playlistItems()
                    .list(List.of("contentDetails"))
                    .setPlaylistId(uploadsPlaylistId)
                    .setMaxResults(maxResults)
                    .setPageToken(pageToken);

            PlaylistItemListResponse playlistResponse = playlistRequest.execute();

            // Extract video IDs
            videoIds.addAll(
                    playlistResponse.getItems().stream()
                            .map(item -> item.getContentDetails().getVideoId())
                            .collect(Collectors.toList())
            );

            pageToken = playlistResponse.getNextPageToken();
        } while (pageToken != null);

        return videoIds;
    }

    /**
     * Fetch detailed information for specific videos
     * Includes statistics (views, likes, comments)
     */
    private List<Video> fetchVideoDetails(YouTube youtube, List<String> videoIds, String artistId)
            throws IOException {

        YouTube.Videos.List request = youtube.videos()
                .list(List.of("snippet", "contentDetails", "statistics"))
                .setId(videoIds);

        VideoListResponse response = request.execute();

        return response.getItems().stream()
                .map(item -> convertToVideo(item, artistId))
                .collect(Collectors.toList());
    }

    /**
     * Convert YouTube API Video to our Video entity
     */
    private Video convertToVideo(com.google.api.services.youtube.model.Video ytVideo, String artistId) {
        VideoSnippet snippet = ytVideo.getSnippet();
        VideoStatistics stats = ytVideo.getStatistics();
        VideoContentDetails content = ytVideo.getContentDetails();
        LocalDateTime publishedAt = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(snippet.getPublishedAt().getValue()),
                ZoneId.systemDefault()
        );
        return Video.builder()
                .artistId(artistId)
                .videoId(ytVideo.getId())
                .title(snippet.getTitle())
                .description(snippet.getDescription())
                .thumbnailUrl(snippet.getThumbnails().getDefault().getUrl())
                .publishedAt(publishedAt)
                .viewCount(stats.getViewCount() != null ? stats.getViewCount().longValue() : 0L)
                .likeCount(stats.getLikeCount() != null ? stats.getLikeCount().longValue() : 0L)
                .commentCount(stats.getCommentCount() != null ? stats.getCommentCount().longValue() : 0L)
                .duration(content.getDuration())
                .tags(snippet.getTags())
                .category(snippet.getCategoryId())
                .lastUpdated(LocalDateTime.now())
                .build();
    }

    /**
     * Update statistics for existing videos
     * Used for periodic syncing
     */
    public void updateVideoStatistics(String artistId, List<String> videoIds) throws IOException {
        Credential credential = oauthService.getCredential(artistId);
        YouTube youtube = youtubeBuilder.setHttpRequestInitializer(credential).build();

        for (int i = 0; i < videoIds.size(); i += 50) {
            int end = Math.min(i + 50, videoIds.size());
            List<String> batch = videoIds.subList(i, end);

            List<Video> updatedVideos = fetchVideoDetails(youtube, batch, artistId);
            videoRepository.saveAll(updatedVideos);
        }

        log.info("Updated statistics for {} videos", videoIds.size());
    }
}