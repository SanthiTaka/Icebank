package com.iceibank.agencia.controller;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.iceibank.agencia.config.AgenciaConfig;
import com.iceibank.agencia.model.Conta;
import com.iceibank.agencia.services.ContasService;
import com.iceibank.agencia.services.RegistroEventos;
import com.iceibank.agencia.services.RelogioLamport;

@RestController
@RequestMapping("/contas")
public class ContasController {

    private final ContasService contasService;
    private final RelogioLamport relogioLamport;
    private final RegistroEventos registroEventos;
    private final int porta;

    public ContasController(
            ContasService contasService,
            RelogioLamport relogioLamport,
            RegistroEventos registroEventos,
            @Value("${server.port:4093}") int porta
    ) {
        this.contasService = contasService;
        this.relogioLamport = relogioLamport;
        this.registroEventos = registroEventos;
        this.porta = porta;
    }

    private int getAgenciaAtual() {
        return porta - AgenciaConfig.PORTA_BASE;
    }

    @PostMapping
    public ResponseEntity<?> criarConta(
            @RequestBody Conta conta
    ) throws IOException {

        if (conta.getSaldo() < 0) {
            return ResponseEntity.badRequest().body(
                    Map.of("erro", "O saldo inicial não pode ser negativo.")
            );
        }

        int agenciaAtual = getAgenciaAtual();
        if (conta.getId() != null) {
            if (AgenciaConfig.agenciaResponsavel(conta.getId().intValue()) != agenciaAtual) {
                return ResponseEntity.badRequest().body(
                        Map.of("erro", "Conta " + conta.getId() + " não pertence a esta agência.")
                );
            }
            if (contasService.buscar(conta.getId()) != null) {
                return ResponseEntity.status(HttpStatus.CONFLICT).body(
                        Map.of("erro", "Conta já existe.")
                );
            }
        }

        int timestamp = relogioLamport.eventoLocal();

        Conta contaCriada = contasService.criar(conta);

        contaCriada.adicionarTransacao(Map.of(
                "tipo", "CRIAR_CONTA",
                "valor", contaCriada.getSaldo(),
                "timestampLamport", timestamp,
                "dataHora", Instant.now().toString(),
                "saldoApos", contaCriada.getSaldo()
        ));

        registroEventos.registrar(
                "CRIAR_CONTA",
                timestamp,
                Map.of(
                        "idConta", contaCriada.getId(),
                        "titular", contaCriada.getTitular(),
                        "saldo", contaCriada.getSaldo()
                )
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(contaCriada);
    }

    @GetMapping
    public ResponseEntity<List<Conta>> listarContas() {
        relogioLamport.eventoLocal();
        return ResponseEntity.ok(contasService.listar());
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> buscarConta(
            @PathVariable Long id
    ) {
        int agenciaAtual = getAgenciaAtual();
        if (AgenciaConfig.agenciaResponsavel(id.intValue()) != agenciaAtual) {
            return ResponseEntity.badRequest().body(
                    Map.of("erro", "Conta " + id + " não pertence a esta agência.")
            );
        }

        relogioLamport.eventoLocal();

        Conta conta = contasService.buscar(id);
        if (conta == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    Map.of("erro", "Conta não encontrada nesta agência.")
            );
        }

        return ResponseEntity.ok(conta);
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> atualizarConta(
            @PathVariable Long id,
            @RequestBody Conta dados
    ) throws IOException {
        int agenciaAtual = getAgenciaAtual();
        if (AgenciaConfig.agenciaResponsavel(id.intValue()) != agenciaAtual) {
            return ResponseEntity.badRequest().body(
                    Map.of("erro", "Conta " + id + " não pertence a esta agência.")
            );
        }

        Conta conta = contasService.buscar(id);
        if (conta == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    Map.of("erro", "Conta não encontrada nesta agência.")
            );
        }

        int timestamp = relogioLamport.eventoLocal();
        Conta contaAtualizada = contasService.atualizar(id, dados);

        registroEventos.registrar(
                "ATUALIZAR_CONTA",
                timestamp,
                Map.of(
                        "idConta", id,
                        "titular", contaAtualizada.getTitular()
                )
        );

        return ResponseEntity.ok(contaAtualizada);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> excluirConta(
            @PathVariable Long id
    ) throws IOException {
        int agenciaAtual = getAgenciaAtual();
        if (AgenciaConfig.agenciaResponsavel(id.intValue()) != agenciaAtual) {
            return ResponseEntity.badRequest().body(
                    Map.of("erro", "Conta " + id + " não pertence a esta agência.")
            );
        }

        Conta conta = contasService.excluir(id);
        if (conta == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    Map.of("erro", "Conta não encontrada nesta agência.")
            );
        }

        int timestamp = relogioLamport.eventoLocal();
        registroEventos.registrar(
                "EXCLUIR_CONTA",
                timestamp,
                Map.of("idConta", id)
        );

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/deposito")
    public ResponseEntity<?> depositar(
            @PathVariable Long id,
            @RequestBody Map<String, Object> dados
    ) throws IOException {
        int agenciaAtual = getAgenciaAtual();
        if (AgenciaConfig.agenciaResponsavel(id.intValue()) != agenciaAtual) {
            return ResponseEntity.badRequest().body(
                    Map.of("erro", "Conta " + id + " não pertence a esta agência.")
            );
        }

        Object valorObj = dados.get("valor");
        if (valorObj == null) {
            return ResponseEntity.badRequest().body(
                    Map.of("erro", "Informe o valor do depósito.")
            );
        }
        double valor = ((Number) valorObj).doubleValue();
        if (valor <= 0) {
            return ResponseEntity.badRequest().body(
                    Map.of("erro", "Valor de depósito inválido.")
            );
        }

        Conta conta = contasService.buscar(id);
        if (conta == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    Map.of("erro", "Conta não encontrada nesta agência.")
            );
        }

        int timestamp = relogioLamport.eventoLocal();
        Conta contaAtualizada = contasService.depositar(id, valor);

        contaAtualizada.adicionarTransacao(Map.of(
                "tipo", "DEPOSITO",
                "valor", valor,
                "timestampLamport", timestamp,
                "dataHora", Instant.now().toString(),
                "saldoApos", contaAtualizada.getSaldo()
        ));

        registroEventos.registrar(
                "DEPOSITO",
                timestamp,
                Map.of(
                        "idConta", id,
                        "valor", valor,
                        "saldoAtual", contaAtualizada.getSaldo()
                )
        );

        return ResponseEntity.ok(contaAtualizada);
    }

    @PostMapping("/{id}/saque")
    public ResponseEntity<?> sacar(
            @PathVariable Long id,
            @RequestBody Map<String, Object> dados
    ) throws IOException {
        int agenciaAtual = getAgenciaAtual();
        if (AgenciaConfig.agenciaResponsavel(id.intValue()) != agenciaAtual) {
            return ResponseEntity.badRequest().body(
                    Map.of("erro", "Conta " + id + " não pertence a esta agência.")
            );
        }

        Object valorObj = dados.get("valor");
        if (valorObj == null) {
            return ResponseEntity.badRequest().body(
                    Map.of("erro", "Informe o valor do saque.")
            );
        }
        double valor = ((Number) valorObj).doubleValue();
        if (valor <= 0) {
            return ResponseEntity.badRequest().body(
                    Map.of("erro", "Valor de saque inválido.")
            );
        }

        Conta conta = contasService.buscar(id);
        if (conta == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    Map.of("erro", "Conta não encontrada nesta agência.")
            );
        }

        if (conta.getSaldo() < valor) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                    Map.of("erro", "Saldo insuficiente.")
            );
        }

        int timestamp = relogioLamport.eventoLocal();
        Conta contaAtualizada = contasService.sacar(id, valor);

        contaAtualizada.adicionarTransacao(Map.of(
                "tipo", "SAQUE",
                "valor", valor,
                "timestampLamport", timestamp,
                "dataHora", Instant.now().toString(),
                "saldoApos", contaAtualizada.getSaldo()
        ));

        registroEventos.registrar(
                "SAQUE",
                timestamp,
                Map.of(
                        "idConta", id,
                        "valor", valor,
                        "saldoAtual", contaAtualizada.getSaldo()
                )
        );

        return ResponseEntity.ok(contaAtualizada);
    }

    // =========================================================================
    // FUNCIONALIDADE ADICIONAL (Sprint 1): Extrato / Histórico de Transações
    // =========================================================================
    @GetMapping("/{id}/extrato")
    public ResponseEntity<?> obterExtrato(
            @PathVariable Long id
    ) throws IOException {
        int agenciaAtual = getAgenciaAtual();
        if (AgenciaConfig.agenciaResponsavel(id.intValue()) != agenciaAtual) {
            return ResponseEntity.badRequest().body(
                    Map.of("erro", "Conta " + id + " não pertence a esta agência.")
            );
        }

        Conta conta = contasService.buscar(id);
        if (conta == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    Map.of("erro", "Conta não encontrada nesta agência.")
            );
        }

        int timestamp = relogioLamport.eventoLocal();

        registroEventos.registrar(
                "CONSULTAR_EXTRATO",
                timestamp,
                Map.of(
                        "idConta", id,
                        "totalTransacoes", conta.getTransacoes().size(),
                        "saldoAtual", conta.getSaldo()
                )
        );

        return ResponseEntity.ok(Map.of(
                "idConta", id,
                "titular", conta.getTitular(),
                "saldoAtual", conta.getSaldo(),
                "agencia", agenciaAtual,
                "timestampLamport", timestamp,
                "transacoes", conta.getTransacoes()
        ));
    }
}
