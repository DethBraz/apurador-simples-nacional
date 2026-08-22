package com.dethbraz.apurador.api;

import com.dethbraz.apurador.aplicacao.ApuracaoService;
import com.dethbraz.apurador.aplicacao.EmpresaService;
import com.dethbraz.apurador.api.dto.ApuracaoResponse;
import com.dethbraz.apurador.api.dto.ApurarRequest;
import com.dethbraz.apurador.dominio.Competencia;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/empresas/{empresaId}")
@Tag(name = "Apuracao", description = "Calculo do DAS com memoria de calculo")
public class ApuracaoController {

    private final ApuracaoService service;

    public ApuracaoController(ApuracaoService service) {
        this.service = service;
    }

    @PostMapping("/apuracoes")
    @Operation(summary = "Apura o DAS de uma competencia",
            description = "Devolve a memoria de calculo completa: RBT12, faixa, aliquota nominal, "
                    + "parcela deduzida, aliquota efetiva e a vigencia da tabela aplicada. "
                    + "Reapurar a mesma competencia atualiza o registro.")
    public ApuracaoResponse apurar(@PathVariable Long empresaId,
                                   @Valid @RequestBody ApurarRequest request) {
        var memoria = service.apurar(empresaId, Competencia.de(request.ano(), request.mes()));
        return ApuracaoResponse.de(memoria);
    }

    @GetMapping("/apuracoes/{ano}-{mes}")
    @Operation(summary = "Recupera uma apuracao ja gravada")
    public ApuracaoResponse buscar(@PathVariable Long empresaId,
                                   @PathVariable int ano,
                                   @PathVariable int mes) {
        return service.buscar(empresaId, Competencia.de(ano, mes))
                .map(ApuracaoResponse::de)
                .orElseThrow(() -> new EmpresaService.RecursoNaoEncontradoException(
                        "Nenhuma apuracao gravada para a competencia %04d-%02d".formatted(ano, mes)));
    }

    @GetMapping("/apuracoes")
    @Operation(summary = "Lista as apuracoes da empresa")
    public List<ApuracaoResponse> listar(@PathVariable Long empresaId) {
        return service.listar(empresaId).stream().map(ApuracaoResponse::de).toList();
    }

    @GetMapping("/rbt12")
    @Operation(summary = "RBT12 isolado",
            description = "Endpoint de conferencia: expoe so a receita bruta dos 12 meses "
                    + "anteriores, e se ela foi proporcionalizada por inicio de atividade.")
    public Map<String, Object> rbt12(@PathVariable Long empresaId,
                                     @RequestParam int ano,
                                     @RequestParam int mes) {
        var resultado = service.rbt12(empresaId, Competencia.de(ano, mes));
        return Map.of(
                "competencia", Competencia.de(ano, mes).toString(),
                "rbt12", resultado.valor(),
                "proporcionalizado", resultado.proporcionalizado());
    }

    @GetMapping("/fator-r")
    @Operation(summary = "Fator R e anexo resultante",
            description = "folha de salarios dos 12 meses dividida pelo RBT12. "
                    + "Igual ou acima de 28% enquadra no Anexo III.")
    public Map<String, Object> fatorR(@PathVariable Long empresaId,
                                      @RequestParam int ano,
                                      @RequestParam int mes,
                                      @RequestParam BigDecimal folhaSalarios12Meses) {
        var resultado = service.fatorR(empresaId, Competencia.de(ano, mes), folhaSalarios12Meses);
        return Map.of(
                "fatorR", resultado.fatorR(),
                "percentual", resultado.percentual(),
                "anexoResultante", resultado.anexo());
    }
}
