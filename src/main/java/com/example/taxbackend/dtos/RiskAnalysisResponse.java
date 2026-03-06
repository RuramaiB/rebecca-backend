package com.example.taxbackend.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RiskAnalysisResponse {
    private String artistId;
    private String artistName;

    private Double riskScore;
    private String riskLevel;
    private Double mlAnomalyScore;
    private Double ruleBasedScore;

    private List<String> indicators;

    private LocalDateTime assessedAt;
    private String analysisVersion;
    private Integer artistsAnalyzed;

    private boolean flaggedForAudit;
    private String recommendation;
}