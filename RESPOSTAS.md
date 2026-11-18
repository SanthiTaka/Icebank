# RESPOSTAS.md — ICEIBank (Sprints 1 e 2)

**Disciplina:** Laboratório de Desenvolvimento de Aplicações Móveis e Distribuídas  
**Aluno:** Individual  
**Projeto:** ICEIBank — Sistema Bancário Distribuído

---

# SPRINT 1

## 1. Parte B — Relógio de Lamport (Seção 6.4)

### 1. Por que o relógio de Lamport usa `max(contador_local, timestampRecebido) + 1` ao receber uma mensagem, em vez de simplesmente adotar o timestamp recebido diretamente?

O algoritmo do Relógio Lógico de Lamport estabelece que a causalidade entre eventos deve ser rigorosamente respeitada (*happened-before relation*, $A \to B \implies C(A) < C(B)$). Quando um processo recebe uma mensagem, o evento de recebimento é necessariamente posterior a dois pontos no tempo lógico:
1. A todos os eventos que o próprio processo receptor já realizou localmente (representados por `contador_local`);
2. Ao evento de envio da mensagem realizado pelo processo emissor (representado por `timestampRecebido`).

Se o processo receptor simplesmente adotasse o `timestampRecebido` diretamente:
- Caso seu `contador_local` já fosse maior que o `timestampRecebido`, o relógio "andaria para trás" no processo receptor, quebrando a monotonicidade estrita de seus próprios eventos locais.
- Se adotasse o valor sem o incremento `+ 1`, o evento de recebimento teria o mesmo timestamp do evento de envio, violando a regra causal de que o recebimento ocorre *estritamente depois* do envio ($C(recebimento) > C(envio)$).

Portanto, a fórmula `max(contador_local, timestampRecebido) + 1` garante simultaneamente a monotonicidade local e a preservação da causalidade distribuída.

---

### 2. Se a Agência 0 está no evento de contador 10 e recebe uma mensagem com timestamp 3 (de uma agência mais “atrasada”), qual o novo valor do contador da Agência 0? O que isso implica sobre agências que processam muitos eventos rapidamente versus agências mais lentas?

Aplicando a regra de atualização do algoritmo de Lamport:
$$\text{novo\_contador} = \max(10, 3) + 1 = 10 + 1 = 11$$

O novo valor do contador da Agência 0 será **11**.

**Implicações:**
- **Agências rápidas não regridem:** Uma agência com alto volume de operações (como a Agência 0, com contador 10) não desacelera nem retrocede seu tempo lógico ao interagir com uma agência com pouca atividade (com contador 3).
- **Avanço assimétrico (salto lógico):** Quando o oposto acontece (uma agência lenta com contador 3 recebe mensagem com timestamp 10), ela é "puxada para frente", saltando diretamente para $\max(3, 10) + 1 = 11$. Isso sincroniza logicamente os processos participantes, fazendo com que a agência mais lenta tome conhecimento do avanço temporal global do sistema distribuído.

---

## 2. Parte D — Transferências (Seção 8.3)

### 1. No trecho `agenciaDestino === idAgencia`, por que a transferência local não precisa da lógica de `aoEnviar()` / `aoReceber()` do relógio de Lamport, enquanto a transferência entre agências precisa?

Na **transferência local**, o débito e o crédito ocorrem dentro do mesmo processo (mesma JVM / mesma instância da agência) e compartilham o mesmo espaço de memória. Não há mensagem trafegando pela rede nem troca de contexto entre processos independentes. Dessa forma, as operações são apenas eventos locais sequenciais de um único processo, bastando invocar `eventoLocal()`, que simplesmente incrementa o contador local em $+1$.

Já na **transferência entre agências**, há dois processos distribuídos independentes comunicando-se através da rede via HTTP. Pelo Teorema de Lamport, para estabelecer causalidade entre dois processos desacoplados no tempo e no espaço, é estritamente obrigatório que:
1. O processo emissor registre o envio e anexe seu timestamp lógico à mensagem (`aoEnviar()`);
2. O processo receptor intercepte a mensagem e atualize seu estado causal com base no timestamp recebido (`aoReceber(ts)`).

