package com.coresolution.cancertreatment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.coresolution.cancertreatment.model.SettingItemRequest;
import com.coresolution.cancertreatment.model.TreatmentTypeDuration;

@SpringBootTest
class TreatmentDurationServiceTests {

    @Autowired
    private TreatmentDurationService durationService;

    @Autowired
    private SettingService settingService;

    private Long newTreatmentType(String name) {
        SettingItemRequest req = new SettingItemRequest();
        req.setName(name);
        return settingService.createItem("dur", "treatment-types", req).getId();
    }

    @Test
    void setsAndReadsDuration() {
        Long id = newTreatmentType("고주파");
        durationService.updateDuration("dur", id, 30);

        TreatmentTypeDuration row = durationService.listDurations("dur").stream()
                .filter(t -> t.getId().equals(id)).findFirst().orElseThrow();
        assertThat(row.getDurationMinutes()).isEqualTo(30);
    }

    @Test
    void zeroStoredAsNull() {
        Long id = newTreatmentType("도수");
        durationService.updateDuration("dur", id, 0);
        TreatmentTypeDuration row = durationService.listDurations("dur").stream()
                .filter(t -> t.getId().equals(id)).findFirst().orElseThrow();
        assertThat(row.getDurationMinutes()).isNull();
    }

    @Test
    void rejectsNegativeAndOverCap() {
        Long id = newTreatmentType("림프");
        assertThatThrownBy(() -> durationService.updateDuration("dur", id, -5))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> durationService.updateDuration("dur", id, 9999))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("초과");
    }
}
