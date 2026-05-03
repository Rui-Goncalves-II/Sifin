# CLAUDE.md — Painel de Investimentos (Java + SQLite + Maven)

Aplicação desktop local (JavaFX + SQLite). Sem servidor, sem web, sem autenticação.

## Stack
Java 17 · JavaFX 21 · SQLite (`xerial/sqlite-jdbc`) · Maven · JFreeChart · Jackson · JUnit 5 · Mockito  
Dólar: `GET https://economia.awesomeapi.com.br/json/last/USD-BRL` (gratuita, sem chave)

---

## Banco de Dados (SQLite)

```sql
CREATE TABLE investimentos (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    nome TEXT NOT NULL,           -- nome livre: "Porquinho CDB", "HGLG11 Carteira"
    tipo TEXT NOT NULL,           -- RENDA_FIXA | RENDA_VARIAVEL | DOLAR
    subtipo TEXT,                 -- CDB, LCI, LCA, TESOURO, FII, ACAO, ETF
    indexador TEXT,               -- CDI, IPCA, SELIC, PREFIXADO
    taxa_anual REAL,              -- opcional; se preenchida, tem prioridade sobre taxa calculada
    data_vencimento TEXT,         -- YYYY-MM-DD, nullable
    moeda TEXT DEFAULT 'BRL',     -- BRL | USD
    ativo INTEGER DEFAULT 1,      -- 0 = arquivado (nunca deletar com histórico)
    notas TEXT,
    criado_em TEXT DEFAULT (datetime('now','localtime')),
    atualizado_em TEXT DEFAULT (datetime('now','localtime'))
);

-- Movimentações de Renda Fixa (apenas DEPOSITO e SAQUE — sem RENDIMENTO)
CREATE TABLE movimentacoes (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    investimento_id INTEGER NOT NULL REFERENCES investimentos(id) ON DELETE RESTRICT,
    periodo_mes INTEGER NOT NULL,  -- 1–12
    periodo_ano INTEGER NOT NULL,
    tipo_mov TEXT NOT NULL,        -- DEPOSITO | SAQUE
    valor REAL NOT NULL,           -- sempre positivo
    cotacao_dolar REAL,
    notas TEXT,
    criado_em TEXT DEFAULT (datetime('now','localtime'))
);

-- VTA informado pelo usuário no dia 1º de cada mês (Renda Fixa)
CREATE TABLE vta_mensal (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    investimento_id INTEGER NOT NULL REFERENCES investimentos(id) ON DELETE RESTRICT,
    periodo_mes INTEGER NOT NULL,
    periodo_ano INTEGER NOT NULL,
    vta REAL NOT NULL,
    criado_em TEXT DEFAULT (datetime('now','localtime')),
    atualizado_em TEXT DEFAULT (datetime('now','localtime')),
    UNIQUE (investimento_id, periodo_mes, periodo_ano)
);

-- VAI de cada ano = saldo de 31/12 do ano anterior (gerado automaticamente na virada)
CREATE TABLE vai_anual (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    investimento_id INTEGER NOT NULL REFERENCES investimentos(id) ON DELETE RESTRICT,
    ano INTEGER NOT NULL,
    vai REAL NOT NULL,             -- no 1º ano = primeiro depósito registrado
    criado_em TEXT DEFAULT (datetime('now','localtime')),
    UNIQUE (investimento_id, ano)
);

-- Operações de Renda Variável
CREATE TABLE aportes_rv (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    investimento_id INTEGER NOT NULL REFERENCES investimentos(id) ON DELETE RESTRICT,
    tipo_op TEXT NOT NULL,         -- COMPRA | VENDA | DIVIDENDO
    periodo_mes INTEGER NOT NULL,
    periodo_ano INTEGER NOT NULL,
    quantidade REAL,               -- cotas (null para DIVIDENDO)
    preco_por_cota REAL,           -- null para DIVIDENDO
    valor REAL NOT NULL,           -- qtd × preco, ou valor do dividendo
    notas TEXT,
    criado_em TEXT DEFAULT (datetime('now','localtime'))
);

-- VAC por período (Renda Variável) — API ou digitado pelo usuário
CREATE TABLE vac_mensal (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    investimento_id INTEGER NOT NULL REFERENCES investimentos(id) ON DELETE RESTRICT,
    periodo_mes INTEGER NOT NULL,
    periodo_ano INTEGER NOT NULL,
    vac REAL NOT NULL,
    fonte TEXT DEFAULT 'MANUAL',   -- MANUAL | API
    criado_em TEXT DEFAULT (datetime('now','localtime')),
    atualizado_em TEXT DEFAULT (datetime('now','localtime')),
    UNIQUE (investimento_id, periodo_mes, periodo_ano)
);

-- Cotações do dólar com cache diário
CREATE TABLE cotacoes_dolar (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    data TEXT NOT NULL UNIQUE,     -- YYYY-MM-DD
    valor_compra REAL NOT NULL,
    valor_venda REAL NOT NULL,
    fonte TEXT DEFAULT 'AwesomeAPI',
    criado_em TEXT DEFAULT (datetime('now','localtime'))
);

CREATE TABLE configuracoes (
    chave TEXT PRIMARY KEY,
    valor TEXT NOT NULL
    -- chaves: meta_patrimonio, meta_aporte_mensal, taxa_cdi_referencia,
    --         meses_projecao, sidebar_expandida
);
```