Sem `aoEnviar()` e `aoReceber()`, a agência de destino não teria como saber em que ponto do tempo lógico a transferência foi originada, rompendo a linha do tempo causal do sistema distribuído.

---

### 2. Reproduza a falha conhecida (tarefa 5) e observe o saldo da conta de origem depois do erro. Ele foi revertido? O que isso significa em termos de consistência do sistema bancário?

Ao simular a queda da agência de destino (por exemplo, desligando a Agência 1 na porta 4094 e disparando uma transferência da conta 0 na Agência 0 para a conta 1), a requisição HTTP falhou com erro de conexão, gerando a resposta `HTTP 502 Bad Gateway` e o registro de log `TRANSFERENCIA_INTERAGENCIA_FALHA`.

**Observação do saldo de origem:**
O saldo da conta de origem **não foi revertido**. O débito havia sido aplicado em memória antes da chamada de rede e permaneceu debitado.

**Significado em termos de consistência:**
Isso evidencia a violação do princípio de **Atomicidade** (das propriedades ACID): a operação foi executada pela metade (o débito ocorreu, mas o crédito não). O dinheiro "desapareceu" temporariamente do sistema bancário, deixando o estado global inconsistente. Em um banco real, essa situação é inaceitável e requer mecanismos de coordenação transacional distribuída.

---

### 3. Pensando à frente para o Sprint 4: cite, em alto nível, duas formas possíveis de corrigir esse problema (não precisa implementar agora, só descrever a ideia).

1. **Protocolo Two-Phase Commit (2PC):**
   - Um coordenador de transação divide a transferência em duas fases:
     - *Fase 1 (Prepare / Vote):* Pergunta a ambas as agências se podem realizar o débito e o crédito (bloqueando/reservando os saldos).
     - *Fase 2 (Commit / Abort):* Se ambas responderem afirmativamente, o coordenador envia a ordem definitiva de `COMMIT`. Se a agência de destino estiver fora do ar ou recusar, envia `ABORT` para todas, garantindo que o débito seja revertido automaticamente.

2. **Padrão Saga (Transações Compensatórias):**
   - A transação distribuída é dividida em uma série de transações locais encadeadas.
   - Caso uma das etapas falhe (como a falha ao contatar a agência de destino), o sistema dispara automaticamente uma **transação compensatória** (no caso, um estorno de crédito na conta de origem) para desfazer os efeitos colaterais da transação inicial, restaurando a consistência eventual do saldo.

---

## 3. Parte E — Linha do Tempo Unificada (Seção 10.3)

### 1. O relógio de Lamport garante que, se A aconteceu antes de B causalmente, `timestamp(A) < timestamp(B)`. Ele não garante a volta. O que isso significa na prática quando você vê dois eventos com timestamps diferentes na linha do tempo, mas sem saber se um realmente influenciou o outro?

Significa que o relógio de Lamport fornece apenas uma **ordenação parcial fraca** dos eventos distribuídos.
- Se sabemos a priori que $A \to B$ (por exemplo, $A$ é o envio de uma transferência e $B$ é o recebimento), temos a certeza de que $\text{timestamp}(A) < \text{timestamp}(B)$.
- Porém, a implicação inversa não é verdadeira ($\text{timestamp}(A) < \text{timestamp}(B) \not\implies A \to B$). Se observamos um evento $A$ na Agência 0 com timestamp 2 e um evento $B$ na Agência 1 com timestamp 5, sem histórico de comunicação entre elas, **não podemos afirmar que $A$ causou $B$**. Eles podem ser eventos totalmente independentes e concorrentes, tendo o timestamp 5 ocorrido apenas porque a Agência 1 executou mais operações locais prévias.

---

### 2. Baseado no que você observou no passo 3 da tarefa: o relógio de Lamport, sozinho, seria suficiente para um sistema que precisa distinguir com certeza “A e B são concorrentes” de “A aconteceu antes de B”? Por que isso motiva o relógio vetorial do Sprint 2?

**Não, o relógio de Lamport não é suficiente.**  
Na linha do tempo unificada, quando dois eventos possuem timestamps iguais ou quando um timestamp é menor que outro sem relação causal direta, o relógio escalar (um único número inteiro) não carrega metadados suficientes para sabermos se um processo conhecia o estado do outro.

