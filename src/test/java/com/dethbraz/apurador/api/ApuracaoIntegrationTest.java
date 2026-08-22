package com.dethbraz.apurador.api;

import com.dethbraz.apurador.dominio.Anexo;
import com.dethbraz.apurador.infra.jpa.ApuracaoRepository;
import com.dethbraz.apurador.infra.jpa.EmpresaRepository;
import com.dethbraz.apurador.infra.jpa.FaixaTabelaEntity;
import com.dethbraz.apurador.infra.jpa.FaixaTabelaRepository;
import com.dethbraz.apurador.infra.jpa.ReceitaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fluxo ponta a ponta: cadastra empresa, lanca receitas, apura, confere.
 *
 * Roda contra H2 em memoria. Note o contraste com os testes de calculo: aqueles
 * nao sobem contexto nenhum e levam milissegundos; este sobe a aplicacao inteira
 * e por isso e mais lento. Ter as duas camadas separadas e o que evita que a
 * suite fique lenta a ponto de ninguem mais rodar.
 *
 * As tabelas aqui sao FICTICIAS (mesmas dos testes de unidade) - inseridas
 * direto no repositorio no setup.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ApuracaoIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private FaixaTabelaRepository faixas;

    @Autowired
    private ApuracaoRepository apuracoes;

    @Autowired
    private ReceitaRepository receitas;

    @Autowired
    private EmpresaRepository empresas;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void carregarTabelasFicticias() {
        // A limpeza segue a ordem das dependencias: apuracao referencia faixa e
        // empresa, receita referencia empresa. Apagar faixa primeiro violaria a
        // chave estrangeira - e o banco reclamaria, corretamente.
        //
        // O contexto Spring e reaproveitado entre os metodos de teste, entao sem
        // esta limpeza os dados de um teste vazam para o seguinte.
        apuracoes.deleteAll();
        receitas.deleteAll();
        empresas.deleteAll();
        faixas.deleteAll();

        LocalDate inicio = LocalDate.of(2020, 1, 1);
        faixas.saveAll(List.of(
                new FaixaTabelaEntity(Anexo.ANEXO_I, 1,
                        new BigDecimal("0.00"), new BigDecimal("100000.00"),
                        new BigDecimal("0.04"), new BigDecimal("0.00"), inicio, null, "FICTICIO"),
                new FaixaTabelaEntity(Anexo.ANEXO_I, 2,
                        new BigDecimal("100000.00"), new BigDecimal("200000.00"),
                        new BigDecimal("0.06"), new BigDecimal("2000.00"), inicio, null, "FICTICIO"),
                new FaixaTabelaEntity(Anexo.ANEXO_I, 3,
                        new BigDecimal("200000.00"), new BigDecimal("300000.00"),
                        new BigDecimal("0.08"), new BigDecimal("6000.00"), inicio, null, "FICTICIO")));
    }

    @Test
    @DisplayName("Fluxo completo: cadastro, lancamentos, apuracao com memoria de calculo")
    void fluxoCompleto() throws Exception {
        Long empresaId = cadastrarEmpresa("12345678000199");

        // 12 meses anteriores a marco/2025 com 10.000 cada -> RBT12 = 120.000
        lancarDozeMeses(empresaId, "10000.00");
        lancarReceita(empresaId, 2025, 3, "10000.00");

        // RBT12 120.000 -> faixa 2
        // aliquota efetiva = (120000 x 0,06 - 2000) / 120000 = 0,0433333333
        // DAS = 10.000 x 0,0433333333 = 433,33
        mockMvc.perform(post("/empresas/{id}/apuracoes", empresaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ano": 2025, "mes": 3}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.competencia").value("2025-03"))
                .andExpect(jsonPath("$.rbt12").value(120000.00))
                .andExpect(jsonPath("$.valorDas").value(433.33))
                .andExpect(jsonPath("$.memoriaCalculo.faixa").value(2))
                .andExpect(jsonPath("$.memoriaCalculo.aliquotaNominal").value(0.060000))
                .andExpect(jsonPath("$.memoriaCalculo.parcelaDeduzir").value(2000.00))
                .andExpect(jsonPath("$.memoriaCalculo.formula").exists())
                .andExpect(jsonPath("$.memoriaCalculo.tabelaVigenteDesde").value("2020-01-01"));
    }

    @Test
    @DisplayName("A apuracao gravada pode ser recuperada depois")
    void apuracaoEhPersistida() throws Exception {
        Long empresaId = cadastrarEmpresa("12345678000188");
        lancarDozeMeses(empresaId, "10000.00");
        lancarReceita(empresaId, 2025, 3, "10000.00");

        mockMvc.perform(post("/empresas/{id}/apuracoes", empresaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ano": 2025, "mes": 3}"""))
                .andExpect(status().isOk());

        mockMvc.perform(get("/empresas/{id}/apuracoes/2025-3", empresaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valorDas").value(433.33));
    }

    @Test
    @DisplayName("Reapurar a mesma competencia atualiza em vez de duplicar")
    void reapuracaoNaoDuplica() throws Exception {
        Long empresaId = cadastrarEmpresa("12345678000177");
        lancarDozeMeses(empresaId, "10000.00");
        lancarReceita(empresaId, 2025, 3, "10000.00");

        apurar(empresaId);
        // Retifica o faturamento do mes e reapura
        lancarReceita(empresaId, 2025, 3, "20000.00");
        apurar(empresaId);

        mockMvc.perform(get("/empresas/{id}/apuracoes", empresaId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].valorDas").value(866.67));
    }

    @Test
    @DisplayName("RBT12 acima do teto devolve 422, nao 500")
    void desenquadramentoDevolve422() throws Exception {
        Long empresaId = cadastrarEmpresa("12345678000166");
        lancarDozeMeses(empresaId, "50000.00"); // RBT12 = 600.000, acima do teto ficticio
        lancarReceita(empresaId, 2025, 3, "10000.00");

        mockMvc.perform(post("/empresas/{id}/apuracoes", empresaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ano": 2025, "mes": 3}"""))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.title").value("Possivel desenquadramento do Simples Nacional"));
    }

    @Test
    @DisplayName("CNPJ invalido devolve 400 com o campo apontado")
    void validacaoDevolve400() throws Exception {
        mockMvc.perform(post("/empresas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cnpj": "123", "razaoSocial": "Teste", "anexo": "ANEXO_I",
                                 "inicioAtividadeAno": 2015, "inicioAtividadeMes": 1}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos.cnpj").exists());
    }

    @Test
    @DisplayName("Empresa inexistente devolve 404")
    void empresaInexistenteDevolve404() throws Exception {
        mockMvc.perform(get("/empresas/{id}", 99999))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Consulta de tabela devolve as faixas vigentes na data")
    void consultaDeTabela() throws Exception {
        mockMvc.perform(get("/tabelas").param("anexo", "ANEXO_I").param("data", "2025-03-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].faixa").value(1));
    }

    // ---------- helpers ----------

    private Long cadastrarEmpresa(String cnpj) throws Exception {
        String corpo = """
                {"cnpj": "%s", "razaoSocial": "Empresa Teste LTDA", "anexo": "ANEXO_I",
                 "inicioAtividadeAno": 2015, "inicioAtividadeMes": 1}""".formatted(cnpj);

        String resposta = mockMvc.perform(post("/empresas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(corpo))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return ((ObjectNode) objectMapper.readTree(resposta)).get("id").asLong();
    }

    private void lancarDozeMeses(Long empresaId, String valor) throws Exception {
        // marco/2025 para tras: 2024-03 ate 2025-02
        for (int mes = 3; mes <= 12; mes++) {
            lancarReceita(empresaId, 2024, mes, valor);
        }
        lancarReceita(empresaId, 2025, 1, valor);
        lancarReceita(empresaId, 2025, 2, valor);
    }

    private void lancarReceita(Long empresaId, int ano, int mes, String valor) throws Exception {
        mockMvc.perform(post("/empresas/{id}/receitas", empresaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ano": %d, "mes": %d, "valor": %s}""".formatted(ano, mes, valor)))
                .andExpect(status().isNoContent());
    }

    private void apurar(Long empresaId) throws Exception {
        mockMvc.perform(post("/empresas/{id}/apuracoes", empresaId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ano": 2025, "mes": 3}"""))
                .andExpect(status().isOk());
    }
}
