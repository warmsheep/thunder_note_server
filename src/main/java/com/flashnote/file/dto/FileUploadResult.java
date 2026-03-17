package com.flashnote.file.dto;

public class FileUploadResult {
    private String objectName;
    private String originalFilename;

    public FileUploadResult(String objectName, String originalFilename) {
        this.objectName = objectName;
        this.originalFilename = originalFilename;
    }

    public String getObjectName() {
        return objectName;
    }

    public void setObjectName(String objectName) {
        this.objectName = objectName;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }
}
