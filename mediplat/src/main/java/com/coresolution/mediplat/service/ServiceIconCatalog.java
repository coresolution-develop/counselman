package com.coresolution.mediplat.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import jakarta.annotation.PostConstruct;

/**
 * static/icons 의 {key}-appicon.svg 를 훑어 앱 등록 화면에서 고를 수 있는
 * 아이콘 key 목록을 만든다. 새 시스템 아이콘은 SVG 파일만 넣고 배포하면 목록에 나타난다.
 *
 * key 는 DB(mp_service.icon_key)에 저장되고 화면에서 파일 경로로 조립되므로,
 * 저장 전에 반드시 이 카탈로그에 있는 값인지 확인한다(경로 조작 차단).
 */
@Service
public class ServiceIconCatalog {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ServiceIconCatalog.class);

    private static final String LOCATION_PATTERN = "classpath*:/static/icons/*-appicon.svg";
    private static final String SUFFIX = "-appicon.svg";
    /** 파일명에서 뽑은 key 가 이 형태를 벗어나면 목록에 넣지 않는다. */
    private static final Pattern KEY_PATTERN = Pattern.compile("[a-z0-9][a-z0-9-]{0,48}");

    private List<String> iconKeys = List.of();

    @PostConstruct
    public void load() {
        List<String> keys = new ArrayList<>();
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver().getResources(LOCATION_PATTERN);
            for (Resource resource : resources) {
                String filename = resource.getFilename();
                if (filename == null || !filename.endsWith(SUFFIX)) {
                    continue;
                }
                String key = filename.substring(0, filename.length() - SUFFIX.length())
                        .toLowerCase(Locale.ROOT);
                if (KEY_PATTERN.matcher(key).matches() && !keys.contains(key)) {
                    keys.add(key);
                }
            }
        } catch (IOException e) {
            // 아이콘을 못 읽어도 앱 등록 자체는 되어야 한다(아이콘 미지정으로 저장됨).
            log.warn("service icon catalog scan failed, falling back to empty list", e);
        }
        keys.sort(String::compareTo);
        iconKeys = List.copyOf(keys);
        log.info("service icon catalog loaded: {} icons", iconKeys.size());
    }

    public List<String> listIconKeys() {
        return iconKeys;
    }

    /**
     * 저장용 정규화. 빈 값은 "아이콘 미지정"(null)으로 본다.
     * 카탈로그에 없는 key 는 저장하지 않고 거부한다.
     */
    public String normalizeForSave(String iconKey) {
        if (!StringUtils.hasText(iconKey)) {
            return null;
        }
        String normalized = iconKey.trim().toLowerCase(Locale.ROOT);
        if (!iconKeys.contains(normalized)) {
            throw new IllegalArgumentException("알 수 없는 아이콘입니다: " + iconKey);
        }
        return normalized;
    }
}
