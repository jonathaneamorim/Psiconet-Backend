package com.psiconet.infra.richtext;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.psiconet.infra.exceptions.BusinessException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RichTextSanitizerTest {

    private final RichTextSanitizer sanitizer = new RichTextSanitizer();
    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode json(String s) throws Exception {
        return mapper.readTree(s.replace('\'', '"'));
    }

    @Test
    void aceitaFormatacaoPermitidaECanonicaliza() throws Exception {
        var doc = sanitizer.sanitize(json("""
                {'type':'doc','content':[
                  {'type':'heading','attrs':{'level':1,'textAlign':'justify','id':'x'},'content':[{'type':'text','text':'Sessão 3'}]},
                  {'type':'paragraph','content':[{'type':'text','text':'Relato ','marks':[{'type':'italic'},{'type':'bold'}]},{'type':'hardBreak'},{'type':'text','text':'fim'}]},
                  {'type':'bulletList','content':[{'type':'listItem','content':[{'type':'paragraph','content':[{'type':'text','text':'item'}]}]}]},
                  {'type':'orderedList','attrs':{'start':1},'content':[{'type':'listItem','content':[{'type':'paragraph','content':[{'type':'text','text':'um','marks':[{'type':'underline'}]}]}]}]}
                ]}"""));

        assertThat(doc.canonicalJson()).doesNotContain("textAlign");
        assertThat(doc.plainText()).contains("Sessão 3").contains("Relato").contains("item");
        // marcas ordenadas → mesmo documento sempre gera o mesmo JSON canônico (hash estável)
        assertThat(doc.canonicalJson()).contains("[{\"type\":\"bold\"},{\"type\":\"italic\"}]");
    }

    @Test
    void recusaNosEMarcasForaDaWhitelist() {
        assertThatThrownBy(() -> sanitizer.sanitize(json("{'type':'doc','content':[{'type':'image','attrs':{'src':'javascript:alert(1)'}}]}")))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> sanitizer.sanitize(json("{'type':'doc','content':[{'type':'paragraph','content':[{'type':'text','text':'x','marks':[{'type':'link','attrs':{'href':'http://mal'}}]}]}]}")))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> sanitizer.sanitize(json("{'type':'doc','content':[{'type':'heading','attrs':{'level':3},'content':[{'type':'text','text':'x'}]}]}")))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> sanitizer.sanitize(json("{'type':'doc','content':[{'type':'text','text':'solto'}]}")))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void htmlEmTextoEhApenasTexto() throws Exception {
        var doc = sanitizer.sanitize(json("{'type':'doc','content':[{'type':'paragraph','content':[{'type':'text','text':'<script>alert(1)</script>'}]}]}"));
        assertThat(doc.plainText()).isEqualTo("<script>alert(1)</script>");
    }

    @Test
    void recusaDocumentoQueNaoEhDoc() {
        assertThatThrownBy(() -> sanitizer.sanitize(json("{'type':'paragraph'}"))).isInstanceOf(BusinessException.class);
    }

    @Test
    void aceitaAlinhamentoETamanhoDeFonteDaWhitelist() throws Exception {
        var doc = sanitizer.sanitize(json("""
                {'type':'doc','content':[
                  {'type':'heading','attrs':{'level':2,'textAlign':'center'},'content':[{'type':'text','text':'T'}]},
                  {'type':'paragraph','attrs':{'textAlign':'right'},'content':[
                    {'type':'text','text':'grande','marks':[{'type':'textStyle','attrs':{'fontSize':'24px','color':'red'}}]}]},
                  {'type':'paragraph','attrs':{'textAlign':'left'},'content':[{'type':'text','text':'padrão'}]}
                ]}"""));

        assertThat(doc.canonicalJson())
                .contains("\"textAlign\":\"center\"")
                .contains("\"textAlign\":\"right\"")
                .contains("\"fontSize\":\"24px\"")
                .doesNotContain("color")
                .doesNotContain("\"left\"");
    }

    @Test
    void descartaTamanhoForaDaListaEAlinhamentoInvalido() throws Exception {
        var doc = sanitizer.sanitize(json("""
                {'type':'doc','content':[{'type':'paragraph','attrs':{'textAlign':'justify;position:fixed'},'content':[
                  {'type':'text','text':'x','marks':[{'type':'textStyle','attrs':{'fontSize':'999px;background:url(x)'}}]}]}]}"""));
        assertThat(doc.canonicalJson()).isEqualTo("{\"content\":[{\"content\":[{\"text\":\"x\",\"type\":\"text\"}],\"type\":\"paragraph\"}],\"type\":\"doc\"}");
    }

    @Test
    void documentosAntigosMantemOMesmoJsonCanonico() throws Exception {
        // Anotações finalizadas antes do suporte a alinhamento/tamanho precisam manter o mesmo hash
        var doc = sanitizer.sanitize(json("{'type':'doc','content':[{'type':'paragraph','content':[{'type':'text','text':'a','marks':[{'type':'italic'},{'type':'bold'}]}]}]}"));
        assertThat(doc.canonicalJson()).isEqualTo(
                "{\"content\":[{\"content\":[{\"marks\":[{\"type\":\"bold\"},{\"type\":\"italic\"}],\"text\":\"a\",\"type\":\"text\"}],\"type\":\"paragraph\"}],\"type\":\"doc\"}");
    }
}
