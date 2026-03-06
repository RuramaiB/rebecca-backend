package com.example.taxbackend.dtos;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ArtistDTO {
    private String id;
    private String name;
    private String email;
    private String youtubeChannelId;
    private String youtubeChannelTitle;
    private String adsenseAccountId;

    private boolean youtubeAuthorized;
    private boolean analyticsAuthorized;
    private boolean adsenseAuthorized;

    private LocalDateTime authorizedAt;
    private LocalDateTime lastSyncedAt;

    // Compliance summary
    private boolean taxCompliant;
    private String complianceLevel;
    private Double outstandingTax;
    private int totalVideos;
    private int totalShots;
    private boolean registeredForDigitalTax;
}
