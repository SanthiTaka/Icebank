# 🏦 ICEIBank

Sistema bancário distribuído desenvolvido em **Java + Spring Boot** para a disciplina de **Laboratório de Desenvolvimento de Aplicações Móveis e Distribuídas**.

O projeto simula um banco distribuído composto por **três agências independentes**, cada uma executando uma instância da mesma aplicação. As contas são particionadas entre as agências e as operações distribuídas utilizam **Relógio Lógico de Lamport** para ordenação dos eventos.

---

## 📌 Sobre o projeto

O ICEIBank foi desenvolvido para aplicar conceitos de:

* Arquitetura REST;
* Spring Boot;
* Sistemas distribuídos;
* Particionamento de dados;
* Relógio lógico de Lamport;
* Comunicação entre processos;
* Transferências locais e entre agências;
* Registro e consolidação de eventos;
* Autenticação utilizando JWT;
* Desenvolvimento de frontend;
* Integração entre frontend e API REST.

A aplicação utiliza o mesmo código-fonte para executar três agências diferentes, identificadas pelas portas:

| Agência   |  Porta | Contas               |
| --------- | -----: | -------------------- |
| Agência 0 | `4093` | IDs `0, 3, 6, 9...`  |
| Agência 1 | `4094` | IDs `1, 4, 7, 10...` |
| Agência 2 | `4095` | IDs `2, 5, 8, 11...` |

A agência responsável por uma conta é determinada por:

```text
agencia = idConta % 3
```

---

## 🏗️ Arquitetura

```text
                    ┌─────────────────────┐
                    │      Frontend       │
                    │    HTML/CSS/JS      │
                    │    Live Server      │
                    └──────────┬──────────┘
                               │
              ┌────────────────┼────────────────┐
              │                │                │
              ▼                ▼                ▼
       ┌─────────────┐  ┌─────────────┐  ┌─────────────┐
       │  Agência 0  │  │  Agência 1  │  │  Agência 2  │
       │    :4093    │  │    :4094    │  │    :4095    │
       └──────┬──────┘  └──────┬──────┘  └──────┬──────┘
              │                │                │
              └────────────────┼────────────────┘
                               │
                     Comunicação REST
```

Cada agência possui:

* seu próprio conjunto de contas;
* seu próprio relógio de Lamport;
* seu próprio arquivo de eventos;
* sua própria porta;
* capacidade de realizar operações locais;
* capacidade de realizar operações entre agências.

---

## 🛠️ Tecnologias utilizadas

### Backend

* **Java 22**
* **Spring Boot 3.4.5**
* Spring Web
* Spring Security
* JWT
* Maven

### Frontend

* HTML5
* CSS3
* JavaScript
* Fetch API
* Live Server

### Conceitos

* API REST
* MVC
* Sistemas distribuídos
* Relógio lógico de Lamport
* Comunicação HTTP entre processos
* Autenticação stateless com JWT
* Particionamento de contas

---

## 📂 Estrutura do projeto

```text
Icebank/
│
├── agencia/
│   ├── src/
│   │   └── main/
│   │       └── java/
│   │           └── com/
│   │               └── iceibank/
│   │                   └── agencia/
│   │                       ├── config/
│   │                       ├── controller/
│   │                       ├── model/
│   │                       └── services/
│   │
│   ├── data/
│   │   └── eventos-agencia-*.jsonl
│   │
│   ├── mesclar-logs.js
│   ├── pom.xml
│   └── ...
│
├── frontend/
│   ├── index.html
│   ├── style.css
│   └── script.js
│
├── evidencias/
│   └── sprint1/
│
├── RESPOSTAS.md
├── README.md
└── .gitignore
```

Os arquivos de log são gerados durante a execução e não devem ser versionados no Git.

---

# 🚀 Como executar

## Pré-requisitos

Instale:

* Java JDK 22 ou compatível;
* Maven;
* Git;
* navegador web;
* VS Code (recomendado).

Verifique as instalações:

```bash
java -version
mvn -version
git --version
```

---

## 1. Clonar o projeto

