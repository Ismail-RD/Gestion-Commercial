package com.example.gestioncommerciale.controller;

import com.example.gestioncommerciale.dto.TableauBordResponse;
import com.example.gestioncommerciale.dto.TableauBordStockResponse;
import com.example.gestioncommerciale.security.Autorisations;
import com.example.gestioncommerciale.service.TableauBordService;
import com.example.gestioncommerciale.service.TableauBordStockService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/tableau-de-bord")
public class TableauBordController {

    /** Bornes de la fenetre d'observation : en dehors, les chiffres n'ont plus de sens. */
    private static final int JOURS_MIN = 7;
    private static final int JOURS_MAX = 365;

    private final TableauBordStockService tableauBordStockService;
    private final TableauBordService tableauBordService;

    public TableauBordController(TableauBordStockService tableauBordStockService,
                                 TableauBordService tableauBordService) {
        this.tableauBordStockService = tableauBordStockService;
        this.tableauBordService = tableauBordService;
    }

    /**
     * Tableau de bord de l'utilisateur connecte, faconne par son role. Ouvert a
     * tous : chacun n'y voit que ce que son role lui donne, et le commercial que
     * son propre portefeuille.
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public TableauBordResponse mien() {
        return tableauBordService.construire();
    }

    /**
     * Vision d'ensemble du stock. Ouverte a qui consulte deja le stock : le
     * magasinier y decide ses transferts, le responsable import ses achats, et
     * la direction y lit l'argent immobilise.
     */
    @GetMapping("/stock")
    @PreAuthorize(Autorisations.LIRE_REFERENTIEL)
    public TableauBordStockResponse stock(@RequestParam(defaultValue = "90") int jours) {
        if (jours < JOURS_MIN || jours > JOURS_MAX) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La fenetre d'observation doit tenir entre " + JOURS_MIN
                            + " et " + JOURS_MAX + " jours");
        }
        return tableauBordStockService.construire(jours);
    }
}
