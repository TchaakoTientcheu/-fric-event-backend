package com.africevent.backend.controller;

import com.africevent.backend.exception.BadRequestException;
import com.africevent.backend.model.User;
import com.africevent.backend.repository.UserRepository;
import com.africevent.backend.security.JwtUtil;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.util.Locale;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest req) {
        String email = normalizeEmail(req.email);
        if (userRepository.findByEmail(email) != null) {
            throw new BadRequestException("Email déjà utilisé");
        }
        User user = new User();
        user.setNom(req.nom.trim());
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(req.password));
        user.setTelephone(req.telephone == null ? null : req.telephone.trim());
        user.setRole("CLIENT");
        userRepository.save(user);
        return ResponseEntity.ok(new MessageResponse("Utilisateur créé"));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest req) {
        String email = req.email.trim();
        User user = userRepository.findByEmail(email);
        if (user == null) {
            user = userRepository.findByEmail(normalizeEmail(email));
        }
        if (user == null || !passwordEncoder.matches(req.password, user.getPassword())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new MessageResponse("Identifiants invalides"));
        }
        String token = jwtUtil.generateToken(user.getId(), user.getEmail(), user.getRole());
        return ResponseEntity.ok(new LoginResponse(
                user.getId(),
                token,
                user.getRole(),
                user.getNom()
        ));
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    @Data static class RegisterRequest {
        @NotBlank(message = "Le nom est obligatoire")
        @Size(max = 100, message = "Le nom ne doit pas dépasser 100 caractères")
        public String nom;

        @NotBlank(message = "L'email est obligatoire")
        @Email(message = "Le format de l'email est invalide")
        @Size(max = 150, message = "L'email ne doit pas dépasser 150 caractères")
        public String email;

        @NotBlank(message = "Le mot de passe est obligatoire")
        @Size(min = 8, max = 72, message = "Le mot de passe doit contenir entre 8 et 72 caractères")
        public String password;

        @Pattern(regexp = "^(\\+?[0-9]{8,15})?$", message = "Le numéro de téléphone est invalide")
        public String telephone;
    }

    @Data static class LoginRequest {
        @NotBlank(message = "L'email est obligatoire")
        public String email;

        @NotBlank(message = "Le mot de passe est obligatoire")
        public String password;
    }

    @Data static class MessageResponse {
        private final String message;
    }

    @Data static class LoginResponse {
        public final String id;
        public final String token;
        public final String role;
        public final String nom;
        public LoginResponse(String id, String token, String role, String nom) {
            this.id = id;
            this.token = token;
            this.role = role;
            this.nom = nom;
        }
    }
}