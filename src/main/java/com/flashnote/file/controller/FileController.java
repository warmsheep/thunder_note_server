package com.flashnote.file.controller;

import com.flashnote.common.response.ApiResponse;
import com.flashnote.file.dto.FileUploadResult;
import com.flashnote.file.service.FileService;
import org.springframework.core.io.InputStreamResource;
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

@RestController
@RequestMapping("/api/files")
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
    public ResponseEntity<InputStreamResource> download(@RequestParam("objectName") String objectName) {
        InputStreamResource resource = new InputStreamResource(fileService.download(objectName));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + objectName + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }
}
