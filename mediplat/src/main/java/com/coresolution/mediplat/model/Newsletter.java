package com.coresolution.mediplat.model;

public class Newsletter {
    private final Long id;
    private final String newsletterCode;
    private final String title;
    private final String summary;
    private final String category;
    private final String tags;
    private final String cadence;
    private final String externalUrl;
    private final String useYn;
    private final Integer displayOrder;

    public Newsletter(
            Long id,
            String newsletterCode,
            String title,
            String summary,
            String category,
            String tags,
            String cadence,
            String externalUrl,
            String useYn,
            Integer displayOrder) {
        this.id = id;
        this.newsletterCode = newsletterCode;
        this.title = title;
        this.summary = summary;
        this.category = category;
        this.tags = tags;
        this.cadence = cadence;
        this.externalUrl = externalUrl;
        this.useYn = useYn;
        this.displayOrder = displayOrder;
    }

    public Long getId() {
        return id;
    }

    public String getNewsletterCode() {
        return newsletterCode;
    }

    public String getTitle() {
        return title;
    }

    public String getSummary() {
        return summary;
    }

    public String getCategory() {
        return category;
    }

    public String getTags() {
        return tags;
    }

    public String getCadence() {
        return cadence;
    }

    public String getExternalUrl() {
        return externalUrl;
    }

    public String getUseYn() {
        return useYn;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public boolean isEnabled() {
        return "Y".equalsIgnoreCase(useYn);
    }
}
