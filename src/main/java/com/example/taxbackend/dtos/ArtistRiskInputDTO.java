package com.example.taxbackend.dtos;

import lombok.*;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ArtistRiskInputDTO {
    private String artistId;
    private String googleAccountId;
    private String paypalEmail;

    private List<Double> monthlyRevenues;
    private Double taxThreshold;

    private Integer missedTaxPeriods;
    private Double taxPaymentRatio;
    private Integer consecutiveCompliantMonths;

    private Double totalRevenueToDate;
    private Double totalTaxPaid;
    private Double outstandingTax;

    private Integer recalculationCount;
    private Integer daysSinceRegistration;
}
