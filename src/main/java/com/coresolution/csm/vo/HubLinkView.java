package com.coresolution.csm.vo;

import lombok.Data;

/**
 * 허브 화면의 링크 한 행. 표시에 필요한 값(host, 환경, 분류 색)을 미리 계산해 담는다.
 * 템플릿에서 URL 파싱·분기를 하지 않도록 {@link com.coresolution.csm.web.HubLinkPresenter}가 채운다.
 */
@Data
public class HubLinkView {

    private Long id;
    private String title;
    private String url;
    /** 화면에는 host만 노출한다. 전체 URL은 title 속성으로만 전달. */
    private String host;
    private String description;

    private String category;
    /** 분류 뱃지에 넣을 2글자 축약. */
    private String catShort;
    /** 라이트 모드 분류 색. 틴트는 CSS color-mix로 만든다. */
    private String catColor;
    private String catColorDark;

    /** prod | dev | demo — 저장값이 없으면 이름·host로 판정한 결과가 들어간다. */
    private String env;
    /** 운영 | 개발 | DEMO */
    private String envLabel;
    /**
     * DB에 저장된 환경값 그대로. 자동 판정 상태면 빈 문자열이다.
     * 관리 화면의 환경 셀렉트가 "자동 판정"과 "명시적으로 운영"을 구분하려면 이 값이 필요하다.
     */
    private String envSource = "";

    private Integer sortOrder;
    private boolean favorite;
    /**
     * 링크를 여는 방식. link/custom은 클릭 추적 경유(/hub/go/{kind}/{id}),
     * raw는 원본 URL로 바로 보낸다. 템플릿이 @{...} 링크식과 외부 URL을 갈라 쓰기 위한 값이다.
     */
    private String kind;

    /** 개인 링크 메모. 공용 링크는 null. */
    private String memo;
    /** 설명·메모에 계정정보가 적힌 링크. 칩만 띄우고 값은 노출하지 않는다. */
    private boolean hasAccountInfo;

    /** 최근 사용 시각 또는 인기 링크 클릭수 등 행 우측 보조 텍스트. */
    private String metaText;
}
