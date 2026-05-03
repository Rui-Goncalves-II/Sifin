CREATE TABLE IF NOT EXISTS investimentos (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    nome TEXT NOT NULL,
    tipo TEXT NOT NULL,
    subtipo TEXT,
    indexador TEXT,
    taxa_anual REAL,
    data_vencimento TEXT,
    moeda TEXT DEFAULT 'BRL',
    ativo INTEGER DEFAULT 1,
    notas TEXT,
    criado_em TEXT DEFAULT (datetime('now','localtime')),
    atualizado_em TEXT DEFAULT (datetime('now','localtime'))
);

CREATE TABLE IF NOT EXISTS movimentacoes (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    investimento_id INTEGER NOT NULL REFERENCES investimentos(id) ON DELETE RESTRICT,
    periodo_mes INTEGER NOT NULL,
    periodo_ano INTEGER NOT NULL,
    tipo_mov TEXT NOT NULL,
    valor REAL NOT NULL,
    cotacao_dolar REAL,
    notas TEXT,
    criado_em TEXT DEFAULT (datetime('now','localtime'))
);

CREATE TABLE IF NOT EXISTS vta_mensal (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    investimento_id INTEGER NOT NULL REFERENCES investimentos(id) ON DELETE RESTRICT,
    periodo_mes INTEGER NOT NULL,
    periodo_ano INTEGER NOT NULL,
    vta REAL NOT NULL,
    criado_em TEXT DEFAULT (datetime('now','localtime')),
    atualizado_em TEXT DEFAULT (datetime('now','localtime')),
    UNIQUE (investimento_id, periodo_mes, periodo_ano)
);

CREATE TABLE IF NOT EXISTS vai_anual (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    investimento_id INTEGER NOT NULL REFERENCES investimentos(id) ON DELETE RESTRICT,
    ano INTEGER NOT NULL,
    vai REAL NOT NULL,
    criado_em TEXT DEFAULT (datetime('now','localtime')),
    UNIQUE (investimento_id, ano)
);

CREATE TABLE IF NOT EXISTS aportes_rv (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    investimento_id INTEGER NOT NULL REFERENCES investimentos(id) ON DELETE RESTRICT,
    tipo_op TEXT NOT NULL,
    periodo_mes INTEGER NOT NULL,
    periodo_ano INTEGER NOT NULL,
    quantidade REAL,
    preco_por_cota REAL,
    valor REAL NOT NULL,
    notas TEXT,
    criado_em TEXT DEFAULT (datetime('now','localtime'))
);

CREATE TABLE IF NOT EXISTS vac_mensal (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    investimento_id INTEGER NOT NULL REFERENCES investimentos(id) ON DELETE RESTRICT,
    periodo_mes INTEGER NOT NULL,
    periodo_ano INTEGER NOT NULL,
    vac REAL NOT NULL,
    fonte TEXT DEFAULT 'MANUAL',
    criado_em TEXT DEFAULT (datetime('now','localtime')),
    atualizado_em TEXT DEFAULT (datetime('now','localtime')),
    UNIQUE (investimento_id, periodo_mes, periodo_ano)
);

CREATE TABLE IF NOT EXISTS cotacoes_dolar (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    data TEXT NOT NULL UNIQUE,
    valor_compra REAL NOT NULL,
    valor_venda REAL NOT NULL,
    fonte TEXT DEFAULT 'AwesomeAPI',
    criado_em TEXT DEFAULT (datetime('now','localtime'))
);

CREATE TABLE IF NOT EXISTS configuracoes (
    chave TEXT PRIMARY KEY,
    valor TEXT NOT NULL
);

INSERT OR IGNORE INTO configuracoes (chave, valor) VALUES
    ('meta_patrimonio', '1000000.00'),
    ('meta_aporte_mensal', '1000.00'),
    ('taxa_cdi_referencia', '10.65'),
    ('meses_projecao', '24'),
    ('sidebar_expandida', '1');
