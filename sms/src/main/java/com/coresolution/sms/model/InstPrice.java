package com.coresolution.sms.model;

/**
 * Per-institution unit prices, read from csm.inst_data_cs
 * (id_col_03 = inst code, sms_price/lms_price/mms_price stored as VARCHAR).
 */
public class InstPrice {

    private final String instCode;
    private final double smsPrice;
    private final double lmsPrice;
    private final double mmsPrice;

    public InstPrice(String instCode, double smsPrice, double lmsPrice, double mmsPrice) {
        this.instCode = instCode;
        this.smsPrice = smsPrice;
        this.lmsPrice = lmsPrice;
        this.mmsPrice = mmsPrice;
    }

    public static InstPrice zero(String instCode) {
        return new InstPrice(instCode, 0.0, 0.0, 0.0);
    }

    public String getInstCode() {
        return instCode;
    }

    public double getSmsPrice() {
        return smsPrice;
    }

    public double getLmsPrice() {
        return lmsPrice;
    }

    public double getMmsPrice() {
        return mmsPrice;
    }
}
