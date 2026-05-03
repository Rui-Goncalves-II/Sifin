# CLAUDE.md — Painel de Investimentos (Java + SQLite + Maven)

## Visão Geral do Projeto

Aplicação desktop Java com interface gráfica (JavaFX) e banco de dados SQLite local.
O usuário cadastra seus investimentos, registra movimentações por período e acompanha
a evolução do patrimônio com projeções baseadas em médias históricas.
**Execução exclusivamente local — sem servidor, sem web, sem autenticação.**

---

## Stack Tecnológica

| Camada        | Tecnologia                                                         |
|---------------|--------------------------------------------------------------------|
| Linguagem     | Java 17+ (LTS)                                                     |
| Interface     | JavaFX 21                                                          |
| Banco         | SQLite via `xerial/sqlite-jdbc`                                    |
| Build         | **Maven**                                                          |
| Gráficos      | JFreeChart                                                         |
| JSON          | Jackson Databind                                                   |
| Cotação Dólar | AwesomeAPI — `https://economia.awesomeapi.com.br/json/last/USD-BRL`|
| Testes        | JUnit 5 + Mockito                                                  |

---

## Regras de Negócio Fundamentais

### Conceito 1 — Investimento (o "produto")
O usuário cria um **investimento** que representa um produto financeiro nomeado por ele.
O nome é livre e descritivo, por exemplo:

- `Porquinho CDB` → um CDB em algum banco
- `Tesouro da Aposentadoria` → Tesouro IPCA+
- `Dólar Reserva` → posição em dólar
- `HGLG11 Carteira` → cotas de FII

Cada investimento tem um **tipo** (Renda Fixa / Renda Variável / Dólar) e
metadados opcionais como indexador, taxa e vencimento.

### Conceito 2 — Movimentação por Período
Toda entrada ou saída de dinheiro é registrada como uma **movimentação** vinculada
a um **período (mês/ano)**, não a uma data exata. Isso reflete a realidade de
quem acompanha a carteira mensalmente.

Exemplo real:
```
Investimento: Porquinho CDB — ano 2026
  VAI (aporte inicial 2026) = R$ 5.200,00  ← saldo acumulado de 31/12/2025

  Período 01/2026: DEPÓSITO R$ 500,00  | VTA informado: R$ 5.750,00
  Período 02/2026: DEPÓSITO R$ 500,00  | VTA informado: R$ 6.310,00
  Período 02/2026: SAQUE    R$ 200,00  |
```

O **saldo de um período** e o **rendimento** são sempre calculados pelo sistema.
O **VTA** (Valor Total do Ativo) é o único dado de valor que o usuário informa diretamente,
sempre no dia 1º de cada mês.

### Conceito 3 — Ciclo Anual e Valor de Aporte Inicial (VAI)

O sistema opera em **ciclos anuais**:

- **VAI (Valor de Aporte Inicial)** = saldo acumulado do ativo em 31/12 do ano anterior.
  É o "capital base" do novo ano. Nunca muda durante o ano vigente.
- **SAM (Soma dos Aportes do Mês)** = soma de todos os depósitos líquidos (depósitos - saques)
  realizados **somente no ano corrente**, excluindo o VAI.
- **VI (Valor Investido)** = VAI + SAM. Representa todo o capital efetivamente colocado no ativo.

```
Virada de ano (automática, em 1º de janeiro):
  VAI_2026 = saldoFinal_2025
  SAM_2026 = 0   ← zera para o novo ciclo
```

No primeiro ano do investimento (sem histórico anterior):
```
  VAI = primeiro depósito registrado
  SAM = depósitos subsequentes do mesmo ano
```

### Conceito 4 — Cálculo de Rendimento (R) — exclusivo Renda Fixa

O rendimento **não é digitado pelo usuário**. É calculado automaticamente a partir do
**VTA informado pelo usuário no dia 1º de cada mês**:

```
R = VTA − (VAI + SAM)
  = VTA − VI
```

Onde:
- **VTA** = Valor Total do Ativo informado pelo usuário no período
- **VAI** = Valor de Aporte Inicial do ano vigente (fixo durante o ano)
- **SAM** = Soma dos aportes (depósitos − saques) do ano corrente até o período
- **R**   = Rendimento calculado do período (pode ser negativo em caso de perda)

**Exemplo:**
```
VAI = R$ 5.200,00  (saldo de 2025)
SAM em 01/2026 = R$ 500,00  (um depósito)
VTA informado  = R$ 5.750,00

R = 5.750 − (5.200 + 500) = R$ 50,00 de rendimento em 01/2026
```

**Alerta de VTA desatualizado:**
Se o dia atual for ≥ 1 e o VTA do mês corrente ainda não foi informado, exibir alerta
visual em destaque na tela do ativo e no dashboard: *"⚠ [Nome do ativo]: VTA de MM/AAAA
não informado. Rendimento do período não calculado."*

### Conceito 5 — Taxa de Rendimento Implícita

#### 5a — Com taxa explícita (informada pelo usuário)
Se o usuário cadastrou uma `taxa_anual` no investimento, ela é usada diretamente.
O sistema converte internamente para taxa mensal equivalente:
```
taxa_mensal = (1 + taxa_anual / 100) ^ (1/12) − 1
```
A taxa explícita **sempre tem prioridade** sobre a taxa calculada.

#### 5b — Sem taxa explícita (calculada pelo sistema)
A taxa acumulada do período é obtida a partir dos rendimentos calculados:
```
taxa_acumulada (%) = ((VTA − VI) / VI) × 100
                   = (R / VI) × 100
```
Como essa taxa é acumulada em `n` meses (meses decorridos desde o início do ano ou
desde o primeiro depósito), o sistema converte automaticamente para taxa mensal equivalente:
```
taxa_mensal = (1 + taxa_acumulada / 100) ^ (1 / n_meses) − 1
```
Essa taxa mensal é usada nas projeções e nos comparativos.

