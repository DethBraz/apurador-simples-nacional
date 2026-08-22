package com.dethbraz.apurador.calculo;

import com.dethbraz.apurador.dominio.Anexo;
import com.dethbraz.apurador.dominio.Competencia;
import com.dethbraz.apurador.dominio.FaixaTabela;
import com.dethbraz.apurador.dominio.MemoriaCalculo;
import com.dethbraz.apurador.dominio.Receita;
import com.dethbraz.apurador.dominio.TabelaAnexoRepositorio;
import com.dethbraz.apurador.dominio.Tributo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Calculo do DAS de uma competencia.
 *
 * A parte contraintuitiva - e a que vale explicar em entrevista - e que a
 * aliquota da tabela NAO e a aliquota que se paga. A tabela traz uma aliquota
 * nominal e uma parcela a deduzir, e a aliquota real sai da combinacao das duas:
 *
 *     aliquotaEfetiva = (RBT12 x aliquotaNominal - parcelaDeduzir) / RBT12
 *
 * O efeito e uma progressividade suave: sem a parcela a deduzir, cruzar o limite
 * de uma faixa provocaria um salto abrupto de imposto sobre todo o faturamento.
 * Com ela, so a parcela excedente e tributada mais pesado - mesma logica do
 * imposto de renda por faixas.
 *
 * Depois disso, a aliquota efetiva incide sobre a receita DO MES, nao sobre o
 * RBT12. O RBT12 serve para descobrir o tamanho da empresa; a base de calculo e
 * o faturamento do mes apurado.
 */
public class CalculadoraDas {

    /**
     * Casas decimais mantidas na aliquota efetiva antes de aplicar sobre a
     * receita. Alto de proposito: arredondar a aliquota cedo propaga erro para o
     * valor final. Arredonda-se so no fim, no valor monetario.
     */
    private static final int ESCALA_ALIQUOTA = 10;
    private static final int ESCALA_MONETARIA = 2;

    private final TabelaAnexoRepositorio tabelas;
    private final CalculadoraRbt12 calculadoraRbt12;

    public CalculadoraDas(TabelaAnexoRepositorio tabelas) {
        this(tabelas, new CalculadoraRbt12());
    }

    public CalculadoraDas(TabelaAnexoRepositorio tabelas, CalculadoraRbt12 calculadoraRbt12) {
        this.tabelas = tabelas;
        this.calculadoraRbt12 = calculadoraRbt12;
    }

    public MemoriaCalculo apurar(Anexo anexo,
                                 Competencia competencia,
                                 List<Receita> receitas,
                                 Competencia inicioAtividade) {

        BigDecimal receitaDoMes = receitaDe(receitas, competencia);

        CalculadoraRbt12.Resultado rbt12 =
                calculadoraRbt12.calcular(receitas, competencia, inicioAtividade);

        // A data da competencia - e nao a data de hoje - resolve a tabela.
        // E isso que mantem apuracoes passadas reproduziveis quando a
        // legislacao muda depois.
        LocalDate dataVigencia = competencia.primeiroDia();

        // Empresa sem faturamento nenhum: nao ha base, nao ha imposto. Tratado
        // explicitamente porque a formula da aliquota efetiva divide por RBT12,
        // e dividir por zero aqui quebraria a apuracao.
        if (rbt12.valor().signum() == 0) {
            return new MemoriaCalculo(
                    competencia, anexo, receitaDoMes, rbt12.valor(), rbt12.proporcionalizado(),
                    primeiraFaixa(anexo, dataVigencia),
                    BigDecimal.ZERO.setScale(ESCALA_ALIQUOTA),
                    BigDecimal.ZERO.setScale(ESCALA_MONETARIA),
                    Map.of());
        }

        FaixaTabela faixa = tabelas.faixaPara(anexo, rbt12.valor(), dataVigencia)
                .orElseThrow(() -> new DesenquadramentoException(anexo, rbt12.valor()));

        BigDecimal aliquotaEfetiva = aliquotaEfetiva(rbt12.valor(), faixa);

        BigDecimal das = receitaDoMes.multiply(aliquotaEfetiva)
                .setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);

        // A reparticao e calculada a partir do DAS ja arredondado, e nao do
        // valor bruto. Assim a soma dos tributos fecha com o valor que sera
        // efetivamente recolhido - e nao com um intermediario que ninguem paga.
        Map<Tributo, BigDecimal> porTributo = faixa.reparticao().distribuir(das);

        return new MemoriaCalculo(
                competencia, anexo, receitaDoMes, rbt12.valor(), rbt12.proporcionalizado(),
                faixa, aliquotaEfetiva, das, porTributo);
    }

    /** (RBT12 x aliquotaNominal - parcelaDeduzir) / RBT12 */
    private BigDecimal aliquotaEfetiva(BigDecimal rbt12, FaixaTabela faixa) {
        BigDecimal numerador = rbt12.multiply(faixa.aliquotaNominal())
                .subtract(faixa.parcelaDeduzir());
        return numerador.divide(rbt12, ESCALA_ALIQUOTA, RoundingMode.HALF_UP);
    }

    private BigDecimal receitaDe(List<Receita> receitas, Competencia competencia) {
        Map<Competencia, BigDecimal> porCompetencia = receitas.stream()
                .collect(Collectors.toMap(Receita::competencia, Receita::valor, BigDecimal::add));
        return porCompetencia.getOrDefault(competencia, BigDecimal.ZERO)
                .setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
    }

    private FaixaTabela primeiraFaixa(Anexo anexo, LocalDate data) {
        return tabelas.faixasVigentes(anexo, data).stream()
                .min((a, b) -> Integer.compare(a.faixa(), b.faixa()))
                .orElseThrow(() -> new IllegalStateException(
                        "Nenhuma tabela vigente para " + anexo + " em " + data));
    }
}
