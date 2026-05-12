package com.coresolution.cancertreatment.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.coresolution.cancertreatment.model.SettingItem;
import com.coresolution.cancertreatment.model.SettingItemRequest;

@SpringBootTest
class SettingServiceTests {

    @Autowired
    private SettingService settingService;

    @Test
    void createUpdateAndDeleteWard() {
        SettingItemRequest createRequest = new SettingItemRequest();
        createRequest.setCode("WARD_TEST");
        createRequest.setName("테스트병동");
        createRequest.setDisplayOrder(10);

        SettingItem created = settingService.createItem("core", "wards", createRequest);

        SettingItemRequest updateRequest = new SettingItemRequest();
        updateRequest.setCode("WARD_TEST");
        updateRequest.setName("테스트병동수정");
        updateRequest.setDisplayOrder(11);
        SettingItem updated = settingService.updateItem("core", "wards", created.getId(), updateRequest);

        settingService.deleteItem("core", "wards", updated.getId());

        assertThat(created.getId()).isNotNull();
        assertThat(updated.getName()).isEqualTo("테스트병동수정");
        assertThat(settingService.listItems("core", "wards"))
                .extracting(SettingItem::getId)
                .doesNotContain(updated.getId());
    }

    @Test
    void createTreatmentOptionWithColor() {
        SettingItemRequest createRequest = new SettingItemRequest();
        createRequest.setCode("COLOR_TEST");
        createRequest.setName("색상옵션");
        createRequest.setColor("#22c55e");

        SettingItem created = settingService.createItem("core", "treatment-options", createRequest);

        SettingItemRequest updateRequest = new SettingItemRequest();
        updateRequest.setCode("COLOR_TEST");
        updateRequest.setName("색상옵션수정");
        updateRequest.setColor("#ef4444");
        SettingItem updated = settingService.updateItem("core", "treatment-options", created.getId(), updateRequest);
        settingService.deleteItem("core", "treatment-options", updated.getId());

        assertThat(created.getColor()).isEqualTo("#22c55e");
        assertThat(updated.getColor()).isEqualTo("#ef4444");
    }
}
