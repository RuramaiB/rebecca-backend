package com.example.taxbackend.dtos;
@lombok.Data
@lombok.Builder

public  class ComplianceStatistics {
    private int totalArtists;
    private int compliantArtists;
    private int nonCompliantArtists;
    private double complianceRate;
    private double totalRevenueTracked;
    private double totalTaxCollected;
    private double totalOutstandingTax;
}

