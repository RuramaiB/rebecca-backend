package com.example.taxbackend.repository;

import com.example.taxbackend.models.TaxConfiguration;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface TaxConfigurationRepository extends MongoRepository<TaxConfiguration, String> {

    // Find active configuration
    Optional<TaxConfiguration> findByActiveTrue();

    Optional<TaxConfiguration> findByCountryAndActiveTrue(String country);

    Optional<TaxConfiguration> findByTaxTypeAndActiveTrue(String taxType);

    // Find by effective date
    List<TaxConfiguration> findByEffectiveFromBeforeAndEffectiveToAfter(
            LocalDateTime now1,
            LocalDateTime now2
    );

    // Find all active configurations
    List<TaxConfiguration> findAllByActiveTrue();
}
