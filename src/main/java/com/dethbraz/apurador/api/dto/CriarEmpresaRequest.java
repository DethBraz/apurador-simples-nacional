package com.dethbraz.apurador.api.dto;

import com.dethbraz.apurador.dominio.Anexo;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record CriarEmpresaRequest(

        @NotBlank(message = "CNPJ e obrigatorio")
        @Pattern(regexp = "\\d{14}", message = "CNPJ deve ter 14 digitos, somente numeros")
        String cnpj,

        @NotBlank(message = "Razao social e obrigatoria")
        String razaoSocial,

        @NotNull(message = "Anexo e obrigatorio")
        Anexo anexo,

        @NotNull @Min(1900) Integer inicioAtividadeAno,

        @NotNull @Min(1) @Max(12) Integer inicioAtividadeMes) {
}
