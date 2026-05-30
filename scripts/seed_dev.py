"""
Popula o banco de dados de desenvolvimento com dados fictícios.
Cobre 2024, 2025 e 2026 (Jan–Abr), deixando Mai/2026 sem VTA/VAC para
disparar os alertas de pendência.
"""

import sqlite3, os

DB = os.path.join(os.path.dirname(__file__), "..", "data", "investimentos.db")

conn = sqlite3.connect(DB)
c = conn.cursor()
conn.execute("PRAGMA foreign_keys = ON")

# ── Investimentos ─────────────────────────────────────────────────────────
c.executemany(
    "INSERT INTO investimentos (id, nome, tipo, subtipo, indexador, taxa_anual, "
    "data_vencimento, moeda, ativo) VALUES (?,?,?,?,?,?,?,?,?)",
    [
        (1, "PORQUINHO CDB",      "RENDA_FIXA",    "CDB",    "CDI",  12.5, "2027-01-15", "BRL", 1),
        (2, "TESOURO SELIC 2027", "RENDA_FIXA",    "TESOURO","SELIC", None, "2027-03-01", "BRL", 1),
        (3, "LCI BANCO PRIME",    "RENDA_FIXA",    "LCI",    "CDI",  10.8, "2026-06-30", "BRL", 1),
        (4, "HGLG11",             "RENDA_VARIAVEL","FII",     None,   None, None,         "BRL", 1),
        (5, "VALE3",              "RENDA_VARIAVEL","ACAO",    None,   None, None,         "BRL", 1),
        (6, "DÓLAR RESERVA",      "DOLAR",          None,     None,   None, None,         "USD", 1),
    ]
)

# ── Movimentações (RF + Dólar) ─────────────────────────────────────────────
c.executemany(
    "INSERT INTO movimentacoes (investimento_id, periodo_mes, periodo_ano, tipo_mov, valor, cotacao_dolar) "
    "VALUES (?,?,?,?,?,?)",
    [
        # PORQUINHO CDB
        (1,  1, 2024, "DEPOSITO", 10000.00, None),
        (1,  6, 2024, "DEPOSITO",  5000.00, None),
        (1,  1, 2025, "DEPOSITO",  3000.00, None),
        (1,  4, 2025, "DEPOSITO",  2000.00, None),
        # TESOURO SELIC
        (2,  7, 2024, "DEPOSITO",  8000.00, None),
        (2,  9, 2024, "DEPOSITO",  4000.00, None),
        (2,  5, 2025, "DEPOSITO",  5000.00, None),
        # LCI BANCO PRIME (começa Mar/2025)
        (3,  3, 2025, "DEPOSITO", 15000.00, None),
        # DÓLAR RESERVA  (valor em USD, cotação histórica)
        (6,  2, 2024, "DEPOSITO",   500.00, 4.97),
        (6,  5, 2024, "DEPOSITO",   500.00, 5.15),
        (6, 10, 2024, "DEPOSITO",   300.00, 5.48),
        (6,  3, 2025, "DEPOSITO",   600.00, 5.87),
        (6,  8, 2025, "DEPOSITO",   400.00, 5.72),
        (6,  1, 2026, "DEPOSITO",   500.00, 5.96),
    ]
)

# ── VAI Anual ─────────────────────────────────────────────────────────────
# 1º ano: não existe na tabela (getVai retorna 0.0); SAM = todos os depósitos
# Anos seguintes: VAI = último VTA do ano anterior
c.executemany(
    "INSERT INTO vai_anual (investimento_id, ano, vai) VALUES (?,?,?)",
    [
        (1, 2025, 16622.00),  # último VTA Dez/2024
        (1, 2026, 24246.00),  # último VTA Dez/2025
        (2, 2025, 12622.00),
        (2, 2026, 19411.00),
        (3, 2026, 16340.00),  # último VTA Dez/2025
    ]
)

