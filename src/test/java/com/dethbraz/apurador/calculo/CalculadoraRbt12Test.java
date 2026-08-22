package com.dethbraz.apurador.calculo;

import com.dethbraz.apurador.dominio.Competencia;
import com.dethbraz.apurador.dominio.Receita;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CalculadoraRbt12Test {

    private final CalculadoraRbt12 calculadora = new CalculadoraRbt12();

    private static final Competencia MARCO_2025 = Competencia.de(2025, 3);

    @Test
    @DisplayName("A janela sao os 12 meses ANTERIORES - o mes apurado fica de fora")
    void naoIncluiOMesApurado() {
        // Erro comum: somar o mes corrente junto. Isso empurra a empresa para
        // uma faixa mais alta e infla o imposto.
        List<Receita> receitas = new ArrayList<>();
        for (Competencia c : MARCO_2025.anteriores(12)) {
            receitas.add(new Receita(c, new BigDecimal("10000.00")));
        }
        // O mes apurado tem faturamento alto, que NAO pode entrar no RBT12.
        receitas.add(new Receita(MARCO_2025, new BigDecimal("999999.00")));

        var resultado = calculadora.calcular(receitas, MARCO_2025, Competencia.de(2015, 1));

        assertThat(resultado.valor()).isEqualByComparingTo("120000.00");
        assertThat(resultado.proporcionalizado()).isFalse();
    }

    @Test
    @DisplayName("Empresa com menos de 12 meses tem o RBT12 proporcionalizado")
    void inicioDeAtividadeProporcionaliza() {
        // Inicio em janeiro/2025, apurando marco/2025: existem 2 meses
        // anteriores (jan e fev). Media x 12 = RBT12.
        // (10.000 + 10.000) / 2 x 12 = 120.000
        Competencia inicio = Competencia.de(2025, 1);
        List<Receita> receitas = List.of(
                new Receita(Competencia.de(2025, 1), new BigDecimal("10000.00")),
                new Receita(Competencia.de(2025, 2), new BigDecimal("10000.00")));

        var resultado = calculadora.calcular(receitas, MARCO_2025, inicio);

        assertThat(resultado.valor()).isEqualByComparingTo("120000.00");
        assertThat(resultado.proporcionalizado()).isTrue();
    }

    @Test
    @DisplayName("No primeiro mes de atividade projeta a receita do proprio mes")
    void primeiroMesDeAtividade() {
        // Nao ha mes anterior nenhum. A regra manda projetar o proprio mes:
        // 10.000 x 12 = 120.000
        List<Receita> receitas = List.of(
                new Receita(MARCO_2025, new BigDecimal("10000.00")));

        var resultado = calculadora.calcular(receitas, MARCO_2025, MARCO_2025);

        assertThat(resultado.valor()).isEqualByComparingTo("120000.00");
        assertThat(resultado.proporcionalizado()).isTrue();
    }

    @Test
    @DisplayName("Meses sem lancamento contam como zero, nao quebram a janela")
    void mesesFaltantesContamComoZero() {
        List<Receita> receitas = List.of(
                new Receita(Competencia.de(2025, 2), new BigDecimal("30000.00")));

        var resultado = calculadora.calcular(receitas, MARCO_2025, Competencia.de(2015, 1));

        assertThat(resultado.valor()).isEqualByComparingTo("30000.00");
    }

    @Test
    @DisplayName("Competencia anterior ao inicio de atividade e erro de entrada")
    void competenciaAnteriorAoInicioEInvalida() {
        assertThatThrownBy(() ->
                calculadora.calcular(List.of(), Competencia.de(2024, 1), Competencia.de(2025, 1)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("anterior ao inicio de atividade");
    }
}
