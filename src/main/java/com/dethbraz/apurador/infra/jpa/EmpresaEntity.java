package com.dethbraz.apurador.infra.jpa;

import com.dethbraz.apurador.dominio.Anexo;
import com.dethbraz.apurador.dominio.Competencia;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "empresa")
public class EmpresaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 14)
    private String cnpj;

    @Column(nullable = false)
    private String razaoSocial;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Anexo anexo;

    // Competencia e um value object do dominio; no banco vira duas colunas.
    // Guardar como dois inteiros (e nao como data) evita a impressao de que
    // existe um dia relevante - apuracao e sempre mensal.
    @Column(nullable = false)
    private Integer inicioAtividadeAno;

    @Column(nullable = false)
    private Integer inicioAtividadeMes;

    protected EmpresaEntity() {
        // exigido pelo JPA
    }

    public EmpresaEntity(String cnpj, String razaoSocial, Anexo anexo, Competencia inicioAtividade) {
        this.cnpj = cnpj;
        this.razaoSocial = razaoSocial;
        this.anexo = anexo;
        this.inicioAtividadeAno = inicioAtividade.ano();
        this.inicioAtividadeMes = inicioAtividade.mes();
    }

    public Long getId() {
        return id;
    }

    public String getCnpj() {
        return cnpj;
    }

    public String getRazaoSocial() {
        return razaoSocial;
    }

    public Anexo getAnexo() {
        return anexo;
    }

    public Competencia getInicioAtividade() {
        return Competencia.de(inicioAtividadeAno, inicioAtividadeMes);
    }

    public void setAnexo(Anexo anexo) {
        this.anexo = anexo;
    }
}
