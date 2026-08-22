package com.dethbraz.apurador.infra.jpa;

import com.dethbraz.apurador.dominio.Competencia;
import com.dethbraz.apurador.dominio.Receita;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;

@Entity
@Table(
        name = "receita",
        // Uma empresa tem no maximo um lancamento por competencia. A restricao
        // fica no banco, e nao so no codigo: e o unico lugar que garante a regra
        // sob concorrencia.
        uniqueConstraints = @UniqueConstraint(
                name = "uk_receita_empresa_competencia",
                columnNames = {"empresa_id", "ano", "mes"}))
public class ReceitaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false)
    private EmpresaEntity empresa;

    @Column(nullable = false)
    private Integer ano;

    @Column(nullable = false)
    private Integer mes;

    // precision/scale explicitos: valor monetario nao pode herdar o default do
    // dialeto, sob risco de truncar centavos em algum banco.
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal valor;

    protected ReceitaEntity() {
        // exigido pelo JPA
    }

    public ReceitaEntity(EmpresaEntity empresa, Competencia competencia, BigDecimal valor) {
        this.empresa = empresa;
        this.ano = competencia.ano();
        this.mes = competencia.mes();
        this.valor = valor;
    }

    public Long getId() {
        return id;
    }

    public Competencia getCompetencia() {
        return Competencia.de(ano, mes);
    }

    public BigDecimal getValor() {
        return valor;
    }

    public void setValor(BigDecimal valor) {
        this.valor = valor;
    }

    /** Converte para o record do dominio. A borda traduz; o dominio nao sabe de JPA. */
    public Receita paraDominio() {
        return new Receita(getCompetencia(), valor);
    }
}
