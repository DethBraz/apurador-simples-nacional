package com.dethbraz.apurador.api.dto;

import com.dethbraz.apurador.dominio.Anexo;
import com.dethbraz.apurador.dominio.MemoriaCalculo;
import com.dethbraz.apurador.dominio.Tributo;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * A resposta da apuracao devolve a CONTA ABERTA, nao so o valor do DAS.
 *
 * Essa e a decisao de produto mais importante da API. Contador nao aceita numero
 * sem procedencia: ele quer conferir o RBT12, a faixa, a aliquota nominal, a
 * parcela deduzida e a aliquota efetiva. E quer saber qual versao da tabela foi
 * aplicada - por isso o bloco tabelaVigenteDesde/Ate.
 *
 * Uma API que devolvesse apenas {"das": 433.33} seria tecnicamente correta e
 * inutilizavel num escritorio de contabilidade.
 */
public record ApuracaoResponse(
        String competencia,
        Anexo anexo,
        BigDecimal receitaBrutaMes,
        BigDecimal rbt12,
        boolean rbt12Proporcionalizado,
        MemoriaResponse memoriaCalculo,
        BigDecimal valorDas,
        ReparticaoResponse reparticao) {

    /**
     * Composicao do DAS por tributo.
     *
     * O campo {@code soma} nao e redundante: ele existe para quem confere poder
     * verificar, sem somar a mao, que a reparticao fecha exatamente com o DAS.
     * Se algum dia divergir, o erro fica visivel na propria resposta.
     */
    public record ReparticaoResponse(
            Map<Tributo, BigDecimal> valores,
            BigDecimal soma,
            boolean confereComODas) {
    }

    public record MemoriaResponse(
            int faixa,
            BigDecimal limiteInferiorFaixa,
            BigDecimal limiteSuperiorFaixa,
            BigDecimal aliquotaNominal,
            BigDecimal parcelaDeduzir,
            BigDecimal aliquotaEfetiva,
            BigDecimal aliquotaEfetivaPercentual,
            String formula,
            LocalDate tabelaVigenteDesde,
            LocalDate tabelaVigenteAte) {
    }

    public static ApuracaoResponse de(MemoriaCalculo m) {
        var faixa = m.faixaAplicada();

        String formula = "((%s x %s) - %s) / %s = %s".formatted(
                m.rbt12(), faixa.aliquotaNominal(), faixa.parcelaDeduzir(),
                m.rbt12(), m.aliquotaEfetiva());

        var memoria = new MemoriaResponse(
                m.faixa(),
                faixa.limiteInferior(),
                faixa.limiteSuperior(),
                faixa.aliquotaNominal(),
                faixa.parcelaDeduzir(),
                m.aliquotaEfetiva(),
                m.aliquotaEfetivaPercentual(),
                formula,
                faixa.vigenciaInicio(),
                faixa.vigenciaFim());

        ReparticaoResponse reparticao = null;
        if (m.temReparticao()) {
            BigDecimal soma = m.somaDosTributos();
            reparticao = new ReparticaoResponse(
                    m.valoresPorTributo(),
                    soma,
                    soma.compareTo(m.valorDas()) == 0);
        }

        return new ApuracaoResponse(
                m.competencia().toString(),
                m.anexo(),
                m.receitaBrutaMes(),
                m.rbt12(),
                m.rbt12Proporcionalizado(),
                memoria,
                m.valorDas(),
                reparticao);
    }
}
