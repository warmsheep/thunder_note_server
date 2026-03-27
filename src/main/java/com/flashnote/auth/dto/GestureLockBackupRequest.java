package com.flashnote.auth.dto;

import jakarta.validation.constraints.NotBlank;

public class GestureLockBackupRequest {
    @NotBlank(message = "Ciphertext is required")
    private String ciphertext;

    @NotBlank(message = "Nonce is required")
    private String nonce;

    @NotBlank(message = "Kdf params are required")
    private String kdfParams;

    @NotBlank(message = "Version is required")
    private String version;

    public String getCiphertext() {
        return ciphertext;
    }

    public void setCiphertext(String ciphertext) {
        this.ciphertext = ciphertext;
    }

    public String getNonce() {
        return nonce;
    }

    public void setNonce(String nonce) {
        this.nonce = nonce;
    }

    public String getKdfParams() {
        return kdfParams;
    }

    public void setKdfParams(String kdfParams) {
        this.kdfParams = kdfParams;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }
}
