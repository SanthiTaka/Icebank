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

## Parte D — Transferências

### 1. Qual é a diferença entre uma transferência local e uma transferência entre agências?

Na transferência local, as contas de origem e destino pertencem à mesma agência. Dessa forma, a operação é realizada diretamente na memória da própria agência, sem necessidade de comunicação pela rede com outra instância.

Na transferência entre agências, as contas pertencem a agências diferentes. A agência de origem identifica a agência responsável pela conta de destino através da regra `idConta % 3` e envia uma requisição HTTP para a agência correspondente. A agência destino recebe a solicitação, atualiza o saldo da conta e registra o evento utilizando seu próprio relógio de Lamport.

### 2. Como a agência determina qual agência é responsável por uma conta?

A distribuição das contas utiliza a operação módulo 3:

`agenciaResponsavel(idConta) = idConta % 3`

Assim:

* IDs 0, 3, 6, 9... pertencem à Agência 0;
* IDs 1, 4, 7, 10... pertencem à Agência 1;
* IDs 2, 5, 8, 11... pertencem à Agência 2.

Como consequência, cada agência gera IDs começando pelo seu número e incrementando de 3 em 3.

### 3. Como o relógio de Lamport é utilizado em uma transferência entre agências?

Antes de enviar a transferência, a agência de origem incrementa seu relógio de Lamport por meio de `aoEnviar()`. O timestamp gerado é enviado junto com a requisição para a agência destino.

Ao receber a transferência, a agência destino utiliza `aoReceber(timestampRecebido)`, calculando:

`max(contador_local, timestampRecebido) + 1`

Dessa forma, o evento de recebimento ocorre logicamente depois tanto dos eventos anteriores da agência destino quanto do evento de envio registrado pela agência de origem.

### 4. O que acontece quando a comunicação com a agência destino falha?

Na implementação da Sprint 1, o débito da conta de origem ocorre antes da tentativa de comunicação com a agência destino.

Se a comunicação falhar, a aplicação registra o evento `TRANSFERENCIA_FALHOU` e retorna um erro HTTP 502 (`Bad Gateway`) informando que houve falha na comunicação com a agência destino.

Essa situação representa uma limitação conhecida da Sprint 1: o sistema ainda não possui um mecanismo distribuído de rollback para desfazer automaticamente o débito realizado na agência de origem.

Esse problema será tratado posteriormente no projeto, especialmente no contexto de transações distribuídas das próximas etapas.

### 5. Por que não é feito rollback automático quando a transferência entre agências falha?

O rollback automático não foi implementado porque a Sprint 1 trabalha apenas com comunicação HTTP simples entre as agências e ainda não possui um protocolo de transação distribuída.

Se a agência de origem debitar a conta e a comunicação com a agência destino falhar, seria necessário um mecanismo capaz de garantir a consistência da operação em múltiplos participantes.

A implementação desse tipo de mecanismo faz parte do escopo das etapas posteriores do projeto, que abordarão transações distribuídas, como 2PC ou Saga.

### 6. Qual é a diferença observável nos logs entre uma transferência local e uma transferência entre agências?

Na transferência local é registrado o evento:

`TRANSFERENCIA_LOCAL`

Já na transferência entre agências são registrados eventos distintos para representar o envio e o recebimento, permitindo observar a comunicação distribuída e a evolução dos relógios de Lamport.

Em caso de falha de comunicação, é registrado:

`TRANSFERENCIA_FALHOU`

Dessa forma, os logs permitem identificar não apenas que uma transferência ocorreu, mas também se ela foi local, entre agências ou se houve falha durante a comunicação.

### 7. O que acontece com os saldos em uma transferência entre agências bem-sucedida?

Em uma transferência bem-sucedida, o valor é subtraído da conta de origem e adicionado à conta de destino.

Por exemplo, considerando uma transferência de R$ 30 da conta 0 para a conta 1:

* Conta 0: R$ 1.000 → R$ 970
* Conta 1: R$ 500 → R$ 530

O valor transferido permanece o mesmo, alterando apenas a distribuição dos saldos entre as duas contas.
