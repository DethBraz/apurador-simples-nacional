package com.dethbraz.apurador.calculo;

import com.dethbraz.apurador.dominio.Anexo;

import java.math.BigDecimal;

/**
 * RBT12 acima do teto do Simples Nacional - nenhuma faixa cobre o valor.
 *
 * E excecao, e nao retorno nulo ou zero, porque desenquadramento e um evento de
 * negocio que exige decisao humana. Devolver um DAS calculado "na ultima faixa"
 * esconderia o problema justamente de quem precisa ve-lo.
 */
public class DesenquadramentoException extends RuntimeException {

    private final transient BigDecimal rbt12;

    public DesenquadramentoException(Anexo anexo, BigDecimal rbt12) {
        super("RBT12 de " + rbt12 + " nao se enquadra em nenhuma faixa vigente do "
                + anexo + ". Possivel desenquadramento do Simples Nacional.");
        this.rbt12 = rbt12;
    }

    public BigDecimal rbt12() {
        return rbt12;
    }
}
