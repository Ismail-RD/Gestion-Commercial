package com.example.gestioncommerciale.service;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/**
 * Stockage de fichiers sur le systeme de fichiers. Seul le chemin relatif
 * retourne est conserve en base ; le binaire vit sur disque.
 */
public interface FileStorageService {

    /**
     * Valide (type MIME autorise, non vide) puis ecrit le fichier sous un nom
     * unique. Retourne le chemin relatif a stocker en base.
     */
    String stocker(MultipartFile fichier, String prefixe);

    /** Charge un fichier precedemment stocke pour le telechargement. */
    Resource charger(String cheminRelatif);

    /** Supprime le fichier sur disque (sans erreur s'il est deja absent). */
    void supprimer(String cheminRelatif);
}
