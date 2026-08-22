package com.dethbraz.apurador.fixture;

import com.dethbraz.apurador.dominio.Anexo;
import com.dethbraz.apurador.dominio.Competencia;
import com.dethbraz.apurador.dominio.FaixaTabela;
import com.dethbraz.apurador.dominio.Receita;
import com.dethbraz.apurador.dominio.TabelaAnexoRepositorio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * ============================================================================
 * ATENCAO: OS NUMEROS DESTE ARQUIVO SAO FICTICIOS.
 *
 * Nao sao as tabelas do Simples Nacional. Sao valores inventados, redondos de
 * proposito, para que os testes sejam conferiveis a mao e para que ninguem os
 * confunda com legislacao.
 *
 * As tabelas reais precisam ser extraidas da LC 123/2006 e do site da Receita
 * Federal, com a vigencia de cada versao registrada junto. Esse levantamento e
 * a Fase 0 do projeto.
 *
 * Este arquivo vive em src/test justamente para que dado ficticio nunca seja
 * empacotado no artefato de producao.
 * ============================================================================
 *
 * As faixas foram montadas com continuidade na fronteira: a parcela a deduzir
 * de cada faixa e calculada como PD(n) = PD(n-1) + limite(n-1) x (aliq(n) -
 * aliq(n-1)). O efeito e que a aliquota efetiva NAO da salto ao cruzar a faixa -
 * que e exatamente o proposito da parcela a deduzir na lei real.
 *
 *   Faixa 1:        0,01 ate 100.000,00  -  4%  -  PD      0,00
 *   Faixa 2:  100.000,01 ate 200.000,00  -  6%  -  PD  2.000,00
 *   Faixa 3:  200.000,01 ate 300.000,00  -  8%  -  PD  6.000,00
 */
public class TabelasFicticias implements TabelaAnexoRepositorio {

    public static final LocalDate VIGENCIA_ANTIGA_INICIO = LocalDate.of(2020, 1, 1);
    public static final LocalDate VIGENCIA_ANTIGA_FIM = LocalDate.of(2025, 12, 31);
    public static final LocalDate VIGENCIA_NOVA_INICIO = LocalDate.of(2026, 1, 1);

    private final List<FaixaTabela> faixas;

    private TabelasFicticias(List<FaixaTabela> faixas) {
        this.faixas = faixas;
    }

    /** Uma unica versao de tabela, vigente por prazo indeterminado. */
    public static TabelasFicticias versaoUnica() {
        return new TabelasFicticias(tabelaPadrao(VIGENCIA_ANTIGA_INICIO, null, "1"));
    }

    /**
     * Duas versoes: a antiga valida ate 31/12/2025 e a nova a partir de
     * 01/01/2026, com aliquotas dobradas para a diferenca ficar obvia no teste.
     * Usada para provar que apuracao passada continua reproduzivel.
     */
    public static TabelasFicticias comDuasVersoes() {
        List<FaixaTabela> todas = new ArrayList<>();
        todas.addAll(tabelaPadrao(VIGENCIA_ANTIGA_INICIO, VIGENCIA_ANTIGA_FIM, "1"));
        todas.addAll(tabelaPadrao(VIGENCIA_NOVA_INICIO, null, "2"));
        return new TabelasFicticias(todas);
    }

    private static List<FaixaTabela> tabelaPadrao(LocalDate inicio, LocalDate fim, String multiplicador) {
        BigDecimal m = new BigDecimal(multiplicador);
        return Arrays.asList(
                faixa(1, "0.00", "100000.00", new BigDecimal("0.04").multiply(m),
                        new BigDecimal("0.00").multiply(m), inicio, fim),
                faixa(2, "100000.00", "200000.00", new BigDecimal("0.06").multiply(m),
                        new BigDecimal("2000.00").multiply(m), inicio, fim),
                faixa(3, "200000.00", "300000.00", new BigDecimal("0.08").multiply(m),
                        new BigDecimal("6000.00").multiply(m), inicio, fim));
    }

    private static FaixaTabela faixa(int numero, String inferior, String superior,
                                     BigDecimal aliquota, BigDecimal parcelaDeduzir,
                                     LocalDate inicio, LocalDate fim) {
        return new FaixaTabela(Anexo.ANEXO_I, numero,
                new BigDecimal(inferior), new BigDecimal(superior),
                aliquota, parcelaDeduzir, inicio, fim);
    }

    @Override
    public List<FaixaTabela> faixasVigentes(Anexo anexo, LocalDate data) {
        return faixas.stream()
                .filter(f -> f.anexo() == anexo)
                .filter(f -> f.vigenteEm(data))
                .toList();
    }

    // ---------- geradores de receita para os testes ----------

    /** 12 competencias anteriores a {@code apuracao}, todas com o mesmo valor. */
    public static List<Receita> dozeMesesDe(String valor, Competencia apuracao) {
        List<Receita> receitas = new ArrayList<>();
        for (Competencia c : apuracao.anteriores(12)) {
            receitas.add(new Receita(c, new BigDecimal(valor)));
        }
        return receitas;
    }

    /**
     * Receitas anteriores somando exatamente {@code totalDesejado}, mais a
     * receita do proprio mes apurado. Util para cravar um RBT12 especifico.
     */
    public static List<Receita> comRbt12Exato(String totalDesejado, String receitaDoMes,
                                              Competencia apuracao) {
        List<Receita> receitas = new ArrayList<>();
        List<Competencia> anteriores = apuracao.anteriores(12);
        receitas.add(new Receita(anteriores.get(0), new BigDecimal(totalDesejado)));
        for (int i = 1; i < anteriores.size(); i++) {
            receitas.add(new Receita(anteriores.get(i), BigDecimal.ZERO));
        }
        receitas.add(new Receita(apuracao, new BigDecimal(receitaDoMes)));
        return receitas;
    }
}