### Conceito 6 — Projeção por Médias Históricas
A projeção usa o **comportamento real observado** do investimento:

```
médiaAporteLíquidoMensal = média de (depósitos − saques) por período,
                            ignorando meses sem movimentação de capital

taxaMensalEfetiva        = taxa_mensal explícita  (se informada pelo usuário)
                           OU taxa_mensal calculada via taxa_acumulada histórica
```

Fórmula de projeção (iterativa, mês a mês):
```
saldo(n) = saldoAtual × (1 + taxaMensal)^n
           + médiaAporteLíquido × [(1 + taxaMensal)^n − 1] / taxaMensal
```
Onde `n` = número de meses projetados.

A projeção é exibida em **3 cenários**:
- **Conservador:** 80% da taxa e do aporte médio
- **Base:** valores exatos observados
- **Otimista:** 120% da taxa e do aporte médio

---

## Modelo de Dados (SQLite)

### Tabela: `investimentos`
```sql
CREATE TABLE IF NOT EXISTS investimentos (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    nome            TEXT NOT NULL,             -- "Porquinho CDB", "Dólar Reserva"
    tipo            TEXT NOT NULL,             -- RENDA_FIXA | RENDA_VARIAVEL | DOLAR
    subtipo         TEXT,                      -- CDB, LCI, LCA, TESOURO, FII, ACAO, ETF
    indexador       TEXT,                      -- CDI, IPCA, SELIC, PREFIXADO (opcional)
    taxa_anual      REAL,                      -- taxa contratada, apenas referência
    data_vencimento TEXT,                      -- YYYY-MM-DD, null se sem vencimento
    moeda           TEXT DEFAULT 'BRL',        -- BRL ou USD
    ativo           INTEGER DEFAULT 1,         -- 1=ativo, 0=arquivado
    notas           TEXT,
    criado_em       TEXT DEFAULT (datetime('now','localtime')),
    atualizado_em   TEXT DEFAULT (datetime('now','localtime'))
);
```

### Tabela: `movimentacoes`
```sql
CREATE TABLE IF NOT EXISTS movimentacoes (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    investimento_id     INTEGER NOT NULL REFERENCES investimentos(id) ON DELETE RESTRICT,
    periodo_mes         INTEGER NOT NULL,      -- 1 a 12
    periodo_ano         INTEGER NOT NULL,      -- ex: 2026
    tipo_mov            TEXT NOT NULL,         -- DEPOSITO | SAQUE
    -- RENDIMENTO não é inserido pelo usuário; é calculado pelo sistema via VTA
    valor               REAL NOT NULL,         -- sempre positivo; tipo_mov define o sinal
    cotacao_dolar       REAL,                  -- cotação do dólar naquele período (se DOLAR)
    notas               TEXT,
    criado_em           TEXT DEFAULT (datetime('now','localtime'))
);
```

### Tabela: `vta_mensal`
Armazena o Valor Total do Ativo informado pelo usuário no dia 1º de cada mês.
A partir do VTA, o sistema calcula o rendimento do período.
```sql
CREATE TABLE IF NOT EXISTS vta_mensal (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    investimento_id     INTEGER NOT NULL REFERENCES investimentos(id) ON DELETE RESTRICT,
    periodo_mes         INTEGER NOT NULL,      -- 1 a 12
    periodo_ano         INTEGER NOT NULL,      -- ex: 2026
    vta                 REAL NOT NULL,         -- valor total informado pelo usuário
    criado_em           TEXT DEFAULT (datetime('now','localtime')),
    atualizado_em       TEXT DEFAULT (datetime('now','localtime')),
    UNIQUE (investimento_id, periodo_mes, periodo_ano)
);
```

### Tabela: `vai_anual`
Armazena o Valor de Aporte Inicial de cada ano (saldo acumulado do ano anterior).
Populada automaticamente na virada de ano ou no primeiro uso do ano.
```sql
CREATE TABLE IF NOT EXISTS vai_anual (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    investimento_id     INTEGER NOT NULL REFERENCES investimentos(id) ON DELETE RESTRICT,
    ano                 INTEGER NOT NULL,      -- ex: 2026
    vai                 REAL NOT NULL,         -- saldo de 31/12 do ano anterior
    criado_em           TEXT DEFAULT (datetime('now','localtime')),
    UNIQUE (investimento_id, ano)
);
-- No primeiro ano do investimento: VAI = primeiro depósito registrado
-- Nos anos seguintes: VAI = saldoFinal do ano anterior (calculado pelo sistema)
```

### Tabela: `cotacoes_dolar`
```sql
CREATE TABLE IF NOT EXISTS cotacoes_dolar (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    data         TEXT NOT NULL UNIQUE,         -- YYYY-MM-DD
    valor_compra REAL NOT NULL,
    valor_venda  REAL NOT NULL,
    fonte        TEXT DEFAULT 'AwesomeAPI',
    criado_em    TEXT DEFAULT (datetime('now','localtime'))
);
```

### Tabela: `configuracoes`
```sql
CREATE TABLE IF NOT EXISTS configuracoes (
    chave   TEXT PRIMARY KEY,
    valor   TEXT NOT NULL
);
-- Chaves padrão inseridas na primeira execução:
-- 'meta_patrimonio'     → '500000'
-- 'meta_aporte_mensal'  → '2000'
-- 'taxa_cdi_referencia' → '10.75'
-- 'meses_projecao'      → '60'
```

---

## Cálculos por Investimento (Renda Fixa)

> Estes cálculos aplicam-se **exclusivamente a investimentos do tipo RENDA_FIXA**.
> Renda variável e dólar seguem lógica própria (seção abaixo).

