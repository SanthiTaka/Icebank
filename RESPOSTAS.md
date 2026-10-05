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

- IDs 0, 3, 6, 9... pertencem à Agência 0;
- IDs 1, 4, 7, 10... pertencem à Agência 1;
- IDs 2, 5, 8, 11... pertencem à Agência 2.

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

- Conta 0: R$ 1.000 → R$ 970
- Conta 1: R$ 500 → R$ 530

O valor transferido permanece o mesmo, alterando apenas a distribuição dos saldos entre as duas contas.

## Parte E — Linha do tempo unificada

### 1. O relógio de Lamport garante que, se A aconteceu antes de B causalmente, timestamp(A) < timestamp(B). Ele não garante a volta. O que isso significa na prática quando vemos dois eventos com timestamps diferentes na linha do tempo, mas sem saber se um realmente influenciou o outro?

Significa que a diferença entre os timestamps não é suficiente para determinar que existe uma relação causal entre os eventos.

O relógio de Lamport garante a seguinte propriedade: se o evento A causou o evento B, então o timestamp de A será menor que o timestamp de B.

Porém, o contrário não é necessariamente verdadeiro. Se A possui timestamp 5 e B possui timestamp 8, não podemos concluir apenas por esses valores que A causou B. Os eventos podem ter ocorrido de forma independente em diferentes agências.

Portanto, o relógio de Lamport permite preservar relações de causalidade conhecidas, mas não permite identificar sozinho todas as relações causais existentes no sistema.

### 2. Baseado no que foi observado: o relógio de Lamport, sozinho, seria suficiente para um sistema que precisa distinguir com certeza “A e B são concorrentes” de “A aconteceu antes de B”? Por que isso motiva o relógio vetorial do Sprint 2?

Não. O relógio de Lamport sozinho não é suficiente para distinguir com certeza eventos concorrentes de eventos que possuem uma relação causal.

Durante o experimento, foi possível observar eventos de agências diferentes com o mesmo timestamp de Lamport. Esses eventos podem ser considerados concorrentes quando não existe uma cadeia causal entre eles. Porém, quando dois eventos possuem timestamps diferentes, não é possível concluir apenas pelos valores do relógio que um evento causou o outro.

O relógio vetorial do Sprint 2 é motivado justamente por essa limitação. Ele mantém informações sobre o estado lógico de cada agência, permitindo comparar dois eventos e determinar se existe uma relação de causalidade ou se eles são concorrentes.

Assim, enquanto o relógio de Lamport fornece uma ordenação lógica consistente dos eventos, o relógio vetorial permite identificar de forma mais precisa a relação de causalidade entre eventos distribuídos.

Parte F:

1. Qual a diferença entre autenticação e autorização? Sua implementação verifica só uma das duas, ou as duas? Por exemplo: um usuário autenticado consegue sacar de uma conta que não é dele, na sua implementação atual?

Autenticação é o processo de verificar quem é o usuário, enquanto autorização determina quais recursos ou operações esse usuário pode acessar. Na implementação atual, foi realizada apenas a autenticação. O usuário informa as credenciais admin e 123456 no endpoint /auth/login e, após a validação, recebe um JWT que deve ser enviado nas requisições protegidas.

A aplicação verifica se o JWT é válido e não está expirado, mas ainda não possui um mecanismo de autorização associado às contas. Portanto, um usuário autenticado consegue realizar operações em qualquer conta, desde que possua um token válido. Por exemplo, atualmente não existe uma regra que impeça o usuário autenticado de sacar de uma conta que não seja sua. Essa limitação é aceitável no escopo da Sprint 1, que tem como objetivo principal demonstrar autenticação por JWT.

2. Por que o servidor não precisa consultar um banco de dados para validar a assinatura de um JWT a cada requisição? O que isso implica sobre escalabilidade, comparado a guardar sessões em memória no servidor?

O JWT contém as informações necessárias para sua validação e é assinado digitalmente com uma chave secreta conhecida pelo servidor. Assim, a cada requisição, o servidor pode verificar localmente a assinatura e a validade temporal do token, sem precisar consultar um banco de dados para descobrir se aquela sessão existe.

