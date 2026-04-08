package com.coresolution.csm.vo;

public class CounselComment {

    /*
     * 
     * SELECT
     * table_name, column_name, column_comment
     * FROM
     * information_schema.columns
     * WHERE
     * table_schema = 'csm' AND table_name = 'counsel_data_hsop_0001';
     * 
     */

    private String table_name;
    private String column_name;
    private String column_comment;
    private String inst;

    /**
     * @return the table_name
     */
    public String getTable_name() {
        return table_name;
    }

    /**
     * @param table_name the table_name to set
     */
    public void setTable_name(String table_name) {
        this.table_name = table_name;
    }

    /**
     * @return the column_name
     */
    public String getColumn_name() {
        return column_name;
    }

    /**
     * @param column_name the column_name to set
     */
    public void setColumn_name(String column_name) {
        this.column_name = column_name;
    }

    /**
     * @return the column_comment
     */
    public String getColumn_comment() {
        return column_comment;
    }

    /**
     * @param column_comment the column_comment to set
     */
    public void setColumn_comment(String column_comment) {
        this.column_comment = column_comment;
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

    @Override
    public String toString() {
        return "CounselComment [table_name=" + table_name + ", column_name=" + column_name + ", column_comment="
                + column_comment + ", inst=" + inst + "]";
    }

}