### Variáveis principais
| Variável | Descrição |
|---|---|
| **VAI** | Valor de Aporte Inicial do ano — saldo acumulado de 31/12 do ano anterior |
| **SAM** | Soma dos aportes líquidos (depósitos − saques) do ano corrente |
| **VI**  | Valor Investido = VAI + SAM |
| **VTA** | Valor Total do Ativo — informado pelo usuário no dia 1º de cada mês |
| **R**   | Rendimento calculado do período |

### 1. Rendimento do Período
```java
// Calculado automaticamente quando o usuário informa o VTA do mês
double vai = buscarVAI(investimentoId, anoAtual);           // da tabela vai_anual
double sam = calcularSAM(investimentoId, anoAtual, mes);    // soma depósitos - saques do ano até o mês
double vi  = vai + sam;
double vta = buscarVTA(investimentoId, mes, anoAtual);      // da tabela vta_mensal

double R = vta - vi;   // pode ser negativo (perda)
```

### 2. SAM — Soma dos Aportes do Ano Corrente
```java
// Soma SOMENTE movimentações do ano vigente (exclui VAI)
double sam = movimentacoes.stream()
    .filter(m -> m.ano == anoAtual && m.mes <= mesReferencia)
    .mapToDouble(m -> m.tipo == DEPOSITO ? m.valor : -m.valor)
    .sum();
```

### 3. Taxa de Rendimento em Percentual
```java
// Com taxa explícita cadastrada pelo usuário → usar sempre ela
if (investimento.taxaAnual != null) {
    double taxaMensal = Math.pow(1 + investimento.taxaAnual / 100, 1.0 / 12) - 1;
    // usar taxaMensal para projeções
}

// Sem taxa explícita → calcular a partir do histórico de VTA
else {
    double taxaAcumulada = (vta - vi) / vi; // em decimal, ex: 0.085 = 8,5%
    int nMeses = mesesDecorridosDesdeInicioAno(); // ou desde primeiro depósito no 1º ano
    double taxaMensal = Math.pow(1 + taxaAcumulada, 1.0 / nMeses) - 1;
    // usar taxaMensal para projeções
}
```

### 4. Saldo Calculado por Período
```java
// saldo = VTA do período (quando disponível)
// se VTA não foi informado ainda, usar último VTA conhecido + projeção pela taxa
double saldoPeriodo(int mes, int ano) {
    Optional<Double> vta = vtaMensalRepository.buscar(investimentoId, mes, ano);
    if (vta.isPresent()) return vta.get();          // dado real informado pelo usuário
    return projetarComTaxaAte(mes, ano);            // estimativa se VTA pendente
}
```

### 5. Virada de Ano (ciclo anual)
```java
// Executar automaticamente em 1º de janeiro OU na primeira abertura do app no novo ano
void virarAno(int investimentoId, int anoAnterior) {
    double saldoFinal = calcularSaldoFinalAno(investimentoId, anoAnterior);
    // saldoFinal = último VTA do ano anterior (mês 12, ou último disponível)
    vaiAnualRepository.inserir(investimentoId, anoAnterior + 1, saldoFinal);
    // SAM do novo ano começa em 0 (não há registro — calculado dinamicamente)
}
```

### 6. Alerta de VTA Desatualizado
```java
// Verificar ao abrir o app e ao entrar no dashboard
List<Investimento> comVtaPendente = investimentos.stream()
    .filter(inv -> inv.tipo == RENDA_FIXA && inv.ativo)
    .filter(inv -> !vtaMensalRepository.existe(inv.id, mesAtual, anoAtual))
    .collect(toList());

// Para cada ativo na lista: exibir banner/badge de alerta
// "⚠ Porquinho CDB: VTA de 05/2026 não informado. Rendimento não calculado."
```

### 7. Projeção (iterativa, mês a mês)
```java
public List<PontoProjecao> projetar(double saldoAtual, double aporteMensal,
                                    double taxaMensal, int meses) {
    List<PontoProjecao> pontos = new ArrayList<>();
    double saldo = saldoAtual;
    for (int i = 1; i <= meses; i++) {
        saldo = saldo * (1 + taxaMensal) + aporteMensal;
        pontos.add(new PontoProjecao(mesAtual + i, saldo));
    }
    return pontos;
}

// Chamar 3 vezes com multiplicadores sobre a taxaMensal efetiva:
projetar(saldo, aporte * 0.80, taxa * 0.80, meses); // Conservador
projetar(saldo, aporte,        taxa,        meses); // Base
projetar(saldo, aporte * 1.20, taxa * 1.20, meses); // Otimista
```

### 8. Consolidação Geral (Patrimônio Total)
```java
double totalBRL = investimentos.stream()
    .filter(inv -> inv.ativo)
    .mapToDouble(inv -> {
        double saldo = saldoService.calcularSaldoAtual(inv.id);
        return inv.moeda.equals("USD") ? saldo * cotacaoDolarAtual : saldo;
    })
    .sum();
```

---

## Cálculos por Investimento (Renda Variável)

> Estes cálculos aplicam-se **exclusivamente a investimentos do tipo RENDA_VARIAVEL**.

### Variáveis principais
| Variável | Descrição |
|---|---|
| **VTP** | Valor Total Pago — soma de todos os valores pagos em todos os aportes da ação |
| **QC**  | Quantidade de Cotas — total de cotas acumuladas (compras − vendas) |
| **VMA** | Valor Médio pago por Ação = VTP / QC |
| **VAC** | Valor Atual da Cota — obtido via API automaticamente ou digitado pelo usuário no dia 1º do mês |
| **D**   | Dividendos — soma de todos os dividendos/proventos recebidos da ação |
| **PTG** | Patrimônio Total Gerado = (VAC × QC) + D |
| **LEB** | Lucro Especulativo Base = (VAC × QC) − VTP |
| **VMR** | Variação Mensal Real = (VAC × QC) mês atual − (VAC × QC) mês anterior |

