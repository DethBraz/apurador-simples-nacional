package com.dethbraz.apurador.dominio;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Mes e ano de apuracao (ex.: 2026-03).
 *
 * E um value object: nao tem identidade propria, so valor. Duas competencias
 * com o mesmo ano e mes sao a mesma coisa - por isso record, que ja entrega
 * equals, hashCode e imutabilidade.
 *
 * Existe como tipo proprio em vez de um par solto de inteiros porque a
 * competencia e a chave que decide QUAL tabela vigente sera aplicada. Deixar
 * isso como dois ints espalhados pelo codigo e o caminho mais curto para
 * inverter mes com ano em alguma chamada.
 */
public record Competencia(int ano, int mes) implements Comparable<Competencia> {

    public Competencia {
        if (mes < 1 || mes > 12) {
            throw new IllegalArgumentException("Mes deve estar entre 1 e 12, recebido: " + mes);
        }
        if (ano < 1900) {
            throw new IllegalArgumentException("Ano invalido: " + ano);
        }
    }

    public static Competencia de(int ano, int mes) {
        return new Competencia(ano, mes);
    }

    /** Primeiro dia do mes. E a data usada para resolver a tabela vigente. */
    public LocalDate primeiroDia() {
        return LocalDate.of(ano, mes, 1);
    }

    public Competencia anterior() {
        return menos(1);
    }

    public Competencia menos(int meses) {
        YearMonth ym = YearMonth.of(ano, mes).minusMonths(meses);
        return new Competencia(ym.getYear(), ym.getMonthValue());
    }

    /**
     * As N competencias imediatamente anteriores a esta, da mais antiga para a
     * mais recente. Usado para montar a janela do RBT12.
     */
    public List<Competencia> anteriores(int quantidade) {
        List<Competencia> janela = new ArrayList<>(quantidade);
        for (int i = quantidade; i >= 1; i--) {
            janela.add(menos(i));
        }
        return janela;
    }

    /** Quantos meses separam esta competencia de outra (positivo se esta for posterior). */
    public int mesesDesde(Competencia outra) {
        return (ano - outra.ano()) * 12 + (mes - outra.mes());
    }

    @Override
    public int compareTo(Competencia outra) {
        return Integer.compare(ano * 12 + mes, outra.ano * 12 + outra.mes);
    }

    @Override
    public String toString() {
        return String.format("%04d-%02d", ano, mes);
    }
}