Isso torna o mecanismo mais escalável, pois múltiplas requisições podem ser validadas sem depender de uma consulta a um armazenamento central de sessões. Além disso, diferentes instâncias da aplicação podem validar o mesmo JWT, desde que possuam a mesma chave de assinatura. Dessa forma, o sistema pode ser distribuído entre várias agências ou servidores sem precisar manter uma sessão armazenada em cada instância.

Em comparação, sessões mantidas em memória exigem que o servidor que recebeu a requisição tenha acesso à sessão correspondente. Em uma arquitetura com várias instâncias, isso pode exigir mecanismos adicionais, como sticky sessions ou um armazenamento compartilhado de sessões, aumentando a complexidade da infraestrutura.

3. O que aconteceria com a segurança do sistema se a chave secreta usada para assinar o JWT vazasse?

Se a chave secreta utilizada para assinar os JWTs vazasse, a segurança da autenticação seria comprometida. Um atacante que obtivesse essa chave poderia criar e assinar tokens JWT falsos, fazendo com que o sistema os considerasse legítimos.

Como a aplicação atualmente utiliza o JWT para autenticar as requisições, um atacante poderia criar um token com uma identidade escolhida por ele e acessar os endpoints protegidos. Por isso, a chave secreta deve ser mantida em segurança e não deve ser exposta no código-fonte ou no repositório em uma aplicação real.

Em um ambiente de produção, a chave deveria ser armazenada em uma variável de ambiente ou em um gerenciador seguro de segredos. Caso ocorresse um vazamento, seria necessário substituir imediatamente a chave e invalidar os tokens que foram assinados com a chave comprometida.

Parte G

### 1. Como o frontend “lembra” de reenviar o token em cada requisição depois do login?

Após o login, o backend retorna um token JWT. O frontend armazena esse token no `localStorage` do navegador utilizando a chave `iceibank_token`.

Nas requisições que precisam de autenticação, o frontend utiliza a função `fazerRequisicao()`. Essa função recupera o token armazenado no `localStorage` e o adiciona automaticamente ao cabeçalho HTTP da requisição:

`Authorization: Bearer <token>`

Dessa forma, o token não precisa ser informado manualmente em cada operação. Enquanto o token estiver armazenado e válido, ele é reutilizado nas requisições de consulta, depósito, saque e transferência.

### 2. Se o token expirar enquanto alguém está usando o frontend no meio de uma operação, o que acontece na sua implementação?

O backend retorna o código HTTP `401 Unauthorized` quando o token está inválido ou expirado.

No frontend, a função `fazerRequisicao()` verifica especificamente esse status. Quando recebe um `401`, ela remove o token armazenado no `localStorage`, retorna a interface para a tela de login e exibe a mensagem:

> "Sessão expirada ou token inválido. Faça login novamente."

Portanto, a pessoa usuária recebe uma informação específica sobre a expiração ou invalidação da sessão, em vez de visualizar apenas uma mensagem de erro genérica.

### 3. Esta unidade da disciplina trata de arquitetura MVC. No seu frontend, onde fica o “M” (Model), o “V” (View) e o “C” (Controller)? Eles existem de forma clara na sua implementação, ou o código ficou mais misturado do que o padrão sugere?

No frontend desenvolvido com HTML, CSS e JavaScript, a separação MVC existe de maneira conceitual, mas não está estruturada de forma tão rígida quanto em frameworks que seguem explicitamente esse padrão.

- **View (V):** está principalmente no `index.html`, que define a estrutura da interface, os campos, botões e áreas onde os resultados das operações são apresentados.
- **Model (M):** corresponde principalmente aos dados das contas e das operações bancárias recebidos e enviados em formato JSON entre o frontend e a API. Esses dados representam as informações que a aplicação manipula.
- **Controller (C):** está principalmente no `script.js`, que trata os eventos dos usuários, realiza as requisições para a API, processa as respostas e determina quais informações devem ser apresentadas na interface.

Portanto, os papéis de Model, View e Controller podem ser identificados, porém a separação não é completamente rígida. Como foi utilizado JavaScript puro, algumas responsabilidades ficam concentradas no `script.js`, fazendo com que o frontend seja mais misturado do que uma implementação MVC tradicional.

## Sprint 2 — Parte B: Relógio Vetorial

### 1. Com 3 agências, o vetor tem 3 posições. Se o sistema crescesse para 10 agências, o que aconteceria com o tamanho de cada vetor anexado a cada mensagem? Isso é um problema? Por quê?