### 1. VTP — Valor Total Pago
```java
// Soma de todos os aportes (compras), sem descontar vendas
// Cada aporte tem: quantidade de cotas compradas + preço pago por cota
double vtp = aportes.stream()
    .filter(a -> a.tipo == COMPRA)
    .mapToDouble(a -> a.quantidade * a.precoPorCota)
    .sum();
```

### 2. QC — Quantidade de Cotas Atual
```java
double qc = aportes.stream()
    .mapToDouble(a -> a.tipo == COMPRA ? a.quantidade : -a.quantidade)
    .sum();
// QC nunca pode ser negativo — validar na Service antes de registrar venda
```

### 3. VMA — Valor Médio por Ação
```java
double vma = vtp / qc;
// VTP considera apenas compras (não recalcula sobre vendas parciais)
// Método: custo médio ponderado simples
```

### 4. VAC — Valor Atual da Cota
```java
// Prioridade 1: buscar via API (Brapi.dev) se ticker estiver cadastrado
// GET https://brapi.dev/api/quote/{TICKER}
// Armazenar na tabela vac_mensal com o período

// Prioridade 2: valor digitado pelo usuário no dia 1º do mês
// Armazenar na mesma tabela vac_mensal

// Se nenhum disponível no mês atual: usar último VAC registrado + alerta visual
double vac = vacMensalRepository.buscarMaisRecente(investimentoId);
```

### 5. D — Dividendos Acumulados
```java
// Soma de todos os proventos/dividendos recebidos desde o início
double d = aportes.stream()
    .filter(a -> a.tipo == DIVIDENDO)
    .mapToDouble(a -> a.valor)
    .sum();
```

### 6. PTG — Patrimônio Total Gerado
```java
double ptg = (vac * qc) + d;
// Representa o valor real atual da posição: mercado + proventos já recebidos
```

### 7. LEB — Lucro Especulativo Base
```java
double leb = (vac * qc) - vtp;
// Positivo = lucro especulativo; negativo = prejuízo especulativo
// Não inclui dividendos (apenas valorização/desvalorização das cotas)

double lebPercentual = (leb / vtp) * 100;
// Exibir junto ao LEB para contextualizar o percentual de ganho/perda
```

### 8. VMR — Variação Mensal Real
```java
// Diferença entre o valor de mercado da posição no mês atual vs mês anterior
double valorAtual    = vacAtual * qc;          // VAC do mês corrente × cotas atuais
double valorAnterior = vacAnterior * qc;       // VAC do mês anterior × cotas atuais

double vmr = valorAtual - valorAnterior;
// Positivo = cresceu; negativo = desvalorizou
// Exibir também como percentual: (vmr / valorAnterior) * 100
```

### 9. Alerta de VAC Desatualizado
```java
// Verificar ao abrir o app — análogo ao alerta de VTA da Renda Fixa
List<Investimento> comVacPendente = investimentos.stream()
    .filter(inv -> inv.tipo == RENDA_VARIAVEL && inv.ativo)
    .filter(inv -> !vacMensalRepository.existeNoMesAtual(inv.id))
    .collect(toList());

// "⚠ HGLG11 Carteira: VAC de 05/2026 não atualizado. PTG e LEB desatualizados."
// Alerta suprimido se o investimento tiver ticker e a API retornou VAC com sucesso
```

### 10. Tabela de dados adicionais no banco: `aportes_rv`
```sql
-- Registra cada compra, venda ou recebimento de dividendo na renda variável
CREATE TABLE IF NOT EXISTS aportes_rv (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    investimento_id   INTEGER NOT NULL REFERENCES investimentos(id) ON DELETE RESTRICT,
    tipo_op           TEXT NOT NULL,       -- COMPRA | VENDA | DIVIDENDO
    periodo_mes       INTEGER NOT NULL,    -- 1 a 12
    periodo_ano       INTEGER NOT NULL,    -- ex: 2026
    quantidade        REAL,               -- cotas compradas/vendidas (null para DIVIDENDO)
    preco_por_cota    REAL,               -- preço unitário pago/recebido (null para DIVIDENDO)
    valor             REAL,               -- valor total da operação (quantidade × preco ou dividendo)
    notas             TEXT,
    criado_em         TEXT DEFAULT (datetime('now','localtime'))
);

-- Registra o VAC por período (API ou digitado pelo usuário)
CREATE TABLE IF NOT EXISTS vac_mensal (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    investimento_id   INTEGER NOT NULL REFERENCES investimentos(id) ON DELETE RESTRICT,
    periodo_mes       INTEGER NOT NULL,
    periodo_ano       INTEGER NOT NULL,
    vac               REAL NOT NULL,      -- valor da cota no período
    fonte             TEXT DEFAULT 'MANUAL', -- MANUAL | API
    criado_em         TEXT DEFAULT (datetime('now','localtime')),
    atualizado_em     TEXT DEFAULT (datetime('now','localtime')),
    UNIQUE (investimento_id, periodo_mes, periodo_ano)
);
```

### 11. Resumo visual da tela de detalhe (Renda Variável)
```
┌─────────────────────────────────────────────────────────┐
│  HGLG11 Carteira                         Renda Variável │
├──────────────┬──────────────┬────────────┬──────────────┤
│  VTP         │  QC          │  VMA       │  VAC         │
│  R$ 12.400   │  80 cotas    │  R$ 155,00 │  R$ 162,50   │
├──────────────┼──────────────┼────────────┼──────────────┤
│  PTG         │  LEB         │  LEB %     │  VMR         │
│  R$ 13.600   │  R$ 600,00   │  +4,84%    │  +R$ 400,00  │
├──────────────┴──────────────┴────────────┴──────────────┤
│  Dividendos acumulados (D):  R$ 600,00                  │
└─────────────────────────────────────────────────────────┘
```

---

## Estrutura de Diretórios

