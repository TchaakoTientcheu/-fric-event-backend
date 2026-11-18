package com.africevent.backend.repository;

import com.africevent.backend.model.EmailOtp;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface EmailOtpRepository extends MongoRepository<EmailOtp, String> {
}