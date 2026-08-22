package com.dethbraz.apurador.dominio;

/**
 * Tributos que compoem o DAS.
 *
 * O DAS e uma guia unica, mas por dentro ele e a soma de varios tributos, cada
 * um com percentual proprio definido por faixa e por anexo. Quem recolhe paga um
 * valor so; a contabilidade precisa saber a composicao.
 *
 * Nem todo anexo tem todos: ICMS aparece no Anexo I (comercio), IPI no II
 * (industria), ISS nos de servico. Por isso a reparticao e um mapa, e nao campos
 * fixos - cada faixa declara os tributos que a compoem.
 */
public enum Tributo {

    IRPJ("Imposto de Renda Pessoa Juridica"),
    CSLL("Contribuicao Social sobre o Lucro Liquido"),
    COFINS("Contribuicao para o Financiamento da Seguridade Social"),
    PIS_PASEP("Programa de Integracao Social"),
    CPP("Contribuicao Patronal Previdenciaria"),
    ICMS("Imposto sobre Circulacao de Mercadorias e Servicos"),
    IPI("Imposto sobre Produtos Industrializados"),
    ISS("Imposto Sobre Servicos");

    private final String descricao;

    Tributo(String descricao) {
        this.descricao = descricao;
    }

    public String descricao() {
        return descricao;
    }
}
