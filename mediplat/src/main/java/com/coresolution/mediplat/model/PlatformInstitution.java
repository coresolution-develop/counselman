package com.coresolution.mediplat.model;

public class PlatformInstitution {
    private final Long id;
    private final String instCode;
    private final String instName;
    private final String useYn;

    public PlatformInstitution(Long id, String instCode, String instName, String useYn) {
        this.id = id;
        this.instCode = instCode;
        this.instName = instName;
        this.useYn = useYn;
    }

    public Long getId() {
        return id;
    }

    public String getInstCode() {
        return instCode;
    }

    public String getInstName() {
        return instName;
    }

    public String getUseYn() {
        return useYn;
    }
}
