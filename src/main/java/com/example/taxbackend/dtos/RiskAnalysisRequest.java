package com.example.taxbackend.dtos;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Risk Analysis Request DTO
 * Input to risk analysis service
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RiskAnalysisRequest {
    private String artistId;
    private boolean includeAllArtists;  // Whether to compare with all artists
    private boolean forceReassessment;  // Ignore cached assessment
}
