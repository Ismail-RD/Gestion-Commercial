package com.example.gestioncommerciale.service.impl;

import com.example.gestioncommerciale.service.FileStorageService;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.UUID;

@Service
public class FileStorageServiceImpl implements FileStorageService {

    // Types autorises pour une fiche technique : PDF et images courantes.
    private static final Set<String> TYPES_AUTORISES =
            Set.of("application/pdf", "image/jpeg", "image/png");

    private final Path racine;

    public FileStorageServiceImpl(
            @Value("${app.upload.dir:./uploads/fiches-techniques}") String dossier) {
        this.racine = Paths.get(dossier).toAbsolutePath().normalize();
    }

    @PostConstruct
    void initialiser() {
        try {
            Files.createDirectories(racine);
        } catch (IOException e) {
            throw new IllegalStateException("Impossible de creer le dossier d'upload : " + racine, e);
        }
    }

    @Override
    public String stocker(MultipartFile fichier, String prefixe) {
        if (fichier == null || fichier.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Le fichier est vide");
        }
        String type = fichier.getContentType();
        if (type == null || !TYPES_AUTORISES.contains(type.toLowerCase())) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Format non autorise : PDF, JPG ou PNG attendu");
        }

        String extension = extension(fichier.getOriginalFilename());
        String nom = (prefixe != null ? prefixe + "-" : "") + UUID.randomUUID() + extension;
        Path cible = racine.resolve(nom).normalize();
        // Garde-fou anti "path traversal" : la cible doit rester sous la racine.
        if (!cible.startsWith(racine)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nom de fichier invalide");
        }
        try {
            Files.copy(fichier.getInputStream(), cible, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Ecriture du fichier impossible");
        }
        return nom;
    }

    @Override
    public Resource charger(String cheminRelatif) {
        Path fichier = racine.resolve(cheminRelatif).normalize();
        if (!fichier.startsWith(racine)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Chemin invalide");
        }
        try {
            Resource resource = new UrlResource(fichier.toUri());
            if (!resource.exists() || !resource.isReadable()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Fichier introuvable");
            }
            return resource;
        } catch (MalformedURLException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Fichier introuvable");
        }
    }

    @Override
    public void supprimer(String cheminRelatif) {
        if (cheminRelatif == null) {
            return;
        }
        try {
            Path fichier = racine.resolve(cheminRelatif).normalize();
            if (fichier.startsWith(racine)) {
                Files.deleteIfExists(fichier);
            }
        } catch (IOException e) {
            // Suppression best-effort : ne pas faire echouer l'operation metier.
        }
    }

    private String extension(String nomOriginal) {
        String ext = StringUtils.getFilenameExtension(nomOriginal);
        return ext != null ? "." + ext.toLowerCase() : "";
    }
}
