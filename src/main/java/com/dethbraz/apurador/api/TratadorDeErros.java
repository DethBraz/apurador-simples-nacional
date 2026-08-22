package com.dethbraz.apurador.api;

import com.dethbraz.apurador.aplicacao.EmpresaService;
import com.dethbraz.apurador.calculo.DesenquadramentoException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Traducao de erro de dominio para HTTP.
 *
 * Usa ProblemDetail (RFC 7807), padrao do Spring 6 - em vez de inventar um
 * formato proprio de erro. Cliente que ja fala o padrao entende sem
 * documentacao extra.
 */
@RestControllerAdvice
public class TratadorDeErros {

    @ExceptionHandler(EmpresaService.RecursoNaoEncontradoException.class)
    public ProblemDetail naoEncontrado(EmpresaService.RecursoNaoEncontradoException e) {
        return problema(HttpStatus.NOT_FOUND, "Recurso nao encontrado", e.getMessage());
    }

    @ExceptionHandler(EmpresaService.RecursoDuplicadoException.class)
    public ProblemDetail duplicado(EmpresaService.RecursoDuplicadoException e) {
        return problema(HttpStatus.CONFLICT, "Recurso duplicado", e.getMessage());
    }

    /**
     * Desenquadramento vira 422, e nao 400 nem 500.
     *
     * A requisicao esta bem formada (nao e 400) e o servidor nao falhou (nao e
     * 500): o que acontece e que a regra de negocio impede o processamento.
     * 422 Unprocessable Entity descreve exatamente isso.
     */
    @ExceptionHandler(DesenquadramentoException.class)
    public ProblemDetail desenquadramento(DesenquadramentoException e) {
        ProblemDetail p = problema(HttpStatus.UNPROCESSABLE_ENTITY,
                "Possivel desenquadramento do Simples Nacional", e.getMessage());
        p.setProperty("rbt12", e.rbt12());
        return p;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail validacao(MethodArgumentNotValidException e) {
        Map<String, String> campos = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(erro -> campos.put(erro.getField(), erro.getDefaultMessage()));

        ProblemDetail p = problema(HttpStatus.BAD_REQUEST,
                "Dados invalidos", "Um ou mais campos nao passaram na validacao");
        p.setProperty("campos", campos);
        return p;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail argumentoInvalido(IllegalArgumentException e) {
        return problema(HttpStatus.BAD_REQUEST, "Requisicao invalida", e.getMessage());
    }

    private ProblemDetail problema(HttpStatus status, String titulo, String detalhe) {
        ProblemDetail p = ProblemDetail.forStatusAndDetail(status, detalhe);
        p.setTitle(titulo);
        p.setProperty("timestamp", Instant.now());
        return p;
    }
}