O vetor também passaria a ter 10 posições, pois cada posição representa o contador lógico de uma agência. Portanto, quanto maior o número de agências, maior será o vetor enviado junto com cada mensagem.

Em um sistema pequeno, como o ICEIBank, isso não é um grande problema. Porém, em sistemas distribuídos com muitas máquinas ou processos, o tamanho do vetor pode gerar maior consumo de memória, armazenamento e tráfego de rede, pois todas as mensagens precisam carregar um contador para cada processo participante.

### 2. Dado V1 = [3, 1, 0] e V2 = [3, 2, 0], qual evento aconteceu primeiro, ou eles são concorrentes?

O evento representado por V1 aconteceu antes do evento representado por V2.

Comparando posição por posição:

- 3 <= 3
- 1 <= 2
- 0 <= 0

Todos os valores de V1 são menores ou iguais aos valores de V2 e pelo menos uma posição é diferente. Portanto:

V1 < V2

Isso indica uma relação causal em que o evento de V1 aconteceu antes do evento de V2.

### 3. Dado V1 = [3, 1, 0] e V2 = [1, 3, 0], qual evento aconteceu primeiro, ou eles são concorrentes?

Os eventos são concorrentes.

Comparando os vetores:

- Na primeira posição, V1 possui 3 e V2 possui 1, então V1 é maior.
- Na segunda posição, V1 possui 1 e V2 possui 3, então V2 é maior.
- Na terceira posição, ambos possuem 0.

Portanto, V1 não é menor ou igual a V2 em todas as posições e V2 também não é menor ou igual a V1 em todas as posições.

Isso significa que não existe uma relação de causalidade conhecida entre os dois eventos. Assim, eles são considerados concorrentes.


## Sprint 2 — Parte C: Publish/Subscribe entre Agências

### 1. No passo 4 da tarefa, o que aconteceu quando a Agência 1 voltou? Se a mensagem não foi aplicada, isso ocorreu porque a mensageria falhou ou por outro motivo?

Quando a Agência 1 voltou a se conectar ao RabbitMQ, a mensagem que havia sido publicada enquanto ela estava fora do ar foi entregue pela fila.

Porém, como as contas do ICEIBank ainda são armazenadas apenas em memória, ao reiniciar a Agência 1 as contas que existiam anteriormente foram perdidas. Dessa forma, o consumidor recebeu a mensagem, mas não encontrou a conta de destino para aplicar o crédito.

Nesse caso, o sistema registra um evento `CREDITO_REMOTO_FALHOU`, informando que a conta não foi encontrada.

Portanto, a falha não ocorreu porque o RabbitMQ perdeu a mensagem. Pelo contrário: a mensagem foi preservada e posteriormente entregue. O problema está na ausência de persistência das contas.

### 2. Compare esse comportamento com o Sprint 1. O que melhorou com a mensageria e o que continua sendo um problema?

No Sprint 1, a comunicação entre as agências era feita por uma chamada REST direta e síncrona. Se a agência de destino estivesse fora do ar, a chamada falhava imediatamente depois que o valor já havia sido debitado da conta de origem.

Na Sprint 2, a transferência utiliza RabbitMQ. A agência de origem não precisa mais que a agência de destino esteja disponível naquele instante. A mensagem pode permanecer armazenada na fila e ser consumida quando a agência voltar.

Isso melhora o desacoplamento e a resiliência da comunicação, pois a indisponibilidade temporária do consumidor não faz a mensagem desaparecer.

Porém, isso não garante que toda a operação bancária esteja correta. As contas ainda vivem apenas em memória. Assim, caso a agência seja reiniciada, os dados das contas são perdidos e uma mensagem preservada pelo RabbitMQ pode chegar sem existir mais uma conta onde aplicar o crédito.

Portanto, "a mensagem não se perdeu" não significa necessariamente que "a transferência foi concluída corretamente".

### 3. O consumidor RabbitMQ processa créditos sem verificar JWT. Isso é um problema de segurança?

O consumidor RabbitMQ não utiliza JWT porque ele não recebe uma requisição HTTP feita pelo frontend. Ele processa mensagens internas publicadas no broker pelas próprias agências do sistema.

No ambiente atual, isso é aceitável desde que somente componentes autorizados tenham acesso às credenciais do RabbitMQ. Quem possui acesso válido ao broker pode, em princípio, publicar mensagens na exchange e tentar simular operações internas.

