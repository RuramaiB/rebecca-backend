package com.example.taxbackend.repository;

import com.example.taxbackend.models.Video;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface VideoRepository extends MongoRepository<Video, String> {

    List<Video> findByArtistId(String artistId);

    Optional<Video> findByVideoId(String videoId);

    List<Video> findByArtistIdAndPublishedAtBetween(
            String artistId,
            LocalDateTime start,
            LocalDateTime end
    );

    // Find top videos by views
    @Query("{ 'artistId': ?0 }")
    List<Video> findTopVideosByViews(String artistId, org.springframework.data.domain.Pageable pageable);

    // Count videos by artist
    long countByArtistId(String artistId);
}