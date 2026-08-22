package com.dethbraz.apurador.api;

import com.dethbraz.apurador.aplicacao.EmpresaService;
import com.dethbraz.apurador.api.dto.CriarEmpresaRequest;
import com.dethbraz.apurador.api.dto.EmpresaResponse;
import com.dethbraz.apurador.api.dto.LancarReceitaRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/empresas")
@Tag(name = "Empresas", description = "Cadastro de empresas e lancamento de faturamento")
public class EmpresaController {

    private final EmpresaService service;

    public EmpresaController(EmpresaService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Cadastra uma empresa")
    public ResponseEntity<EmpresaResponse> criar(@Valid @RequestBody CriarEmpresaRequest request) {
        var empresa = service.criar(request);
        return ResponseEntity
                .created(URI.create("/empresas/" + empresa.getId()))
                .body(EmpresaResponse.de(empresa));
    }

    @GetMapping
    @Operation(summary = "Lista as empresas cadastradas")
    public List<EmpresaResponse> listar() {
        return service.listar().stream().map(EmpresaResponse::de).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Busca uma empresa")
    public EmpresaResponse buscar(@PathVariable Long id) {
        return EmpresaResponse.de(service.buscar(id));
    }

    @PostMapping("/{id}/receitas")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Lanca ou retifica o faturamento de uma competencia",
            description = "Lancar duas vezes na mesma competencia sobrescreve o valor anterior - "
                    + "retificacao e rotina no fiscal.")
    public void lancarReceita(@PathVariable Long id,
                              @Valid @RequestBody LancarReceitaRequest request) {
        service.lancarReceita(id, request);
    }
}
