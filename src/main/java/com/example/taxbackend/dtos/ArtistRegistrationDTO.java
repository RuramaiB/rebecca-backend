package com.example.taxbackend.dtos;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Artist Registration DTO
 * Used for creating/updating artist profiles
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ArtistRegistrationDTO {
    private String googleAccountId;
    private String email;
    private String name;
    private String youtubeChannelId;
    private String youtubeChannelTitle;
    private String adsenseAccountId;
}
