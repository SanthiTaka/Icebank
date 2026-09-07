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
