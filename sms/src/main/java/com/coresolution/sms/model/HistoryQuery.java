package com.coresolution.sms.model;

/**
 * Paged history query parameters. Mirrors the subset of csm's Criteria the history
 * list actually uses. {@code type} is "reserved"/"sent"/empty; {@code fail} non-blank
 * means include failure rows.
 */
public class HistoryQuery {

    private String inst;
    private String type;
    private String keyword;
    private String fail;
    private int page = 1;
    private int perPageNum = 10;

    public HistoryQuery() {
    }

    public HistoryQuery(String inst, String type, String keyword, String fail, int page, int perPageNum) {
        this.inst = inst;
        this.type = type;
        this.keyword = keyword;
        this.fail = fail;
        this.page = page <= 0 ? 1 : page;
        this.perPageNum = perPageNum <= 0 ? 10 : perPageNum;
    }

    /** Zero-based offset for SQL LIMIT. */
    public int getPageStart() {
        return (page - 1) * perPageNum;
    }

    public String getInst() {
        return inst;
    }

    public void setInst(String inst) {
        this.inst = inst;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public String getFail() {
        return fail;
    }

    public void setFail(String fail) {
        this.fail = fail;
    }

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = page <= 0 ? 1 : page;
    }

    public int getPerPageNum() {
        return perPageNum;
    }

    public void setPerPageNum(int perPageNum) {
        this.perPageNum = perPageNum <= 0 ? 10 : perPageNum;
    }
}
