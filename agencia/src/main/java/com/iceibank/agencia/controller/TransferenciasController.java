package com.iceibank.agencia.controller;

import java.io.IOException;
import java.time.Instant;
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
import com.iceibank.agencia.services.RelogioLamport;

@RestController
@RequestMapping("/transferencias")
public class TransferenciasController {

    private final ContasService contasService;
    private final RelogioLamport relogioLamport;
    private final RegistroEventos registroEventos;
    private final RestTemplate restTemplate;
    private final int porta;

    public TransferenciasController(
            ContasService contasService,
            RelogioLamport relogioLamport,
            RegistroEventos registroEventos,
            @Value("${server.port:4093}") int porta
    ) {
        this.contasService = contasService;
        this.relogioLamport = relogioLamport;
        this.registroEventos = registroEventos;
        this.porta = porta;
        this.restTemplate = new RestTemplate();
    }

    private int getAgenciaAtual() {
        return porta - AgenciaConfig.PORTA_BASE;
    }

    @PostMapping
    public ResponseEntity<?> transferir(
            @RequestBody Map<String, Object> dados
    ) throws IOException {

        Object origemObj = dados.get("idOrigem");
        Object destinoObj = dados.get("idDestino");
        Object valorObj = dados.get("valor");

        if (origemObj == null || destinoObj == null || valorObj == null) {
            return ResponseEntity.badRequest().body(
                    Map.of("erro", "Parâmetros idOrigem, idDestino e valor são obrigatórios.")
            );
        }

        Long origem = ((Number) origemObj).longValue();
        Long destino = ((Number) destinoObj).longValue();
        double valor = ((Number) valorObj).doubleValue();

        if (valor <= 0) {
            return ResponseEntity.badRequest().body(
                    Map.of("erro", "O valor da transferência deve ser maior que zero.")
            );
        }

        if (origem.equals(destino)) {
            return ResponseEntity.badRequest().body(
                    Map.of("erro", "As contas de origem e destino devem ser diferentes.")
            );
        }

        int agenciaOrigem = AgenciaConfig.agenciaResponsavel(origem.intValue());
        int agenciaDestino = AgenciaConfig.agenciaResponsavel(destino.intValue());
        int agenciaAtual = getAgenciaAtual();

        if (agenciaOrigem != agenciaAtual) {
            return ResponseEntity.badRequest().body(
                    Map.of("erro", "A conta de origem não pertence a esta agência.")
            );
        }

        Conta contaOrigem = contasService.buscar(origem);
        if (contaOrigem == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    Map.of("erro", "Conta de origem não encontrada nesta agência.")
            );
        }

        if (contaOrigem.getSaldo() < valor) {
            return ResponseEntity.badRequest().body(
                    Map.of("erro", "Saldo insuficiente.")
            );
        }

        // =========================================================
        // TRANSFERÊNCIA LOCAL (mesma agência)
        // =========================================================
        if (agenciaOrigem == agenciaDestino) {
            Conta contaDestino = contasService.buscar(destino);
            if (contaDestino == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                        Map.of("erro", "Conta de destino não encontrada.")
                );
            }

            int timestamp = relogioLamport.eventoLocal();

            contaOrigem.setSaldo(contaOrigem.getSaldo() - valor);
            contaDestino.setSaldo(contaDestino.getSaldo() + valor);

            String agora = Instant.now().toString();
            contaOrigem.adicionarTransacao(Map.of(
                    "tipo", "TRANSFERENCIA_LOCAL_DEBITO",
                    "valor", valor,
                    "destino", destino,
                    "timestampLamport", timestamp,
                    "dataHora", agora,
                    "saldoApos", contaOrigem.getSaldo()
            ));
            contaDestino.adicionarTransacao(Map.of(
                    "tipo", "TRANSFERENCIA_LOCAL_CREDITO",
                    "valor", valor,
                    "origem", origem,
                    "timestampLamport", timestamp,
                    "dataHora", agora,
                    "saldoApos", contaDestino.getSaldo()
            ));

