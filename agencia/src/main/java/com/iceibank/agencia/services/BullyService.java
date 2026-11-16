
package com.iceibank.agencia.services;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iceibank.agencia.config.AgenciaConfig;
import com.iceibank.agencia.config.AgenciaConfig.Agencia;
import com.iceibank.agencia.model.BullyMensagem;

@Service
public class BullyService {

    private static final int TIMEOUT_MS = 1000;

    private final int idAgencia;
    private final RelogioVetorial relogio;
    private final RegistroEventos registro;
    private final ObjectMapper objectMapper;

    private final HttpClient httpClient;

    private final AtomicBoolean eleicaoEmAndamento =
            new AtomicBoolean(false);

    private volatile Integer liderAtual;

    public BullyService(
            @Value("${server.port}") int porta,
            RelogioVetorial relogio,
            RegistroEventos registro,
            ObjectMapper objectMapper
    ) {
        this.idAgencia = porta - AgenciaConfig.PORTA_BASE;
        this.relogio = relogio;
        this.registro = registro;
        this.objectMapper = objectMapper;

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(TIMEOUT_MS))
                .build();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void iniciarAoSubir() {
        System.out.println(
                "[BULLY] Agência " + idAgencia
                + " iniciou. Procurando líder..."
        );

        iniciarEleicao();
    }

    public void iniciarEleicao() {

        if (!eleicaoEmAndamento.compareAndSet(false, true)) {
            return;
        }

        try {
            int[] vetor = relogio.eventoLocal();

            registrar(
                    "ELEICAO_INICIADA",
                    vetor,
                    Map.of("idAgencia", idAgencia)
            );

            boolean alguemRespondeu = false;

            for (Agencia agencia : AgenciaConfig.AGENCIAS) {

                if (agencia.id() <= idAgencia) {
                    continue;
                }

                int[] vetorEnvio = relogio.aoEnviar();

                BullyMensagem mensagem = new BullyMensagem(
                        idAgencia,
                        null,
                        vetorEnvio
                );

                try {
                    enviarPost(
                            agencia.url() + "/bully/eleicao",
                            mensagem
                    );

                    alguemRespondeu = true;

                    System.out.println(
                            "[BULLY] Agência " + agencia.id()
                            + " respondeu à eleição da Agência "
                            + idAgencia
                    );

                } catch (Exception erro) {
                    System.out.println(
                            "[BULLY] Agência " + agencia.id()
                            + " não respondeu."
                    );
                }
            }

            if (!alguemRespondeu) {
                tornarMeLider();
            }

        } finally {
            eleicaoEmAndamento.set(false);
        }
    }

    private void tornarMeLider() {

        liderAtual = idAgencia;

        int[] vetor = relogio.eventoLocal();

        registrar(
                "TORNOU_LIDER",
                vetor,
                Map.of("idAgencia", idAgencia)
        );

        System.out.println(
                "[BULLY] Agência " + idAgencia
                + " tornou-se a LÍDER."
        );

        for (Agencia agencia : AgenciaConfig.AGENCIAS) {

            if (agencia.id() == idAgencia) {
                continue;
            }

            try {
                int[] vetorEnvio = relogio.aoEnviar();

                BullyMensagem mensagem = new BullyMensagem(
                        null,
                        idAgencia,
                        vetorEnvio
                );

                enviarPost(
                        agencia.url() + "/bully/coordenador",
                        mensagem
                );

            } catch (Exception erro) {
                System.out.println(
                        "[BULLY] Não foi possível anunciar a liderança "
                        + "para a Agência " + agencia.id()
                );
            }
        }
    }

    public void receberEleicao(
            Integer origemId,
            int[] vetorRecebido
    ) {

        int[] vetor = relogio.aoReceber(vetorRecebido);

        registrar(
                "ELEICAO_RECEBIDA",
                vetor,
                Map.of(
                        "de", origemId,
                        "minhaId", idAgencia
                )
        );

        System.out.println(
                "[BULLY] Agência " + idAgencia
                + " recebeu eleição da Agência " + origemId
        );

        CompletableFuture.runAsync(this::iniciarEleicao);
    }

    public void receberCoordenador(
            Integer liderId,
            int[] vetorRecebido
    ) {

        int[] vetor = relogio.aoReceber(vetorRecebido);

        // Evita que um anúncio atrasado de uma agência
        // inferior substitua uma liderança superior já conhecida.
        if (liderAtual != null && liderId < liderAtual) {
            return;
        }

        liderAtual = liderId;

        registrar(
                "LIDER_ANUNCIADO",
                vetor,
                Map.of(
                        "liderId", liderId,
                        "agencia", idAgencia
                )
        );

        System.out.println(
                "[BULLY] Agência " + idAgencia
                + " reconheceu a Agência " + liderId
                + " como líder."
        );
    }

    public Integer obterLiderAtual() {
        return liderAtual;
    }

    private void enviarPost(
            String url,
            BullyMensagem mensagem
    ) throws IOException, InterruptedException {

        String json = objectMapper.writeValueAsString(mensagem);

        HttpRequest requisicao = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofMillis(TIMEOUT_MS))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> resposta = httpClient.send(
                requisicao,
                HttpResponse.BodyHandlers.ofString()
        );

        if (resposta.statusCode() < 200
                || resposta.statusCode() >= 300) {
            throw new IOException(
                    "Resposta HTTP: " + resposta.statusCode()
            );
        }
    }

    public int obterIdAgencia() {
    return idAgencia;
}

    private void registrar(
            String tipo,
            int[] vetor,
            Map<String, Object> detalhes
    ) {
        try {
            registro.registrar(tipo, vetor, detalhes);
        } catch (IOException erro) {
            System.err.println(
                    "[BULLY] Erro ao registrar evento: "
                    + erro.getMessage()
            );
        }
    }
}
