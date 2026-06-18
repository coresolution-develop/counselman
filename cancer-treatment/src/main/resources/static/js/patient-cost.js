// 환자 본인부담 총액 계산 — 환자관리/치료현황(병실별) 등에서 공용으로 재사용한다.
// 새 계산식을 만들지 말고 반드시 이 모듈을 통해 계산할 것.
window.CtPatientCost = (function () {
    const DAYS_PER_WEEK = 7;

    function packageAmount(p, weeks) {
        const unitPrice = Number(p.unitPrice || 0);
        const frequency = Number(p.frequency || 1);
        if (p.billingUnit === 'DAY') {
            return unitPrice * frequency * (weeks * DAYS_PER_WEEK);
        }
        return unitPrice * frequency * weeks;
    }

    function computePatientTotal(patient, packages) {
        if (!patient || !patient.prescriptionItemIds || !patient.prescriptionItemIds.length) return 0;
        const weeks = Number(patient.prescriptionWeeks || 0);
        // 본인부담율: 저장값이 0/없음이면 기본값 100% (0%는 본인부담 계산기에서 의미 없음)
        const copayment = patient.copaymentRate ? Number(patient.copaymentRate) : 100;
        const selectedIds = new Set((patient.prescriptionItemIds || []).map(Number));
        let subtotal = 0;
        (packages || []).forEach(function (p) {
            if (!selectedIds.has(Number(p.id))) return;
            subtotal += packageAmount(p, weeks);
        });
        let total = subtotal * copayment / 100;
        if (patient.totalDiscountType === 'PERCENT') {
            total = total * (1 - Math.min(100, patient.totalDiscountValue || 0) / 100);
        } else if (patient.totalDiscountType === 'AMOUNT') {
            total = Math.max(0, total - (patient.totalDiscountValue || 0));
        }
        return Math.round(total);
    }

    // 처방 총 회차 = 처방된 각 패키지의 수량(frequency × 주수[, DAY면 ×7]) 합. 비용 계산의 수량 로직과 동일.
    function prescribedSessionCount(patient, packages) {
        if (!patient || !patient.prescriptionItemIds || !patient.prescriptionItemIds.length) return null;
        const weeks = Number(patient.prescriptionWeeks || 0);
        if (weeks <= 0) return null;
        const selectedIds = new Set((patient.prescriptionItemIds || []).map(Number));
        let sessions = 0;
        (packages || []).forEach(function (p) {
            if (!selectedIds.has(Number(p.id))) return;
            const frequency = Number(p.frequency || 1);
            sessions += p.billingUnit === 'DAY' ? frequency * weeks * DAYS_PER_WEEK : frequency * weeks;
        });
        return sessions > 0 ? sessions : null;
    }

    return {
        computePatientTotal: computePatientTotal,
        packageAmount: packageAmount,
        prescribedSessionCount: prescribedSessionCount
    };
})();
