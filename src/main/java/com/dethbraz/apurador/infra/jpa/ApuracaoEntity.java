package com.dethbraz.apurador.infra.jpa;

import com.dethbraz.apurador.dominio.Anexo;
import com.dethbraz.apurador.dominio.Competencia;
import com.dethbraz.apurador.dominio.MemoriaCalculo;
import com.dethbraz.apurador.dominio.Tributo;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.MapKeyEnumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;

/**
 * Apuracao gravada, com a memoria de calculo inteira.
 *
 * Guardar so o valor do DAS seria menor e inutil para auditoria. O que da valor
 * a esta tabela e o campo {@code faixaAplicada}: ele aponta para a LINHA EXATA
 * de tabela usada, com sua vigencia. Meses depois, da para provar qual versao da
 * legislacao gerou aquele numero - sem depender de recalcular.
 */
@Entity
@Table(
        name = "apuracao",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_apuracao_empresa_competencia",
                columnNames = {"empresa_id", "ano", "mes"}))
public class ApuracaoEntity {

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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Anexo anexo;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal receitaBrutaMes;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal rbt12;

    // Nome da coluna explicito de proposito. A estrategia de nomes padrao do
    // Spring nao insere underscore entre digito e maiuscula, entao
    // "rbt12Proporcionalizado" viraria "rbt12proporcionalizado" - ilegivel e
    // divergente do SQL da migracao. Em campo com numero no meio, nomear a mao
    // evita depender de uma convencao que tem excecoes.
    @Column(name = "rbt12_proporcionalizado", nullable = false)
    private Boolean rbt12Proporcionalizado;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "faixa_tabela_id", nullable = false)
    private FaixaTabelaEntity faixaAplicada;

    @Column(nullable = false, precision = 12, scale = 10)
    private BigDecimal aliquotaEfetiva;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal valorDas;

    @Column(nullable = false)
    private Instant apuradoEm;

    /**
     * Composicao do DAS gravada junto com a apuracao.
     *
     * Poderia ser recalculada a partir da faixa, mas gravar torna o registro
     * autossuficiente: a guia paga fica documentada como foi emitida, sem
     * depender de reexecutar o calculo anos depois.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "apuracao_tributo",
            joinColumns = @JoinColumn(name = "apuracao_id"))
    @MapKeyEnumerated(EnumType.STRING)
    @MapKeyColumn(name = "tributo", length = 20)
    @Column(name = "valor", nullable = false, precision = 15, scale = 2)
    private Map<Tributo, BigDecimal> valoresPorTributo = new EnumMap<>(Tributo.class);

    protected ApuracaoEntity() {
        // exigido pelo JPA
    }

    public ApuracaoEntity(EmpresaEntity empresa, MemoriaCalculo memoria, FaixaTabelaEntity faixaAplicada) {
        this.empresa = empresa;
        this.faixaAplicada = faixaAplicada;
        aplicar(memoria);
    }

    /** Reapuracao sobrescreve o resultado, mantendo a mesma linha da tabela. */
    public void atualizar(MemoriaCalculo memoria, FaixaTabelaEntity faixaAplicada) {
        this.faixaAplicada = faixaAplicada;
        aplicar(memoria);
    }

    private void aplicar(MemoriaCalculo memoria) {
        this.ano = memoria.competencia().ano();
        this.mes = memoria.competencia().mes();
        this.anexo = memoria.anexo();
        this.receitaBrutaMes = memoria.receitaBrutaMes();
        this.rbt12 = memoria.rbt12();
        this.rbt12Proporcionalizado = memoria.rbt12Proporcionalizado();
        this.aliquotaEfetiva = memoria.aliquotaEfetiva();
        this.valorDas = memoria.valorDas();
        this.apuradoEm = Instant.now();
        this.valoresPorTributo = memoria.valoresPorTributo().isEmpty()
                ? new EnumMap<>(Tributo.class)
                : new EnumMap<>(memoria.valoresPorTributo());
    }

    public Long getId() {
        return id;
    }

    public Competencia getCompetencia() {
        return Competencia.de(ano, mes);
    }

    public Instant getApuradoEm() {
        return apuradoEm;
    }

    public MemoriaCalculo paraDominio() {
        return new MemoriaCalculo(getCompetencia(), anexo, receitaBrutaMes, rbt12,
                rbt12Proporcionalizado, faixaAplicada.paraDominio(), aliquotaEfetiva, valorDas,
                valoresPorTributo);
    }
}
