package com.dethbraz.apurador.config;

import com.dethbraz.apurador.dominio.Anexo;
import com.dethbraz.apurador.dominio.Tributo;
import com.dethbraz.apurador.infra.jpa.FaixaTabelaEntity;
import com.dethbraz.apurador.infra.jpa.FaixaTabelaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * ============================================================================
 * DADOS FICTICIOS - PERFIL DE DESENVOLVIMENTO APENAS
 *
 * As faixas abaixo NAO sao as tabelas do Simples Nacional. Sao valores
 * inventados, redondos de proposito, para que a aplicacao suba demonstravel
 * antes da Fase 0 estar pronta.
 *
 * A classe so e instanciada quando apurador.seed-ficticio=true, propriedade
 * definida exclusivamente em application-dev.properties. No perfil padrao a
 * tabela sobe VAZIA - que e o comportamento correto para producao, onde os
 * dados devem ser importados via POST /tabelas a partir da LC 123/2006.
 *
 * O log de aviso na subida existe para que ninguem rode isso achando que sao
 * as aliquotas reais.
 * ============================================================================
 */
@Component
@ConditionalOnProperty(name = "apurador.seed-ficticio", havingValue = "true")
public class SeedDesenvolvimento implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedDesenvolvimento.class);

    private final FaixaTabelaRepository repository;

    public SeedDesenvolvimento(FaixaTabelaRepository repository) {
        this.repository = repository;
    }

    @Override
    public void run(String... args) {
        if (repository.count() > 0) {
            return;
        }

        LocalDate inicio = LocalDate.of(2020, 1, 1);
        String fonte = "FICTICIO - dados de demonstracao, nao usar";

        repository.saveAll(List.of(
                faixa(1, "0.00", "100000.00", "0.04", "0.00", inicio, fonte),
                faixa(2, "100000.00", "200000.00", "0.06", "2000.00", inicio, fonte),
                faixa(3, "200000.00", "300000.00", "0.08", "6000.00", inicio, fonte)));

        log.warn("=================================================================");
        log.warn(" TABELAS FICTICIAS CARREGADAS (perfil dev)");
        log.warn(" Estes NAO sao os valores do Simples Nacional.");
        log.warn(" Importe as tabelas oficiais via POST /tabelas antes de usar.");
        log.warn("=================================================================");
    }

    private FaixaTabelaEntity faixa(int numero, String inferior, String superior,
                                    String aliquota, String parcelaDeduzir,
                                    LocalDate inicio, String fonte) {
        return new FaixaTabelaEntity(Anexo.ANEXO_I, numero,
                new BigDecimal(inferior), new BigDecimal(superior),
                new BigDecimal(aliquota), new BigDecimal(parcelaDeduzir),
                inicio, null, fonte, reparticaoFicticia());
    }

    /** Percentuais ficticios que somam 100%, so para a demo mostrar a reparticao. */
    private Map<Tributo, BigDecimal> reparticaoFicticia() {
        Map<Tributo, BigDecimal> p = new EnumMap<>(Tributo.class);
        p.put(Tributo.IRPJ, new BigDecimal("0.0550"));
        p.put(Tributo.CSLL, new BigDecimal("0.0350"));
        p.put(Tributo.COFINS, new BigDecimal("0.1274"));
        p.put(Tributo.PIS_PASEP, new BigDecimal("0.0276"));
        p.put(Tributo.CPP, new BigDecimal("0.4150"));
        p.put(Tributo.ICMS, new BigDecimal("0.3400"));
        return p;
    }
}
