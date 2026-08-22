package com.dethbraz.apurador.api;

import com.dethbraz.apurador.dominio.Anexo;
import com.dethbraz.apurador.dominio.FaixaTabela;
import com.dethbraz.apurador.dominio.Reparticao;
import com.dethbraz.apurador.dominio.Tributo;
import com.dethbraz.apurador.infra.jpa.FaixaTabelaEntity;
import com.dethbraz.apurador.infra.jpa.FaixaTabelaRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/tabelas")
@Tag(name = "Tabelas", description = "Faixas por anexo, versionadas por vigencia")
public class TabelaController {

    private final FaixaTabelaRepository repository;

    public TabelaController(FaixaTabelaRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    @Operation(summary = "Consulta as faixas vigentes de um anexo em uma data",
            description = "Sem o parametro data, usa a data de hoje.")
    public List<FaixaTabela> consultar(
            @RequestParam Anexo anexo,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate data) {

        LocalDate referencia = data != null ? data : LocalDate.now();
        return repository.vigentesEm(anexo, referencia).stream()
                .map(FaixaTabelaEntity::paraDominio)
                .toList();
    }

    /**
     * Importacao das tabelas oficiais (saida da Fase 0).
     *
     * Nao existe endpoint de UPDATE de faixa, e isso e proposital: mudanca de
     * legislacao entra como versao nova com vigencia nova, nunca alterando a
     * linha antiga. Editar o passado quebraria a reprodutibilidade das apuracoes
     * ja realizadas.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Importa faixas de tabela",
            description = "Carrega uma versao de tabela. Para atualizar a legislacao, importe uma "
                    + "nova versao com vigenciaInicio posterior - nao edite a anterior.")
    public Map<String, Object> importar(@Valid @RequestBody ImportarTabelaRequest request) {
        List<FaixaTabelaEntity> entidades = request.faixas().stream()
                .map(f -> {
                    // Valida a reparticao ANTES de gravar: o construtor de
                    // Reparticao rejeita percentuais que nao somam 100%. Sem
                    // isso, dado inconsistente entraria no banco e so apareceria
                    // na hora de repartir uma guia.
                    Map<Tributo, BigDecimal> reparticao =
                            f.reparticao() == null ? Map.of() : f.reparticao();
                    new Reparticao(reparticao);

                    return new FaixaTabelaEntity(
                            request.anexo(), f.faixa(), f.limiteInferior(), f.limiteSuperior(),
                            f.aliquotaNominal(), f.parcelaDeduzir(),
                            request.vigenciaInicio(), request.vigenciaFim(), request.fonte(),
                            reparticao);
                })
                .toList();

        repository.saveAll(entidades);

        return Map.of(
                "anexo", request.anexo(),
                "faixasImportadas", entidades.size(),
                "vigenciaInicio", request.vigenciaInicio(),
                "fonte", request.fonte() == null ? "nao informada" : request.fonte());
    }

    public record ImportarTabelaRequest(
            @NotNull Anexo anexo,
            @NotNull LocalDate vigenciaInicio,
            LocalDate vigenciaFim,
            String fonte,
            @NotEmpty(message = "Informe ao menos uma faixa") List<FaixaRequest> faixas) {
    }

    /**
     * @param reparticao percentual de cada tributo em fracao (0.055 = 5,5%).
     *                   Opcional: se omitido, a faixa fica sem detalhamento e a
     *                   apuracao devolve apenas o DAS total. Se informado,
     *                   precisa somar 100%.
     */
    public record FaixaRequest(
            @NotNull Integer faixa,
            @NotNull BigDecimal limiteInferior,
            @NotNull BigDecimal limiteSuperior,
            @NotNull BigDecimal aliquotaNominal,
            @NotNull BigDecimal parcelaDeduzir,
            Map<Tributo, BigDecimal> reparticao) {
    }
}
