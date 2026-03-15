package com.flashnote.file.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

public interface FileService {
    String upload(String username, MultipartFile file);

    InputStream download(String objectName);
}
