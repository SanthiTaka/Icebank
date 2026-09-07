package com.iceibank.agencia.config;

import java.util.List;

public class AgenciaConfig {

    public static final int OFFSET = 93;

    public static final int NUMERO_AGENCIAS = 3;

    public static final int PORTA_BASE = 4000 + OFFSET;

    public static final List<Agencia> AGENCIAS = List.of(
            new Agencia(0, "http://localhost:" + PORTA_BASE),
            new Agencia(1, "http://localhost:" + (PORTA_BASE + 1)),
            new Agencia(2, "http://localhost:" + (PORTA_BASE + 2))
    );

    public static int agenciaResponsavel(int idConta) {
        return idConta % NUMERO_AGENCIAS;
    }

    public record Agencia(int id, String url) {

    }
}
