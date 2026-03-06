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
public class TaxSummaryDTO {
    private String artistId;
    private String artistName;

    // Current period
    private String currentPeriod;
    private Double currentPeriodRevenue;
    private Double currentPeriodTax;

    // Year-to-date
    private Double ytdRevenue;
    private Double ytdTax;
    private Double ytdNetRevenue;

    // Lifetime
    private Double lifetimeRevenue;
    private Double lifetimeTax;

    // Outstanding
    private Double outstandingTax;
    private int outstandingPayments;

    // Compliance
    private boolean taxCompliant;
    private String complianceLevel;
    private LocalDateTime lastCalculation;
    private LocalDateTime nextDue;
}