```
investimentos-app/
├── src/
│   ├── main/
│   │   ├── java/br/investimentos/
│   │   │   ├── Main.java
│   │   │   ├── db/
│   │   │   │   └── DatabaseManager.java
│   │   │   ├── model/
│   │   │   │   ├── Investimento.java
│   │   │   │   ├── Movimentacao.java            -- DEPOSITO | SAQUE (Renda Fixa)
│   │   │   │   ├── AporteRv.java                -- COMPRA | VENDA | DIVIDENDO (Renda Variável)
│   │   │   │   ├── VtaMensal.java               -- VTA por período (Renda Fixa)
│   │   │   │   ├── VacMensal.java               -- VAC por período (Renda Variável)
│   │   │   │   ├── VaiAnual.java                -- Aporte inicial anual (Renda Fixa)
│   │   │   │   ├── TipoInvestimento.java        -- Enum: RENDA_FIXA, RENDA_VARIAVEL, DOLAR
│   │   │   │   ├── TipoMovimentacao.java        -- Enum: DEPOSITO, SAQUE
│   │   │   │   ├── TipoOperacaoRv.java          -- Enum: COMPRA, VENDA, DIVIDENDO
│   │   │   │   └── CotacaoDolar.java
│   │   │   ├── repository/
│   │   │   │   ├── InvestimentoRepository.java
│   │   │   │   ├── MovimentacaoRepository.java
│   │   │   │   ├── AporteRvRepository.java      -- CRUD de operações de Renda Variável
│   │   │   │   ├── VtaMensalRepository.java
│   │   │   │   ├── VacMensalRepository.java     -- CRUD do VAC por período
│   │   │   │   ├── VaiAnualRepository.java
│   │   │   │   └── CotacaoRepository.java
│   │   │   ├── service/
│   │   │   │   ├── RendimentoService.java       -- R = VTA - (VAI + SAM) [Renda Fixa]
│   │   │   │   ├── RendaVariavelService.java    -- VTP, QC, VMA, PTG, LEB, VMR, D
│   │   │   │   ├── TaxaService.java             -- Conversão taxa anual ↔ mensal
│   │   │   │   ├── SaldoService.java            -- Saldo por período
│   │   │   │   ├── ProjecaoService.java         -- Projeção com 3 cenários
│   │   │   │   ├── VaiService.java              -- Virada de ano (Renda Fixa)
│   │   │   │   ├── AlertaService.java           -- VTA e VAC pendentes
│   │   │   │   ├── ConsolidacaoService.java     -- Patrimônio total consolidado
│   │   │   │   └── CotacaoService.java          -- Dólar + VAC via API
│   │   │   └── ui/
│   │   │       ├── MainWindow.java
│   │   │       ├── dashboard/DashboardPanel.java
│   │   │       ├── investimento/
│   │   │       │   ├── InvestimentoListPanel.java
│   │   │       │   ├── InvestimentoFormDialog.java
│   │   │       │   ├── InvestimentoDetalheRfPanel.java   -- Detalhe Renda Fixa
│   │   │       │   └── InvestimentoDetalheRvPanel.java   -- Detalhe Renda Variável
│   │   │       ├── movimentacao/
│   │   │       │   ├── MovimentacaoFormDialog.java       -- Depósito/Saque (RF)
│   │   │       │   ├── AporteRvFormDialog.java           -- Compra/Venda/Dividendo (RV)
│   │   │       │   ├── VtaFormDialog.java                -- Informar VTA do mês (RF)
│   │   │       │   └── VacFormDialog.java                -- Informar VAC do mês (RV)
│   │   │       └── projecao/ProjecaoPanel.java
│   │   └── resources/db/schema.sql
│   └── test/java/br/investimentos/service/
│       ├── RendimentoServiceTest.java
│       ├── RendaVariavelServiceTest.java
│       ├── TaxaServiceTest.java
│       └── ProjecaoServiceTest.java
├── data/
│   └── investimentos.db
├── CLAUDE.md
├── pom.xml
└── README.md
```

---

## pom.xml — Dependências

```xml
<project>
  <modelVersion>4.0.0</modelVersion>
  <groupId>br.investimentos</groupId>
  <artifactId>painel-investimentos</artifactId>
  <version>1.0.0</version>

  <properties>
    <maven.compiler.source>17</maven.compiler.source>
    <maven.compiler.target>17</maven.compiler.target>
    <javafx.version>21</javafx.version>
  </properties>

  <dependencies>
    <dependency>
      <groupId>org.xerial</groupId>
      <artifactId>sqlite-jdbc</artifactId>
      <version>3.45.3.0</version>
    </dependency>
    <dependency>
      <groupId>com.fasterxml.jackson.core</groupId>
      <artifactId>jackson-databind</artifactId>
      <version>2.17.1</version>
    </dependency>
    <dependency>
      <groupId>org.openjfx</groupId>
      <artifactId>javafx-controls</artifactId>
      <version>${javafx.version}</version>
    </dependency>
    <dependency>
      <groupId>org.openjfx</groupId>
      <artifactId>javafx-fxml</artifactId>
      <version>${javafx.version}</version>
    </dependency>
    <dependency>
      <groupId>org.jfree</groupId>
      <artifactId>jfreechart</artifactId>
      <version>1.5.4</version>
    </dependency>
    <dependency>
      <groupId>org.junit.jupiter</groupId>
      <artifactId>junit-jupiter</artifactId>
      <version>5.10.2</version>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.mockito</groupId>
      <artifactId>mockito-core</artifactId>
      <version>5.11.0</version>
      <scope>test</scope>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-shade-plugin</artifactId>
        <version>3.5.2</version>
        <executions>
          <execution>
            <phase>package</phase>
            <goals><goal>shade</goal></goals>
            <configuration>
              <transformers>
                <transformer implementation=
                  "org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                  <mainClass>br.investimentos.Main</mainClass>
                </transformer>
              </transformers>
            </configuration>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>
</project>
```

