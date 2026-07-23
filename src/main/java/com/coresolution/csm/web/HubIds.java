package com.coresolution.csm.web;

import java.util.ArrayList;
import java.util.List;

/**
 * 드래그 재정렬 요청의 id CSV 파싱 유틸.
 * "3,1,2" 형태를 순서를 보존한 Long 목록으로 바꾼다 — 숫자가 아니거나 0 이하인 토큰은 버린다.
 * 최대 개수를 제한해 비정상적으로 큰 입력으로 인한 대량 UPDATE를 막는다.
 */
public final class HubIds {

    private static final int MAX_IDS = 500;

    private HubIds() {
    }

    public static List<Long> parseCsv(String csv) {
        List<Long> ids = new ArrayList<>();
        if (csv == null || csv.isBlank()) {
            return ids;
        }
        for (String token : csv.split(",")) {
            if (ids.size() >= MAX_IDS) {
                break;
            }
            String trimmed = token.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            try {
                long id = Long.parseLong(trimmed);
                if (id > 0) {
                    ids.add(id);
                }
            } catch (NumberFormatException ignored) {
                // 숫자 아닌 토큰은 무시
            }
        }
        return ids;
    }
}
