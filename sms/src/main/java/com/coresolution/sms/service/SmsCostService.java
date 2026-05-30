package com.coresolution.sms.service;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.coresolution.sms.model.InstPrice;
import com.coresolution.sms.repository.SmsRepository;

/**
 * SMS cost computation. Because csm.transmission_history_&lt;inst&gt; is shared with
 * CounselMan, every count here already includes CounselMan-originated sends — the
 * "CounselMan 사용분 합산" requirement is satisfied simply by reading these tables.
 *
 * Cost math mirrors csm PageController.ratePage / monthlyUsage / buildMonthlyBillingRows.
 */
@Service
public class SmsCostService {

    private static final Logger log = LoggerFactory.getLogger(SmsCostService.class);
    private final NumberFormat won = NumberFormat.getNumberInstance(Locale.KOREA);

    private final SmsRepository repository;

    public SmsCostService(SmsRepository repository) {
        this.repository = repository;
    }

    /** Per-institution cost summary: all-time totals, this/last month, and monthly billing rows. */
    public Map<String, Object> buildInstCost(String inst) {
        InstPrice price;
        Map<String, Integer> usage;
        List<Map<String, Object>> monthlyUsage;
        double thisMonthTotal;
        double lastMonthTotal;
        try {
            price = repository.price(inst);
            usage = repository.getSendTypeUsage(inst);
            monthlyUsage = repository.getMonthlyUsage(inst);
            thisMonthTotal = costOf(repository.getSendTypeUsageByMonth(inst, LocalDate.now()), price);
            lastMonthTotal = costOf(repository.getSendTypeUsageByMonth(inst, LocalDate.now().minusMonths(1)), price);
        } catch (Exception e) {
            // Institution with no history table yet (e.g. never sent) — show an empty,
            // zeroed view instead of failing the page. Matches the platform aggregate's resilience.
            log.warn("[sms/cost] no usage for inst={} ({})", inst, e.getMessage());
            return emptyInstCost(inst);
        }

        int smsCount = usage.getOrDefault("sms", 0);
        int lmsCount = usage.getOrDefault("lms", 0);
        int mmsCount = usage.getOrDefault("mms", 0);
        double smsTotal = smsCount * price.getSmsPrice();
        double lmsTotal = lmsCount * price.getLmsPrice();
        double mmsTotal = mmsCount * price.getMmsPrice();
        double total = smsTotal + lmsTotal + mmsTotal;

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("inst", inst);
        out.put("smsPrice", price.getSmsPrice());
        out.put("lmsPrice", price.getLmsPrice());
        out.put("mmsPrice", price.getMmsPrice());
        out.put("smsCount", smsCount);
        out.put("lmsCount", lmsCount);
        out.put("mmsCount", mmsCount);
        out.put("smsTotal", smsTotal);
        out.put("lmsTotal", lmsTotal);
        out.put("mmsTotal", mmsTotal);
        out.put("total", total);
        out.put("totalText", formatWon(total));
        out.put("thisMonthTotal", thisMonthTotal);
        out.put("thisMonthTotalText", formatWon(thisMonthTotal));
        out.put("lastMonthTotal", lastMonthTotal);
        out.put("lastMonthTotalText", formatWon(lastMonthTotal));
        out.put("monthlyBilling", buildMonthlyBillingRows(monthlyUsage, price));
        return out;
    }

