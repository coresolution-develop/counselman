package com.coresolution.csm.vo;

public class AjaxResponse {

    private String result;
    private String message; // Add a message field for additional information
    private Object data; // ✅ `data` 필드를 추가하여 다양한 데이터 저장 가능

    public AjaxResponse() {
    }

    // Constructor with result and message
    public AjaxResponse(String result, String message) {
        this.result = result;
        this.message = message;
    }

    // Getters and Setters
    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    /**
     * @return the data
     */
    public Object getData() {
        return data;
    }

    /**
     * @param data the data to set
     */
    public void setData(Object data) {
        this.data = data;
    }

    @Override
    public String toString() {
        return "AjaxResponse [result=" + result + ", message=" + message + ", data=" + data + "]";
    }

}
