package com.dethbraz.apurador.infra.jpa;

import com.dethbraz.apurador.dominio.Anexo;
import com.dethbraz.apurador.dominio.FaixaTabela;
import com.dethbraz.apurador.dominio.Reparticao;
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
import jakarta.persistence.MapKeyColumn;
import jakarta.persistence.MapKeyEnumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;

/**
 * Persistencia das faixas por anexo e vigencia.
 *
 * Repare que NAO existe update nesta entidade em lugar nenhum da aplicacao.
 * Mudanca de legislacao entra como LINHA NOVA com vigencia nova, e a anterior
 * ganha uma data de fim. Editar uma linha existente reescreveria o passado e
 * quebraria a reprodutibilidade das apuracoes ja feitas - que e justamente o
 * problema que este projeto resolve.
 */
@Entity
@Table(name = "faixa_tabela")
public class FaixaTabelaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Anexo anexo;

    @Column(nullable = false)
    private Integer faixa;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal limiteInferior;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal limiteSuperior;

    // scale 6: aliquota e fracao (0,04 = 4%), e algumas faixas usam mais casas.
    @Column(nullable = false, precision = 10, scale = 6)
    private BigDecimal aliquotaNominal;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal parcelaDeduzir;

    @Column(nullable = false)
    private LocalDate vigenciaInicio;

    /** null = vigente por prazo indeterminado */
    @Column
    private LocalDate vigenciaFim;

    /** De onde veio o dado (ex.: "LC 123/2006 Anexo I"). Torna a tabela auditavel. */
    @Column(length = 200)
    private String fonte;

    /**
     * Percentual de cada tributo nesta faixa, em tabela lateral.
     *
     * @ElementCollection em vez de entidade propria porque a reparticao nao tem
     * vida sem a faixa: nao e consultada isoladamente nem referenciada por
     * ninguem. Apagar a faixa deve apagar a reparticao junto, que e exatamente
     * o comportamento de uma colecao de elementos.
     */
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "faixa_reparticao",
            joinColumns = @JoinColumn(name = "faixa_tabela_id"))
    @MapKeyEnumerated(EnumType.STRING)
    @MapKeyColumn(name = "tributo", length = 20)
    @Column(name = "percentual", nullable = false, precision = 10, scale = 6)
    private Map<Tributo, BigDecimal> reparticao = new EnumMap<>(Tributo.class);

    protected FaixaTabelaEntity() {
        // exigido pelo JPA
    }

    public FaixaTabelaEntity(Anexo anexo, int faixa,
                             BigDecimal limiteInferior, BigDecimal limiteSuperior,
                             BigDecimal aliquotaNominal, BigDecimal parcelaDeduzir,
                             LocalDate vigenciaInicio, LocalDate vigenciaFim,
                             String fonte) {
        this(anexo, faixa, limiteInferior, limiteSuperior, aliquotaNominal,
                parcelaDeduzir, vigenciaInicio, vigenciaFim, fonte, Map.of());
    }

    public FaixaTabelaEntity(Anexo anexo, int faixa,
                             BigDecimal limiteInferior, BigDecimal limiteSuperior,
                             BigDecimal aliquotaNominal, BigDecimal parcelaDeduzir,
                             LocalDate vigenciaInicio, LocalDate vigenciaFim,
                             String fonte, Map<Tributo, BigDecimal> reparticao) {
        this.anexo = anexo;
        this.faixa = faixa;
        this.limiteInferior = limiteInferior;
        this.limiteSuperior = limiteSuperior;
        this.aliquotaNominal = aliquotaNominal;
        this.parcelaDeduzir = parcelaDeduzir;
        this.vigenciaInicio = vigenciaInicio;
        this.vigenciaFim = vigenciaFim;
        this.fonte = fonte;
        this.reparticao = reparticao == null || reparticao.isEmpty()
                ? new EnumMap<>(Tributo.class)
                : new EnumMap<>(reparticao);
    }

    public Long getId() {
        return id;
    }

    public String getFonte() {
        return fonte;
    }

    public Map<Tributo, BigDecimal> getReparticao() {
        return reparticao;
    }

    public FaixaTabela paraDominio() {
        return new FaixaTabela(anexo, faixa, limiteInferior, limiteSuperior,
                aliquotaNominal, parcelaDeduzir, vigenciaInicio, vigenciaFim,
                new Reparticao(reparticao));
    }
}
