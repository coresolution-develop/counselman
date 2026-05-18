package com.coresolution.csm.controller;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import jakarta.servlet.http.HttpSession;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import com.coresolution.csm.serivce.CsmAuthService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/faq")
@RequiredArgsConstructor
public class FaqApiController {

    private static final Logger log = LoggerFactory.getLogger(FaqApiController.class);

    private final CsmAuthService cs;
    private final JdbcTemplate   jdbcTemplate;

    // ─────────────────────────────────────────────────
    // 목록 조회
    // ─────────────────────────────────────────────────
    @GetMapping
    @PreAuthorize("hasAuthority('FAQ:READ') or hasRole('INST_ADMIN') or hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<?> list(
            @RequestParam(defaultValue = "") String category,
            @RequestParam(defaultValue = "") String keyword,
            HttpSession session) {
        String inst = resolveInst(session);
        if (inst == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        String safe = cs.sanitizeInstPublic(inst);

        try {
            StringBuilder sql = new StringBuilder(
                "SELECT id, category, question, answer, sort_order, use_yn," +
                " DATE_FORMAT(created_at, '%Y-%m-%d %H:%i') AS created_at" +
                " FROM csm.faq_" + safe + " WHERE use_yn = 'Y'");
            List<Object> params = new ArrayList<>();
            if (!category.isBlank()) {
                sql.append(" AND category = ?");
                params.add(category);
            }
            if (!keyword.isBlank()) {
                sql.append(" AND (question LIKE ? OR answer LIKE ?)");
                params.add("%" + keyword + "%");
                params.add("%" + keyword + "%");
            }
            sql.append(" ORDER BY sort_order ASC, id ASC");

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());
            return ResponseEntity.ok(rows);
        } catch (Exception e) {
            log.warn("[FAQ:list] inst={}: {}", safe, e.getMessage());
            return ResponseEntity.ok(List.of());
        }
    }

