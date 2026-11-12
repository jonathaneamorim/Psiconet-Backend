package com.psiconet.infra.exceptions;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.ServletRequestBindingException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.HashMap;
import java.util.Map;

/**
 * Handler global que centraliza a captura de exceções e retorna respostas padronizadas.
 * Garante que detalhes internos do servidor não sejam vazados e que o cliente receba
 * mensagens de erro claras e consistentes.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex) {
        log.error("Erro interno inesperado: ", ex);
        ErrorResponse response = new ErrorResponse(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Ocorreu um erro interno inesperado no servidor."
        );
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException ex) {
        log.warn("Conflito de dados no campo '{}'.", ex.getField());
        Map<String, String> errors = ex.getField() == null ? null : Map.of(ex.getField(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new ErrorResponse(HttpStatus.CONFLICT.value(), ex.getMessage(), errors));
    }

    @ExceptionHandler(com.psiconet.infra.security.InvalidSessionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidSession(com.psiconet.infra.security.InvalidSessionException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.withCode(HttpStatus.UNAUTHORIZED.value(), "SESSION_EXPIRED", ex.getMessage()));
    }

    @ExceptionHandler(LegalVersionOutdatedException.class)
    public ResponseEntity<ErrorResponse> handleLegalVersionOutdated(LegalVersionOutdatedException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.withCode(HttpStatus.CONFLICT.value(), "LEGAL_VERSION_OUTDATED", ex.getMessage()));
    }

    @ExceptionHandler(AccountStatusException.class)
    public ResponseEntity<ErrorResponse> handleAccountStatus(AccountStatusException ex) {
        log.warn("Login recusado pelo status da conta: {}", ex.getCode());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.withCode(HttpStatus.FORBIDDEN.value(), ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ErrorResponse> handleTooManyRequests(TooManyRequestsException ex) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.getRetryAfterSeconds()))
                .body(ErrorResponse.withCode(HttpStatus.TOO_MANY_REQUESTS.value(), "RATE_LIMITED", ex.getMessage()));
    }

    @ExceptionHandler(com.psiconet.infra.video.VideoAccessException.class)
    public ResponseEntity<ErrorResponse> handleVideoAccess(com.psiconet.infra.video.VideoAccessException ex) {
        log.warn("Entrada na sala de vídeo recusada: {}", ex.getCode());
        return ResponseEntity.status(ex.getStatus())
                .body(ErrorResponse.withCode(ex.getStatus(), ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(com.psiconet.infra.video.InvalidWebhookSignatureException.class)
    public ResponseEntity<ErrorResponse> handleInvalidWebhook(com.psiconet.infra.video.InvalidWebhookSignatureException ex) {
        log.warn("Webhook de vídeo recusado: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.withCode(HttpStatus.UNAUTHORIZED.value(), "UNAUTHENTICATED", "Assinatura inválida."));
    }

    @ExceptionHandler(ClinicalUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleClinicalUnavailable(ClinicalUnavailableException ex) {
        log.error("Requisição clínica recusada: chave-mestra não configurada.");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ErrorResponse.withCode(HttpStatus.SERVICE_UNAVAILABLE.value(), "CLINICAL_UNAVAILABLE", ex.getMessage()));
    }

    // Nenhum código da aplicação lança IllegalArgumentException para o cliente (regra vira BusinessException):
    // quando ela chega aqui vem de biblioteca/JDK, e a mensagem é interna — não é devolvida nem logada inteira.
    @ExceptionHandler({IllegalArgumentException.class, java.time.DateTimeException.class})
    public ResponseEntity<ErrorResponse> handleIllegalArgument(RuntimeException ex) {
        log.warn("Argumento inválido: {}", ex.getClass().getSimpleName());
        ErrorResponse response = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Requisição inválida. Verifique os dados enviados."
        );
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(EntityNotFoundException ex) {
        log.warn("Recurso não encontrado: {}", ex.getMessage());
        ErrorResponse response = new ErrorResponse(
                HttpStatus.NOT_FOUND.value(),
                ex.getMessage()
        );
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(BadCredentialsException ex) {
        log.warn("Falha na autenticação: credenciais inválidas.");
        ErrorResponse response = new ErrorResponse(
                HttpStatus.UNAUTHORIZED.value(),
                "Credenciais inválidas. Verifique seu e-mail e senha."
        );
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        log.warn("Acesso negado: {}", ex.getMessage());
        ErrorResponse response = new ErrorResponse(
                HttpStatus.FORBIDDEN.value(),
                "Você não tem permissão para acessar este recurso."
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, BindException.class})
    public ResponseEntity<ErrorResponse> handleValidationExceptions(BindException ex) {
        Map<String, String> errors = new HashMap<>();

        ex.getBindingResult().getAllErrors().forEach((error) -> {
            String fieldName = error instanceof FieldError fieldError ? fieldError.getField() : error.getObjectName();
            errors.putIfAbsent(fieldName, error.getDefaultMessage());
        });

        // Só os nomes dos campos: as mensagens podem ecoar o valor enviado.
        log.warn("Falha na validação de dados: {}", errors.keySet());

        ErrorResponse response = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                "Erro de validação nos campos enviados.",
                errors
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @ExceptionHandler(org.springframework.security.authentication.DisabledException.class)
    public ResponseEntity<ErrorResponse> handleDisabled(org.springframework.security.authentication.DisabledException ex) {
        log.warn("Tentativa de login em conta desativada.");
        ErrorResponse response = new ErrorResponse(
                HttpStatus.FORBIDDEN.value(),
                "Sua conta está inativa ou aguardando verificação. Por favor, entre em contato com o administrador."
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    @ExceptionHandler(org.springframework.security.authentication.LockedException.class)
    public ResponseEntity<ErrorResponse> handleLocked(org.springframework.security.authentication.LockedException ex) {
        log.warn("Tentativa de login em conta bloqueada.");
        ErrorResponse response = new ErrorResponse(
                HttpStatus.FORBIDDEN.value(),
                "Sua conta está bloqueada. Por favor, entre em contato com o suporte."
        );
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(response);
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException ex) {
        log.warn("Violação de regra de negócio: {}", ex.getMessage());
        Map<String, String> errors = null;

        if (ex.getField() != null) {
            errors = new HashMap<>();
            errors.put(ex.getField(), ex.getMessage());
        }

        ErrorResponse response = new ErrorResponse(
                HttpStatus.BAD_REQUEST.value(),
                ex.getMessage(),
                errors
        );

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    // Conflito de escrita concorrente (ex.: duas requisições simultâneas aprovando/rejeitando o
    // mesmo pagamento, ou tentando o mesmo código de redefinição de senha em paralelo) — protegido
    // por @Version nas entidades sensíveis. Sem esse handler, cairia no handleGeneric como 500.
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        log.warn("Conflito de concorrência ao atualizar registro: {}", ex.getMessage());
        ErrorResponse response = new ErrorResponse(
                HttpStatus.CONFLICT.value(),
                "Este registro foi alterado por outra requisição ao mesmo tempo. Atualize a página e tente novamente."
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    // Violação de constraint única/FK no banco (ex.: duas tentativas concorrentes de cadastro com o
    // mesmo e-mail/CPF passando ambas pela validação de aplicação antes do insert). A constraint do
    // banco já impede o dado duplicado — aqui só evitamos vazar um 500 genérico para esse caso.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        // Não loga a mensagem do banco: ela costuma trazer os valores (ex.: CPF/e-mail duplicado).
        log.warn("Violação de integridade de dados: {}", ex.getMostSpecificCause().getClass().getSimpleName());
        ErrorResponse response = new ErrorResponse(
                HttpStatus.CONFLICT.value(),
                "Não foi possível concluir a operação: um registro conflitante já existe."
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class,
            MultipartException.class,
            ServletRequestBindingException.class
    })
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception ex) {
        log.warn("Requisição malformada: {}", ex.getClass().getSimpleName());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(HttpStatus.BAD_REQUEST.value(), "Requisição inválida. Verifique os dados enviados."));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ErrorResponse(HttpStatus.NOT_FOUND.value(), "Recurso não encontrado."));
    }

    // Validação de parâmetros simples (@RequestParam/@PathVariable com @Min, @Size...): 400 com erros por campo.
    @ExceptionHandler({ConstraintViolationException.class, HandlerMethodValidationException.class})
    public ResponseEntity<ErrorResponse> handleParameterValidation(Exception ex) {
        Map<String, String> errors = new HashMap<>();
        if (ex instanceof ConstraintViolationException cve) {
            cve.getConstraintViolations().forEach(v -> {
                String path = v.getPropertyPath().toString();
                errors.putIfAbsent(path.substring(path.lastIndexOf('.') + 1), v.getMessage());
            });
        } else if (ex instanceof HandlerMethodValidationException hmve) {
            hmve.getParameterValidationResults().forEach(r -> r.getResolvableErrors().forEach(e ->
                    errors.putIfAbsent(r.getMethodParameter().getParameterName(), e.getDefaultMessage())));
        }
        log.warn("Falha na validação de parâmetros: {}", errors.keySet());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ErrorResponse(HttpStatus.BAD_REQUEST.value(), "Erro de validação nos campos enviados.",
                        errors.isEmpty() ? null : errors));
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(new ErrorResponse(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value(), "Formato de envio não suportado para este recurso."));
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ErrorResponse> handleMediaTypeNotAcceptable(HttpMediaTypeNotAcceptableException ex) {
        return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE)
                .body(new ErrorResponse(HttpStatus.NOT_ACCEPTABLE.value(), "Formato de resposta não disponível para este recurso."));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(new ErrorResponse(HttpStatus.METHOD_NOT_ALLOWED.value(), "Método não suportado para este recurso."));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        log.warn("Upload rejeitado por exceder o tamanho máximo permitido.");
        ErrorResponse response = new ErrorResponse(
                HttpStatus.PAYLOAD_TOO_LARGE.value(),
                "Arquivo muito grande. Reduza o tamanho e tente novamente."
        );
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(response);
    }
}
