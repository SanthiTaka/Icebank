package com.iceibank.agencia.controller;

import java.io.IOException;
import java.util.List;
import java.util.Map;

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

    public ContasController(
            ContasService contasService,
            RelogioLamport relogioLamport,
            RegistroEventos registroEventos
    ) {
        this.contasService = contasService;
        this.relogioLamport = relogioLamport;
        this.registroEventos = registroEventos;
    }

    @PostMapping
    public ResponseEntity<Conta> criarConta(
            @RequestBody Conta conta
    ) throws IOException {

        if (conta.getSaldo() < 0) {
            return ResponseEntity.badRequest().build();
        }

        int timestamp = relogioLamport.eventoLocal();

        Conta contaCriada = contasService.criar(conta);

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
    public ResponseEntity<Conta> buscarConta(
            @PathVariable Long id
    ) {

        relogioLamport.eventoLocal();

        Conta conta = contasService.buscar(id);

        if (conta == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(conta);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Conta> atualizarConta(
            @PathVariable Long id,
            @RequestBody Conta dados
    ) throws IOException {

        Conta conta = contasService.buscar(id);

        if (conta == null) {
            return ResponseEntity.notFound().build();
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
    public ResponseEntity<Void> excluirConta(
            @PathVariable Long id
    ) throws IOException {

        Conta conta = contasService.excluir(id);

        if (conta == null) {
            return ResponseEntity.notFound().build();
        }

        int timestamp = relogioLamport.eventoLocal();

        registroEventos.registrar(
                "EXCLUIR_CONTA",
                timestamp,
                Map.of(
                        "idConta", id
                )
        );

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/deposito")
    public ResponseEntity<Conta> depositar(
            @PathVariable Long id,
            @RequestBody Map<String, Double> dados
    ) throws IOException {

        Double valor = dados.get("valor");

        if (valor == null || valor <= 0) {
            return ResponseEntity.badRequest().build();
        }

        Conta conta = contasService.buscar(id);

        if (conta == null) {
            return ResponseEntity.notFound().build();
        }

        int timestamp = relogioLamport.eventoLocal();

        Conta contaAtualizada = contasService.depositar(id, valor);

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
    public ResponseEntity<Conta> sacar(
            @PathVariable Long id,
            @RequestBody Map<String, Double> dados
    ) throws IOException {

        Double valor = dados.get("valor");

        if (valor == null || valor <= 0) {
            return ResponseEntity.badRequest().build();
        }

        Conta conta = contasService.buscar(id);

        if (conta == null) {
            return ResponseEntity.notFound().build();
        }

        if (conta.getSaldo() < valor) {
            return ResponseEntity
                    .status(HttpStatus.BAD_REQUEST)
                    .build();
        }

        int timestamp = relogioLamport.eventoLocal();

        Conta contaAtualizada = contasService.sacar(id, valor);

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
}
