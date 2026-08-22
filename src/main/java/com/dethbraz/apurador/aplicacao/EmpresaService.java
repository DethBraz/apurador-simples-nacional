package com.dethbraz.apurador.aplicacao;

import com.dethbraz.apurador.api.dto.CriarEmpresaRequest;
import com.dethbraz.apurador.api.dto.LancarReceitaRequest;
import com.dethbraz.apurador.dominio.Competencia;
import com.dethbraz.apurador.infra.jpa.EmpresaEntity;
import com.dethbraz.apurador.infra.jpa.EmpresaRepository;
import com.dethbraz.apurador.infra.jpa.ReceitaEntity;
import com.dethbraz.apurador.infra.jpa.ReceitaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class EmpresaService {

    private final EmpresaRepository empresas;
    private final ReceitaRepository receitas;

    public EmpresaService(EmpresaRepository empresas, ReceitaRepository receitas) {
        this.empresas = empresas;
        this.receitas = receitas;
    }

    @Transactional
    public EmpresaEntity criar(CriarEmpresaRequest request) {
        if (empresas.existsByCnpj(request.cnpj())) {
            throw new RecursoDuplicadoException("Ja existe empresa com o CNPJ " + request.cnpj());
        }
        Competencia inicio = Competencia.de(request.inicioAtividadeAno(), request.inicioAtividadeMes());
        return empresas.save(new EmpresaEntity(
                request.cnpj(), request.razaoSocial(), request.anexo(), inicio));
    }

    @Transactional(readOnly = true)
    public EmpresaEntity buscar(Long id) {
        return empresas.findById(id)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Empresa " + id + " nao encontrada"));
    }

    @Transactional(readOnly = true)
    public List<EmpresaEntity> listar() {
        return empresas.findAll();
    }

    /**
     * Lancar receita na mesma competencia sobrescreve o valor anterior.
     *
     * Escolha deliberada: retificacao de faturamento e rotina no fiscal, e
     * rejeitar o segundo lancamento obrigaria o usuario a apagar antes de
     * corrigir. A unique constraint no banco garante que nunca existam dois.
     */
    @Transactional
    public ReceitaEntity lancarReceita(Long empresaId, LancarReceitaRequest request) {
        EmpresaEntity empresa = buscar(empresaId);
        Competencia competencia = Competencia.de(request.ano(), request.mes());

        return receitas.findByEmpresaIdAndAnoAndMes(empresaId, request.ano(), request.mes())
                .map(existente -> {
                    existente.setValor(request.valor());
                    return existente;
                })
                .orElseGet(() -> receitas.save(
                        new ReceitaEntity(empresa, competencia, request.valor())));
    }

    @Transactional(readOnly = true)
    public List<ReceitaEntity> listarReceitas(Long empresaId) {
        buscar(empresaId);
        return receitas.findByEmpresaId(empresaId);
    }

    public static class RecursoNaoEncontradoException extends RuntimeException {
        public RecursoNaoEncontradoException(String mensagem) {
            super(mensagem);
        }
    }

    public static class RecursoDuplicadoException extends RuntimeException {
        public RecursoDuplicadoException(String mensagem) {
            super(mensagem);
        }
    }
}