```bash
git clone <URL_DO_REPOSITORIO>
```

Entre no projeto:

```bash
cd Icebank
```

---

## 2. Compilar o backend

Entre na pasta da agência:

```bash
cd agencia
```

Execute:

```bash
mvn clean package
```

---

## 3. Executar as três agências

O mesmo código é executado três vezes, utilizando portas diferentes.

### Agência 0

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=4093"
```

### Agência 1

Em outro terminal:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=4094"
```

### Agência 2

Em outro terminal:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=4095"
```

As três instâncias devem permanecer executando simultaneamente.

---

# 🔐 Autenticação

A API utiliza **JWT (JSON Web Token)** para autenticação.

## Login

Endpoint:

```http
POST /auth/login
```

Exemplo:

```json
{
  "usuario": "admin",
  "senha": "123456"
}
```

Exemplo de requisição:

```text
http://localhost:4093/auth/login
```

A API retorna um token JWT.

Nas demais requisições protegidas, o token deve ser enviado no cabeçalho:

```http
Authorization: Bearer <TOKEN>
```

### Credenciais acadêmicas

```text
Usuário: admin
Senha: 123456
```

> As credenciais são utilizadas exclusivamente para fins acadêmicos no projeto.

---

# 💳 Operações disponíveis

## Contas

### Criar conta

```http
POST /contas
```

Exemplo:

```json
{
  "titular": "João Silva",
  "saldo": 1000
}
```

### Listar contas

```http
GET /contas
```

### Consultar conta

```http
GET /contas/{id}
```

### Atualizar conta

```http
PUT /contas/{id}
```

### Excluir conta

```http
DELETE /contas/{id}
```

---

## 💰 Depósito

```http
POST /contas/{id}/deposito
```

Exemplo:

```json
{
  "valor": 500
}
```

---

## 💸 Saque

```http
POST /contas/{id}/saque
```

Exemplo:

```json
{
  "valor": 200
}
```

A operação não permite:

* valores menores ou iguais a zero;
* saque superior ao saldo disponível.

---

# 🔄 Transferências

O sistema suporta dois tipos de transferência.

## Transferência local

Quando origem e destino pertencem à mesma agência, a operação é realizada localmente.

Exemplo:

```text
Conta 0 → Conta 3
```

Ambas pertencem à Agência 0:

```text
0 % 3 = 0
3 % 3 = 0
```

---

## Transferência entre agências

Quando as contas pertencem a agências diferentes, as instâncias se comunicam através de HTTP.

Exemplo:

```text
Agência 0
Conta 0
   │
   │ HTTP
   ▼
Agência 1
Conta 1
```

A transferência utiliza eventos distintos para representar o envio e o recebimento:

```text
TRANSFERENCIA_INTERAGENCIA_ENVIO
TRANSFERENCIA_INTERAGENCIA_RECEBIMENTO
```

O timestamp de Lamport é enviado junto à comunicação para que a agência receptora possa atualizar seu relógio lógico.

---

# ⏱️ Relógio de Lamport

Cada agência possui um relógio lógico independente.

Os eventos locais incrementam o relógio:

```text
L = L + 1
```

No envio de uma mensagem:

```text
L = L + 1
```

Ao receber uma mensagem com timestamp `T`:

```text
L = max(L, T) + 1
```

Isso permite estabelecer uma ordenação lógica dos eventos distribuídos.

Exemplo:

```text
Agência 0                         Agência 1

L = 5
   │
   │ envio (L = 6)
   ├────────────────────────────►
   │                              L = max(2,6)+1
   │                              L = 7
