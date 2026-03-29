package com.flashnote.file;

import com.flashnote.common.config.MinioConfig;
import com.flashnote.common.exception.BusinessException;
import com.flashnote.common.response.ErrorCode;
import com.flashnote.common.service.CurrentUserService;
import com.flashnote.file.dto.FileUploadResult;
import com.flashnote.file.service.impl.FileServiceImpl;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FileServiceImplTest {

    @Test
    void upload_withValidImage_succeeds() throws Exception {
        MinioClient minioClient = mock(MinioClient.class);
        MinioConfig minioConfig = mockMinioConfig();
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        when(currentUserService.getRequiredUserId("alice")).thenReturn(7L);

        MultipartFile file = mock(MultipartFile.class);
        when(file.getSize()).thenReturn(1024L);
        when(file.getContentType()).thenReturn("image/png");
        when(file.getOriginalFilename()).thenReturn("photo.png");
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[]{1, 2, 3}));

        FileServiceImpl service = new FileServiceImpl(minioClient, minioConfig, null, currentUserService);

        FileUploadResult result = service.upload("alice", file);

        assertNotNull(result);
        assertEquals("photo.png", result.getOriginalFilename());
        ArgumentCaptor<PutObjectArgs> argsCaptor = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minioClient).putObject(argsCaptor.capture());
        PutObjectArgs args = argsCaptor.getValue();
        assertEquals("thunder-note", args.bucket());
        assertEquals("image/png", args.contentType());
    }

    @Test
    void upload_withExceedSize_throwsBadRequest() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getSize()).thenReturn(201L * 1024 * 1024);
        when(file.getContentType()).thenReturn("image/png");

        FileServiceImpl service = new FileServiceImpl(mock(MinioClient.class), mockMinioConfig(), null, mock(CurrentUserService.class));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.upload("alice", file));
        assertEquals(ErrorCode.BAD_REQUEST.getCode(), ex.getCode());
        assertEquals("File size exceeds 200MB limit", ex.getMessage());
    }

    @Test
    void upload_withUnknownTypeAndExtension_preservesBadRequest() {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getSize()).thenReturn(100L);
        when(file.getContentType()).thenReturn("application/x-unknown-binary");
        when(file.getOriginalFilename()).thenReturn("payload.bin");

        FileServiceImpl service = new FileServiceImpl(mock(MinioClient.class), mockMinioConfig(), null, mock(CurrentUserService.class));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.upload("alice", file));
        assertEquals(ErrorCode.BAD_REQUEST.getCode(), ex.getCode());
        assertEquals("File type not allowed", ex.getMessage());
    }

    @Test
    void upload_withOctetStreamAndPngExtension_succeeds() throws Exception {
        MinioClient minioClient = mock(MinioClient.class);
        MinioConfig minioConfig = mockMinioConfig();
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        when(currentUserService.getRequiredUserId("alice")).thenReturn(7L);

        MultipartFile file = mock(MultipartFile.class);
        when(file.getSize()).thenReturn(1024L);
        when(file.getContentType()).thenReturn("application/octet-stream");
        when(file.getOriginalFilename()).thenReturn("photo.png");
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[]{1, 2, 3}));

        FileServiceImpl service = new FileServiceImpl(minioClient, minioConfig, null, currentUserService);

        FileUploadResult result = service.upload("alice", file);

        assertNotNull(result);
        assertEquals("photo.png", result.getOriginalFilename());
        verify(minioClient).putObject(any(PutObjectArgs.class));
    }

    @Test
    void upload_withMarkdownExtension_succeeds() throws Exception {
        MinioClient minioClient = mock(MinioClient.class);
        MinioConfig minioConfig = mockMinioConfig();
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        when(currentUserService.getRequiredUserId("alice")).thenReturn(7L);

        MultipartFile file = mock(MultipartFile.class);
        when(file.getSize()).thenReturn(1024L);
        when(file.getContentType()).thenReturn("application/octet-stream");
        when(file.getOriginalFilename()).thenReturn("notes.md");
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[]{1, 2, 3}));

        FileServiceImpl service = new FileServiceImpl(minioClient, minioConfig, null, currentUserService);

        FileUploadResult result = service.upload("alice", file);

        assertNotNull(result);
        assertEquals("notes.md", result.getOriginalFilename());
        verify(minioClient).putObject(any(PutObjectArgs.class));
    }

    @Test
    void upload_withApkExtension_succeeds() throws Exception {
        MinioClient minioClient = mock(MinioClient.class);
        MinioConfig minioConfig = mockMinioConfig();
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        when(currentUserService.getRequiredUserId("alice")).thenReturn(7L);

        MultipartFile file = mock(MultipartFile.class);
        when(file.getSize()).thenReturn(1024L);
        when(file.getContentType()).thenReturn("application/octet-stream");
        when(file.getOriginalFilename()).thenReturn("installer.apk");
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[]{1, 2, 3}));

        FileServiceImpl service = new FileServiceImpl(minioClient, minioConfig, null, currentUserService);

        FileUploadResult result = service.upload("alice", file);

        assertNotNull(result);
        assertEquals("installer.apk", result.getOriginalFilename());
        verify(minioClient).putObject(any(PutObjectArgs.class));
    }

    @Test
    void upload_whenMinioFails_returnsInternalError() throws Exception {
        MinioClient minioClient = mock(MinioClient.class);
        MinioConfig minioConfig = mockMinioConfig();
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        when(currentUserService.getRequiredUserId("alice")).thenReturn(7L);
        doThrow(new RuntimeException("minio down")).when(minioClient).putObject(any(PutObjectArgs.class));

        MultipartFile file = mock(MultipartFile.class);
        when(file.getSize()).thenReturn(1024L);
        when(file.getContentType()).thenReturn("image/png");
        when(file.getOriginalFilename()).thenReturn("photo.png");
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(new byte[]{1, 2, 3}));

        FileServiceImpl service = new FileServiceImpl(minioClient, minioConfig, null, currentUserService);

        BusinessException ex = assertThrows(BusinessException.class, () -> service.upload("alice", file));
        assertEquals(ErrorCode.INTERNAL_ERROR.getCode(), ex.getCode());
        assertEquals("File upload failed", ex.getMessage());
    }

    @Test
    void download_whenMinioFails_throwsNotFoundMappedError() throws Exception {
        MinioClient minioClient = mock(MinioClient.class);
        when(minioClient.getObject(any(GetObjectArgs.class))).thenThrow(new RuntimeException("missing"));

        FileServiceImpl service = new FileServiceImpl(minioClient, mockMinioConfig(), null, mock(CurrentUserService.class));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.download("/1/demo.pdf"));

        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
        assertEquals("File not found", ex.getMessage());
        verify(minioClient).getObject(any(GetObjectArgs.class));
    }

    @Test
    void download_withTraversalObjectName_throwsNotFoundMappedError() {
        FileServiceImpl service = new FileServiceImpl(mock(MinioClient.class), mockMinioConfig(), null, mock(CurrentUserService.class));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.download("../etc/passwd"));
        assertEquals(ErrorCode.NOT_FOUND.getCode(), ex.getCode());
        assertEquals("File not found", ex.getMessage());
    }

    @Test
    void deleteObject_withEmptyName_doesNothing() throws Exception {
        MinioClient minioClient = mock(MinioClient.class);
        FileServiceImpl service = new FileServiceImpl(minioClient, mockMinioConfig(), null, mock(CurrentUserService.class));

        service.deleteObject("   ");

        verify(minioClient, never()).removeObject(any(RemoveObjectArgs.class));
    }

    @Test
    void deleteObject_withValidName_removesObject() throws Exception {
        MinioClient minioClient = mock(MinioClient.class);
        FileServiceImpl service = new FileServiceImpl(minioClient, mockMinioConfig(), null, mock(CurrentUserService.class));

        service.deleteObject("/1/demo.pdf");

        verify(minioClient).removeObject(any(RemoveObjectArgs.class));
    }

    @Test
    void deleteObject_withTraversalName_throwsBadRequest() {
        FileServiceImpl service = new FileServiceImpl(mock(MinioClient.class), mockMinioConfig(), null, mock(CurrentUserService.class));

        BusinessException ex = assertThrows(BusinessException.class, () -> service.deleteObject("../etc/passwd"));
        assertEquals(ErrorCode.BAD_REQUEST.getCode(), ex.getCode());
        assertEquals("Invalid object name", ex.getMessage());
    }

    private MinioConfig mockMinioConfig() {
        MinioConfig config = mock(MinioConfig.class);
        when(config.getBucket()).thenReturn("thunder-note");
        return config;
    }
}
