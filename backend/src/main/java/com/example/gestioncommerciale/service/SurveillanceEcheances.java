package com.example.gestioncommerciale.service;

import com.example.gestioncommerciale.entity.Facture;
import com.example.gestioncommerciale.repository.FactureRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Fait basculer en retard les factures dont l'echeance est passee.
 *
 * <p>Encaissement et renegociation d'echeance recalculent deja le statut au
 * moment ou ils se produisent. Le retard, lui, n'a pas d'evenement : il arrive
 * tout seul a minuit. C'est la seule raison d'etre de ce balayage.
 *
 * <p>Il tourne chaque nuit, et aussi au demarrage : une application eteinte
 * pendant la nuit rattraperait sinon son retard seulement le lendemain, et
 * afficherait entre-temps des factures a jour qui ne le sont plus.
 */
@Component
public class SurveillanceEcheances {

    private static final Logger log = LoggerFactory.getLogger(SurveillanceEcheances.class);

    private final FactureRepository factureRepository;
    private final AlertesEcheances alertes;

    public SurveillanceEcheances(FactureRepository factureRepository,
                                 AlertesEcheances alertes) {
        this.factureRepository = factureRepository;
        this.alertes = alertes;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void auDemarrage() {
        balayer();
    }

    /**
     * Peu apres minuit : les echeances de la veille viennent de passer.
     *
     * <p>L'ordre compte : les statuts basculent d'abord, les alertes sont
     * emises ensuite. L'inverse notifierait des factures encore marquees a
     * jour, et en oublierait qui viennent d'echoir.
     */
    @Scheduled(cron = "${app.factures.cron-retard:0 5 0 * * *}")
    public void balayer() {
        marquerLesFacturesEchues();
        alertes.balayer();
    }

    @Transactional
    public void marquerLesFacturesEchues() {
        List<Facture> echues = factureRepository.echuesNonMarquees(LocalDate.now());
        if (echues.isEmpty()) {
            return;
        }
        for (Facture facture : echues) {
            facture.recalculerStatut();
        }
        factureRepository.saveAll(echues);
        log.info("{} facture(s) passee(s) en retard", echues.size());
    }
}
