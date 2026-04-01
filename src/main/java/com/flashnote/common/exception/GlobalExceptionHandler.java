package com.flashnote.common.exception;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashnote.common.logging.ApiLoggingSupport;
import com.flashnote.common.response.ApiResponse;
import com.flashnote.common.response.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception ex) {
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
}
