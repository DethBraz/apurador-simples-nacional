package com.dethbraz.apurador.dominio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Porta de acesso as tabelas dos anexos.
 *
 * E uma interface no dominio, e nao um repositorio Spring Data, de proposito:
 * o nucleo de calculo nao precisa saber se as tabelas vem de PostgreSQL, de um
 * JSON ou de memoria. Na Fase 2 entra uma implementacao JPA; nos testes usamos
 * uma implementacao em memoria. O calculo nao muda em nenhum dos casos.
 *
 * Esse e o ponto de inversao de dependencia que faz os testes rodarem sem banco.
 */
public interface TabelaAnexoRepositorio {

    /** Todas as faixas de um anexo vigentes na data informada. */
    List<FaixaTabela> faixasVigentes(Anexo anexo, LocalDate data);

    /**
     * A faixa que cobre o RBT12 na data informada.
     *
     * Optional.empty() quando o RBT12 ultrapassa o limite do Simples Nacional -
     * caso real de desenquadramento, que o chamador precisa tratar em vez de
     * receber um valor silenciosamente errado.
     */
    default Optional<FaixaTabela> faixaPara(Anexo anexo, BigDecimal rbt12, LocalDate data) {
        return faixasVigentes(anexo, data).stream()
                .filter(f -> f.contem(rbt12))
                .findFirst();
    }
}
