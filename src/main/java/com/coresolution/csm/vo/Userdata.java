package com.coresolution.csm.vo;

public class Userdata {

    /*
     * CREATE TABLE `user_data_hsop_0001` (
     * `us_col_01` int NOT NULL AUTO_INCREMENT COMMENT '사용자코드',
     * `us_col_02` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT
     * NULL COMMENT '아이디',
     * `us_col_03` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
     * DEFAULT NULL COMMENT '사용자비번',
     * `us_col_04` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
     * DEFAULT NULL COMMENT '소속회사코드',
     * `us_col_05` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
     * DEFAULT NULL COMMENT '소속회사명',
     * `us_col_06` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
     * DEFAULT NULL COMMENT '비고',
     * `us_col_07` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
     * DEFAULT 'y' COMMENT '사용상태',
     * `us_col_08` int DEFAULT '1' COMMENT '권한',
     * `us_col_09` int DEFAULT '1' COMMENT '1=사용,2=삭제',
     * `us_col_10` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
     * DEFAULT NULL COMMENT '연락처',
     * `us_col_11` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
     * DEFAULT NULL COMMENT '이메일',
     * `us_col_12` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci
     * DEFAULT NULL COMMENT '이름',
     * PRIMARY KEY (`us_col_01`)
     * ) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4
     * COLLATE=utf8mb4_unicode_ci COMMENT='사용자정보';
     * 
     * 
     */

    private int us_col_01;
    private String us_col_02;
    private String us_col_03;
    private String us_col_04;
    private String us_col_05;
    private String us_col_06;
    private String us_col_07;
    private int us_col_08;
    private int us_col_09;
    private String us_col_10;
    private String us_col_11;
    private String us_col_12;
    private String us_col_13;
    private String us_col_14;
    private String resetToken;

    /**
     * @return the us_col_01
     */
    public int getUs_col_01() {
        return us_col_01;
    }

    /**
     * @param us_col_01 the us_col_01 to set
     */
    public void setUs_col_01(int us_col_01) {
        this.us_col_01 = us_col_01;
    }

    /**
     * @return the us_col_02
     */
    public String getUs_col_02() {
        return us_col_02;
    }

    /**
     * @param us_col_02 the us_col_02 to set
     */
    public void setUs_col_02(String us_col_02) {
        this.us_col_02 = us_col_02;
    }

    /**
     * @return the us_col_03
     */
    public String getUs_col_03() {
        return us_col_03;
    }

    /**
     * @param us_col_03 the us_col_03 to set
     */
    public void setUs_col_03(String us_col_03) {
        this.us_col_03 = us_col_03;
    }

    /**
     * @return the us_col_04
     */
    public String getUs_col_04() {
        return us_col_04;
    }

    /**
     * @param us_col_04 the us_col_04 to set
     */
    public void setUs_col_04(String us_col_04) {
        this.us_col_04 = us_col_04;
    }

    /**
     * @return the us_col_05
     */
    public String getUs_col_05() {
        return us_col_05;
    }

    /**
     * @param us_col_05 the us_col_05 to set
     */
    public void setUs_col_05(String us_col_05) {
        this.us_col_05 = us_col_05;
    }

    /**
     * @return the us_col_06
     */
    public String getUs_col_06() {
        return us_col_06;
    }

    /**
     * @param us_col_06 the us_col_06 to set
     */
    public void setUs_col_06(String us_col_06) {
        this.us_col_06 = us_col_06;
    }

    /**
     * @return the us_col_07
     */
    public String getUs_col_07() {
        return us_col_07;
    }

    /**
     * @param us_col_07 the us_col_07 to set
     */
    public void setUs_col_07(String us_col_07) {
        this.us_col_07 = us_col_07;
    }

    /**
     * @return the us_col_08
     */
    public int getUs_col_08() {
        return us_col_08;
    }

    /**
     * @param us_col_08 the us_col_08 to set
     */
    public void setUs_col_08(int us_col_08) {
        this.us_col_08 = us_col_08;
    }

    /**
     * @return the us_col_09
     */
    public int getUs_col_09() {
        return us_col_09;
    }

    /**
     * @param us_col_09 the us_col_09 to set
     */
    public void setUs_col_09(int us_col_09) {
        this.us_col_09 = us_col_09;
    }

    /**
     * @return the us_col_10
     */
    public String getUs_col_10() {
        return us_col_10;
    }

    /**
     * @param us_col_10 the us_col_10 to set
     */
    public void setUs_col_10(String us_col_10) {
        this.us_col_10 = us_col_10;
    }

    /**
     * @return the us_col_11
     */
    public String getUs_col_11() {
        return us_col_11;
    }

    /**
     * @param us_col_11 the us_col_11 to set
     */
    public void setUs_col_11(String us_col_11) {
        this.us_col_11 = us_col_11;
    }

    /**
     * @return the us_col_12
     */
    public String getUs_col_12() {
        return us_col_12;
    }

    /**
     * @param us_col_12 the us_col_12 to set
     */
    public void setUs_col_12(String us_col_12) {
        this.us_col_12 = us_col_12;
    }

    /**
     * @return the us_col_13
     */
    public String getUs_col_13() {
        return us_col_13;
    }

    /**
     * @param us_col_13 the us_col_13 to set
     */
    public void setUs_col_13(String us_col_13) {
        this.us_col_13 = us_col_13;
    }

    /**
     * @return the us_col_14
     */
    public String getUs_col_14() {
        return us_col_14;
    }

    /**
     * @param us_col_14 the us_col_14 to set
     */
    public void setUs_col_14(String us_col_14) {
        this.us_col_14 = us_col_14;
    }

    /**
     * @return the resetToken
     */
    public String getResetToken() {
        return resetToken;
    }

    /**
     * @param resetToken the resetToken to set
     */
    public void setResetToken(String resetToken) {
        this.resetToken = resetToken;
    }

    @Override
    public String toString() {
        return "Userdata [us_col_01=" + us_col_01 + ", us_col_02=" + us_col_02 + ", us_col_03=" + us_col_03
                + ", us_col_04=" + us_col_04 + ", us_col_05=" + us_col_05 + ", us_col_06=" + us_col_06 + ", us_col_07="
                + us_col_07 + ", us_col_08=" + us_col_08 + ", us_col_09=" + us_col_09 + ", us_col_10=" + us_col_10
                + ", us_col_11=" + us_col_11 + ", us_col_12=" + us_col_12 + ", us_col_13=" + us_col_13 + ", us_col_14="
                + us_col_14 + ", resetToken=" + resetToken + "]";
    }

}
