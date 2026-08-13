package com.coresolution.csm.web;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.coresolution.csm.vo.CompanyLink;
import com.coresolution.csm.vo.HubCustomLink;
import com.coresolution.csm.vo.HubHistoryView;
import com.coresolution.csm.vo.HubLinkView;

/**
 * 링크 엔티티 → 화면용 {@link HubLinkView} 매핑.
 *
 * <p>환경(운영/개발/DEMO)과 분류 색은 아직 DB 컬럼이 없어 이름·host로 판정한다.
 * 링크에 env 컬럼과 분류 색 컬럼이 생기면 이 클래스의 판정 메서드만 컬럼 조회로 바꾸면 된다.
 */
@Component
public class HubLinkPresenter {

    /** 분류별 확정 색상·축약. 디자인 핸드오프의 분류 컬러 표를 그대로 옮긴 값이다. */
    private static final Map<String, Category> CATEGORIES = new LinkedHashMap<>();
    static {
        CATEGORIES.put("오션팔레트 군산", new Category("오션", "#0d6b7d", "#4cc2d6"));
        CATEGORIES.put("아마존 당진", new Category("당진", "#8a5a10", "#e0a13a"));
        CATEGORIES.put("아마존 완주", new Category("완주", "#97452a", "#d97757"));
        CATEGORIES.put("스포플랫", new Category("스포", "#0f7a3d", "#4ec98a"));
        CATEGORIES.put("병원", new Category("병원", "#2f5bb8", "#7fa9ff"));
        CATEGORIES.put("스테이", new Category("스테", "#6b3f8f", "#c084d8"));
        CATEGORIES.put("스테이&카라반", new Category("카라", "#6b3f8f", "#c084d8"));
        CATEGORIES.put("티켓플랫", new Category("티켓", "#8c2f5c", "#d67ba0"));
        CATEGORIES.put("이지에셋", new Category("이지", "#4a6b1f", "#9db34f"));
        CATEGORIES.put("ATS 군산 DEMO", new Category("ATS", "#4a3ba8", "#a78bfa"));
        CATEGORIES.put("조이랜드", new Category("조이", "#3a45a8", "#7170ff"));
        CATEGORIES.put("코어솔루션", new Category("코어", "#4b4f57", "#8a8f98"));
    }

    /** 표에 없는 분류에 배정할 색. 같은 이름이면 항상 같은 색이 나오도록 이름 해시로 고른다. */
    private static final String[][] FALLBACK_PALETTE = {
            {"#0d6b7d", "#4cc2d6"},
            {"#8a5a10", "#e0a13a"},
            {"#97452a", "#d97757"},
            {"#0f7a3d", "#4ec98a"},
            {"#2f5bb8", "#7fa9ff"},
            {"#6b3f8f", "#c084d8"},
            {"#8c2f5c", "#d67ba0"},
            {"#4a6b1f", "#9db34f"},
    };

    private static final Category ETC = new Category("기타", "#4b4f57", "#8a8f98");

    /** 설명·메모에 이 단어가 있으면 "계정" 칩을 띄운다. 값 자체는 화면에 내보내지 않는다. */
    private static final String[] ACCOUNT_HINTS = {"계정", "비밀번호", "패스워드", "password", "passwd", "pw/", "id/pw"};

    // ── 공용 링크 ────────────────────────────────────────────────────────

    public List<HubLinkView> publicLinks(List<CompanyLink> links, Set<Long> favoriteIds, boolean loggedIn) {
        List<HubLinkView> out = new ArrayList<>();
        if (links == null) return out;
        for (CompanyLink link : links) {
            out.add(publicLink(link, favoriteIds != null && favoriteIds.contains(link.getId()), loggedIn));
        }
        return out;
    }

    public HubLinkView publicLink(CompanyLink link, boolean favorite, boolean loggedIn) {
        HubLinkView view = base(link.getTitle(), link.getUrl(), link.getCategory());
        view.setId(link.getId());
        view.setDescription(link.getDescription());
        view.setSortOrder(link.getSortOrder());
        view.setFavorite(favorite);
        view.setHasAccountInfo(mentionsAccount(link.getDescription()));
        // 미로그인은 클릭 이력을 남기지 않으므로 경유 없이 원본 URL로 바로 보낸다.
        view.setKind(loggedIn ? "link" : "raw");
        return view;
    }

