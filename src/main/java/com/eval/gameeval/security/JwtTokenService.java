package com.eval.gameeval.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.Map;

@Component
public class JwtTokenService {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.issuer}")
    private String issuer;

    @Value("${jwt.access-seconds:14400}")
    private long accessSeconds;

    public String generateAccessToken(Map<String, Object> claims, String subject, String jti) {
        Instant now = Instant.now();
        Date issuedAt = Date.from(now);
        Date expiresAt = Date.from(now.plusSeconds(accessSeconds));

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuer(issuer)
                .setId(jti)
                .setIssuedAt(issuedAt)
                .setExpiration(expiresAt)
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    public Claims parseAccessClaims(String token) {
        Jws<Claims> jws = Jwts.parserBuilder()
                .requireIssuer(issuer)
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token);
        return jws.getBody();
    }

    public long getAccessSeconds() {
        return accessSeconds;
    }

    public LocalDateTime getAccessExpireTime() {
        return LocalDateTime.ofInstant(Instant.now().plusSeconds(accessSeconds), ZoneId.systemDefault());
    }

    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
