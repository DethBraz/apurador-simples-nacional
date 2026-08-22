package com.dethbraz.apurador.dominio;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReparticaoTest {

    /**
     * Percentuais ficticios que somam 100%, escolhidos para produzir restos
     * "feios" no arredondamento - e assim exercitar o problema dos centavos.
     */
    private static Reparticao reparticaoDeSeisTributos() {
        Map<Tributo, BigDecimal> p = new EnumMap<>(Tributo.class);
        p.put(Tributo.IRPJ, new BigDecimal("0.0550"));
        p.put(Tributo.CSLL, new BigDecimal("0.0350"));
        p.put(Tributo.COFINS, new BigDecimal("0.1274"));
        p.put(Tributo.PIS_PASEP, new BigDecimal("0.0276"));
        p.put(Tributo.CPP, new BigDecimal("0.4150"));
        p.put(Tributo.ICMS, new BigDecimal("0.3400"));
        return new Reparticao(p);
    }

    @Nested
    @DisplayName("Validacao dos percentuais")
    class Validacao {

        @Test
        @DisplayName("Percentuais que nao somam 100% sao rejeitados na criacao")
        void somaDiferenteDeCemFalha() {
            Map<Tributo, BigDecimal> p = new EnumMap<>(Tributo.class);
            p.put(Tributo.IRPJ, new BigDecimal("0.50"));
            p.put(Tributo.CSLL, new BigDecimal("0.30")); // soma 80%

            assertThatThrownBy(() -> new Reparticao(p))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("devem somar 100%");
        }

        @Test
        @DisplayName("Percentual negativo e rejeitado")
        void percentualNegativoFalha() {
            Map<Tributo, BigDecimal> p = new EnumMap<>(Tributo.class);
            p.put(Tributo.IRPJ, new BigDecimal("1.20"));
            p.put(Tributo.CSLL, new BigDecimal("-0.20")); // soma 100%, mas negativo

            assertThatThrownBy(() -> new Reparticao(p))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("negativo");
        }

        @Test
        @DisplayName("Reparticao nao informada e valida e nao distribui nada")
        void naoInformadaEValida() {
            Reparticao vazia = Reparticao.naoInformada();

            assertThat(vazia.informada()).isFalse();
            assertThat(vazia.distribuir(new BigDecimal("433.33"))).isEmpty();
        }
    }

    @Nested
    @DisplayName("O problema dos centavos")
    class ProblemaDosCentavos {

        @Test
        @DisplayName("A soma das partes bate exatamente com o total")
        void somaDasPartesBateComOTotal() {
            // 433,33 repartido em 6 percentuais quebrados. Arredondando cada um
            // por conta propria, a soma daria 433,32 ou 433,34.
            BigDecimal das = new BigDecimal("433.33");

            Map<Tributo, BigDecimal> partes = reparticaoDeSeisTributos().distribuir(das);

            BigDecimal soma = partes.values().stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            assertThat(soma).isEqualByComparingTo(das);
        }

        @ParameterizedTest(name = "DAS de {0} reparte sem perder nem criar centavo")
        @ValueSource(strings = {
                "0.01", "0.07", "1.00", "10.03", "99.99", "100.00", "433.33",
                "1234.56", "9999.99", "12345.67", "87654.21", "1000000.01"
        })
        @DisplayName("Varredura de valores")
        void varreduraDeValores(String valor) {
            BigDecimal das = new BigDecimal(valor);

            Map<Tributo, BigDecimal> partes = reparticaoDeSeisTributos().distribuir(das);

            BigDecimal soma = partes.values().stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            assertThat(soma)
                    .as("a soma dos tributos precisa fechar com o DAS de %s", valor)
                    .isEqualByComparingTo(das);
        }

        @Test
        @DisplayName("Os centavos que sobram vao para quem mais perdeu no truncamento")
        void centavosVaoParaOsMaioresRestos() {
            // 1,00 dividido em tres partes iguais: 0,3333... cada.
            // Truncando: 0,33 + 0,33 + 0,33 = 0,99. Sobra 1 centavo.
            // Os tres tem resto identico, entao o desempate e pela ordem do enum:
            // IRPJ (primeiro) recebe o centavo.
            Map<Tributo, BigDecimal> p = new EnumMap<>(Tributo.class);
            BigDecimal umTerco = new BigDecimal("0.333333");
            p.put(Tributo.IRPJ, umTerco);
            p.put(Tributo.CSLL, umTerco);
            p.put(Tributo.COFINS, new BigDecimal("0.333334"));

            Map<Tributo, BigDecimal> partes = new Reparticao(p).distribuir(new BigDecimal("1.00"));

            assertThat(partes.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add))
                    .isEqualByComparingTo("1.00");
            assertThat(partes.get(Tributo.COFINS)).isEqualByComparingTo("0.34");
            assertThat(partes.get(Tributo.IRPJ)).isEqualByComparingTo("0.33");
            assertThat(partes.get(Tributo.CSLL)).isEqualByComparingTo("0.33");
        }

        @Test
        @DisplayName("Repartir o mesmo valor duas vezes da o mesmo resultado")
        void distribuicaoEDeterministica() {
            // TESTE FRACO, mantido de proposito como rede de baixo custo.
            //
            // Ele so compara duas chamadas no mesmo processo, com os mesmos
            // dados - passaria mesmo com uma implementacao instavel entre
            // execucoes distintas. Quem realmente protege o determinismo e o
            // empateSegueAOrdemDoEnum abaixo.
            BigDecimal das = new BigDecimal("433.33");
            Reparticao reparticao = reparticaoDeSeisTributos();

            assertThat(reparticao.distribuir(das))
                    .isEqualTo(reparticao.distribuir(das));
        }

        @Test
        @DisplayName("Empate de restos e resolvido pela ordem do enum")
        void empateSegueAOrdemDoEnum() {
            // Quatro tributos a 25%, DAS de 1,01:
            //   1,01 x 0,25 = 0,2525 para todos
            //   truncado 0,25, resto 0,0025 IDENTICO nos quatro
            //   soma truncada 1,00 -> sobra exatamente 1 centavo
            //
            // Com todos empatados e um unico centavo em disputa, a unica coisa
            // que decide o vencedor e o criterio de desempate. O contrato e:
            // ganha o de menor ordinal no enum - aqui, IRPJ.
            //
            // O QUE ESTE TESTE PROTEGE: hoje o resultado tambem sairia correto
            // sem o thenComparing(ordinal), porque EnumMap itera na ordem do
            // enum e List.sort e estavel. Sao duas propriedades implicitas.
            // Trocar o EnumMap por HashMap em Reparticao.copiar() derruba as
            // duas de uma vez - e este teste acusa, enquanto os outros 55 nao.
            Map<Tributo, BigDecimal> p = new EnumMap<>(Tributo.class);
            p.put(Tributo.IRPJ, new BigDecimal("0.25"));
            p.put(Tributo.CSLL, new BigDecimal("0.25"));
            p.put(Tributo.COFINS, new BigDecimal("0.25"));
            p.put(Tributo.PIS_PASEP, new BigDecimal("0.25"));

            Map<Tributo, BigDecimal> partes = new Reparticao(p).distribuir(new BigDecimal("1.01"));

            assertThat(partes.get(Tributo.IRPJ))
                    .as("o menor ordinal do enum recebe o centavo em caso de empate")
                    .isEqualByComparingTo("0.26");
            assertThat(partes.get(Tributo.CSLL)).isEqualByComparingTo("0.25");
            assertThat(partes.get(Tributo.COFINS)).isEqualByComparingTo("0.25");
            assertThat(partes.get(Tributo.PIS_PASEP)).isEqualByComparingTo("0.25");

            assertThat(partes.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add))
                    .isEqualByComparingTo("1.01");
        }

        @Test
        @DisplayName("DAS zero reparte zero para todos")
        void dasZero() {
            Map<Tributo, BigDecimal> partes =
                    reparticaoDeSeisTributos().distribuir(BigDecimal.ZERO);

            assertThat(partes.values()).allSatisfy(valor ->
                    assertThat(valor).isEqualByComparingTo("0.00"));
        }
    }
}
