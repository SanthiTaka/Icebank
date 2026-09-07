package com.iceibank.agencia.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.iceibank.agencia.model.Conta;

@Service
public class ContasService {

    private final Map<Long, Conta> contas = new ConcurrentHashMap<>();
    private final AtomicLong proximoId;

    public ContasService(@Value("${server.port:4093}") int porta) {
        int agencia = porta - 4093;
        this.proximoId = new AtomicLong(agencia);
    }

    public Conta criar(Conta conta) {
        Long id = proximoId.getAndAdd(3);
        conta.setId(id);
        contas.put(id, conta);
        return conta;
    }

    public List<Conta> listar() {
        return new ArrayList<>(contas.values());
    }

    public Conta buscar(Long id) {
        return contas.get(id);
    }

    public Conta atualizar(Long id, Conta dados) {
        Conta conta = contas.get(id);

        if (conta == null) {
            return null;
        }

        conta.setTitular(dados.getTitular());
        conta.setSaldo(dados.getSaldo());

        return conta;
    }

    public Conta excluir(Long id) {
        return contas.remove(id);
    }

    public synchronized Conta depositar(Long id, double valor) {
        Conta conta = contas.get(id);

        if (conta == null) {
            return null;
        }

        conta.setSaldo(conta.getSaldo() + valor);
        return conta;
    }

    public synchronized Conta sacar(Long id, double valor) {
        Conta conta = contas.get(id);

        if (conta == null || conta.getSaldo() < valor) {
            return null;
        }

        conta.setSaldo(conta.getSaldo() - valor);
        return conta;
    }
}