package com.example.gestioncommerciale.service;

import com.example.gestioncommerciale.config.SocieteProperties;
import com.example.gestioncommerciale.entity.Client;
import com.example.gestioncommerciale.entity.ClientEntreprise;
import com.example.gestioncommerciale.entity.ClientParticulier;
import com.example.gestioncommerciale.entity.Devis;
import com.example.gestioncommerciale.entity.LigneDevis;
import com.example.gestioncommerciale.entity.Utilisateur;
import com.example.gestioncommerciale.exception.ResourceNotFoundException;
import com.example.gestioncommerciale.repository.DevisRepository;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.jsoup.Jsoup;
import org.jsoup.helper.W3CDom;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Genere le PDF d'un devis a partir d'un template HTML (Thymeleaf) rendu en PDF
 * par openhtmltopdf. Le HTML de Thymeleaf est d'abord parse par jsoup (tolerant)
 * puis converti en DOM W3C, ce qu'attend openhtmltopdf.
 */
@Service
public class DevisPdfService {

    private static final DateTimeFormatter DATE_FR = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final DevisRepository devisRepository;
    private final SpringTemplateEngine templateEngine;
    private final SocieteProperties societe;
    private final String conditionLivraison;
    private final String conditionReglement;
    private final String conditionValidite;
    private final PolitiquePouvoirs politiquePouvoirs;
    private final DecimalFormat montantFormat;

    public DevisPdfService(DevisRepository devisRepository,
                           SpringTemplateEngine templateEngine,
                           SocieteProperties societe,
                           @Value("${app.devis.conditions.livraison:}") String conditionLivraison,
                           @Value("${app.devis.conditions.reglement:}") String conditionReglement,
                           @Value("${app.devis.conditions.validite:}") String conditionValidite,
                           PolitiquePouvoirs politiquePouvoirs) {
        this.devisRepository = devisRepository;
        this.templateEngine = templateEngine;
        this.societe = societe;
        this.conditionLivraison = conditionLivraison;
        this.conditionReglement = conditionReglement;
        this.conditionValidite = conditionValidite;
        this.politiquePouvoirs = politiquePouvoirs;

        DecimalFormatSymbols symboles = new DecimalFormatSymbols(Locale.FRANCE);
        symboles.setGroupingSeparator(' ');
        symboles.setDecimalSeparator(',');
        this.montantFormat = new DecimalFormat("#,##0.00", symboles);
    }

    @Transactional(readOnly = true)
    public byte[] genererDevisPdf(Long id) {
        Devis devis = devisRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Devis", id));

        // Tant que la remise n'est pas tranchee, rien n'en sort sur papier :
        // un PDF imprime circule aussi bien qu'un email.
        if (politiquePouvoirs.validationAttendue(devis)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "La remise de ce devis doit d'abord etre validee par le responsable "
                            + "commercial : il ne peut pas encore etre imprime");
        }

        Context ctx = new Context(Locale.FRANCE);
        ctx.setVariable("societe", societe);
        ctx.setVariable("logoSogetherm", dataUri("pdf/logos/sogetherm.png"));
        ctx.setVariable("logoIso9001", dataUri("pdf/logos/afaq-iso-9001.png"));
        ctx.setVariable("logoIso14001", dataUri("pdf/logos/afaq-iso-14001.png"));
        ctx.setVariable("logoIso45001", dataUri("pdf/logos/afaq-iso-45001.png"));

        // Conditions
        ctx.setVariable("conditionLivraison", conditionLivraison);
        ctx.setVariable("conditionReglement", conditionReglement);
        ctx.setVariable("conditionValidite", conditionValidite);

        // En-tete devis
        ctx.setVariable("numero", devis.getNumero());
        ctx.setVariable("reference", devis.getReference());
        ctx.setVariable("date", devis.getDateCreation() != null
                ? devis.getDateCreation().format(DATE_FR) : "");
        // Emetteur = auteur du devis ; Representant = commercial du client
        ctx.setVariable("emetteur", nomUtilisateur(devis.getCommercial()));
        ctx.setVariable("representant", nomUtilisateur(devis.getClient().getCommercial()));

        // Client
        Client client = devis.getClient();
        ctx.setVariable("clientNom", nomClientComplet(client));
        ctx.setVariable("clientAdresse", client.getAdresse());
        ctx.setVariable("clientTelephones", client.getTelephones());

        // Lignes
        List<Ligne> lignes = new ArrayList<>();
        BigDecimal tauxTvaAffiche = BigDecimal.ZERO;
        for (LigneDevis l : devis.getLignes()) {
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

        // Totaux
        BigDecimal ht = valeur(devis.getMontantHT());
        BigDecimal ttc = valeur(devis.getMontantTTC());
        BigDecimal tva = ttc.subtract(ht);
        ctx.setVariable("totalHT", formatNombre(ht));
        ctx.setVariable("totalTVA", formatNombre(tva));
        ctx.setVariable("netAPayer", formatNombre(ttc));
        // Code TVA : ex. C20 pour 20 %
        ctx.setVariable("tauxTVA", tauxTvaAffiche.stripTrailingZeros().toPlainString());
        ctx.setVariable("codeTVA", "C" + tauxTvaAffiche.setScale(0, java.math.RoundingMode.HALF_UP).toPlainString());

        String html = templateEngine.process("devis-pdf", ctx);
        return htmlVersPdf(html);
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
            throw new IllegalStateException("Echec de la generation du PDF du devis", e);
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
        // Le client vient d'une relation LAZY : c'est un proxy du type de base, donc
        // instanceof echoue tant qu'on ne l'a pas materialise en sous-classe reelle.
        Client client = (Client) org.hibernate.Hibernate.unproxy(c);
        if (client instanceof ClientEntreprise e && e.getRaisonSociale() != null) {
            return e.getRaisonSociale();
        }
        if (client instanceof ClientParticulier p && p.getPrenom() != null) {
            return (p.getPrenom() + " " + client.getNom()).trim();
        }
        return client.getNom();
    }

    /** Ligne du tableau du devis, valeurs pre-formatees pour l'affichage. */
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
