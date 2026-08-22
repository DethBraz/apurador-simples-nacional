package com.dethbraz.apurador.infra.jpa;

import com.dethbraz.apurador.dominio.Anexo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface FaixaTabelaRepository extends JpaRepository<FaixaTabelaEntity, Long> {

    /**
     * Faixas de um anexo validas na data.
     *
     * A condicao de vigencia fim precisa aceitar null (vigencia aberta).
     * Esquecer esse OR e o jeito mais rapido de a consulta devolver vazio para
     * a tabela atual e a apuracao falhar sem motivo aparente.
     */
    @Query("""
            select f from FaixaTabelaEntity f
            where f.anexo = :anexo
              and f.vigenciaInicio <= :data
              and (f.vigenciaFim is null or f.vigenciaFim >= :data)
            order by f.faixa asc
            """)
    List<FaixaTabelaEntity> vigentesEm(@Param("anexo") Anexo anexo,
                                       @Param("data") LocalDate data);

    long count();
}
