# Apurador Simples Nacional

API REST de apuração do Simples Nacional: calcula o DAS de uma competência a partir
do faturamento, com **tabelas de alíquota versionadas por vigência**.

Java 17 · Spring Boot 3.2 · PostgreSQL · Flyway · Docker · JUnit 5 · AssertJ

---

## O problema que este projeto resolve

Calcular imposto é fácil. Calcular imposto **de forma reproduzível** não é.

A legislação do Simples Nacional muda: faixas, alíquotas e parcelas a deduzir são
alteradas por lei. Se as tabelas forem constantes no código, uma apuração de março
de 2025 recalculada hoje devolve um valor diferente do que foi efetivamente pago na
época — e o histórico deixa de bater.

Para software fiscal isso é inaceitável. Contador precisa reproduzir a apuração de
dois anos atrás exatamente como ela foi feita, e provar de onde saiu o número.

**A decisão central deste projeto:** as tabelas são *dados com vigência*, não lógica.

```java
// A data da competência — e não a data de hoje — resolve a tabela.
LocalDate dataVigencia = competencia.primeiroDia();
FaixaTabela faixa = tabelas.faixaPara(anexo, rbt12, dataVigencia)
```

Cada apuração grava a referência da linha de tabela aplicada, com seu período de
vigência. O passado permanece auditável.

Não existe endpoint de *update* de faixa, e isso é proposital: mudança de legislação
entra como versão nova, nunca alterando a linha antiga.

---

## O cálculo

A parte contraintuitiva: **a alíquota da tabela não é a alíquota que se paga.**

```
RBT12           = soma das receitas dos 12 meses ANTERIORES (não inclui o mês apurado)
faixa           = a linha onde limiteInferior < RBT12 <= limiteSuperior
alíquotaEfetiva = (RBT12 × alíquotaNominal − parcelaDeduzir) ÷ RBT12
DAS             = receitaDoMês × alíquotaEfetiva
```

A parcela a deduzir existe para tornar a progressividade suave: sem ela, cruzar o
limite de uma faixa aplicaria a alíquota maior sobre todo o faturamento e o imposto
daria um salto. Há um teste dedicado a comprovar essa continuidade.

O **Fator R** (`folha de salários 12 meses ÷ RBT12`) decide entre Anexo III e Anexo V
para serviços: igual ou acima de 28% vai para o III.

---

## A repartição e o problema dos centavos

O DAS é uma guia única, mas por dentro se divide entre IRPJ, CSLL, COFINS, PIS/PASEP,
CPP e — conforme o anexo — ICMS, IPI ou ISS.

Repartir dinheiro por percentual é onde software financeiro erra em silêncio.
**Arredondando cada tributo por conta própria, a soma das partes frequentemente não
bate com o total.** Um DAS de R$ 100,01 repartido em seis tributos com arredondamento
independente soma R$ 100,00 — um centavo evapora.

A solução usada aqui é o **método dos maiores restos**: trunca todos para baixo,
conta quantos centavos sobraram e distribui essas sobras aos tributos que mais
perderam no truncamento. O desempate é pela ordem do enum, para que repartir a mesma
guia duas vezes dê sempre o mesmo resultado.

```json
"reparticao": {
  "valores": { "IRPJ": 5.50, "CSLL": 3.50, "COFINS": 12.74,
               "PIS_PASEP": 2.76, "CPP": 41.51, "ICMS": 34.00 },
  "soma": 100.01,
  "confereComODas": true
}
```

Repare no `CPP: 41.51` — é onde o centavo da sobra foi parar. E o campo
`confereComODas` não é redundante: permite conferir sem somar à mão, e deixa qualquer
divergência futura visível na própria resposta.

---

## Rodando

```bash
docker compose up
```

Sobe PostgreSQL e a API em `http://localhost:8080`. Swagger em
`http://localhost:8080/swagger-ui.html`.

O compose usa o perfil `dev`, que carrega **tabelas fictícias** para a aplicação subir
demonstrável — com um aviso em log na subida. O perfil padrão sobe com a tabela vazia,
como deve ser em produção.

### Exemplo de uso