---

## Fórmulas — Renda Fixa

| Variável | Fórmula |
|---|---|
| SAM | Σ(DEPOSITO − SAQUE) do ano corrente até o mês de referência — calculado dinamicamente, nunca armazenado |
| VI | VAI + SAM |
| R (rendimento) | VTA − VI — calculado ao salvar o VTA; pode ser negativo |
| taxa_acumulada | R / VI |
| taxa_mensal (sem taxa explícita) | `(1 + taxa_acumulada)^(1/nMeses) − 1` |
| taxa_mensal (com taxa explícita) | `(1 + taxa_anual/100)^(1/12) − 1` — **sempre prioridade** |
| saldo do período | VTA (quando disponível); senão último VTA + projeção |
| projeção mês n | `saldo × (1+taxa)^n + aporte × [(1+taxa)^n − 1] / taxa` |

**Virada de ano:** `VaiService.virarAno()` na 1ª abertura após 1º/jan.  
`VAI_novoAno = último VTA disponível do ano anterior`

**Projeção — 3 cenários:** multiplicar taxa e aporte por 0,8 / 1,0 / 1,2.

**Alerta VTA:** se mês atual sem VTA → `"⚠ [nome]: VTA de MM/AAAA não informado."`

---

## Fórmulas — Renda Variável

| Variável | Fórmula |
|---|---|
| VTP | Σ(quantidade × preco_por_cota) das COMPRAs |
| QC | Σ cotas compradas − Σ cotas vendidas; nunca negativo |
| VMA | VTP / QC |
| D | Σ valor de todos os DIVIDENDOs |
| PTG | (VAC × QC) + D |
| LEB | (VAC × QC) − VTP; exibir também `(LEB/VTP) × 100` |
| VMR | (VAC_atual × QC) − (VAC_anterior × QC); positivo = valorização |

**VAC:** buscar via Brapi.dev se ticker cadastrado (`GET https://brapi.dev/api/quote/{TICKER}`), senão digitado pelo usuário. Cache em `vac_mensal`.  
**Alerta VAC:** se mês atual sem VAC → `"⚠ [nome]: VAC de MM/AAAA não atualizado."`

---

## Dashboard — Variáveis e Filtro Anual

Padrão: ano vigente. Opções: anos com dados + "Todos os anos" (sentinela `ANO_TODOS = -1`).

