package com.africevent.backend.service;

import com.africevent.backend.exception.BadRequestException;
import com.africevent.backend.model.User;
import com.africevent.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Comptes clients sans mot de passe : l'email verifie par code sert d'identite.
 * Les comptes ADMIN ne peuvent jamais s'authentifier par code : ils gardent leur mot de passe.
 */
@Service
@RequiredArgsConstructor
public class OtpAccountService {

    private static final String ROLE_CLIENT = "CLIENT";
    private static final String ROLE_ADMIN = "ADMIN";
    private static final int NAME_MAX_LENGTH = 100;
    private static final String MSG_INVALID = "Code invalide ou expiré. Veuillez demander un nouveau code.";

    private final OtpService otpService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    /**
     * Envoie un code. Pour un compte administrateur rien n'est envoye, mais l'appelant
     * repond de la meme facon : on ne revele pas quelles adresses existent.
     */
    public void requestOtp(String email) {
        String normalized = otpService.normalizeEmail(email);
        User existing = userRepository.findByEmail(normalized);
        if (existing != null && ROLE_ADMIN.equals(existing.getRole())) {
            return;
        }
        otpService.send(normalized);
    }

    /** Verifie le code puis retourne le compte, cree au besoin. */
    public User authenticate(String email, String code) {
        String normalized = otpService.normalizeEmail(email);
        otpService.verifyAndConsume(normalized, code);

        User existing = userRepository.findByEmail(normalized);
        if (existing != null) {
            return requireClient(existing);
        }

        User user = new User();
        user.setNom(defaultName(normalized));
        user.setEmail(normalized);
        // Mot de passe aleatoire inconnu de tous : la connexion par mot de passe reste impossible.
        user.setPassword(passwordEncoder.encode(UUID.randomUUID() + UUID.randomUUID().toString()));
        user.setRole(ROLE_CLIENT);

        try {
            return userRepository.save(user);
        } catch (DuplicateKeyException e) {
            User concurrent = userRepository.findByEmail(normalized);
            if (concurrent == null) {
                throw e;
            }
            return requireClient(concurrent);
        }
    }

    private User requireClient(User user) {
        if (ROLE_ADMIN.equals(user.getRole())) {
            throw new BadRequestException(MSG_INVALID);
        }
        return user;
    }

    private String defaultName(String email) {
        int at = email.indexOf('@');
        String name = at > 0 ? email.substring(0, at) : email;
        return name.length() <= NAME_MAX_LENGTH ? name : name.substring(0, NAME_MAX_LENGTH);
    }
}