package com.coresolution.mediplat.model;

public class PlatformService {
    private final Long id;
    private final String serviceCode;
    private final String serviceName;
    private final String baseUrl;
    private final String baseUrlLocal;
    private final String baseUrlDev;
    private final String baseUrlProd;
    private final String ssoEntryPath;
    private final String userTarget;
    private final String adminTarget;
    private final String description;
    private final String useYn;
    private final Integer displayOrder;
    private final String accessYn;

    public PlatformService(
            Long id,
            String serviceCode,
            String serviceName,
            String baseUrl,
            String baseUrlLocal,
            String baseUrlDev,
            String baseUrlProd,
            String ssoEntryPath,
            String userTarget,
            String adminTarget,
            String description,
            String useYn,
            Integer displayOrder,
            String accessYn) {
        this.id = id;
        this.serviceCode = serviceCode;
        this.serviceName = serviceName;
        this.baseUrl = baseUrl;
        this.baseUrlLocal = baseUrlLocal;
        this.baseUrlDev = baseUrlDev;
        this.baseUrlProd = baseUrlProd;
        this.ssoEntryPath = ssoEntryPath;
        this.userTarget = userTarget;
        this.adminTarget = adminTarget;
        this.description = description;
        this.useYn = useYn;
        this.displayOrder = displayOrder;
        this.accessYn = accessYn;
    }

    public Long getId() {
        return id;
    }

    public String getServiceCode() {
        return serviceCode;
    }

    public String getServiceName() {
        return serviceName;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public String getBaseUrlLocal() {
        return baseUrlLocal;
    }

    public String getBaseUrlDev() {
        return baseUrlDev;
    }

    public String getBaseUrlProd() {
        return baseUrlProd;
    }

    public String getSsoEntryPath() {
        return ssoEntryPath;
    }

    public String getUserTarget() {
        return userTarget;
    }

    public String getAdminTarget() {
        return adminTarget;
    }

    public String getDescription() {
        return description;
    }

    public String getUseYn() {
        return useYn;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public String getAccessYn() {
        return accessYn;
    }

    public boolean isEnabled() {
        return "Y".equalsIgnoreCase(useYn);
    }

    public boolean isAccessible() {
        return isEnabled() && "Y".equalsIgnoreCase(accessYn);
    }
}