Essa deficiência motiva diretamente o **Relógio Vetorial (Sprint 2)**:
- Em vez de um escalar, cada processo mantém um vetor contendo o conhecimento do estado lógico de *todas* as agências do sistema.
- Comparando vetores posição a posição ($V_A \le V_B$), o relógio vetorial garante equivalência lógica nos dois sentidos ($A \to B \iff V_A < V_B$), permitindo afirmar com 100% de precisão matemática quando dois eventos são concorrentes ($V_A \parallel V_B$) ou causalmente relacionados.

---

## 4. Parte F — Autenticação (JWT) (Seções 11.1 e 11.3)

### Decisões de Design Implementadas:
- **Formato das Credenciais:** Usuário e senha trafegados via JSON no corpo da requisição (`POST /auth/login`), validando usuário `admin` e senha `123456`.
- **Tempo de Expiração:** Configurado em `application.properties` para **600.000 ms (10 minutos)**. Esse tempo garante segurança contra replay attacks e vazamento de tokens, sem forçar o usuário a relogar a cada minuto.
- **Chamada entre Agências (`/transferencias/receber`):** Decidiu-se liberar a rota `/transferencias/receber` no `SecurityConfig` para chamadas internas de backend a backend. Em uma arquitetura orientada a serviços distribuídos nesta fase, a autenticação de usuário final (Bearer JWT) destina-se a clientes externos (frontend). A comunicação inter-agências é tratada como canal interno direto entre nós da aplicação.

---

### 1. Qual a diferença entre autenticação e autorização? Sua implementação verifica só uma das duas, ou as duas? Por exemplo: um usuário autenticado consegue sacar de uma conta que não é dele, na sua implementação atual?

- **Autenticação:** É a validação da **identidade** do chamador ("quem é você?").
- **Autorização:** É a validação dos **privilégios e permissões** do usuário sobre um recurso específico ("o que você pode fazer com este recurso?").

**Na implementação atual:**
Verifica-se apenas a **autenticação**. Uma vez que o usuário envia um Bearer JWT válido gerado no `/auth/login`, ele tem acesso autorizado a qualquer rota da API protegida. Portanto, um usuário autenticado consegue realizar saques ou transferências em qualquer conta cadastrada, mesmo que ela pertença a outro titular. Essa separação é esperada para o escopo da Sprint 1, onde o objetivo é estabelecer o filtro de token e a infraestrutura segura.

---

### 2. Por que o servidor não precisa consultar um banco de dados para validar a assinatura de um JWT a cada requisição? O que isso implica sobre escalabilidade, comparado a guardar sessões em memória no servidor?

O JWT é **autocontido** (*stateless*): ele carrega em seu *payload* as informações do usuário (claims) e no cabeçalho o algoritmo de assinatura. O servidor assina o token com uma chave secreta criptográfica (HMAC-SHA). Quando o token retorna no cabeçalho `Authorization: Bearer`, qualquer agência que possua a mesma chave secreta pode verificar a integridade da assinatura matematicamente, sem realizar nenhuma consulta externa.

**Implicações de Escalabilidade:**
- **Zero I/O de sessão:** Elimina o gargalo de consultas a bancos de dados de sessão (Redis/Postgres) a cada requisição HTTP.
- **Escala horizontal trivial:** Como as agências não guardam sessão em memória, qualquer requisição pode cair em qualquer réplica ou agência sem necessidade de *sticky sessions* ou sincronização de memória de sessão.

---

### 3. O que aconteceria com a segurança do sistema se a chave secreta usada para assinar o JWT vazasse?

O sistema seria completamente comprometido:
- Um invasor de posse da chave secreta poderia forjar tokens JWT arbitrários com qualquer privilégio, identidade ou tempo de expiração.
- As instâncias do servidor aceitariam esses tokens como genuínos, permitindo que o atacante executasse qualquer operação bancária protegida.
- A única remediação imediata seria a **rotação da chave secreta**, invalidando instantaneamente todos os tokens ativos no sistema e obrigando todos os usuários legítimos a efetuarem novo login.

---

## 5. Parte G — Frontend (Seção 12.3)

### 1. Como o frontend “lembra” de reenviar o token em cada requisição depois do login? Descreva, em alto nível, o mecanismo que você implementou.

