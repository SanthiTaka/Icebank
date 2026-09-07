const loginSection = document.getElementById("login-section");
const bankSection = document.getElementById("bank-section");

const loginForm = document.getElementById("login-form");
const loginMensagem = document.getElementById("login-mensagem");

const logoutButton = document.getElementById("logout-button");
const agenciaSelect = document.getElementById("agencia");

const TOKEN_KEY = "iceibank_token";

function obterToken() {
  return localStorage.getItem(TOKEN_KEY);
}

function obterUrlBase() {
  return `http://localhost:${agenciaSelect.value}`;
}

function mostrarBanco() {
  loginSection.hidden = true;
  bankSection.hidden = false;
}

function mostrarLogin() {
  loginSection.hidden = false;
  bankSection.hidden = true;
}

function mostrarMensagem(elemento, mensagem, sucesso = false) {
  elemento.textContent = mensagem;
  elemento.style.color = sucesso ? "green" : "red";
}

async function fazerRequisicao(url, opcoes = {}) {
  const token = obterToken();

  const headers = {
    ...(opcoes.headers || {}),
  };

  if (token) {
    headers["Authorization"] = `Bearer ${token}`;
  }

  if (opcoes.body) {
    headers["Content-Type"] = "application/json";
  }

  const resposta = await fetch(url, {
    ...opcoes,
    headers,
  });

  let dados = null;

  try {
    dados = await resposta.json();
  } catch {
    dados = null;
  }

  if (resposta.status === 401) {
    localStorage.removeItem(TOKEN_KEY);
    mostrarLogin();

    mostrarMensagem(
      loginMensagem,
      "Sessão expirada ou token inválido. Faça login novamente.",
    );

    throw new Error("Sessão expirada ou token inválido.");
  }

  if (!resposta.ok) {
    const mensagem =
      dados?.erro ||
      dados?.message ||
      `Erro na operação. Código HTTP: ${resposta.status}`;

    throw new Error(mensagem);
  }

  return dados;
}

loginForm.addEventListener("submit", async (event) => {
  event.preventDefault();

  const usuario = document.getElementById("usuario").value;
  const senha = document.getElementById("senha").value;

  try {
    const resposta = await fetch(`${obterUrlBase()}/auth/login`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        usuario: usuario,
        senha: senha,
      }),
    });

    let dados = null;

    try {
      dados = await resposta.json();
    } catch {
      dados = null;
    }

    if (!resposta.ok) {
      mostrarMensagem(
        loginMensagem,
        dados?.erro || "Usuário ou senha inválidos.",
      );

      return;
    }

    localStorage.setItem(TOKEN_KEY, dados.token);

    mostrarMensagem(loginMensagem, "Login realizado com sucesso.", true);

    mostrarBanco();
  } catch (erro) {
    mostrarMensagem(loginMensagem, "Não foi possível conectar à agência.");

    console.error(erro);
  }
});

logoutButton.addEventListener("click", () => {
  localStorage.removeItem(TOKEN_KEY);
  mostrarLogin();
  loginMensagem.textContent = "";
});

const consultaButton = document.getElementById("consulta-button");

const contaResultado = document.getElementById("conta-resultado");

consultaButton.addEventListener("click", async () => {
  const id = document.getElementById("consulta-id").value;

  if (!id) {
    mostrarMensagem(contaResultado, "Informe o ID da conta.");

    return;
  }

  try {
    const conta = await fazerRequisicao(`${obterUrlBase()}/contas/${id}`, {
      method: "GET",
    });

    contaResultado.innerHTML = `
            <strong>Conta encontrada</strong><br>
            ID: ${conta.id}<br>
            Titular: ${conta.titular}<br>
            Saldo: R$ ${Number(conta.saldo).toFixed(2)}
        `;

    contaResultado.style.color = "green";
  } catch (erro) {
    mostrarMensagem(contaResultado, erro.message);
  }
});

const depositoButton = document.getElementById("deposito-button");
const depositoResultado = document.getElementById("deposito-resultado");

