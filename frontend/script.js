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

// CRIAR CONTA
const criarContaButton = document.getElementById("criar-conta-button");
const criarContaResultado = document.getElementById("criar-conta-resultado");

criarContaButton.addEventListener("click", async () => {
  const titular = document.getElementById("criar-titular").value.trim();
  const saldo = Number(document.getElementById("criar-saldo").value);

  if (!titular) {
    mostrarMensagem(criarContaResultado, "Informe o nome do titular.");
    return;
  }

  if (isNaN(saldo) || saldo < 0) {
    mostrarMensagem(criarContaResultado, "Informe um saldo inicial válido.");
    return;
  }

  try {
    const conta = await fazerRequisicao(`${obterUrlBase()}/contas`, {
      method: "POST",
      body: JSON.stringify({
        titular: titular,
        saldo: saldo,
      }),
    });

    criarContaResultado.innerHTML = `
      <strong>Conta criada com sucesso!</strong><br>
      ID: ${conta.id}<br>
      Titular: ${conta.titular}<br>
      Saldo inicial: R$ ${Number(conta.saldo).toFixed(2)}
    `;
    criarContaResultado.style.color = "green";
    document.getElementById("criar-titular").value = "";
    document.getElementById("criar-saldo").value = "";
  } catch (erro) {
    mostrarMensagem(criarContaResultado, erro.message);
  }
});

// CONSULTAR CONTA
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

// EXTRATO (Funcionalidade Adicional)
const extratoButton = document.getElementById("extrato-button");
const extratoResultado = document.getElementById("extrato-resultado");

extratoButton.addEventListener("click", async () => {
  const id = document.getElementById("extrato-id").value;

  if (!id) {
    mostrarMensagem(extratoResultado, "Informe o ID da conta.");
    return;
  }

  try {
    const dados = await fazerRequisicao(`${obterUrlBase()}/contas/${id}/extrato`, {
      method: "GET",
    });

    let html = `
      <strong>Extrato da Conta ${dados.idConta} (${dados.titular})</strong><br>
      Saldo Atual: R$ ${Number(dados.saldoAtual).toFixed(2)} | Relógio Lamport: ${dados.timestampLamport}<br>
      <table style="width: 100%; border-collapse: collapse; margin-top: 8px;">
        <thead>
          <tr style="background-color: #f0f0f0; text-align: left;">
            <th style="padding: 4px; border: 1px solid #ccc;">Lamport</th>
            <th style="padding: 4px; border: 1px solid #ccc;">Tipo</th>
            <th style="padding: 4px; border: 1px solid #ccc;">Valor</th>
            <th style="padding: 4px; border: 1px solid #ccc;">Saldo Após</th>
            <th style="padding: 4px; border: 1px solid #ccc;">Data/Hora</th>
          </tr>
        </thead>
        <tbody>
    `;

    if (dados.transacoes && dados.transacoes.length > 0) {
      for (const t of dados.transacoes) {
        html += `
          <tr>
            <td style="padding: 4px; border: 1px solid #ccc;">${t.timestampLamport}</td>
            <td style="padding: 4px; border: 1px solid #ccc;">${t.tipo}</td>
            <td style="padding: 4px; border: 1px solid #ccc;">R$ ${Number(t.valor).toFixed(2)}</td>
            <td style="padding: 4px; border: 1px solid #ccc;">R$ ${Number(t.saldoApos).toFixed(2)}</td>
            <td style="padding: 4px; border: 1px solid #ccc;">${new Date(t.dataHora).toLocaleTimeString()}</td>
          </tr>
        `;
      }
    } else {
      html += `<tr><td colspan="5" style="padding: 4px; border: 1px solid #ccc; text-align: center;">Nenhuma transação registrada.</td></tr>`;
    }

    html += `</tbody></table>`;
    extratoResultado.innerHTML = html;
    extratoResultado.style.color = "black";
  } catch (erro) {
    mostrarMensagem(extratoResultado, erro.message);
  }
});

// DEPÓSITO
const depositoButton = document.getElementById("deposito-button");
const depositoResultado = document.getElementById("deposito-resultado");

depositoButton.addEventListener("click", async (event) => {
  event.preventDefault();

  const id = document.getElementById("deposito-id").value;
  const valor = Number(document.getElementById("deposito-valor").value);

  if (!id) {
    mostrarMensagem(depositoResultado, "Informe o ID da conta.");
    return;
  }

  if (!valor || valor <= 0) {
    mostrarMensagem(depositoResultado, "Informe um valor de depósito válido.");
    return;
  }

  try {
    const conta = await fazerRequisicao(
      `${obterUrlBase()}/contas/${id}/deposito`,
      {
        method: "POST",
        body: JSON.stringify({ valor }),
      },
    );

    depositoResultado.innerHTML = `
      <strong>Depósito realizado com sucesso!</strong><br>
      Conta: ${conta.id}<br>
      Novo saldo: R$ ${Number(conta.saldo).toFixed(2)}
    `;
    depositoResultado.style.color = "green";
    document.getElementById("deposito-valor").value = "";
  } catch (erro) {
    mostrarMensagem(depositoResultado, erro.message);
  }
});

// SAQUE
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
        body: JSON.stringify({ valor }),
      },
    );

    saqueResultado.innerHTML = `
      <strong>Saque realizado com sucesso!</strong><br>
      Conta: ${conta.id}<br>
      Novo saldo: R$ ${Number(conta.saldo).toFixed(2)}
    `;
    saqueResultado.style.color = "green";
    document.getElementById("saque-valor").value = "";
  } catch (erro) {
    mostrarMensagem(saqueResultado, erro.message);
  }
});

// TRANSFERÊNCIA
const transferenciaButton = document.getElementById("transferencia-button");
const transferenciaResultado = document.getElementById("transferencia-resultado");

transferenciaButton.addEventListener("click", async () => {
  const idOrigem = Number(document.getElementById("transferencia-origem").value);
  const idDestino = Number(document.getElementById("transferencia-destino").value);
  const valor = Number(document.getElementById("transferencia-valor").value);

  if (Number.isNaN(idOrigem) || Number.isNaN(idDestino)) {
    mostrarMensagem(transferenciaResultado, "Informe as contas de origem e destino.");
    return;
  }

  if (idOrigem === idDestino) {
    mostrarMensagem(transferenciaResultado, "A conta de origem e destino devem ser diferentes.");
    return;
  }

  if (!valor || valor <= 0) {
    mostrarMensagem(transferenciaResultado, "Informe um valor de transferência válido.");
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
    document.getElementById("transferencia-valor").value = "";
  } catch (erro) {
    mostrarMensagem(transferenciaResultado, erro.message);
  }
});

if (obterToken()) {
  mostrarBanco();
}
