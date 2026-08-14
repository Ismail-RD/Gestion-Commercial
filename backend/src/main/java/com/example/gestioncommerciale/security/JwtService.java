package com.example.gestioncommerciale.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.function.Function;

@Service
public class JwtService {

    /** 256 bits : longueur minimale exigee par HMAC-SHA256. */
    private static final int OCTETS_MINIMUM = 32;

    @Value("${app.jwt.secret:}")
    private String secret;

    @Value("${app.jwt.expiration-ms}")
    private long expirationMs;

    /**
     * Refuse de demarrer sans secret de signature valide.
     *
     * <p>Il n'y a volontairement aucune valeur par defaut. Un secret ecrit dans
     * le depot est un secret public : quiconque le lit peut forger un jeton et
     * se faire passer pour l'administrateur. Mieux vaut une application qui ne
     * demarre pas, avec un message qui dit quoi faire, qu'une application qui
     * demarre en croyant etre protegee.
     */
    @PostConstruct
    void verifierLeSecret() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(EXPLICATION.formatted(
                    "aucun secret n'est configure"));
        }
        int octets;
        try {
            octets = Decoders.BASE64.decode(secret).length;
        } catch (RuntimeException pasDuBase64) {
            throw new IllegalStateException(EXPLICATION.formatted(
                    "le secret n'est pas une chaine Base64 valide"));
        }
        if (octets < OCTETS_MINIMUM) {
            throw new IllegalStateException(EXPLICATION.formatted(
                    "le secret ne fait que " + (octets * 8) + " bits, il en faut au moins 256"));
        }
    }

    private static final String EXPLICATION = """
            Signature des jetons impossible : %s.

            Definissez app.jwt.secret, par la variable d'environnement JWT_SECRET
            ou dans backend/application-local.properties. Il doit s'agir d'une
            chaine Base64 d'au moins 256 bits, propre a chaque environnement.

            Pour en generer une :
              openssl rand -base64 48
            """;

    public String genererToken(UserDetails userDetails) {
        String role = userDetails.getAuthorities().stream()
                .findFirst()
                .map(a -> a.getAuthority())
                .orElse("");
        Date maintenant = new Date();
        Date expiration = new Date(maintenant.getTime() + expirationMs);
        return Jwts.builder()
                .subject(userDetails.getUsername())
                .claim("role", role)
                .issuedAt(maintenant)
                .expiration(expiration)
                .signWith(cleDeSignature())
                .compact();
    }

    public String extraireEmail(String token) {
        return extraireClaim(token, Claims::getSubject);
    }

    public boolean estValide(String token, UserDetails userDetails) {
        final String email = extraireEmail(token);
        return email.equals(userDetails.getUsername()) && !estExpire(token);
    }

    private boolean estExpire(String token) {
        return extraireClaim(token, Claims::getExpiration).before(new Date());
    }

    private <T> T extraireClaim(String token, Function<Claims, T> resolver) {
        final Claims claims = Jwts.parser()
                .verifyWith(cleDeSignature())
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return resolver.apply(claims);
    }

    private SecretKey cleDeSignature() {
        byte[] keyBytes = Decoders.BASE64.decode(secret);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
