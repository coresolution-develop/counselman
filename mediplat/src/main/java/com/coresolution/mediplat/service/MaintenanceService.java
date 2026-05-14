package com.coresolution.mediplat.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MaintenanceService {

    @Value("${platform.maintenance.file-path:/var/www/html/maintenance.html}")
    private String filePath;

    public String getMaintenanceHtml() {
        Path path = Paths.get(filePath);
        if (!Files.exists(path)) {
            return "";
        }
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }

    public void saveMaintenanceHtml(String html) {
        Path path = Paths.get(filePath);
        Path parent = path.getParent();
        try {
            if (parent != null && !Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            Files.writeString(path, html == null ? "" : html, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("점검 페이지 저장 실패: " + e.getMessage(), e);
        }
    }
}