```

O relógio de Lamport não representa necessariamente o horário real. Ele representa a **ordenação causal dos eventos**.

---

# 📝 Registro de eventos

As operações realizadas pelas agências são registradas em arquivos `.jsonl`.

Exemplo:

```json
{
  "agencia": "agencia-0",
  "tipo": "CRIAR_CONTA",
  "timestampLamport": 1,
  "horaParede": "2026-09-07T..."
}
```

Cada agência possui seu próprio arquivo de eventos.

```text
data/
├── eventos-agencia-0.jsonl
├── eventos-agencia-1.jsonl
└── eventos-agencia-2.jsonl
```

---

# 🔀 Mesclagem dos logs

O projeto possui o script:

```text
agencia/mesclar-logs.js
```

Ele reúne os eventos registrados pelas três agências e produz uma linha do tempo unificada, ordenada pelo timestamp lógico de Lamport.

Para executar:

```bash
cd agencia
node mesclar-logs.js
```

A saída permite analisar a ordem lógica dos eventos e identificar situações de concorrência entre as agências.

---

# ❤️ Funcionalidade adicional

Além das funcionalidades obrigatórias, o projeto possui um **health-check por agência**.

Endpoint:

```http
GET /status
```

Exemplo:

```json
{
  "agencia": "agencia-0",
  "porta": 4093,
  "status": "ONLINE",
  "lamport": 18,
  "quantidadeContas": 2
}
```

A funcionalidade permite consultar:

* agência responsável pela instância;
* porta utilizada;
* status da aplicação;
* valor atual do relógio de Lamport;
* quantidade de contas daquela agência.

Como existem três instâncias independentes, esse endpoint facilita a observação do estado de cada agência durante os testes distribuídos.

---

# ⚠️ Falha conhecida — Sprint 1

O projeto mantém uma limitação definida para esta etapa.

Em uma transferência entre agências, a conta de origem é debitada antes da comunicação com a agência de destino.

Caso a agência de destino esteja indisponível após o débito, a operação retorna erro e o saldo da origem não é automaticamente revertido.

Fluxo:

```text
Agência 0
   │
   │ 1. Debita origem
   │
   │ 2. Tenta comunicar
   ▼
Agência 1
   X
Indisponível
```

Resultado:

```text
TRANSFERENCIA_FALHOU
```

Essa inconsistência é uma limitação conhecida do Sprint 1 e será tratada posteriormente com mecanismos de transação distribuída, como **Two-Phase Commit (2PC)** ou **Saga**.

---

# 🌐 Frontend

O projeto possui uma interface web desenvolvida com HTML, CSS e JavaScript.

A interface permite executar:

* login;
* consulta de conta;
* depósito;
* saque;
* transferência local;
* transferência entre agências;
* tratamento de erros;
* logout.

O token JWT é armazenado no `localStorage` do navegador e enviado automaticamente nas requisições protegidas.

---

## Executando o frontend

Abra a pasta:

```text
frontend/
```

e execute o projeto utilizando o **Live Server** do VS Code.

> Recomenda-se abrir o Live Server diretamente na pasta `frontend`, evitando que alterações nos arquivos de log do backend provoquem recarregamento automático da página.

A interface estará disponível normalmente em:

```text
http://127.0.0.1:5500
```

---

# 🧪 Testes e evidências

As evidências da execução do Sprint 1 estão organizadas em:

```text
evidencias/sprint1/
```

Entre os cenários documentados estão:

* login;
* autenticação sem token;
* autenticação com token;
* token expirado;
* consulta de conta;
* depósito;
* saque;
* transferência local;
* transferência entre agências;
* falha conhecida;
* linha do tempo dos eventos;
* funcionalidade adicional;
* operações realizadas pelo frontend.

---

# 📚 Documentação acadêmica

As respostas e justificativas referentes às decisões de implementação estão disponíveis em:

```text
RESPOSTAS.md
```

O documento contém as respostas relacionadas a:

* Relógio de Lamport;
* transferências distribuídas;
* consistência;
* falha conhecida;
* consolidação dos logs;
* JWT;
* autenticação e autorização;
* arquitetura MVC;
* funcionamento do frontend;
* funcionalidade adicional.

---

# 👨‍💻 Projeto acadêmico

**Projeto:** ICEIBank — Sprint 1
**Disciplina:** Laboratório de Desenvolvimento de Aplicações Móveis e Distribuídas
**Curso:** Engenharia de Software
**Instituição:** PUC Minas

---

## 📄 Licença

Projeto desenvolvido para fins acadêmicos.
