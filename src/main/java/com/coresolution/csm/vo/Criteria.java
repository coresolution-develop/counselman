package com.coresolution.csm.vo;

import java.util.Arrays;

public class Criteria {
    private int page;
    private int perPageNum;

    private String keyword;
    private byte[] keywordBytes;
    private String type;
    private String dateRange;
    private String counselor;
    private String inst;

    private String startDate;
    private String endDate;

    private String key;
    private String searchType;
    private String end;
    private String status;
    private String pathType;

    private String fail;

    private String aesKey;

    public String getFail() {
        return fail;
    }

    public void setFail(String fail) {
        this.fail = fail;
    }

    public String getAesKey() {
        return aesKey;
    }

    public void setAesKey(String aesKey) {
        this.aesKey = aesKey;
    }

    public String getSearchType() {
        return searchType;
    }

    public void setSearchType(String searchType) {
        this.searchType = (searchType == null) ? "" : searchType;
    }

    public Criteria() {
        this.page = 1;
        this.perPageNum = 10;
        this.keyword = "";
        this.keywordBytes = null; // ✅ 기본값 설정
        this.dateRange = "10";
        this.type = "";
        this.counselor = "";
        this.startDate = "";
        this.endDate = "";
        this.inst = "";
        this.key = "";
        this.searchType = ""; // 기본값 추가
        this.end = "";
        this.status = "";
        this.pathType = "";
        this.fail = "";
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = (status == null) ? "" : status;
    }

    public String getPathType() {
        return pathType;
    }

    public void setPathType(String pathType) {
        this.pathType = (pathType == null) ? "" : pathType;
    }

    public String getEnd() {
        return end;
    }

    public void setEnd(String end) {
        this.end = end;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getStartDate() {
        return startDate;
    }

    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }

    public String getEndDate() {
        return endDate;
    }

    public void setEndDate(String endDate) {
        this.endDate = endDate;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDateRange() {
        return dateRange;
    }

    public void setDateRange(String dateRange) {
        this.dateRange = dateRange;
    }

    public String getCounselor() {
        return counselor;
    }

    public void setCounselor(String counselor) {
        this.counselor = counselor;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public String getInst() {
        return inst;
    }

    public void setInst(String inst) {
        this.inst = inst;
    }

    public int getPageStart() {
        return (this.page - 1) * perPageNum;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        if (page <= 0) {
            this.page = 1;
        } else {
            this.page = page;
        }
    }

    public int getPerPageNum() {
        return perPageNum;
    }

    public void setPerPageNum(int perPageNum) {
        if (perPageNum <= 0 || perPageNum > 100) {
            this.perPageNum = 10;
        } else {
            this.perPageNum = perPageNum;
        }
    }

    public byte[] getKeywordBytes() {
        return keywordBytes;
    }

    public void setKeywordBytes(byte[] keywordBytes) {
        this.keywordBytes = keywordBytes;
        // this.keyword = null; // 🔥 기존 `keyword` 초기화
    }

    @Override
    public String toString() {
        return "Criteria [page=" + page + ", perPageNum=" + perPageNum + ", keyword=" + keyword + ", keywordBytes="
                + Arrays.toString(keywordBytes) + ", type=" + type + ", dateRange=" + dateRange + ", counselor="
                + counselor + ", inst=" + inst + ", startDate=" + startDate + ", endDate=" + endDate + ", key=" + key
                + ", searchType=" + searchType + ", end=" + end + ", status=" + status + ", pathType=" + pathType
                + ", fail=" + fail + "]";
    }

}
