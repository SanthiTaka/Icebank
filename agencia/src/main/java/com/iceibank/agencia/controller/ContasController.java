package com.iceibank.agencia.controller;

import com.iceibank.agencia.model.Conta;
import com.iceibank.agencia.services.RelogioLamport;
import com.iceibank.agencia.services.RegistroEventos;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@RestController
@RequestMapping("/contas")
public class ContasController {

    private final Map<Long, Conta> contas = new ConcurrentHashMap<>();
    private final AtomicLong proximoId = new AtomicLong(0);

    private final RelogioLamport relogioLamport = new RelogioLamport();
    private final RegistroEventos registroEventos;

    public ContasController() throws IOException {
        this.registroEventos = new RegistroEventos("agencia");
    }

    @PostMapping
    public ResponseEntity<Conta> criarConta(@RequestBody Conta conta) throws IOException {

        int timestamp = relogioLamport.eventoLocal();

        Long id = proximoId.getAndIncrement();
        conta.setId(id);

        if (conta.getSaldo() < 0) {
            return ResponseEntity.badRequest().build();
        }

        contas.put(id, conta);

        registroEventos.registrar(
                "CRIAR_CONTA",
                timestamp,
                Map.of(
                        "idConta", id,
                        "titular", conta.getTitular(),
                        "saldo", conta.getSaldo()
                )
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(conta);
    }

    @GetMapping
    public ResponseEntity<List<Conta>> listarContas() {

        int timestamp = relogioLamport.eventoLocal();

        return ResponseEntity.ok(new ArrayList<>(contas.values()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Conta> buscarConta(@PathVariable Long id) {

        int timestamp = relogioLamport.eventoLocal();

        Conta conta = contas.get(id);

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

        int timestamp = relogioLamport.eventoLocal();

        Conta conta = contas.get(id);

        if (conta == null) {
            return ResponseEntity.notFound().build();
        }

        conta.setTitular(dados.getTitular());

        registroEventos.registrar(
                "ATUALIZAR_CONTA",
                timestamp,
                Map.of(
                        "idConta", id,
                        "titular", conta.getTitular()
                )
        );

        return ResponseEntity.ok(conta);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluirConta(
            @PathVariable Long id
    ) throws IOException {

        int timestamp = relogioLamport.eventoLocal();

        Conta conta = contas.remove(id);

        if (conta == null) {
            return ResponseEntity.notFound().build();
        }

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

        int timestamp = relogioLamport.eventoLocal();

        Conta conta = contas.get(id);

        if (conta == null) {
            return ResponseEntity.notFound().build();
        }

        Double valor = dados.get("valor");

        if (valor == null || valor <= 0) {
            return ResponseEntity.badRequest().build();
        }

        conta.setSaldo(conta.getSaldo() + valor);

        registroEventos.registrar(
                "DEPOSITO",
                timestamp,
                Map.of(
                        "idConta", id,
                        "valor", valor,
                        "saldoAtual", conta.getSaldo()
                )
        );

        return ResponseEntity.ok(conta);
    }

    @PostMapping("/{id}/saque")
    public ResponseEntity<Conta> sacar(
            @PathVariable Long id,
            @RequestBody Map<String, Double> dados
    ) throws IOException {

        int timestamp = relogioLamport.eventoLocal();

        Conta conta = contas.get(id);

        if (conta == null) {
            return ResponseEntity.notFound().build();
        }

        Double valor = dados.get("valor");

        if (valor == null || valor <= 0) {
            return ResponseEntity.badRequest().build();
        }

        if (conta.getSaldo() < valor) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).build();
        }

        conta.setSaldo(conta.getSaldo() - valor);

        registroEventos.registrar(
                "SAQUE",
                timestamp,
                Map.of(
                        "idConta", id,
                        "valor", valor,
                        "saldoAtual", conta.getSaldo()
                )
        );

        return ResponseEntity.ok(conta);
    }   
}