    /**
     * Cross-institution aggregate (platform admin only). Each institution's counts are
     * priced with ITS OWN unit prices before summing — prices differ per institution.
     * A missing/invalid per-institution table is skipped (logged), never aborts the whole.
     */
    public Map<String, Object> buildPlatformCost() {
        Map<String, double[]> monthlyAccum = new TreeMap<>(); // month -> [sms,lms,mms,total], desc handled at end
        List<Map<String, Object>> institutions = new ArrayList<>();
        double grandTotal = 0.0;

        for (String inst : repository.listInstitutionCodes()) {
            try {
                repository.safeInst(inst); // skip codes that can't be a safe table suffix
            } catch (IllegalArgumentException e) {
                log.warn("[sms/cost/platform] skip invalid inst code: {}", inst);
                continue;
            }
            try {
                InstPrice price = repository.price(inst);
                List<Map<String, Object>> monthlyUsage = repository.getMonthlyUsage(inst);
                double instTotal = 0.0;
                for (Map<String, Object> row : monthlyUsage) {
                    String month = Objects.toString(row.get("month"), "");
                    int sms = numberToInt(row.get("sms"));
                    int lms = numberToInt(row.get("lms"));
                    int mms = numberToInt(row.get("mms"));
                    double rowTotal = sms * price.getSmsPrice() + lms * price.getLmsPrice() + mms * price.getMmsPrice();
                    instTotal += rowTotal;

                    double[] acc = monthlyAccum.computeIfAbsent(month, k -> new double[4]);
                    acc[0] += sms;
                    acc[1] += lms;
                    acc[2] += mms;
                    acc[3] += rowTotal;
                }
                grandTotal += instTotal;

                Map<String, Object> instRow = new LinkedHashMap<>();
                instRow.put("inst", inst);
                instRow.put("total", instTotal);
                instRow.put("totalText", formatWon(instTotal));
                institutions.add(instRow);
            } catch (Exception e) {
                log.warn("[sms/cost/platform] skip inst={} ({})", inst, e.getMessage());
            }
        }

        // Emit months newest-first to match the per-institution view.
        List<Map<String, Object>> monthly = new ArrayList<>();
        List<String> months = new ArrayList<>(monthlyAccum.keySet());
        for (int i = months.size() - 1; i >= 0; i--) {
            String month = months.get(i);
            double[] acc = monthlyAccum.get(month);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("month", month);
            m.put("sms", (int) acc[0]);
            m.put("lms", (int) acc[1]);
            m.put("mms", (int) acc[2]);
            m.put("total", acc[3]);
            m.put("totalText", formatWon(acc[3]));
            monthly.add(m);
        }
        institutions.sort((a, b) -> Double.compare(
                ((Number) b.get("total")).doubleValue(), ((Number) a.get("total")).doubleValue()));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("grandTotal", grandTotal);
        out.put("grandTotalText", formatWon(grandTotal));
        out.put("monthly", monthly);
        out.put("institutions", institutions);
        return out;
    }

    private Map<String, Object> emptyInstCost(String inst) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("inst", inst);
        out.put("smsPrice", 0.0);
        out.put("lmsPrice", 0.0);
        out.put("mmsPrice", 0.0);
        out.put("smsCount", 0);
        out.put("lmsCount", 0);
        out.put("mmsCount", 0);
        out.put("smsTotal", 0.0);
        out.put("lmsTotal", 0.0);
        out.put("mmsTotal", 0.0);
        out.put("total", 0.0);
        out.put("totalText", formatWon(0));
        out.put("thisMonthTotal", 0.0);
        out.put("thisMonthTotalText", formatWon(0));
        out.put("lastMonthTotal", 0.0);
        out.put("lastMonthTotalText", formatWon(0));
        out.put("monthlyBilling", new ArrayList<Map<String, Object>>());
        return out;
    }

    private List<Map<String, Object>> buildMonthlyBillingRows(List<Map<String, Object>> monthlyUsage, InstPrice price) {
        List<Map<String, Object>> rows = new ArrayList<>();
        if (monthlyUsage == null) {
            return rows;
        }
        for (Map<String, Object> row : monthlyUsage) {
            int sms = numberToInt(row.get("sms"));
            int lms = numberToInt(row.get("lms"));
            int mms = numberToInt(row.get("mms"));
            double smsTotal = sms * price.getSmsPrice();
            double lmsTotal = lms * price.getLmsPrice();
            double mmsTotal = mms * price.getMmsPrice();
            double total = smsTotal + lmsTotal + mmsTotal;

            Map<String, Object> out = new LinkedHashMap<>();
            out.put("month", Objects.toString(row.get("month"), ""));
            out.put("sms", sms);
            out.put("lms", lms);
            out.put("mms", mms);
            out.put("smsTotal", smsTotal);
            out.put("lmsTotal", lmsTotal);
            out.put("mmsTotal", mmsTotal);
            out.put("total", total);
            out.put("totalText", formatWon(total));
            rows.add(out);
        }
        return rows;
    }

    private double costOf(Map<String, Integer> usage, InstPrice price) {
        return usage.getOrDefault("sms", 0) * price.getSmsPrice()
                + usage.getOrDefault("lms", 0) * price.getLmsPrice()
                + usage.getOrDefault("mms", 0) * price.getMmsPrice();
    }

    private String formatWon(double value) {
        return won.format(Math.round(value)) + "원";
    }

    private int numberToInt(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(Objects.toString(value, "0"));
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
