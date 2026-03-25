package com.flashnote.common.logging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import org.springframework.validation.BindingResult;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;

import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;

import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Aspect
@Component
public class ApiLoggingAspect {
    private static final Logger log = LoggerFactory.getLogger(ApiLoggingAspect.class);
    private static final int MAX_LOG_LENGTH = 4000;

    private final ObjectMapper objectMapper;

    public ApiLoggingAspect(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Around("within(@org.springframework.web.bind.annotation.RestController *)")
    public Object logApiRequestAndResponse(ProceedingJoinPoint joinPoint) throws Throwable {
        HttpServletRequest request = ApiLoggingSupport.currentRequest();
        String apiName = resolveApiName(joinPoint, request);
        String requestPayload = buildRequestPayload(joinPoint.getArgs(), ((MethodSignature) joinPoint.getSignature()).getParameterNames());
        log.info("API request - interface={}, params={}", apiName, requestPayload);

        try {
            Object response = joinPoint.proceed();
            log.info("API response - interface={}, result={}", apiName, toJson(response));
            return response;
        } catch (Throwable throwable) {
            log.error("API exception - interface={}, errorType={}, errorMessage={}",
                    apiName,
                    throwable.getClass().getSimpleName(),
                    truncate(throwable.getMessage()),
                    throwable);
            throw throwable;
        }
    }

    private String resolveApiName(ProceedingJoinPoint joinPoint, HttpServletRequest request) {
        String method = request == null ? "UNKNOWN" : request.getMethod();
        String uri = request == null ? "UNKNOWN" : request.getRequestURI();
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        return method + " " + uri + " -> " + signature.getDeclaringType().getSimpleName() + "." + signature.getName();
    }

    private String buildRequestPayload(Object[] args, String[] parameterNames) {
        Map<String, Object> requestMap = new LinkedHashMap<>();
        for (int i = 0; i < args.length; i++) {
            Object sanitized = sanitizeValue(args[i]);
            if (sanitized == null) {
                continue;
            }
            String parameterName = parameterNames != null && i < parameterNames.length && parameterNames[i] != null
                    ? parameterNames[i]
                    : "arg" + i;
            requestMap.put(parameterName, sanitized);
        }
        return toJson(requestMap);
    }

    private Object sanitizeValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof HttpServletRequest || value instanceof HttpServletResponse
                || value instanceof BindingResult || value instanceof InputStream
                || value instanceof OutputStream || value instanceof ModelAndView) {
            return null;
        }
        if (value instanceof Authentication authentication) {
            Map<String, Object> authMap = new LinkedHashMap<>();
            authMap.put("name", authentication.getName());
            authMap.put("authorities", authentication.getAuthorities());
            return authMap;
        }
        if (value instanceof MultipartFile file) {
            return describeMultipartFile(file);
        }
        if (value instanceof MultipartFile[] files) {
            List<Map<String, Object>> fileDescriptions = new ArrayList<>();
            for (MultipartFile file : files) {
                fileDescriptions.add(describeMultipartFile(file));
            }
            return fileDescriptions;
        }
        if (value instanceof Collection<?> collection) {
            List<Object> sanitizedCollection = new ArrayList<>();
            for (Object item : collection) {
                sanitizedCollection.add(sanitizeValue(item));
            }
            return sanitizedCollection;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> sanitizedMap = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                sanitizedMap.put(String.valueOf(entry.getKey()), sanitizeValue(entry.getValue()));
            }
            return sanitizedMap;
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            List<Object> sanitizedArray = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                sanitizedArray.add(sanitizeValue(Array.get(value, i)));
            }
            return sanitizedArray;
        }
        return value;
    }

    private Map<String, Object> describeMultipartFile(MultipartFile file) {
        Map<String, Object> fileInfo = new LinkedHashMap<>();
        fileInfo.put("name", file.getName());
        fileInfo.put("originalFilename", file.getOriginalFilename());
        fileInfo.put("size", file.getSize());
        fileInfo.put("contentType", file.getContentType());
        return fileInfo;
    }

    private String toJson(Object value) {
        if (value instanceof ResponseEntity<?> responseEntity) {
            Object body = responseEntity.getBody();
            if (body instanceof Resource) {
                return "<" + responseEntity.getStatusCode() + ", stream body, "
                        + responseEntity.getHeaders() + ">";
            }
        }
        try {
            return truncate(objectMapper.writeValueAsString(value));
        } catch (JsonProcessingException e) {
            return truncate(String.valueOf(value));
        }
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        if (value.length() <= MAX_LOG_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_LOG_LENGTH) + "...(truncated)";
    }
}
