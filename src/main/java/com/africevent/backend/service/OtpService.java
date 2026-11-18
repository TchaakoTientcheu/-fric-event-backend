package com.africevent.backend.service;

import com.africevent.backend.exception.BadRequestException;
import com.africevent.backend.model.EmailOtp;
import com.africevent.backend.repository.EmailOtpRepository;
import com.africevent.backend.util.CryptoUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * Codes a usage unique envoyes par email. Le code n'est jamais stocke ni journalise :
 * seule son empreinte HMAC l'est. La consommation est atomique (un code ne sert qu'une fois),
 * le nombre d'essais est limite et un delai separe deux envois.
 */
@Service
@RequiredArgsConstructor
public class OtpService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String MSG_INVALID = "Code invalide ou expiré. Veuillez demander un nouveau code.";

    private final EmailOtpRepository otpRepository;
    private final MailService mailService;
    private final MongoTemplate mongoTemplate;

    @Value("${app.jwt.secret}")
    private String hashKey;

    @Value("${app.otp.length:6}")
    private int length;

    @Value("${app.otp.expiration-minutes:10}")
    private int expirationMinutes;

    @Value("${app.otp.resend-seconds:60}")
    private int resendSeconds;

    @Value("${app.otp.max-attempts:5}")
    private int maxAttempts;

    public String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    /** Genere et envoie un code. Refuse un renvoi trop rapproche (429). */
    public void send(String email) {
        String normalized = normalizeEmail(email);
        LocalDateTime now = LocalDateTime.now();

        EmailOtp otp = otpRepository.findById(normalized).orElseGet(EmailOtp::new);
        if (otp.getLastSentAt() != null && otp.getLastSentAt().plusSeconds(resendSeconds).isAfter(now)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Un code vient d'être envoyé. Veuillez patienter avant d'en demander un nouveau.");
        }

        String code = generateCode();
        otp.setId(normalized);
        otp.setCodeHash(hash(normalized, code));
        otp.setExpiresAt(now.plusMinutes(expirationMinutes));
        otp.setLastSentAt(now);
        otp.setAttempts(0);
        otpRepository.save(otp);

        try {
            mailService.sendOtp(normalized, code, expirationMinutes);
        } catch (RuntimeException e) {
            // L'envoi a echoue : le delai d'attente ne doit pas penaliser le client.
            otpRepository.deleteById(normalized);
            throw e;
        }
    }

    /** Verifie et consomme le code de facon atomique. Leve une erreur 400 si le code est invalide. */
    public void verifyAndConsume(String email, String code) {
        String normalized = normalizeEmail(email);
        String candidate = code == null ? "" : code.trim();

        Query valid = Query.query(Criteria.where("id").is(normalized)
                .and("codeHash").is(hash(normalized, candidate))
                .and("expiresAt").gt(LocalDateTime.now())
                .and("attempts").lt(maxAttempts));

        if (mongoTemplate.findAndRemove(valid, EmailOtp.class) != null) {
            return;
        }

        mongoTemplate.updateFirst(
                Query.query(Criteria.where("id").is(normalized)),
                new Update().inc("attempts", 1),
                EmailOtp.class);
        throw new BadRequestException(MSG_INVALID);
    }

    private String generateCode() {
        int digits = Math.max(4, Math.min(9, length));
        int bound = (int) Math.pow(10, digits);
        return String.format("%0" + digits + "d", RANDOM.nextInt(bound));
    }

    private String hash(String normalizedEmail, String code) {
        return CryptoUtil.hmacSha256Hex(hashKey, normalizedEmail + ":" + code);
    }
}