Ao submeter o formulário de login com sucesso, o frontend recebe o token JWT e o persiste no `localStorage` do navegador sob a chave `iceibank_token`.  
Todas as chamadas à API são centralizadas na função utilitária assíncrona `fazerRequisicao(url, opcoes)`. Antes de disparar o `fetch`, essa função lê o token armazenado via `localStorage.getItem(TOKEN_KEY)` e injeta dinamicamente o cabeçalho:
```http
Authorization: Bearer <token>
```
Dessa forma, qualquer operação de consulta, criação de conta, depósito, saque, transferência ou extrato carrega o token de forma transparente.

---

### 2. Se o token expirar enquanto alguém está usando o frontend no meio de uma operação, o que acontece na sua implementação? A interface avisa a pessoa usuária, ou ela só vê um erro genérico?

A função `fazerRequisicao` intercepta especificamente o código de resposta `HTTP 401 Unauthorized`.  
Quando o status 401 é detectado:
1. O token inválido/expirado é removido do armazenamento local (`localStorage.removeItem(TOKEN_KEY)`);
2. A tela do banco é ocultada e a tela de login é exibida novamente;
3. É apresentada de forma clara e visível para o usuário a mensagem:
   > *"Sessão expirada ou token inválido. Faça login novamente."*

O usuário não vê um erro genérico e é conduzido imediatamente para renovar sua sessão.

---

### 3. Esta unidade da disciplina trata de arquitetura MVC. No seu frontend, onde fica o “M” (Model), o “V” (View) e o “C” (Controller)? Eles existem de forma clara na sua implementação, ou o código ficou mais misturado do que o padrão sugere?

- **View (V):** Representada no `index.html` e estilizada via `style.css`. Define o DOM, formulários de entrada, seletores de agência e elementos de exibição de resultados e mensagens.
- **Model (M):** Representado pelas estruturas de dados recebidas da API REST (objetos de Conta, Extrato, Transferência e Mensagens de erro em JSON) e mantidas temporariamente no estado da página.
- **Controller (C):** Concentrado no arquivo `script.js`, que atua como controlador de eventos de interface (*event listeners*), validação de formulários, chamadas de rede e manipulação dinâmica dos elementos da View.

**Análise crítica:**  
Por se tratar de JavaScript puro (Vanilla JS) sem framework MVVM/MVC formal (como Angular ou Vue), a separação entre Controller e View fica parcialmente misturada no `script.js`, pois o mesmo script que orquestra as regras e requisições também manipula o DOM diretamente (`innerHTML`, manipulação de estilos e classes). No entanto, a separação conceitual de responsabilidades está preservada.

---

# SPRINT 2

## 6. Sprint 2 — Parte B: Relógio Vetorial (Seção 6.4)

### 1. Com 3 agências, o vetor tem 3 posições. Se o sistema crescesse para 10 agências, o que aconteceria com o tamanho de cada vetor anexado a cada mensagem? Isso é um problema? Por quê?

O vetor passaria a ter 10 posições, pois cada posição representa o contador lógico de uma agência do sistema.  
Em um sistema pequeno como o ICEIBank (3 a 10 nós), o overhead de tráfego de rede e memória é negligenciável (poucos bytes adicionais por mensagem).  
No entanto, em sistemas distribuídos de larga escala (com centenas ou milhares de nós/processos):
- O tamanho do vetor cresce linearmente com o número de processos ($O(N)$), aumentando o consumo de largura de banda em todas as trocas de mensagens e o consumo de armazenamento nos logs de eventos.
- A complexidade de comparação de vetores também cresce com $O(N)$ por par comparado.  
Por essa razão, em sistemas de escala massiva, costumam-se utilizar relógios vetoriais esparsos, matrizes de causalidade com janelas deslizantes ou abordagens baseadas em relógios físicos sincronizados com limites de erro conhecidos (como o TrueTime do Google Spanner).

---

### 2. Dado V1 = [3, 1, 0] e V2 = [3, 2, 0], qual evento aconteceu primeiro, ou eles são concorrentes?

O evento associado a **V1 aconteceu antes** do evento associado a **V2** ($V_1 \to V_2$).

