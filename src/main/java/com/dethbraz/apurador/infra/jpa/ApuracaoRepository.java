package com.dethbraz.apurador.infra.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ApuracaoRepository extends JpaRepository<ApuracaoEntity, Long> {

    Optional<ApuracaoEntity> findByEmpresaIdAndAnoAndMes(Long empresaId, int ano, int mes);

    List<ApuracaoEntity> findByEmpresaIdOrderByAnoAscMesAsc(Long empresaId);
}
