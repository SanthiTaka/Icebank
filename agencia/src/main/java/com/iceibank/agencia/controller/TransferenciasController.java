package com.iceibank.agencia.controller;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import com.iceibank.agencia.config.AgenciaConfig;
import com.iceibank.agencia.model.Conta;
import com.iceibank.agencia.services.ContasService;
import com.iceibank.agencia.services.RegistroEventos;
import com.iceibank.agencia.services.RelogioVetorial;

@RestController
@RequestMapping("/transferencias")
public class TransferenciasController {

    private final ContasService contasService;
    private final RelogioVetorial relogioVetorial;
    private final RegistroEventos registroEventos;
    private final RestTemplate restTemplate;

    private final int porta;

    public TransferenciasController(
            ContasService contasService,
            RelogioVetorial relogioVetorial,
            RegistroEventos registroEventos,
            @Value("${server.port:4093}") int porta
    ) {
        this.contasService = contasService;
        this.relogioVetorial = relogioVetorial;
        this.registroEventos = registroEventos;
        this.porta = porta;
        this.restTemplate = new RestTemplate();
    }

    @PostMapping
    public ResponseEntity<?> transferir(
            @RequestBody Map<String, Object> dados
    ) throws IOException {

        Long origem = ((Number) dados.get("idOrigem")).longValue();
        Long destino = ((Number) dados.get("idDestino")).longValue();
        double valor = ((Number) dados.get("valor")).doubleValue();

        if (valor <= 0) {
            return ResponseEntity.badRequest().body(
                    Map.of(
                            "erro",
                            "O valor da transferência deve ser maior que zero"
                    )
            );
        }

        if (origem.equals(destino)) {
            return ResponseEntity.badRequest().body(
                    Map.of(
                            "erro",
                            "As contas de origem e destino devem ser diferentes"
                    )
            );
        }

        int agenciaOrigem =
                AgenciaConfig.agenciaResponsavel(origem.intValue());

        int agenciaDestino =
                AgenciaConfig.agenciaResponsavel(destino.intValue());

        int agenciaAtual =
                porta - AgenciaConfig.PORTA_BASE;

        if (agenciaOrigem != agenciaAtual) {
            return ResponseEntity.badRequest().body(
                    Map.of(
                            "erro",
                            "A conta de origem não pertence a esta agência"
                    )
            );
        }

        Conta contaOrigem =
                contasService.buscar(origem);

        if (contaOrigem == null) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(
                            Map.of(
                                    "erro",
                                    "Conta de origem não encontrada"
                            )
                    );
        }

        if (contaOrigem.getSaldo() < valor) {
            return ResponseEntity.badRequest().body(
                    Map.of(
                            "erro",
                            "Saldo insuficiente"
                    )
            );
        }

        // =========================================================
        // TRANSFERÊNCIA LOCAL
        // =========================================================

        if (agenciaOrigem == agenciaDestino) {

            Conta contaDestino =
                    contasService.buscar(destino);

            if (contaDestino == null) {
                return ResponseEntity
                        .status(HttpStatus.NOT_FOUND)
                        .body(
                                Map.of(
                                        "erro",
                                        "Conta de destino não encontrada"
                                )
                        );
            }

            int[] timestampVetorial =
                    relogioVetorial.eventoLocal();

            contaOrigem.setSaldo(
                    contaOrigem.getSaldo() - valor
            );

            contaDestino.setSaldo(
                    contaDestino.getSaldo() + valor
            );

            registroEventos.registrar(
                    "TRANSFERENCIA_LOCAL",
                    timestampVetorial,
                    Map.of(
                            "origem", origem,
                            "destino", destino,
                            "valor", valor,
                            "saldoOrigem", contaOrigem.getSaldo(),
                            "saldoDestino", contaDestino.getSaldo()
                    )
            );

            return ResponseEntity.ok(
                    Map.of(
                            "mensagem",
                            "Transferência realizada com sucesso",
                            "origem",
                            contaOrigem,
                            "destino",
                            contaDestino,
                            "timestampVetorial",
                            timestampVetorial
                    )
            );
        }

        // =========================================================
        // TRANSFERÊNCIA ENTRE AGÊNCIAS
        // =========================================================

