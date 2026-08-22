package com.dethbraz.apurador.calculo;

import com.dethbraz.apurador.dominio.Anexo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class CalculadoraFatorRTest {

    private final CalculadoraFatorR calculadora = new CalculadoraFatorR();

    @Test
    @DisplayName("Fator R em 28% cravados cai no Anexo III - o igual pertence ao III")
    void limiteExatoVaiParaAnexoIII() {
        // Fronteira de alto risco: a diferenca de carga entre III e V e grande.
        // 28.000 / 100.000 = 0,28 exatos.
        var resultado = calculadora.calcular(
                new BigDecimal("28000.00"), new BigDecimal("100000.00"));

        assertThat(resultado.fatorR()).isEqualByComparingTo("0.28");
        assertThat(resultado.anexo()).isEqualTo(Anexo.ANEXO_III);
    }

    @Test
    @DisplayName("Um centavo abaixo do limite muda o anexo inteiro")
    void umCentavoAbaixoVaiParaAnexoV() {
        var resultado = calculadora.calcular(
                new BigDecimal("27999.99"), new BigDecimal("100000.00"));

        assertThat(resultado.anexo()).isEqualTo(Anexo.ANEXO_V);
    }

    @ParameterizedTest(name = "folha {0} sobre RBT12 {1} -> {2}")
    @CsvSource({
            "28000.00, 100000.00, ANEXO_III",
            "27999.99, 100000.00, ANEXO_V",
            "50000.00, 100000.00, ANEXO_III",
            "10000.00, 100000.00, ANEXO_V",
            "0.00,     100000.00, ANEXO_V"
    })
    @DisplayName("Varredura do Fator R")
    void varredura(String folha, String rbt12, Anexo esperado) {
        var resultado = calculadora.calcular(new BigDecimal(folha), new BigDecimal(rbt12));

        assertThat(resultado.anexo()).isEqualTo(esperado);
    }

    @Test
    @DisplayName("RBT12 zero nao divide por zero e resolve para Anexo V")
    void rbt12ZeroNaoQuebra() {
        var resultado = calculadora.calcular(new BigDecimal("5000.00"), BigDecimal.ZERO);

        assertThat(resultado.fatorR()).isEqualByComparingTo("0");
        assertThat(resultado.anexo()).isEqualTo(Anexo.ANEXO_V);
    }
}
