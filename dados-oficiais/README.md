# Tabelas oficiais do Simples Nacional

Os arquivos deste diretorio sao a saida da Fase 0: as tabelas reais da
legislacao, prontas para importar via `POST /tabelas`.

Ate aqui o projeto subia com **tabelas ficticias** de demonstracao. Elas nao
existem mais no caminho padrao: o `docker-compose.yml` sobe sem o perfil `dev`,
com a tabela vazia, como em producao.

## Fonte

Lei Complementar 123/2006, anexos com a redacao dada pela **LC 155/2016**,
vigencia a partir de **01/01/2018**.

Texto consolidado: https://www.planalto.gov.br/ccivil_03/leis/lcp/lcp123.htm

Os valores foram extraidos do texto oficial, nao digitados de memoria nem
copiados de blog. Cada arquivo carrega o campo `fonte`, que fica gravado na
linha do banco junto com a faixa.

## Como importar

Com a aplicacao no ar:

```bash
cd dados-oficiais
for f in anexo_i.json anexo_iii.json anexo_v.json; do
  curl -s -X POST http://localhost:8080/tabelas \
    -H "Content-Type: application/json" --data-binary @"$f"
done
```

Conferencia rapida — toda faixa tem que somar 100%:

```sql
SELECT f.anexo, f.faixa, ROUND(SUM(r.percentual) * 100, 4) AS soma_pct
FROM faixa_tabela f JOIN faixa_reparticao r ON r.faixa_tabela_id = f.id
GROUP BY f.anexo, f.faixa ORDER BY f.anexo, f.faixa;
```

## O que esta aqui

| Anexo | Atividade | Faixas |
|---|---|---|
| I | Comercio | 6 |
| III | Servicos, Fator R >= 28% | 6 |
| V | Servicos, Fator R < 28% | 6 |

Os anexos III e V andam juntos de proposito: sao os dois lados do Fator R, que
a `CalculadoraFatorR` alterna. Sem os dois carregados, essa regra nao tem como
ser exercitada.

Na 6a faixa de cada anexo o ICMS (I) e o ISS (III e V) somem da reparticao — a
lei os retira nessa faixa. Por isso a reparticao e um mapa e nao uma lista fixa
de campos: sao 6 tributos nas faixas 1 a 5 e 5 na faixa 6.

## O que NAO esta implementado

Registrado aqui porque dado incompleto sem aviso e pior que dado ausente.

**Anexos II e IV.** Industria e servicos sem CPP no DAS. Nao foram importados;
a estrutura suporta, e so extrair do mesmo texto.

**Teto de 5% do ISS (Anexo III, 5a faixa).** A lei determina que o percentual
efetivo de ISS nao passe de 5%, e que o excedente seja redistribuido de forma
proporcional aos tributos federais da mesma faixa quando a aliquota efetiva
supera 14,92537%. A tabela importada tem os percentuais nominais; **a regra de
redistribuicao nao existe no codigo**. Apuracoes do Anexo III na 5a faixa com
aliquota efetiva acima desse ponto repartem errado.

**Sublimite estadual.** Empresas que ultrapassam o sublimite de receita
recolhem ICMS/ISS fora do DAS. Nao tratado.

**LC 214/2025.** O proprio texto do Planalto ja marca "Vide Lei Complementar
n. 214, de 2025" nestes anexos: a reforma tributaria vai alterar a partilha.
Quando entrar em vigor, o caminho correto e **importar uma versao nova com
`vigenciaInicio` na data de producao de efeitos** — nunca editar as linhas de
2018. E exatamente para isso que a tabela e versionada por vigencia.
