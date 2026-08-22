package com.dethbraz.apurador.calculo;

import com.dethbraz.apurador.dominio.Anexo;
import com.dethbraz.apurador.dominio.Competencia;
import com.dethbraz.apurador.dominio.MemoriaCalculo;
import com.dethbraz.apurador.dominio.Receita;
import com.dethbraz.apurador.fixture.TabelasFicticias;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * Os testes que justificam o projeto.
 *
 * Nao sao testes de getter: cada um cobre uma fronteira onde o calculo fiscal
 * erra de verdade e o erro custa dinheiro.
 */
class CalculadoraDasTest {

    private static final Competencia MARCO_2025 = Competencia.de(2025, 3);
    private static final Competencia INICIO_ANTIGO = Competencia.de(2015, 1);

    private final CalculadoraDas calculadora = new CalculadoraDas(TabelasFicticias.versaoUnica());

    @Nested
    @DisplayName("Fronteira entre faixas")
    class FronteiraEntreFaixas {

        @Test
        @DisplayName("RBT12 no valor exato do limite pertence a faixa DE BAIXO")
        void limiteExatoFicaNaFaixaInferior() {
            // A tabela le "de 100.000,01 ate 200.000,00": o limite superior
            // pertence a faixa. Com RBT12 = 100.000,00 cravado, a empresa ainda
            // esta na faixa 1. Trocar o <= por < em FaixaTabela.contem() quebra
            // exatamente este teste.
            List<Receita> receitas =
                    TabelasFicticias.comRbt12Exato("100000.00", "10000.00", MARCO_2025);

            MemoriaCalculo memoria =
                    calculadora.apurar(Anexo.ANEXO_I, MARCO_2025, receitas, INICIO_ANTIGO);

            assertThat(memoria.faixa()).isEqualTo(1);
            assertThat(memoria.aliquotaEfetiva())
                    .isEqualByComparingTo(new BigDecimal("0.04"));
        }

        @Test
        @DisplayName("Um centavo acima do limite ja cai na faixa seguinte")
        void umCentavoAcimaMudaDeFaixa() {
            List<Receita> receitas =
                    TabelasFicticias.comRbt12Exato("100000.01", "10000.00", MARCO_2025);

            MemoriaCalculo memoria =
                    calculadora.apurar(Anexo.ANEXO_I, MARCO_2025, receitas, INICIO_ANTIGO);

            assertThat(memoria.faixa()).isEqualTo(2);
        }

        @Test
        @DisplayName("A parcela a deduzir evita salto de aliquota na virada de faixa")
        void aliquotaEfetivaEContinuaNaFronteira() {
            // Este teste explica o proposito da parcela a deduzir. Sem ela, subir
            // de faixa aplicaria a aliquota maior sobre TODO o faturamento e o
            // imposto daria um salto. Com ela, a aliquota efetiva atravessa a
            // fronteira praticamente sem degrau.
            var antes = calculadora.apurar(Anexo.ANEXO_I, MARCO_2025,
                    TabelasFicticias.comRbt12Exato("100000.00", "10000.00", MARCO_2025),
                    INICIO_ANTIGO);

            var depois = calculadora.apurar(Anexo.ANEXO_I, MARCO_2025,
                    TabelasFicticias.comRbt12Exato("100000.01", "10000.00", MARCO_2025),
                    INICIO_ANTIGO);

            assertThat(antes.faixa()).isNotEqualTo(depois.faixa());
            assertThat(depois.aliquotaEfetiva().doubleValue())
                    .isCloseTo(antes.aliquotaEfetiva().doubleValue(), within(0.0000001));
        }

        @ParameterizedTest(name = "RBT12 {0} cai na faixa {1}")
        @CsvSource({
                "50000.00,  1",
                "100000.00, 1",
                "100000.01, 2",
                "150000.00, 2",
                "200000.00, 2",
                "200000.01, 3",
                "300000.00, 3"
        })
        @DisplayName("Varredura das faixas")
        void varreduraDasFaixas(String rbt12, int faixaEsperada) {
            List<Receita> receitas =
                    TabelasFicticias.comRbt12Exato(rbt12, "1000.00", MARCO_2025);

            MemoriaCalculo memoria =
                    calculadora.apurar(Anexo.ANEXO_I, MARCO_2025, receitas, INICIO_ANTIGO);

            assertThat(memoria.faixa()).isEqualTo(faixaEsperada);
        }
    }

    @Nested
    @DisplayName("Calculo do DAS")
    class CalculoDoDas {

