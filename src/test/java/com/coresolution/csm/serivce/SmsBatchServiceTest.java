package com.coresolution.csm.serivce;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

class SmsBatchServiceTest {

    private SmsService smsService;
    private ExternalSmsGatewayService gateway;
    private JdbcTemplate jdbcTemplate;
    private SmsBatchService service;

    private static final String INST = "COHS";

    @BeforeEach
    void setUp() {
        smsService = mock(SmsService.class);
        gateway = mock(ExternalSmsGatewayService.class);
        jdbcTemplate = mock(JdbcTemplate.class);
        service = new SmsBatchService(smsService, new SmsMessageTypeResolver(), gateway, jdbcTemplate);
        ReflectionTestUtils.setField(service, "maxRecipients", 500);
        ReflectionTestUtils.setField(service, "sendDelayMs", 0L);

        when(smsService.isRegisteredSenderNumber(eq(INST), anyString())).thenReturn(true);
        when(smsService.unitCostJeon(eq(INST), anyString())).thenReturn(960);
        AtomicLong idSeq = new AtomicLong(100);
        when(smsService.insertHistoryReady(eq(INST), anyString(), anyString(), anyString(),
                anyString(), any(), anyString(), anyString()))
                .thenAnswer(inv -> idSeq.incrementAndGet());
    }

    private Map<String, Object> acceptOk() {
        return Map.of("code", "1000", "description", "success", "messagekey", "MK-1", "_raw", "{}");
    }

    @Test
    void normalizesAndDeduplicatesRecipients() throws Exception {
        when(gateway.send(any())).thenReturn(acceptOk());

        SmsBatchService.BatchOutcome outcome = service.send(INST, "tester", "idem-1", "021234567",
                "hello", List.of("010-1234-5678", "01012345678", "010 1234 5678"), null);

        // 3건이 같은 번호로 정규화되어 1건만 발송된다
        assertThat(outcome.total()).isEqualTo(1);
        assertThat(outcome.success()).isEqualTo(1);
        assertThat(outcome.results().get(0).recipient()).isEqualTo("01012345678");
        assertThat(outcome.results().get(0).historyId()).isNotNull();
    }

    @Test
    void rejectsUnregisteredSenderNumber() {
        when(smsService.isRegisteredSenderNumber(eq(INST), anyString())).thenReturn(false);

        assertThatThrownBy(() -> service.send(INST, "tester", "idem-1", "010-9999-9999",
                "hello", List.of("01012345678"), null))
                .isInstanceOf(SmsBatchService.BatchRequestException.class)
                .hasMessageContaining("발신번호");
    }

    @Test
    void rejectsWhenOverMaxRecipients() {
        ReflectionTestUtils.setField(service, "maxRecipients", 2);
        List<String> recipients = List.of("01011111111", "01022222222", "01033333333");

        assertThatThrownBy(() -> service.send(INST, "tester", "idem-1", "021234567",
                "hello", recipients, null))
                .isInstanceOf(SmsBatchService.BatchRequestException.class)
                .hasMessageContaining("최대 2건");
    }

    @Test
    void invalidRecipientBecomesFailedWithoutSending() throws Exception {
        when(gateway.send(any())).thenReturn(acceptOk());

        SmsBatchService.BatchOutcome outcome = service.send(INST, "tester", "idem-1", "021234567",
                "hello", List.of("123", "01012345678"), null);

        assertThat(outcome.total()).isEqualTo(2);
        assertThat(outcome.success()).isEqualTo(1);
        assertThat(outcome.failed()).isEqualTo(1);
        assertThat(outcome.results())
                .anySatisfy(r -> {
                    assertThat(r.recipient()).isEqualTo("123");
                    assertThat(r.status()).isEqualTo("FAILED");
                    assertThat(r.historyId()).isNull();
                });
    }

    @Test
    void gatewayFailureIsFinalizedAsFailed() throws Exception {
        when(gateway.send(any())).thenReturn(
                Map.of("code", "2001", "description", "invalid from", "_raw", "{}"));

        SmsBatchService.BatchOutcome outcome = service.send(INST, "tester", "idem-1", "021234567",
                "hello", List.of("01012345678"), null);

        assertThat(outcome.failed()).isEqualTo(1);
        verify(smsService).finalizeHistory(eq(INST), anyLong(), eq(SmsService.STATUS_FAILED),
                anyString(), any(), eq("2001"));
    }

    @Test
    void messageTimeoutIsUnknownAndBilled() throws Exception {
        when(gateway.send(any())).thenThrow(new BizppurioTimeoutException(
                BizppurioTimeoutException.Phase.MESSAGE, "https://api", 5000, 10000, null));

        SmsBatchService.BatchOutcome outcome = service.send(INST, "tester", "idem-1", "021234567",
                "hello", List.of("01012345678"), null);

        assertThat(outcome.unknown()).isEqualTo(1);
        verify(smsService).finalizeHistory(eq(INST), anyLong(), eq(SmsService.STATUS_UNKNOWN),
                any(), any(), any());
        // UNKNOWN 은 환불 금지 원칙 — total_cost 에 포함되어야 한다
        verify(jdbcTemplate).update(contains("UPDATE csm.sms_batch"),
                eq(0), eq(0), eq(1), eq(960L), anyString());
    }

