package com.coresolution.sms.model;

import java.util.List;
import java.util.Map;

/**
 * Inbound payload for POST /api/sms/send.
 * {@code from}/{@code to} are E.164-ish digit strings; {@code type} is sms/lms/mms.
 * {@code subject} applies to lms/mms only; {@code files} to mms only.
 * {@code sendtime} is an optional epoch-seconds reservation time.
 */
public class SmsSendRequest {

    private String type;
    private String from;
    private String to;
    private String subject;
    private String message;
    private String refkey;
    private String sendtime;
    private List<Map<String, String>> files;

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public String getTo() {
        return to;
    }

    public void setTo(String to) {
        this.to = to;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getRefkey() {
        return refkey;
    }

    public void setRefkey(String refkey) {
        this.refkey = refkey;
    }

    public String getSendtime() {
        return sendtime;
    }

    public void setSendtime(String sendtime) {
        this.sendtime = sendtime;
    }

    public List<Map<String, String>> getFiles() {
        return files;
    }

    public void setFiles(List<Map<String, String>> files) {
        this.files = files;
    }
}
