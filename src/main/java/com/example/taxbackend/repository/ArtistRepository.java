package com.example.taxbackend.repository;

import com.example.taxbackend.models.Artist;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Artist Repository
 * Manages musician/artist data
 */
@Repository
public interface ArtistRepository extends MongoRepository<Artist, String> {

    Optional<Artist> findByGoogleAccountId(String googleAccountId);

    Optional<Artist> findByYoutubeChannelId(String youtubeChannelId);

    Optional<Artist> findByEmail(String email);

    List<Artist> findByYoutubeAuthorizedTrue();

    List<Artist> findByYoutubeAuthorizedTrueAndRoleNot(boolean authorized, com.example.taxbackend.user.Role role);

    List<Artist> findByAnalyticsAuthorizedTrue();

    List<Artist> findByAnalyticsAuthorizedTrueAndRoleNot(boolean authorized, com.example.taxbackend.user.Role role);

    List<Artist> findByAdsenseAuthorizedTrue();

    List<Artist> findByAdsenseAuthorizedTrueAndRoleNot(boolean authorized, com.example.taxbackend.user.Role role);

    List<Artist> findAllByRole(com.example.taxbackend.user.Role role);

    List<Artist> findAllByRoleNot(com.example.taxbackend.user.Role role);

    // Count methods
    long countByYoutubeAuthorizedTrue();

    long countByYoutubeAuthorizedTrueAndRoleNot(boolean authorized, com.example.taxbackend.user.Role role);

    long countByAnalyticsAuthorizedTrue();

    long countByAdsenseAuthorizedTrue();
}
