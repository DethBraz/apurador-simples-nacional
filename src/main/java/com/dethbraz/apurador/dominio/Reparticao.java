package com.dethbraz.apurador.dominio;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Como o DAS se reparte entre os tributos de uma faixa.
 *
 * Percentuais em fracao: 0.055 = 5,5%. A soma precisa fechar em 100%.
 *
 * ---------------------------------------------------------------------------
 * O PROBLEMA DOS CENTAVOS
 *
 * Repartir dinheiro por percentual e onde software financeiro erra silenciosamente.
 * Arredondar cada tributo por conta propria quase sempre faz a soma das partes
 * NAO bater com o total:
 *
 *     DAS 433,33 repartido em 6 tributos, cada um arredondado a 2 casas
 *     -> a soma pode dar 433,32 ou 433,34
 *
 * Um centavo de diferenca numa guia e divergencia contabil real. E ninguem
 * percebe ate a conferencia do fechamento.
 *
 * A solucao usada aqui e o metodo dos maiores restos (Hare): trunca todo mundo
 * para baixo, calcula quantos centavos sobraram, e distribui esses centavos aos
 * tributos cuja parte fracionaria descartada foi maior. O resultado soma
 * exatamente o total, sempre - e ha teste cravando isso para muitos valores.
 * ---------------------------------------------------------------------------
 */
public record Reparticao(Map<Tributo, BigDecimal> percentuais) {

    private static final BigDecimal CEM_PORCENTO = BigDecimal.ONE;
    private static final BigDecimal TOLERANCIA = new BigDecimal("0.000001");
    private static final BigDecimal UM_CENTAVO = new BigDecimal("0.01");
    private static final int ESCALA_MONETARIA = 2;

    public Reparticao {
        // Atencao ao EnumMap: o construtor de copia exige mapa NAO VAZIO quando
        // a origem nao e um EnumMap - ele deduz o tipo da chave a partir do
        // primeiro elemento, e sem elementos lanca IllegalArgumentException.
        // Por isso a copia passa por copiar(), que trata o caso vazio.
        percentuais = copiar(percentuais);

        if (!percentuais.isEmpty()) {
            BigDecimal soma = percentuais.values().stream()
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            if (soma.subtract(CEM_PORCENTO).abs().compareTo(TOLERANCIA) > 0) {
                throw new IllegalArgumentException(
                        "Os percentuais da reparticao devem somar 100%. Somaram: "
                                + soma.multiply(BigDecimal.valueOf(100)) + "%");
            }
            percentuais.forEach((tributo, percentual) -> {
                if (percentual.signum() < 0) {
                    throw new IllegalArgumentException(
                            "Percentual negativo para " + tributo + ": " + percentual);
                }
            });
        }
    }

    /** Copia segura para EnumMap, tolerando mapa nulo ou vazio. */
    private static Map<Tributo, BigDecimal> copiar(Map<Tributo, BigDecimal> origem) {
        Map<Tributo, BigDecimal> copia = new EnumMap<>(Tributo.class);
        if (origem != null) {
            copia.putAll(origem);
        }
        return copia;
    }

    /**
     * Faixa cuja reparticao ainda nao foi cadastrada.
     *
     * Existe porque a Fase 0 pode entregar as aliquotas antes dos percentuais de
     * reparticao. Nesse caso a apuracao continua funcionando e devolve o DAS
     * total - so nao detalha a composicao, em vez de falhar.
     */
    public static Reparticao naoInformada() {
        return new Reparticao(Map.of());
    }

    public boolean informada() {
        return !percentuais.isEmpty();
    }

    /**
     * Reparte {@code total} entre os tributos garantindo que a soma das partes
     * seja exatamente {@code total}.
     *
     * Metodo dos maiores restos:
     *   1. cada tributo recebe seu valor truncado para baixo (2 casas);
     *   2. sobra uma diferenca de N centavos em relacao ao total;
     *   3. esses N centavos vao para os tributos que mais perderam no truncamento.
     *
     * O desempate final e pela ordem do enum, para que o resultado seja
     * deterministico - repartir a mesma guia duas vezes precisa dar igual.
     */
    public Map<Tributo, BigDecimal> distribuir(BigDecimal total) {
        if (!informada()) {
            return Map.of();
        }

        BigDecimal totalNormalizado = total.setScale(ESCALA_MONETARIA, RoundingMode.HALF_UP);

        record Parcela(Tributo tributo, BigDecimal truncado, BigDecimal resto) {}

        List<Parcela> parcelas = new ArrayList<>();
        BigDecimal somaTruncada = BigDecimal.ZERO;

        for (Map.Entry<Tributo, BigDecimal> entrada : copiar(percentuais).entrySet()) {
            BigDecimal exato = totalNormalizado.multiply(entrada.getValue());
            BigDecimal truncado = exato.setScale(ESCALA_MONETARIA, RoundingMode.DOWN);
            parcelas.add(new Parcela(entrada.getKey(), truncado, exato.subtract(truncado)));
            somaTruncada = somaTruncada.add(truncado);
        }

        // Quantos centavos sobraram para distribuir.
        BigDecimal sobra = totalNormalizado.subtract(somaTruncada);
        int centavosASobrar = sobra.divide(UM_CENTAVO, 0, RoundingMode.HALF_UP).intValue();

        // Maior resto primeiro; empate resolvido pela ordem do enum.
        parcelas.sort(Comparator
                .comparing(Parcela::resto).reversed()
                .thenComparing(p -> p.tributo().ordinal()));

        Map<Tributo, BigDecimal> resultado = new LinkedHashMap<>();
        for (int i = 0; i < parcelas.size(); i++) {
            Parcela p = parcelas.get(i);
            BigDecimal valor = i < centavosASobrar ? p.truncado().add(UM_CENTAVO) : p.truncado();
            resultado.put(p.tributo(), valor);
        }

        // Devolve na ordem do enum, e nao na ordem do desempate - saida estavel
        // e mais legivel para quem confere.
        Map<Tributo, BigDecimal> ordenado = new EnumMap<>(Tributo.class);
        ordenado.putAll(resultado);
        return ordenado;
    }
}
