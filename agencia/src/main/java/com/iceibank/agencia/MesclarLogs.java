package com.iceibank.agencia;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class MesclarLogs {

    public static void main(String[] args) throws IOException {

        Path pastaDados = Paths.get("data");

        if (!Files.exists(pastaDados)) {
            System.out.println("Pasta data não encontrada.");
            return;
        }

        ObjectMapper objectMapper = new ObjectMapper();

        List<Map<String, Object>> todosEventos = new ArrayList<>();

        try (var arquivos = Files.list(pastaDados)) {

            arquivos
                    .filter(arquivo -> arquivo.toString().endsWith(".jsonl"))
                    .forEach(arquivo -> {

                        try {

                            List<String> linhas = Files.readAllLines(arquivo);

                            for (String linha : linhas) {

                                if (linha.isBlank()) {
                                    continue;
                                }

                                Map<String, Object> evento =
                                        objectMapper.readValue(
                                                linha,
                                                new TypeReference<Map<String, Object>>() {}
                                        );

                                todosEventos.add(evento);
                            }

                        } catch (IOException e) {
                            System.out.println(
                                    "Erro ao ler o arquivo: " + arquivo
                            );
                        }
                    });
        }

        todosEventos.sort(
                Comparator.comparingInt(
                        evento -> ((Number) evento.get("timestampLamport")).intValue()
                )
        );

        System.out.println(
                "=== Linha do tempo unificada (ordenada por relogio de Lamport) ==="
        );

        for (Map<String, Object> evento : todosEventos) {

            System.out.println(
                    "[Lamport " +
                    evento.get("timestampLamport") +
                    "] (" +
                    evento.get("horaParede") +
                    ") " +
                    evento.get("agencia") +
                    " - " +
                    evento.get("tipo") +
                    " " +
                    evento.get("detalhes")
            );
        }
    }
}