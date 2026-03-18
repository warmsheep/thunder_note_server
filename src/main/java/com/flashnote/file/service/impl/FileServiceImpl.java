package com.flashnote.file.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.flashnote.auth.entity.User;
import com.flashnote.auth.mapper.UserMapper;
import com.flashnote.common.config.MinioConfig;
import com.flashnote.common.exception.BusinessException;
import com.flashnote.common.response.ErrorCode;
import com.flashnote.file.dto.FileUploadResult;
import com.flashnote.file.service.FileService;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Service
public class FileServiceImpl implements FileService {
    private static final Logger log = LoggerFactory.getLogger(FileServiceImpl.class);
    private final MinioClient minioClient;
    private final MinioConfig minioConfig;
    private final UserMapper userMapper;

    public FileServiceImpl(MinioClient minioClient, MinioConfig minioConfig, UserMapper userMapper) {
        this.minioClient = minioClient;
        this.minioConfig = minioConfig;
        this.userMapper = userMapper;
    }

    @Override
    public FileUploadResult upload(String username, MultipartFile file) {
        try {
            Long userId = getRequiredUserId(username);
            String extension = getFileExtension(file.getOriginalFilename());
            String objectName = userId + "/" + UUID.randomUUID() + extension;

            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioConfig.getBucket())
                    .object(objectName)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .contentType(file.getContentType())
                    .build());

            return new FileUploadResult(objectName, file.getOriginalFilename());
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "File upload failed");
        }
    }

    @Override
    public InputStream download(String objectName) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(minioConfig.getBucket())
                    .object(normalizeObjectName(objectName))
                    .build());
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "File not found");
        }
    }

    @Override
    public void deleteObject(String objectName) {
        String normalizedObjectName = normalizeObjectName(objectName);
        if (normalizedObjectName.isEmpty()) {
            return;
        }
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(minioConfig.getBucket())
                    .object(normalizedObjectName)
                    .build());
        } catch (Exception ex) {
            log.warn("Delete object failed: {}", normalizedObjectName, ex);
        }
    }

    private Long getRequiredUserId(String username) {
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (user == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "User not found");
        }
        return user.getId();
    }

    private String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.'));
    }

    private String normalizeObjectName(String objectName) {
        if (objectName == null || objectName.isBlank()) {
            return "";
        }
        String value = objectName.trim();
        if (value.startsWith("http")) {
            int queryStart = value.indexOf('?');
            String query = queryStart >= 0 ? value.substring(queryStart + 1) : "";
            for (String pair : query.split("&")) {
                if (pair.startsWith("objectName=")) {
                    return URLDecoder.decode(pair.substring("objectName=".length()), StandardCharsets.UTF_8);
                }
            }
            int schemeSplit = value.indexOf("://");
            int pathStart = value.indexOf('/', schemeSplit > 0 ? schemeSplit + 3 : 0);
            if (pathStart >= 0 && pathStart + 1 < value.length()) {
                value = value.substring(pathStart + 1);
            }
            if (value.startsWith("api/files/download")) {
                return "";
            }
            return value;
        }
        if (value.startsWith("/")) {
            value = value.substring(1);
        }
        return value;
    }
}
