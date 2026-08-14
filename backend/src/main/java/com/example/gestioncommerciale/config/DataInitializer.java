package com.example.gestioncommerciale.config;

import com.example.gestioncommerciale.entity.*;
import com.example.gestioncommerciale.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UtilisateurRepository utilisateurRepository;
    private final PouvoirRoleRepository pouvoirRoleRepository;
    private final DepotRepository depotRepository;
    private final CategorieRepository categorieRepository;
    private final MarqueRepository marqueRepository;
    private final FournisseurRepository fournisseurRepository;
    private final ClientRepository clientRepository;
    private final ProduitRepository produitRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(UtilisateurRepository utilisateurRepository,
                           PouvoirRoleRepository pouvoirRoleRepository,
                           DepotRepository depotRepository,
                           CategorieRepository categorieRepository,
                           MarqueRepository marqueRepository,
                           FournisseurRepository fournisseurRepository,
                           ClientRepository clientRepository,
                           ProduitRepository produitRepository,
                           PasswordEncoder passwordEncoder) {
        this.utilisateurRepository = utilisateurRepository;
        this.pouvoirRoleRepository = pouvoirRoleRepository;
        this.depotRepository = depotRepository;
        this.categorieRepository = categorieRepository;
        this.marqueRepository = marqueRepository;
        this.fournisseurRepository = fournisseurRepository;
        this.clientRepository = clientRepository;
        this.produitRepository = produitRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        seedAdmin();
        seedPouvoirs();
        seedDepots();
        seedCategories();
        seedMarques();
        seedFournisseurs();
        seedClients();
        seedProduits();
    }

    /**
     * Chaque compte est cree independamment : un garde global du type
     * "count() == 0" empecherait tout nouveau compte d'apparaitre sur une base
     * deja amorcee.
     */
    private void seedAdmin() {
        creerUtilisateurSiAbsent("admin@gestioncommerciale.local", "Admin", "Systeme",
                "Admin@123", Role.ADMIN);
        creerUtilisateurSiAbsent("m.benali@sogetherm.ma", "Benali", "Mohamed",
                "Commercial@123", Role.COMMERCIAL);
        creerUtilisateurSiAbsent("i.rachid@sogetherm.ma", "rachid", "ismail",
                "Magazinier@123", Role.MAGASINIER);
        creerUtilisateurSiAbsent("import@sogetherm.ma","rachid","rizki","Import@123",Role.RESPONSABLE_IMPORT);
        creerUtilisateurSiAbsent("l.rachid@sogetherm.ma", "bennani", "ismail",
                "Comptable@123", Role.COMPTABLE);
    }

    private void creerUtilisateurSiAbsent(String email, String nom, String prenom,
                                          String motDePasse, Role role) {
        if (utilisateurRepository.existsByEmail(email)) {
            return;
        }
        utilisateurRepository.save(Utilisateur.builder()
                .nom(nom)
                .prenom(prenom)
                .email(email)
                .motDePasse(passwordEncoder.encode(motDePasse))
                .role(role)
                .actif(true)
                .build());
        log.info("Compte cree : {} ({})", email, role);
    }

    /**
     * Valeurs de depart des pouvoirs. La migration les pose deja sur PostgreSQL ;
     * ce filet sert aux schemas crees directement depuis les entites, ou une
     * table vide priverait chaque role de tout pouvoir.
     */
    private void seedPouvoirs() {
        // Le commercial ne fixe pas les plafonds de credit : pas de montant.
        creerPouvoirSiAbsent(Role.COMMERCIAL, new BigDecimal("20"), null);
        creerPouvoirSiAbsent(Role.RESPONSABLE_COMMERCIAL,
                new BigDecimal("50"), new BigDecimal("100000"));
    }

    private void creerPouvoirSiAbsent(Role role, BigDecimal seuilRemisePct,
                                      BigDecimal plafondCreditMax) {
        if (pouvoirRoleRepository.existsById(role)) {
            return;
        }
        pouvoirRoleRepository.save(PouvoirRole.builder()
                .role(role)
                .seuilRemisePct(seuilRemisePct)
                .plafondCreditMax(plafondCreditMax)
                .build());
    }

    private void seedDepots() {
        // Deux depots identifies par leur code
        for (String code : new String[]{"SH", "AB"}) {
            if (!depotRepository.existsByCode(code)) {
                depotRepository.save(Depot.builder().code(code).build());
            }
        }
    }

    private void seedCategories() {
        if (categorieRepository.count() == 0) {
            String[][] cats = {
                    {"Chauffage", "Chaudières, radiateurs, pompes à chaleur"},
                    {"Climatisation", "Split, multisplit, VRV, climatisation centrale"},
                    {"Solaire", "Chauffe-eau solaire, capteurs solaires, photovoltaïque"},
                    {"Ventilation", "CTA, VMC, extraction, ventilation industrielle"},
                    {"Plomberie", "Raccordements, tuyauterie, sanitaire"},
                    {"Froid commercial", "Chillers, groupes froid, vitrines réfrigérées"},
                    {"Électricité", "Tableaux, câblage, détection, domotique"}
            };
            for (String[] cat : cats) {
                categorieRepository.save(Categorie.builder()
                        .nom(cat[0])
                        .description(cat[1])
                        .build());
            }
            log.info("7 categories HVAC creees");
        }
    }

    private void seedMarques() {
        if (marqueRepository.count() == 0) {
            Object[][] data = {
                    {"Daikin", null, "+212 522 800 300", "contact@daikin.ma", "Casablanca", "www.daikin.ma"},
                    {"Mitsubishi Electric", null, "+212 522 800 301", "contact@mitsubishi.ma", "Casablanca", "www.mitsubishielectric.com"},
                    {"Trane", null, "+212 522 800 302", "contact@trane.ma", "Casablanca", "www.trane.com"},
                    {"Viessmann", null, "+212 522 800 303", "contact@viessmann.ma", "Casablanca", "www.viessmann.com"},
                    {"Atlantic", null, "+212 522 800 304", "contact@atlantic.ma", "Casablanca", "www.atlantic.fr"},
                    {"Vaillant", null, "+212 522 800 305", "contact@vaillant.ma", "Casablanca", "www.vaillant.fr"},
                    {"Thermor", null, "+212 522 800 306", "contact@thermor.ma", "Casablanca", "www.thermor.fr"},
                    {"Lifosol", null, "+212 522 800 307", "contact@lifosol.ma", "Casablanca", "www.lifosol.com"},
                    {"Carrier", null, "+212 522 800 308", "contact@carrier.ma", "Casablanca", "www.carrier.com"},
                    {"Bosch", null, "+212 522 800 309", "contact@bosch.ma", "Casablanca", "www.bosch-thermotechnik.fr"}
            };
            for (Object[] d : data) {
                marqueRepository.save(Marque.builder()
                        .nom((String) d[0])
                        .logo((String) d[1])
                        .telephone((String) d[2])
                        .email((String) d[3])
                        .adresse((String) d[4])
                        .siteWeb((String) d[5])
                        .build());
            }
            log.info("10 marques HVAC creees");
        }
    }

    private void seedFournisseurs() {
        if (fournisseurRepository.count() == 0) {
            Fournisseur f1 = FournisseurEntreprise.builder()
                    .nom("Daikin Morocco")
                    .email("fournisseur@daikin.ma")
                    .telephones(List.of("+212 522 500 100"))
                    .adresse("Zone Industrielle Ain Sebaa, Casablanca")
                    .raisonSociale("Daikin Air Conditioning Morocco SARL")
                    .ice("001234567000045")
                    .identifiantFiscal("12345678")
                    .build();
            Fournisseur f2 = FournisseurEntreprise.builder()
                    .nom("Trane Technologies Maroc")
                    .email("fournisseur@trane.ma")
                    .telephones(List.of("+212 522 500 200"))
                    .adresse("Boulevard Zerktouni, Casablanca")
                    .raisonSociale("Trane Technologies Maroc SARL")
                    .ice("001987654000032")
                    .identifiantFiscal("98765432")
                    .build();
            Fournisseur f3 = FournisseurEntreprise.builder()
                    .nom("Viessmann Maroc")
                    .email("fournisseur@viessmann.ma")
                    .telephones(List.of("+212 522 500 300"))
                    .adresse("Route d'El Jadida, Casablanca")
                    .raisonSociale("Viessmann Climatisation et Chauffage Maroc")
                    .ice("001122334000078")
                    .identifiantFiscal("11223344")
                    .build();
            Fournisseur f4 = FournisseurEntreprise.builder()
                    .nom("Atlantic Distribution Maroc")
                    .email("fournisseur@atlantic.ma")
                    .telephones(List.of("+212 522 500 400"))
                    .adresse("Casa nearshore, Casablanca")
                    .raisonSociale("Atlantic SARL")
                    .ice("001556677000091")
                    .build();
            fournisseurRepository.save(f1);
            fournisseurRepository.save(f2);
            fournisseurRepository.save(f3);
            fournisseurRepository.save(f4);
            log.info("4 fournisseurs crees");
        }
    }

    private void seedClients() {
        if (clientRepository.count() == 0) {
            Utilisateur commercial = utilisateurRepository.findAll().stream()
                    .filter(u -> u.getRole() == Role.COMMERCIAL)
                    .findFirst().orElse(null);

            // Clients entreprises
            ClientEntreprise c1 = ClientEntreprise.builder()
                    .nom("BTP Construction")
                    .email("contact@btp-construction.ma")
                    .telephones(List.of("+212 522 600 100"))
                    .adresse("Zone Industrielle Sidi Bernoussi")
                    .commercial(commercial)
                    .raisonSociale("BTP Construction SARL")
                    .ice("002998877000012")
                    .contactNom("Alami")
                    .contactPrenom("Hassan")
                    .build();
            ClientEntreprise c2 = ClientEntreprise.builder()
                    .nom("Hotel Mogador")
                    .email("tech@hotel-mogador.ma")
                    .telephones(List.of("+212 522 600 200"))
                    .adresse("Boulevard Mohammed V, Casablanca")
                    .commercial(commercial)
                    .raisonSociale("Hotel Mogador Palace SARL")
                    .ice("002443322000056")
                    .contactNom("Tazi")
                    .contactPrenom("Fatima")
                    .build();
            ClientEntreprise c3 = ClientEntreprise.builder()
                    .nom("Clinique Sainte Marie")
                    .email("admin@clinique-sainte-marie.ma")
                    .telephones(List.of("+212 522 600 300"))
                    .adresse("Avenue des FAR, Casablanca")
                    .commercial(commercial)
                    .raisonSociale("Clinique Sainte Marie SA")
                    .ice("002667788000034")
                    .contactNom("Bennani")
                    .contactPrenom("Dr. Ahmed")
                    .build();

            // Clients particuliers
            ClientParticulier c4 = ClientParticulier.builder()
                    .nom("El Fassi")
                    .email("el.fassi@gmail.com")
                    .telephones(List.of("+212 661 100 200"))
                    .adresse("Hay Riad, Rabat")
                    .commercial(commercial)
                    .prenom("Karim")
                    .cin("AB123456")
                    .dateNaissance(LocalDate.of(1985, 3, 15))
                    .build();
            ClientParticulier c5 = ClientParticulier.builder()
                    .nom("Ait Ouakrim")
                    .email("ait.ouakrim@yahoo.fr")
                    .telephones(List.of("+212 662 200 300"))
                    .adresse("Anfa, Casablanca")
                    .commercial(commercial)
                    .prenom("Sara")
                    .cin("CD789012")
                    .dateNaissance(LocalDate.of(1990, 7, 22))
                    .build();

            clientRepository.save(c1);
            clientRepository.save(c2);
            clientRepository.save(c3);
            clientRepository.save(c4);
            clientRepository.save(c5);
            log.info("5 clients crees (3 entreprises + 2 particuliers)");
        }
    }

    private void seedProduits() {
        if (produitRepository.count() == 0) {
            Set<Marque> allMarques = new HashSet<>(marqueRepository.findAll());
            Set<Fournisseur> allFournisseurs = new HashSet<>(fournisseurRepository.findAll());

            Object[][] produits = {
                    {"DAI-S200FXM", "Daikin Split 20000 BTU FTXM200R", "Split mural inverter haute efficacité", 8500.00, 20.0, "U", "Climatisation", new String[]{"Daikin"}, new String[]{"Daikin Morocco"}},
                    {"DAI-S120FXM", "Daikin Split 12000 BTU FTXM120R", "Split mural inverter", 5200.00, 20.0, "U", "Climatisation", new String[]{"Daikin"}, new String[]{"Daikin Morocco"}},
                    {"DAI-MULTI100", "Daikin Multisplit VRV IV 100000 BTU", "Système VRV IV gainable", 45000.00, 20.0, "U", "Climatisation", new String[]{"Daikin"}, new String[]{"Daikin Morocco"}},
                    {"TRN-SPLIT18", "Trane Split Mural 18000 BTU", "Split système mural", 6800.00, 20.0, "U", "Climatisation", new String[]{"Trane"}, new String[]{"Trane Technologies Maroc"}},
                    {"TRN-CHILLER350", "Trane Chiller 350 kW", "Groupe d'eau glacée air/eau", 120000.00, 20.0, "U", "Froid commercial", new String[]{"Trane"}, new String[]{"Trane Technologies Maroc"}},
                    {"VIE-CHaudiere100", "Viessmann Chaudière murale gaz Vitodens 100", "Chaudière condensation", 12500.00, 20.0, "U", "Chauffage", new String[]{"Viessmann"}, new String[]{"Viessmann Maroc"}},
                    {"VIE-PAC200", "Viessmann PAC Haute Température 200 kW", "Pompe à chaleur eau chaude", 65000.00, 20.0, "U", "Chauffage", new String[]{"Viessmann"}, new String[]{"Viessmann Maroc"}},
                    {"ATL-SOLAIRE300", "Atlantic Capteur Solaire 300L", "Ballon solaire thermique", 4500.00, 20.0, "U", "Solaire", new String[]{"Atlantic"}, new String[]{"Atlantic Distribution Maroc"}},
                    {"ATL-CH150", "Atlantic Chauffe-eau Thermodynamique 150L", "CET murale haute performance", 3200.00, 20.0, "U", "Solaire", new String[]{"Atlantic"}, new String[]{"Atlantic Distribution Maroc"}},
                    {"MIS-MSZ25", "Mitsubishi Electric MSZ-LN25VG", "Split mural silence", 4800.00, 20.0, "U", "Climatisation", new String[]{"Mitsubishi Electric"}, new String[]{"Daikin Morocco"}},
                    {"BOS-CW400", "Bosch Chaudière au sol Greenstar 400", "Chaudière gaz à condensation", 18000.00, 20.0, "U", "Chauffage", new String[]{"Bosch"}, new String[]{"Viessmann Maroc"}},
                    {"CAR-AC100", "Carrier Aeropack 100 kW", "Groupe pompe à chaleur", 55000.00, 20.0, "U", "Chauffage", new String[]{"Carrier"}, new String[]{"Trane Technologies Maroc"}}
            };

            for (Object[] p : produits) {
                String catNom = (String) p[6];
                Categorie categorie = categorieRepository.findAll().stream()
                        .filter(c -> c.getNom().equals(catNom))
                        .findFirst().orElse(null);

                Set<Marque> marques = new HashSet<>();
                for (String marqueNom : (String[]) p[7]) {
                    marqueRepository.findAll().stream()
                            .filter(m -> m.getNom().equals(marqueNom))
                            .findFirst().ifPresent(marques::add);
                }

                Produit produit = Produit.builder()
                        .reference((String) p[0])
                        .designation((String) p[1])
                        .description((String) p[2])
                        .prixUnitaireHT(BigDecimal.valueOf((double) p[3]))
                        .tauxTVA(BigDecimal.valueOf((double) p[4]))
                        .uniteMesure((String) p[5])
                        .categorie(categorie)
                        .marques(marques)
                        .build();

                // Le premier fournisseur listé devient le fournisseur principal
                boolean principal = true;
                for (String fNom : (String[]) p[8]) {
                    Fournisseur fournisseur = fournisseurRepository.findAll().stream()
                            .filter(f -> f.getNom().equals(fNom))
                            .findFirst().orElse(null);
                    if (fournisseur != null) {
                        produit.ajouterFournisseur(fournisseur, (String) p[0], principal);
                        principal = false;
                    }
                }
                produitRepository.save(produit);
            }
            log.info("12 produits HVAC de test crees");
        }
    }
}
