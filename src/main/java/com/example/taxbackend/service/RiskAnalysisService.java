package com.example.taxbackend.service;

import com.example.taxbackend.dtos.ArtistRiskInputDTO;
import com.example.taxbackend.dtos.RiskAnalysisResponse;
import com.example.taxbackend.dtos.RiskDashboardDTO;
import com.example.taxbackend.models.Artist;
import com.example.taxbackend.models.ComplianceStatus;
import com.example.taxbackend.models.RevenueRecord;
import com.example.taxbackend.models.RiskAssessment;
import com.example.taxbackend.models.TaxRecord;
import com.example.taxbackend.repository.ArtistRepository;
import com.example.taxbackend.repository.ComplianceStatusRepository;
import com.example.taxbackend.repository.RevenueRepository;
import com.example.taxbackend.repository.RiskAssessmentRepository;
import com.example.taxbackend.repository.TaxRecordRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class RiskAnalysisService {

    private final ArtistRepository artistRepository;
    private final TaxRecordRepository taxRecordRepository;
    private final RevenueRepository revenueRepository;
    private final ComplianceStatusRepository complianceRepository;
    private final RiskAssessmentRepository riskAssessmentRepository;
    private final ObjectMapper objectMapper;

    @Value("${risk.assessment.cache.hours:24}")
    private int cacheHours;

    @Value("${ollama.base-url:http://localhost:11434}")
    private String ollamaBaseUrl;

    @Value("${ollama.model:llama3}")
    private String ollamaModel;

    @Value("${ollama.timeout-seconds:60}")
    private int ollamaTimeoutSeconds;

    /**
     * Analyze compliance risk for specific artist
     *
     * @param artistId Artist to analyze
     * @param forceReassessment Bypass cache
     * @return Risk analysis result
     */
    @Transactional
    public RiskAnalysisResponse analyzeArtistRisk(String artistId, boolean forceReassessment) {
        log.info("Starting risk analysis for artist: {}", artistId);

        // Check for recent assessment (if not forcing)
        if (!forceReassessment) {
            Optional<RiskAssessment> recent = riskAssessmentRepository
                    .findFirstByArtistIdOrderByAssessedAtDesc(artistId);

            if (recent.isPresent()) {
                RiskAssessment cached = recent.get();
                long hoursSinceAssessment = ChronoUnit.HOURS.between(
                        cached.getAssessedAt(), LocalDateTime.now());

                if (hoursSinceAssessment < cacheHours) {
                    log.info("Using cached assessment from {} hours ago", hoursSinceAssessment);
                    return toResponseDTO(cached);
                }
            }
        }

        try {
            Artist targetArtist = artistRepository.findById(artistId)
                    .orElseThrow(() -> new IllegalArgumentException("Artist not found: " + artistId));
            ArtistRiskInputDTO targetData = buildArtistRiskData(targetArtist);
            RuleRiskResult rule = deriveRuleRisk(targetData);
            OllamaRiskResult inferred = getOllamaRiskAssessment(targetData, rule);

            RiskAssessment assessment = createAssessmentFromOllama(artistId, inferred);
            assessment = riskAssessmentRepository.save(assessment);

            log.info("Risk analysis completed: {} - {} (score: {})",
                    artistId, assessment.getRiskLevel(), assessment.getRiskScore());

            return toResponseDTO(assessment);

        } catch (Exception e) {
            log.error("Error analyzing artist risk: {}", e.getMessage(), e);
            throw new RuntimeException("Risk analysis failed: " + e.getMessage(), e);
        }
    }

    /**
     * Analyze all active artists
     * Used by scheduler for bulk assessment
     */
    @Transactional
    public List<RiskAnalysisResponse> analyzeAllArtists() {
        log.info("Starting bulk risk analysis for all artists");

        List<Artist> allArtists = artistRepository.findByYoutubeAuthorizedTrue();
        List<RiskAnalysisResponse> results = new ArrayList<>();

        int successCount = 0;
        int failureCount = 0;

        for (Artist artist : allArtists) {
            try {
                RiskAnalysisResponse result = analyzeArtistRisk(artist.getId(), true);
                results.add(result);
                successCount++;
            } catch (Exception e) {
                log.error("Failed to analyze artist {}: {}", artist.getId(), e.getMessage());
                failureCount++;
            }
        }

        log.info("Bulk analysis complete: {} succeeded, {} failed", successCount, failureCount);
        return results;
    }

    /**
     * Get risk dashboard summary
     */
    public RiskDashboardDTO getRiskDashboard() {
        List<RiskAssessment> allAssessments = riskAssessmentRepository.findAll();

        // Get latest assessment per artist
        Map<String, RiskAssessment> latestPerArtist = new HashMap<>();
        for (RiskAssessment assessment : allAssessments) {
            String artistId = assessment.getArtistId();
            if (!latestPerArtist.containsKey(artistId) ||
                    assessment.getAssessedAt().isAfter(latestPerArtist.get(artistId).getAssessedAt())) {
                latestPerArtist.put(artistId, assessment);
            }
        }

        List<RiskAssessment> latestAssessments = new ArrayList<>(latestPerArtist.values());

        // Calculate statistics
        long lowCount = latestAssessments.stream()
                .filter(a -> "LOW".equals(a.getRiskLevel())).count();
        long mediumCount = latestAssessments.stream()
                .filter(a -> "MEDIUM".equals(a.getRiskLevel())).count();
        long highCount = latestAssessments.stream()
                .filter(a -> "HIGH".equals(a.getRiskLevel())).count();

        long flaggedCount = latestAssessments.stream()
                .filter(RiskAssessment::isFlaggedForAudit).count();

        long pendingReview = latestAssessments.stream()
                .filter(a -> a.isFlaggedForAudit() && !a.isReviewed()).count();

        double avgScore = latestAssessments.stream()
                .mapToDouble(RiskAssessment::getRiskScore)
                .average()
                .orElse(0.0);

        // Get high-risk artists
        List<RiskAnalysisResponse> highRisk = latestAssessments.stream()
                .filter(a -> "HIGH".equals(a.getRiskLevel()))
                .sorted(Comparator.comparingDouble(RiskAssessment::getRiskScore).reversed())
                .limit(10)
                .map(this::toResponseDTO)
                .collect(Collectors.toList());

        LocalDateTime lastAssessment = latestAssessments.stream()
                .map(RiskAssessment::getAssessedAt)
                .max(LocalDateTime::compareTo)
                .orElse(null);

        return RiskDashboardDTO.builder()
                .totalArtists((int) artistRepository.count())
                .assessedArtists(latestAssessments.size())
                .lowRiskCount((int) lowCount)
                .mediumRiskCount((int) mediumCount)
                .highRiskCount((int) highCount)
                .flaggedForAudit((int) flaggedCount)
                .pendingReview((int) pendingReview)
                .averageRiskScore(Math.round(avgScore * 1000.0) / 1000.0)
                .highRiskArtists(highRisk)
                .lastAssessment(lastAssessment)
                .nextScheduledAssessment(LocalDateTime.now().plusDays(30))
                .build();
    }

    /**
     * Build risk input data for an artist
     */
    private ArtistRiskInputDTO buildArtistRiskData(Artist artist) {
        String artistId = artist.getId();

        // Get compliance status
        ComplianceStatus compliance = complianceRepository.findByArtistId(artistId)
                .orElse(new ComplianceStatus());

        // Get recent revenue records (last 12 months)
        LocalDateTime oneYearAgo = LocalDateTime.now().minusYears(1);
        List<RevenueRecord> revenues = revenueRepository
                .findByArtistIdAndPeriodStartBetween(artistId, oneYearAgo, LocalDateTime.now());

        // Group revenues by month
        List<Double> monthlyRevenues = revenues.stream()
                .collect(Collectors.groupingBy(
                        r -> r.getPeriodStart().getMonth(),
                        Collectors.summingDouble(r -> r.getTotalRevenue() != null ? r.getTotalRevenue() : 0.0)
                ))
                .values().stream()
                .collect(Collectors.toList());

        // Get tax records for recalculation count
        List<TaxRecord> taxRecords = taxRecordRepository.findByArtistId(artistId);
        int recalcCount = taxRecords.size();

        LocalDateTime startDate = artist.getAuthorizedAt() != null ? artist.getAuthorizedAt() : LocalDateTime.now();
        if (artist.getYoutubeChannelPublishedAt() != null && artist.getYoutubeChannelPublishedAt().isBefore(startDate)) {
            startDate = artist.getYoutubeChannelPublishedAt();
        }
        
        // Use TaxRecordRepository or VideoMetadataRepository to find first activity if needed
        // For simplicity here, we use authorizedAt or channel date
        
        long daysSinceReg = ChronoUnit.DAYS.between(startDate, LocalDateTime.now());

        return ArtistRiskInputDTO.builder()
                .artistId(artistId)
                .googleAccountId(artist.getGoogleAccountId())
                .paypalEmail(null) // Add if you have this field
                .monthlyRevenues(monthlyRevenues)
                .taxThreshold(100.0) // From tax configuration
                .missedTaxPeriods(Math.max(compliance.getMissedPayments(), 0))
                .taxPaymentRatio(calculateTaxPaymentRatio(artistId))
                .consecutiveCompliantMonths(Math.max(compliance.getConsecutiveCompliantMonths(), 0))
                .totalRevenueToDate(compliance.getTotalRevenueToDate() != null ?
                        compliance.getTotalRevenueToDate() : 0.0)
                .totalTaxPaid(compliance.getTotalTaxPaid() != null ?
                        compliance.getTotalTaxPaid() : 0.0)
                .outstandingTax(compliance.getOutstandingTax() != null ?
                        compliance.getOutstandingTax() : 0.0)
                .recalculationCount(recalcCount)
                .daysSinceRegistration((int) daysSinceReg)
                .build();
    }

    /**
     * Calculate tax payment ratio
     */
    private Double calculateTaxPaymentRatio(String artistId) {
        List<TaxRecord> allTax = taxRecordRepository.findByArtistId(artistId);
        if (allTax.isEmpty()) return 1.0;

        long paidCount = allTax.stream()
                .filter(t -> TaxRecord.PaymentStatus.PAID.equals(t.getPaymentStatus()))
                .count();

        return (double) paidCount / allTax.size();
    }

    private RuleRiskResult deriveRuleRisk(ArtistRiskInputDTO input) {
        List<String> indicators = new ArrayList<>();
        double score = 0.0;
        if ((input.getOutstandingTax() != null ? input.getOutstandingTax() : 0.0) > 0) {
            score += 0.25; indicators.add("Outstanding tax balance exists");
        }
        if ((input.getMissedTaxPeriods() != null ? input.getMissedTaxPeriods() : 0) >= 2) {
            score += 0.25; indicators.add("Multiple missed tax periods detected");
        }
        if ((input.getTaxPaymentRatio() != null ? input.getTaxPaymentRatio() : 1.0) < 0.6) {
            score += 0.20; indicators.add("Low historical tax payment ratio");
        }
        if ((input.getConsecutiveCompliantMonths() != null ? input.getConsecutiveCompliantMonths() : 0) == 0
                && (input.getMissedTaxPeriods() != null ? input.getMissedTaxPeriods() : 0) > 0) {
            score += 0.15; indicators.add("No compliant streak with missed periods");
        }
        if ((input.getRecalculationCount() != null ? input.getRecalculationCount() : 0) > 24) {
            score += 0.10; indicators.add("High recalculation activity pattern");
        }
        return new RuleRiskResult(Math.min(1.0, score), indicators);
    }

    private OllamaRiskResult getOllamaRiskAssessment(ArtistRiskInputDTO targetData, RuleRiskResult rule) {
        try {
            String prompt = """
                    Return STRICT JSON only with keys: riskScore (0..1), riskLevel (LOW|MEDIUM|HIGH), recommendation, indicators[].
                    Zimbabwe DST risk context.
                    missedTaxPeriods=%d
                    taxPaymentRatio=%.4f
                    consecutiveCompliantMonths=%d
                    totalRevenueToDate=%.2f
                    totalTaxPaid=%.2f
                    outstandingTax=%.2f
                    recalculationCount=%d
                    daysSinceRegistration=%d
                    monthlyRevenues=%s
                    ruleScore=%.4f
                    ruleIndicators=%s
                    """.formatted(
                    targetData.getMissedTaxPeriods() != null ? targetData.getMissedTaxPeriods() : 0,
                    targetData.getTaxPaymentRatio() != null ? targetData.getTaxPaymentRatio() : 1.0,
                    targetData.getConsecutiveCompliantMonths() != null ? targetData.getConsecutiveCompliantMonths() : 0,
                    targetData.getTotalRevenueToDate() != null ? targetData.getTotalRevenueToDate() : 0.0,
                    targetData.getTotalTaxPaid() != null ? targetData.getTotalTaxPaid() : 0.0,
                    targetData.getOutstandingTax() != null ? targetData.getOutstandingTax() : 0.0,
                    targetData.getRecalculationCount() != null ? targetData.getRecalculationCount() : 0,
                    targetData.getDaysSinceRegistration() != null ? targetData.getDaysSinceRegistration() : 0,
                    String.valueOf(targetData.getMonthlyRevenues()),
                    rule.ruleScore,
                    String.valueOf(rule.indicators));

            Map<String, Object> payload = new HashMap<>();
            payload.put("model", ollamaModel);
            payload.put("stream", false);
            payload.put("format", "json");
            payload.put("prompt", prompt);
            String body = objectMapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(ollamaBaseUrl + "/api/generate"))
                    .timeout(Duration.ofSeconds(Math.max(10, ollamaTimeoutSeconds)))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Ollama HTTP " + response.statusCode());
            }

            JsonNode wrapped = objectMapper.readTree(response.body());
            String modelOutput = wrapped.has("response") ? wrapped.get("response").asText() : "{}";
            JsonNode model = objectMapper.readTree(modelOutput);

            double score = clampScore(model.has("riskScore") ? model.get("riskScore").asDouble() : rule.ruleScore);
            String level = model.has("riskLevel") ? model.get("riskLevel").asText() : toRiskLevel(score);
            String recommendation = model.has("recommendation")
                    ? model.get("recommendation").asText()
                    : "Monitor artist for potential tax non-compliance patterns.";

            List<String> indicators = new ArrayList<>(rule.indicators);
            if (model.has("indicators") && model.get("indicators").isArray()) {
                model.get("indicators").forEach(n -> indicators.add(n.asText()));
            }
            return new OllamaRiskResult(score, level, recommendation, rule.ruleScore, indicators);

        } catch (Exception ex) {
            log.warn("Ollama risk inference failed, using deterministic rules fallback: {}", ex.getMessage());
            return new OllamaRiskResult(rule.ruleScore, toRiskLevel(rule.ruleScore),
                    "Rules-based fallback applied. Review artist manually.", rule.ruleScore, rule.indicators);
        }
    }

    private double clampScore(double score) { return Math.max(0.0, Math.min(1.0, score)); }
    private String toRiskLevel(double score) { return score >= 0.7 ? "HIGH" : score >= 0.3 ? "MEDIUM" : "LOW"; }

    private RiskAssessment createAssessmentFromOllama(String artistId, OllamaRiskResult result) {
        // Get previous assessment for trend calculation
        Optional<RiskAssessment> previous = riskAssessmentRepository
                .findFirstByArtistIdOrderByAssessedAtDesc(artistId);

        RiskAssessment assessment = RiskAssessment.builder()
                .artistId(artistId)
                .riskScore(result.riskScore)
                .riskLevel(result.riskLevel)
                .mlAnomalyScore(result.riskScore)
                .ruleBasedScore(result.ruleBasedScore)
                .indicators(result.indicators)
                .assessedAt(LocalDateTime.now())
                .analysisVersion("ollama-" + ollamaModel)
                .artistsAnalyzed((int) artistRepository.countByYoutubeAuthorizedTrue())
                .flaggedForAudit("HIGH".equals(result.riskLevel))
                .reviewed(false)
                .build();

        // Calculate trend
        if (previous.isPresent()) {
            assessment.setPreviousRiskLevel(previous.get().getRiskLevel());
            assessment.setRiskTrend(result.riskScore - previous.get().getRiskScore());
        }

        return assessment;
    }

    /**
     * Convert RiskAssessment to Response DTO
     */
    private RiskAnalysisResponse toResponseDTO(RiskAssessment assessment) {
        Artist artist = artistRepository.findById(assessment.getArtistId()).orElse(null);

        String recommendation = generateRecommendation(assessment);

        return RiskAnalysisResponse.builder()
                .artistId(assessment.getArtistId())
                .artistName(artist != null ? artist.getName() : "Unknown")
                .riskScore(assessment.getRiskScore())
                .riskLevel(assessment.getRiskLevel())
                .mlAnomalyScore(assessment.getMlAnomalyScore())
                .ruleBasedScore(assessment.getRuleBasedScore())
                .indicators(assessment.getIndicators())
                .assessedAt(assessment.getAssessedAt())
                .analysisVersion(assessment.getAnalysisVersion())
                .artistsAnalyzed(assessment.getArtistsAnalyzed())
                .flaggedForAudit(assessment.isFlaggedForAudit())
                .recommendation(recommendation)
                .build();
    }

    /**
     * Generate human-readable recommendation
     */
    private String generateRecommendation(RiskAssessment assessment) {
        String level = assessment.getRiskLevel();

        switch (level) {
            case "HIGH":
                return "IMMEDIATE ACTION REQUIRED: Flag for manual audit and review all transactions";
            case "MEDIUM":
                return "MONITOR CLOSELY: Schedule review within 30 days and track behavior changes";
            case "LOW":
                return "NO ACTION NEEDED: Continue routine monitoring";
            default:
                return "Review assessment details";
        }
    }

    private record RuleRiskResult(double ruleScore, List<String> indicators) {}
    private record OllamaRiskResult(double riskScore, String riskLevel, String recommendation,
                                    double ruleBasedScore, List<String> indicators) {}
}
