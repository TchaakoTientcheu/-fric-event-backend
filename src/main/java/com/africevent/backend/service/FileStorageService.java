package com.africevent.backend.service;

import com.africevent.backend.exception.BadRequestException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class FileStorageService {

    private static final int SIGNATURE_LENGTH = 12;

    @Value("${app.upload.dir}")
    private String uploadDir;

    @Value("${app.upload.allowed-extensions}")
    private String[] allowedExtensions;

    private Path rootLocation;
    private Set<String> allowed;

    @PostConstruct
    public void init() throws IOException {
        rootLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(rootLocation);
        allowed = Arrays.stream(allowedExtensions)
                .map(extension -> extension.trim().toLowerCase(Locale.ROOT))
                .filter(extension -> !extension.isEmpty())
                .collect(Collectors.toSet());
    }

    /**
     * Enregistre une image. Le nom d'origine du fichier n'est jamais utilise :
     * l'extension est deduite du contenu reel du fichier (signature binaire).
     */
    public String store(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Le fichier image est obligatoire");
        }

        String extension = detectImageExtension(file);
        if (extension == null || !allowed.contains(extension)) {
            throw new BadRequestException(
                    "Format d'image non autorise. Formats acceptes : " + String.join(", ", allowed));
        }

        String filename = UUID.randomUUID() + "." + extension;
        Path target = rootLocation.resolve(filename).normalize();
        if (!target.startsWith(rootLocation)) {
            throw new BadRequestException("Nom de fichier invalide");
        }

        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return "/uploads/" + filename;
    }

    private String detectImageExtension(MultipartFile file) throws IOException {
        byte[] header;
        try (InputStream in = file.getInputStream()) {
            header = in.readNBytes(SIGNATURE_LENGTH);
        }
        if (header.length < SIGNATURE_LENGTH) {
            return null;
        }
        if (matches(header, 0, 0xFF, 0xD8, 0xFF)) {
            return "jpg";
        }
        if (matches(header, 0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return "png";
        }
        if (matches(header, 0, 0x52, 0x49, 0x46, 0x46) && matches(header, 8, 0x57, 0x45, 0x42, 0x50)) {
            return "webp";
        }
        return null;
    }

    private boolean matches(byte[] data, int offset, int... expected) {
        for (int i = 0; i < expected.length; i++) {
            if ((data[offset + i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }
}