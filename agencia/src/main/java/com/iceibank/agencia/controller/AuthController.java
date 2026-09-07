package com.iceibank.agencia.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.iceibank.agencia.model.LoginRequest;
import com.iceibank.agencia.model.LoginResponse;
import com.iceibank.agencia.services.JwtService;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final JwtService jwtService;

    public AuthController(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {

        if (!"admin".equals(request.getUsuario())
                || !"123456".equals(request.getSenha())) {

            return ResponseEntity
                    .status(HttpStatus.UNAUTHORIZED)
                    .body(
                            Map.of(
                                    "erro",
                                    "Usuário ou senha inválidos"
                            )
                    );
        }

        String token = jwtService.gerarToken(
                request.getUsuario()
        );

        return ResponseEntity.ok(
                new LoginResponse(token)
        );
    }
}