---

## Fluxos de Uso (User Stories)

### US1 — Criar um Investimento
```
1. Usuário clica em "Novo Investimento"
2. Preenche: nome ("Porquinho CDB"), tipo (Renda Fixa), subtipo (CDB),
   indexador (CDI), taxa (12,5% a.a.), vencimento (opcional)
3. Salva → investimento criado, saldo = R$ 0,00
4. Sistema exibe na lista com saldo zerado aguardando movimentações
```

### US2 — Registrar Depósito ou Saque
```
1. Usuário seleciona "Porquinho CDB" → "Nova Movimentação"
2. Tipo = DEPÓSITO | Período = 02/2026 | Valor = R$ 500,00
3. Sistema salva na tabela movimentacoes e recalcula o SAM do ano
4. Rendimento ainda não atualizado — aguarda VTA do período
```

### US3 — Informar VTA do Mês (gatilho do rendimento)
```
1. No dia 1º de cada mês, o sistema exibe alerta nos ativos sem VTA
2. Usuário seleciona "Porquinho CDB" → "Informar VTA"
3. Informa: VTA = R$ 5.750,00 para o período 01/2026
4. Sistema calcula automaticamente:
   VAI = R$ 5.200,00 | SAM = R$ 500,00 | VI = R$ 5.700,00
   R = 5.750 − 5.700 = R$ 50,00
5. Exibe: rendimento de R$ 50,00 (0,88%) em 01/2026
```

### US4 — Virada de Ano
```
1. Ao abrir o app em janeiro/2027, sistema detecta que 2026 encerrou
2. Busca o último VTA de 2026 disponível (preferencialmente mês 12)
3. Grava: VAI_2027 = R$ [último VTA de 2026]
4. Notifica o usuário: "VAI de 2027 definido para [nome do ativo]: R$ X"
5. SAM de 2027 começa zerado, aguardando novos aportes
```

### US5 — Visualizar Evolução por Período
```
Tela de detalhe exibe tabela cronológica:

| Período | Depósitos | Saques | VTA      | VI       | Rendimento | % Período |
|---------|-----------|--------|----------|----------|------------|-----------|
| 01/2026 | R$ 500    | R$ 0   | R$ 5.750 | R$ 5.700 | R$ 50,00   | 0,88%     |
| 02/2026 | R$ 500    | R$ 200 | R$ 6.120 | R$ 6.200 | −R$ 80,00  | −1,29%    |
| 03/2026 | —         | —      | ⚠ Pendente | —      | —          | —         |

+ Gráfico de linha com evolução do VTA mês a mês
```

### US6 — Ver Projeção
```
1. Sistema calcula automaticamente com base no histórico real:
   - Aporte líquido médio: média dos (depósitos - saques) por período
   - Taxa implícita mensal: média de (rendimento / saldo_inicio_periodo)
2. Projeta nos 3 cenários para 12, 24 e 60 meses
3. Exibe gráfico de linha com os 3 cenários sobrepostos
4. Exibe: "Para atingir R$ 100.000, você precisará de X meses (cenário base)"
```

---

## Validações Obrigatórias

| Regra | Comportamento |
|---|---|
| VTA obrigatório para rendimento | Rendimento só é calculado após VTA do período ser informado |
| VTA não pode ser negativo | Bloquear VTA ≤ 0 na camada Service |
| Saque não pode superar VI | SAM líquido (depósitos − saques) não pode tornar VI negativo |
| Período obrigatório | Toda movimentação exige mês (1-12) e ano válidos |
| Valor sempre positivo | Campo `valor` sempre > 0; `tipo_mov` define o sinal |
| VAI imutável no ano | VAI de um ano não pode ser editado após definido (apenas pelo processo de virada) |
| Deletar com histórico | Proibido — apenas arquivar (`ativo = 0`) |
| Período futuro | Permitido para depósitos/saques planejados, sinalizar visualmente |
| VTA desatualizado | Exibir alerta se dia ≥ 1 e VTA do mês corrente ausente |

---

## Integração: Cotação do Dólar em Tempo Real

```java
// Endpoint gratuito, sem chave de API
GET https://economia.awesomeapi.com.br/json/last/USD-BRL

// Resposta relevante:
// { "USDBRL": { "bid": "5.3210", "ask": "5.3250", "pctChange": "0.05" } }

public CotacaoDolar obterCotacao() {
    // 1. Verificar cache: SELECT * FROM cotacoes_dolar WHERE data = date('now','localtime')
    // 2. Se encontrado → retornar do banco
    // 3. Se não → chamar API via java.net.http.HttpClient
    // 4. Parsear com Jackson → salvar no banco → retornar
}

// Refresh automático a cada 15 minutos:
ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
scheduler.scheduleAtFixedRate(this::atualizarCotacao, 0, 15, TimeUnit.MINUTES);
```

---

## Dashboard — Variáveis e Cálculos

Todas as variáveis abaixo são calculadas dinamicamente para o **ano vigente**,
exceto onde indicado. São de responsabilidade do `ConsolidacaoService`.

