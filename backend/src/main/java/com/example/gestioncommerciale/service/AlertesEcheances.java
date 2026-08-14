package com.example.gestioncommerciale.service;

import com.example.gestioncommerciale.entity.CommandeFournisseur;
import com.example.gestioncommerciale.entity.Devis;
import com.example.gestioncommerciale.entity.Facture;
import com.example.gestioncommerciale.entity.NiveauNotification;
import com.example.gestioncommerciale.entity.Paiement;
import com.example.gestioncommerciale.entity.Role;
import com.example.gestioncommerciale.entity.StatutPaiement;
import com.example.gestioncommerciale.entity.TypeDocument;
import com.example.gestioncommerciale.entity.TypeNotification;
import com.example.gestioncommerciale.repository.CommandeFournisseurRepository;
import com.example.gestioncommerciale.repository.DevisRepository;
import com.example.gestioncommerciale.repository.FactureRepository;
import com.example.gestioncommerciale.repository.PaiementRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.WeekFields;
import java.util.List;

/**
 * Alertes que rien ne declenche sinon le temps qui passe.
 *
 * <p>Une echeance depassee, un cheque qui dort, un devis qui va expirer, un
 * conteneur qui n'arrive pas : personne n'agit, donc personne ne serait
 * prevenu. C'est la seule categorie d'alertes qui a besoin d'un balayage.
 *
 * <p><b>Le point delicat est la repetition.</b> Une facture echue depuis trois
 * semaines ne doit pas produire vingt-et-une notifications, mais elle ne doit
 * pas non plus disparaitre apres la premiere : une relance oubliee est de
 * l'argent perdu. Chaque alerte porte donc une cle qui inclut sa periode, et
 * l'index unique en base fait le reste. La periode se choisit selon le rythme
 * auquel il est raisonnable de se faire rappeler la chose :
 *
 * <ul>
 *   <li>facture echue : une fois par mois, au rythme des relances ;</li>
 *   <li>effet a remettre et import en retard : une fois par semaine, parce que
 *       chaque jour perdu coute ;</li>
 *   <li>devis qui expire : une seule fois, la date de fin ne bougera plus.</li>
 * </ul>
 */
@Service
public class AlertesEcheances {

    private static final Logger log = LoggerFactory.getLogger(AlertesEcheances.class);

    private final FactureRepository factureRepository;
    private final PaiementRepository paiementRepository;
    private final DevisRepository devisRepository;
    private final CommandeFournisseurRepository commandeFournisseurRepository;
    private final NotificationService notifications;

    /** Combien de jours avant l'echeance d'un effet on previent la comptabilite. */
    private final int joursAvantEcheanceEffet;

    /** Combien de jours avant sa fin de validite un devis fait l'objet d'une alerte. */
    private final int joursAvantExpirationDevis;

    public AlertesEcheances(FactureRepository factureRepository,
                            PaiementRepository paiementRepository,
                            DevisRepository devisRepository,
                            CommandeFournisseurRepository commandeFournisseurRepository,
                            NotificationService notifications,
                            @Value("${app.alertes.effet-jours-avant:3}") int joursAvantEcheanceEffet,
                            @Value("${app.alertes.devis-jours-avant:7}") int joursAvantExpirationDevis) {
        this.factureRepository = factureRepository;
        this.paiementRepository = paiementRepository;
        this.devisRepository = devisRepository;
        this.commandeFournisseurRepository = commandeFournisseurRepository;
        this.notifications = notifications;
        this.joursAvantEcheanceEffet = joursAvantEcheanceEffet;
        this.joursAvantExpirationDevis = joursAvantExpirationDevis;
    }

    @Transactional
    public void balayer() {
        LocalDate aujourdhui = LocalDate.now();
        int emises = facturesEchues()
                + effetsAEcheance(aujourdhui)
                + devisQuiExpirent(aujourdhui)
                + importsEnRetard(aujourdhui);
        if (emises > 0) {
            log.info("{} alerte(s) d'echeance emise(s)", emises);
        }
    }

    /**
     * Factures dont l'echeance est passee. Elles partent a la comptabilite, qui
     * relance, et au commercial du client, qui connait l'interlocuteur.
     */
    private int facturesEchues() {
        List<Facture> echues = factureRepository.enRetard(null);
        for (Facture f : echues) {
            long jours = f.getDateEcheance() == null ? 0
                    : ChronoUnit.DAYS.between(f.getDateEcheance(), LocalDate.now());
            NotificationService.Alerte alerte = new NotificationService.Alerte(
                    TypeNotification.FACTURE_ECHUE,
                    // Au-dela de deux mois, ce n'est plus un retard, c'est un impaye.
                    jours > 60 ? NiveauNotification.URGENT : NiveauNotification.ALERTE,
                    "Facture " + f.getNumero() + " echue depuis " + jours + " jour(s)",
                    reste(f) + " DH restent dus"
                            + (f.getClient() != null ? " par " + f.getClient().getNom() : "") + ".",
                    TypeDocument.FACTURE, f.getId(),
                    cleMensuelle(TypeNotification.FACTURE_ECHUE, f.getId()));
            notifications.auxRoles(alerte, Role.COMPTABLE);
            if (f.getClient() != null) {
                notifications.auCommercial(f.getClient().getCommercial(), alerte);
            }
        }
        return echues.size();
    }

