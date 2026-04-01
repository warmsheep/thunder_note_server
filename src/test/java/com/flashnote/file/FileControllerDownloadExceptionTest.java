package com.flashnote.file;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flashnote.common.exception.BusinessException;
import com.flashnote.common.exception.GlobalExceptionHandler;
import com.flashnote.common.response.ErrorCode;
import com.flashnote.file.controller.FileController;
import com.flashnote.file.dto.FileUploadResult;
import com.flashnote.file.service.FileService;
import java.io.InputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.multipart.MultipartFile;

class FileControllerDownloadExceptionTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        FileController controller = new FileController(new FailingFileService());
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler(new ObjectMapper()))
                .build();
    }

    @Test
    void download_whenImageRequestFails_returnsJsonErrorBody() throws Exception {
        mockMvc.perform(get("/api/files/download")
                        .param("objectName", "missing.jpg")
                        .accept(MediaType.IMAGE_JPEG))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value(ErrorCode.NOT_FOUND.getCode()))
                .andExpect(jsonPath("$.message").value("File not found"));
    }

    private static final class FailingFileService implements FileService {
        @Override
        public FileUploadResult upload(String username, MultipartFile file) {
            throw new UnsupportedOperationException();
        }

        @Override
        public InputStream download(String objectName) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "File not found");
        }

        @Override
        public void deleteObject(String objectName) {
            throw new UnsupportedOperationException();
        }
    }
}
