
package com.iceibank.agencia.model;

public record BullyMensagem(
        Integer origemId,
        Integer liderId,
        int[] vetorEnvio
) {
}