        int[] vetorEnvio =
                relogioVetorial.aoEnviar();

        // Por enquanto continuamos com a mesma lógica da Sprint 1.
        // O RabbitMQ será introduzido na Parte C.
        contaOrigem.setSaldo(
                contaOrigem.getSaldo() - valor
        );

        registroEventos.registrar(
                "TRANSFERENCIA_INTERAGENCIA_ENVIO",
                vetorEnvio,
                Map.of(
                        "origem", origem,
                        "destino", destino,
                        "valor", valor,
                        "agenciaDestino", agenciaDestino,
                        "saldoOrigem", contaOrigem.getSaldo()
                )
        );

        int portaDestino =
                AgenciaConfig.PORTA_BASE + agenciaDestino;

        String urlDestino =
                "http://localhost:"
                + portaDestino
                + "/transferencias/receber";

        Map<String, Object> requisicao =
                Map.of(
                        "idOrigem", origem,
                        "idDestino", destino,
                        "valor", valor,
                        "vetorEnvio", vetorEnvio
                );

        try {

            restTemplate.postForEntity(
                    urlDestino,
                    requisicao,
                    Map.class
            );

            return ResponseEntity.ok(
                    Map.of(
                            "mensagem",
                            "Transferência entre agências realizada com sucesso",
                            "origem",
                            origem,
                            "destino",
                            destino,
                            "valor",
                            valor,
                            "timestampVetorial",
                            vetorEnvio
                    )
            );

        } catch (Exception e) {

            int[] vetorFalha =
                    relogioVetorial.eventoLocal();

            registroEventos.registrar(
                    "TRANSFERENCIA_INTERAGENCIA_FALHA",
                    vetorFalha,
                    Map.of(
                            "origem", origem,
                            "destino", destino,
                            "valor", valor,
                            "erro",
                            e.getMessage() == null
                                    ? "Falha na comunicação"
                                    : e.getMessage()
                    )
            );

            return ResponseEntity
                    .status(HttpStatus.BAD_GATEWAY)
                    .body(
                            Map.of(
                                    "erro",
                                    "Falha na comunicação com a agência destino"
                            )
                    );
        }
    }

    @PostMapping("/receber")
    public ResponseEntity<?> receberTransferencia(
            @RequestBody Map<String, Object> dados
    ) throws IOException {

        Long origem =
                ((Number) dados.get("idOrigem")).longValue();

        Long destino =
                ((Number) dados.get("idDestino")).longValue();

        double valor =
                ((Number) dados.get("valor")).doubleValue();

        int[] vetorRecebido =
                converterVetor(
                        dados.get("vetorEnvio")
                );

        Conta contaDestino =
                contasService.buscar(destino);

        if (contaDestino == null) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(
                            Map.of(
                                    "erro",
                                    "Conta de destino não encontrada"
                            )
                    );
        }

        int[] timestampVetorial =
                relogioVetorial.aoReceber(
                        vetorRecebido
                );

        contaDestino.setSaldo(
                contaDestino.getSaldo() + valor
        );

        registroEventos.registrar(
                "TRANSFERENCIA_INTERAGENCIA_RECEBIMENTO",
                timestampVetorial,
                Map.of(
                        "origem", origem,
                        "destino", destino,
                        "valor", valor,
                        "vetorRecebido", vetorRecebido,
                        "saldoDestino", contaDestino.getSaldo()
                )
        );

        return ResponseEntity.ok(
                Map.of(
                        "mensagem",
                        "Transferência recebida com sucesso",
                        "destino",
                        contaDestino,
                        "timestampVetorial",
                        timestampVetorial
                )
        );
    }

    private int[] converterVetor(Object objeto) {

        if (objeto instanceof List<?> lista) {

            int[] vetor =
                    new int[lista.size()];

            for (int i = 0; i < lista.size(); i++) {
                vetor[i] =
                        ((Number) lista.get(i)).intValue();
            }

            return vetor;
        }

        if (objeto instanceof int[] vetor) {
            return vetor.clone();
        }

        throw new IllegalArgumentException(
                "Vetor recebido em formato inválido"
        );
    }
}