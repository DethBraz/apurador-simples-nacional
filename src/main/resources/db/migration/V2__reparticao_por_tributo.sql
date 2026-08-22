-- V2: repartição do DAS por tributo.
--
-- O DAS e uma guia unica, mas por dentro se divide entre IRPJ, CSLL, COFINS,
-- PIS/PASEP, CPP e, conforme o anexo, ICMS, IPI ou ISS. Cada faixa declara os
-- percentuais; cada apuracao grava os valores resultantes.
--
-- Entra como migracao separada, e nao editando a V1, porque a V1 ja foi
-- aplicada. Alterar uma migracao ja executada quebra o checksum do Flyway - e,
-- pior, faz bancos diferentes divergirem silenciosamente.

CREATE TABLE faixa_reparticao (
    faixa_tabela_id BIGINT         NOT NULL REFERENCES faixa_tabela (id),
    tributo         VARCHAR(20)    NOT NULL,
    percentual      NUMERIC(10, 6) NOT NULL,
    PRIMARY KEY (faixa_tabela_id, tributo)
);

CREATE TABLE apuracao_tributo (
    apuracao_id BIGINT         NOT NULL REFERENCES apuracao (id),
    tributo     VARCHAR(20)    NOT NULL,
    valor       NUMERIC(15, 2) NOT NULL,
    PRIMARY KEY (apuracao_id, tributo)
);
