package com.example.taxbackend.repository;

import com.example.taxbackend.models.PayNowDetails;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface PaymentRepository extends MongoRepository<PayNowDetails,String> {
    List<PayNowDetails> findByArtistId(String artistId);
}