# ── VTA Mensal (RF) ───────────────────────────────────────────────────────
# PORQUINHO CDB — taxa ≈ 12.5% aa → ~0.99% am
vta_cdb = [
    # 2024
    (1,  1, 2024, 10099.00), (1,  2, 2024, 10199.00), (1,  3, 2024, 10300.00),
    (1,  4, 2024, 10402.00), (1,  5, 2024, 10505.00), (1,  6, 2024, 15659.00),
    (1,  7, 2024, 15814.00), (1,  8, 2024, 15971.00), (1,  9, 2024, 16130.00),
    (1, 10, 2024, 16290.00), (1, 11, 2024, 16451.00), (1, 12, 2024, 16622.00),
    # 2025  (VAI=16622, depósitos: Jan +3000, Abr +2000)
    (1,  1, 2025, 19813.00), (1,  2, 2025, 20009.00), (1,  3, 2025, 20207.00),
    (1,  4, 2025, 22424.00), (1,  5, 2025, 22646.00), (1,  6, 2025, 22870.00),
    (1,  7, 2025, 23096.00), (1,  8, 2025, 23323.00), (1,  9, 2025, 23552.00),
    (1, 10, 2025, 23783.00), (1, 11, 2025, 24016.00), (1, 12, 2025, 24246.00),
    # 2026  (VAI=24246, sem depósitos) — Mai pendente (alerta)
    (1,  1, 2026, 24486.00), (1,  2, 2026, 24728.00),
    (1,  3, 2026, 24972.00), (1,  4, 2026, 25218.00),
]

# TESOURO SELIC 2027 — começa Jul/2024, ~10.5% aa → ~0.84% am
vta_teso = [
    # 2024
    (2,  7, 2024,  8067.00), (2,  8, 2024,  8135.00), (2,  9, 2024, 12312.00),
    (2, 10, 2024, 12415.00), (2, 11, 2024, 12519.00), (2, 12, 2024, 12622.00),
    # 2025  (VAI=12622, depósito: Mai +5000)
    (2,  1, 2025, 12728.00), (2,  2, 2025, 12835.00), (2,  3, 2025, 12942.00),
    (2,  4, 2025, 13051.00), (2,  5, 2025, 18308.00), (2,  6, 2025, 18462.00),
    (2,  7, 2025, 18617.00), (2,  8, 2025, 18773.00), (2,  9, 2025, 18930.00),
    (2, 10, 2025, 19089.00), (2, 11, 2025, 19249.00), (2, 12, 2025, 19411.00),
    # 2026  (VAI=19411, sem depósitos)
    (2,  1, 2026, 19573.00), (2,  2, 2026, 19736.00),
    (2,  3, 2026, 19901.00), (2,  4, 2026, 20067.00),
]

# LCI BANCO PRIME — começa Mar/2025, 10.8% aa → ~0.86% am
vta_lci = [
    # 2025  (VAI=0, depósito: Mar +15000)
    (3,  3, 2025, 15129.00), (3,  4, 2025, 15259.00), (3,  5, 2025, 15390.00),
    (3,  6, 2025, 15522.00), (3,  7, 2025, 15655.00), (3,  8, 2025, 15789.00),
    (3,  9, 2025, 15925.00), (3, 10, 2025, 16062.00), (3, 11, 2025, 16200.00),
    (3, 12, 2025, 16340.00),
    # 2026  (VAI=16340, sem depósitos)
    (3,  1, 2026, 16481.00), (3,  2, 2026, 16622.00),
    (3,  3, 2026, 16765.00), (3,  4, 2026, 16909.00),
]

c.executemany(
    "INSERT INTO vta_mensal (investimento_id, periodo_mes, periodo_ano, vta) VALUES (?,?,?,?)",
    vta_cdb + vta_teso + vta_lci
)

