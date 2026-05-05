package com.coresolution.csm.vo;

public class FaqDto {

    private Long   id;
    private String category;
    private String question;
    private String answer;
    private int    sortOrder;
    private String useYn;
    private String createdAt;
    private String updatedAt;

    public Long   getId()          { return id; }
    public void   setId(Long id)   { this.id = id; }

    public String getCategory()              { return category; }
    public void   setCategory(String v)      { this.category = v; }

    public String getQuestion()              { return question; }
    public void   setQuestion(String v)      { this.question = v; }

    public String getAnswer()                { return answer; }
    public void   setAnswer(String v)        { this.answer = v; }

    public int    getSortOrder()             { return sortOrder; }
    public void   setSortOrder(int v)        { this.sortOrder = v; }

    public String getUseYn()                 { return useYn; }
    public void   setUseYn(String v)         { this.useYn = v; }

    public String getCreatedAt()             { return createdAt; }
    public void   setCreatedAt(String v)     { this.createdAt = v; }

    public String getUpdatedAt()             { return updatedAt; }
    public void   setUpdatedAt(String v)     { this.updatedAt = v; }
}
