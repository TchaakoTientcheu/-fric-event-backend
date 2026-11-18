package com.africevent.backend.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Document(collection = "users")
public class User {
    @Id
    private String id;
    private String nom;
    @Indexed(unique = true)
    private String email;
    private String password;
    private String telephone;
    private String role; // ADMIN, CLIENT
}