package com.africevent.backend.config;

import com.africevent.backend.model.User;
import com.africevent.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Order(1)
public class AdminSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;

    @Value("${app.admin.email:tchaakofreedy8@gmail.com}")
    private String adminEmail;

    @Value("${app.admin.password:Admin@1234}")
    private String adminPassword;

    @Value("${app.admin.nom:Administrateur}")
    private String adminNom;

    @Value("${app.admin.telephone:+237600000000}")
    private String adminTelephone;

    @Override
    public void run(String... args) {
        // DIAGNOSTIC TEMPORAIRE : origine et longueur du mot de passe, jamais sa valeur
        log.info("Diagnostic admin : mot de passe lu depuis [{}], longueur {}",
                sourceOf("app.admin.password"), adminPassword.length());

        User existing = userRepository.findByEmail(adminEmail);

        if (existing != null) {
            log.info("Admin déjà existant avec l'email {} — aucune action", adminEmail);
            return;
        }

        User admin = new User();
        admin.setNom(adminNom);
        admin.setEmail(adminEmail);
        admin.setPassword(passwordEncoder.encode(adminPassword));
        admin.setTelephone(adminTelephone);
        admin.setRole("ADMIN");

        userRepository.save(admin);

        // Le mot de passe n'est jamais écrit dans les logs
        log.info("Utilisateur ADMIN créé avec succès : {}", adminEmail);
    }

    /** DIAGNOSTIC TEMPORAIRE : nom de la première source de configuration qui définit la propriété */
    private String sourceOf(String property) {
        if (environment instanceof ConfigurableEnvironment) {
            for (PropertySource<?> source : ((ConfigurableEnvironment) environment).getPropertySources()) {
                if (source.containsProperty(property)) {
                    return source.getName();
                }
            }
        }
        return "valeur par défaut du code";
    }
}