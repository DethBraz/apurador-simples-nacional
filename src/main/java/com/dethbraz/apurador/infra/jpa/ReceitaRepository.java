package com.dethbraz.apurador.infra.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ReceitaRepository extends JpaRepository<ReceitaEntity, Long> {

    List<ReceitaEntity> findByEmpresaId(Long empresaId);

    Optional<ReceitaEntity> findByEmpresaIdAndAnoAndMes(Long empresaId, int ano, int mes);
}
