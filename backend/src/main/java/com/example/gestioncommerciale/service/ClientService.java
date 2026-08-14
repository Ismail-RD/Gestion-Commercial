package com.example.gestioncommerciale.service;

import com.example.gestioncommerciale.dto.ClientRequest;
import com.example.gestioncommerciale.dto.ClientResponse;
import com.example.gestioncommerciale.dto.PageResponse;
import com.example.gestioncommerciale.dto.filter.ClientFilter;
import org.springframework.data.domain.Pageable;

public interface ClientService {

    ClientResponse creer(ClientRequest request);

    ClientResponse modifier(Long id, ClientRequest request);

    ClientResponse trouverParId(Long id);

    PageResponse<ClientResponse> lister(ClientFilter filtre, Pageable pageable);

    /**
     * Change le commercial en charge. Reservee a l'admin (voir le controleur) :
     * le commercial est sinon fige sur celui qui a saisi le client.
     */
    ClientResponse reattribuer(Long id, Long commercialId);

    /**
     * Definit le plafond de credit d'un client apres sa creation (null = aucun
     * plafond). Peut faire basculer le client en BLOQUE si l'encours le depasse.
     */
    ClientResponse definirPlafond(Long id, java.math.BigDecimal plafondCredit);

    /** Debloque un client (etat credit -> ACTIF). Reservee a l'admin. */
    ClientResponse debloquer(Long id);

    /** Bloque manuellement un client (etat credit -> BLOQUE). Reservee a l'admin. */
    ClientResponse bloquer(Long id);

    /**
     * Reevalue l'etat credit d'un client : le passe a BLOQUE si son encours
     * depasse son plafond. Ne debloque jamais automatiquement.
     */
    void reevaluerBlocage(Long clientId);

    void supprimer(Long id);
}