# ── Aportes RV ────────────────────────────────────────────────────────────
c.executemany(
    "INSERT INTO aportes_rv (investimento_id, tipo_op, periodo_mes, periodo_ano, "
    "quantidade, preco_por_cota, valor) VALUES (?,?,?,?,?,?,?)",
    [
        # HGLG11 — compras
        (4, "COMPRA",  1, 2024, 50.0, 145.00,  7250.00),
        (4, "COMPRA",  5, 2024, 30.0, 148.00,  4440.00),
        (4, "COMPRA",  3, 2025, 20.0, 152.00,  3040.00),
        # HGLG11 — dividendos (80 cotas a partir de Mai/2024; 100 a partir de Mar/2025)
        (4, "DIVIDENDO",  2, 2024, None, None,   55.00),
        (4, "DIVIDENDO",  3, 2024, None, None,   55.00),
        (4, "DIVIDENDO",  4, 2024, None, None,   55.00),
        (4, "DIVIDENDO",  5, 2024, None, None,   92.00),
        (4, "DIVIDENDO",  6, 2024, None, None,   92.00),
        (4, "DIVIDENDO",  7, 2024, None, None,   92.00),
        (4, "DIVIDENDO",  8, 2024, None, None,   92.00),
        (4, "DIVIDENDO",  9, 2024, None, None,   92.00),
        (4, "DIVIDENDO", 10, 2024, None, None,   92.00),
        (4, "DIVIDENDO", 11, 2024, None, None,   92.00),
        (4, "DIVIDENDO", 12, 2024, None, None,   92.00),
        (4, "DIVIDENDO",  1, 2025, None, None,   94.40),
        (4, "DIVIDENDO",  2, 2025, None, None,   94.40),
        (4, "DIVIDENDO",  3, 2025, None, None,  118.00),
        (4, "DIVIDENDO",  4, 2025, None, None,  120.00),
        (4, "DIVIDENDO",  5, 2025, None, None,  120.00),
        (4, "DIVIDENDO",  6, 2025, None, None,  120.00),
        (4, "DIVIDENDO",  7, 2025, None, None,  120.00),
        (4, "DIVIDENDO",  8, 2025, None, None,  120.00),
        (4, "DIVIDENDO",  9, 2025, None, None,  120.00),
        (4, "DIVIDENDO", 10, 2025, None, None,  120.00),
        (4, "DIVIDENDO", 11, 2025, None, None,  120.00),
        (4, "DIVIDENDO", 12, 2025, None, None,  120.00),
        (4, "DIVIDENDO",  1, 2026, None, None,  122.00),
        (4, "DIVIDENDO",  2, 2026, None, None,  122.00),
        (4, "DIVIDENDO",  3, 2026, None, None,  122.00),
        (4, "DIVIDENDO",  4, 2026, None, None,  122.00),
        # VALE3 — compras
        (5, "COMPRA",  3, 2024, 100.0, 65.00,  6500.00),
        (5, "COMPRA",  8, 2024,  50.0, 58.00,  2900.00),
        (5, "COMPRA", 11, 2025,  80.0, 62.00,  4960.00),
        # VALE3 — dividendos (semestrais)
        (5, "DIVIDENDO",  5, 2024, None, None,  350.00),
        (5, "DIVIDENDO", 11, 2024, None, None,  280.00),
        (5, "DIVIDENDO",  5, 2025, None, None,  450.00),
        (5, "DIVIDENDO", 11, 2025, None, None,  380.00),
        (5, "DIVIDENDO",  3, 2026, None, None,  280.00),
    ]
)

# ── VAC Mensal ────────────────────────────────────────────────────────────
vac_hglg = [
    # 2024
    (4,  1, 2024, 145.00), (4,  2, 2024, 147.00), (4,  3, 2024, 146.50),
    (4,  4, 2024, 148.00), (4,  5, 2024, 148.00), (4,  6, 2024, 150.00),
    (4,  7, 2024, 151.00), (4,  8, 2024, 149.50), (4,  9, 2024, 152.00),
    (4, 10, 2024, 153.00), (4, 11, 2024, 155.00), (4, 12, 2024, 156.50),
    # 2025
    (4,  1, 2025, 155.00), (4,  2, 2025, 157.00), (4,  3, 2025, 152.00),
    (4,  4, 2025, 154.00), (4,  5, 2025, 156.00), (4,  6, 2025, 158.00),
    (4,  7, 2025, 159.00), (4,  8, 2025, 160.00), (4,  9, 2025, 158.50),
    (4, 10, 2025, 162.00), (4, 11, 2025, 164.00), (4, 12, 2025, 165.00),
    # 2026  (Mai pendente — alerta)
    (4,  1, 2026, 163.00), (4,  2, 2026, 166.00),
    (4,  3, 2026, 167.00), (4,  4, 2026, 168.50),
]

vac_vale = [
    # 2024 (começa Mar)
    (5,  3, 2024, 65.00), (5,  4, 2024, 63.00), (5,  5, 2024, 66.00),
    (5,  6, 2024, 64.50), (5,  7, 2024, 62.00), (5,  8, 2024, 58.00),
    (5,  9, 2024, 57.00), (5, 10, 2024, 59.50), (5, 11, 2024, 61.00),
    (5, 12, 2024, 63.00),
    # 2025
    (5,  1, 2025, 60.00), (5,  2, 2025, 62.50), (5,  3, 2025, 64.00),
    (5,  4, 2025, 65.00), (5,  5, 2025, 66.50), (5,  6, 2025, 68.00),
    (5,  7, 2025, 67.00), (5,  8, 2025, 65.00), (5,  9, 2025, 66.50),
    (5, 10, 2025, 68.00), (5, 11, 2025, 62.00), (5, 12, 2025, 63.00),
    # 2026
    (5,  1, 2026, 61.00), (5,  2, 2026, 63.50),
    (5,  3, 2026, 65.00), (5,  4, 2026, 66.00),
]

