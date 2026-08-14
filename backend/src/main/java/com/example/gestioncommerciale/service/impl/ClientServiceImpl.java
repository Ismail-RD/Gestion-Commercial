package com.example.gestioncommerciale.service.impl;

import com.example.gestioncommerciale.dto.ClientRequest;
import com.example.gestioncommerciale.dto.ClientResponse;
import com.example.gestioncommerciale.dto.PageResponse;
import com.example.gestioncommerciale.dto.RibDto;
import com.example.gestioncommerciale.dto.filter.ClientFilter;
import com.example.gestioncommerciale.entity.Client;
import com.example.gestioncommerciale.entity.ClientEntreprise;
import com.example.gestioncommerciale.entity.ClientParticulier;
import com.example.gestioncommerciale.entity.NiveauNotification;
import com.example.gestioncommerciale.entity.TypeDocument;
import com.example.gestioncommerciale.entity.TypeNotification;
import com.example.gestioncommerciale.entity.Rib;
import com.example.gestioncommerciale.entity.Role;
import com.example.gestioncommerciale.entity.StatutClient;
import com.example.gestioncommerciale.entity.TypeClient;
import com.example.gestioncommerciale.entity.Utilisateur;
import com.example.gestioncommerciale.exception.ResourceNotFoundException;
import com.example.gestioncommerciale.repository.ClientRepository;
import com.example.gestioncommerciale.repository.CommandeRepository;
import com.example.gestioncommerciale.repository.DevisRepository;
import com.example.gestioncommerciale.repository.FactureRepository;
import com.example.gestioncommerciale.repository.UtilisateurRepository;
import com.example.gestioncommerciale.security.CurrentUserService;
import com.example.gestioncommerciale.service.NotificationService;
import com.example.gestioncommerciale.service.PolitiquePouvoirs;
import com.example.gestioncommerciale.service.ClientService;
import com.example.gestioncommerciale.specification.ClientSpecifications;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class ClientServiceImpl implements ClientService {

    private final ClientRepository clientRepository;
    private final UtilisateurRepository utilisateurRepository;
    private final DevisRepository devisRepository;
    private final CommandeRepository commandeRepository;
    private final FactureRepository factureRepository;
    private final CurrentUserService currentUserService;
    private final PolitiquePouvoirs politiquePouvoirs;
    private final NotificationService notifications;

    public ClientServiceImpl(ClientRepository clientRepository,
                             UtilisateurRepository utilisateurRepository,
                             DevisRepository devisRepository,
                             CommandeRepository commandeRepository,
                             FactureRepository factureRepository,
                             CurrentUserService currentUserService,
                             PolitiquePouvoirs politiquePouvoirs,
                             NotificationService notifications) {
        this.clientRepository = clientRepository;
        this.utilisateurRepository = utilisateurRepository;
        this.devisRepository = devisRepository;
        this.commandeRepository = commandeRepository;
        this.factureRepository = factureRepository;
        this.currentUserService = currentUserService;
        this.politiquePouvoirs = politiquePouvoirs;
        this.notifications = notifications;
    }

    @Override
    public ClientResponse creer(ClientRequest request) {
        if (clientRepository.existsByEmail(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Un client avec l'email '" + request.email() + "' existe deja");
        }
        Client client = creerEntite(request);
        appliquerChamps(client, request);
        // Le commercial en charge est celui qui saisit le client : aucune saisie,
        // donc aucune erreur d'attribution possible.
        client.setCommercial(currentUserService.getUtilisateurCourant());
        // Client neuf : aucun encours a ce stade.
        return toResponse(clientRepository.save(client), BigDecimal.ZERO);
    }

    @Override
    public ClientResponse modifier(Long id, ClientRequest request) {
        Client client = getOrThrow(id);
        // Le type est porte par l'heritage JOINED : un PARTICULIER ne peut pas
        // devenir ENTREPRISE. Avant ce garde, la demande etait silencieusement
        // ignoree et ecrasait au passage les champs de l'autre type.
        if (request.typeClient() != client.getTypeClient()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Le type d'un client ne peut pas etre change ("
                            + client.getTypeClient() + " -> " + request.typeClient() + ")");
        }
        if (clientRepository.existsByEmailAndIdNot(request.email(), id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Un client avec l'email '" + request.email() + "' existe deja");
        }
        // Le commercial en charge n'est pas modifiable : il reste celui qui a
        // saisi le client, sinon la tracabilite de la relation client serait perdue.
        appliquerChamps(client, request);
        // Un plafond revu a la baisse peut faire basculer le client en BLOQUE.
        appliquerBlocageSiDepasse(client);
        return toResponse(clientRepository.save(client), encours(client.getId()));
    }

    @Override
    public ClientResponse reattribuer(Long id, Long commercialId) {
        Client client = getOrThrow(id);
        Utilisateur nouveau = utilisateurRepository.findById(commercialId)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur", commercialId));
        if (!nouveau.isActif()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Impossible d'attribuer un client a un compte desactive");
        }
        // Un portefeuille client se confie a la force de vente : l'attribuer a un
        // magasinier ou a un responsable import n'aurait aucun sens, et le rendrait
        // invisible a tout le monde puisque la propriete suit le commercial.
        if (nouveau.getRole() != Role.COMMERCIAL && nouveau.getRole() != Role.RESPONSABLE_COMMERCIAL) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Un client ne peut etre confie qu'a un commercial ou au responsable commercial");
        }
        client.setCommercial(nouveau);
        return toResponse(clientRepository.save(client), encours(id));
    }

    @Override
    @Transactional(readOnly = true)
    public ClientResponse trouverParId(Long id) {
        return toResponse(getOrThrow(id), encours(id));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ClientResponse> lister(ClientFilter filtre, Pageable pageable) {
        Specification<Client> spec = ClientSpecifications.avecFiltre(filtre);
        // Un commercial ne voit que son portefeuille ; l'encadrement voit tout.
        Long restriction = currentUserService.restrictionAuCommercial();
        if (restriction != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("commercial").get("id"), restriction));
        }
        // Encours non calcule en liste (evite un N+1) : disponible sur la fiche detail.
        return PageResponse.from(
                clientRepository.findAll(spec, pageable).map(c -> toResponse(c, null)));
    }

    @Override
    public ClientResponse definirPlafond(Long id, BigDecimal plafondCredit) {
        Client client = getOrThrow(id);
        // Champ laisse vide = aucun credit accorde, donc plafond a 0.
        BigDecimal demande = plafondCredit != null ? plafondCredit : BigDecimal.ZERO;
        exigerPouvoirDAccorder(demande);
        client.setPlafondCredit(demande);
        // Un plafond nouvellement pose (ou abaisse) peut faire basculer en BLOQUE.
        appliquerBlocageSiDepasse(client);
        return toResponse(clientRepository.save(client), encours(id));
    }

    /**
     * Le credit engage la tresorerie : chaque role a une limite de ce qu'il
     * consent seul. Au-dela, la decision revient a l'administrateur, qui n'en a
     * pas.
     */
    private void exigerPouvoirDAccorder(BigDecimal plafond) {
        Utilisateur decideur = currentUserService.getUtilisateurCourant();
        if (politiquePouvoirs.depasseSonPlafondCredit(decideur, plafond)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Un plafond de " + plafond.stripTrailingZeros().toPlainString()
                            + " DH depasse ce que vous pouvez accorder ("
                            + politiquePouvoirs.plafondCreditMaxDe(decideur)
                                    .stripTrailingZeros().toPlainString()
                            + " DH) : seul un administrateur peut aller au-dela");
        }
    }

    @Override
    public ClientResponse debloquer(Long id) {
        Client client = getOrThrow(id);
        client.setStatut(StatutClient.ACTIF);
        return toResponse(clientRepository.save(client), encours(id));
    }

    @Override
    public ClientResponse bloquer(Long id) {
        Client client = getOrThrow(id);
        client.setStatut(StatutClient.BLOQUE);
        return toResponse(clientRepository.save(client), encours(id));
    }

    @Override
    public void reevaluerBlocage(Long clientId) {
        // Appel interne declenche par la facturation : pas de controle de
        // propriete ici, il ne s'agit pas d'un acces utilisateur au dossier.
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Client", clientId));
        appliquerBlocageSiDepasse(client);
        clientRepository.save(client);
    }

    /**
     * Passe le client a BLOQUE si son encours depasse son plafond (jamais
     * l'inverse). Aucun plafond explicite vaut 0 : sans autorisation de credit,
     * la moindre facture impayee bloque le client.
     */
    private void appliquerBlocageSiDepasse(Client client) {
        if (client.estBloque() || encours(client.getId()).compareTo(plafond(client)) <= 0) {
            return;
        }
        client.setStatut(StatutClient.BLOQUE);
        // Le blocage tombe sans que personne ne l'ait decide : il nait d'une
        // facture emise ou d'un paiement rejete. Le commercial doit l'apprendre
        // maintenant, pas au moment ou son client lui redemande un devis.
        notifications.auCommercial(client.getCommercial(), new NotificationService.Alerte(
                TypeNotification.CLIENT_BLOQUE, NiveauNotification.URGENT,
                "Client bloque : " + client.getNom(),
                "Encours de " + encours(client.getId()).setScale(2, RoundingMode.HALF_UP)
                        + " DH pour un plafond de "
                        + plafond(client).setScale(2, RoundingMode.HALF_UP)
                        + " DH. Plus aucun devis ni commande n'est possible avant deblocage.",
                TypeDocument.CLIENT, client.getId()));
    }

    /** Plafond du client, 0 par defaut (il n'existe pas de credit illimite). */
    private BigDecimal plafond(Client client) {
        return client.getPlafondCredit() != null ? client.getPlafondCredit() : BigDecimal.ZERO;
    }

    private BigDecimal encours(Long clientId) {
        return factureRepository.encoursClient(clientId);
    }

    @Override
    public void supprimer(Long id) {
        Client client = getOrThrow(id);
        // Un client rattache a des documents commerciaux ne peut pas disparaitre :
        // cela laisserait devis/commandes/factures sans titulaire.
        if (devisRepository.existsByClientId(id)
                || commandeRepository.existsByClientId(id)
                || factureRepository.existsByClientId(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Ce client ne peut pas etre supprime : il possede des devis, "
                            + "commandes ou factures");
        }
        clientRepository.delete(client);
    }

    private Client getOrThrow(Long id) {
        Client client = clientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Client", id));
        exigerAcces(client);
        return client;
    }

    /**
     * Un commercial n'accede qu'a ses propres clients : sans ce garde, il lui
     * suffirait de deviner un identifiant pour lire ou modifier la fiche d'un
     * collegue.
     */
    private void exigerAcces(Client client) {
        Long restriction = currentUserService.restrictionAuCommercial();
        if (restriction == null) {
            return;
        }
        Long titulaire = client.getCommercial() != null ? client.getCommercial().getId() : null;
        if (!restriction.equals(titulaire)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Ce client est suivi par un autre commercial");
        }
    }

    /** Une chaine vide venant d'un formulaire signifie "non renseigne" -> NULL. */
    private String videEnNull(String valeur) {
        return (valeur == null || valeur.isBlank()) ? null : valeur.trim();
    }

    private Client creerEntite(ClientRequest request) {
        if (request.typeClient() == TypeClient.ENTREPRISE) {
            return ClientEntreprise.builder().build();
        }
        return ClientParticulier.builder().build();
    }


    private void appliquerChamps(Client client, ClientRequest request) {
        client.setNom(request.nom());
        client.setEmail(request.email());
        client.setTelephones(nettoyerTelephones(request.telephones()));
        client.setRibs(versRibs(request.ribs()));
        client.setAdresse(request.adresse());
        // Le plafond de credit ne fait pas partie de la fiche saisie : il se
        // regle separement via definirPlafond, donc on n'y touche pas ici.
        client.setTypeClient(request.typeClient());

        if (client instanceof ClientEntreprise entreprise) {
            entreprise.setRaisonSociale(request.raisonSociale());
            // Un champ laisse vide dans le formulaire arrive en "" : l'absence
            // d'ICE / d'identifiant fiscal doit s'exprimer par NULL (les
            // contraintes base refusent "").
            entreprise.setIce(videEnNull(request.ice()));
            entreprise.setIdentifiantFiscal(videEnNull(request.identifiantFiscal()));
            entreprise.setContactNom(request.contactNom());
            entreprise.setContactPrenom(request.contactPrenom());
        } else if (client instanceof ClientParticulier particulier) {
            particulier.setPrenom(request.prenom());
            particulier.setCin(videEnNull(request.cin()));
            if (request.dateNaissance() != null && !request.dateNaissance().isBlank()) {
                particulier.setDateNaissance(LocalDate.parse(request.dateNaissance()));
            }
        }
    }

    /** Nettoie une liste de telephones : retire les valeurs vides. */
    private List<String> nettoyerTelephones(List<String> telephones) {
        if (telephones == null) {
            return new ArrayList<>();
        }
        return telephones.stream()
                .filter(t -> t != null && !t.isBlank())
                .map(String::trim)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /** Convertit les RIB du DTO en entites embarquees (ignore les RIB vides). */
    private List<Rib> versRibs(List<RibDto> ribs) {
        if (ribs == null) {
            return new ArrayList<>();
        }
        return ribs.stream()
                .filter(r -> r != null && r.rib() != null && !r.rib().isBlank())
                .map(r -> new Rib(r.rib().trim(), videEnNull(r.banque())))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private List<RibDto> versRibDtos(List<Rib> ribs) {
        return ribs.stream()
                .map(r -> new RibDto(r.getRib(), r.getBanque()))
                .toList();
    }

    private ClientResponse toResponse(Client c, BigDecimal encours) {
        Utilisateur com = c.getCommercial();
        String prenom = null, raisonSociale = null, ice = null, identifiantFiscal = null;
        String contactNom = null, contactPrenom = null, cin = null;
        LocalDate dateNaissance = null;

        if (c instanceof ClientEntreprise e) {
            raisonSociale = e.getRaisonSociale();
            ice = e.getIce();
            identifiantFiscal = e.getIdentifiantFiscal();
            contactNom = e.getContactNom();
            contactPrenom = e.getContactPrenom();
        } else if (c instanceof ClientParticulier p) {
            prenom = p.getPrenom();
            dateNaissance = p.getDateNaissance();
            cin = p.getCin();
        }

        return new ClientResponse(
                c.getId(),
                c.getNom(),
                prenom,
                c.getEmail(),
                c.getTelephones(),
                versRibDtos(c.getRibs()),
                c.getAdresse(),
                c.getTypeClient(),
                c.getDateCreation(),
                com != null ? com.getId() : null,
                com != null ? com.getPrenom() + " " + com.getNom() : null,
                c.getPlafondCredit(),
                c.getStatut(),
                encours,
                raisonSociale,
                ice,
                identifiantFiscal,
                contactNom,
                contactPrenom,
                dateNaissance,
                cin
        );
    }
}
