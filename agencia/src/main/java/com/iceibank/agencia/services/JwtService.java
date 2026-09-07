package com.iceibank.agencia.services;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

@Service
public class JwtService {

    private final SecretKey chave;
    private final long expiracao;

    public JwtService(
            @Value("${jwt.secret}") String segredo,
            @Value("${jwt.expiration}") long expiracao
    ) {
        this.chave = Keys.hmacShaKeyFor(
                segredo.getBytes(StandardCharsets.UTF_8)
        );

        this.expiracao = expiracao;
    }

    public String gerarToken(String usuario) {

        Date agora = new Date();

        Date expiracaoToken = new Date(
                agora.getTime() + expiracao
        );

        return Jwts.builder()
                .subject(usuario)
                .issuedAt(agora)
                .expiration(expiracaoToken)
                .signWith(chave)
                .compact();
    }

    public Claims validarToken(String token) {

        return Jwts.parser()
                .verifyWith(chave)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