c.executemany(
    "INSERT INTO vac_mensal (investimento_id, periodo_mes, periodo_ano, vac, fonte) "
    "VALUES (?,?,?,?,'MANUAL')",
    vac_hglg + vac_vale
)

# ── Cotações Dólar ────────────────────────────────────────────────────────
c.executemany(
    "INSERT OR IGNORE INTO cotacoes_dolar (data, valor_compra, valor_venda, fonte) VALUES (?,?,?,'AwesomeAPI')",
    [
        ("2026-05-29", 5.82, 5.86),
        ("2026-05-28", 5.80, 5.84),
        ("2026-01-15", 5.96, 5.98),
        ("2025-08-15", 5.72, 5.74),
        ("2025-03-15", 5.87, 5.89),
        ("2024-10-15", 5.48, 5.50),
        ("2024-05-15", 5.15, 5.17),
        ("2024-02-15", 4.97, 4.99),
    ]
)

# ── Gastos ────────────────────────────────────────────────────────────────
# Mensalidades (tipo=MENSALIDADE; periodo_mes/ano = início; fim_*=NULL=ativa)
c.executemany(
    "INSERT INTO gastos (tipo, descricao, periodo_mes, periodo_ano, valor) VALUES (?,?,?,?,?)",
    [
        ("MENSALIDADE", "NETFLIX",  1, 2024,  25.90),
        ("MENSALIDADE", "ACADEMIA", 2, 2024,  99.90),
        ("MENSALIDADE", "SPOTIFY",  6, 2025,  21.90),
    ]
)

# Alimentar 2024 + 2025 + 2026
alimentar = [
    # 2024
    ("ALIMENTAR","SUPERMERCADO", 1,2024,450), ("ALIMENTAR","SUPERMERCADO", 2,2024,420),
    ("ALIMENTAR","RESTAURANTE",  2,2024,180), ("ALIMENTAR","SUPERMERCADO", 3,2024,470),
    ("ALIMENTAR","SUPERMERCADO", 4,2024,430), ("ALIMENTAR","IFOOD",        4,2024,120),
    ("ALIMENTAR","SUPERMERCADO", 5,2024,460), ("ALIMENTAR","SUPERMERCADO", 6,2024,440),
    ("ALIMENTAR","RESTAURANTE",  6,2024,200), ("ALIMENTAR","SUPERMERCADO", 7,2024,455),
    ("ALIMENTAR","SUPERMERCADO", 8,2024,470), ("ALIMENTAR","SUPERMERCADO", 9,2024,480),
    ("ALIMENTAR","SUPERMERCADO",10,2024,490), ("ALIMENTAR","RESTAURANTE", 10,2024,150),
    ("ALIMENTAR","SUPERMERCADO",11,2024,510), ("ALIMENTAR","SUPERMERCADO",12,2024,550),
    ("ALIMENTAR","RESTAURANTE", 12,2024,220),
    # 2025
    ("ALIMENTAR","SUPERMERCADO", 1,2025,520), ("ALIMENTAR","SUPERMERCADO", 2,2025,500),
    ("ALIMENTAR","SUPERMERCADO", 3,2025,530), ("ALIMENTAR","RESTAURANTE",  3,2025,190),
    ("ALIMENTAR","SUPERMERCADO", 4,2025,510), ("ALIMENTAR","SUPERMERCADO", 5,2025,480),
    ("ALIMENTAR","IFOOD",        5,2025,145), ("ALIMENTAR","SUPERMERCADO", 6,2025,490),
    ("ALIMENTAR","SUPERMERCADO", 7,2025,505), ("ALIMENTAR","SUPERMERCADO", 8,2025,515),
    ("ALIMENTAR","RESTAURANTE",  8,2025,210), ("ALIMENTAR","SUPERMERCADO", 9,2025,495),
    ("ALIMENTAR","SUPERMERCADO",10,2025,520), ("ALIMENTAR","SUPERMERCADO",11,2025,540),
    ("ALIMENTAR","SUPERMERCADO",12,2025,560), ("ALIMENTAR","RESTAURANTE", 12,2025,250),
    # 2026
    ("ALIMENTAR","SUPERMERCADO", 1,2026,530), ("ALIMENTAR","SUPERMERCADO", 2,2026,515),
    ("ALIMENTAR","SUPERMERCADO", 3,2026,540), ("ALIMENTAR","RESTAURANTE",  3,2026,195),
    ("ALIMENTAR","SUPERMERCADO", 4,2026,525),
]

