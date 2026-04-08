package com.coresolution.csm.vo;

public class CounselDataEntry {
    /*
     * 
     * CREATE TABLE counsel_data_hsop_0001_entries (
     * entry_id INT AUTO_INCREMENT PRIMARY KEY,
     * cs_idx INT NOT NULL,
     * category_id INT NOT NULL,
     * subcategory_id INT NOT NULL,
     * value TEXT,
     * FOREIGN KEY (cs_idx) REFERENCES counsel_data_hsop_0001(cs_idx) ON DELETE
     * CASCADE,
     * FOREIGN KEY (category_id) REFERENCES counsel_category1_hsop_0001(cc_col_01)
     * ON DELETE CASCADE,
     * FOREIGN KEY (subcategory_id) REFERENCES
     * counsel_category2_hsop_0001(cc_col_01) ON DELETE CASCADE,
     * INDEX idx_cs_idx (cs_idx),
     * INDEX idx_category_id (category_id),
     * INDEX idx_subcategory_id (subcategory_id)
     * );
     * 
     * 
     */
    private Long entry_id;
    private Long cs_idx;
    private Long category_id;
    private Long subcategory_id;
    private String value;
    private String inst;
    private String fieldType;

    /**
     * @return the entry_id
     */
    public Long getEntry_id() {
        return entry_id;
    }

    /**
     * @param entry_id the entry_id to set
     */
    public void setEntry_id(Long entry_id) {
        this.entry_id = entry_id;
    }

    /**
     * @return the cs_idx
     */
    public Long getCs_idx() {
        return cs_idx;
    }

    /**
     * @param cs_idx the cs_idx to set
     */
    public void setCs_idx(Long cs_idx) {
        this.cs_idx = cs_idx;
    }

    /**
     * @return the category_id
     */
    public Long getCategory_id() {
        return category_id;
    }

    /**
     * @param category_id the category_id to set
     */
    public void setCategory_id(Long category_id) {
        this.category_id = category_id;
    }

    /**
     * @return the subcategory_id
     */
    public Long getSubcategory_id() {
        return subcategory_id;
    }

    /**
     * @param subcategory_id the subcategory_id to set
     */
    public void setSubcategory_id(Long subcategory_id) {
        this.subcategory_id = subcategory_id;
    }

    /**
     * @return the value
     */
    public String getValue() {
        return value;
    }

    /**
     * @param value the value to set
     */
    public void setValue(String value) {
        this.value = value;
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
     * @return the fieldType
     */
    public String getFieldType() {
        return fieldType;
    }

    /**
     * @param fieldType the fieldType to set
     */
    public void setFieldType(String fieldType) {
        this.fieldType = fieldType;
    }

    @Override
    public String toString() {
        return "CounselDataEntry [entry_id=" + entry_id + ", cs_idx=" + cs_idx + ", category_id=" + category_id
                + ", subcategory_id=" + subcategory_id + ", value=" + value + ", inst=" + inst + ", fieldType="
                + fieldType + "]";
    }
}
