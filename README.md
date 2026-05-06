# Sifin — Painel de Investimentos

Aplicação desktop **open source** para controle individual de investimentos pessoais.
Roda 100% local — sem servidor, sem cadastro, sem internet obrigatória.

---

## O que é o Sifin?

O Sifin organiza sua carteira de investimentos em um único lugar, calculando rendimentos,
projeções e consolidando tudo em um dashboard visual.

**Módulos disponíveis**

| Módulo | O que faz |
|---|---|
| **Renda Fixa** | Registra CDB, LCI, LCA, Tesouro Direto. Calcula VTA, VAI, SAM, rendimento e taxa mensal. |
| **Renda Variável** | Controla FIIs, ações e ETFs. Calcula preço médio, patrimônio, LEB e dividendos. |
| **Dólar** | Acompanha investimentos em dólar com cotação atualizada automaticamente. |
| **Gastos** | Registra despesas por categoria (Alimentar, Diversos, Mensalidades) com suporte a parcelamento automático. |
| **Dashboard** | Consolida patrimônio total, rendimentos e crescimento com gráficos e filtro por ano. |

**Destaques**

- Cotação do dólar atualizada automaticamente (AwesomeAPI)
- Cotação de ativos via Brapi.dev (ações, FIIs, ETFs)
- Projeção em 3 cenários (pessimista, realista, otimista)
- Gráficos de evolução patrimonial (JFreeChart)
- Banco de dados local SQLite — seus dados ficam só na sua máquina
- Parcelamento automático: cadastre uma compra parcelada e o sistema distribui as parcelas nos meses seguintes

---

## Pré-requisitos

Apenas o **Git** para clonar o repositório. O script de setup instala Java e Maven automaticamente.

---

## Instalação — Linux / macOS

```bash
# 1. Clone o repositório
git clone https://github.com/Rui-Goncalves-II/Sifin.git
cd sifin

# 2. Dê permissão e rode o setup
chmod +x setup/linux/setup.sh
./setup/linux/setup.sh
```

O script vai:
1. Verificar se o Java 17+ está instalado — instala automaticamente se necessário (via apt, dnf ou pacman)
2. Verificar se o Maven está instalado — instala automaticamente se necessário
3. Compilar o projeto e gerar o JAR em `target/`
4. Criar o script `run.sh` na raiz do projeto
5. Criar um atalho na **área de trabalho** e no menu de aplicativos

Para iniciar após o setup:

```bash
./run.sh
```

---

## Instalação — Windows

> **Requisito:** Windows 10 (build 1809+) ou Windows 11 com o **winget** disponível (já incluso por padrão).

```powershell
# 1. Clone o repositório
git clone https://github.com/Rui-Goncalves-II/Sifin.git
cd sifin

# 2. Execute o setup (PowerShell como Administrador)
powershell -ExecutionPolicy Bypass -File setup\windows\setup.ps1
```

O script vai:
1. Verificar se o Java 17+ está instalado — instala automaticamente via winget (Eclipse Temurin)
2. Verificar se o Maven está instalado — instala automaticamente via winget
3. Compilar o projeto e gerar o JAR em `target\`
4. Criar o script `run.bat` na raiz do projeto
5. Criar um atalho na **área de trabalho**

Para iniciar após o setup:

```bat
run.bat
```

---

## Recompilar após alterações

```bash
# Linux / macOS
mvn package -DskipTests

# Windows
mvn package -DskipTests
```

---

## Tecnologias

- **Java 17** + **JavaFX 21**
- **SQLite** (xerial/sqlite-jdbc) — banco local em `data/investimentos.db`
- **Maven** — gerenciador de build e dependências
- **JFreeChart** — gráficos
- **Jackson** — consumo de APIs REST

---

## Licença

Distribuído sob a licença MIT. Veja `LICENSE` para mais detalhes.
