package com.coresolution.csm.vo;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;

public class CounselData {
    /*
     * 
     * CREATE TABLE counsel_data_hsop_0001 (
     * cs_idx INT AUTO_INCREMENT PRIMARY KEY,
     * created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
     * updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
     * `cs_col_01` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
     * DEFAULT NULL COMMENT '환자명',
     * `cs_col_02` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
     * DEFAULT NULL COMMENT '환자성별',
     * `cs_col_03` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
     * DEFAULT NULL COMMENT '생년월일',
     * `cs_col_04` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
     * DEFAULT NULL COMMENT '나이(만)',
     * `cs_col_05` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
     * DEFAULT NULL COMMENT '보험종류',
     * `cs_col_06` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
     * DEFAULT NULL COMMENT '실손보험',
     * `cs_col_07` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
     * DEFAULT NULL COMMENT '현위치',
     * `cs_col_08` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
     * DEFAULT NULL COMMENT '상담유입경로',
     * `cs_col_09` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
     * DEFAULT NULL COMMENT '잠재고객',
     * `cs_col_10` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
     * DEFAULT NULL COMMENT 'BC',
     * `cs_col_11` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
     * DEFAULT NULL COMMENT '현상태',
     * `cs_col_12` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
     * DEFAULT NULL COMMENT '환자치료요구사항',
     * `cs_col_13` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
     * DEFAULT NULL COMMENT '보호자명',
     * `cs_col_14` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
     * DEFAULT NULL COMMENT '관계',
     * `cs_col_15` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
     * DEFAULT NULL COMMENT '보호자번화번호',
     * `cs_col_16` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
     * DEFAULT NULL COMMENT '상담일',
     * `cs_col_17` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
     * DEFAULT NULL COMMENT '상담자',
     * `cs_col_18` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
     * DEFAULT NULL COMMENT '상담방법',
     * `cs_col_19` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
     * DEFAULT NULL COMMENT '상담결과',
     * `cs_col_20` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
     * DEFAULT NULL COMMENT '결과사유',
     * `cs_col_21` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
     * DEFAULT NULL COMMENT '입원예정일자',
     * `cs_col_22` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
     * DEFAULT NULL COMMENT '입원비',
     * `cs_col_23` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
     * DEFAULT NULL COMMENT '간병비',
     * `cs_col_24` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
     * DEFAULT NULL COMMENT '상급병실비',
     * `cs_col_25` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
     * DEFAULT NULL COMMENT '기타비용',
     * `cs_col_26` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
     * DEFAULT NULL COMMENT '1일간병',
     * `cs_col_27` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
     * DEFAULT NULL COMMENT '간병구분',
     * `cs_col_28` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
     * DEFAULT NULL COMMENT '행위료',
     * `cs_col_29` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
     * DEFAULT NULL COMMENT '한방협진',
     * `cs_col_30` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
     * DEFAULT NULL COMMENT '용창유무',
     * `cs_col_31` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
     * DEFAULT NULL COMMENT '합계',
     * `cs_col_32` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
     * DEFAULT NULL COMMENT '상담내용',
     * `cs_col_33` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
     * DEFAULT NULL COMMENT '소속기관코드',
     * `cs_col_34` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
     * DEFAULT NULL COMMENT '소속기관명',
     * `cs_col_35` varchar(2) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
     * DEFAULT NULL COMMENT '보이기=y/감추기=n',
     * `cs_col_36` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
     * DEFAULT NULL COMMENT '최종수정',
     * `cs_col_37` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
     * DEFAULT NULL COMMENT '결정일',
     * `cs_col_38` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
     * DEFAULT NULL COMMENT '병실호수',
     * `cs_col_39` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
     * DEFAULT NULL COMMENT '앰블여부',
     * `cs_col_40` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
     * DEFAULT NULL COMMENT '차트번호',
     * `cs_col_41` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
     * DEFAULT NULL COMMENT '주치의',
     * `cs_col_42` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
     * DEFAULT NULL COMMENT '예상환자분류군'
     * );
     * 
     */
    private String inst;

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

