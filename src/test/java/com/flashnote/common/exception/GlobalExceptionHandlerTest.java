package com.flashnote.common.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashnote.common.response.ApiResponse;
import com.flashnote.common.response.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler(new ObjectMapper());
    }

    @Test
    void handleBusinessException_forcesJsonContentType() {
        BusinessException ex = new BusinessException(ErrorCode.NOT_FOUND, "File not found");

        ResponseEntity<ApiResponse<Void>> response = handler.handleBusinessException(ex);

        assertEquals(MediaType.APPLICATION_JSON, response.getHeaders().getContentType());
        assertEquals(ErrorCode.NOT_FOUND.getCode(), response.getBody().getCode());
        assertEquals("File not found", response.getBody().getMessage());
    }

    @Test
    void handleException_forcesJsonContentType() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleException(new RuntimeException("boom"));

        assertEquals(MediaType.APPLICATION_JSON, response.getHeaders().getContentType());
        assertEquals(ErrorCode.INTERNAL_ERROR.getCode(), response.getBody().getCode());
        assertEquals("Internal server error", response.getBody().getMessage());
    }
}
