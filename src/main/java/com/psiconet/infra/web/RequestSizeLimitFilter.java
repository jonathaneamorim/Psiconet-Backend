package com.psiconet.infra.web;

import com.psiconet.infra.exceptions.ErrorResponse;
import com.psiconet.infra.security.JsonSecurityErrorHandlers;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * Teto de tamanho do corpo das requisições (a API é exposta direto, sem proxy na frente). Corpo maior
 * que o limite é recusado com 413 antes de ser lido para a memória; sem Content-Length (chunked), a
 * leitura é interrompida ao passar do limite. Uploads multipart seguem os limites de
 * {@code spring.servlet.multipart.*}. O webhook de vídeo, público, tem limite próprio bem menor.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestSizeLimitFilter extends OncePerRequestFilter {

    static final String VIDEO_WEBHOOK_PATH = "/webhooks/video";

    private final JsonSecurityErrorHandlers errorWriter;
    private final long maxBodyBytes;
    private final long maxWebhookBodyBytes;

    public RequestSizeLimitFilter(
            JsonSecurityErrorHandlers errorWriter,
            @Value("${psiconet.http.max-body-size:2MB}") DataSize maxBodySize,
            @Value("${psiconet.video.webhook-max-body-size:64KB}") DataSize maxWebhookBodySize
    ) {
        this.errorWriter = errorWriter;
        this.maxBodyBytes = maxBodySize.toBytes();
        this.maxWebhookBodyBytes = maxWebhookBodySize.toBytes();
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String contentType = request.getContentType();
        return contentType != null && contentType.toLowerCase().startsWith("multipart/");
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        long limit = path.equals(VIDEO_WEBHOOK_PATH) ? maxWebhookBodyBytes : maxBodyBytes;

        if (request.getContentLengthLong() > limit) {
            errorWriter.write(response, ErrorResponse.withCode(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE,
                    "PAYLOAD_TOO_LARGE", "A requisição excede o tamanho máximo permitido."));
            return;
        }
        chain.doFilter(new LimitedBodyRequest(request, limit), response);
    }

    /** Conta os bytes lidos e falha ao passar do limite (cobre corpo sem Content-Length). */
    private static final class LimitedBodyRequest extends HttpServletRequestWrapper {
        private final long limit;
        private ServletInputStream stream;

        LimitedBodyRequest(HttpServletRequest request, long limit) {
            super(request);
            this.limit = limit;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (stream == null) stream = new LimitedInputStream(super.getInputStream(), limit);
            return stream;
        }

        @Override
        public BufferedReader getReader() throws IOException {
            String encoding = getCharacterEncoding();
            Charset charset = encoding != null ? Charset.forName(encoding) : StandardCharsets.UTF_8;
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }
    }

    private static final class LimitedInputStream extends ServletInputStream {
        private final ServletInputStream delegate;
        private final long limit;
        private long read;

        LimitedInputStream(ServletInputStream delegate, long limit) {
            this.delegate = delegate;
            this.limit = limit;
        }

        @Override
        public int read() throws IOException {
            int b = delegate.read();
            if (b != -1) count(1);
            return b;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int n = delegate.read(buffer, offset, length);
            if (n > 0) count(n);
            return n;
        }

        private void count(int n) throws IOException {
            read += n;
            if (read > limit) throw new IOException("Corpo da requisição excede o tamanho máximo permitido.");
        }

        @Override public boolean isFinished() { return delegate.isFinished(); }
        @Override public boolean isReady() { return delegate.isReady(); }
        @Override public void setReadListener(ReadListener listener) { delegate.setReadListener(listener); }
    }
}