    // ── 개인 링크 ────────────────────────────────────────────────────────

    public List<HubLinkView> customLinks(List<HubCustomLink> links) {
        List<HubLinkView> out = new ArrayList<>();
        if (links == null) return out;
        for (HubCustomLink link : links) {
            out.add(customLink(link));
        }
        return out;
    }

    public HubLinkView customLink(HubCustomLink link) {
        HubLinkView view = base(link.getTitle(), link.getUrl(), link.getCategory());
        view.setId(link.getId());
        view.setMemo(link.getMemo());
        view.setSortOrder(link.getSortOrder());
        view.setHasAccountInfo(mentionsAccount(link.getMemo()));
        view.setKind("custom");
        return view;
    }

    // ── 최근 사용 (스냅샷) ───────────────────────────────────────────────

    public List<HubLinkView> historyLinks(List<HubHistoryView> items) {
        List<HubLinkView> out = new ArrayList<>();
        if (items == null) return out;
        for (HubHistoryView item : items) {
            HubLinkView view = base(item.getTitle(), item.getUrl(), null);
            view.setMetaText(item.getAccessedAt());
            // 스냅샷 URL을 그대로 연다 — 이력에서 다시 이력을 남길 이유가 없다.
            view.setKind("raw");
            out.add(view);
        }
        return out;
    }

    // ── 분류 ─────────────────────────────────────────────────────────────

    /** 사이드바·그룹 헤더의 컬러 스퀘어용. */
    public Category category(String name) {
        if (name == null || name.isBlank()) return ETC;
        Category exact = CATEGORIES.get(name.trim());
        if (exact != null) return exact;
        String trimmed = name.trim();
        String[] pair = FALLBACK_PALETTE[Math.floorMod(trimmed.hashCode(), FALLBACK_PALETTE.length)];
        return new Category(shorten(trimmed), pair[0], pair[1]);
    }

    // ── 내부 ─────────────────────────────────────────────────────────────

    private HubLinkView base(String title, String url, String category) {
        HubLinkView view = new HubLinkView();
        view.setTitle(title);
        view.setUrl(url);
        view.setHost(hostOf(url));
        view.setCategory(category);

        Category cat = category(category);
        view.setCatShort(cat.shortLabel());
        view.setCatColor(cat.color());
        view.setCatColorDark(cat.colorDark());

        String env = envOf(title, view.getHost());
        view.setEnv(env);
        view.setEnvLabel(envLabel(env));
        return view;
    }

    /** 표시용 host. 파싱 실패하면 원본을 그대로 돌려준다(빈 칸으로 두지 않는다). */
    static String hostOf(String url) {
        if (url == null || url.isBlank()) return "";
        try {
            String host = URI.create(url.trim()).getHost();
            if (host == null || host.isBlank()) return url.trim();
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (IllegalArgumentException e) {
            return url.trim();
        }
    }

    /**
     * 환경 판정. 개발서버를 운영으로 착각해 여는 사고를 막는 것이 목적이라
     * 애매하면 운영(prod)이 아니라 눈에 띄는 쪽으로 보내는 편이 안전하다.
     */
    static String envOf(String title, String host) {
        String name = title == null ? "" : title.toUpperCase(Locale.ROOT);
        if (name.contains("DEMO")) return "demo";
        String h = host == null ? "" : host.toLowerCase(Locale.ROOT);
        if (h.startsWith("dev.") || h.contains("-dev.") || h.contains(".dev.")) return "dev";
        return "prod";
    }

    private static String envLabel(String env) {
        return switch (env) {
            case "dev" -> "개발";
            case "demo" -> "DEMO";
            default -> "운영";
        };
    }

    private static boolean mentionsAccount(String text) {
        if (text == null || text.isBlank()) return false;
        String lower = text.toLowerCase(Locale.ROOT);
        for (String hint : ACCOUNT_HINTS) {
            if (lower.contains(hint)) return true;
        }
        return false;
    }

    /** 표에 없는 분류의 축약 — 앞 2글자. 공백만 있으면 "기타". */
    private static String shorten(String name) {
        String compact = name.replace(" ", "");
        if (compact.isEmpty()) return "기타";
        return compact.length() <= 2 ? compact : compact.substring(0, 2);
    }

    /** 분류 뱃지·컬러 스퀘어에 필요한 값 묶음. */
    public record Category(String shortLabel, String color, String colorDark) {}
}
