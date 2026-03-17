package com.flashnote.file.service;

import com.flashnote.file.dto.FileUploadResult;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

public interface FileService {
    FileUploadResult upload(String username, MultipartFile file);

    InputStream download(String objectName);
}
