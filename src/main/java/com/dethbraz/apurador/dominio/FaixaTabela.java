package com.dethbraz.apurador.dominio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Uma linha da tabela de um anexo, valida durante um periodo de vigencia.
 *
 * ESTA CLASSE E O CORACAO DO PROJETO.
 *
 * A alternativa preguicosa seria constantes no codigo (ALIQUOTA_FAIXA_1 = ...).
 * O problema: quando a lei muda, o codigo antigo desaparece, e recalcular uma
 * competencia passada passa a devolver um valor diferente do que foi pago na
 * epoca. Para software fiscal isso e inaceitavel - contador precisa reproduzir
 * a apuracao de dois anos atras exatamente como ela foi feita.
 *
 * Por isso a tabela e DADO com vigencia, nao logica. A apuracao resolve a linha
 * pela data da competencia, e o passado permanece reproduzivel.
 *
 * @param vigenciaFim null significa "vigente por prazo indeterminado".
 * @param reparticao  como o DAS desta faixa se divide entre os tributos; pode
 *                    ser {@link Reparticao#naoInformada()} enquanto o dado nao
 *                    foi cadastrado.
 */
public record FaixaTabela(
        Anexo anexo,
        int faixa,
        BigDecimal limiteInferior,
        BigDecimal limiteSuperior,
        BigDecimal aliquotaNominal,
        BigDecimal parcelaDeduzir,
        LocalDate vigenciaInicio,
        LocalDate vigenciaFim,
        Reparticao reparticao
) {

    /** Construtor de conveniencia para faixas sem reparticao cadastrada. */
    public FaixaTabela(Anexo anexo, int faixa,
                       BigDecimal limiteInferior, BigDecimal limiteSuperior,
                       BigDecimal aliquotaNominal, BigDecimal parcelaDeduzir,
                       LocalDate vigenciaInicio, LocalDate vigenciaFim) {
        this(anexo, faixa, limiteInferior, limiteSuperior, aliquotaNominal,
                parcelaDeduzir, vigenciaInicio, vigenciaFim, Reparticao.naoInformada());
    }

    public FaixaTabela {
        Objects.requireNonNull(anexo, "anexo");
        reparticao = reparticao == null ? Reparticao.naoInformada() : reparticao;
        Objects.requireNonNull(limiteInferior, "limiteInferior");
        Objects.requireNonNull(limiteSuperior, "limiteSuperior");
        Objects.requireNonNull(aliquotaNominal, "aliquotaNominal");
        Objects.requireNonNull(parcelaDeduzir, "parcelaDeduzir");
        Objects.requireNonNull(vigenciaInicio, "vigenciaInicio");

        if (limiteSuperior.compareTo(limiteInferior) <= 0) {
            throw new IllegalArgumentException(
                    "limiteSuperior deve ser maior que limiteInferior na faixa " + faixa);
        }
        if (vigenciaFim != null && vigenciaFim.isBefore(vigenciaInicio)) {
            throw new IllegalArgumentException("vigenciaFim anterior a vigenciaInicio");
        }
    }

    /**
     * A faixa cobre este RBT12?
     *
     * ATENCAO AO INTERVALO - e aqui que mora o bug classico.
     * A tabela oficial le "de 180.000,01 ate 360.000,00", ou seja: o limite
     * superior PERTENCE a faixa, e o inferior NAO. Logo a comparacao correta e
     * (rbt12 > limiteInferior) e (rbt12 <= limiteSuperior).
     *
     * Trocar um <= por < aqui joga a empresa para a faixa seguinte no valor
     * exato de corte, e isso vira imposto errado. Existe teste dedicado a este
     * comportamento em CalculadoraDasTest.
     */
    public boolean contem(BigDecimal rbt12) {
        return rbt12.compareTo(limiteInferior) > 0
                && rbt12.compareTo(limiteSuperior) <= 0;
    }

    /** A linha estava valida nesta data? */
    public boolean vigenteEm(LocalDate data) {
        boolean jaComecou = !data.isBefore(vigenciaInicio);
        boolean naoTerminou = vigenciaFim == null || !data.isAfter(vigenciaFim);
        return jaComecou && naoTerminou;
    }
}
