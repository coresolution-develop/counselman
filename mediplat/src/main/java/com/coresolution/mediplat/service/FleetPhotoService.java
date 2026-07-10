package com.coresolution.mediplat.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * 계기판 사진 로컬 디스크 저장. csm WAR의 파일 저장 관례(로컬 디스크 + 경로 DB 저장 + traversal 가드)를
 * 따른다. DB에는 base-dir 기준 상대 경로만 저장하고, 파일은 base-dir 하위에 inst/연/월로 분리한다.
 *
 * <p><b>운영 필수(되짚음 ②·③)</b>: {@code platform.fleet.photo.base-dir}는 반드시
 * <b>일일 백업 대상 볼륨</b>과 일치시켜야 한다. DB엔 경로만 있어 파일이 유실되면 세무 증빙이 사라진다.
 * 배포 체크리스트({@code docs/fleet-deploy-checklist.md}) 참조.
 *
 * <p>리사이즈/압축은 신규 의존성(Thumbnailator 등)을 피하기 위해 P0에서는 하지 않는다(원본 저장).
 * 대신 멀티파트 업로드 한도로 크기를 제한한다.
 */
@Service
public class FleetPhotoService {

    private static final DateTimeFormatter YEAR_MONTH = DateTimeFormatter.ofPattern("yyyy/MM");

    @Value("${platform.fleet.photo.base-dir:${user.home}/mediplat-fleet-photos}")
    private String baseDir;

    /**
     * 사진을 저장하고 base-dir 기준 상대 경로("core/2026/07/uuid.jpg")를 반환한다.
     * <b>DB 트랜잭션 밖에서</b> 먼저 호출해야 한다(되짚음 ①). 실패 시 예외를 던진다.
     */
    public String store(String instCode, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("계기판 사진이 필요합니다.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("image/")) {
            throw new IllegalArgumentException("이미지 파일만 업로드할 수 있습니다.");
        }
        String normalizedInst = StringUtils.hasText(instCode) ? instCode.trim() : "unknown";
        Path base = basePath();
        Path dir = base.resolve(normalizedInst).resolve(LocalDate.now().format(YEAR_MONTH)).normalize();
        String storedName = UUID.randomUUID() + "." + resolveExtension(file);
        Path target = dir.resolve(storedName).normalize();
        // traversal 가드: 최종 경로가 base-dir 하위여야 한다
        if (!target.startsWith(base)) {
            throw new IllegalArgumentException("잘못된 저장 경로입니다.");
        }
        try {
            Files.createDirectories(dir);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new IllegalStateException("사진 저장에 실패했습니다: " + e.getMessage(), e);
        }
        return base.relativize(target).toString().replace('\\', '/');
    }

    /**
     * 상대 경로의 사진 파일을 best-effort 삭제한다(고아 파일 최소화, 되짚음 ④).
     * DB 트랜잭션 실패 시 방금 쓴 사진을 지우는 용도라 예외를 던지지 않는다.
     */
    public boolean delete(String relativePath) {
        if (!StringUtils.hasText(relativePath)) {
            return false;
        }
        try {
            Path base = basePath();
            Path target = base.resolve(relativePath).normalize();
            if (!target.startsWith(base)) {
                return false;
            }
            return Files.deleteIfExists(target);
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    /** 상대 경로를 절대 경로로 해석한다(사진 서빙용). base-dir 밖이면 null. */
    public Path resolve(String relativePath) {
        if (!StringUtils.hasText(relativePath)) {
            return null;
        }
        Path base = basePath();
        Path target = base.resolve(relativePath).normalize();
        return target.startsWith(base) ? target : null;
    }

    private Path basePath() {
        return Paths.get(baseDir).toAbsolutePath().normalize();
    }

    private String resolveExtension(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name != null) {
            int dot = name.lastIndexOf('.');
            if (dot >= 0 && dot < name.length() - 1) {
                String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
                if (ext.matches("[a-z0-9]{1,5}")) {
                    return ext;
                }
            }
        }
        String contentType = file.getContentType();
        if (contentType != null) {
            String lower = contentType.toLowerCase(Locale.ROOT);
            if (lower.contains("png")) {
                return "png";
            }
            if (lower.contains("webp")) {
                return "webp";
            }
        }
        return "jpg";
    }
}
