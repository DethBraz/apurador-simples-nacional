package com.dethbraz.apurador.config;

import com.dethbraz.apurador.calculo.CalculadoraDas;
import com.dethbraz.apurador.calculo.CalculadoraFatorR;
import com.dethbraz.apurador.calculo.CalculadoraRbt12;
import com.dethbraz.apurador.dominio.TabelaAnexoRepositorio;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * As calculadoras viram beans AQUI, e nao com @Component nelas mesmas.
 *
 * Motivo: manter o pacote de calculo livre de anotacoes de framework. Assim ele
 * continua sendo Java puro, instanciavel com new em qualquer teste, e a decisao
 * de expo-lo ao Spring fica isolada nesta classe de configuracao.
 */
@Configuration
public class CalculoConfig {

    @Bean
    public CalculadoraRbt12 calculadoraRbt12() {
        return new CalculadoraRbt12();
    }

    @Bean
    public CalculadoraDas calculadoraDas(TabelaAnexoRepositorio tabelas, CalculadoraRbt12 rbt12) {
        return new CalculadoraDas(tabelas, rbt12);
    }

    @Bean
    public CalculadoraFatorR calculadoraFatorR() {
        return new CalculadoraFatorR();
    }
}