```bash
# 1. cadastrar empresa
curl -X POST localhost:8080/empresas -H 'Content-Type: application/json' \
  -d '{"cnpj":"11222333000181","razaoSocial":"Comercio LTDA","anexo":"ANEXO_I",
       "inicioAtividadeAno":2015,"inicioAtividadeMes":1}'

# 2. lançar faturamento de uma competência
curl -X POST localhost:8080/empresas/1/receitas -H 'Content-Type: application/json' \
  -d '{"ano":2025,"mes":2,"valor":10000.00}'

# 3. apurar
curl -X POST localhost:8080/empresas/1/apuracoes -H 'Content-Type: application/json' \
  -d '{"ano":2025,"mes":3}'
```

Resposta:

```json
{
  "competencia": "2025-03",
  "rbt12": 120000.00,
  "memoriaCalculo": {
    "faixa": 2,
    "aliquotaNominal": 0.060000,
    "parcelaDeduzir": 2000.00,
    "aliquotaEfetiva": 0.0433333333,
    "formula": "((120000.00 x 0.060000) - 2000.00) / 120000.00 = 0.0433333333",
    "tabelaVigenteDesde": "2020-01-01"
  },
  "valorDas": 433.33
}
```

A resposta devolve a **conta aberta**, não só o valor. Uma API que retornasse apenas
`{"das": 433.33}` seria tecnicamente correta e inutilizável num escritório de
contabilidade.

---

## Endpoints

| Método | Rota | Descrição |
|---|---|---|
| `POST` | `/empresas` | Cadastra empresa |
| `GET` | `/empresas` · `/empresas/{id}` | Consulta |
| `POST` | `/empresas/{id}/receitas` | Lança ou retifica faturamento |
| `POST` | `/empresas/{id}/apuracoes` | Apura o DAS da competência |
| `GET` | `/empresas/{id}/apuracoes/{ano}-{mes}` | Recupera apuração gravada |
| `GET` | `/empresas/{id}/rbt12` | RBT12 isolado, para conferência |
| `GET` | `/empresas/{id}/fator-r` | Fator R e anexo resultante |
| `GET` · `POST` | `/tabelas` | Consulta e importa versões de tabela |

Erros seguem **RFC 7807 (ProblemDetail)**. Desenquadramento devolve `422`, não `400`
nem `500`: a requisição está bem formada e o servidor não falhou — é a regra de
negócio que impede o processamento.

---

## Migrações

O schema é criado pelo **Flyway** (`src/main/resources/db/migration`), não por
`ddl-auto`. Em sistema fiscal o schema precisa de histórico auditável pelo mesmo
motivo que as tabelas de alíquota precisam.

O Hibernate roda com `ddl-auto=validate`: se uma entidade mudar sem a migração
correspondente, **a aplicação não sobe**. Isso já pegou um erro real durante o
desenvolvimento — a estratégia de nomes do Spring não insere underscore entre dígito
e maiúscula, então `rbt12Proporcionalizado` mapeava para `rbt12proporcionalizado` e
divergia do SQL. A validação barrou na subida, em vez de estourar em produção no
primeiro insert.

Os testes de integração rodam **as mesmas migrações** contra H2 em modo PostgreSQL —
cada execução da suíte verifica que elas aplicam limpo.

---

## Arquitetura

O núcleo de cálculo é **Java puro, sem Spring**. O domínio declara a porta
(`TabelaAnexoRepositorio`); a infraestrutura implementa.

```
dominio/     Competencia, Anexo, FaixaTabela, Receita, MemoriaCalculo
             TabelaAnexoRepositorio  ← porta
calculo/     CalculadoraRbt12, CalculadoraDas, CalculadoraFatorR   ← sem framework
infra/jpa/   entidades, repositórios, TabelaAnexoRepositorioJpa    ← adaptador
aplicacao/   ApuracaoService, EmpresaService                       ← orquestração
api/         controllers, DTOs, TratadorDeErros
```

Consequência prática: os 49 testes de cálculo rodam em **~0,2 s** sem subir contexto,
e os 7 de integração sobem a aplicação inteira. Separar as duas camadas é o que evita
a suíte ficar lenta a ponto de ninguém mais rodar.

---

## Testes

```bash
mvn test
```

**56 testes** — 49 de unidade no núcleo, 7 de integração ponta a ponta com MockMvc
sobre H2.