### Variáveis do Dashboard
| Variável | Nome completo | Fórmula |
|---|---|---|
| **VIARF** | Valor Investido Anual Renda Fixa | Σ depósitos líquidos (DEPOSITO − SAQUE) de todos os ativos RF no ano vigente |
| **RARF** | Rendimento Anual Renda Fixa | Σ rendimentos calculados (R) de todos os ativos RF no ano vigente |
| **VTARF** | Valor Total Acumulado Renda Fixa | VIARF + RARF |
| **VIARV** | Valor Investido Anual Renda Variável | Σ (quantidade × preço) de todas as COMPRAs de ativos RV no ano vigente |
| **PARV** | Patrimônio Atual Renda Variável | Σ (VAC × QC) de todos os ativos RV ativos |
| **DTA** | Dividendo Total Anual | Σ todos os DIVIDENDOs de ativos RV no ano vigente |
| **VTARV** | Valor Total Acumulado Renda Variável | PARV + DTA |
| **VTIA** | Valor Total Investido Anual | VIARF + VIARV |
| **VTRA** | Valor Total de Rendimento Anual | RARF + DTA |
| **PTA** | Patrimônio Total Anual | VTARF + VTARV |
| **PCPA** | Porcentagem de Crescimento Patrimonial Anual | (VTRA / PTA) × 100 |
| **PRAT** | Porcentagem de Rendimento Anual Total | (VTRA / VTIA) × 100 |

### Cálculos em Java
```java
// ConsolidacaoService.java — todos os valores para o ano vigente

// Renda Fixa
double viarf = movimentacaoRepo.somaDepositosLiquidos(anoAtual, RENDA_FIXA);
double rarf  = rendimentoService.somaRendimentos(anoAtual, RENDA_FIXA);
double vtarf = viarf + rarf;

// Renda Variável
double viarv = aporteRvRepo.somaCompras(anoAtual);          // Σ (qtd × preço) das COMPRAs
double parv  = rendaVariavelService.somaPatrimonioAtual();  // Σ (VAC × QC) de todos os ativos RV
double dta   = aporteRvRepo.somaDividendos(anoAtual);
double vtarv = parv + dta;

// Consolidados
double vtia  = viarf + viarv;
double vtra  = rarf + dta;
double pta   = vtarf + vtarv;

// Percentuais (proteger contra divisão por zero)
double pcpa  = pta  > 0 ? (vtra / pta)  * 100 : 0;
double prat  = vtia > 0 ? (vtra / vtia) * 100 : 0;
```

### Mensagens de exibição
```java
// PCPA — exibida como subtítulo do card de crescimento patrimonial
String msgPcpa = String.format("Seu patrimônio cresceu %.2f%% esse ano", pcpa);

// PRAT — exibida como subtítulo do card de rendimento total
String msgPrat = String.format("Esse ano você teve um rendimento total de %.2f%%", prat);
```

### Layout do Dashboard

```
┌─────────────────────────────────────────────────────────────────────┐
│  💰 Cotação do Dólar        📅 Ano vigente: 2026                   │
│  Compra: R$ 5,32 | Venda: R$ 5,33 | Var: +0,05%                   │
├──────────────────┬──────────────────┬──────────────────────────────┤
│  RENDA FIXA      │  RENDA VARIÁVEL  │  CONSOLIDADO                 │
│                  │                  │                              │
│  VIARF           │  VIARV           │  VTIA                        │
│  R$ 6.000        │  R$ 4.800        │  R$ 10.800                   │
│                  │                  │                              │
│  RARF            │  DTA             │  VTRA                        │
│  R$ 850          │  R$ 320          │  R$ 1.170                    │
│                  │                  │                              │
│  VTARF           │  VTARV           │  PTA                         │
│  R$ 6.850        │  R$ 9.120        │  R$ 15.970                   │
│                  │  (PARV: R$8.800) │                              │
├──────────────────┴──────────────────┴──────────────────────────────┤
│  📈 Seu patrimônio cresceu 7,33% esse ano          (PCPA)          │
│  💵 Esse ano você teve um rendimento total de 10,83%  (PRAT)       │
├─────────────────────────────────────────────────────────────────────┤
│  Gráfico pizza: Alocação RF × RV × Dólar                          │
│  Gráfico linha: Evolução do PTA mês a mês no ano vigente           │
│  Gráfico barras: VTRA por mês (RARF + DTA, últimos 12 meses)      │
│  Tabela: todos os ativos com saldo/posição atual e % da carteira   │
└─────────────────────────────────────────────────────────────────────┘
```

### Regras de exibição
| Situação | Comportamento |
|---|---|
| VTIA = 0 (nenhum aporte no ano) | Exibir PRAT como `—` em vez de 0% |
| PTA = 0 | Exibir PCPA como `—` em vez de 0% |
| PCPA < 0 | Exibir em vermelho com ícone ▼ |
| PCPA > 0 | Exibir em verde com ícone ▲ |
| PRAT < 0 | Exibir em vermelho com ícone ▼ |
| PRAT > 0 | Exibir em verde com ícone ▲ |
| VAC de algum ativo RV desatualizado | Exibir aviso: "⚠ PARV pode estar desatualizado" |
| VTA de algum ativo RF pendente | Exibir aviso: "⚠ VTARF pode estar desatualizado" |

---

## Convenções de Código

- Padrão **Repository → Service → UI** — UI nunca acessa o banco diretamente
- Períodos armazenados como dois `INTEGER` separados: `periodo_mes` e `periodo_ano`
- Datas como `LocalDate` internamente; persistir como `TEXT` ISO-8601 no SQLite
- Valores monetários: `double` para cálculo, `BigDecimal` + `NumberFormat.getCurrencyInstance(new Locale("pt","BR"))` para exibição
- Logs com `java.util.logging` — nunca `System.out.println` em produção
- Exceções de banco encapsuladas em `DatabaseException extends RuntimeException`
- `DatabaseManager` singleton com `PRAGMA foreign_keys = ON` e `PRAGMA journal_mode = WAL`
- Migrations versionadas em `schema.sql` controladas por tabela `schema_version`
- **Build exclusivamente Maven** — nunca sugerir ou gerar arquivos Gradle

---

## Ordem de Implementação