diverso = [
    # 2024
    ("DIVERSO","COMBUSTIVEL", 1,2024,180), ("DIVERSO","COMBUSTIVEL", 2,2024,175),
    ("DIVERSO","ROUPAS",      3,2024,280), ("DIVERSO","COMBUSTIVEL", 3,2024,190),
    ("DIVERSO","COMBUSTIVEL", 4,2024,185), ("DIVERSO","COMBUSTIVEL", 5,2024,195),
    ("DIVERSO","COMBUSTIVEL", 6,2024,200), ("DIVERSO","NOTEBOOK",    7,2024,3800),
    ("DIVERSO","COMBUSTIVEL", 7,2024,210), ("DIVERSO","COMBUSTIVEL", 8,2024,205),
    ("DIVERSO","COMBUSTIVEL", 9,2024,215), ("DIVERSO","ROUPAS",     10,2024,320),
    ("DIVERSO","COMBUSTIVEL",10,2024,220), ("DIVERSO","COMBUSTIVEL",11,2024,225),
    ("DIVERSO","COMBUSTIVEL",12,2024,230), ("DIVERSO","PRESENTES",  12,2024,450),
    # 2025
    ("DIVERSO","COMBUSTIVEL", 1,2025,235), ("DIVERSO","COMBUSTIVEL", 2,2025,230),
    ("DIVERSO","ROUPAS",      3,2025,350), ("DIVERSO","COMBUSTIVEL", 3,2025,240),
    ("DIVERSO","COMBUSTIVEL", 4,2025,245), ("DIVERSO","COMBUSTIVEL", 5,2025,250),
    ("DIVERSO","SMARTPHONE",  6,2025,2800),("DIVERSO","COMBUSTIVEL", 6,2025,240),
    ("DIVERSO","COMBUSTIVEL", 7,2025,255), ("DIVERSO","COMBUSTIVEL", 8,2025,260),
    ("DIVERSO","COMBUSTIVEL", 9,2025,250), ("DIVERSO","ROUPAS",     10,2025,290),
    ("DIVERSO","COMBUSTIVEL",10,2025,265), ("DIVERSO","COMBUSTIVEL",11,2025,270),
    ("DIVERSO","COMBUSTIVEL",12,2025,275), ("DIVERSO","PRESENTES",  12,2025,520),
    # 2026
    ("DIVERSO","COMBUSTIVEL", 1,2026,280), ("DIVERSO","COMBUSTIVEL", 2,2026,275),
    ("DIVERSO","COMBUSTIVEL", 3,2026,285), ("DIVERSO","ROUPAS",      4,2026,310),
    ("DIVERSO","COMBUSTIVEL", 4,2026,290),
]

c.executemany(
    "INSERT INTO gastos (tipo, descricao, periodo_mes, periodo_ano, valor) VALUES (?,?,?,?,?)",
    alimentar + diverso
)

# ── Configurações ─────────────────────────────────────────────────────────
c.execute("INSERT OR IGNORE INTO configuracoes (chave, valor) VALUES ('sidebar_expandida','1')")
c.execute("INSERT OR IGNORE INTO configuracoes (chave, valor) VALUES ('taxa_cdi_referencia','10.5')")
c.execute("INSERT OR IGNORE INTO configuracoes (chave, valor) VALUES ('meta_patrimonio','200000')")
c.execute("INSERT OR IGNORE INTO configuracoes (chave, valor) VALUES ('meta_aporte_mensal','5000')")

conn.commit()
conn.close()

print("✓ Seed concluído:")
print("  6 investimentos (3 RF · 2 RV · 1 Dólar)")
print("  Histórico 2024–2026 (Abr), Mai/2026 sem VTA/VAC → alertas ativos")
print("  Gastos: 3 mensalidades + alimentar + diversos 2024–2026")
