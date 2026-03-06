package com.example.taxbackend.repository;

import com.example.taxbackend.models.TaxRecord;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TaxRecordRepository extends MongoRepository<TaxRecord, String> {

    // Find by artist
    List<TaxRecord> findByArtistId(String artistId);

    List<TaxRecord> findByArtistIdOrderByPeriodStartDesc(String artistId);

    // Find by period
    Optional<TaxRecord> findByArtistIdAndPeriod(String artistId, String period);

    List<TaxRecord> findByPeriod(String period);

    List<TaxRecord> findByTaxYear(String taxYear);

    // Find by payment status
    List<TaxRecord> findByPaymentStatus(TaxRecord.PaymentStatus status);

    List<TaxRecord> findByArtistIdAndPaymentStatus(
            String artistId,
            TaxRecord.PaymentStatus status
    );

    // Find overdue payments
    List<TaxRecord> findByPaymentStatusAndDueDateBefore(
            TaxRecord.PaymentStatus status,
            LocalDateTime date
    );

    // Find by date range
    List<TaxRecord> findByPeriodStartBetween(
            LocalDateTime start,
            LocalDateTime end
    );

    List<TaxRecord> findByArtistIdAndPeriodStartBetween(
            String artistId,
            LocalDateTime start,
            LocalDateTime end
    );

    // Calculate totals
    @Query(value = "{ 'artistId': ?0 }", fields = "{ 'taxAmount': 1 }")
    List<TaxRecord> findTaxAmountsByArtistId(String artistId);

    @Query(value = "{ 'artistId': ?0, 'paymentStatus': 'PAID' }",
            fields = "{ 'taxAmount': 1 }")
    List<TaxRecord> findPaidTaxAmountsByArtistId(String artistId);

    // Outstanding tax
    @Query("{ 'artistId': ?0, 'paymentStatus': { $in: ['PENDING', 'OVERDUE', 'PARTIAL'] } }")
    List<TaxRecord> findOutstandingTaxRecords(String artistId);

    // Recent records
    List<TaxRecord> findTop12ByArtistIdOrderByPeriodStartDesc(String artistId);

    // Count by status
    long countByPaymentStatus(TaxRecord.PaymentStatus status);

    long countByArtistIdAndPaymentStatus(String artistId, TaxRecord.PaymentStatus status);

    // Delete by artist (for cleanup)
    void deleteByArtistId(String artistId);

    List<TaxRecord> findByArtistIdAndTaxTypeOrderByPeriodStartDesc(String artistId, TaxRecord.TaxType taxType);

    Optional<TaxRecord> findByArtistIdAndPeriodAndTaxType(String artistId, String period, TaxRecord.TaxType taxType);
}
