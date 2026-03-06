package com.example.taxbackend.dtos;
import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;
import java.util.List;
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MonthlyTaxReportDTO {
    private String period;
    private LocalDateTime generatedAt;

    // Aggregates
    private int totalArtists;
    private int artistsWithRevenue;
    private int artistsAboveThreshold;

    private Double totalGrossRevenue;
    private Double totalTaxCollected;
    private Double totalNetRevenue;

    // By payment status
    private int paidCount;
    private int pendingCount;
    private int overdueCount;

    private Double paidAmount;
    private Double pendingAmount;
    private Double overdueAmount;

    // Compliance
    private int compliantArtists;
    private int nonCompliantArtists;

    // Detailed records
    private List<TaxRecordDTO> records;
}
