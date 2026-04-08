package com.coresolution.csm.vo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class Category3 {
    private int cc_col_01;
    private String cc_col_02;
    private List<String> cc_col_03 = new ArrayList<>();
    private int cc_col_04;
    private String inst;
    private String category3;

    // cc_col_03 필드를 콤마로 구분된 문자열로 반환하는 메서드
    public String getCc_col_03AsString() {
        if (cc_col_03 == null || cc_col_03.isEmpty()) {
            return ""; // 빈 문자열 반환
        }
        return String.join(",", cc_col_03);
    }

    // cc_col_03 필드를 콤마로 구분된 문자열로 설정하는 메서드
    public void setCc_col_03FromString(String cc_col_03) {
        if (cc_col_03 != null && !cc_col_03.isEmpty()) {
            this.cc_col_03 = Arrays.stream(cc_col_03.split(","))
                    .map(String::trim) // 각 옵션의 공백 제거
                    .filter(s -> s != null && !s.isEmpty()) // 빈 문자열 및 null 값 제거
                    .collect(Collectors.toList());
        } else {
            this.cc_col_03 = new ArrayList<>(); // 옵션이 없을 경우 빈 리스트 설정
        }
    }
// ✅ 이 한 줄만 있으면 MyBatis가 column 'cc_col_03' 문자열을 자동으로 이 setter로 넣어줍니다.
    public void setCc_col_03(String csv) { setCc_col_03FromString(csv); }

    // (선택) 템플릿에서 쓰기 좋은 표시용 getter
    public String getCcCol03Display() {
        String s = getCc_col_03AsString();
        return (s != null && !s.isBlank()) ? s : "";
    }
    /**
     * @return the cc_col_01
     */
    public int getCc_col_01() {
        return cc_col_01;
    }

    /**
     * @param cc_col_01 the cc_col_01 to set
     */
    public void setCc_col_01(int cc_col_01) {
        this.cc_col_01 = cc_col_01;
    }

    /**
     * @return the cc_col_02
     */
    public String getCc_col_02() {
        return cc_col_02;
    }

    /**
     * @param cc_col_02 the cc_col_02 to set
     */
    public void setCc_col_02(String cc_col_02) {
        this.cc_col_02 = cc_col_02;
    }

    /**
     * @return the cc_col_03
     */
    public List<String> getCc_col_03() {
        return cc_col_03;
    }

    /**
     * @param cc_col_03 the cc_col_03 to set
     */
    public void setCc_col_03(List<String> cc_col_03) {
        this.cc_col_03 = cc_col_03;
    }

    /**
     * @return the cc_col_04
     */
    public int getCc_col_04() {
        return cc_col_04;
    }

    /**
     * @param cc_col_04 the cc_col_04 to set
     */
    public void setCc_col_04(int cc_col_04) {
        this.cc_col_04 = cc_col_04;
    }

    /**
     * @return the inst
     */
    public String getInst() {
        return inst;
    }

    /**
     * @param inst the inst to set
     */
    public void setInst(String inst) {
        this.inst = inst;
    }

    /**
     * @return the category3
     */
    public String getCategory3() {
        return category3;
    }

    /**
     * @param category3 the category3 to set
     */
    public void setCategory3(String category3) {
        this.category3 = category3;
    }

    @Override
    public String toString() {
        return "Category3 [cc_col_01=" + cc_col_01 + ", cc_col_02=" + cc_col_02 + ", cc_col_03="
                + getCc_col_03AsString()
                + ", cc_col_04=" + cc_col_04 + ", inst=" + inst + ", category3=" + category3 + "]";
    }
}
