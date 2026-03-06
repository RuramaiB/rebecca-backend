package com.example.taxbackend.controller;


import com.example.taxbackend.dtos.ArtistDTO;
import com.example.taxbackend.dtos.ArtistRegistrationDTO;
import com.example.taxbackend.models.Artist;
import com.example.taxbackend.service.ArtistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Artist Controller
 * Manages artist profiles
 */
@RestController
@RequestMapping("/api/artists")
@Slf4j
@RequiredArgsConstructor
public class ArtistController {

    private final ArtistService artistService;

    @GetMapping("get-artist-by-/{id}")
    public ResponseEntity<?> getArtist(@PathVariable String id) {
        try {
            Artist artist = artistService.getArtist(id);
            ArtistDTO dto = artistService.toDTO(artist);

            return ResponseEntity.ok(dto);

        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (Exception e) {
            log.error("Error fetching artist", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to fetch artist"));
        }
    }

    @GetMapping("/get-all-artists")
    public ResponseEntity<?> getAllArtists(
            @RequestParam(required = false) String filter) {

        try {
            List<Artist> artists;

            if ("authorized".equals(filter)) {
                artists = artistService.getAuthorizedArtists();
            } else if ("analytics".equals(filter)) {
                artists = artistService.getAnalyticsAuthorizedArtists();
            } else if ("adsense".equals(filter)) {
                artists = artistService.getAdSenseAuthorizedArtists();
            } else {
                artists = artistService.getAllArtists();
            }

            List<ArtistDTO> dtos = artistService.toDTOs(artists);

            return ResponseEntity.ok(Map.of(
                    "count", dtos.size(),
                    "filter", filter != null ? filter : "all",
                    "artists", dtos
            ));

        } catch (Exception e) {
            log.error("Error fetching artists", e);
            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to fetch artists"));
        }
    }

}
