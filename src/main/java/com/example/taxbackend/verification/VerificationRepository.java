package com.example.taxbackend.verification;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface VerificationRepository extends MongoRepository<Verification, String> {


    Optional<Verification> findByEmail(String email);

    Optional<Verification> findByEmailAndVerificationCode(String email, String verificationCode);
}