depositoButton.addEventListener("click", async (event) => {
  event.preventDefault();
  event.stopImmediatePropagation();

  const id = document.getElementById("deposito-id").value;
  const valor = Number(document.getElementById("deposito-valor").value);

  depositoResultado.textContent = "";

  if (!id) {
    depositoResultado.textContent = "Informe o ID da conta.";
    depositoResultado.style.color = "red";
    return;
  }

  if (!valor || valor <= 0) {
    depositoResultado.textContent = "Informe um valor de depósito válido.";
    depositoResultado.style.color = "red";
    return;
  }

  depositoResultado.textContent = "Processando...";
  depositoResultado.style.color = "black";

  try {
    const conta = await fazerRequisicao(
      `${obterUrlBase()}/contas/${id}/deposito`,
      {
        method: "POST",
        body: JSON.stringify({
          valor: valor,
        }),
      },
    );

    console.log("RESPOSTA DO DEPÓSITO:", conta);

    depositoResultado.textContent =
      `Depósito realizado com sucesso! ` +
      `Conta: ${conta.id} | ` +
      `Novo saldo: R$ ${Number(conta.saldo).toFixed(2)}`;

    depositoResultado.style.color = "green";
  } catch (erro) {
    console.error("ERRO NO DEPÓSITO:", erro);

    depositoResultado.textContent = erro.message;

    depositoResultado.style.color = "red";
  }
});

const saqueButton = document.getElementById("saque-button");

const saqueResultado = document.getElementById("saque-resultado");

saqueButton.addEventListener("click", async () => {
  const id = document.getElementById("saque-id").value;

  const valor = Number(document.getElementById("saque-valor").value);

  if (!id) {
    mostrarMensagem(saqueResultado, "Informe o ID da conta.");

    return;
  }

  if (!valor || valor <= 0) {
    mostrarMensagem(saqueResultado, "Informe um valor de saque válido.");

    return;
  }

  try {
    const conta = await fazerRequisicao(
      `${obterUrlBase()}/contas/${id}/saque`,
      {
        method: "POST",
        body: JSON.stringify({
          valor: valor,
        }),
      },
    );

    saqueResultado.innerHTML = `
            <strong>Saque realizado com sucesso!</strong><br>
            Conta: ${conta.id}<br>
            Novo saldo: R$ ${Number(conta.saldo).toFixed(2)}
        `;

    saqueResultado.style.color = "green";

    document.getElementById("saque-id").value = "";
    document.getElementById("saque-valor").value = "";
  } catch (erro) {
    if (erro.message.includes("Código HTTP: 400")) {
      mostrarMensagem(saqueResultado, "Saldo insuficiente ou valor inválido.");
    } else {
      mostrarMensagem(saqueResultado, erro.message);
    }
  }
});

const transferenciaButton = document.getElementById("transferencia-button");

const transferenciaResultado = document.getElementById(
  "transferencia-resultado",
);

transferenciaButton.addEventListener("click", async () => {
  const idOrigem = Number(
    document.getElementById("transferencia-origem").value,
  );

  const idDestino = Number(
    document.getElementById("transferencia-destino").value,
  );

  const valor = Number(document.getElementById("transferencia-valor").value);

  if (Number.isNaN(idOrigem) || Number.isNaN(idDestino)) {
    mostrarMensagem(
      transferenciaResultado,
      "Informe as contas de origem e destino.",
    );

    return;
  }

  if (idOrigem === idDestino) {
    mostrarMensagem(
      transferenciaResultado,
      "A conta de origem e destino devem ser diferentes.",
    );

    return;
  }

  if (!valor || valor <= 0) {
    mostrarMensagem(
      transferenciaResultado,
      "Informe um valor de transferência válido.",
    );

    return;
  }

  try {
    const resultado = await fazerRequisicao(
      `${obterUrlBase()}/transferencias`,
      {
        method: "POST",
        body: JSON.stringify({
          idOrigem: idOrigem,
          idDestino: idDestino,
          valor: valor,
        }),
      },
    );

    transferenciaResultado.innerHTML = `
            <strong>${resultado.mensagem}</strong><br>
            Origem: ${idOrigem}<br>
            Destino: ${idDestino}<br>
            Valor: R$ ${valor.toFixed(2)}
        `;

    transferenciaResultado.style.color = "green";

    document.getElementById("transferencia-origem").value = "";

    document.getElementById("transferencia-destino").value = "";

    document.getElementById("transferencia-valor").value = "";
  } catch (erro) {
    mostrarMensagem(transferenciaResultado, erro.message);
  }
});

if (obterToken()) {
  mostrarBanco();
}

window.addEventListener("beforeunload", () => {
  console.log("A PÁGINA ESTÁ SENDO RECARREGADA");
});

window.addEventListener("beforeunload", () => {
  console.log("A PÁGINA ESTÁ SENDO RECARREGADA");
});