**Comparação posição por posição:**
- Posição 0: $3 \le 3$
- Posição 1: $1 \le 2$
- Posição 2: $0 \le 0$

Como $V_1[i] \le V_2[i]$ para todo $i$ e $V_1 \ne V_2$ (existe pelo menos uma posição estritamente menor, $1 < 2$), $V_1$ domina causalmente $V_2$. O processo 1 avançou um evento lógico conhecendo previamente o estado representado em $V_1$.

---

### 3. Dado V1 = [3, 1, 0] e V2 = [1, 3, 0], qual evento aconteceu primeiro, ou eles são concorrentes?

Os eventos são **concorrentes** ($V_1 \parallel V_2$).

**Comparação posição por posição:**
- Posição 0: $3 > 1$ (V1 é estritamente maior que V2 na posição da Agência 0)
- Posição 1: $1 < 3$ (V2 é estritamente maior que V1 na posição da Agência 1)
- Posição 2: $0 = 0$

Como nem todos os elementos de $V_1$ são $\le V_2$ e nem todos os de $V_2$ são $\le V_1$, nenhum dos eventos tinha conhecimento causal do outro no momento em que ocorreram. Eles aconteceram de forma totalmente independente e paralela.

---

## 7. Sprint 2 — Parte C: Publish/Subscribe entre Agências (Seção 7.5)

### 1. No passo 4 da tarefa, o que aconteceu quando a Agência 1 voltou? Se a mensagem não foi aplicada, isso ocorreu porque a mensageria falhou ou por outro motivo?

Quando a Agência 1 voltou e reconectou ao RabbitMQ, a mensagem retida na fila durável (`fila-agencia-1`) foi imediatamente entregue pelo broker ao consumidor da agência.  
No entanto, como as contas do ICEIBank nesta etapa ainda vivem **exclusivamente em memória**, ao reiniciar o processo da Agência 1 as contas cadastradas antes da queda foram perdidas. Quando o método `receberCredito` tentou buscar a conta de destino, ela não existia, registrando o log:
```
CREDITO_REMOTO_FALHOU { motivo: "conta nao encontrada", ... }
```
Portanto, a falha **não foi do RabbitMQ**. A mensageria cumpriu perfeitamente seu papel de entrega garantida e assíncrona. O problema foi a ausência de persistência em disco/banco de dados das entidades de conta.

---

### 2. Compare esse comportamento com o Sprint 1. O que melhorou com a mensageria e o que continua sendo um problema em aberto?

- **O que melhorou:**
  No Sprint 1, a chamada REST síncrona falhava imediatamente com erro 502 se a agência destino estivesse fora do ar, e a mensagem simplesmente se perdia no ar. Na Sprint 2, a mensageria assíncrona garante que a mensagem fica gravada no broker em fila durável até que o destinatário esteja online, desacoplando o emissor do receptor no tempo e eliminando falhas de envio por indisponibilidade transitória.
- **O que continua em aberto:**
  "A mensagem não se perde" não equivale a "a transação bancária está consistente e completa". Se a conta não existir mais após o reinício, ou se houver falha de negócio na agência de destino, o dinheiro continua debitado da origem sem ter sido creditado no destino, evidenciando a necessidade de persistência durável e de protocolos de transação distribuída (Sprint 4).

---

### 3. O consumidor RabbitMQ processa créditos sem verificar JWT. Isso é um problema de segurança?

O consumidor AMQP escuta eventos que trafegam internamente dentro da infraestrutura do message broker, e não via requisições HTTP expostas a clientes web.  
Em um ambiente seguro de produção:
- O acesso ao RabbitMQ é restrito por credenciais privadas, certificados TLS e rede interna isolada (VPC). Apenas agências autorizadas possuem credenciais para publicar na exchange `iceibank.eventos`.
- Dessa forma, o broker atua como um canal confiável entre serviços internos, dispensando o overhead de validação de token de sessão de usuário final para cada mensagem trafegada.
- No entanto, caso a conexão com o broker não fosse protegida ou as credenciais vazassem, qualquer pessoa conectada ao RabbitMQ poderia forjar mensagens de crédito remoto. Em sistemas críticos, pode-se incluir uma assinatura digital na mensagem (*payload signing*) para garantir a autenticidade do nó emissor.

