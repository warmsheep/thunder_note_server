package com.flashnote.file.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.flashnote.auth.entity.User;
import com.flashnote.auth.mapper.UserMapper;
import com.flashnote.common.config.MinioConfig;
import com.flashnote.common.exception.BusinessException;
import com.flashnote.common.response.ErrorCode;
import com.flashnote.file.service.FileService;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.UUID;

@Service
public class FileServiceImpl implements FileService {
    private final MinioClient minioClient;
    private final MinioConfig minioConfig;
    private final UserMapper userMapper;

    public FileServiceImpl(MinioClient minioClient, MinioConfig minioConfig, UserMapper userMapper) {
        this.minioClient = minioClient;
        this.minioConfig = minioConfig;
        this.userMapper = userMapper;
    }

    @Override
    public String upload(String username, MultipartFile file) {
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

            return objectName;
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "File upload failed");
        }
    }

    @Override
    public InputStream download(String objectName) {
        try {
            return minioClient.getObject(GetObjectArgs.builder()
                    .bucket(minioConfig.getBucket())
                    .object(objectName)
                    .build());
        } catch (Exception ex) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "File not found");
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
}
