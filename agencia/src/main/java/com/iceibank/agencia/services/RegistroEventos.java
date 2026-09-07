package com.iceibank.agencia.services;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

public class RegistroEventos {

    private final String nomeAgencia;
    private final File arquivo;
    private final ObjectMapper objectMapper;

    public RegistroEventos(String nomeAgencia) throws IOException {

        this.nomeAgencia = nomeAgencia;
        this.objectMapper = new ObjectMapper();

        File pastaDados = new File("data");

        if (!pastaDados.exists()) {
            pastaDados.mkdirs();
        }

        this.arquivo = new File(
                pastaDados,
                "eventos-" + nomeAgencia + ".jsonl"
        );
    }

    public synchronized Map<String, Object> registrar(
            String tipo,
            int timestampLamport,
            Map<String, Object> detalhes
    ) throws IOException {

        Map<String, Object> evento = new LinkedHashMap<>();

        evento.put("agencia", nomeAgencia);
        evento.put("tipo", tipo);
        evento.put("timestampLamport", timestampLamport);
        evento.put("horaParede", Instant.now().toString());
        evento.put("detalhes", detalhes);

        String json = objectMapper.writeValueAsString(evento);

        try (FileWriter writer = new FileWriter(arquivo, true)) {
            writer.write(json);
            writer.write(System.lineSeparator());
        }

        System.out.println(
                "[Lamport " + timestampLamport + "] "
                + tipo + " " + detalhes
        );

        return evento;
    }
}
