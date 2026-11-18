package com.africevent.backend.controller;

import com.africevent.backend.dto.CreateUserRequest;
import com.africevent.backend.dto.UserResponse;
import com.africevent.backend.exception.ConflictException;
import com.africevent.backend.exception.ResourceNotFoundException;
import com.africevent.backend.model.User;
import com.africevent.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @GetMapping
    public List<UserResponse> getAll() {
        return userRepository.findAll().stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @PostMapping
    public ResponseEntity<UserResponse> create(@Valid @RequestBody CreateUserRequest req) {
        String email = req.getEmail().trim().toLowerCase(Locale.ROOT);
        if (userRepository.findByEmail(email) != null) {
            throw new ConflictException("Email déjà utilisé");
        }

        User user = new User();
        user.setNom(req.getNom().trim());
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setTelephone(req.getTelephone() == null ? null : req.getTelephone().trim());
        user.setRole(req.getRole());

        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(userRepository.save(user)));
    }

    @GetMapping("/by-email/{email}")
    public UserResponse findByEmail(@PathVariable String email) {
        User user = userRepository.findByEmail(email.trim());
        if (user == null) {
            user = userRepository.findByEmail(email.trim().toLowerCase(Locale.ROOT));
        }
        if (user == null) {
            throw new ResourceNotFoundException("Utilisateur introuvable");
        }
        return toResponse(user);
    }

    private UserResponse toResponse(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .nom(user.getNom())
                .email(user.getEmail())
                .telephone(user.getTelephone())
                .role(user.getRole())
                .build();
    }
}