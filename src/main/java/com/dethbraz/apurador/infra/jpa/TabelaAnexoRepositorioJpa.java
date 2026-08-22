package com.dethbraz.apurador.infra.jpa;

import com.dethbraz.apurador.dominio.Anexo;
import com.dethbraz.apurador.dominio.FaixaTabela;
import com.dethbraz.apurador.dominio.TabelaAnexoRepositorio;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Adaptador: implementa a porta do dominio usando JPA.
 *
 * Esta classe e a unica fronteira entre persistencia e calculo. O dominio
 * declara o que precisa (TabelaAnexoRepositorio); aqui se resolve como buscar.
 * Nos testes de unidade a mesma porta e implementada em memoria - por isso os
 * 29 testes de calculo rodam sem banco e em milissegundos.
 */
@Component
public class TabelaAnexoRepositorioJpa implements TabelaAnexoRepositorio {

    private final FaixaTabelaRepository repository;

    public TabelaAnexoRepositorioJpa(FaixaTabelaRepository repository) {
        this.repository = repository;
    }

    @Override
    public List<FaixaTabela> faixasVigentes(Anexo anexo, LocalDate data) {
        return repository.vigentesEm(anexo, data).stream()
                .map(FaixaTabelaEntity::paraDominio)
                .toList();
    }

    /**
     * Versao que devolve a ENTIDADE da faixa aplicada.
     *
     * A apuracao precisa guardar a referencia da linha exata usada, e para isso
     * precisa do id - que o record de dominio nao carrega de proposito. Este
     * metodo existe so para a camada de aplicacao amarrar a apuracao a sua
     * origem no banco.
     */
    public Optional<FaixaTabelaEntity> entidadeDaFaixa(Anexo anexo, BigDecimal rbt12, LocalDate data) {
        return repository.vigentesEm(anexo, data).stream()
                .filter(e -> e.paraDominio().contem(rbt12))
                .findFirst();
    }

    /** Primeira faixa vigente - usada quando o RBT12 e zero e nao ha enquadramento a fazer. */
    public Optional<FaixaTabelaEntity> primeiraFaixa(Anexo anexo, LocalDate data) {
        return repository.vigentesEm(anexo, data).stream().findFirst();
    }
}
