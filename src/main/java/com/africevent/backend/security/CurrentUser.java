package com.africevent.backend.security;

import org.springframework.security.core.Authentication;

public final class CurrentUser {

    private static final String ROLE_ADMIN = "ROLE_ADMIN";

    private CurrentUser() {
    }

    /** Identifiant de l'utilisateur, tel que place par JwtAuthFilter dans le contexte de securite. */
    public static String id(Authentication authentication) {
        return authentication.getName();
    }

    public static boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> ROLE_ADMIN.equals(authority.getAuthority()));
    }
}