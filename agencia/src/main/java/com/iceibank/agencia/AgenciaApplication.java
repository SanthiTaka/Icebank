package com.iceibank.agencia;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import com.iceibank.agencia.config.AgenciaConfig;

@SpringBootApplication
public class AgenciaApplication {

    public static void main(String[] args) {

        String agenciaIdString = System.getenv().getOrDefault("AGENCIA_ID", "0");

        int agenciaId = Integer.parseInt(agenciaIdString);

        if (agenciaId < 0 || agenciaId >= AgenciaConfig.NUMERO_AGENCIAS) {
            System.err.println("Agência " + agenciaId + " não configurada.");
            System.exit(1);
        }

        System.setProperty(
                "server.port",
                String.valueOf(
                        AgenciaConfig.PORTA_BASE + agenciaId
                )
        );

        SpringApplication.run(AgenciaApplication.class, args);
    }
}
