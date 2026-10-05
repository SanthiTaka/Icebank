package com.iceibank.agencia.services;

import java.io.IOException;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import com.iceibank.agencia.config.AgenciaConfig;
import com.iceibank.agencia.model.Conta;
import com.iceibank.agencia.model.MensagemConfirmacao;
import com.iceibank.agencia.model.MensagemCredito;

@Service
public class ConsumidorCreditoService {

    private final ContasService contasService;
    private final RelogioVetorial relogioVetorial;
    private final RegistroEventos registroEventos;
    private final MensageriaService mensageriaService;

    private final int idAgencia;

    public ConsumidorCreditoService(
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

        this.idAgencia =
                porta - AgenciaConfig.PORTA_BASE;
    }

    @RabbitListener(
            queues = "#{filaAgenciaCredito.name}"
    )
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
                            + " não encontrada."
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
                "[RabbitMQ] Crédito aplicado. Novo saldo: "
                        + conta.getSaldo()
        );

        // =================================================
        // FUNCIONALIDADE ADICIONAL:
        // CONFIRMAÇÃO DE CRÉDITO
        // =================================================

        int[] vetorConfirmacao =
                relogioVetorial.aoEnviar();

        MensagemConfirmacao confirmacao =
                new MensagemConfirmacao(
                        mensagem.getIdConta(),
                        mensagem.getValor(),
                        idAgencia,
                        vetorConfirmacao
                );

        mensageriaService.publicarConfirmacao(
                mensagem.getOrigemAgencia(),
                confirmacao
        );

        registroEventos.registrar(
                "CONFIRMACAO_CREDITO_PUBLICADA",
                vetorConfirmacao,
                Map.of(
                        "idConta",
                        mensagem.getIdConta(),

                        "valor",
                        mensagem.getValor(),

                        "agenciaOrigem",
                        mensagem.getOrigemAgencia(),

                        "agenciaDestino",
                        idAgencia
                )
        );
    }
}