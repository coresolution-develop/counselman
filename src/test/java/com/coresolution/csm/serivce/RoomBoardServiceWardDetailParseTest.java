package com.coresolution.csm.serivce;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import com.coresolution.csm.vo.RoomBoardImportResult;
import com.coresolution.csm.vo.RoomBoardImportRow;

/**
 * Regression test for the rich ward-detail EMR export parser (병실현황판 상세조회):
 * leading blank column offset, and the fields only this source carries
 * (유형/보험, 상병코드, 상병명, 보호자전화).
 */
@ExtendWith(MockitoExtension.class)
class RoomBoardServiceWardDetailParseTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private static final String HEADER = String.join("\t",
            "병실", "진찰", "입원유형", "의사", "수진자", "차트번호", "유형", "나이", "성", "입원일자",
            "HD", "수술일자", "POD", "Remark", "수술명", "피보명", "전화번호", "휴대전화", "보호자",
            "보호자전화번호", "병실코드", "적용일자", "사용자", "비고", "상병코드", "상병명칭", "과목", "보험회사");

    @Test
    void parsesWardDetailExport_withLeadingBlankColumn_andRichFields() {
        // Data rows carry a leading blank column, so values sit one column right of the header.
        // 김화명: 보호자전화번호(col 20) blank, but 휴대전화(col 18) has a number — must NOT be used as guardian phone.
        String kim = row("", "3병동-0318-03", "06", "", "송태효", "김화명", "00020725", "보험", "87", "F",
                "2022-12-30", "1265", "", "", "고혈압", "", "김화명", "", "010-8422-6588", "", "",
                "003-0318-03", "20241202", "", " ", "F009", "상세불명의 알츠하이머병에서의 치매(G30.9†)", "04", " ");
        // 전은순: 보호자전화번호(col 20) populated — must be captured.
        String eun = row("", "3병동-0307-03", "02", "", "송호찬", "전은순", "00017038", "의급", "88", "F",
                "2007-04-30", "6988", "", "", "고혈압,당뇨", "", "전은순", "229-9216", "01068045737", "", "010-9999-1234",
                "003-0307-03", "20201004", "", " ", "G819", "상세불명의 편마비", "04", " ");
        String jo = row("", "3병동-0313-02", "02", "", "송호찬", "조희퐁", "00021377", "자보", "87", "M",
                "2026-03-12", "97", "", "", "", "", "조희퐁", "", "", "", "",
                "003-0313-02", "", " ", " ", "F009", "상세불명의 알츠하이머병에서의 치매(G30.9†)", "04", "09 현대해상화재보험");
        String emptyBed = row("", "3병동-0319-04", "", "", "", "", "", "", "", "",
                "", "", "", "", "", "", "", "", "", "", "", "003-0319-04", "", "", "", "", "", "", "");
        String raw = String.join("\n", HEADER, kim, eun, jo, emptyBed);

        RoomBoardService service = new RoomBoardService(jdbcTemplate);
        // Requested EXCEL_DETAIL, but the header signature must auto-detect WARD_DETAIL.
        RoomBoardImportResult result = service.previewImport("FALH", "EXCEL_DETAIL", "2026-06-16", "10:00", raw);

        assertEquals("WARD_DETAIL", result.getSourceType());

        List<RoomBoardImportRow> rows = result.getRows();
        assertEquals(3, rows.size(), "header and empty bed rows are skipped");

        RoomBoardImportRow kimRow = rows.get(0);
        assertEquals("3병동", kimRow.getWardName());
        assertEquals("318호", kimRow.getRoomName());
        assertEquals("김화명", kimRow.getPatientName());
        assertEquals("00020725", kimRow.getPatientNo());
        assertEquals("보험", kimRow.getPatientType());
        assertEquals("87", kimRow.getAge());
        assertEquals("F", kimRow.getGender());
        assertEquals("2022-12-30", kimRow.getAdmissionDate());
        assertEquals("F009", kimRow.getDiseaseCode());
        assertTrue(kimRow.getDiseaseName().contains("알츠하이머"));
        assertEquals("", kimRow.getPhoneGuardian(),
                "blank 보호자전화번호 stays blank — 휴대전화 is not substituted");

        RoomBoardImportRow eunRow = rows.get(1);
        assertEquals("전은순", eunRow.getPatientName());
        assertEquals("의급", eunRow.getPatientType());
        assertEquals("010-9999-1234", eunRow.getPhoneGuardian(),
                "populated 보호자전화번호 is captured");
        assertEquals("G819", eunRow.getDiseaseCode());

        RoomBoardImportRow joRow = rows.get(2);
        assertEquals("자보", joRow.getPatientType());
        assertEquals("", joRow.getPhoneGuardian());
        assertEquals("F009", joRow.getDiseaseCode());
    }

    private String row(String... cells) {
        return String.join("\t", cells);
    }
}
