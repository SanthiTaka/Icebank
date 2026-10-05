package com.iceibank.agencia.controller;

import java.io.IOException;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.iceibank.agencia.config.AgenciaConfig;
import com.iceibank.agencia.model.Conta;
import com.iceibank.agencia.model.MensagemCredito;
import com.iceibank.agencia.services.ContasService;
import com.iceibank.agencia.services.MensageriaService;
import com.iceibank.agencia.services.RegistroEventos;
import com.iceibank.agencia.services.RelogioVetorial;

@RestController
@RequestMapping("/transferencias")
public class TransferenciasController {

    private final ContasService contasService;
    private final RelogioVetorial relogioVetorial;
    private final RegistroEventos registroEventos;
    private final MensageriaService mensageriaService;

    private final int porta;

    public TransferenciasController(
            ContasService contasService,
            RelogioVetorial relogioVetorial,
            RegistroEventos registroEventos,
            MensageriaService mensageriaService,
            @Value("${server.port:4093}") int porta
    ) {
        this.contasService = contasService;
        this.relogioVetorial = relogioVetorial;
        this.registroEventos = registroEventos;
        this.mensageriaService = mensageriaService;
        this.porta = porta;
    }

    @PostMapping
    public ResponseEntity<?> transferir(
            @RequestBody Map<String, Object> dados
    ) throws IOException {

        Long origem =
                ((Number) dados.get("idOrigem"))
                        .longValue();

        Long destino =
                ((Number) dados.get("idDestino"))
                        .longValue();

        double valor =
                ((Number) dados.get("valor"))
                        .doubleValue();

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
                AgenciaConfig.agenciaResponsavel(
                        origem.intValue()
                );

        int agenciaDestino =
                AgenciaConfig.agenciaResponsavel(
                        destino.intValue()
                );

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

        // =====================================================
        // TRANSFERÊNCIA LOCAL
        // =====================================================

        if (agenciaDestino == agenciaAtual) {

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

            int[] vetorDebito =
                    relogioVetorial.eventoLocal();

            contaOrigem.setSaldo(
                    contaOrigem.getSaldo() - valor
            );

            registroEventos.registrar(
                    "TRANSFERENCIA_DEBITO",
                    vetorDebito,
                    Map.of(
                            "idOrigem", origem,
                            "idDestino", destino,
                            "valor", valor,
                            "saldoOrigem",
                            contaOrigem.getSaldo()
                    )
            );

            int[] vetorCredito =
                    relogioVetorial.eventoLocal();

            contaDestino.setSaldo(
                    contaDestino.getSaldo() + valor
            );

            registroEventos.registrar(
                    "TRANSFERENCIA_CREDITO",
                    vetorCredito,
                    Map.of(
                            "idOrigem", origem,
                            "idDestino", destino,
                            "valor", valor,
                            "saldoDestino",
                            contaDestino.getSaldo()
                    )
            );

            return ResponseEntity.ok(
                    Map.of(
                            "mensagem",
                            "Transferência concluída (mesma agência)."
                    )
            );
        }

        // =====================================================
        // TRANSFERÊNCIA ENTRE AGÊNCIAS
        // =====================================================

        int[] vetorDebito =
                relogioVetorial.eventoLocal();

        contaOrigem.setSaldo(
                contaOrigem.getSaldo() - valor
        );

        registroEventos.registrar(
                "TRANSFERENCIA_DEBITO",
                vetorDebito,
                Map.of(
                        "idOrigem", origem,
                        "idDestino", destino,
                        "valor", valor,
                        "agenciaDestino", agenciaDestino,
                        "saldoOrigem",
                        contaOrigem.getSaldo()
                )
        );

        int[] vetorEnvio =
                relogioVetorial.aoEnviar();

        MensagemCredito mensagem =
                new MensagemCredito(
                        destino,
                        valor,
                        vetorEnvio,
                        agenciaAtual
                );

        try {

            mensageriaService.publicarCredito(
                    agenciaDestino,
                    mensagem
            );

            registroEventos.registrar(
                    "TRANSFERENCIA_PUBLICADA",
                    vetorEnvio,
                    Map.of(
                            "idOrigem", origem,
                            "idDestino", destino,
                            "valor", valor,
                            "agenciaDestino",
                            agenciaDestino
                    )
            );

            return ResponseEntity.ok(
                    Map.of(
                            "mensagem",
                            "Transferência publicada para a agência de destino (entrega assíncrona)."
                    )
            );

        } catch (Exception e) {

            int[] vetorFalha =
                    relogioVetorial.eventoLocal();

            registroEventos.registrar(
                    "PUBLICACAO_RABBITMQ_FALHOU",
                    vetorFalha,
                    Map.of(
                            "idOrigem", origem,
                            "idDestino", destino,
                            "valor", valor,
                            "erro",
                            e.getMessage() == null
                                    ? "Erro ao publicar mensagem"
                                    : e.getMessage()
                    )
            );

            return ResponseEntity
                    .status(HttpStatus.BAD_GATEWAY)
                    .body(
                            Map.of(
                                    "erro",
                                    "Falha ao publicar transferência no RabbitMQ"
                            )
                    );
        }
    }
}