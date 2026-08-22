package com.dethbraz.apurador.calculo;

import com.dethbraz.apurador.dominio.Competencia;
import com.dethbraz.apurador.dominio.Receita;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Receita Bruta Total dos 12 meses anteriores.
 *
 * Detalhe que quase todo mundo erra: o RBT12 NAO inclui o mes que esta sendo
 * apurado. A janela sao os 12 meses ANTERIORES. Como o RBT12 e o que define a
 * faixa, incluir o mes corrente empurra a empresa para faixa mais alta e infla
 * o imposto.
 *
 * O segundo detalhe e a empresa em inicio de atividade: sem 12 meses de
 * historico nao existe janela completa, e a lei manda proporcionalizar - usa-se
 * a media dos meses ja decorridos multiplicada por 12.
 */
public class CalculadoraRbt12 {

    private static final int MESES_JANELA = 12;
    private static final int ESCALA_MONETARIA = 2;

    /**
     * @param valor           o RBT12 apurado
     * @param proporcionalizado true quando a empresa ainda nao tinha 12 meses de
     *                          atividade e o valor veio de media x 12
     */
    public record Resultado(BigDecimal valor, boolean proporcionalizado) {}

    /**
     * @param receitas        faturamento por competencia (a ordem nao importa)
     * @param apuracao        competencia sendo apurada
     * @param inicioAtividade competencia de inicio de atividade da empresa
     */
    public Resultado calcular(List<Receita> receitas, Competencia apuracao, Competencia inicioAtividade) {
        Map<Competencia, BigDecimal> porCompetencia = receitas.stream()
                .collect(Collectors.toMap(Receita::competencia, Receita::valor, BigDecimal::add));

        int mesesDecorridos = apuracao.mesesDesde(inicioAtividade);

        if (mesesDecorridos < 0) {
            throw new IllegalArgumentException(
                    "Competencia apurada (" + apuracao + ") e anterior ao inicio de atividade ("
                            + inicioAtividade + ")");
        }

        // Primeiro mes de atividade: nao ha nenhum mes anterior. A regra manda
        // usar a receita do proprio mes projetada para 12 meses.
        if (mesesDecorridos == 0) {
            BigDecimal receitaDoMes = porCompetencia.getOrDefault(apuracao, BigDecimal.ZERO);
            return new Resultado(escalar(receitaDoMes.multiply(BigDecimal.valueOf(MESES_JANELA))), true);
        }

        int mesesConsiderados = Math.min(mesesDecorridos, MESES_JANELA);

        BigDecimal soma = apuracao.anteriores(mesesConsiderados).stream()
                .map(c -> porCompetencia.getOrDefault(c, BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Janela completa: o RBT12 e a soma direta.
        if (mesesConsiderados == MESES_JANELA) {
            return new Resultado(escalar(soma), false);
        }

        // Janela incompleta: media dos meses decorridos projetada para 12.
        BigDecimal media = soma.divide(BigDecimal.valueOf(mesesConsiderados), 10, RoundingMode.HALF_UP);
        return new Resultado(escalar(media.multiply(BigDecimal.valueOf(MESES_JANELA))), true);
    }

    private BigDecimal escalar(BigDecimal valor) {
        return valor.setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);
    }
}
