package com.dethbraz.apurador.calculo;

import com.dethbraz.apurador.dominio.Anexo;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Fator R - decide entre Anexo III e Anexo V para atividades de servico.
 *
 *     fatorR = folha de salarios dos 12 meses / RBT12
 *
 * Igual ou acima de 28%, a empresa apura pelo Anexo III (carga menor). Abaixo,
 * pelo Anexo V. A diferenca de imposto entre os dois anexos e substancial, o que
 * torna os 28% uma fronteira de alto risco: um centavo de folha a menos muda o
 * anexo inteiro.
 *
 * Por isso o limite e comparado com BigDecimal e existe teste cravado no valor
 * exato. Com double, 0.28 nao e representavel de forma exata e a comparacao no
 * limite se torna imprevisivel - o tipo de bug que so aparece em producao, no
 * caso de um cliente especifico.
 */
public class CalculadoraFatorR {

    /** 28% - o limite legal entre Anexo III e Anexo V. */
    public static final BigDecimal LIMITE = new BigDecimal("0.28");

    private static final int ESCALA = 10;

    public record Resultado(BigDecimal fatorR, Anexo anexo) {

        public BigDecimal percentual() {
            return fatorR.multiply(BigDecimal.valueOf(100));
        }
    }

    public Resultado calcular(BigDecimal folhaSalarios12Meses, BigDecimal rbt12) {
        if (folhaSalarios12Meses.signum() < 0) {
            throw new IllegalArgumentException("Folha de salarios nao pode ser negativa");
        }

        // Sem faturamento nos 12 meses o Fator R e indefinido (divisao por zero).
        // Convenciona-se fator zero, o que leva ao Anexo V - a alternativa
        // (assumir Anexo III) beneficiaria a empresa sem respaldo na apuracao.
        if (rbt12.signum() == 0) {
            return new Resultado(BigDecimal.ZERO.setScale(ESCALA), Anexo.ANEXO_V);
        }

        BigDecimal fatorR = folhaSalarios12Meses.divide(rbt12, ESCALA, RoundingMode.HALF_UP);

        // >= 28% cai no Anexo III. O igual PERTENCE ao Anexo III.
        Anexo anexo = fatorR.compareTo(LIMITE) >= 0 ? Anexo.ANEXO_III : Anexo.ANEXO_V;

        return new Resultado(fatorR, anexo);
    }
}
