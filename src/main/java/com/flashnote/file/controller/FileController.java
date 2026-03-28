package com.flashnote.file.controller;

import com.flashnote.common.response.ApiResponse;
import com.flashnote.file.dto.FileUploadResult;
import com.flashnote.file.service.FileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLConnection;

@RestController
@RequestMapping("/api/files")
@Slf4j
public class FileController {
    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping("/upload")
    public ApiResponse<FileUploadResult> upload(Authentication authentication,
                                                 @RequestParam("file") MultipartFile file) {
        return ApiResponse.success(fileService.upload(authentication.getName(), file));
    }

    @GetMapping("/download")
    public ResponseEntity<Resource> download(@RequestParam("objectName") String objectName) throws IOException {
        byte[] data;
        try (InputStream stream = fileService.download(objectName)) {
            data = stream.readAllBytes();
        }

        MediaType contentType = guessMediaType(objectName);
        ByteArrayResource resource = new ByteArrayResource(data);
        return ResponseEntity.ok()
                .contentType(contentType)
                .contentLength(data.length)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + extractFileName(objectName) + "\"")
                .body(resource);
    }

    private MediaType guessMediaType(String objectName) {
        String mimeType = URLConnection.guessContentTypeFromName(objectName);
        if (mimeType != null) {
            try {
                return MediaType.parseMediaType(mimeType);
            } catch (Exception e) {
                log.warn("Failed to parse MIME type for objectName={}: {}", objectName, e.getMessage());
            }
        }
        return MediaType.APPLICATION_OCTET_STREAM;
    }

    private String extractFileName(String objectName) {
        if (objectName == null) {
            return "file";
        }
        int lastSlash = objectName.lastIndexOf('/');
        return lastSlash >= 0 && lastSlash < objectName.length() - 1
                ? objectName.substring(lastSlash + 1)
                : objectName;
    }
}
