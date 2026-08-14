package com.example.gestioncommerciale.service;

import com.example.gestioncommerciale.config.SocieteProperties;
import com.example.gestioncommerciale.entity.Client;
import com.example.gestioncommerciale.entity.ClientEntreprise;
import com.example.gestioncommerciale.entity.ClientParticulier;
import com.example.gestioncommerciale.entity.Facture;
import com.example.gestioncommerciale.entity.LigneFacture;
import com.example.gestioncommerciale.entity.Utilisateur;
import com.example.gestioncommerciale.exception.ResourceNotFoundException;
import com.example.gestioncommerciale.repository.FactureRepository;
import com.example.gestioncommerciale.security.CurrentUserService;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.hibernate.Hibernate;
import org.jsoup.Jsoup;
import org.jsoup.helper.W3CDom;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
 * Genere le PDF d'une facture sur la mise en page SOGETHERM, comme le devis.
 * Deux particularites : l'echeance figure dans l'en-tete, et l'etat du reglement
 * (deja regle / reste a payer) accompagne les totaux.
 */
@Service
public class FacturePdfService {

    private static final DateTimeFormatter DATE_FR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final FactureRepository factureRepository;
    private final SpringTemplateEngine templateEngine;
    private final SocieteProperties societe;
    private final String conditionReglement;
    private final CurrentUserService currentUserService;
    private final DecimalFormat montantFormat;

    public FacturePdfService(FactureRepository factureRepository,
                             SpringTemplateEngine templateEngine,
                             SocieteProperties societe,
                             @Value("${app.devis.conditions.reglement:}") String conditionReglement,
                             CurrentUserService currentUserService) {
        this.factureRepository = factureRepository;
        this.templateEngine = templateEngine;
        this.societe = societe;
        this.conditionReglement = conditionReglement;
        this.currentUserService = currentUserService;

        DecimalFormatSymbols symboles = new DecimalFormatSymbols(Locale.FRANCE);
        symboles.setGroupingSeparator(' ');
        symboles.setDecimalSeparator(',');
        this.montantFormat = new DecimalFormat("#,##0.00", symboles);
    }

    @Transactional(readOnly = true)
    public byte[] genererFacturePdf(Long id) {
        Facture facture = factureRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Facture", id));

        Context ctx = new Context(Locale.FRANCE);
        ctx.setVariable("societe", societe);
        ctx.setVariable("logoSogetherm", dataUri("pdf/logos/sogetherm.png"));
        ctx.setVariable("logoIso9001", dataUri("pdf/logos/afaq-iso-9001.png"));
        ctx.setVariable("logoIso14001", dataUri("pdf/logos/afaq-iso-14001.png"));
        ctx.setVariable("logoIso45001", dataUri("pdf/logos/afaq-iso-45001.png"));
        ctx.setVariable("conditionReglement", conditionReglement);

        // En-tete
        ctx.setVariable("numero", facture.getNumero());
        ctx.setVariable("date", facture.getDateFacture() != null
                ? facture.getDateFacture().format(DATE_FR) : "");
        ctx.setVariable("echeance", facture.getDateEcheance() != null
                ? facture.getDateEcheance().format(DATE_FR) : "");
        ctx.setVariable("commandeNumero", facture.getCommande() != null
                ? facture.getCommande().getNumero() : "");
        // Emetteur = celui qui edite le document, comme sur le bon de livraison ;
        // Representant = commercial du client.
        ctx.setVariable("emetteur",
                nomUtilisateur(currentUserService.getUtilisateurCourant()));
        ctx.setVariable("representant", nomUtilisateur(facture.getClient().getCommercial()));

        // Client
        Client client = (Client) Hibernate.unproxy(facture.getClient());
        ctx.setVariable("clientNom", nomClientComplet(client));
        ctx.setVariable("clientAdresse", client.getAdresse());
        ctx.setVariable("clientTelephones", client.getTelephones());
        ctx.setVariable("clientIce", client instanceof ClientEntreprise e ? e.getIce() : null);

        // Lignes
        List<Ligne> lignes = new ArrayList<>();
        BigDecimal tauxTvaAffiche = BigDecimal.ZERO;
        for (LigneFacture l : facture.getLignes()) {
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

        // Totaux et etat du reglement
        BigDecimal ht = valeur(facture.getMontantHT());
        BigDecimal ttc = valeur(facture.getMontantTTC());
        BigDecimal paye = valeur(facture.getMontantPaye());
        ctx.setVariable("totalHT", formatNombre(ht));
        ctx.setVariable("totalTVA", formatNombre(ttc.subtract(ht)));
        ctx.setVariable("netAPayer", formatNombre(ttc));
        ctx.setVariable("montantPaye", formatNombre(paye));
        ctx.setVariable("resteAPayer", formatNombre(ttc.subtract(paye)));
        ctx.setVariable("statut", libelleStatut(facture));
        ctx.setVariable("tauxTVA", tauxTvaAffiche.stripTrailingZeros().toPlainString());
        ctx.setVariable("codeTVA", "C" + tauxTvaAffiche.setScale(0, RoundingMode.HALF_UP).toPlainString());

        return htmlVersPdf(templateEngine.process("facture-pdf", ctx));
    }

    private String libelleStatut(Facture facture) {
        return switch (facture.getStatut()) {
            case EMISE -> "Emise";
            case PARTIELLEMENT_PAYEE -> "Partiellement reglee";
            case PAYEE -> "Reglee";
            case ANNULEE -> "Annulee";
            default -> facture.getStatut().name();
        };
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
            throw new IllegalStateException("Echec de la generation du PDF de la facture", e);
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
        if (c instanceof ClientEntreprise e && e.getRaisonSociale() != null) {
            return e.getRaisonSociale();
        }
        if (c instanceof ClientParticulier p && p.getPrenom() != null) {
            return (p.getPrenom() + " " + c.getNom()).trim();
        }
        return c.getNom();
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
