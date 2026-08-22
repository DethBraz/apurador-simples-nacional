package com.dethbraz.apurador.aplicacao;

import com.dethbraz.apurador.calculo.CalculadoraDas;
import com.dethbraz.apurador.calculo.CalculadoraFatorR;
import com.dethbraz.apurador.calculo.CalculadoraRbt12;
import com.dethbraz.apurador.calculo.DesenquadramentoException;
import com.dethbraz.apurador.dominio.Competencia;
import com.dethbraz.apurador.dominio.MemoriaCalculo;
import com.dethbraz.apurador.dominio.Receita;
import com.dethbraz.apurador.infra.jpa.ApuracaoEntity;
import com.dethbraz.apurador.infra.jpa.ApuracaoRepository;
import com.dethbraz.apurador.infra.jpa.EmpresaEntity;
import com.dethbraz.apurador.infra.jpa.FaixaTabelaEntity;
import com.dethbraz.apurador.infra.jpa.ReceitaEntity;
import com.dethbraz.apurador.infra.jpa.ReceitaRepository;
import com.dethbraz.apurador.infra.jpa.TabelaAnexoRepositorioJpa;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Orquestra a apuracao: busca os dados, chama o calculo puro, grava o resultado.
 *
 * Repare no que este servico NAO faz: nenhuma regra de calculo mora aqui. Ele
 * carrega receitas, delega para CalculadoraDas e persiste. Manter a regra fora
 * da camada de servico e o que permite testar o calculo sem banco.
 */
@Service
public class ApuracaoService {

    private final EmpresaService empresas;
    private final ReceitaRepository receitas;
    private final ApuracaoRepository apuracoes;
    private final CalculadoraDas calculadoraDas;
    private final CalculadoraRbt12 calculadoraRbt12;
    private final CalculadoraFatorR calculadoraFatorR;
    private final TabelaAnexoRepositorioJpa tabelas;

    public ApuracaoService(EmpresaService empresas,
                           ReceitaRepository receitas,
                           ApuracaoRepository apuracoes,
                           CalculadoraDas calculadoraDas,
                           CalculadoraRbt12 calculadoraRbt12,
                           CalculadoraFatorR calculadoraFatorR,
                           TabelaAnexoRepositorioJpa tabelas) {
        this.empresas = empresas;
        this.receitas = receitas;
        this.apuracoes = apuracoes;
        this.calculadoraDas = calculadoraDas;
        this.calculadoraRbt12 = calculadoraRbt12;
        this.calculadoraFatorR = calculadoraFatorR;
        this.tabelas = tabelas;
    }

    @Transactional
    public MemoriaCalculo apurar(Long empresaId, Competencia competencia) {
        EmpresaEntity empresa = empresas.buscar(empresaId);

        List<Receita> lancamentos = receitas.findByEmpresaId(empresaId).stream()
                .map(ReceitaEntity::paraDominio)
                .toList();

        MemoriaCalculo memoria = calculadoraDas.apurar(
                empresa.getAnexo(), competencia, lancamentos, empresa.getInicioAtividade());

        // Recupera a ENTIDADE da faixa para amarrar a apuracao a linha exata de
        // tabela que a gerou. E isso que torna o resultado auditavel depois.
        FaixaTabelaEntity faixa = tabelas
                .entidadeDaFaixa(empresa.getAnexo(), memoria.rbt12(), competencia.primeiroDia())
                .or(() -> tabelas.primeiraFaixa(empresa.getAnexo(), competencia.primeiroDia()))
                .orElseThrow(() -> new DesenquadramentoException(empresa.getAnexo(), memoria.rbt12()));

        // Reapurar a mesma competencia atualiza o registro em vez de duplicar -
        // ha unique constraint em (empresa, ano, mes) garantindo isso no banco.
        Optional<ApuracaoEntity> existente = apuracoes
                .findByEmpresaIdAndAnoAndMes(empresaId, competencia.ano(), competencia.mes());

        if (existente.isPresent()) {
            existente.get().atualizar(memoria, faixa);
        } else {
            apuracoes.save(new ApuracaoEntity(empresa, memoria, faixa));
        }

        return memoria;
    }

    @Transactional(readOnly = true)
    public Optional<MemoriaCalculo> buscar(Long empresaId, Competencia competencia) {
        empresas.buscar(empresaId);
        return apuracoes.findByEmpresaIdAndAnoAndMes(empresaId, competencia.ano(), competencia.mes())
                .map(ApuracaoEntity::paraDominio);
    }

    @Transactional(readOnly = true)
    public List<MemoriaCalculo> listar(Long empresaId) {
        empresas.buscar(empresaId);
        return apuracoes.findByEmpresaIdOrderByAnoAscMesAsc(empresaId).stream()
                .map(ApuracaoEntity::paraDominio)
                .toList();
    }

    /** RBT12 isolado - endpoint de conferencia pedido por quem opera o sistema. */
    @Transactional(readOnly = true)
    public CalculadoraRbt12.Resultado rbt12(Long empresaId, Competencia competencia) {
        EmpresaEntity empresa = empresas.buscar(empresaId);
        List<Receita> lancamentos = receitas.findByEmpresaId(empresaId).stream()
                .map(ReceitaEntity::paraDominio)
                .toList();
        return calculadoraRbt12.calcular(lancamentos, competencia, empresa.getInicioAtividade());
    }

    @Transactional(readOnly = true)
    public CalculadoraFatorR.Resultado fatorR(Long empresaId, Competencia competencia,
                                              BigDecimal folhaSalarios12Meses) {
        return calculadoraFatorR.calcular(folhaSalarios12Meses, rbt12(empresaId, competencia).valor());
    }
}