    // ─────────────────────────────────────────────────
    // 카테고리 목록
    // ─────────────────────────────────────────────────
    @GetMapping("/categories")
    @PreAuthorize("hasAuthority('FAQ:READ') or hasRole('INST_ADMIN') or hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<?> categories(HttpSession session) {
        String inst = resolveInst(session);
        if (inst == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        String safe = cs.sanitizeInstPublic(inst);
        try {
            List<String> cats = jdbcTemplate.queryForList(
                "SELECT DISTINCT category FROM csm.faq_" + safe
                + " WHERE use_yn = 'Y' ORDER BY category",
                String.class);
            return ResponseEntity.ok(cats);
        } catch (Exception e) {
            return ResponseEntity.ok(List.of());
        }
    }

    // ─────────────────────────────────────────────────
    // 등록
    // ─────────────────────────────────────────────────
    @PostMapping
    @PreAuthorize("hasAuthority('FAQ:CREATE') or hasRole('INST_ADMIN') or hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<?> create(@RequestBody Map<String, Object> body, HttpSession session) {
        String inst = resolveInst(session);
        if (inst == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        String safe = cs.sanitizeInstPublic(inst);

        String category = str(body.get("category"));
        String question = str(body.get("question"));
        String answer   = str(body.get("answer"));
        if (category.isBlank() || question.isBlank() || answer.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "카테고리, 질문, 답변은 필수입니다."));
        }

        try {
            int sortOrder = toInt(body.get("sortOrder"), 0);
            jdbcTemplate.update(
                "INSERT INTO csm.faq_" + safe
                + " (category, question, answer, sort_order, use_yn) VALUES (?, ?, ?, ?, 'Y')",
                category, question, answer, sortOrder);
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (Exception e) {
            log.error("[FAQ:create] inst={}: {}", safe, e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", "등록 실패: " + e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────
    // 수정
    // ─────────────────────────────────────────────────
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('FAQ:EDIT') or hasRole('INST_ADMIN') or hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<?> update(
            @PathVariable long id,
            @RequestBody Map<String, Object> body,
            HttpSession session) {
        String inst = resolveInst(session);
        if (inst == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        String safe = cs.sanitizeInstPublic(inst);

        String category = str(body.get("category"));
        String question = str(body.get("question"));
        String answer   = str(body.get("answer"));
        if (category.isBlank() || question.isBlank() || answer.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "카테고리, 질문, 답변은 필수입니다."));
        }

        try {
            int sortOrder = toInt(body.get("sortOrder"), 0);
            int rows = jdbcTemplate.update(
                "UPDATE csm.faq_" + safe
                + " SET category=?, question=?, answer=?, sort_order=?, updated_at=NOW() WHERE id=?",
                category, question, answer, sortOrder, id);
            if (rows == 0) return ResponseEntity.notFound().build();
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (Exception e) {
            log.error("[FAQ:update] inst={} id={}: {}", safe, id, e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", "수정 실패: " + e.getMessage()));
        }
    }

    // ─────────────────────────────────────────────────
    // 삭제 (소프트)
    // ─────────────────────────────────────────────────
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('FAQ:DELETE') or hasRole('INST_ADMIN') or hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<?> delete(@PathVariable long id, HttpSession session) {
        String inst = resolveInst(session);
        if (inst == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        String safe = cs.sanitizeInstPublic(inst);

        try {
            int rows = jdbcTemplate.update(
                "UPDATE csm.faq_" + safe + " SET use_yn='N', updated_at=NOW() WHERE id=?", id);
            if (rows == 0) return ResponseEntity.notFound().build();
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (Exception e) {
            log.error("[FAQ:delete] inst={} id={}: {}", safe, id, e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", "삭제 실패"));
        }
    }

    // ─────────────────────────────────────────────────
    // 엑셀 템플릿 다운로드 (샘플 양식)
    // ─────────────────────────────────────────────────
    @GetMapping("/upload/template")
    @PreAuthorize("hasAuthority('FAQ:CREATE') or hasRole('INST_ADMIN') or hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<ByteArrayResource> downloadTemplate() {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("FAQ");

            CellStyle header = wb.createCellStyle();
            Font hf = wb.createFont();
            hf.setBold(true);
            header.setFont(hf);

            String[] cols = { "카테고리*", "질문*", "답변*", "정렬순서" };
            Row head = sheet.createRow(0);
            for (int i = 0; i < cols.length; i++) {
                Cell c = head.createCell(i);
                c.setCellValue(cols[i]);
                c.setCellStyle(header);
            }

            String[][] samples = {
                { "병원이용", "주차장은 어디에 있나요?", "본관 지하 1~3층에 무료 주차가 가능합니다.", "1" },
                { "입원안내", "면회 시간은 어떻게 되나요?", "평일 18:00~20:00, 주말 10:00~20:00 입니다.", "2" },
            };
            for (int r = 0; r < samples.length; r++) {
                Row row = sheet.createRow(r + 1);
                for (int c = 0; c < samples[r].length; c++) {
                    row.createCell(c).setCellValue(samples[r][c]);
                }
            }

            for (int i = 0; i < cols.length; i++) sheet.setColumnWidth(i, 6000);
            sheet.setColumnWidth(2, 14000);
            sheet.createFreezePane(0, 1);

            // Comment row hint (optional row, ignored on upload)
            Row hint = sheet.createRow(samples.length + 2);
            Cell hc = hint.createCell(0);
            hc.setCellValue("* 표시 필드는 필수. 카테고리는 자유 입력, 정렬순서 미입력 시 0.");
            sheet.addMergedRegion(new CellRangeAddress(hint.getRowNum(), hint.getRowNum(), 0, 3));

            wb.write(out);
            byte[] bytes = out.toByteArray();
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"faq-upload-template.xlsx\"")
                    .contentLength(bytes.length)
                    .body(new ByteArrayResource(bytes));
        } catch (Exception e) {
            log.error("[FAQ:template] {}", e.getMessage(), e);
            return ResponseEntity.status(500).build();
        }
    }

    // ─────────────────────────────────────────────────
    // 엑셀 업로드 (대량 등록)
    // ─────────────────────────────────────────────────
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('FAQ:CREATE') or hasRole('INST_ADMIN') or hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<?> upload(
            @RequestParam("file") MultipartFile file,
            HttpSession session) {
        String inst = resolveInst(session);
        if (inst == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        String safe = cs.sanitizeInstPublic(inst);

        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "파일이 비어있습니다."));
        }
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (!name.endsWith(".xlsx") && !name.endsWith(".xls")) {
            return ResponseEntity.badRequest().body(Map.of("error", "엑셀(.xlsx, .xls) 파일만 업로드 가능합니다."));
        }

        int success = 0;
        int skipped = 0;
        List<Map<String, Object>> errors = new ArrayList<>();
        DataFormatter fmt = new DataFormatter();

        try (InputStream in = file.getInputStream();
             Workbook wb = WorkbookFactory.create(in)) {
            Sheet sheet = wb.getSheetAt(0);
            if (sheet == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "엑셀에 시트가 없습니다."));
            }
            int last = sheet.getLastRowNum();
            for (int r = 1; r <= last; r++) {  // row 0 = header
                Row row = sheet.getRow(r);
                if (row == null) { skipped++; continue; }

                String category = fmt.formatCellValue(row.getCell(0)).trim();
                String question = fmt.formatCellValue(row.getCell(1)).trim();
                String answer   = fmt.formatCellValue(row.getCell(2)).trim();
                String orderRaw = fmt.formatCellValue(row.getCell(3)).trim();

                if (category.isBlank() && question.isBlank() && answer.isBlank()) {
                    skipped++;
                    continue;
                }
                if (category.isBlank() || question.isBlank() || answer.isBlank()) {
                    errors.add(rowError(r + 1, "카테고리·질문·답변은 필수입니다."));
                    continue;
                }
                if (question.length() > 500) {
                    errors.add(rowError(r + 1, "질문은 500자 이내여야 합니다."));
                    continue;
                }
                int sortOrder = 0;
                if (!orderRaw.isBlank()) {
                    try { sortOrder = Integer.parseInt(orderRaw.replaceAll("[^0-9-]", "")); }
                    catch (NumberFormatException e) { sortOrder = 0; }
                }

                try {
                    jdbcTemplate.update(
                        "INSERT INTO csm.faq_" + safe
                        + " (category, question, answer, sort_order, use_yn) VALUES (?, ?, ?, ?, 'Y')",
                        category, question, answer, sortOrder);
                    success++;
                } catch (Exception e) {
                    errors.add(rowError(r + 1, "DB 오류: " + e.getMessage()));
                }
            }
        } catch (Exception e) {
            log.error("[FAQ:upload] inst={}: {}", safe, e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", "엑셀 파일을 읽지 못했습니다: " + e.getMessage()));
        }

        Map<String, Object> result = new HashMap<>();
        result.put("ok", true);
        result.put("success", success);
        result.put("skipped", skipped);
        result.put("failed", errors.size());
        result.put("errors", errors);
        return ResponseEntity.ok(result);
    }

    private Map<String, Object> rowError(int rowNo, String message) {
        Map<String, Object> m = new HashMap<>();
        m.put("row", rowNo);
        m.put("message", message);
        return m;
    }

    // ─────────────────── helpers ───────────────────

    private String resolveInst(HttpSession session) {
        Object v = session.getAttribute("inst");
        return v instanceof String s ? s : null;
    }

    private String str(Object v) {
        return v == null ? "" : v.toString().trim();
    }

    private int toInt(Object v, int def) {
        if (v == null) return def;
        try { return ((Number) v).intValue(); }
        catch (Exception e) { return def; }
    }
}
