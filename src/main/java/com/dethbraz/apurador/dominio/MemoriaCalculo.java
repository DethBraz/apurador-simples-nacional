package com.dethbraz.apurador.dominio;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Resultado de uma apuracao, com a conta aberta.
 *
 * Devolver apenas o valor do DAS seria tecnicamente suficiente e praticamente
 * inutil. Contador nao confia em numero solto: ele quer conferir de onde saiu.
 * Por isso o retorno carrega todos os intermediarios - RBT12, faixa, aliquota
 * nominal, parcela deduzida e aliquota efetiva.
 *
 * O campo {@code faixaAplicada} guarda a propria linha da tabela usada,
 * incluindo a vigencia. E o que torna a apuracao auditavel: da para provar qual
 * versao da legislacao gerou aquele numero.
 */
public record MemoriaCalculo(
        Competencia competencia,
        Anexo anexo,
        BigDecimal receitaBrutaMes,
        BigDecimal rbt12,
        boolean rbt12Proporcionalizado,
        FaixaTabela faixaAplicada,
        BigDecimal aliquotaEfetiva,
        BigDecimal valorDas,
        Map<Tributo, BigDecimal> valoresPorTributo
) {

    public MemoriaCalculo {
        // EnumMap nao aceita construtor de copia com mapa vazio nao-EnumMap.
        Map<Tributo, BigDecimal> copia = new EnumMap<>(Tributo.class);
        if (valoresPorTributo != null) {
            copia.putAll(valoresPorTributo);
        }
        valoresPorTributo = Collections.unmodifiableMap(copia);
    }

    /** A soma dos tributos - deve ser identica ao valor do DAS. */
    public BigDecimal somaDosTributos() {
        return valoresPorTributo.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public boolean temReparticao() {
        return !valoresPorTributo.isEmpty();
    }

    /** Numero da faixa (1 a 6), atalho de leitura. */
    public int faixa() {
        return faixaAplicada.faixa();
    }

    /** Aliquota efetiva em pontos percentuais, para exibicao (ex.: 6.5400). */
    public BigDecimal aliquotaEfetivaPercentual() {
        return aliquotaEfetiva.multiply(BigDecimal.valueOf(100));
    }

    @Override
    public String toString() {
        return """
                Apuracao %s | %s faixa %d
                  Receita do mes ....... %s
                  RBT12 ................ %s%s
                  Aliquota nominal ..... %s
                  Parcela a deduzir .... %s
                  Aliquota efetiva ..... %s%%
                  DAS .................. %s
                  Tabela vigente desde . %s%s"""
                .formatted(
                        competencia, anexo, faixa(),
                        receitaBrutaMes,
                        rbt12, rbt12Proporcionalizado ? " (proporcionalizado)" : "",
                        faixaAplicada.aliquotaNominal(),
                        faixaAplicada.parcelaDeduzir(),
                        aliquotaEfetivaPercentual(),
                        valorDas,
                        faixaAplicada.vigenciaInicio(),
                        reparticaoFormatada());
    }

    private String reparticaoFormatada() {
        if (!temReparticao()) {
            return "";
        }
        StringBuilder sb = new StringBuilder("\n  Reparticao:");
        valoresPorTributo.forEach((tributo, valor) ->
                sb.append("\n    %-10s %s".formatted(tributo, valor)));
        sb.append("\n    %-10s %s".formatted("SOMA", somaDosTributos()));
        return sb.toString();
    }
}
