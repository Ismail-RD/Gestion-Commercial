package com.example.gestioncommerciale.service.email;

/**
 * Achemine un email, quel que soit le chemin emprunte.
 *
 * <p>Deux mises en oeuvre coexistent, choisies par {@code app.mail.transport} :
 * le SMTP classique, et l'API HTTPS de Brevo. Cette seconde voie existe parce
 * que beaucoup d'hebergeurs filtrent les ports SMTP sortants pour limiter les
 * envois de courrier indesirable ; le port 443, lui, passe partout.
 *
 * <p>En cas d'echec, l'implementation leve une {@link org.springframework.mail.MailException} :
 * les services metier n'ont ainsi qu'un seul type d'erreur a traiter, le meme
 * hier en SMTP et aujourd'hui en HTTPS.
 */
public interface ExpediteurEmail {

    /**
     * @throws org.springframework.mail.MailException si le message n'a pas pu partir
     */
    void envoyer(MessageEmail message);

    /**
     * Valeur a placer dans l'attribut {@code src} du logo d'en-tete, ou une
     * chaine vide si ce chemin d'envoi ne sait pas afficher d'image.
     *
     * <p>Le SMTP integre l'image au message ({@code cid:}), qui s'affiche alors
     * sans rien demander. L'API de Brevo n'accepte pas les images integrees :
     * elle renvoie vers une adresse publique, que le lecteur devra peut-etre
     * autoriser. Les gabarits masquent l'image quand cette valeur est vide.
     */
    String sourceLogo();
}
