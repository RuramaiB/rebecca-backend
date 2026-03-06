package com.example.taxbackend.service;

import com.example.taxbackend.dtos.ArtistDTO;
import com.example.taxbackend.dtos.ArtistRegistrationDTO;
import com.example.taxbackend.models.Artist;
import com.example.taxbackend.models.ComplianceStatus;
import com.example.taxbackend.repository.ArtistRepository;
import com.example.taxbackend.repository.ComplianceStatusRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Artist Service - Manages artist profiles and registration
 *
 * Academic Note: This service handles CRUD operations for artists
 * and initializes compliance tracking for new artists
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ArtistService {

    private final ArtistRepository artistRepository;
    private final ComplianceStatusRepository complianceRepository;

    /**
     * Register new artist
     * Creates artist profile and initializes compliance tracking
     *
     * @param registration Artist registration data
     * @return Created artist entity
     */
    @Transactional
    public Artist registerArtist(ArtistRegistrationDTO registration) {
        log.info("Registering new artist: {}", registration.getEmail());

        // Check if artist already exists
        Optional<Artist> existing = artistRepository.findByEmail(registration.getEmail());
        if (existing.isPresent()) {
            throw new IllegalArgumentException("Artist with email already exists: "
                    + registration.getEmail());
        }

        // Create artist entity
        Artist artist = Artist.builder()
                .googleAccountId(registration.getGoogleAccountId())
                .email(registration.getEmail())
                .name(registration.getName())
                .youtubeChannelId(registration.getYoutubeChannelId())
                .youtubeChannelTitle(registration.getYoutubeChannelTitle())
                .adsenseAccountId(registration.getAdsenseAccountId())
                .authorizedAt(LocalDateTime.now())
                .youtubeAuthorized(false)
                .analyticsAuthorized(false)
                .adsenseAuthorized(false)
                .build();

        artist = artistRepository.save(artist);
        log.info("Artist created with ID: {}", artist.getId());

        // Initialize compliance status
        initializeComplianceStatus(artist);

        return artist;
    }

    /**
     * Update existing artist profile
     *
     * @param artistId Artist ID
     * @param updates  Update data
     * @return Updated artist
     */
    @Transactional
    public Artist updateArtist(String artistId, ArtistRegistrationDTO updates) {
        log.info("Updating artist: {}", artistId);

        Artist artist = artistRepository.findById(artistId)
                .orElseThrow(() -> new IllegalArgumentException("Artist not found: " + artistId));

        // Update fields if provided
        if (updates.getName() != null) {
            artist.setName(updates.getName());
        }
        if (updates.getEmail() != null) {
            artist.setEmail(updates.getEmail());
        }
        if (updates.getYoutubeChannelId() != null) {
            artist.setYoutubeChannelId(updates.getYoutubeChannelId());
        }
        if (updates.getYoutubeChannelTitle() != null) {
            artist.setYoutubeChannelTitle(updates.getYoutubeChannelTitle());
        }
        if (updates.getAdsenseAccountId() != null) {
            artist.setAdsenseAccountId(updates.getAdsenseAccountId());
        }

        artist = artistRepository.save(artist);
        log.info("Artist updated: {}", artistId);

        return artist;
    }

    /**
     * Update artist authorization status
     * Called after successful OAuth flow
     *
     * @param artistId      Artist ID
     * @param youtubeAuth   YouTube authorization status
     * @param analyticsAuth Analytics authorization status
     * @param adsenseAuth   AdSense authorization status
     */
    @Transactional
    public void updateAuthorizationStatus(
            String artistId,
            boolean youtubeAuth,
            boolean analyticsAuth,
            boolean adsenseAuth) {

        Artist artist = artistRepository.findById(artistId)
                .orElseThrow(() -> new IllegalArgumentException("Artist not found: " + artistId));

        artist.setYoutubeAuthorized(youtubeAuth);
        artist.setAnalyticsAuthorized(analyticsAuth);
        artist.setAdsenseAuthorized(adsenseAuth);
        artist.setLastSyncedAt(LocalDateTime.now());

        artistRepository.save(artist);
        log.info("Updated authorization status for artist: {}", artistId);
    }

    /**
     * Get artist by ID
     *
     * @param artistId Artist ID
     * @return Artist entity
     */
    public Artist getArtist(String artistId) {
        return artistRepository.findById(artistId)
                .orElseThrow(() -> new IllegalArgumentException("Artist not found: " + artistId));
    }

    /**
     * Get artist by Google account ID
     *
     * @param googleAccountId Google account ID
     * @return Artist entity
     */
    public Optional<Artist> getArtistByGoogleAccount(String googleAccountId) {
        return artistRepository.findByGoogleAccountId(googleAccountId);
    }

    /**
     * Get artist by YouTube channel ID
     *
     * @param channelId YouTube channel ID
     * @return Artist entity
     */
    public Optional<Artist> getArtistByYouTubeChannel(String channelId) {
        return artistRepository.findByYoutubeChannelId(channelId);
    }

    /**
     * Get artist by email
     *
     * @param email Email address
     * @return Artist entity
     */
    public Optional<Artist> getArtistByEmail(String email) {
        return artistRepository.findByEmail(email);
    }

    /**
     * Get all artists
     *
     * @return List of all artists
     */
    public List<Artist> getAllArtists() {
        return artistRepository.findAllByRoleNot(com.example.taxbackend.user.Role.ADMIN);
    }

    /**
     * Get all authorized artists
     *
     * @return List of artists with YouTube authorization
     */
    public List<Artist> getAuthorizedArtists() {
        return artistRepository.findByYoutubeAuthorizedTrueAndRoleNot(true, com.example.taxbackend.user.Role.ADMIN);
    }

    /**
     * Get artists with analytics authorization
     *
     * @return List of artists with analytics access
     */
    public List<Artist> getAnalyticsAuthorizedArtists() {
        return artistRepository.findByAnalyticsAuthorizedTrueAndRoleNot(true, com.example.taxbackend.user.Role.ADMIN);
    }

    /**
     * Get artists with AdSense authorization
     *
     * @return List of artists with AdSense access
     */
    public List<Artist> getAdSenseAuthorizedArtists() {
        return artistRepository.findByAdsenseAuthorizedTrueAndRoleNot(true, com.example.taxbackend.user.Role.ADMIN);
    }

    /**
     * Delete artist
     * Also deletes associated compliance status
     *
     * @param artistId Artist ID
     */
    @Transactional
    public void deleteArtist(String artistId) {
        log.info("Deleting artist: {}", artistId);

        // Delete compliance status
        complianceRepository.deleteByArtistId(artistId);

        // Delete artist
        artistRepository.deleteById(artistId);

        log.info("Artist deleted: {}", artistId);
    }

    /**
     * Convert artist entity to DTO
     *
     * @param artist Artist entity
     * @return Artist DTO
     */
    public ArtistDTO toDTO(Artist artist) {
        ComplianceStatus compliance = complianceRepository.findByArtistId(artist.getId())
                .orElse(null);

        return ArtistDTO.builder()
                .id(artist.getId())
                .name(artist.getName())
                .email(artist.getEmail())
                .youtubeChannelId(artist.getYoutubeChannelId())
                .youtubeChannelTitle(artist.getYoutubeChannelTitle())
                .adsenseAccountId(artist.getAdsenseAccountId())
                .youtubeAuthorized(artist.isYoutubeAuthorized())
                .analyticsAuthorized(artist.isAnalyticsAuthorized())
                .adsenseAuthorized(artist.isAdsenseAuthorized())
                .authorizedAt(artist.getAuthorizedAt())
                .lastSyncedAt(artist.getLastSyncedAt())
                .taxCompliant(compliance != null && compliance.isTaxCompliant())
                .complianceLevel(compliance != null && compliance.getComplianceLevel() != null
                        ? compliance.getComplianceLevel().toString()
                        : "N/A")
                .outstandingTax(compliance != null ? compliance.getOutstandingTax() : 0.0)
                .totalVideos(compliance != null ? compliance.getTotalVideosToDate() : 0)
                .totalShots(compliance != null ? compliance.getTotalShotsToDate() : 0)
                .registeredForDigitalTax(compliance != null && compliance.isRegisteredForDigitalTax())
                .build();
    }

    /**
     * Convert list of artists to DTOs
     *
     * @param artists List of artists
     * @return List of DTOs
     */
    public List<ArtistDTO> toDTOs(List<Artist> artists) {
        return artists.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    /**
     * Initialize compliance status for new artist
     *
     * @param artist Artist entity
     */
    private void initializeComplianceStatus(Artist artist) {
        ComplianceStatus compliance = ComplianceStatus.builder()
                .artistId(artist.getId())
                .taxCompliant(true) // Start as compliant
                .complianceLevel(ComplianceStatus.ComplianceLevel.GOOD)
                .complianceNotes("New artist - no tax records yet")
                .totalRevenueToDate(0.0)
                .totalTaxPaid(0.0)
                .outstandingTax(0.0)
                .currentYearRevenue(0.0)
                .currentYearTax(0.0)
                .consecutiveCompliantMonths(0)
                .missedPayments(0)
                .hasOverduePayments(false)
                .needsAttention(false)
                .registeredForDigitalTax(true) // Auto-registered
                .registeredDate(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        complianceRepository.save(compliance);
        log.info("Initialized compliance status for artist: {}", artist.getId());
    }

    /**
     * Onboard existing users who don't have compliance status
     * Ensures all artists are registered for Digital Service Tax
     */
    @PostConstruct
    public void onboardExistingUsers() {
        log.info("Starting automated onboarding for existing users...");
        List<Artist> artists = artistRepository.findAllByRole(com.example.taxbackend.user.Role.ARTIST);

        for (Artist artist : artists) {
            Optional<ComplianceStatus> status = complianceRepository.findByArtistId(artist.getId());
            if (status.isEmpty()) {
                log.info("Onboarding existing artist: {}", artist.getEmail());
                initializeComplianceStatus(artist);
            } else if (!status.get().isRegisteredForDigitalTax()) {
                ComplianceStatus compliance = status.get();
                compliance.setRegisteredForDigitalTax(true);
                compliance.setRegisteredDate(LocalDateTime.now());
                complianceRepository.save(compliance);
                log.info("Updated DST registration for existing artist: {}", artist.getEmail());
            }
        }
        log.info("Automated onboarding complete.");
    }
}
