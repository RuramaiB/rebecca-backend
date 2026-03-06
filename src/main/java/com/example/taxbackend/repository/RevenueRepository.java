package com.example.taxbackend.repository;

import com.example.taxbackend.models.RevenueRecord;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface RevenueRepository extends MongoRepository<RevenueRecord, String> {

    List<RevenueRecord> findByArtistId(String artistId);

    List<RevenueRecord> findByVideoId(String videoId);

    // Find revenue for specific period
    List<RevenueRecord> findByArtistIdAndPeriodStartBetween(
            String artistId,
            LocalDateTime start,
            LocalDateTime end
    );

    // Find revenue by source
    List<RevenueRecord> findByArtistIdAndSource(
            String artistId,
            RevenueRecord.RevenueSource source
    );

    // Find revenue for video in period
    List<RevenueRecord> findByArtistIdAndVideoIdAndPeriodStartBetween(
            String artistId,
            String videoId,
            LocalDateTime start,
            LocalDateTime end
    );

    // Calculate total revenue for artist
    @Query(value = "{ 'artistId': ?0 }", fields = "{ 'totalRevenue': 1 }")
    List<RevenueRecord> findTotalRevenueByArtist(String artistId);

    // Find latest revenue records
    List<RevenueRecord> findByArtistIdOrderByPeriodStartDesc(
            String artistId,
            org.springframework.data.domain.Pageable pageable
    );

    // Find verified revenue (from AdSense)
    List<RevenueRecord> findByArtistIdAndVerifiedTrue(String artistId);
}