    /**
     * Effets encore en portefeuille alors que leur echeance approche ou est
     * passee. C'est de la tresorerie immobilisee : un cheque oublie dans un
     * tiroir ne se signale jamais tout seul.
     */
    private int effetsAEcheance(LocalDate aujourdhui) {
        LocalDate limite = aujourdhui.plusDays(joursAvantEcheanceEffet);
        int emises = 0;
        for (Paiement p : paiementRepository.effetsEnAttente()) {
            if (p.getDateEcheance() == null || p.getDateEcheance().isAfter(limite)) {
                continue;
            }
            boolean depassee = p.getDateEcheance().isBefore(aujourdhui);
            // Un effet deja remis en banque suit son cours : seul celui qui
            // dort encore en portefeuille appelle un geste.
            String action = p.getStatut() == StatutPaiement.RECU
                    ? "a remettre en banque" : "remis, encaissement a confirmer";
            notifications.auxRoles(new NotificationService.Alerte(
                            TypeNotification.EFFET_A_REMETTRE,
                            depassee ? NiveauNotification.URGENT : NiveauNotification.ALERTE,
                            (p.getNumeroEffet() != null ? p.getNumeroEffet() : "Effet")
                                    + " " + action,
                            "Echeance le " + p.getDateEcheance() + " — " + p.getMontant() + " DH"
                                    + (p.getFacture() != null
                                            ? " sur " + p.getFacture().getNumero() : "") + ".",
                            TypeDocument.FACTURE,
                            p.getFacture() != null ? p.getFacture().getId() : null,
                            cleHebdomadaire(TypeNotification.EFFET_A_REMETTRE, p.getId())),
                    Role.COMPTABLE);
            emises++;
        }
        return emises;
    }

    /**
     * Devis partis chez le client dont la validite touche a sa fin : c'est le
     * moment de relancer, apres il faudra refaire le prix.
     */
    private int devisQuiExpirent(LocalDate aujourdhui) {
        List<Devis> expirants = devisRepository.expirantAvant(
                aujourdhui, aujourdhui.plusDays(joursAvantExpirationDevis), null);
        for (Devis d : expirants) {
            long jours = ChronoUnit.DAYS.between(aujourdhui, d.getDateValidite());
            notifications.auCommercial(d.getCommercial(), new NotificationService.Alerte(
                    TypeNotification.DEVIS_EXPIRE_BIENTOT, NiveauNotification.ALERTE,
                    "Devis " + d.getNumero() + " expire dans " + jours + " jour(s)",
                    "Sans reponse du client avant le " + d.getDateValidite()
                            + ", le prix sera a refaire.",
                    TypeDocument.DEVIS, d.getId(),
                    // La date de validite ne bougera plus : une alerte suffit.
                    TypeNotification.DEVIS_EXPIRE_BIENTOT + ":" + d.getId()));
        }
        return expirants.size();
    }

    /** Marchandise annoncee et jamais arrivee : le fournisseur ne le dira pas. */
    private int importsEnRetard(LocalDate aujourdhui) {
        List<CommandeFournisseur> retards =
                commandeFournisseurRepository.enRetardArrivee(aujourdhui);
        for (CommandeFournisseur c : retards) {
            long jours = ChronoUnit.DAYS.between(c.getDateArriveePrevue(), aujourdhui);
            notifications.auxRoles(new NotificationService.Alerte(
                            TypeNotification.IMPORT_EN_RETARD,
                            jours > 15 ? NiveauNotification.URGENT : NiveauNotification.ALERTE,
                            c.getNumero() + " : " + jours + " jour(s) de retard",
                            "Arrivee annoncee le " + c.getDateArriveePrevue()
                                    + ", statut " + c.getStatut() + ". A relancer aupres de "
                                    + (c.getFournisseur() != null
                                            ? c.getFournisseur().getNom() : "votre fournisseur") + ".",
                            TypeDocument.COMMANDE_FOURNISSEUR, c.getId(),
                            cleHebdomadaire(TypeNotification.IMPORT_EN_RETARD, c.getId())),
                    Role.RESPONSABLE_IMPORT);
        }
        return retards.size();
    }

    // --- Cles d'idempotence ---

    private String cleMensuelle(TypeNotification type, Long documentId) {
        LocalDate maintenant = LocalDate.now();
        return type + ":" + documentId + ":" + maintenant.getYear() + "-"
                + String.format("%02d", maintenant.getMonthValue());
    }

    private String cleHebdomadaire(TypeNotification type, Long documentId) {
        LocalDate maintenant = LocalDate.now();
        int semaine = maintenant.get(WeekFields.ISO.weekOfWeekBasedYear());
        return type + ":" + documentId + ":"
                + maintenant.get(WeekFields.ISO.weekBasedYear()) + "-S"
                + String.format("%02d", semaine);
    }

    private String reste(Facture f) {
        if (f.getMontantTTC() == null) {
            return "0";
        }
        return f.getMontantTTC()
                .subtract(f.getMontantPaye() != null ? f.getMontantPaye()
                        : java.math.BigDecimal.ZERO)
                .toPlainString();
    }
}