    @Test
    void tokenTimeoutIsSafeFailure() throws Exception {
        when(gateway.send(any())).thenThrow(new BizppurioTimeoutException(
                BizppurioTimeoutException.Phase.TOKEN, "https://api", 5000, 10000, null));

        SmsBatchService.BatchOutcome outcome = service.send(INST, "tester", "idem-1", "021234567",
                "hello", List.of("01012345678"), null);

        assertThat(outcome.failed()).isEqualTo(1);
        assertThat(outcome.unknown()).isZero();
    }

    @Test
    void duplicateIdemKeyReturnsExistingBatchWithoutSending() throws Exception {
        when(jdbcTemplate.update(contains("INSERT INTO csm.sms_batch"), any(Object[].class)))
                .thenThrow(new DuplicateKeyException("uk_batch_idem"));
        when(jdbcTemplate.queryForMap(contains("FROM csm.sms_batch"), eq(INST), eq("idem-1")))
                .thenReturn(Map.of("batch_id", "B-1", "success_count", 1, "failed_count", 0, "unknown_count", 0));
        when(smsService.listHistoryByBatch(INST, "B-1")).thenReturn(List.of(
                Map.of("id", 101L, "to_phone", "01012345678", "status", "SENT")));

        SmsBatchService.BatchOutcome outcome = service.send(INST, "tester", "idem-1", "021234567",
                "hello", List.of("01012345678"), null);

        assertThat(outcome.duplicate()).isTrue();
        assertThat(outcome.batchId()).isEqualTo("B-1");
        assertThat(outcome.success()).isEqualTo(1);
        verify(gateway, never()).send(any());
    }

    @Test
    void refkeyIsAssignedFromHistoryId() throws Exception {
        when(gateway.send(any())).thenReturn(acceptOk());

        service.send(INST, "tester", "idem-1", "021234567", "hello", List.of("01012345678"), null);

        verify(smsService).assignHistoryRefkey(eq(INST), eq(101L), eq("MP-COHS-101"));
    }

    @Test
    void mismatchedJudgmentStillSucceedsByCode() throws Exception {
        // description 이 바뀌어도 code=1000 이면 성공으로 판정한다 (벤더 문구 변경 방어)
        when(gateway.send(any())).thenReturn(Map.of("code", "1000", "description", "OK", "_raw", "{}"));

        SmsBatchService.BatchOutcome outcome = service.send(INST, "tester", "idem-1", "021234567",
                "hello", List.of("01012345678"), null);

        assertThat(outcome.success()).isEqualTo(1);
    }

    /**
     * total_cost 오버플로 회귀 방지 (CSM-1).
     *
     * <p>int 로 계산하면 단가 × 건수가 2,147,483,647전을 넘는 순간 음수가 된다.
     * 여기서는 단가를 크게 잡아 적은 건수로 경계를 넘긴다 — 실제 사고 경로도
     * 대량 발송이 아니라 단가 입력 오류 쪽이 가깝다(단가 화면에 자릿수 검증이 없다).
     */
    @Test
    void totalCostDoesNotOverflowWithLargeUnitCost() throws Exception {
        // 5,000,000전(50,000원) × 500건 = 2,500,000,000전. int 상한을 넘는다.
        when(smsService.unitCostJeon(eq(INST), anyString())).thenReturn(5_000_000);
        when(gateway.send(any())).thenReturn(acceptOk());

        List<String> recipients = new java.util.ArrayList<>();
        for (int i = 0; i < 500; i++) {
            recipients.add(String.format("010%08d", i));
        }

        SmsBatchService.BatchOutcome outcome = service.send(INST, "tester", "idem-1", "021234567",
                "hello", recipients, null);

        assertThat(outcome.success()).isEqualTo(500);
        // int 였다면 -1,794,967,296 이 기록됐을 자리다.
        verify(jdbcTemplate).update(contains("UPDATE csm.sms_batch"),
                eq(500), eq(0), eq(0), eq(2_500_000_000L), anyString());
    }

    @Test
    void totalCostStaysCorrectAtIntBoundary() throws Exception {
        // 정확히 int 상한을 1전 넘기는 조합: 2,147,483,647 + 1
        when(smsService.unitCostJeon(eq(INST), anyString())).thenReturn(1_073_741_824); // 2^30
        when(gateway.send(any())).thenReturn(acceptOk());

        SmsBatchService.BatchOutcome outcome = service.send(INST, "tester", "idem-1", "021234567",
                "hello", List.of("01012345678", "01012345679"), null);

        assertThat(outcome.success()).isEqualTo(2);
        // 2^30 × 2 = 2^31 = 2,147,483,648. int 로는 정확히 Integer.MIN_VALUE 가 된다.
        verify(jdbcTemplate).update(contains("UPDATE csm.sms_batch"),
                eq(2), eq(0), eq(0), eq(2_147_483_648L), anyString());
    }
}
