package com.dethbraz.apurador.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ApurarRequest(

        @NotNull @Min(1900) Integer ano,

        @NotNull @Min(1) @Max(12) Integer mes) {
}
