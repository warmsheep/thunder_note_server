package com.flashnote.common.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashnote.common.logging.ApiLoggingSupport;
import com.flashnote.common.response.ApiResponse;
import com.flashnote.common.response.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.io.IOException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ObjectMapper objectMapper;

    public GlobalExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex) {
        log.warn("业务异常: {}", ex.getMessage());
        ApiResponse<Void> response = ApiResponse.error(ex.getCode(), ex.getMessage());
        logApiErrorResponse(response);
        return jsonResponse(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException ex) {
        FieldError fieldError = ex.getBindingResult().getFieldError();
        String message = fieldError != null ? fieldError.getDefaultMessage() : ErrorCode.BAD_REQUEST.getMessage();
        log.warn("参数校验失败: {}", message);
        ApiResponse<Void> response = ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), message);
        logApiErrorResponse(response);
        return jsonResponse(response);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex) {
        log.warn("请求解析失败: {}", ex.getMessage());
        ApiResponse<Void> response = ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), "Invalid request payload");
        logApiErrorResponse(response);
        return jsonResponse(response);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex) {
        log.warn("上传文件超限: {}", ex.getMessage());
        ApiResponse<Void> response = ApiResponse.error(ErrorCode.BAD_REQUEST.getCode(), "File size exceeds 200MB limit");
        logApiErrorResponse(response);
        return jsonResponse(response);
    }

    @ExceptionHandler(AsyncRequestNotUsableException.class)
    public ResponseEntity<Void> handleAsyncRequestNotUsableException(AsyncRequestNotUsableException ex) {
        log.warn("客户端在响应写出期间断开连接: {}", ex.getMessage());
        return ResponseEntity.noContent().build();
    }

    /**
     * 静态资源不存在（如浏览器自动请求 /favicon.ico、误访问 /web/assets/xxx.js 等）时，
     * Spring 6.1+ 抛出 NoResourceFoundException。这类异常属于客户端层面的 404，
     * 不应被 fallback 到 Exception 兜底打 ERROR 级别堆栈，否则首页一刷新就会刷一行
     * "服务器内部错误" 误报。
     *
     * 这里统一返回 HTTP 404 空响应，并以 DEBUG 级别记录路径，避免日志噪音。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Void> handleNoResourceFoundException(NoResourceFoundException ex) {
        if (log.isDebugEnabled()) {
            log.debug("静态资源未找到: {}", ex.getResourcePath());
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception ex) {
        if (isClientAbort(ex)) {
            log.warn("客户端在响应写出期间断开连接: {}", ex.getMessage());
            return ResponseEntity.noContent().build();
        }
        log.error("服务器内部错误", ex);
        ApiResponse<Void> response = ApiResponse.error(ErrorCode.INTERNAL_ERROR.getCode(), "Internal server error");
        logApiErrorResponse(response);
        return jsonResponse(response);
    }

    private ResponseEntity<ApiResponse<Void>> jsonResponse(ApiResponse<Void> response) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(response);
    }

    private void logApiErrorResponse(ApiResponse<Void> response) {
        HttpServletRequest request = ApiLoggingSupport.currentRequest();
        String apiName = ApiLoggingSupport.resolveApiName(request);
        log.info("API response - interface={}, result={}", apiName, toJson(response));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }

    private boolean isClientAbort(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof AsyncRequestNotUsableException) {
                return true;
            }
            String className = current.getClass().getName();
            if (className.endsWith("ClientAbortException")) {
                return true;
            }
            if (current instanceof IOException && containsAbortMessage(current.getMessage())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean containsAbortMessage(String message) {
        if (message == null) {
            return false;
        }
        String normalized = message.toLowerCase();
        return normalized.contains("broken pipe")
                || normalized.contains("connection reset")
                || normalized.contains("连接被对方重设");
    }
}
