package com.psiconet.infra.legal;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LegalDocumentRegistryTest {

    @Test
    void leCabecalhoESeparaOConteudo() {
        LegalDocument doc = LegalDocumentRegistry.parse(LegalDocumentType.TERMS,
                "---\ntipo: TERMS\ntitulo: Termos de Uso\nversao: 2.1.0\nvigencia: 2027-01-15\n---\n\n## Seção\n\nTexto.\n");
        assertThat(doc.version()).isEqualTo("2.1.0");
        assertThat(doc.effectiveDate()).isEqualTo(LocalDate.of(2027, 1, 15));
        assertThat(doc.title()).isEqualTo("Termos de Uso");
        assertThat(doc.content()).isEqualTo("## Seção\n\nTexto.");
    }

    @Test
    void recusaDocumentoSemCabecalhoComTipoTrocadoOuVersaoInvalida() {
        assertThatThrownBy(() -> LegalDocumentRegistry.parse(LegalDocumentType.TERMS, "# Sem cabeçalho"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> LegalDocumentRegistry.parse(LegalDocumentType.TERMS,
                "---\ntipo: PRIVACY\nversao: 1.0.0\nvigencia: 2026-10-02\n---\nx"))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> LegalDocumentRegistry.parse(LegalDocumentType.TERMS,
                "---\ntipo: TERMS\nversao: v1\nvigencia: 2026-10-02\n---\nx"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void documentosEmpacotadosCarregam() {
        LegalDocumentRegistry registry = new LegalDocumentRegistry();
        registry.load();
        for (LegalDocumentType type : LegalDocumentType.values()) {
            assertThat(registry.get(type).content()).isNotBlank();
        }
    }
}
