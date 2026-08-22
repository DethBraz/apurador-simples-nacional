package com.dethbraz.apurador.dominio;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Faturamento de uma competencia.
 *
 * BigDecimal, nunca double. Valor monetario em ponto flutuante acumula erro de
 * arredondamento, e em calculo de imposto isso vira divergencia de centavos que
 * o contador enxerga na conferencia.
 */
public record Receita(Competencia competencia, BigDecimal valor) {

    public Receita {
        Objects.requireNonNull(competencia, "competencia");
        Objects.requireNonNull(valor, "valor");
        if (valor.signum() < 0) {
            throw new IllegalArgumentException("Receita nao pode ser negativa: " + valor);
        }
    }

    public static Receita de(int ano, int mes, String valor) {
        return new Receita(Competencia.de(ano, mes), new BigDecimal(valor));
    }
}
