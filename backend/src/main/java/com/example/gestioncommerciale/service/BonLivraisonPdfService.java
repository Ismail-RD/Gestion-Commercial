package com.example.gestioncommerciale.service;

import com.example.gestioncommerciale.config.BonLivraisonProperties;
import com.example.gestioncommerciale.config.SocieteProperties;
import com.example.gestioncommerciale.entity.Client;
import com.example.gestioncommerciale.entity.ClientEntreprise;
import com.example.gestioncommerciale.entity.ClientParticulier;
import com.example.gestioncommerciale.entity.Commande;
import com.example.gestioncommerciale.entity.LigneCommande;
import com.example.gestioncommerciale.entity.Utilisateur;
import com.example.gestioncommerciale.exception.ResourceNotFoundException;
import com.example.gestioncommerciale.repository.CommandeRepository;
import com.example.gestioncommerciale.security.CurrentUserService;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.jsoup.Jsoup;
import org.jsoup.helper.W3CDom;
import org.jsoup.nodes.Document;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Genere le bon de livraison d'une commande, sur la mise en page SOGETHERM.
 *
 * Deux differences avec le devis : les prix et totaux sont optionnels (un BL
 * part souvent chez le destinataire sans montants), et seul le client emarge.
 */
@Service
public class BonLivraisonPdfService {

    private static final DateTimeFormatter DATE_FR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final CommandeRepository commandeRepository;
    private final SpringTemplateEngine templateEngine;
    private final SocieteProperties societe;
    private final List<String> notes;
    private final CurrentUserService currentUserService;
    private final PolitiquePouvoirs politiquePouvoirs;
    private final DecimalFormat montantFormat;

    public BonLivraisonPdfService(CommandeRepository commandeRepository,
                                  SpringTemplateEngine templateEngine,
                                  SocieteProperties societe,
                                  BonLivraisonProperties bonLivraison,
                                  CurrentUserService currentUserService,
                                  PolitiquePouvoirs politiquePouvoirs) {
        this.commandeRepository = commandeRepository;
        this.templateEngine = templateEngine;
        this.societe = societe;
        this.notes = bonLivraison.notes();
        this.currentUserService = currentUserService;
        this.politiquePouvoirs = politiquePouvoirs;

        DecimalFormatSymbols symboles = new DecimalFormatSymbols(Locale.FRANCE);
        symboles.setGroupingSeparator(' ');
        symboles.setDecimalSeparator(',');
        this.montantFormat = new DecimalFormat("#,##0.00", symboles);
    }

    /**
     * @param avecPrix true pour imprimer les colonnes de prix et le bloc des
     *                 totaux, false pour un bon de livraison sans montants.
     */
    @Transactional(readOnly = true)
    public byte[] genererBonLivraisonPdf(Long commandeId, boolean avecPrix) {
        return genererPdf(commandeId, "BON DE LIVRAISON :", avecPrix, true);
    }

    /**
     * Bon de preparation : liste de picking interne pour preparer la commande.
     * Ressemble au bon de livraison mais sans prix ni signature client.
     */
    @Transactional(readOnly = true)
    public byte[] genererBonPreparationPdf(Long commandeId) {
        return genererPdf(commandeId, "PREPARATION DE LIVRAISON :", false, false);
    }

    private byte[] genererPdf(Long commandeId, String titre, boolean avecPrix, boolean avecSignature) {
        Commande commande = commandeRepository.findById(commandeId)
                .orElseThrow(() -> new ResourceNotFoundException("Commande", commandeId));

        // Une remise non tranchee gele la commande : rien n'en sort sur papier,
        // sinon le document circule avec un prix que personne n'a approuve.
        if (politiquePouvoirs.validationAttendue(commande)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La remise de cette commande doit d'abord etre validee par le "
                            + "responsable commercial");
        }

        Context ctx = new Context(Locale.FRANCE);
        ctx.setVariable("societe", societe);
        ctx.setVariable("logoSogetherm", dataUri("pdf/logos/sogetherm.png"));
        ctx.setVariable("logoIso9001", dataUri("pdf/logos/afaq-iso-9001.png"));
        ctx.setVariable("logoIso14001", dataUri("pdf/logos/afaq-iso-14001.png"));
        ctx.setVariable("logoIso45001", dataUri("pdf/logos/afaq-iso-45001.png"));
        ctx.setVariable("titre", titre);
        ctx.setVariable("avecPrix", avecPrix);
        ctx.setVariable("avecSignature", avecSignature);
        ctx.setVariable("notes", notes);

