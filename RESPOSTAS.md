# RESPOSTAS.md

## Parte B — Relógio de Lamport

### 1. Por que usar max(contador_local, timestampRecebido) + 1?

Ao receber uma mensagem, o relógio local precisa considerar tanto os eventos que já aconteceram na própria agência quanto o evento que veio da outra agência.

Por isso é utilizado `max(contador_local, timestampRecebido)`, garantindo que o relógio não volte para um valor menor que o seu contador atual. O `+1` representa o novo evento de recebimento da mensagem.

Dessa forma, o relógio lógico mantém a propriedade de que um evento causalmente anterior possui timestamp menor que o evento posterior.

### 2. Agência 0 está no contador 10 e recebe timestamp 3. Qual o novo valor?

O novo valor será:

`max(10, 3) + 1 = 11`

Portanto, o contador da Agência 0 passa para 11.

Isso mostra que uma agência que já processou muitos eventos não precisa voltar para o tempo lógico de uma agência mais lenta. Agências que processam eventos em velocidades diferentes podem possuir contadores diferentes, mas o recebimento de mensagens ajusta o relógio para preservar a relação de causalidade.
