
package com.iceibank.agencia.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.iceibank.agencia.model.BullyMensagem;
import com.iceibank.agencia.services.BullyService;

@RestController
@RequestMapping("/bully")
public class BullyController {

    private final BullyService bullyService;

    public BullyController(BullyService bullyService) {
        this.bullyService = bullyService;
    }

    @PostMapping("/eleicao")
    public ResponseEntity<Void> receberEleicao(
            @RequestBody BullyMensagem mensagem
    ) {

        if (mensagem.origemId() == null
                || mensagem.vetorEnvio() == null
                || mensagem.vetorEnvio().length != 3) {
            return ResponseEntity.badRequest().build();
        }

        bullyService.receberEleicao(
                mensagem.origemId(),
                mensagem.vetorEnvio()
        );

        return ResponseEntity.ok().build();
    }

    @PostMapping("/coordenador")
    public ResponseEntity<Void> receberCoordenador(
            @RequestBody BullyMensagem mensagem
    ) {

        if (mensagem.liderId() == null
                || mensagem.vetorEnvio() == null
                || mensagem.vetorEnvio().length != 3) {
            return ResponseEntity.badRequest().build();
        }

        bullyService.receberCoordenador(
                mensagem.liderId(),
                mensagem.vetorEnvio()
        );

        return ResponseEntity.ok().build();
    }

   @GetMapping("/lider")
public ResponseEntity<Object> consultarLider() {

    return ResponseEntity.ok(
            java.util.Map.of(
                    "idAgencia", bullyService.obterIdAgencia(),
                    "liderAtual",
                    bullyService.obterLiderAtual() == null
                            ? -1
                            : bullyService.obterLiderAtual()
            )
    );
}
}
