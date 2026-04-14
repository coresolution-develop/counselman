package com.coresolution.mediplat.model;

import java.util.List;

public class RoomBoardViewerAccount {
    private final String username;
    private final String displayName;
    private final String useYn;
    private final List<String> scopeInstCodes;
    private final List<String> scopeInstNames;
    private final String scopeSummary;

    public RoomBoardViewerAccount(
            String username,
            String displayName,
            String useYn,
            List<String> scopeInstCodes,
            List<String> scopeInstNames,
            String scopeSummary) {
        this.username = username;
        this.displayName = displayName;
        this.useYn = useYn;
        this.scopeInstCodes = scopeInstCodes;
        this.scopeInstNames = scopeInstNames;
        this.scopeSummary = scopeSummary;
    }

    public String getUsername() {
        return username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getUseYn() {
        return useYn;
    }

    public List<String> getScopeInstCodes() {
        return scopeInstCodes;
    }

    public List<String> getScopeInstNames() {
        return scopeInstNames;
    }

    public String getScopeSummary() {
        return scopeSummary;
    }
}
