package com.africevent.backend.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuthResponse {
    private String id;
    private String token;
    private String role;
    private String nom;
    private String email;
}