    private Integer cs_idx;
    private int idx;

    /**
     * @return the idx
     */
    public int getIdx() {
        return idx;
    }

    /**
     * @param idx the idx to set
     */
    public void setIdx(int idx) {
        this.idx = idx;
    }

    private byte[] cs_col_01_raw; // 추가

    public byte[] getCs_col_01_raw() {
        return cs_col_01_raw;
    }

    public void setCs_col_01_raw(byte[] v) {
        this.cs_col_01_raw = v;
    }

    private Timestamp created_at;
    private Timestamp updated_at;
    private String cs_col_01;
    private String cs_col_02;
    private String cs_col_03;
    private String cs_col_04;
    private String cs_col_05;
    private String cs_col_06;
    private String cs_col_07;
    private String cs_col_07_text;
    private String cs_col_08;
    private String cs_col_08_text;
    private String cs_col_09;
    private String cs_col_10;
    private String cs_col_11;
    private String cs_col_12;
    // private List<String> cs_col_13; // Guardian names
    // private List<String> cs_col_14; // Relationships
    // private List<String> cs_col_15; // Contact numbers
    // 바꾼 후 (DB 컬럼과 동일하게 String)
    private String cs_col_13;
    private String cs_col_14;
    private String cs_col_15;
    private String cs_col_16;
    private String cs_col_17;
    private String cs_col_18;
    private String cs_col_19;
    private String cs_col_20;
    private String cs_col_21;
    private String cs_col_22;
    private String cs_col_23;
    private String cs_col_24;
    private String cs_col_25;
    private String cs_col_26;
    private String cs_col_27;
    private String cs_col_28;
    private String cs_col_29;
    private String cs_col_30;
    private String cs_col_31;
    private String cs_col_32;
    private String cs_col_33;
    private String cs_col_34;
    private String cs_col_35;
    private String cs_col_36;
    private String cs_col_37;
    private String cs_col_38;
    private String cs_col_39;
    private String cs_col_40;
    private String cs_col_41;
    private String cs_col_42;
    private List<CounselDataEntry> entries; // 엔트리 리스트 추가
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "cs_idx") // Maps to guardians.counsel_data_id
    private List<Guardian> guardians = new ArrayList<>();
    private byte[] cs_col_01_hash;

    /**
     * @return the cs_idx
     */
    public Integer getCs_idx() {
        return cs_idx;
    }

    public byte[] getCs_col_01_hash() {
        return cs_col_01_hash;
    }

    public void setCs_col_01_hash(byte[] cs_col_01_hash) {
        this.cs_col_01_hash = cs_col_01_hash;
    }

    /**
     * @param cs_idx the cs_idx to set
     */
    public void setCs_idx(Integer cs_idx) {
        this.cs_idx = cs_idx;
    }

    /**
     * @return the created_at
     */
    public Timestamp getCreated_at() {
        return created_at;
    }

    /**
     * @param created_at the created_at to set
     */
    public void setCreated_at(Timestamp created_at) {
        this.created_at = created_at;
    }

    /**
     * @return the updated_at
     */
    public Timestamp getUpdated_at() {
        return updated_at;
    }

    /**
     * @param updated_at the updated_at to set
     */
    public void setUpdated_at(Timestamp updated_at) {
        this.updated_at = updated_at;
    }

    /**
     * @return the cs_col_01
     */
    public String getCs_col_01() {
        return cs_col_01;
    }

    /**
     * @param cs_col_01 the cs_col_01 to set
     */
    public void setCs_col_01(String cs_col_01) {
        this.cs_col_01 = cs_col_01;
    }

    /**
     * @return the cs_col_02
     */
    public String getCs_col_02() {
        return cs_col_02;
    }

    /**
     * @param cs_col_02 the cs_col_02 to set
     */
    public void setCs_col_02(String cs_col_02) {
        this.cs_col_02 = cs_col_02;
    }

    /**
     * @return the cs_col_03
     */
    public String getCs_col_03() {
        return cs_col_03;
    }

    /**
     * @param cs_col_03 the cs_col_03 to set
     */
    public void setCs_col_03(String cs_col_03) {
        this.cs_col_03 = cs_col_03;
    }

    /**
     * @return the cs_col_04
     */
    public String getCs_col_04() {
        return cs_col_04;
    }

    /**
     * @param cs_col_04 the cs_col_04 to set
     */
    public void setCs_col_04(String cs_col_04) {
        this.cs_col_04 = cs_col_04;
    }

    /**
     * @return the cs_col_05
     */
    public String getCs_col_05() {
        return cs_col_05;
    }

    /**
     * @param cs_col_05 the cs_col_05 to set
     */
    public void setCs_col_05(String cs_col_05) {
        this.cs_col_05 = cs_col_05;
    }

    /**
     * @return the cs_col_06
     */
    public String getCs_col_06() {
        return cs_col_06;
    }

    /**
     * @param cs_col_06 the cs_col_06 to set
     */
    public void setCs_col_06(String cs_col_06) {
        this.cs_col_06 = cs_col_06;
    }

    /**
     * @return the cs_col_07
     */
    public String getCs_col_07() {
        return cs_col_07;
    }

    /**
     * @param cs_col_07 the cs_col_07 to set
     */
    public void setCs_col_07(String cs_col_07) {
        this.cs_col_07 = cs_col_07;
    }

    /**
     * @return the cs_col_07_text
     */
    public String getCs_col_07_text() {
        return cs_col_07_text;
    }

    /**
     * @param cs_col_07_text the cs_col_07_text to set
     */
    public void setCs_col_07_text(String cs_col_07_text) {
        this.cs_col_07_text = cs_col_07_text;
    }

    /**
     * @return the cs_col_08
     */
    public String getCs_col_08() {
        return cs_col_08;
    }

    /**
     * @param cs_col_08 the cs_col_08 to set
     */
    public void setCs_col_08(String cs_col_08) {
        this.cs_col_08 = cs_col_08;
    }

    /**
     * @return the cs_col_08_text
     */
    public String getCs_col_08_text() {
        return cs_col_08_text;
    }

    /**
     * @param cs_col_08_text the cs_col_08_text to set
     */
    public void setCs_col_08_text(String cs_col_08_text) {
        this.cs_col_08_text = cs_col_08_text;
    }

    /**
     * @return the cs_col_09
     */
    public String getCs_col_09() {
        return cs_col_09;
    }

    /**
     * @param cs_col_09 the cs_col_09 to set
     */
    public void setCs_col_09(String cs_col_09) {
        this.cs_col_09 = cs_col_09;
    }

    /**
     * @return the cs_col_10
     */
    public String getCs_col_10() {
        return cs_col_10;
    }

    /**
     * @param cs_col_10 the cs_col_10 to set
     */
    public void setCs_col_10(String cs_col_10) {
        this.cs_col_10 = cs_col_10;
    }

    /**
     * @return the cs_col_11
     */
    public String getCs_col_11() {
        return cs_col_11;
    }

    /**
     * @param cs_col_11 the cs_col_11 to set
     */
    public void setCs_col_11(String cs_col_11) {
        this.cs_col_11 = cs_col_11;
    }

    /**
     * @return the cs_col_12
     */
    public String getCs_col_12() {
        return cs_col_12;
    }

    /**
     * @param cs_col_12 the cs_col_12 to set
     */
    public void setCs_col_12(String cs_col_12) {
        this.cs_col_12 = cs_col_12;
    }

    // /**
    // * @return the cs_col_13
    // */
    // public List<String> getCs_col_13() {
    // return cs_col_13;
    // }

    // public void setCs_col_13(List<String> cs_col_13) {
    // this.cs_col_13 = cs_col_13;
    // }

    // public List<String> getCs_col_14() {
    // return cs_col_14;
    // }

    // public void setCs_col_14(List<String> cs_col_14) {
    // this.cs_col_14 = cs_col_14;
    // }

    // public List<String> getCs_col_15() {
    // return cs_col_15;
    // }

    // public void setCs_col_15(List<String> cs_col_15) {
    // this.cs_col_15 = cs_col_15;
    // }

    public String getCs_col_13() {
        return cs_col_13;
    }

    public void setCs_col_13(String v) {
        this.cs_col_13 = v;
    }

    public String getCs_col_14() {
        return cs_col_14;
    }

    public void setCs_col_14(String v) {
        this.cs_col_14 = v;
    }

    public String getCs_col_15() {
        return cs_col_15;
    }

    public void setCs_col_15(String v) {
        this.cs_col_15 = v;
    }

    /**
     * @return the cs_col_16
     */
    public String getCs_col_16() {
        return cs_col_16;
    }

    /**
     * @param cs_col_16 the cs_col_16 to set
     */
    public void setCs_col_16(String cs_col_16) {
        this.cs_col_16 = cs_col_16;
    }

    /**
     * @return the cs_col_17
     */
    public String getCs_col_17() {
        return cs_col_17;
    }

    /**
     * @param cs_col_17 the cs_col_17 to set
     */
    public void setCs_col_17(String cs_col_17) {
        this.cs_col_17 = cs_col_17;
    }

    /**
     * @return the cs_col_18
     */
    public String getCs_col_18() {
        return cs_col_18;
    }

    /**
     * @param cs_col_18 the cs_col_18 to set
     */
    public void setCs_col_18(String cs_col_18) {
        this.cs_col_18 = cs_col_18;
    }

    /**
     * @return the cs_col_19
     */
    public String getCs_col_19() {
        return cs_col_19;
    }

    /**
     * @param cs_col_19 the cs_col_19 to set
     */
    public void setCs_col_19(String cs_col_19) {
        this.cs_col_19 = cs_col_19;
    }

    /**
     * @return the cs_col_20
     */
    public String getCs_col_20() {
        return cs_col_20;
    }

    /**
     * @param cs_col_20 the cs_col_20 to set
     */
    public void setCs_col_20(String cs_col_20) {
        this.cs_col_20 = cs_col_20;
    }

    /**
     * @return the cs_col_21
     */
    public String getCs_col_21() {
        return cs_col_21;
    }

    /**
     * @param cs_col_21 the cs_col_21 to set
     */
    public void setCs_col_21(String cs_col_21) {
        this.cs_col_21 = cs_col_21;
    }

    /**
     * @return the cs_col_22
     */
    public String getCs_col_22() {
        return cs_col_22;
    }

    /**
     * @param cs_col_22 the cs_col_22 to set
     */
    public void setCs_col_22(String cs_col_22) {
        this.cs_col_22 = cs_col_22;
    }

    /**
     * @return the cs_col_23
     */
    public String getCs_col_23() {
        return cs_col_23;
    }

    /**
     * @param cs_col_23 the cs_col_23 to set
     */
    public void setCs_col_23(String cs_col_23) {
        this.cs_col_23 = cs_col_23;
    }

    /**
     * @return the cs_col_24
     */
    public String getCs_col_24() {
        return cs_col_24;
    }

    /**
     * @param cs_col_24 the cs_col_24 to set
     */
    public void setCs_col_24(String cs_col_24) {
        this.cs_col_24 = cs_col_24;
    }

    /**
     * @return the cs_col_25
     */
    public String getCs_col_25() {
        return cs_col_25;
    }

    /**
     * @param cs_col_25 the cs_col_25 to set
     */
    public void setCs_col_25(String cs_col_25) {
        this.cs_col_25 = cs_col_25;
    }

    /**
     * @return the cs_col_26
     */
    public String getCs_col_26() {
        return cs_col_26;
    }

    /**
     * @param cs_col_26 the cs_col_26 to set
     */
    public void setCs_col_26(String cs_col_26) {
        this.cs_col_26 = cs_col_26;
    }

    /**
     * @return the cs_col_27
     */
    public String getCs_col_27() {
        return cs_col_27;
    }

    /**
     * @param cs_col_27 the cs_col_27 to set
     */
    public void setCs_col_27(String cs_col_27) {
        this.cs_col_27 = cs_col_27;
    }

    /**
     * @return the cs_col_28
     */
    public String getCs_col_28() {
        return cs_col_28;
    }

    /**
     * @param cs_col_28 the cs_col_28 to set
     */
    public void setCs_col_28(String cs_col_28) {
        this.cs_col_28 = cs_col_28;
    }

    /**
     * @return the cs_col_29
     */
    public String getCs_col_29() {
        return cs_col_29;
    }

    /**
     * @param cs_col_29 the cs_col_29 to set
     */
    public void setCs_col_29(String cs_col_29) {
        this.cs_col_29 = cs_col_29;
    }

    /**
     * @return the cs_col_30
     */
    public String getCs_col_30() {
        return cs_col_30;
    }

    /**
     * @param cs_col_30 the cs_col_30 to set
     */
    public void setCs_col_30(String cs_col_30) {
        this.cs_col_30 = cs_col_30;
    }

    /**
     * @return the cs_col_31
     */
    public String getCs_col_31() {
        return cs_col_31;
    }

    /**
     * @param cs_col_31 the cs_col_31 to set
     */
    public void setCs_col_31(String cs_col_31) {
        this.cs_col_31 = cs_col_31;
    }

    /**
     * @return the cs_col_32
     */
    public String getCs_col_32() {
        return cs_col_32;
    }

    /**
     * @param cs_col_32 the cs_col_32 to set
     */
    public void setCs_col_32(String cs_col_32) {
        this.cs_col_32 = cs_col_32;
    }

    /**
     * @return the cs_col_33
     */
    public String getCs_col_33() {
        return cs_col_33;
    }

    /**
     * @param cs_col_33 the cs_col_33 to set
     */
    public void setCs_col_33(String cs_col_33) {
        this.cs_col_33 = cs_col_33;
    }

    /**
     * @return the cs_col_34
     */
    public String getCs_col_34() {
        return cs_col_34;
    }

    /**
     * @param cs_col_34 the cs_col_34 to set
     */
    public void setCs_col_34(String cs_col_34) {
        this.cs_col_34 = cs_col_34;
    }

    /**
     * @return the cs_col_35
     */
    public String getCs_col_35() {
        return cs_col_35;
    }

    /**
     * @param cs_col_35 the cs_col_35 to set
     */
    public void setCs_col_35(String cs_col_35) {
        this.cs_col_35 = cs_col_35;
    }

    /**
     * @return the cs_col_36
     */
    public String getCs_col_36() {
        return cs_col_36;
    }

    /**
     * @param cs_col_36 the cs_col_36 to set
     */
    public void setCs_col_36(String cs_col_36) {
        this.cs_col_36 = cs_col_36;
    }

    /**
     * @return the cs_col_37
     */
    public String getCs_col_37() {
        return cs_col_37;
    }

    /**
     * @param cs_col_37 the cs_col_37 to set
     */
    public void setCs_col_37(String cs_col_37) {
        this.cs_col_37 = cs_col_37;
    }

    /**
     * @return the cs_col_38
     */
    public String getCs_col_38() {
        return cs_col_38;
    }

    /**
     * @param cs_col_38 the cs_col_38 to set
     */
    public void setCs_col_38(String cs_col_38) {
        this.cs_col_38 = cs_col_38;
    }

    /**
     * @return the cs_col_39
     */
    public String getCs_col_39() {
        return cs_col_39;
    }

    /**
     * @param cs_col_39 the cs_col_39 to set
     */
    public void setCs_col_39(String cs_col_39) {
        this.cs_col_39 = cs_col_39;
    }

    /**
     * @return the cs_col_40
     */
    public String getCs_col_40() {
        return cs_col_40;
    }

    /**
     * @param cs_col_40 the cs_col_40 to set
     */
    public void setCs_col_40(String cs_col_40) {
        this.cs_col_40 = cs_col_40;
    }

    /**
     * @return the cs_col_41
     */
    public String getCs_col_41() {
        return cs_col_41;
    }

    /**
     * @param cs_col_41 the cs_col_41 to set
     */
    public void setCs_col_41(String cs_col_41) {
        this.cs_col_41 = cs_col_41;
    }

    /**
     * @return the cs_col_42
     */
    public String getCs_col_42() {
        return cs_col_42;
    }

    /**
     * @param cs_col_42 the cs_col_42 to set
     */
    public void setCs_col_42(String cs_col_42) {
        this.cs_col_42 = cs_col_42;
    }

    /**
     * @return the entries
     */
    public List<CounselDataEntry> getEntries() {
        return entries;
    }

    /**
     * @param entries the entries to set
     */
    public void setEntries(List<CounselDataEntry> entries) {
        this.entries = entries;
    }

    public List<Guardian> getGuardians() {
        return guardians;
    }

    public void setGuardians(List<Guardian> guardians) {
        this.guardians = guardians;
    }

    @Override
    public String toString() {
        return "CounselData [inst=" + inst + ", cs_idx=" + cs_idx + ", idx=" + idx + ", created_at=" + created_at
                + ", updated_at=" + updated_at + ", cs_col_01=" + cs_col_01 + ", cs_col_02=" + cs_col_02
                + ", cs_col_03=" + cs_col_03 + ", cs_col_04=" + cs_col_04 + ", cs_col_05=" + cs_col_05 + ", cs_col_06="
                + cs_col_06 + ", cs_col_07=" + cs_col_07 + ", cs_col_07_text=" + cs_col_07_text + ", cs_col_08="
                + cs_col_08 + ", cs_col_08_text=" + cs_col_08_text + ", cs_col_09=" + cs_col_09 + ", cs_col_10="
                + cs_col_10 + ", cs_col_11=" + cs_col_11 + ", cs_col_12=" + cs_col_12 + ", cs_col_13=" + cs_col_13
                + ", cs_col_14=" + cs_col_14 + ", cs_col_15=" + cs_col_15 + ", cs_col_16=" + cs_col_16 + ", cs_col_17="
                + cs_col_17 + ", cs_col_18=" + cs_col_18 + ", cs_col_19=" + cs_col_19 + ", cs_col_20=" + cs_col_20
                + ", cs_col_21=" + cs_col_21 + ", cs_col_22=" + cs_col_22 + ", cs_col_23=" + cs_col_23 + ", cs_col_24="
                + cs_col_24 + ", cs_col_25=" + cs_col_25 + ", cs_col_26=" + cs_col_26 + ", cs_col_27=" + cs_col_27
                + ", cs_col_28=" + cs_col_28 + ", cs_col_29=" + cs_col_29 + ", cs_col_30=" + cs_col_30 + ", cs_col_31="
                + cs_col_31 + ", cs_col_32=" + cs_col_32 + ", cs_col_33=" + cs_col_33 + ", cs_col_34=" + cs_col_34
                + ", cs_col_35=" + cs_col_35 + ", cs_col_36=" + cs_col_36 + ", cs_col_37=" + cs_col_37 + ", cs_col_38="
                + cs_col_38 + ", cs_col_39=" + cs_col_39 + ", cs_col_40=" + cs_col_40 + ", cs_col_41=" + cs_col_41
                + ", cs_col_42=" + cs_col_42 + ", entries=" + entries + ", guardians=" + guardians + ", cs_col_01_hash="
                + Arrays.toString(cs_col_01_hash) + "]";
    }

}