        // En-tete : le BL porte son propre numero de commande et rappelle la
        // reference du devis d'origine, reperes du client.
        ctx.setVariable("numero", commande.getNumero());
        ctx.setVariable("reference", commande.getDevis() != null
                ? reference(commande) : "");
        ctx.setVariable("date", commande.getDateCommande() != null
                ? commande.getDateCommande().format(DATE_FR) : "");
        // Emetteur = celui qui edite le document : c'est lui qui le sort et le
        // remet, quel que soit le createur de la commande.
        ctx.setVariable("emetteur",
                nomUtilisateur(currentUserService.getUtilisateurCourant()));
        ctx.setVariable("representant", nomUtilisateur(commande.getClient().getCommercial()));

        Client client = commande.getClient();
        ctx.setVariable("clientNom", nomClientComplet(client));
        ctx.setVariable("clientAdresse", client.getAdresse());
        ctx.setVariable("clientTelephones", client.getTelephones());

        List<Ligne> lignes = new ArrayList<>();
        BigDecimal tauxTvaAffiche = BigDecimal.ZERO;
        for (LigneCommande l : commande.getLignes()) {
            String reference = l.getProduit() != null ? l.getProduit().getReference() : null;
            String unite = l.getProduit() != null ? l.getProduit().getUniteMesure() : null;
            lignes.add(new Ligne(
                    reference != null ? reference : "",
                    l.getDesignation(),
                    unite != null ? unite : "",
                    formatNombre(l.getQuantite()),
                    formatNombre(l.getPrixUnitaire()),
                    formatNombre(l.getMontantLigne())));
            if (l.getTauxTVA() != null) {
                tauxTvaAffiche = l.getTauxTVA();
            }
        }
        ctx.setVariable("lignes", lignes);

        BigDecimal ht = valeur(commande.getMontantHT());
        BigDecimal ttc = valeur(commande.getMontantTTC());
        ctx.setVariable("totalHT", formatNombre(ht));
        ctx.setVariable("totalTVA", formatNombre(ttc.subtract(ht)));
        ctx.setVariable("netAPayer", formatNombre(ttc));
        ctx.setVariable("tauxTVA", tauxTvaAffiche.stripTrailingZeros().toPlainString());
        ctx.setVariable("codeTVA", "C" + tauxTvaAffiche.setScale(0, RoundingMode.HALF_UP).toPlainString());

        return htmlVersPdf(templateEngine.process("bon-livraison-pdf", ctx));
    }

    /** Reference du devis d'origine, a defaut son numero. */
    private String reference(Commande commande) {
        String ref = commande.getDevis().getReference();
        return ref != null && !ref.isBlank() ? ref : commande.getDevis().getNumero();
    }

    private byte[] htmlVersPdf(String html) {
        Document jsoupDoc = Jsoup.parse(html);
        jsoupDoc.outputSettings().syntax(Document.OutputSettings.Syntax.xml);
        org.w3c.dom.Document w3c = new W3CDom().fromJsoup(jsoupDoc);

        try (ByteArrayOutputStream os = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withW3cDocument(w3c, null);
            builder.toStream(os);
            builder.run();
            return os.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Echec de la generation du bon de livraison", e);
        }
    }

    /** Encode une ressource image du classpath en data URI base64 (vide si absente). */
    private String dataUri(String chemin) {
        try {
            ClassPathResource res = new ClassPathResource(chemin);
            if (!res.exists()) {
                return "";
            }
            byte[] bytes = res.getInputStream().readAllBytes();
            return "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(bytes);
        } catch (Exception e) {
            return "";
        }
    }

    private String formatNombre(BigDecimal valeur) {
        return montantFormat.format(valeur != null ? valeur : BigDecimal.ZERO);
    }

    private BigDecimal valeur(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private String nomUtilisateur(Utilisateur u) {
        return u != null ? (u.getPrenom() + " " + u.getNom()).trim() : "";
    }

    private String nomClientComplet(Client c) {
        // Relation LAZY : materialiser le proxy en sous-classe reelle avant instanceof.
        Client client = (Client) org.hibernate.Hibernate.unproxy(c);
        if (client instanceof ClientEntreprise e && e.getRaisonSociale() != null) {
            return e.getRaisonSociale();
        }
        if (client instanceof ClientParticulier p && p.getPrenom() != null) {
            return (p.getPrenom() + " " + client.getNom()).trim();
        }
        return client.getNom();
    }

    /** Ligne du tableau, valeurs pre-formatees pour l'affichage. */
    public record Ligne(
            String reference,
            String designation,
            String unite,
            String quantite,
            String prixUnitaire,
            String montant
    ) {
    }
}
