package com.coresolution.csm.vo;

import java.time.LocalDateTime;
import java.util.Arrays;

public class Guardian {

    /*
     * 
     * CREATE TABLE counsel_data_hsop_0001_guardians (
     * id INT AUTO_INCREMENT PRIMARY KEY,
     * cs_idx INT NOT NULL,
     * name VARCHAR(255) NOT NULL,
     * relationship VARCHAR(255),
     * contact_number VARCHAR(20),
     * created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
     * updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
     * FOREIGN KEY (cs_idx) REFERENCES counsel_data_hsop_0001(cs_idx) ON DELETE
     * CASCADE
     * );
     * 
     */
    private int id;
    private Long cs_idx;
    private String name;
    private String relationship;
    private String contact_number;
    private LocalDateTime created_at;
    private LocalDateTime updated_at;
    private String inst;
    private byte[] name_hash;
    private byte[] contact_number_hash;

    public byte[] getName_hash() {
        return name_hash;
    }

    public void setName_hash(byte[] name_hash) {
        this.name_hash = name_hash;
    }

    public byte[] getContact_number_hash() {
        return contact_number_hash;
    }

    public void setContact_number_hash(byte[] contact_number_hash) {
        this.contact_number_hash = contact_number_hash;
    }

    /**
     * @return the id
     */
    public int getId() {
        return id;
    }

    /**
     * @param id the id to set
     */
    public void setId(int id) {
        this.id = id;
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
     * @return the name
     */
    public String getName() {
        return name;
    }

    /**
     * @param name the name to set
     */
    public void setName(String name) {
        this.name = name;
    }

    /**
     * @return the relationship
     */
    public String getRelationship() {
        return relationship;
    }

    /**
     * @param relationship the relationship to set
     */
    public void setRelationship(String relationship) {
        this.relationship = relationship;
    }

    /**
     * @return the created_at
     */
    public LocalDateTime getCreated_at() {
        return created_at;
    }

    /**
     * @param created_at the created_at to set
     */
    public void setCreated_at(LocalDateTime created_at) {
        this.created_at = created_at;
    }

    /**
     * @return the updated_at
     */
    public LocalDateTime getUpdated_at() {
        return updated_at;
    }

    /**
     * @param updated_at the updated_at to set
     */
    public void setUpdated_at(LocalDateTime updated_at) {
        this.updated_at = updated_at;
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
     * @return the contact_number
     */
    public String getContact_number() {
        return contact_number;
    }

    /**
     * @param contact_number the contact_number to set
     */
    public void setContact_number(String contact_number) {
        this.contact_number = contact_number;
    }

    @Override
    public String toString() {
        return "Guardian [id=" + id + ", cs_idx=" + cs_idx + ", name=" + name + ", relationship=" + relationship
                + ", contact_number=" + contact_number + ", created_at=" + created_at + ", updated_at=" + updated_at
                + ", inst=" + inst + ", name_hash=" + Arrays.toString(name_hash) + ", contact_number_hash="
                + Arrays.toString(contact_number_hash) + "]";
    }

}
