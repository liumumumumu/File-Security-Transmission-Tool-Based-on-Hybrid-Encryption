package com.client.direct.qr;

import java.nio.file.Path;
import java.time.Instant;

/**
 * Author: LQH
 * Date: 2026-05-19
 * Purpose: 数据载体类，记录二维码生成后产生了那些文件
 *
 * */

public class QrArtifact
{
    private String inviteId;
    private String role;
    private Instant expiresAt;
    private Path pngPath;
    private Path fst1Path;
    private Path asciiPath;

    public QrArtifact() {}

    public QrArtifact(String inviteId,
                      String role,
                      Instant expiresAt,
                      Path pngPath,
                      Path fst1Path,
                      Path asciiPath) {
        this.asciiPath = asciiPath;
        this.expiresAt = expiresAt;
        this.fst1Path = fst1Path;
        this.inviteId = inviteId;
        this.pngPath = pngPath;
        this.role = role;
    }

    public Path getAsciiPath() {
        return asciiPath;
    }

    public void setAsciiPath(Path asciiPath) {
        this.asciiPath = asciiPath;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Path getFst1Path() {
        return fst1Path;
    }

    public void setFst1Path(Path fst1Path) {
        this.fst1Path = fst1Path;
    }

    public String getInviteId() {
        return inviteId;
    }

    public void setInviteId(String inviteId) {
        this.inviteId = inviteId;
    }

    public Path getPngPath() {
        return pngPath;
    }

    public void setPngPath(Path pngPath) {
        this.pngPath = pngPath;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    @Override
    public String toString() {
        return "QrArtifact{" +
                "asciiPath=" + asciiPath +
                ", inviteId='" + inviteId + '\'' +
                ", role='" + role + '\'' +
                ", expiresAt=" + expiresAt +
                ", pngPath=" + pngPath +
                ", fst1Path=" + fst1Path +
                '}';
    }
}