```
FASE 1 — Fundação (sem UI)
  ✅ pom.xml configurado com todas as dependências
  ✅ DatabaseManager + schema.sql + migrations
  ✅ Models: Investimento, Movimentacao, enums TipoInvestimento / TipoMovimentacao
  ✅ Repositories: CRUD básico para cada entidade
  ✅ SaldoService com testes JUnit (cobrir US2, US3, US4)
  ✅ ProjecaoService com testes JUnit

FASE 2 — CRUD na Interface
  ✅ MainWindow com navegação lateral ou por abas
  ✅ InvestimentoListPanel: listar, arquivar
  ✅ InvestimentoFormDialog: criar e editar
  ✅ MovimentacaoFormDialog: depósito / saque / rendimento com seletor de período
  ✅ InvestimentoDetalhePanel: tabela de períodos + gráfico de linha

FASE 3 — Dashboard
  ✅ Cards de resumo (patrimônio, dólar, rendimentos, aporte médio)
  ✅ CotacaoService + cotação dólar ao vivo com cache
  ✅ Gráficos de alocação (pizza por tipo e por investimento)
  ✅ Gráfico de evolução histórica do patrimônio total

FASE 4 — Projeção
  ✅ ProjecaoPanel com seletor de horizonte (12 / 24 / 60 meses)
  ✅ Gráfico de linha com 3 cenários sobrepostos
  ✅ Calculadora de meta: "Em quantos meses chego em R$ X?"

FASE 5 — Refinamentos
  ✅ Tela de configurações (meta, taxa CDI de referência, meses de projeção)
  ✅ Backup/restore manual do arquivo .db
  ✅ Exportação CSV do histórico de movimentações
  ✅ Validações e mensagens de erro amigáveis
```

---

## Recomendações de Incrementos Futuros

### 🔌 Cotações Automáticas
| Incremento | Detalhe |
|---|---|
| Ações e FIIs BR | Brapi.dev — gratuita sem chave: `GET https://brapi.dev/api/quote/HGLG11` |
| Tesouro Direto | API oficial B3 pública com preços dos títulos |
| Cripto | CoinGecko API (gratuita): BTC, ETH, USDT em BRL |
| Atualização automática | Buscar cotação ao abrir detalhe, cache de 15 min no banco |

### 📊 Análises Avançadas
| Incremento | Detalhe |
|---|---|
| Comparativo com CDI | Mostrar se o investimento está "batendo" o CDI no período |
| Rentabilidade acumulada | % total desde o primeiro depósito |
| Alerta de rendimento zerado | Avisar se ativo ficou meses sem registrar rendimento |
| Rebalanceamento | Sugerir em qual tipo aportar para atingir alocação alvo |

### 🖥️ Usabilidade
| Incremento | Detalhe |
|---|---|
| Importação em lote | CSV com colunas: investimento, período, tipo, valor |
| Exportação PDF | Relatório do dashboard para impressão (Apache PDFBox) |
| Alertas de vencimento | Notificação visual para renda fixa próxima do vencimento |
| Tema escuro/claro | Toggle com preferência salva em `configuracoes` |

### 🛡️ Segurança e Confiabilidade
| Incremento | Detalhe |
|---|---|
| Backup automático | Cópia diária do `.db` com timestamp na pasta `backup/` |
| Exportação/importação JSON | Serializar carteira inteira para migração ou backup externo |
| Criptografia do banco | SQLCipher com senha definida pelo usuário |
| Log de auditoria | Registrar todas as operações com timestamp em tabela `audit_log` |

---

## Contexto Permanente para o Claude Code

> Ao receber qualquer tarefa neste projeto, seguir obrigatoriamente:
>
> - **Build:** Maven exclusivamente — nunca gerar ou sugerir arquivos Gradle
> - **Arquitetura:** Repository → Service → UI — nunca pular camadas
>
> **Renda Fixa:**
> - Rendimento `R = VTA − (VAI + SAM)` — calculado pelo sistema, nunca digitado
> - `TipoMovimentacao`: apenas `DEPOSITO` e `SAQUE` — sem `RENDIMENTO`
> - `VTA`: tabela `vta_mensal`; único valor digitado diretamente pelo usuário na RF
> - `VAI`: tabela `vai_anual`; imutável no ano; gerado na virada pelo `VaiService`
> - `SAM`: calculado dinamicamente — nunca armazenado
> - Taxa: se `taxa_anual` preenchida → `(1 + taxa/100)^(1/12) − 1`; se ausente → `(1 + R/VI)^(1/nMeses) − 1`
>
> **Renda Variável:**
> - Operações em `aportes_rv` com `TipoOperacaoRv`: `COMPRA`, `VENDA`, `DIVIDENDO`
> - `VTP` = soma de `(quantidade × preco_por_cota)` apenas das `COMPRA`s
> - `QC` = soma de cotas compradas − cotas vendidas; nunca negativo
> - `VMA = VTP / QC` — custo médio ponderado; não recalcula sobre vendas parciais
> - `VAC`: tabela `vac_mensal`; fonte `API` (Brapi.dev) ou `MANUAL`
> - `PTG = (VAC × QC) + D`
> - `LEB = (VAC × QC) − VTP`; exibir também `(LEB / VTP) × 100`
> - `VMR = (VAC_atual × QC) − (VAC_anterior × QC)`; positivo = valorização
> - `D` = soma de todos os registros `DIVIDENDO` em `aportes_rv`
>
> **Geral:**
> - `AlertaService` cobre VTA pendente (RF) e VAC pendente (RV)
> - Período: dois `INTEGER` separados (`periodo_mes`, `periodo_ano`) — nunca `String`
> - Valores: `double` para cálculo, `BigDecimal` + `NumberFormat` para exibição em BRL
> - Dólar: AwesomeAPI com cache diário na tabela `cotacoes_dolar`
> - Banco: arquivo `data/investimentos.db` relativo ao diretório de execução do JAR
> - Exclusão: investimentos com histórico nunca são deletados — apenas arquivados (`ativo = 0`)
> - Virada de ano: `VaiService.virarAno()` na primeira abertura do app após 1º de janeiro
