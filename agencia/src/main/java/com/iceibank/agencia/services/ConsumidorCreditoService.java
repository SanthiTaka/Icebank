package com.iceibank.agencia.services;

import java.io.IOException;
import java.util.Map;

import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import com.iceibank.agencia.model.Conta;
import com.iceibank.agencia.model.MensagemCredito;

@Service
public class ConsumidorCreditoService {

    private final ContasService contasService;
    private final RelogioVetorial relogioVetorial;
    private final RegistroEventos registroEventos;

    public ConsumidorCreditoService(
            ContasService contasService,
            RelogioVetorial relogioVetorial,
            RegistroEventos registroEventos
    ) {
        this.contasService = contasService;
        this.relogioVetorial = relogioVetorial;
        this.registroEventos = registroEventos;
    }

    @RabbitListener(queues = "#{filaAgencia.name}")
    public void receberCredito(
            MensagemCredito mensagem
    ) throws IOException {

        System.out.println(
                "[RabbitMQ] Crédito recebido para conta "
                + mensagem.getIdConta()
        );

        int[] timestampVetorial =
                relogioVetorial.aoReceber(
                        mensagem.getVetorEnvio()
                );

        Conta conta =
                contasService.buscar(
                        mensagem.getIdConta()
                );

        if (conta == null) {

            registroEventos.registrar(
                    "CREDITO_REMOTO_FALHOU",
                    timestampVetorial,
                    Map.of(
                            "idConta",
                            mensagem.getIdConta(),
                            "valor",
                            mensagem.getValor(),
                            "origemAgencia",
                            mensagem.getOrigemAgencia(),
                            "motivo",
                            "conta nao encontrada"
                    )
            );

            System.out.println(
                    "[RabbitMQ] Conta "
                    + mensagem.getIdConta()
                    + " não encontrada. Crédito não aplicado."
            );

            return;
        }

        conta.setSaldo(
                conta.getSaldo()
                + mensagem.getValor()
        );

        registroEventos.registrar(
                "TRANSFERENCIA_CREDITO_REMOTO",
                timestampVetorial,
                Map.of(
                        "idConta",
                        mensagem.getIdConta(),
                        "valor",
                        mensagem.getValor(),
                        "origemAgencia",
                        mensagem.getOrigemAgencia(),
                        "saldoAtual",
                        conta.getSaldo()
                )
        );

        System.out.println(
                "[RabbitMQ] Crédito remoto aplicado. Novo saldo: "
                + conta.getSaldo()
        );
    }
}