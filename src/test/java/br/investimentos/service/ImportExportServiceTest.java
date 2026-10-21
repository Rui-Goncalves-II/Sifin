package br.investimentos.service;

import br.investimentos.db.DatabaseManager;
import br.investimentos.model.*;
import br.investimentos.model.enums.*;
import br.investimentos.repository.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ImportExportServiceTest {

    private static Path home;

    private InvestimentoRepository invRepo;
    private MovimentacaoRepository movRepo;
    private VtaMensalRepository vtaRepo;
    private GastoRepository gastoRepo;
    private CotacaoRepository cotRepo;
    private ConfigService configSvc;
    private ImportExportService svc;

    @BeforeAll
    static void initDb() throws Exception {
        home = Files.createTempDirectory("sifin-test");
        System.setProperty("app.home", home.toString());
    }

    @BeforeEach
    void setUp() {
        DatabaseManager.getInstance().limparBancoDados();
        invRepo = new InvestimentoRepository();
        movRepo = new MovimentacaoRepository();
        vtaRepo = new VtaMensalRepository();
        gastoRepo = new GastoRepository();
        cotRepo = new CotacaoRepository();
        configSvc = new ConfigService();
        svc = new ImportExportService(invRepo, movRepo, vtaRepo, new VaiAnualRepository(),
                new AporteRvRepository(), new VacMensalRepository(), gastoRepo, cotRepo, configSvc);
    }

    @Test
    void exportarERestaurar_preservaDiaNotasMultilinhaConfigsECotacoes() throws Exception {
        Investimento inv = new Investimento();
        inv.setNome("CDB Teste");
        inv.setTipo(TipoInvestimento.RENDA_FIXA);
        inv.setMoeda("BRL");
        inv.setAtivo(true);
        invRepo.salvar(inv);

        vtaRepo.salvar(vta(inv.getId(), 10, 1000.0));
        vtaRepo.salvar(vta(inv.getId(), 25, 1010.0));

        Movimentacao m = new Movimentacao();
        m.setInvestimentoId(inv.getId());
        m.setPeriodoDia(15); m.setPeriodoMes(3); m.setPeriodoAno(2026);
        m.setTipoMov(TipoMovimentacao.DEPOSITO);
        m.setValor(500.0);
        m.setNotas("linha 1\nlinha 2, com \"aspas\"");
        movRepo.salvar(m);

        Gasto g = new Gasto();
        g.setTipo(TipoGasto.ALIMENTAR);
        g.setDescricao("Mercado");
        g.setPeriodoDia(7); g.setPeriodoMes(3); g.setPeriodoAno(2026);
        g.setValor(120.0);
        gastoRepo.salvar(g);

        configSvc.set("brapi_token", "abc123");
        CotacaoDolar c = new CotacaoDolar();
        c.setData("2026-03-15"); c.setValorCompra(5.1); c.setValorVenda(5.2); c.setFonte("AwesomeAPI");
        cotRepo.salvar(c);

        File csv = home.resolve("backup.csv").toFile();
        svc.exportar(csv);

        ImportExportService.ImportResult res = svc.restaurar(svc.ler(csv));
        assertEquals(0, res.ignoradas());
        assertNotNull(res.backup());
        assertTrue(res.backup().exists());

        List<Investimento> invs = invRepo.findAllInclArquivados();
        assertEquals(1, invs.size());
        int id = invs.get(0).getId();

        List<VtaMensal> vtas = vtaRepo.findByInvestimento(id);
        vtas.sort(Comparator.comparingInt(VtaMensal::getPeriodoDia));
        assertEquals(2, vtas.size());
        assertEquals(10, vtas.get(0).getPeriodoDia());
        assertEquals(1000.0, vtas.get(0).getVta(), 1e-9);
        assertEquals(25, vtas.get(1).getPeriodoDia());
        assertEquals(1010.0, vtas.get(1).getVta(), 1e-9);

        List<Movimentacao> movs = movRepo.findByInvestimento(id);
        assertEquals(1, movs.size());
        assertEquals(15, movs.get(0).getPeriodoDia());
        assertEquals("linha 1\nlinha 2, com \"aspas\"", movs.get(0).getNotas());

        List<Gasto> gastos = gastoRepo.findAll();
        assertEquals(1, gastos.size());
        assertEquals(7, gastos.get(0).getPeriodoDia());

        assertEquals("abc123", configSvc.getBrapiToken());
        assertTrue(cotRepo.findByData("2026-03-15").isPresent());
        assertEquals(5.2, cotRepo.findByData("2026-03-15").get().getValorVenda(), 1e-9);
    }

    @Test
    void importar_csvFormatoAntigo_assumeDiaUm() throws Exception {
        String header = "record_type,nome_ativo,tipo_ativo,subtipo,indexador,taxa_anual,data_vencimento,moeda," +
                "ativo,notas_ativo,periodo_mes,periodo_ano,tipo_mov,valor,cotacao_dolar,notas," +
                "tipo_op,quantidade,preco_por_cota,vta,vac,fonte_vac,ano_vai,vai," +
                "gasto_tipo,gasto_descricao,gasto_fim_mes,gasto_fim_ano,gasto_parcelas_total,gasto_parcela_numero,gasto_grupo_parcela";
        String inv = "INVESTIMENTO,LCI Antiga,RENDA_FIXA,,,,,BRL,1,,,,,,,,,,,,,,,,,,,,,,";
        String mov = "MOVIMENTACAO,LCI Antiga,,,,,,,,,4,2025,DEPOSITO,300.0,,,,,,,,,,,,,,,,,";
        File csv = home.resolve("antigo.csv").toFile();
        Files.writeString(csv.toPath(), String.join("\r\n", header, inv, mov) + "\r\n", StandardCharsets.UTF_8);

        ImportExportService.ImportResult res = svc.importar(csv);
        assertEquals(1, res.ativosNovos());
        assertEquals(1, res.movimentacoes());

        int id = invRepo.findAllInclArquivados().get(0).getId();
        assertEquals(1, movRepo.findByInvestimento(id).get(0).getPeriodoDia());
    }

    @Test
    void importar_csvComDiaZero_assumeDiaUm() throws Exception {
        String header = "record_type,nome_ativo,tipo_ativo,subtipo,indexador,taxa_anual,data_vencimento,moeda," +
                "ativo,notas_ativo,periodo_mes,periodo_ano,tipo_mov,valor,cotacao_dolar,notas," +
                "tipo_op,quantidade,preco_por_cota,vta,vac,fonte_vac,ano_vai,vai," +
                "gasto_tipo,gasto_descricao,gasto_fim_mes,gasto_fim_ano,gasto_parcelas_total,gasto_parcela_numero,gasto_grupo_parcela," +
                "periodo_dia";
        String inv = "INVESTIMENTO,CDB Zero,RENDA_FIXA,,,,,BRL,1,,,,,,,,,,,,,,,,,,,,,,,";
        String mov = "MOVIMENTACAO,CDB Zero,,,,,,,,,9,2026,DEPOSITO,200.0,,,,,,,,,,,,,,,,,,0";
        String vta = "VTA_MENSAL,CDB Zero,,,,,,,,,9,2026,,,,,,,,210.0,,,,,,,,,,,,0";
        File csv = home.resolve("dia-zero.csv").toFile();
        Files.writeString(csv.toPath(), String.join("\r\n", header, inv, mov, vta) + "\r\n", StandardCharsets.UTF_8);

        svc.importar(csv);

        int id = invRepo.findAllInclArquivados().get(0).getId();
        assertEquals(1, movRepo.findByInvestimento(id).get(0).getPeriodoDia());
        assertEquals(1, vtaRepo.findByInvestimento(id).get(0).getPeriodoDia());
    }

    @Test
    void ler_registroInvalido_naoTocaNoBanco() throws Exception {
        Investimento inv = new Investimento();
        inv.setNome("Existente");
        inv.setTipo(TipoInvestimento.RENDA_FIXA);
        inv.setMoeda("BRL");
        inv.setAtivo(true);
        invRepo.salvar(inv);

        File csv = home.resolve("ruim.csv").toFile();
        svc.exportar(csv);
        Files.writeString(csv.toPath(),
                "INVESTIMENTO,X,TIPO_INEXISTENTE,,,,,BRL,1,,,,,,,,,,,,,,,,,,,,,,,\n",
                StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.APPEND);

        assertThrows(java.io.IOException.class, () -> svc.ler(csv));
        assertEquals(1, invRepo.findAllInclArquivados().size());
    }

    private static VtaMensal vta(int invId, int dia, double valor) {
        VtaMensal v = new VtaMensal();
        v.setInvestimentoId(invId);
        v.setPeriodoDia(dia); v.setPeriodoMes(3); v.setPeriodoAno(2026);
        v.setVta(valor);
        return v;
    }
}