Por isso, a segurança dessa comunicação depende do controle das credenciais e permissões do RabbitMQ. Em um sistema real, seria importante restringir quais aplicações podem publicar e consumir determinadas filas ou exchanges e proteger adequadamente as credenciais.

O JWT continua sendo necessário para proteger as requisições HTTP feitas pelos usuários ao backend, enquanto o RabbitMQ utiliza seu próprio mecanismo de autenticação e autorização para a comunicação interna.

## Sprint 2 — Parte D: Linha do Tempo Causal

### 1. No Sprint 1, o relógio de Lamport não permitia essa análise. O que exatamente, no relógio vetorial, torna possível essa comparação confiável?

O relógio vetorial mantém um contador separado para cada processo ou agência. Dessa forma, cada timestamp carrega informações sobre quais eventos de outras agências já eram conhecidos no momento em que o evento aconteceu.

Ao comparar dois vetores posição por posição, é possível verificar se todas as posições de um vetor são menores ou iguais às posições do outro. Nesse caso existe uma relação de causalidade.

Se um vetor for maior em uma posição e o outro for maior em outra, nenhum deles contém completamente o histórico causal do outro. Nesse caso, os eventos são concorrentes.

O relógio de Lamport utilizava apenas um número inteiro e, por isso, conseguia garantir que uma relação causal produzisse timestamps crescentes, mas não permitia concluir o inverso com certeza.

### 2. Encontre, no seu próprio teste, um par de eventos que o script classificou como concorrente. Faz sentido?

No teste realizado, o script identificou como concorrentes eventos de criação de conta ocorridos de forma independente na Agência 0 e na Agência 1.

Essa classificação faz sentido porque as duas operações aconteceram em agências diferentes sem que houvesse troca de mensagens ou transferência entre elas naquele momento.

Como nenhum dos eventos possuía conhecimento causal sobre o outro, nenhum dos vetores dominava completamente o outro. Dessa forma, o relógio vetorial permitiu identificar corretamente que os eventos eram concorrentes e que não existia uma relação de causa e efeito entre eles.

### 3. O algoritmo de comparação é O(n²). Isso seria um problema com milhões de eventos? Como torná-lo mais escalável?

Sim. Como o algoritmo compara cada evento com todos os eventos posteriores, a quantidade de comparações cresce aproximadamente com o quadrado da quantidade de eventos.

Com poucos eventos isso é aceitável, mas com milhões de registros o custo de processamento seria muito alto.

Uma alternativa seria limitar a comparação a janelas de tempo ou subconjuntos de eventos relevantes, evitando comparar eventos que claramente não precisam ser relacionados. Também seria possível indexar eventos por processo, utilizar processamento incremental conforme novos eventos chegam ou empregar sistemas especializados de processamento distribuído para analisar grandes volumes de logs.

## Sprint 2 — Funcionalidade Adicional

### Confirmação de crédito via mensageria

Como funcionalidade adicional da Sprint 2, foi implementado um mecanismo de confirmação de crédito entre as agências.

Na implementação obrigatória, a agência de origem publica uma mensagem solicitando que outra agência credite determinado valor em uma conta. Porém, a resposta HTTP para a transferência indica apenas que essa mensagem foi publicada no RabbitMQ, e não que o crédito realmente foi aplicado.

Para melhorar a observabilidade desse fluxo, após a agência de destino consumir a mensagem e aplicar o crédito com sucesso, ela publica uma segunda mensagem usando a routing key `agencia.<id>.confirmacao`.

Cada agência possui uma fila adicional para receber essas confirmações. Quando a agência de origem consome a mensagem, é registrado no log o evento `CONFIRMACAO_CREDITO_RECEBIDA`.

A confirmação somente é publicada se a conta de destino existir e o crédito for realmente aplicado. Caso a mensagem de crédito seja recebida e a conta não exista, o sistema registra `CREDITO_REMOTO_FALHOU` e nenhuma confirmação é enviada.

Escolhi essa funcionalidade porque ela complementa a arquitetura assíncrona da Sprint 2. A publicação inicial informa que o RabbitMQ recebeu a solicitação, enquanto a confirmação permite observar posteriormente que a operação de crédito foi efetivamente processada pela agência de destino.