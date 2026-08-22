package com.dethbraz.apurador.dominio;

/**
 * Anexos do Simples Nacional (LC 123/2006).
 *
 * III e V sao os unicos que podem ser decididos em tempo de apuracao, pela
 * regra do Fator R - ver {@link com.dethbraz.apurador.calculo.CalculadoraFatorR}.
 * Os demais vem do enquadramento cadastral da empresa.
 */
public enum Anexo {

    ANEXO_I("Comercio"),
    ANEXO_II("Industria"),
    ANEXO_III("Servicos - Fator R igual ou acima de 28%"),
    ANEXO_IV("Servicos - sem CPP no DAS"),
    ANEXO_V("Servicos - Fator R abaixo de 28%");

    private final String descricao;

    Anexo(String descricao) {
        this.descricao = descricao;
    }

    public String descricao() {
        return descricao;
    }

    /** Anexos cuja definicao depende do Fator R apurado no mes. */
    public boolean dependeDeFatorR() {
        return this == ANEXO_III || this == ANEXO_V;
    }
}
