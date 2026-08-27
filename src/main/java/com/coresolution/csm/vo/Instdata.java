package com.coresolution.csm.vo;

public class Instdata {
    private Integer id_col_01;
    private String id_col_02;
    private String id_col_03;
    private String id_col_04;
    private String id_col_05;
    private String id_col_06;
    private String id_col_07;
    private String id_col_08;
    private String id_col_09;
    private String sms_price;

    /**
     * 이 기관에 적용된 플랫폼 단가 버전 (CSM-3 이 미러링한다).
     *
     * <p>{@code null} 이면 플랫폼 단가가 아니다 — 연동 전에 설정된 값이거나 미설정이다.
     * 채널별이 아니라 <b>기관 단위</b>다. 폴링이 세 채널을 같은 버전으로 저장한다.
     */
    private Integer sms_price_version;
    private String lms_price;
    private String mms_price;

    public Integer getId_col_01() {
        return id_col_01;
    }

    public void setId_col_01(Integer id_col_01) {
        this.id_col_01 = id_col_01;
    }

    public String getId_col_02() {
        return id_col_02;
    }

    public void setId_col_02(String id_col_02) {
        this.id_col_02 = id_col_02;
    }

    public String getId_col_03() {
        return id_col_03;
    }

    public void setId_col_03(String id_col_03) {
        this.id_col_03 = id_col_03;
    }

    public String getId_col_04() {
        return id_col_04;
    }

    public void setId_col_04(String id_col_04) {
        this.id_col_04 = id_col_04;
    }

    public String getId_col_05() {
        return id_col_05;
    }

    public void setId_col_05(String id_col_05) {
        this.id_col_05 = id_col_05;
    }

    public String getId_col_06() {
        return id_col_06;
    }

    public void setId_col_06(String id_col_06) {
        this.id_col_06 = id_col_06;
    }

    public String getId_col_07() {
        return id_col_07;
    }

    public void setId_col_07(String id_col_07) {
        this.id_col_07 = id_col_07;
    }

    public String getId_col_08() {
        return id_col_08;
    }

    public void setId_col_08(String id_col_08) {
        this.id_col_08 = id_col_08;
    }

    public String getId_col_09() {
        return id_col_09;
    }

    public void setId_col_09(String id_col_09) {
        this.id_col_09 = id_col_09;
    }

    public String getSms_price() {
        return sms_price;
    }

    public Integer getSms_price_version() {
        return sms_price_version;
    }

    public void setSms_price_version(Integer sms_price_version) {
        this.sms_price_version = sms_price_version;
    }

    public void setSms_price(String sms_price) {
        this.sms_price = sms_price;
    }

    public String getLms_price() {
        return lms_price;
    }

    public void setLms_price(String lms_price) {
        this.lms_price = lms_price;
    }

    public String getMms_price() {
        return mms_price;
    }

    public void setMms_price(String mms_price) {
        this.mms_price = mms_price;
    }
}