| Variável | Fórmula |
|---|---|
| VIARF | Σ depósitos líquidos RF no ano filtrado |
| RARF | Σ rendimentos R de ativos RF no ano filtrado |
| VTARF | VIARF + RARF |
| VIARV | Σ (qtd × preço) das COMPRAs RV no ano filtrado |
| PARV | Σ (VAC × QC) de todos ativos RV — **sempre valor atual** |
| DTA | Σ DIVIDENDOs RV no ano filtrado |
| VTARV | PARV + DTA |
| VTIA | VIARF + VIARV |
| VTRA | RARF + DTA |
| PTA | VTARF + VTARV |
| PCPA | (VTRA / PTA) × 100 → `"Seu patrimônio cresceu X% [esse ano / no histórico total]"` |
| PRAT | (VTRA / VTIA) × 100 → `"Você teve um rendimento total de X% [esse ano / no histórico total]"` |

**Proteções:** PCPA = `—` se PTA = 0; PRAT = `—` se VTIA = 0.  
**Cores:** positivo = verde ▲; negativo = vermelho ▼.  
**Modo "Todos os anos":** VIARF/VIARV/RARF/DTA somam todos os anos; PARV sempre atual.

**Layout do dashboard:**
```
[◀ 2024] [2025] [▶ 2026 ✓] [Todos os anos]      💰 USD: R$5,32/5,33 (+0,05%)

┌─ RENDA FIXA ─┬─ RENDA VARIÁVEL ─┬─ CONSOLIDADO ─┐
│ VIARF        │ VIARV            │ VTIA          │
│ RARF         │ DTA              │ VTRA          │
│ VTARF        │ VTARV (PARV)     │ PTA           │
└──────────────┴──────────────────┴───────────────┘
▲ PCPA: Seu patrimônio cresceu X% esse ano
▲ PRAT: Você teve um rendimento total de X% esse ano

[Pizza: RF×RV×Dólar] [Linha: PTA/mês] [Barras: VTRA/mês]
[Tabela: ativos com saldo atual e % da carteira]
```

---

## Layout Desktop — Sidebar Retrátil

```
┌──────────────┬──────────────────────────────────┐
│  SideBar     │  Área de Conteúdo                │
│  200px / 56px│  (Panel trocado por navegação)   │
└──────────────┴──────────────────────────────────┘
```

| Ícone | Rótulo | Estado | Painel carregado |
|---|---|---|---|
| 🏠 | Dashboard | **padrão/ativo** | `DashboardPanel` |
| 🏦 | Renda Fixa | ativo | `RendaFixaListPanel` |
| 💹 | Renda Variável | ativo | `RendaVariavelListPanel` (FIIs + Ações, subtipo filtrável) |
| 💵 | Dólar | ativo | `DolarListPanel` |
| 🔄 | Transações | ativo | `TransacaoPanel` (histórico unificado) |
| 💸 | Gastos | 🔒 futuro | tooltip "Em breve"; `setDisable(true)` |

**Regras da sidebar:**
- Toggle `☰` anima largura em 150ms (`TranslateTransition`)
- Modo retraído: apenas ícone + tooltip com nome ao hover
- Item selecionado: background com cor de acento
- Estado persistido: `configuracoes` → `sidebar_expandida = 1|0`
- Navegação via `contentArea.getChildren().setAll(panel)` — sem novas janelas
- Dialogs (formulários, VTA, VAC) abrem como `Stage` modal

---

## Estrutura de Pacotes

