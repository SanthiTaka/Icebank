package com.iceibank.agencia;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
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

        List<Map<String, Object>> todosEventos =
                new ArrayList<>();

        try (var arquivos = Files.list(pastaDados)) {

            arquivos
                    .filter(
                            arquivo ->
                                    arquivo
                                            .toString()
                                            .endsWith(".jsonl")
                    )
                    .forEach(arquivo -> {

                        try {

                            List<String> linhas =
                                    Files.readAllLines(
                                            arquivo
                                    );

                            for (String linha : linhas) {

                                if (linha.isBlank()) {
                                    continue;
                                }

                                Map<String, Object> evento =
                                        objectMapper.readValue(
                                                linha,
                                                new TypeReference<
                                                        Map<String, Object>
                                                >() {}
                                        );

                                /*
                                 * Ignora logs antigos da Sprint 1
                                 * que ainda possuem timestampLamport.
                                 */
                                if (!evento.containsKey(
                                        "timestampVetorial"
                                )) {

                                    System.out.println(
                                            "Ignorando evento antigo sem timestampVetorial: "
                                                    + evento.get("tipo")
                                    );

                                    continue;
                                }

                                todosEventos.add(evento);
                            }

                        } catch (IOException e) {

                            System.out.println(
                                    "Erro ao ler o arquivo: "
                                            + arquivo
                            );
                        }
                    });
        }

        if (todosEventos.isEmpty()) {

            System.out.println(
                    "Nenhum evento com relógio vetorial encontrado."
            );

            return;
        }

        /*
         * A linha do tempo visual é ordenada pela hora
         * física apenas para facilitar a leitura.
         *
         * A relação causal será determinada pelos vetores.
         */
        todosEventos.sort(
                Comparator.comparing(
                        evento ->
                                Instant.parse(
                                        evento
                                                .get("horaParede")
                                                .toString()
                                )
                )
        );

        System.out.println(
                "=== Linha do tempo ordenada por hora de parede ==="
        );

        for (Map<String, Object> evento : todosEventos) {

            List<Integer> vetor =
                    obterVetor(evento);

            System.out.println(
                    "["
                            + evento.get("agencia")
                            + "] vetor="
                            + vetor
                            + " | "
                            + evento.get("tipo")
                            + " | "
                            + evento.get("detalhes")
            );
        }

        System.out.println();

        System.out.println(
                "=== Pares de eventos CONCORRENTES entre agências diferentes ==="
        );

        boolean encontrouConcorrente = false;

        for (int i = 0;
             i < todosEventos.size();
             i++) {

            for (int j = i + 1;
                 j < todosEventos.size();
                 j++) {

                Map<String, Object> evento1 =
                        todosEventos.get(i);

                Map<String, Object> evento2 =
                        todosEventos.get(j);

                /*
                 * Só interessa comparar eventos de
                 * agências diferentes.
                 */
                if (
                        evento1
                                .get("agencia")
                                .equals(
                                        evento2.get("agencia")
                                )
                ) {
                    continue;
                }

                List<Integer> vetor1 =
                        obterVetor(evento1);

                List<Integer> vetor2 =
                        obterVetor(evento2);

                String relacao =
                        compararVetores(
                                vetor1,
                                vetor2
                        );

                if (
                        "CONCORRENTES"
                                .equals(relacao)
                ) {

                    encontrouConcorrente = true;

                    System.out.println(
                            "["
                                    + evento1.get("agencia")
                                    + "] "
                                    + evento1.get("tipo")
                                    + " "
                                    + vetor1
                                    + "  x  "
                                    + "["
                                    + evento2.get("agencia")
                                    + "] "
                                    + evento2.get("tipo")
                                    + " "
                                    + vetor2
                    );
                }
            }
        }

        if (!encontrouConcorrente) {

            System.out.println(
                    "(nenhum par concorrente encontrado nesta execução)"
            );

            System.out.println(
                    "Gere eventos independentes em agências diferentes e execute novamente."
            );
        }
    }

    private static List<Integer> obterVetor(
            Map<String, Object> evento
    ) {

        Object valor =
                evento.get(
                        "timestampVetorial"
                );

        if (!(valor instanceof List<?> lista)) {

            throw new IllegalArgumentException(
                    "timestampVetorial em formato inválido"
            );
        }

        List<Integer> vetor =
                new ArrayList<>();

        for (Object item : lista) {

            vetor.add(
                    ((Number) item)
                            .intValue()
            );
        }

        return vetor;
    }

    private static String compararVetores(
            List<Integer> v1,
            List<Integer> v2
    ) {

        if (v1.size() != v2.size()) {

            throw new IllegalArgumentException(
                    "Vetores com tamanhos diferentes"
            );
        }

        boolean v1MenorOuIgual = true;
        boolean v2MenorOuIgual = true;

        for (int i = 0;
             i < v1.size();
             i++) {

            if (
                    v1.get(i)
                            > v2.get(i)
            ) {
                v1MenorOuIgual = false;
            }

            if (
                    v2.get(i)
                            > v1.get(i)
            ) {
                v2MenorOuIgual = false;
            }
        }

        if (
                v1MenorOuIgual
                && v2MenorOuIgual
        ) {
            return "IGUAIS";
        }

        if (v1MenorOuIgual) {
            return "ANTES";
        }

        if (v2MenorOuIgual) {
            return "DEPOIS";
        }

        return "CONCORRENTES";
    }
}