package com.coresolution.cancertreatment.service;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.coresolution.cancertreatment.model.TreatmentPackage;
import com.coresolution.cancertreatment.model.TreatmentPackageRequest;
import com.coresolution.cancertreatment.repository.TreatmentPackageRepository;

@Service
public class TreatmentPackageService {

    private static final Set<String> ALLOWED_BILLING_UNITS = Set.of("WEEK", "DAY");

    private final TreatmentPackageRepository repository;

    public TreatmentPackageService(TreatmentPackageRepository repository) {
        this.repository = repository;
    }

    public List<TreatmentPackage> listPackages(String instCode, Long treatmentRoomId, Long categoryId) {
        return repository.findPackages(requireInst(instCode), treatmentRoomId, categoryId);
    }

    public TreatmentPackage createPackage(String instCode, TreatmentPackageRequest request) {
        Valid valid = validate(request);
        return repository.createPackage(
                requireInst(instCode),
                valid.categoryId(),
                valid.treatmentRoomId(),
                valid.packageName(),
                valid.abbreviation(),
                valid.unitPrice(),
                valid.billingUnit(),
                valid.frequency(),
                valid.displayOrder());
    }

    public TreatmentPackage updatePackage(String instCode, Long id, TreatmentPackageRequest request) {
        if (id == null) {
            throw new IllegalArgumentException("항목 ID가 없습니다.");
        }
        Valid valid = validate(request);
        return repository.updatePackage(
                requireInst(instCode),
                id,
                valid.categoryId(),
                valid.treatmentRoomId(),
                valid.packageName(),
                valid.abbreviation(),
                valid.unitPrice(),
                valid.billingUnit(),
                valid.frequency(),
                valid.displayOrder());
    }

    public void deletePackage(String instCode, Long id) {
        if (id == null) {
            throw new IllegalArgumentException("항목 ID가 없습니다.");
        }
        repository.deactivatePackage(requireInst(instCode), id);
    }

    private Valid validate(TreatmentPackageRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("요청 본문이 없습니다.");
        }
        if (request.getCategoryId() == null) {
            throw new IllegalArgumentException("카테고리를 선택해주세요.");
        }
        if (request.getTreatmentRoomId() == null) {
            throw new IllegalArgumentException("치료실을 선택해주세요.");
        }
        String name = request.getPackageName();
        if (!StringUtils.hasText(name)) {
            throw new IllegalArgumentException("항목명을 입력해주세요.");
        }
        if (name.length() > 200) {
            throw new IllegalArgumentException("항목명은 200자 이하로 입력해주세요.");
        }
        String abbreviation = request.getAbbreviation();
        if (abbreviation != null) abbreviation = abbreviation.trim();
        if (StringUtils.hasText(abbreviation) && abbreviation.length() > 50) {
            throw new IllegalArgumentException("약어는 50자 이하로 입력해주세요.");
        }
        if (!StringUtils.hasText(abbreviation)) abbreviation = null;
        int unitPrice = request.getUnitPrice() == null ? 0 : request.getUnitPrice();
        if (unitPrice < 0) {
            throw new IllegalArgumentException("단가는 0원 이상이어야 합니다.");
        }
        String billingUnit = request.getBillingUnit() == null ? "WEEK" : request.getBillingUnit().trim().toUpperCase();
        if (!ALLOWED_BILLING_UNITS.contains(billingUnit)) {
            throw new IllegalArgumentException("주기 단위는 WEEK 또는 DAY 여야 합니다.");
        }
        int frequency = request.getFrequency() == null ? 1 : request.getFrequency();
        if (frequency < 1) {
            throw new IllegalArgumentException("횟수는 1 이상이어야 합니다.");
        }
        int displayOrder = request.getDisplayOrder() == null ? 0 : request.getDisplayOrder();
        return new Valid(
                request.getCategoryId(),
                request.getTreatmentRoomId(),
                name.trim(),
                abbreviation,
                unitPrice,
                billingUnit,
                frequency,
                displayOrder);
    }

    private String requireInst(String instCode) {
        if (!StringUtils.hasText(instCode)) {
            throw new IllegalArgumentException("병원 코드가 없습니다.");
        }
        return instCode.trim();
    }

    private record Valid(
            Long categoryId,
            Long treatmentRoomId,
            String packageName,
            String abbreviation,
            int unitPrice,
            String billingUnit,
            int frequency,
            int displayOrder) {
    }
}
