package com.example.taxbackend.repository;

import com.example.taxbackend.models.Video;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface VideoMetadataRepository extends MongoRepository<Video, String> {

    long countByArtistIdAndPublishedAt(String artistId, LocalDateTime start, LocalDateTime end);

    List<Video> findByArtistIdAndPublishedAtBetween(String artistId, LocalDateTime start, LocalDateTime end);

    List<Video> findByArtistId(String artistId);

    @Query("SELECT DISTINCT v.artistId FROM VideoMetadata v WHERE v.publishedDate BETWEEN :start AND :end")
    List<String> findDistinctArtistIdsByUploadDateBetween(@Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);
}