            registroEventos.registrar(
                    "TRANSFERENCIA_LOCAL",
                    timestamp,
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
                            "mensagem", "Transferência realizada com sucesso (mesma agência).",
                            "origem", contaOrigem,
                            "destino", contaDestino,
                            "timestampLamport", timestamp
                    )
            );
        }

        // =========================================================
        // TRANSFERÊNCIA ENTRE AGÊNCIAS (REST direto)
        // =========================================================
        int timestampEnvio = relogioLamport.aoEnviar();

        // Débito realizado ANTES do envio.
        // Limitação conhecida da Sprint 1: sem rollback automático.
        contaOrigem.setSaldo(contaOrigem.getSaldo() - valor);

        String agora = Instant.now().toString();
        contaOrigem.adicionarTransacao(Map.of(
                "tipo", "TRANSFERENCIA_INTERAGENCIA_ENVIADA",
                "valor", valor,
                "destino", destino,
                "agenciaDestino", agenciaDestino,
                "timestampLamport", timestampEnvio,
                "dataHora", agora,
                "saldoApos", contaOrigem.getSaldo()
        ));

        registroEventos.registrar(
                "TRANSFERENCIA_INTERAGENCIA_ENVIO",
                timestampEnvio,
                Map.of(
                        "origem", origem,
                        "destino", destino,
                        "valor", valor,
                        "agenciaDestino", agenciaDestino,
                        "saldoOrigem", contaOrigem.getSaldo()
                )
        );

        int portaDestino = AgenciaConfig.PORTA_BASE + agenciaDestino;
        String urlDestino = "http://localhost:" + portaDestino + "/transferencias/receber";

        Map<String, Object> requisicao = Map.of(
                "idOrigem", origem,
                "idDestino", destino,
                "valor", valor,
                "timestampLamport", timestampEnvio
        );

        try {
            ResponseEntity<Map> resposta = restTemplate.postForEntity(
                    urlDestino,
                    requisicao,
                    Map.class
            );

            return ResponseEntity.ok(
                    Map.of(
                            "mensagem", "Transferência entre agências realizada com sucesso.",
                            "origem", origem,
                            "destino", destino,
                            "valor", valor,
                            "timestampLamport", timestampEnvio
                    )
            );

        } catch (Exception e) {
            // LIMITAÇÃO CONHECIDA: O débito na origem NÃO é revertido.
            int tsFalha = relogioLamport.eventoLocal();

            registroEventos.registrar(
                    "TRANSFERENCIA_INTERAGENCIA_FALHA",
                    tsFalha,
                    Map.of(
                            "origem", origem,
                            "destino", destino,
                            "valor", valor,
                            "erro", e.getMessage() == null ? "Falha na comunicação" : e.getMessage()
                    )
            );

            return ResponseEntity
                    .status(HttpStatus.BAD_GATEWAY)
                    .body(
                            Map.of(
                                    "erro", "Falha ao contatar agência de destino. Débito já aplicado - inconsistência conhecida (ver Sprint 4).",
                                    "detalhe", e.getMessage() == null ? "Falha na comunicação" : e.getMessage()
                            )
                    );
        }
    }

    @PostMapping("/receber")
    public ResponseEntity<?> receberTransferencia(
            @RequestBody Map<String, Object> dados
    ) throws IOException {

        Long origem = ((Number) dados.get("idOrigem")).longValue();
        Long destino = ((Number) dados.get("idDestino")).longValue();
        double valor = ((Number) dados.get("valor")).doubleValue();
        int timestampRecebido = ((Number) dados.get("timestampLamport")).intValue();

        Conta contaDestino = contasService.buscar(destino);
        if (contaDestino == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    Map.of("erro", "Conta de destino não encontrada nesta agência.")
            );
        }

        int timestamp = relogioLamport.aoReceber(timestampRecebido);

        contaDestino.setSaldo(contaDestino.getSaldo() + valor);

        String agora = Instant.now().toString();
        contaDestino.adicionarTransacao(Map.of(
                "tipo", "TRANSFERENCIA_INTERAGENCIA_RECEBIDA",
                "valor", valor,
                "origem", origem,
                "timestampLamport", timestamp,
                "dataHora", agora,
                "saldoApos", contaDestino.getSaldo()
        ));

        registroEventos.registrar(
                "TRANSFERENCIA_INTERAGENCIA_RECEBIMENTO",
                timestamp,
                Map.of(
                        "origem", origem,
                        "destino", destino,
                        "valor", valor,
                        "timestampRecebido", timestampRecebido,
                        "saldoDestino", contaDestino.getSaldo()
                )
        );

        return ResponseEntity.ok(
                Map.of(
                        "mensagem", "Transferência recebida com sucesso.",
                        "destino", contaDestino,
                        "timestampLamport", timestamp
                )
        );
    }
}
