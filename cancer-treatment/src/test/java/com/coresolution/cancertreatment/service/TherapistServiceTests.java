package com.coresolution.cancertreatment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.coresolution.cancertreatment.model.Therapist;
import com.coresolution.cancertreatment.model.TherapistRequest;

@SpringBootTest
class TherapistServiceTests {

    @Autowired
    private TherapistService therapistService;

    @Test
    void createThenList() {
        TherapistRequest req = new TherapistRequest();
        req.setName("김치료");
        therapistService.createTherapist("th", req);

        List<Therapist> list = therapistService.listTherapists("th");
        assertThat(list).extracting(Therapist::getName).contains("김치료");
    }

    @Test
    void rejectsDuplicateName() {
        TherapistRequest a = new TherapistRequest();
        a.setName("이치료");
        therapistService.createTherapist("th", a);

        TherapistRequest b = new TherapistRequest();
        b.setName("이치료");
        assertThatThrownBy(() -> therapistService.createTherapist("th", b))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이미 등록된 치료사");
    }
}