| Caso | Por que importa |
|---|---|
| RBT12 no valor exato do limite | O limite superior **pertence** à faixa. É o clássico `<` vs `<=` — e aqui ele vira imposto errado |
| Continuidade da alíquota na fronteira | Comprova o papel da parcela a deduzir |
| Fator R em 28% cravados | Fronteira que muda o anexo inteiro; feita com `BigDecimal` porque `0.28` não é exato em `double` |
| Empresa em início de atividade | Menos de 12 meses exige proporcionalização (média × 12) |
| Primeiro mês de atividade | Sem mês anterior: projeta a receita do próprio mês |
| RBT12 não inclui o mês apurado | Incluí-lo empurra a empresa para faixa mais alta |
| Mês sem faturamento | DAS zero sem divisão por zero |
| RBT12 acima do teto | Acusa desenquadramento em vez de calcular errado silenciosamente |
| **Reprodutibilidade histórica** | Com duas versões de tabela no banco, competência de 2025 continua usando a tabela de 2025 |
| Reapuração não duplica | Retificar e reapurar atualiza o registro; unique constraint garante no banco |
| **Soma da repartição == DAS** | Varredura de 12 valores provando que nenhum centavo se perde nem se cria |
| **Empate de restos** | Crava qual tributo recebe o centavo quando os restos são idênticos — o único teste que detecta a perda do desempate |
| Percentuais que não somam 100% | Rejeitados na criação, antes de entrarem no banco |

### Os testes têm dentes

Teste que nunca falhou não provou nada. Cada garantia central foi verificada por
mutação — quebrando o código de propósito para confirmar que a suíte acusa.

| Mutação | Resultado |
|---|---|
| `<=` → `<` em `FaixaTabela.contem()` | **5 testes falham** |
| `RoundingMode.DOWN` → `HALF_UP` em `Reparticao.distribuir()` | **4 testes falham** |
| `EnumMap` → `HashMap` **e** remoção do desempate por ordinal | **1 teste falha** (`empateSegueAOrdemDoEnum`) |

A segunda mutação é a mais instrutiva. Com `HALF_UP`, as parcelas podem subir, a soma
truncada ultrapassa o total e a sobra fica negativa — o laço de correção só sabe
*acrescentar* centavos, então o excesso passa direto. O erro sempre aparece **para
cima**: um DAS de R$ 1,00 soma R$ 1,02, dois por cento de distorção. O `DOWN` não é
preferência de estilo, é pré-condição do algoritmo.

A terceira delimita o valor do desempate explícito com honestidade. Hoje o resultado
sairia correto sem ele, porque `EnumMap` itera na ordem do enum e `List.sort` é
estável — duas propriedades implícitas. O `thenComparing(ordinal)` não corrige um bug
atual: **torna explícita uma garantia que hoje depende de detalhes de implementação**,
e sobrevive a uma troca de coleção que nenhum outro teste detectaria.

Vale registrar o contraponto: o teste `distribuicaoEDeterministica` é fraco de
propósito e está comentado como tal. Ele compara duas chamadas no mesmo processo,
então passaria mesmo com uma implementação instável. Quem protege o determinismo de
verdade é o teste de empate.

---

## Estado

- [x] **Fase 1** — núcleo de cálculo, domínio, testes de fronteira
- [x] **Fase 2** — API REST, PostgreSQL, Swagger, Docker, CI
- [x] **Fase 3** — repartição do DAS por tributo, Flyway no lugar de `ddl-auto`

Possíveis próximos passos: sublimite de ISS com redistribuição, segregação de receitas
por atividade, e autenticação.

---

## ⚠️ Sobre as tabelas de alíquota

**As tabelas fictícias deste repositório não são as do Simples Nacional.** São valores
redondos inventados (4%, 6%, 8%), para que os testes sejam conferíveis à mão e para que
ninguém os confunda com legislação. Vivem em `src/test` e no perfil `dev`, isolados do
caminho de produção.

As tabelas reais devem ser extraídas da **LC 123/2006** e do site da **Receita Federal**
e importadas via `POST /tabelas`, com a vigência e a fonte de cada versão registradas
junto do dado — a entidade tem campo `fonte` para isso.

O mesmo vale para os percentuais de repartição por tributo, que fazem parte das
mesmas tabelas oficiais. O importador valida que somem 100% antes de gravar.
