package com.recovertogether.backend.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import io.jsonwebtoken.Claims;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService
{

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expirationtime;

    private SecretKey getSigningKey()
    {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(String email)
    {
        return Jwts.builder().subject(email).
                issuedAt(new Date()).
                expiration(new Date(System.currentTimeMillis()+expirationtime)).
                signWith(getSigningKey()).compact();
    }

    public String extractEmail(String token)
    {
        Claims claims=Jwts.parser().
                verifyWith(getSigningKey()).
                build().
                parseSignedClaims(token).
                getPayload();

        return claims.getSubject();
    }

    public boolean validateToken(String token)
    {
        try{
            Jwts.parser().
                    verifyWith(getSigningKey()).
                    build().
                    parseSignedClaims(token);

            return true;
        }catch(Exception e)
        {
            e.printStackTrace();
            return false;
        }
    }
}
