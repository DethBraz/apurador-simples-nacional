package com.dethbraz.apurador.api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record LancarReceitaRequest(

        @NotNull @Min(1900) Integer ano,

        @NotNull @Min(1) @Max(12) Integer mes,

        @NotNull(message = "Valor e obrigatorio")
        @DecimalMin(value = "0.00", message = "Receita nao pode ser negativa")
        @Digits(integer = 13, fraction = 2, message = "Valor monetario aceita no maximo 2 casas decimais")
        BigDecimal valor) {
}
