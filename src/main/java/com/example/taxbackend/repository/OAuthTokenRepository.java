package com.example.taxbackend.repository;

import com.example.taxbackend.token.OAuthToken;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OAuthTokenRepository extends MongoRepository<OAuthToken, String> {

    Optional<OAuthToken> findByArtistId(String artistId);

    void deleteByArtistId(String artistId);

    List<OAuthToken> findByExpiresAtBefore(LocalDateTime dateTime);
    // Check if artist has valid token
    boolean existsByArtistIdAndExpiresAtAfter(String artistId, LocalDateTime dateTime);
}