---

## 8. Sprint 2 — Parte D: Linha do Tempo Causal (Seção 8.3)

### 1. No Sprint 1, o relógio de Lamport não permitia essa análise. O que exatamente, no relógio vetorial, torna possível essa comparação confiável?

O relógio de Lamport utiliza um único número escalar, o que só permite garantir causalidade em um único sentido: $A \to B \implies C(A) < C(B)$. Quando observamos dois eventos com timestamps diferentes, não há como saber se um causou o outro ou se são concorrentes.  
No relógio vetorial, cada processo mantém uma entrada no vetor para cada agência do sistema. Cada evento carrega o estado causal completo conhecido por aquele processo no momento da sua ocorrência.  
Isso torna a relação bicondicional:
$$A \to B \iff V_A < V_B$$
Ao comparar os vetores posição por posição, se houver inversão de superioridade em posições distintas, temos a certeza matemática de que os eventos são **concorrentes**.

---

### 2. Encontre, no seu próprio teste, um par de eventos que o script classificou como concorrente. Faz sentido?

No teste realizado, o script identificou como concorrentes os eventos de criação de conta na Agência 0 (`CRIAR_CONTA` com vetor `[1, 0, 0]`) e na Agência 1 (`CRIAR_CONTA` com vetor `[0, 1, 0]`).  
**Faz total sentido:**  
As duas contas foram criadas de forma independente em agências distintas, sem que houvesse nenhuma troca de mensagens ou transferências prévias entre elas. Nenhum dos nós sabia da existência do evento do outro nó quando os registrou, comprovando que não existe qualquer relação de causa e efeito entre eles.

---

### 3. O algoritmo de comparação é $O(n^2)$. Isso seria um problema com milhões de eventos? Como torná-lo mais escalável?

Sim, um algoritmo $O(n^2)$ torna-se inviável para milhões de eventos, pois para $10^6$ eventos seriam necessárias aproximadamente $5 \times 10^{11}$ comparações.  
**Estratégias de escalabilidade:**
1. **Janelas de tempo / Particionamento temporal:** Comparar apenas eventos que ocorreram dentro de uma janela temporal física próxima (ex.: últimos 5 minutos), descartando comparações com eventos distantes que já foram consolidados.
2. **Indexação por Grafo Causal (DAG):** Em vez de comparar todos contra todos, modelar as dependências como um grafo acíclico direcionado, onde apenas nós vizinhos ou ramos divergentes são analisados.
3. **Processamento em Stream distribuído:** Processar eventos em tempo real com frameworks como Apache Flink ou Spark Streaming, mantendo o estado causal em memória particionada.

---

## 9. Sprint 2 — Funcionalidade Adicional

### Confirmação de Crédito via Mensageria RabbitMQ
**Exchange:** `iceibank.eventos`  
**Routing Key:** `agencia.<id>.confirmacao`  
**Fila:** `fila-confirmacao-agencia-<id>`

### O que ela faz e justificativa da escolha:
Na arquitetura assíncrona da Sprint 2, a agência de origem publica a ordem de crédito no RabbitMQ e encerra a requisição HTTP com 200, sem saber se o crédito foi realmente aplicado no destino.  
Para fechar o ciclo de observabilidade, foi implementado o mecanismo de **Confirmação de Crédito**:
1. Quando a agência de destino consome a mensagem e aplica o crédito com sucesso na conta, ela avança seu relógio vetorial (`aoEnviar()`) e publica uma mensagem de confirmação na exchange com a routing key `agencia.<agenciaOrigem>.confirmacao`.
2. A agência de origem possui uma fila dedicada de confirmações. Ao consumir esse evento, ela atualiza seu relógio vetorial (`aoReceber()`) e registra no log o evento:
   ```json
   { "tipo": "CONFIRMACAO_CREDITO_RECEBIDA", "detalhes": { "idConta": 1, "valor": 30.0, "agenciaDestino": 1 } }
   ```
3. Se a conta não existir no destino, nenhuma confirmação é enviada e é registrado `CREDITO_REMOTO_FALHOU`.

Essa funcionalidade proporciona visibilidade de ponta a ponta sobre a conclusão efetiva das transferências assíncronas no sistema distribuído.
