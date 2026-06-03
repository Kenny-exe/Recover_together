package com.recovertogether.backend.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtService
{
    private final SecretKey secretKey =
            Keys.hmacShaKeyFor("mysecretkeymysecretkeymysecretkey12".getBytes());

    public String generateToken(String email)
    {
        return Jwts.builder().subject(email).
                issuedAt(new Date()).
                expiration(new Date(System.currentTimeMillis()+1000*60*60)).
                signWith(secretKey).compact();
    }
}
