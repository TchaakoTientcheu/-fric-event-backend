package com.africevent.backend.dto;

import lombok.Data;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

@Data
public class CreateUserRequest {

    @NotBlank(message = "Le nom est obligatoire")
    @Size(max = 100, message = "Le nom ne doit pas dépasser 100 caractères")
    private String nom;

    @NotBlank(message = "L'email est obligatoire")
    @Email(message = "Le format de l'email est invalide")
    @Size(max = 150, message = "L'email ne doit pas dépasser 150 caractères")
    private String email;

    @NotBlank(message = "Le mot de passe est obligatoire")
    @Size(min = 8, max = 72, message = "Le mot de passe doit contenir entre 8 et 72 caractères")
    private String password;

    @Pattern(regexp = "^(\\+?[0-9]{8,15})?$", message = "Le numéro de téléphone est invalide")
    private String telephone;

    @NotBlank(message = "Le rôle est obligatoire")
    @Pattern(regexp = "ADMIN|CLIENT", message = "Le rôle doit être ADMIN ou CLIENT")
    private String role;
}