```
br.investimentos/
├── Main.java
├── db/DatabaseManager.java          -- singleton; PRAGMA foreign_keys=ON; journal_mode=WAL
├── model/
│   ├── Investimento, Movimentacao, AporteRv
│   ├── VtaMensal, VacMensal, VaiAnual, CotacaoDolar
│   └── enums: TipoInvestimento, TipoMovimentacao, TipoOperacaoRv
├── repository/
│   ├── InvestimentoRepository, MovimentacaoRepository
│   ├── AporteRvRepository, VtaMensalRepository
│   ├── VacMensalRepository, VaiAnualRepository, CotacaoRepository
├── service/
│   ├── RendimentoService     -- R = VTA − (VAI + SAM)
│   ├── RendaVariavelService  -- VTP, QC, VMA, PTG, LEB, VMR, D
│   ├── TaxaService           -- conversão taxa anual ↔ mensal
│   ├── SaldoService          -- saldo por período
│   ├── ProjecaoService       -- 3 cenários
│   ├── VaiService            -- virada de ano
│   ├── AlertaService         -- VTA e VAC pendentes
│   ├── ConsolidacaoService   -- todas as variáveis do dashboard por ano
│   └── CotacaoService        -- dólar via API + cache; VAC via Brapi.dev
└── ui/
    ├── MainWindow.java
    ├── component/SideBar.java
    ├── dashboard/DashboardPanel.java
    ├── rendafixa/  RendaFixaListPanel, RendaFixaFormDialog, RendaFixaDetalhePanel, VtaFormDialog
    ├── rendavariavel/ RendaVariavelListPanel, RendaVariavelFormDialog, RendaVariavelDetalhePanel, VacFormDialog
    ├── dolar/      DolarListPanel, DolarFormDialog, DolarDetalhePanel
    ├── transacao/  TransacaoPanel
    └── projecao/   ProjecaoPanel
```

---

## Fases de Implementação

```
F1 – Fundação:    DatabaseManager · schema.sql · Models · Repositories · RendimentoService (testes)
F2 – CRUD UI:     MainWindow + SideBar · formulários RF/RV/Dólar · VtaFormDialog · VacFormDialog
F3 – Dashboard:   ConsolidacaoService · filtro anual · cotação dólar · gráficos
F4 – Projeção:    ProjecaoService · 3 cenários · calculadora de meta
F5 – Refinamentos: configurações · backup/restore · exportação CSV · alertas de vencimento RF
```

---

## Incrementos Futuros

- **Cotações BR:** Brapi.dev para ações/FIIs/ETFs; API B3 para Tesouro Direto; CoinGecko para cripto
- **Análises:** comparativo CDI, rentabilidade acumulada, rebalanceamento por alocação alvo
- **Usabilidade:** importação CSV (extrato corretora), exportação PDF (Apache PDFBox), tema escuro/claro
- **Segurança:** backup automático diário, exportação JSON, SQLCipher
- **Gastos:** módulo futuro com lógica própria (placeholder na sidebar já implementado)

---

## Regras Permanentes para o Claude Code

**Build:** Maven exclusivamente.  
**Arquitetura:** Repository → Service → UI; UI nunca acessa banco.  
**Período:** dois `INTEGER` separados (`periodo_mes`, `periodo_ano`); nunca `String`.  
**Valores:** `double` para cálculo; `BigDecimal` + `NumberFormat` para exibição em BRL.  
**Exclusão:** nunca deletar com histórico — arquivar (`ativo = 0`).  
**Banco:** `data/investimentos.db` relativo ao diretório do JAR.

**RF:** `TipoMovimentacao` = DEPOSITO | SAQUE. SAM nunca armazenado. VTA = único valor digitado. VAI imutável no ano. Taxa explícita tem prioridade absoluta.  
**RV:** `TipoOperacaoRv` = COMPRA | VENDA | DIVIDENDO em `aportes_rv`. VTP conta só COMPRAs. QC nunca negativo. VAC fonte API suprime alerta.  
**Dashboard:** `ConsolidacaoService.calcular(int ano)` — `ANO_TODOS = -1`. PARV sempre atual. Mensagens adaptam "esse ano" / "no histórico total".  
**Dólar:** cache diário em `cotacoes_dolar`; refresh a cada 15 min via `ScheduledExecutorService`.  
**Virada de ano:** `VaiService.virarAno()` na 1ª abertura após 1º/jan.  
**Sidebar:** Dashboard é tela padrão. Item Gastos: `setDisable(true)`, tooltip "Em breve".
