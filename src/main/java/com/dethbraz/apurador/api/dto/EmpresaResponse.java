package com.dethbraz.apurador.api.dto;

import com.dethbraz.apurador.dominio.Anexo;
import com.dethbraz.apurador.infra.jpa.EmpresaEntity;

public record EmpresaResponse(Long id, String cnpj, String razaoSocial,
                              Anexo anexo, String inicioAtividade) {

    public static EmpresaResponse de(EmpresaEntity e) {
        return new EmpresaResponse(e.getId(), e.getCnpj(), e.getRazaoSocial(),
                e.getAnexo(), e.getInicioAtividade().toString());
    }
}
