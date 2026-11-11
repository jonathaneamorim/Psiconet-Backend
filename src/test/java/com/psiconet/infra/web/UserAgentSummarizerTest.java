package com.psiconet.infra.web;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserAgentSummarizerTest {

    @Test
    void resumeNavegadoresComuns() {
        assertThat(UserAgentSummarizer.summarize(
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36 Edg/141.0.0.0"))
                .isEqualTo("Edge 141 · Windows");
        assertThat(UserAgentSummarizer.summarize(
                "Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1"))
                .isEqualTo("Safari 18 · iOS");
        assertThat(UserAgentSummarizer.summarize("Mozilla/5.0 (X11; Linux x86_64; rv:131.0) Gecko/20100101 Firefox/131.0"))
                .isEqualTo("Firefox 131 · Linux");
        assertThat(UserAgentSummarizer.summarize("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/141.0 Mobile Safari/537.36"))
                .isEqualTo("Chrome 141 · Android");
    }

    @Test
    void vazioOuDesconhecido() {
        assertThat(UserAgentSummarizer.summarize(null)).isNull();
        assertThat(UserAgentSummarizer.summarize("  ")).isNull();
        assertThat(UserAgentSummarizer.summarize("curl/8.0")).isEqualTo("Outro navegador");
    }
}