        @Test
        @DisplayName("Aplica a aliquota efetiva sobre a receita do mes, nao sobre o RBT12")
        void aplicaSobreReceitaDoMes() {
            // RBT12 = 120.000 (12 x 10.000) -> faixa 2
            // aliquota efetiva = (120000 x 0,06 - 2000) / 120000 = 0,0433333333
            // DAS = 10.000 x 0,0433333333 = 433,33
            List<Receita> receitas = TabelasFicticias.dozeMesesDe("10000.00", MARCO_2025);
            receitas = new java.util.ArrayList<>(receitas);
            receitas.add(new Receita(MARCO_2025, new BigDecimal("10000.00")));

            MemoriaCalculo memoria =
                    calculadora.apurar(Anexo.ANEXO_I, MARCO_2025, receitas, INICIO_ANTIGO);

            assertThat(memoria.rbt12()).isEqualByComparingTo("120000.00");
            assertThat(memoria.faixa()).isEqualTo(2);
            assertThat(memoria.valorDas()).isEqualByComparingTo("433.33");
        }

        @Test
        @DisplayName("Mes sem faturamento gera DAS zero, mesmo com RBT12 alto")
        void mesSemFaturamento() {
            List<Receita> receitas = TabelasFicticias.dozeMesesDe("10000.00", MARCO_2025);

            MemoriaCalculo memoria =
                    calculadora.apurar(Anexo.ANEXO_I, MARCO_2025, receitas, INICIO_ANTIGO);

            assertThat(memoria.rbt12()).isEqualByComparingTo("120000.00");
            assertThat(memoria.valorDas()).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("Empresa sem faturamento nenhum nao quebra por divisao por zero")
        void semFaturamentoNenhum() {
            // RBT12 zero faria a formula da aliquota efetiva dividir por zero.
            // O caso e tratado explicitamente em vez de estourar ArithmeticException.
            MemoriaCalculo memoria = calculadora.apurar(
                    Anexo.ANEXO_I, MARCO_2025, List.of(), INICIO_ANTIGO);

            assertThat(memoria.rbt12()).isEqualByComparingTo("0.00");
            assertThat(memoria.valorDas()).isEqualByComparingTo("0.00");
            assertThat(memoria.faixa()).isEqualTo(1);
        }

        @Test
        @DisplayName("RBT12 acima do teto acusa desenquadramento em vez de calcular errado")
        void acimaDoTetoLancaExcecao() {
            List<Receita> receitas =
                    TabelasFicticias.comRbt12Exato("500000.00", "10000.00", MARCO_2025);

            assertThatThrownBy(() ->
                    calculadora.apurar(Anexo.ANEXO_I, MARCO_2025, receitas, INICIO_ANTIGO))
                    .isInstanceOf(DesenquadramentoException.class)
                    .hasMessageContaining("desenquadramento");
        }
    }

    @Nested
    @DisplayName("Reprodutibilidade historica")
    class ReprodutibilidadeHistorica {

        @Test
        @DisplayName("Apuracao passada usa a tabela que valia na epoca, nao a atual")
        void competenciaPassadaUsaTabelaAntiga() {
            // ESTE E O TESTE QUE CONTA A HISTORIA DA ARQUITETURA.
            //
            // Existem duas versoes de tabela cadastradas: a antiga (ate 2025) e a
            // nova (a partir de 2026, com aliquotas dobradas). Apurar uma
            // competencia de 2025 tem que continuar usando a tabela de 2025 -
            // mesmo com a nova ja no banco.
            //
            // Se as aliquotas fossem constantes no codigo, este teste seria
            // impossivel de escrever.
            CalculadoraDas comHistorico = new CalculadoraDas(TabelasFicticias.comDuasVersoes());

            List<Receita> receitas =
                    TabelasFicticias.comRbt12Exato("50000.00", "10000.00", MARCO_2025);

            MemoriaCalculo memoria =
                    comHistorico.apurar(Anexo.ANEXO_I, MARCO_2025, receitas, INICIO_ANTIGO);

            // Tabela antiga: faixa 1 com 4%
            assertThat(memoria.aliquotaEfetiva()).isEqualByComparingTo(new BigDecimal("0.04"));
            assertThat(memoria.valorDas()).isEqualByComparingTo("400.00");
            assertThat(memoria.faixaAplicada().vigenciaInicio())
                    .isEqualTo(TabelasFicticias.VIGENCIA_ANTIGA_INICIO);
        }

        @Test
        @DisplayName("Mesma empresa, competencia nova, tabela nova")
        void competenciaNovaUsaTabelaNova() {
            CalculadoraDas comHistorico = new CalculadoraDas(TabelasFicticias.comDuasVersoes());
            Competencia marco2026 = Competencia.de(2026, 3);

            List<Receita> receitas =
                    TabelasFicticias.comRbt12Exato("50000.00", "10000.00", marco2026);

            MemoriaCalculo memoria =
                    comHistorico.apurar(Anexo.ANEXO_I, marco2026, receitas, INICIO_ANTIGO);

            // Tabela nova: faixa 1 com 8% (o dobro)
            assertThat(memoria.aliquotaEfetiva()).isEqualByComparingTo(new BigDecimal("0.08"));
            assertThat(memoria.valorDas()).isEqualByComparingTo("800.00");
            assertThat(memoria.faixaAplicada().vigenciaInicio())
                    .isEqualTo(TabelasFicticias.VIGENCIA_NOVA_INICIO);
        }
    }
}
