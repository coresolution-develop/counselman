package com.coresolution.csm.controller;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URLEncoder;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Comparator;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.BeanWrapperImpl;
import org.springframework.http.HttpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.core.io.InputStreamResource;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import com.coresolution.csm.config.InstDetails;
import com.coresolution.csm.serivce.CounselListService;
import com.coresolution.csm.serivce.CsmAuthService;
import com.coresolution.csm.serivce.CsmEmailService;
import com.coresolution.csm.serivce.ModuleFeatureService;
import com.coresolution.csm.serivce.CsmPasswordResetTokenService;
import com.coresolution.csm.serivce.ExternalSmsGatewayService;
import com.coresolution.csm.serivce.SmsService;
import com.coresolution.csm.util.AES128;
import com.coresolution.csm.vo.Card;
import com.coresolution.csm.vo.Category1;
import com.coresolution.csm.vo.Category2;
import com.coresolution.csm.vo.Category1WithSubcategoriesAndOptions;
import com.coresolution.csm.vo.Category2WithOptions;
import com.coresolution.csm.vo.Category3;
import com.coresolution.csm.vo.CounselData;
import com.coresolution.csm.vo.CounselDataEntry;
import com.coresolution.csm.vo.CounselLog;
import com.coresolution.csm.vo.CounselLogGuardian;
import com.coresolution.csm.vo.CounselReservation;
import com.coresolution.csm.vo.Counsel_phone;
import com.coresolution.csm.vo.Criteria;
import com.coresolution.csm.vo.Guardian;
import com.coresolution.csm.vo.Instdata;
import com.coresolution.csm.vo.OrderedItem;
import com.coresolution.csm.vo.Paging;
import com.coresolution.csm.vo.SmsTemplate;
import com.coresolution.csm.vo.Userdata;
import com.coresolution.csm.vo.UserdataCs;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;

@Slf4j
@Validated
@Controller
@RequestMapping("/")
public class PageController {
    @Autowired
    private CsmAuthService cs;

    @Value("${mediplat.platform.base-url:http://localhost:8082}")
    private String mediplatPlatformBaseUrl;
    @Autowired
    private CsmPasswordResetTokenService tokenService;
    @Autowired
    private CsmEmailService emailService;
    @Autowired
    private SmsService ss;
    @Autowired
    private ExternalSmsGatewayService externalSmsGatewayService;
    @Autowired
    private CounselListService counselListService;
    @Autowired
    private ModuleFeatureService moduleFeatureService;
    @Autowired
    private PlatformTransactionManager transactionManager;
    private TransactionTemplate transactionTemplate;

    @PostConstruct
    void initTransactionTemplate() {
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }
    @Value("${app.counsel.audio.base-dir:${user.home}/csm-audio}")
    private String counselAudioBaseDir;
    @Value("${app.counsel.file.base-dir:${user.home}/csm-counsel-files}")
    private String counselFileBaseDir;
    @Value("${ncp.clova.invoke-url:}")
    private String clovaInvokeUrl;
    @Value("${ncp.clova.secret-key:}")
    private String clovaSecretKey;
    @Value("${ncp.clova.grpc-url:}")
    private String clovaGrpcUrl;
    @Value("${openai.api.base-url:https://api.openai.com/v1}")
    private String openAiBaseUrl;
    @Value("${openai.api.key:}")
    private String openAiApiKey;
    @Value("${openai.api.model:gpt-4.1-mini}")
    private String openAiModel;
    @Value("${app.counsel.list.hidden-columns:cs_idx,cs_col_01_hash}")
    private String counselListHiddenColumnsRaw;
    private static final String DEFAULT_ADMISSION_PLEDGE_TEXT = "본인은 입원 연계 및 상담을 위해 제공한 정보가 병원 입원 진행에 활용되는 것에 동의합니다. "
            + "또한 상담 과정에서 안내받은 내용을 확인하였으며, 안내된 절차에 따라 성실히 협조할 것을 서약합니다.";
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder().build();
    @Autowired
    private AES128 aes;
    @Value("${login.aes-key}")
    private String aesKey;

    @RequestMapping("login")
    public String login() {
        return "redirect:" + resolveMediplatLoginRedirectUrl();
    }

    @GetMapping({ "findpwd", "/findpwd" })
    public String findpwd() {
        return "csm/login/Findpwd";
    }

    @PostMapping({ "findpwd/post", "/findpwd/post" })
    @ResponseBody
    public Map<String, Object> postFindpwd(
            @RequestParam("us_col_04") @NotBlank @Email @Size(max = 100) String usCol04,
            @RequestParam("us_col_02") @NotBlank @Size(max = 50) String usCol02,
            @RequestParam("us_col_12") @NotBlank @Size(max = 100) String usCol12,
            HttpServletRequest request) {
        Map<String, Object> response = new HashMap<>();
        try {
            boolean exists = cs.findUserByIdAndName(usCol02, usCol12, usCol04);
            if (!exists) {
                response.put("result", false);
                response.put("msg", "사용자 정보가 존재하지 않습니다.");
                return response;
            }

            Userdata user = cs.userInfoById(usCol04, usCol02);
            if (user == null) {
                response.put("result", false);
                response.put("msg", "사용자 정보를 불러올 수 없습니다.");
                return response;
            }
            if (user.getUs_col_11() == null || user.getUs_col_11().trim().isEmpty()) {
                response.put("result", false);
                response.put("msg", "이메일 정보가 없습니다.");
                return response;
            }

            String token = tokenService.generateToken(user.getUs_col_11(), usCol04, String.valueOf(user.getUs_col_01()));
            String resetLink = request.getScheme() + "://" + request.getServerName()
                    + ((request.getServerPort() == 80 || request.getServerPort() == 443) ? ""
                            : ":" + request.getServerPort())
                    + request.getContextPath()
                    + "/ResetPwd?us_col_01=" + user.getUs_col_01()
                    + "&inst=" + usCol04
                    + "&token=" + token;
            String instName = Optional.ofNullable(cs.coreInstFindByCode(usCol04))
                    .map(Instdata::getId_col_02)
                    .orElse("");
            emailService.sendPasswordResetLink(
                    user.getUs_col_11(),
                    resetLink,
                    String.valueOf(user.getUs_col_01()),
                    usCol04,
                    usCol02,
                    instName);

            response.put("result", true);
            response.put("msg", "등록된 이메일로 비밀번호 변경 링크를 전송하였습니다.");
            return response;
        } catch (Exception e) {
            log.error("[findpwd/post] fail inst={}, userId={}", usCol04, usCol02, e);
            response.put("result", false);
            response.put("msg", "비밀번호 변경 요청 중 오류가 발생했습니다.");
            return response;
        }
    }

    @GetMapping({ "ResetPwd", "/ResetPwd" })
    public String resetPwdPage(
            @RequestParam(value = "us_col_01", required = false) String usCol01,
            @RequestParam(value = "inst", required = false) String inst,
            @RequestParam(value = "token", required = false) String token,
            Model model) {
        CsmPasswordResetTokenService.ResetTokenContext tokenContext = tokenService.getTokenContext(token);
        if (token == null
                || token.isBlank()
                || tokenContext == null
                || !isSameValue(tokenContext.userId(), usCol01)
                || !isSameInstValue(tokenContext.inst(), inst)) {
            model.addAttribute("Msg", "유효하지 않거나 만료된 링크입니다.");
            model.addAttribute("redirect", true);
            model.addAttribute("us_col_01", usCol01);
            model.addAttribute("inst", inst);
            model.addAttribute("token", token);
            return "csm/login/ResetPwd";
        }
        model.addAttribute("us_col_01", usCol01);
        model.addAttribute("inst", inst);
        model.addAttribute("token", token);
        return "csm/login/ResetPwd";
    }

    @PostMapping({ "ResetPwd", "/ResetPwd" })
    @ResponseBody
    public ResponseEntity<?> resetPassword(@RequestBody Map<String, String> requestBody) {
        String usCol01 = requestBody.get("us_col_01");
        String usCol03 = requestBody.get("us_col_03");
        String inst = requestBody.get("inst");
        String token = requestBody.get("token");

        CsmPasswordResetTokenService.ResetTokenContext tokenContext = tokenService.getTokenContext(token);
        if (tokenContext == null) {
            return ResponseEntity.badRequest().body("유효하지 않거나 만료된 링크입니다.");
        }
        if (usCol01 == null || usCol01.isBlank() || usCol03 == null || usCol03.isBlank() || inst == null
                || inst.isBlank()) {
            return ResponseEntity.badRequest().body("비밀번호 변경에 실패했습니다.");
        }
        if (!isSameValue(tokenContext.userId(), usCol01) || !isSameInstValue(tokenContext.inst(), inst)) {
            log.warn("[ResetPwd] token scope mismatch tokenUser={}, reqUser={}, tokenInst={}, reqInst={}",
                    tokenContext.userId(), usCol01, tokenContext.inst(), inst);
            return ResponseEntity.badRequest().body("유효하지 않거나 만료된 링크입니다.");
        }

        try {
            String cryptogram = aes.encrypt(usCol03);
            int userIdx = Integer.parseInt(tokenContext.userId());
            String targetInst = tokenContext.inst();
            int updated = cs.updatePwd(targetInst, userIdx, cryptogram);
            if (updated > 0) {
                tokenService.invalidateToken(token);
                return ResponseEntity.ok("비밀번호 변경에 성공했습니다.");
            }
            return ResponseEntity.badRequest().body("비밀번호 변경에 실패했습니다.");
        } catch (Exception e) {
            log.error("[ResetPwd] fail us_col_01={}, inst={}", usCol01, inst, e);
            return ResponseEntity.badRequest().body("비밀번호 변경에 실패했습니다.");
        }
    }

    @GetMapping({ "logout", "/logout" })
    public String logout(HttpServletRequest request, HttpServletResponse response, HttpSession session) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        new SecurityContextLogoutHandler().logout(request, response, auth);
        SecurityContextHolder.clearContext();
        return "redirect:" + resolveMediplatRedirectUrl();
    }

    private String resolveMediplatRedirectUrl() {
        String normalized = StringUtils.hasText(mediplatPlatformBaseUrl)
                ? mediplatPlatformBaseUrl.trim()
                : "http://localhost:8082";
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.isBlank() ? "http://localhost:8082" : normalized;
    }

    private String resolveMediplatLoginRedirectUrl() {
        String baseUrl = resolveMediplatRedirectUrl();
        return baseUrl.endsWith("/login") ? baseUrl : baseUrl + "/login";
    }

    private boolean isSameValue(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return left.trim().equals(right.trim());
    }

    private boolean isSameInstValue(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return left.trim().equalsIgnoreCase(right.trim());
    }

    @GetMapping({ "admin", "/admin", "admin/", "/admin/" })
    public String adminPage(Model model, HttpSession session) {
        String inst = ensureInst(session);
        if (inst == null) {
            return "redirect:/login";
        }
        Userdata userinfo = ensureUserInfo(session, inst);

        Userdata query = new Userdata();
        query.setUs_col_04(inst);
        List<Userdata> userList = cs.userSelect(query);

        model.addAttribute("info", userinfo);
        model.addAttribute("user", userList != null ? userList : Collections.emptyList());
        model.addAttribute("count", userList != null ? userList.size() : 0);
        model.addAttribute("endVar", "on");
        model.addAttribute("st", "");
        model.addAttribute("kw", "");
        populateModuleFeatureModel(model, inst);
        return "csm/admin/admin";
    }

    @GetMapping({ "newuserPopup", "/newuserPopup" })
    public String newUserPopup(Model model, HttpSession session) {
        String inst = ensureInst(session);
        if (inst == null) {
            return "redirect:/login";
        }
        Userdata userinfo = ensureUserInfo(session, inst);
        model.addAttribute("info", userinfo);
        model.addAttribute("instName", userinfo != null ? userinfo.getUs_col_05() : "");
        return "csm/admin/newUserPopup";
    }

    @GetMapping({ "modifyuserPopup", "/modifyuserPopup" })
    public String modifyUserPopup(
            Model model,
            HttpSession session,
            @RequestParam(value = "us_col_01", required = false) Integer us_col_01,
            @RequestParam(value = "instCode", required = false) String instCode) {
        String inst = ensureInst(session);
        if (inst == null) {
            return "redirect:/login";
        }
        if (us_col_01 == null || us_col_01 <= 0) {
            return "redirect:/admin";
        }
        String targetInst = (instCode == null || instCode.isBlank()) ? inst : instCode;
        Userdata user = cs.userInfo(us_col_01, targetInst);
        model.addAttribute("user", user);
        model.addAttribute("instName", user != null ? user.getUs_col_05() : "");
        return "csm/admin/modifyUserPopup";
    }

    @PostMapping("newuser/post")
    @ResponseBody
    public Map<String, Object> newUserPost(HttpSession session, Userdata ud) {
        String inst = ensureInst(session);
        if (inst == null) {
            return Map.of("result", "0", "msg", "세션 만료");
        }
        ud.setUs_col_04(inst);
        if (ud.getUs_col_07() == null || ud.getUs_col_07().isBlank()) {
            ud.setUs_col_07("y");
        }
        if (ud.getUs_col_09() == 0) {
            ud.setUs_col_09(1);
        }
        int result = cs.userInsert(ud);
        return Map.of("result", result > 0 ? "1" : "0");
    }

    @PostMapping("modifyuserPopup/post")
    @ResponseBody
    public Map<String, Object> modifyUserPost(HttpSession session, Userdata ud) {
        String inst = ensureInst(session);
        if (inst == null) {
            return Map.of("result", "0", "msg", "세션 만료");
        }
        ud.setUs_col_04(inst);
        int result = cs.userUpdate(ud);
        return Map.of("result", result > 0 ? "1" : "0");
    }

    @PostMapping("user/delete")
    @ResponseBody
    public Map<String, Object> userDelete(HttpSession session, Userdata ud) {
        String inst = ensureInst(session);
        if (inst == null) {
            return Map.of("result", "0", "msg", "세션 만료");
        }
        if (ud.getUs_col_04() == null || ud.getUs_col_04().isBlank()) {
            ud.setUs_col_04(inst);
        }
        int result = cs.userDelete(ud);
        return Map.of("result", result > 0 ? "1" : "0");
    }

    @GetMapping({ "core/admin", "/core/admin" })
    public String coreAdminPage(Model model, HttpSession session) {
        String inst = ensureInst(session);
        if (!isCoreInst(inst)) {
            return "redirect:/login";
        }
        Userdata info = ensureUserInfo(session, inst);
        List<Instdata> list = Optional.ofNullable(cs.coreInstSelect())
                .orElse(Collections.emptyList())
                .stream()
                .filter(Objects::nonNull)
                .toList();
        log.info("coreAdminPage inst list size={}", list.size());
        model.addAttribute("info", info);
        model.addAttribute("list", list);
        model.addAttribute("instCount", list.size());
        return "csm/core/admin/admin";
    }

    @GetMapping({ "core/user", "/core/user" })
    public String coreUserPage(Model model, HttpSession session) {
        String inst = ensureInst(session);
        if (!isCoreInst(inst)) {
            return "redirect:/login";
        }
        return "redirect:/core/admin";
    }

    @GetMapping({ "core/setting", "/core/setting" })
    public String coreSettingPage(Model model, HttpSession session) {
        String inst = ensureInst(session);
        if (!isCoreInst(inst)) {
            return "redirect:/login";
        }
        Userdata info = ensureUserInfo(session, inst);
        List<Map<String, Object>> template = Optional.ofNullable(cs.coreTemplateSelect())
                .orElse(Collections.emptyList())
                .stream()
                .filter(Objects::nonNull)
                .toList();
        List<Instdata> institutions = Optional.ofNullable(cs.coreInstSelect())
                .orElse(Collections.emptyList())
                .stream()
                .filter(Objects::nonNull)
                .toList();
        model.addAttribute("info", info);
        model.addAttribute("template", template);
        model.addAttribute("instCount", template.size());
        model.addAttribute("moduleFeatures", moduleFeatureService.getFeatureDefinitions());
        model.addAttribute("moduleFeatureRows", moduleFeatureService.getFeatureRows(institutions));
        return "csm/core/admin/setting";
    }

    @GetMapping({ "core/categorysetting", "/core/categorysetting" })
    public String coreCategorySettingPage(Model model, HttpSession session) {
        String inst = ensureInst(session);
        if (!isCoreInst(inst)) {
            return "redirect:/login";
        }
        Userdata info = ensureUserInfo(session, inst);
        List<Map<String, Object>> template = Optional.ofNullable(cs.coreTemplateSelect())
                .orElse(Collections.emptyList())
                .stream()
                .filter(Objects::nonNull)
                .toList();
        model.addAttribute("info", info);
        model.addAttribute("template", template);
        model.addAttribute("instCount", template.size());
        return "csm/core/admin/categorysetting";
    }

    @GetMapping({ "core/smssetting", "/core/smssetting" })
    public String coreSmsSettingPage(Model model, HttpSession session) {
        String inst = ensureInst(session);
        if (!isCoreInst(inst)) {
            return "redirect:/login";
        }
        Userdata info = ensureUserInfo(session, inst);
        List<Instdata> list = Optional.ofNullable(cs.coreInstSelect())
                .orElse(Collections.emptyList())
                .stream()
                .filter(Objects::nonNull)
                .toList();
        model.addAttribute("info", info);
        model.addAttribute("list", list);
        model.addAttribute("instCount",
                list.stream().filter(i -> i != null && !"core".equalsIgnoreCase(i.getId_col_03())).count());
        return "csm/core/admin/smssetting";
    }

    @GetMapping({ "core/notice", "/core/notice" })
    public String coreNoticePage(
            @RequestParam(value = "status", defaultValue = "ALL") String status,
            @RequestParam(value = "keyword", defaultValue = "") String keyword,
            Model model,
            HttpSession session) {
        String inst = ensureInst(session);
        if (!isCoreInst(inst)) {
            return "redirect:/login";
        }
        Userdata info = ensureUserInfo(session, inst);
        String selectedStatus = normalizeCoreNoticeStatusParam(status, true);
        String searchKeyword = safeString(keyword).trim();

        List<Map<String, Object>> notices = cs.coreNoticeList(selectedStatus, searchKeyword, 500);
        List<Instdata> institutions = Optional.ofNullable(cs.coreInstSelect())
                .orElse(Collections.emptyList())
                .stream()
                .filter(Objects::nonNull)
                .filter(item -> item.getId_col_03() != null && !"core".equalsIgnoreCase(item.getId_col_03()))
                .toList();

        model.addAttribute("info", info);
        model.addAttribute("selectedStatus", selectedStatus);
        model.addAttribute("keyword", searchKeyword);
        model.addAttribute("notices", notices);
        model.addAttribute("institutions", institutions);
        model.addAttribute("noticeCount", notices.size());
        return "csm/core/admin/notice";
    }

    @GetMapping({ "core/notice/detail/{noticeId}", "/core/notice/detail/{noticeId}" })
    @ResponseBody
    public ResponseEntity<?> coreNoticeDetail(
            @PathVariable("noticeId") long noticeId,
            HttpSession session) {
        String inst = ensureInst(session);
        if (!isCoreInst(inst)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "권한 없음"));
        }
        Map<String, Object> notice = cs.coreNoticeById(noticeId);
        if (notice == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", "공지 정보를 찾을 수 없습니다."));
        }
        return ResponseEntity.ok(Map.of("result", "1", "notice", notice));
    }

    @PostMapping({ "core/notice/save", "/core/notice/save" })
    @ResponseBody
    public ResponseEntity<?> coreNoticeSave(
            HttpSession session,
            @RequestBody Map<String, Object> body) {
        String inst = ensureInst(session);
        if (!isCoreInst(inst)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "권한 없음"));
        }
        try {
            Map<String, Object> payload = new HashMap<>();
            if (body != null) {
                payload.putAll(body);
            }

            if (safeString(readAsString(payload, "createdBy")).isBlank()) {
                payload.put("createdBy", resolveNoticeActor(inst, session));
            }

            List<String> targetInstCodes = toStringList(payload.get("targetInstCodes"));
            if (targetInstCodes.isEmpty()) {
                targetInstCodes = toStringList(payload.get("target_codes_csv"));
            }
            long noticeId = cs.coreNoticeSave(payload, targetInstCodes);
            return ResponseEntity.ok(Map.of("result", "1", "id", noticeId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("result", "0", "message", e.getMessage()));
        } catch (Exception e) {
            log.error("[core/notice/save] fail", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("result", "0", "message", "공지 저장 중 오류가 발생했습니다."));
        }
    }

    @PostMapping({ "core/notice/status", "/core/notice/status" })
    @ResponseBody
    public ResponseEntity<?> coreNoticeStatusUpdate(
            HttpSession session,
            @RequestBody Map<String, Object> body) {
        String inst = ensureInst(session);
        if (!isCoreInst(inst)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "권한 없음"));
        }
        long noticeId = parseLongSafely(body == null ? null : body.get("id"), 0L);
        String status = readAsString(body == null ? Collections.emptyMap() : body, "status").trim();
        if (noticeId <= 0 || status.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("result", "0", "message", "필수값이 누락되었습니다."));
        }
        int updated = cs.coreNoticeUpdateStatus(noticeId, status);
        if (updated <= 0) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("result", "0", "message", "공지 상태를 변경할 수 없습니다."));
        }
        return ResponseEntity.ok(Map.of("result", "1", "updated", updated));
    }

    @GetMapping({ "core/newinstPopup", "/core/newinstPopup" })
    public String coreNewInstPopup(Model model, HttpSession session) {
        String inst = ensureInst(session);
        if (!isCoreInst(inst)) {
            return "redirect:/login";
        }
        model.addAttribute("info", ensureUserInfo(session, inst));
        return "csm/core/admin/newinstPopup";
    }

    @GetMapping({ "core/newuserPopup", "/core/newuserPopup" })
    public String coreNewUserPopup(Model model, HttpSession session) {
        String inst = ensureInst(session);
        if (!isCoreInst(inst)) {
            return "redirect:/login";
        }
        return "redirect:/core/admin";
    }

    @GetMapping({ "core/modifyinstPopup", "/core/modifyinstPopup" })
    public String coreModifyInstPopup(
            Model model,
            HttpSession session,
            @RequestParam("code") String instCode) {
        String inst = ensureInst(session);
        if (!isCoreInst(inst)) {
            return "redirect:/login";
        }
        model.addAttribute("info", ensureUserInfo(session, inst));
        model.addAttribute("instdata", cs.coreInstFindByCode(instCode));
        return "csm/core/admin/modifyinstPopup";
    }

    @PostMapping("core/inst/post")
    @ResponseBody
    public Map<String, Object> coreInstInsert(HttpSession session, Instdata id) {
        String inst = ensureInst(session);
        if (!isCoreInst(inst)) {
            return Map.of("result", "0", "msg", "권한 없음");
        }
        int result = cs.coreInstInsert(id);
        if (result > 0 && id != null && id.getId_col_03() != null && !id.getId_col_03().isBlank()) {
            try {
                cs.createCoreInstSchemaTables(id.getId_col_03());
            } catch (Exception e) {
                log.error("coreInstInsert schema creation failed. inst={}", id.getId_col_03(), e);
                return Map.of("result", "0", "msg", "기관 생성 후 테이블 생성에 실패했습니다.");
            }
        }
        return Map.of("result", result > 0 ? "1" : "0");
    }

    @PostMapping("core/inst/update")
    @ResponseBody
    public Map<String, Object> coreInstUpdate(HttpSession session, Instdata id) {
        String inst = ensureInst(session);
        if (!isCoreInst(inst)) {
            return Map.of("result", "0", "msg", "권한 없음");
        }
        int result = cs.coreInstUpdate(id);
        return Map.of("result", result > 0 ? "1" : "0");
    }

    @PostMapping("core/inst/delete")
    @ResponseBody
    public Map<String, Object> coreInstDelete(HttpSession session, @RequestParam("id_col_01") int idCol01) {
        String inst = ensureInst(session);
        if (!isCoreInst(inst)) {
            return Map.of("result", "0", "msg", "권한 없음");
        }
        int result = cs.coreInstDelete(idCol01);
        return Map.of("result", result > 0 ? "1" : "0");
    }

    @PostMapping("core/inst/schema/status")
    @ResponseBody
    public Map<String, Object> coreInstSchemaStatus(
            HttpSession session,
            @RequestParam("instCode") @NotBlank @jakarta.validation.constraints.Pattern(regexp = "^[A-Za-z0-9_]{2,20}$", message = "기관코드 형식이 올바르지 않습니다.") String instCode) {
        String inst = ensureInst(session);
        if (!isCoreInst(inst)) {
            return Map.of("result", "0", "msg", "권한 없음");
        }
        try {
            Map<String, Object> status = cs.inspectCoreInstSchema(instCode);
            Map<String, Object> response = new HashMap<>();
            response.put("result", "1");
            response.put("status", status);
            return response;
        } catch (IllegalArgumentException e) {
            return Map.of("result", "0", "msg", "기관코드 형식이 올바르지 않습니다.");
        } catch (Exception e) {
            log.error("[core/inst/schema/status] fail instCode={}", instCode, e);
            return Map.of("result", "0", "msg", "점검 중 오류가 발생했습니다.");
        }
    }

    @PostMapping("core/inst/schema/repair")
    @ResponseBody
    public Map<String, Object> coreInstSchemaRepair(
            HttpSession session,
            @RequestParam("instCode") @NotBlank @jakarta.validation.constraints.Pattern(regexp = "^[A-Za-z0-9_]{2,20}$", message = "기관코드 형식이 올바르지 않습니다.") String instCode) {
        String inst = ensureInst(session);
        if (!isCoreInst(inst)) {
            return Map.of("result", "0", "msg", "권한 없음");
        }
        try {
            Map<String, Object> repairResult = cs.repairCoreInstSchema(instCode);
            Map<String, Object> response = new HashMap<>();
            response.put("result", "1");
            response.put("repair", repairResult);
            return response;
        } catch (IllegalArgumentException e) {
            return Map.of("result", "0", "msg", "기관코드 형식이 올바르지 않습니다.");
        } catch (Exception e) {
            log.error("[core/inst/schema/repair] fail instCode={}", instCode, e);
            return Map.of("result", "0", "msg", "복구 중 오류가 발생했습니다.");
        }
    }

    @PostMapping("core/modifyinst/post/{id_col_01}")
    @ResponseBody
    public Map<String, Object> coreModifyInstPost(
            HttpSession session,
            @PathVariable("id_col_01") int idCol01,
            Instdata id) {
        String inst = ensureInst(session);
        if (!isCoreInst(inst)) {
            return Map.of("result", "0", "msg", "권한 없음");
        }
        id.setId_col_01(idCol01);
        int result = cs.coreInstUpdate(id);
        return Map.of("result", result > 0 ? "1" : "0");
    }

    @PostMapping("core/instfind")
    @ResponseBody
    public Map<String, Object> coreInstNumberFind(HttpSession session, @RequestParam("id_col_02") String instName) {
        String inst = ensureInst(session);
        if (!isCoreInst(inst)) {
            return Map.of("result", "0", "msg", "권한 없음");
        }
        String instNumber = cs.coreInstNumberFind(instName);
        return Map.of("instNumber", instNumber == null ? "" : instNumber);
    }

    @PostMapping("core/newuser/post")
    @ResponseBody
    public Map<String, Object> coreNewUserPost(HttpSession session, Userdata ud,
            @RequestParam Map<String, String> raw) {
        String inst = ensureInst(session);
        if (!isCoreInst(inst)) {
            return Map.of("result", false, "msg", "권한 없음");
        }
        if (ud.getUs_col_04() == null || ud.getUs_col_04().isBlank()
                || ud.getUs_col_02() == null || ud.getUs_col_02().isBlank()) {
            return Map.of("result", false, "msg", "필수값 누락");
        }
        Userdata exists = cs.loadUserInfo(ud.getUs_col_04(), ud.getUs_col_02());
        if (exists != null) {
            return Map.of("result", false, "msg", "이미 등록된 아이디입니다.");
        }

        if (ud.getUs_col_03() != null && !ud.getUs_col_03().isBlank()) {
            ud.setUs_col_03(aes.encrypt(ud.getUs_col_03()));
        } else {
            ud.setUs_col_03(null);
        }
        if (ud.getUs_col_07() == null || ud.getUs_col_07().isBlank()) {
            ud.setUs_col_07("y");
        }
        if (ud.getUs_col_09() == 0) {
            ud.setUs_col_09(1);
        }
        int ins = cs.userInsert(ud);
        if (ins <= 0) {
            return Map.of("result", false, "msg", "사용자 생성 실패");
        }

        UserdataCs us = new UserdataCs();
        us.setUs_col_01(ud.getUs_col_02());
        us.setUs_col_02(ud.getUs_col_04());
        us.setUs_col_03(ud.getUs_col_05());
        us.setUs_col_04(ud.getUs_col_06());
        us.setUs_col_05(ud.getUs_col_10());
        us.setUs_col_06(ud.getUs_col_11());
        us.setUs_col_07(raw.getOrDefault("id_col_07", ""));
        cs.newuserInsertCs(us);

        return Map.of("result", true);
    }

    @PostMapping("core/user/update")
    @ResponseBody
    public Map<String, Object> coreUserUpdate(HttpSession session, Userdata ud) {
        String inst = ensureInst(session);
        if (!isCoreInst(inst)) {
            return Map.of("result", "0", "msg", "권한 없음");
        }
        if (ud.getUs_col_04() == null || ud.getUs_col_04().isBlank()) {
            return Map.of("result", "0", "msg", "기관코드 누락");
        }
        if (ud.getUs_col_03() != null && !ud.getUs_col_03().isBlank()) {
            ud.setUs_col_03(aes.encrypt(ud.getUs_col_03()));
        }
        int result = cs.userUpdate(ud);
        return Map.of("result", result > 0 ? "1" : "0");
    }

    @PostMapping("core/user/delete")
    @ResponseBody
    public Map<String, Object> coreUserDelete(
            HttpSession session,
            @RequestParam("id") int id,
            @RequestParam("us_col_02") String targetInst,
            @RequestParam("us_col_01") String targetUserId) {
        String inst = ensureInst(session);
        if (!isCoreInst(inst)) {
            return Map.of("result", "0", "msg", "권한 없음");
        }
        cs.userDeleteCs(id);
        cs.userDeleteByUsername(targetInst, targetUserId);
        return Map.of("result", "1");
    }

    @PostMapping("core/setting/templateInsert")
    @ResponseBody
    public Map<String, Object> coreTemplateInsert(HttpSession session, @RequestBody Map<String, Object> body) {
        String inst = ensureInst(session);
        if (!isCoreInst(inst)) {
            return Map.of("result", "0", "message", "권한 없음");
        }
        String name = Objects.toString(body.get("name"), "").trim();
        String description = Objects.toString(body.get("description"), "").trim();
        if (name.isBlank()) {
            return Map.of("result", "0", "message", "템플릿명을 입력해주세요.");
        }
        int result = cs.coreTemplateInsert(name, description);
        return Map.of("result", result > 0 ? "1" : "0");
    }

    @PostMapping("core/setting/templateModify")
    @ResponseBody
    public Map<String, Object> coreTemplateModify(HttpSession session, @RequestBody Map<String, Object> body) {
        String inst = ensureInst(session);
        if (!isCoreInst(inst)) {
            return Map.of("result", "0", "message", "권한 없음");
        }
        int idx = Integer.parseInt(Objects.toString(body.get("idx"), "0"));
        String name = Objects.toString(body.get("name"), "").trim();
        String description = Objects.toString(body.get("description"), "").trim();
        if (idx <= 0 || name.isBlank()) {
            return Map.of("result", "0", "message", "필수값 누락");
        }
        int result = cs.coreTemplateModify(idx, name, description);
        return Map.of("result", result > 0 ? "1" : "0");
    }

    @PostMapping("core/setting/templateDelete")
    @ResponseBody
    public Map<String, Object> coreTemplateDelete(HttpSession session, @RequestBody Map<String, Object> body) {
        String inst = ensureInst(session);
        if (!isCoreInst(inst)) {
            return Map.of("result", "0", "message", "권한 없음");
        }
        int idx = Integer.parseInt(Objects.toString(body.get("idx"), "0"));
        if (idx <= 0) {
            return Map.of("result", "0", "message", "템플릿 idx 오류");
        }
        int result = cs.coreTemplateDelete(idx);
        return Map.of("result", result > 0 ? "1" : "0");
    }

    @PostMapping("core/setting/moduleFeatureUpdate")
    @ResponseBody
    public Map<String, Object> coreModuleFeatureUpdate(
            HttpSession session,
            @RequestBody Map<String, Object> body) {
        String inst = ensureInst(session);
        if (!isCoreInst(inst)) {
            return Map.of("result", "0", "message", "권한 없음");
        }
        String instCode = Objects.toString(body.get("instCode"), "").trim();
        String featureCode = Objects.toString(body.get("featureCode"), "").trim();
        boolean enabled = Boolean.parseBoolean(Objects.toString(body.get("enabled"), "false"));
        Userdata info = ensureUserInfo(session, inst);
        try {
            moduleFeatureService.updateFeature(
                    instCode,
                    featureCode,
                    enabled,
                    info == null ? "" : safeString(info.getUs_col_02()).trim());
            return Map.of("result", "1");
        } catch (IllegalArgumentException e) {
            return Map.of("result", "0", "message", e.getMessage());
        } catch (Exception e) {
            log.error("[core/setting/moduleFeatureUpdate] fail instCode={}, featureCode={}", instCode, featureCode, e);
            return Map.of("result", "0", "message", "기능 상태 변경 중 오류가 발생했습니다.");
        }
    }

    @PostMapping("core/smssetting/priceInsert")
    @ResponseBody
    public Map<String, Object> coreSmsPriceInsert(HttpSession session, @RequestBody Instdata id) {
        String inst = ensureInst(session);
        if (!isCoreInst(inst)) {
            return Map.of("result", "0", "message", "권한 없음");
        }
        String id03 = id.getId_col_03();
        String sms = id.getSms_price();
        String lms = id.getLms_price();
        String mms = id.getMms_price();

        int result;
        if ("all".equalsIgnoreCase(id03)) {
            result = cs.corePriceInsertAll(sms, lms, mms);
        } else {
            result = cs.corePriceInsert(id03, sms, lms, mms);
        }
        return Map.of("result", result > 0 ? "1" : "0");
    }

    @GetMapping("core/setting/maincategory/{templateIdx}")
    @ResponseBody
    public ResponseEntity<?> coreMainCategory(HttpSession session, @PathVariable int templateIdx) {
        String inst = ensureInst(session);
        if (!isCoreInst(inst)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("권한 없음");
        }
        return ResponseEntity.ok(cs.coreTemplateMainSelect(templateIdx));
    }

    @GetMapping("core/setting/subcategory/{mainCategoryId}")
    @ResponseBody
    public ResponseEntity<?> coreSubCategory(HttpSession session, @PathVariable int mainCategoryId) {
        String inst = ensureInst(session);
        if (!isCoreInst(inst)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("권한 없음");
        }
        return ResponseEntity.ok(cs.coreTemplateSubSelect(mainCategoryId));
    }

    @GetMapping("core/setting/options/{subcategoryId}")
    @ResponseBody
    public ResponseEntity<?> coreOptions(HttpSession session, @PathVariable int subcategoryId) {
        String inst = ensureInst(session);
        if (!isCoreInst(inst)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("권한 없음");
        }
        return ResponseEntity.ok(cs.coreTemplateOptionSelect(subcategoryId));
    }

    @PostMapping("core/setting/category1")
    @ResponseBody
    public ResponseEntity<?> coreCategory1Insert(HttpSession session, @RequestBody Map<String, Object> body) {
        String inst = ensureInst(session);
        if (!isCoreInst(inst)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "권한 없음"));
        }
        int templateIdx = Integer.parseInt(Objects.toString(body.get("template_idx"), "0"));
        String name = Objects.toString(body.get("main_col_01"), "").trim();
        if (templateIdx <= 0 || name.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "필수값 누락"));
        }
        int turn = cs.coreTemplateMainSelect(templateIdx).size() + 1;
        cs.coreTemplateMainInsert(templateIdx, name, turn);
        return ResponseEntity.ok(Map.of("message", "ok"));
    }

    @PostMapping("core/setting/category2")
    @ResponseBody
    public ResponseEntity<?> coreCategory2Insert(HttpSession session, @RequestBody Map<String, Object> body) {
        String inst = ensureInst(session);
        if (!isCoreInst(inst)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "권한 없음"));
        }
        int mainIdx = Integer
                .parseInt(Objects.toString(body.get("main_idx"), Objects.toString(body.get("hiddenid"), "0")));
        String name = Objects.toString(body.get("sub_col_01"), Objects.toString(body.get("cc_col_02"), "")).trim();
        int chk = parseIntSafely(body.get("sub_col_02"), parseBool01(body.get("checkbox")));
        int rad = parseIntSafely(body.get("sub_col_03"), parseBool01(body.get("radio")));
        int txt = parseIntSafely(body.get("sub_col_04"), parseBool01(body.get("textbox")));
        int sel = parseIntSafely(body.get("sub_col_05"), parseBool01(body.get("selectbox")));
        if (mainIdx <= 0 || name.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "필수값 누락"));
        }
        int turn = cs.coreTemplateSubSelect(mainIdx).size() + 1;
        cs.coreTemplateSubInsert(mainIdx, name, chk, rad, txt, sel, turn);
        return ResponseEntity.ok(Map.of("message", "ok"));
    }

    @PostMapping("core/setting/options/insert")
    @ResponseBody
    public ResponseEntity<?> coreOptionsInsert(HttpSession session, @RequestBody Map<String, Object> body) {
        String inst = ensureInst(session);
        if (!isCoreInst(inst)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "권한 없음"));
        }
        int subIdx = Integer
                .parseInt(Objects.toString(body.get("subCategoryId"), Objects.toString(body.get("sub_idx"), "0")));
        Object rawOptions = body.get("options");
        if (subIdx <= 0 || !(rawOptions instanceof List<?> list) || list.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "필수값 누락"));
        }
        int turnBase = cs.coreTemplateOptionSelect(subIdx).size();
        int i = 1;
        for (Object o : list) {
            String option = Objects.toString(o, "").trim();
            if (option.isBlank())
                continue;
            cs.coreTemplateOptionInsert(subIdx, option, turnBase + i);
            i++;
        }
        return ResponseEntity.ok(Map.of("message", "ok"));
    }

    @PostMapping("core/setting/addOptions")
    @ResponseBody
    public ResponseEntity<?> coreAddOptionsAlias(HttpSession session, @RequestBody Map<String, Object> body) {
        return coreOptionsInsert(session, body);
    }

    @PostMapping("core/setting/{type}/delete")
    @ResponseBody
    public ResponseEntity<?> coreCategoryDelete(HttpSession session, @PathVariable String type,
            @RequestBody Map<String, Object> body) {
        String inst = ensureInst(session);
        if (!isCoreInst(inst)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "권한 없음"));
        }
        int idx = Integer.parseInt(Objects.toString(body.get("idx"), "0"));
        if (idx <= 0) {
            return ResponseEntity.badRequest().body(Map.of("message", "idx 오류"));
        }
        int result = 0;
        if ("main".equals(type) || "parent".equals(type)) {
            result = cs.coreTemplateMainDeleteCascade(idx);
        } else if ("sub".equals(type) || "child".equals(type)) {
            result = cs.coreTemplateSubDeleteCascade(idx);
        } else if ("option".equals(type) || "select".equals(type)) {
            result = cs.coreTemplateOptionDelete(idx);
        } else {
            return ResponseEntity.badRequest().body(Map.of("message", "잘못된 type"));
        }
        return ResponseEntity.ok(Map.of("result", result > 0 ? "1" : "0"));
    }

    @PostMapping("core/setting/modifyCategory")
    @ResponseBody
    public ResponseEntity<?> coreModifyCategory(HttpSession session, @RequestBody Map<String, Object> body) {
        String inst = ensureInst(session);
        if (!isCoreInst(inst)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "권한 없음"));
        }
        String type = Objects.toString(body.get("type"), "");
        int id = Integer.parseInt(Objects.toString(body.get("id"), "0"));
        String inputValue = Objects.toString(body.get("inputValue"), "").trim();
        if (id <= 0 || inputValue.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "필수값 누락"));
        }
        int result = 0;
        if ("main".equals(type) || "parent".equals(type)) {
            result = cs.coreTemplateMainUpdate(id, inputValue);
        } else if ("sub".equals(type) || "child".equals(type)) {
            Integer chk = parseNullableBool01(body.get("sub_col_02"));
            Integer rad = parseNullableBool01(body.get("sub_col_03"));
            Integer txt = parseNullableBool01(body.get("sub_col_04"));
            Integer sel = parseNullableBool01(body.get("sub_col_05"));
            result = cs.coreTemplateSubUpdateWithFlags(id, inputValue, chk, rad, txt, sel);
        } else if ("option".equals(type) || "select".equals(type)) {
            result = cs.coreTemplateOptionUpdate(id, inputValue);
        }
        return ResponseEntity.ok(Map.of("result", result > 0 ? "1" : "0"));
    }

    @PostMapping("core/setting/saveCategoryOrder")
    @ResponseBody
    public ResponseEntity<?> coreSaveCategoryOrder(HttpSession session, @RequestBody List<Map<String, Object>> rows) {
        String inst = ensureInst(session);
        if (!isCoreInst(inst)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "권한 없음"));
        }
        for (Map<String, Object> r : rows) {
            if (r == null)
                continue;
            String type = Objects.toString(r.get("type"), "");
            int id = Integer.parseInt(Objects.toString(r.get("id"), Objects.toString(r.get("idx"), "0")));
            int turn = Integer.parseInt(Objects.toString(r.get("order"), Objects.toString(r.get("turn"), "0")));
            if (id <= 0 || turn < 0)
                continue;
            if ("main".equals(type) || "parent".equals(type)) {
                cs.coreTemplateMainTurnUpdate(id, turn);
            } else if ("sub".equals(type) || "child".equals(type)) {
                cs.coreTemplateSubTurnUpdate(id, turn);
            } else if ("option".equals(type) || "select".equals(type)) {
                cs.coreTemplateOptionTurnUpdate(id, turn);
            }
        }
        return ResponseEntity.ok(Map.of("result", "1"));
    }

    @GetMapping({ "getSubcategories", "/getSubcategories" })
    @ResponseBody
    public ResponseEntity<?> getSubcategories(HttpSession session, @RequestParam("categoryId") int categoryId) {
        String inst = ensureInst(session);
        if (inst == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("세션이 만료되었습니다.");
        }
        return ResponseEntity.ok(cs.getSubcategories(inst, categoryId));
    }

    @GetMapping({ "getOptions", "/getOptions" })
    @ResponseBody
    public ResponseEntity<?> getOptions(HttpSession session, @RequestParam("categoryId") int categoryId) {
        String inst = ensureInst(session);
        if (inst == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("세션이 만료되었습니다.");
        }
        return ResponseEntity.ok(cs.getOptions(inst, categoryId));
    }

    @GetMapping({ "getMajorCategories", "/getMajorCategories" })
    @ResponseBody
    public ResponseEntity<?> getMajorCategories(HttpSession session) {
        String inst = ensureInst(session);
        if (inst == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("세션이 만료되었습니다.");
        }
        return ResponseEntity.ok(cs.selectCategory1(inst));
    }

    @PostMapping({ "setting/category1", "/setting/category1" })
    @ResponseBody
    public ResponseEntity<?> settingCategory1Insert(HttpSession session, @RequestBody Map<String, Object> body) {
        String inst = ensureInst(session);
        if (inst == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "세션이 만료되었습니다."));
        }

        String name = readAsString(body, "cc_col_02", "main_col_01", "name").trim();
        if (name.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "상담항목명을 입력해주세요."));
        }

        var row = cs.insertCategory1(inst, name);
        return ResponseEntity.ok(Map.of("message", "ok", "data", row));
    }

    @PostMapping({ "setting/category2", "/setting/category2" })
    @ResponseBody
    public ResponseEntity<?> settingCategory2Insert(HttpSession session, @RequestBody Map<String, Object> body) {
        String inst = ensureInst(session);
        if (inst == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "세션이 만료되었습니다."));
        }

        int mainId = parseIntSafely(body.get("hiddenid"), parseIntSafely(body.get("main_idx"),
                parseIntSafely(body.get("cc_col_03"), 0)));
        String name = readAsString(body, "cc_col_02", "sub_col_01", "name").trim();
        int chk = parseIntSafely(body.get("sub_col_02"), parseBool01(body.get("checkbox")));
        int rad = parseIntSafely(body.get("sub_col_03"), parseBool01(body.get("radio")));
        int txt = parseIntSafely(body.get("sub_col_04"), parseBool01(body.get("textbox")));
        int sel = parseIntSafely(body.get("sub_col_05"), parseBool01(body.get("selectbox")));

        if (mainId <= 0 || name.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "필수값 누락"));
        }

        var row = cs.insertCategory2(inst, mainId, name, chk, rad, txt, sel);
        List<String> options = toStringList(body.get("options"));
        if (sel == 1 && !options.isEmpty()) {
            cs.insertCategory3(inst, row.getCc_col_01(), row.getCc_col_02(), options);
        }
        return ResponseEntity.ok(Map.of("message", "ok", "data", row));
    }

    @PostMapping({ "setting/category3", "/setting/category3" })
    @ResponseBody
    public ResponseEntity<?> settingCategory3Insert(HttpSession session, @RequestBody Map<String, Object> body) {
        String inst = ensureInst(session);
        if (inst == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "세션이 만료되었습니다."));
        }

        int subId = parseIntSafely(body.get("cc_col_04"),
                parseIntSafely(body.get("subCategoryId"), parseIntSafely(body.get("sub_idx"), 0)));
        String label = readAsString(body, "cc_col_02", "name").trim();
        List<String> options = toStringList(body.get("cc_col_03"));
        if (subId <= 0 || (label.isBlank() && options.isEmpty())) {
            return ResponseEntity.badRequest().body(Map.of("message", "필수값 누락"));
        }

        cs.insertCategory3(inst, subId, label, options);
        return ResponseEntity.ok(Map.of("message", "ok", "data", cs.getOptions(inst, subId)));
    }

    @PostMapping({ "setting/categoryDelete", "/setting/categoryDelete", "csm/setting/categoryDelete",
            "/csm/setting/categoryDelete" })
    @ResponseBody
    public ResponseEntity<?> settingCategoryDelete(HttpSession session, @RequestBody Map<String, Object> body) {
        String inst = ensureInst(session);
        if (inst == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "세션이 만료되었습니다."));
        }

        String type = readAsString(body, "type");
        int id = parseIntSafely(body.get("id"), parseIntSafely(body.get("idx"), 0));
        if (id <= 0 || type.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "필수값 누락"));
        }

        int result = cs.deleteCategory(inst, type, id);
        return ResponseEntity.ok(Map.of("result", result > 0 ? "1" : "0"));
    }

    @PostMapping({ "setting/modifyCategory", "/setting/modifyCategory", "setting/categoryModify",
            "/setting/categoryModify", "csm/setting/categoryModify", "/csm/setting/categoryModify" })
    @ResponseBody
    public ResponseEntity<?> settingModifyCategory(HttpSession session, @RequestBody Map<String, Object> body) {
        String inst = ensureInst(session);
        if (inst == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "세션이 만료되었습니다."));
        }

        String type = readAsString(body, "type");
        int id = parseIntSafely(body.get("id"),
                parseIntSafely(body.get("cc_col_01"), parseIntSafely(body.get("idx"), 0)));
        String inputValue = readAsString(body, "inputValue", "cc_col_02", "name").trim();
        Integer chk = parseNullableBool01(body.get("sub_col_02"));
        Integer rad = parseNullableBool01(body.get("sub_col_03"));
        Integer txt = parseNullableBool01(body.get("sub_col_04"));
        Integer sel = parseNullableBool01(body.get("sub_col_05"));

        if (chk == null && body.containsKey("checkbox")) {
            chk = parseBool01(body.get("checkbox"));
        }
        if (rad == null && body.containsKey("radio")) {
            rad = parseBool01(body.get("radio"));
        }
        if (txt == null && body.containsKey("textbox")) {
            txt = parseBool01(body.get("textbox"));
        }
        if (sel == null && body.containsKey("selectbox")) {
            sel = parseBool01(body.get("selectbox"));
        }

        if (id <= 0 || type.isBlank() || inputValue.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("message", "필수값 누락"));
        }

        int result = cs.modifyCategory(inst, type, id, inputValue, chk, rad, txt, sel);
        return ResponseEntity.ok(Map.of("result", result > 0 ? "1" : "0"));
    }

    @PostMapping({ "setting/saveCategoryOrder", "/setting/saveCategoryOrder" })
    @ResponseBody
    public ResponseEntity<?> settingSaveCategoryOrder(HttpSession session,
            @RequestBody List<Map<String, Object>> rows) {
        String inst = ensureInst(session);
        if (inst == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "세션이 만료되었습니다."));
        }
        if (rows == null || rows.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "저장할 순서 데이터가 없습니다."));
        }

        List<Map<String, Object>> normalized = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            if (row == null) {
                continue;
            }
            String type = readAsString(row, "type").trim().toLowerCase(Locale.ROOT);
            int id = parseIntSafely(row.get("id"), parseIntSafely(row.get("idx"), 0));
            if (id <= 0) {
                if ("parent".equals(type) || "main".equals(type)) {
                    id = parseIntSafely(row.get("categoryId"), 0);
                } else if ("child".equals(type) || "sub".equals(type)) {
                    id = parseIntSafely(row.get("subcategoryId"), 0);
                } else if ("option".equals(type) || "select".equals(type)) {
                    id = parseIntSafely(row.get("itemId"), parseIntSafely(row.get("subcategoryId"), 0));
                }
            }
            int order = parseIntSafely(row.get("order"), parseIntSafely(row.get("turn"), 0));
            if (id <= 0) {
                continue;
            }
            Map<String, Object> mapped = new HashMap<>();
            mapped.put("type", type);
            mapped.put("id", id);
            mapped.put("order", order);
            normalized.add(mapped);
        }

        int updated = cs.saveCategoryOrder(inst, normalized);
        return ResponseEntity.ok(Map.of("result", updated > 0 ? "1" : "0", "updated", updated));
    }

    // @GetMapping(value = "counsel/list", params = "!requestType")
    // public ModelAndView listView(
    // ModelAndView mv,
    // HttpSession session,
    // Criteria cri,
    // @RequestParam(value = "dateRange", defaultValue = "") String dateRange,
    // @RequestParam(value = "searchType", defaultValue = "") String searchType,
    // @RequestParam(value = "keyword", defaultValue = "") String keyword,
    // @RequestParam(value = "end", defaultValue = "on") String end,
    // @RequestParam(value = "page", defaultValue = "1") int page,
    // @RequestParam(value = "perPageNum", defaultValue = "30") int perPageNum) {

    // String inst = ensureInst(session);
    // if (inst == null)
    // return new ModelAndView("redirect:/login"); // context-path `/csm` 자동 부착됨

    // // --- 세션 userInfo가 없으면 보충 ---
    // Userdata info = (Userdata) session.getAttribute("userInfo");
    // if (info == null) {
    // String username =
    // SecurityContextHolder.getContext().getAuthentication().getName();
    // info = cs.loadUserInfo(inst, username); // <- 서비스 호출
    // if (info != null)
    // session.setAttribute("userInfo", info);
    // }
    // mv.addObject("info", info);
    // System.out.println("Info = " + info);

    // // 검색조건 세팅
    // bindCriteria(cri, inst, page, perPageNum, dateRange, searchType, keyword,
    // end);

    // // 데이터 조회
    // List<CounselData> cslist = cs.searchCounselData(cri);
    // int totalCnt = cs.CounselListCnt(cri);

    // // 복호화/마스킹
    // postProcessDecryptAndMask(cslist, inst);

    // // 페이징
    // Paging pageMaker = new Paging();
    // pageMaker.setCri(cri);
    // pageMaker.setTotalCount(totalCnt);

    // // 모델
    // mv.addObject("cri", cri);
    // mv.addObject("pageMaker", pageMaker);
    // mv.addObject("cnt", totalCnt);
    // mv.addObject("cslist", cslist);
    // mv.addObject("orderItems", cs.getOrderItems(inst));
    // mv.addObject("innerContentItems", cs.getInnerContentItems(inst));

    // mv.setViewName("csm/counsel/list");
    // return mv;
    // }

    /** JSON 반환 (무한스크롤 등 AJAX) */
    // @GetMapping(value = "/list", params = "requestType=json")
    // @ResponseBody
    // public ResponseEntity<Map<String, Object>> listJson(
    // HttpSession session,
    // Criteria cri,
    // @RequestParam(value = "dateRange", defaultValue = "") String dateRange,
    // @RequestParam(value = "searchType", defaultValue = "") String searchType,
    // @RequestParam(value = "keyword", defaultValue = "") String keyword,
    // @RequestParam(value = "end", defaultValue = "on") String end,
    // @RequestParam(value = "page", defaultValue = "1") int page,
    // @RequestParam(value = "perPageNum", defaultValue = "30") int perPageNum) {

    // String inst = ensureInst(session);
    // if (inst == null) {
    // return ResponseEntity.status(401)
    // .body(Map.of("success", false, "message", "세션이 만료되었습니다."));
    // }

    // bindCriteria(cri, inst, page, perPageNum, dateRange, searchType, keyword,
    // end);

    // List<CounselData> cslist = cs.searchCounselData(cri);
    // int totalCnt = cs.CounselListCnt(cri);

    // // 데이터가 없을 경우 처리
    // if (cslist.isEmpty()) {
    // Map<String, Object> body = new HashMap<>();
    // body.put("success", true);
    // body.put("cslist", Collections.emptyList());
    // body.put("hasMore", false);
    // body.put("orderItems", cs.getOrderItems(inst));
    // body.put("innerContentItems", cs.getInnerContentItems(inst));
    // return ResponseEntity.ok(body);
    // }

    // postProcessDecryptAndMask(cslist, inst);

    // boolean hasMore = (page * perPageNum) < totalCnt;

    // Map<String, Object> body = new HashMap<>();
    // body.put("success", true);
    // body.put("cslist", cslist);
    // body.put("hasMore", hasMore);
    // body.put("orderItems", cs.getOrderItems(inst));
    // body.put("innerContentItems", cs.getInnerContentItems(inst));

    // return ResponseEntity.ok(body);
    // }
    @GetMapping(value = "counsel/list", params = "!requestType", produces = "text/html")
    public ModelAndView getCounselListView(
            ModelAndView mv, HttpSession session, HttpServletRequest req, Criteria cri,
            @RequestParam(value = "dateRange", defaultValue = "") String dateRange,
            @RequestParam(value = "startDate", defaultValue = "") String startDate,
            @RequestParam(value = "endDate", defaultValue = "") String endDate,
            @RequestParam(value = "status", defaultValue = "all") String status,
            @RequestParam(value = "pathType", defaultValue = "all") String pathType,
            @RequestParam(value = "searchType", defaultValue = "") String searchType,
            @RequestParam(value = "keyword", defaultValue = "") String keyword,
            @RequestParam(value = "end", defaultValue = "on") String end,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "perPageNum", defaultValue = "30") int perPageNum) {

        String inst = ensureInst(session);
        if (inst == null)
            return new ModelAndView("redirect:/login");

        bindCriteria(cri, inst, page, perPageNum, dateRange, startDate, endDate, status, pathType, searchType, keyword,
                end);

        List<CounselData> cslist = cs.searchCounselData(cri);
        int totalCnt = cs.CounselListCnt(cri);
        postProcessDecryptAndMask(cslist, inst, cri.getSearchType(), cri.getKeyword());
        List<CounselReservation> reservedCounselReservations = cs.listCounselReservations(inst, "RESERVED", 200);
        if (reservedCounselReservations != null) {
            reservedCounselReservations.sort(Comparator
                    .comparing((CounselReservation r) -> r == null || r.getPriority() == null ? 99 : r.getPriority())
                    .thenComparing(r -> r == null ? "" : safeString(r.getReserved_at()))
                    .thenComparing(r -> r == null || r.getId() == null ? Long.MAX_VALUE : r.getId()));
        }

        Set<String> hiddenListColumns = getHiddenListColumns();
        List<Map<String, Object>> orderItems = normalizeDynamicOrderItemComments(
                inst,
                filterListSettingItems(cs.getOrderItems(inst), hiddenListColumns));
        List<Map<String, Object>> innerContentItems = buildListSettingInnerContentItems(inst, orderItems,
                hiddenListColumns);

        mv.addObject("orderItems", orderItems != null ? orderItems : Collections.emptyList());
        mv.addObject("innerContentItems", innerContentItems != null ? innerContentItems : Collections.emptyList());
        Paging pageMaker = new Paging();
        pageMaker.setCri(cri); // 내부에서 calculatePaging() 한 번 호출
        pageMaker.setTotalCount(totalCnt); // 다시 계산
        mv.addObject("pageMaker", pageMaker);
        mv.addObject("cri", cri);
        mv.addObject("cnt", totalCnt);
        mv.addObject("cslist", cslist);
        mv.addObject("statusOptions", cs.getCounselStatusOptions(inst));
        mv.addObject("pathTypeOptions", cs.getCounselPathTypeOptions(inst));
        mv.addObject("reservedCounselReservations",
                reservedCounselReservations != null ? reservedCounselReservations : Collections.emptyList());
        boolean mobile = isMobile(req);
        if (!mobile) {
            mv.addObject("headerMode", "list");
        }
        mv.setViewName(mobile ? "csm/counsel/list_m" : "csm/counsel/list");
        return mv;
    }

    @GetMapping(value = "counsel/list", params = "requestType=json", produces = "application/json;charset=UTF-8")
    @ResponseBody
    public Map<String, Object> getCounselListJson(
            HttpSession session, HttpServletRequest request, Criteria cri,
            @RequestParam(value = "dateRange", defaultValue = "") String dateRange,
            @RequestParam(value = "startDate", defaultValue = "") String startDate,
            @RequestParam(value = "endDate", defaultValue = "") String endDate,
            @RequestParam(value = "status", defaultValue = "all") String status,
            @RequestParam(value = "pathType", defaultValue = "all") String pathType,
            @RequestParam(value = "searchType", defaultValue = "") String searchType,
            @RequestParam(value = "keyword", defaultValue = "") String keyword,
            @RequestParam(value = "end", defaultValue = "on") String end,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "perPageNum", defaultValue = "30") int perPageNum) {

        log.warn(
                "[JSON] /counsel/list ENTER page={} perPageNum={} range={} start={} endDate={} status={} pathType={} st={} kw='{}' end={}",
                page, perPageNum, dateRange, startDate, endDate, status, pathType, searchType, keyword, end);

        String inst = ensureInst(session);
        if (inst == null) {
            log.warn("[JSON] no inst in session → redirect");
            return Map.of("success", false, "redirect", loginRedirectPath(request));
        }

        bindCriteria(cri, inst, page, perPageNum, dateRange, startDate, endDate, status, pathType, searchType, keyword,
                end);

        // 1) 조회
        List<CounselData> raw = cs.searchCounselData(cri);
        if (raw == null)
            raw = List.of();

        // 2) null 요소 제거 + 로깅
        int before = raw.size();
        List<CounselData> cslist = new ArrayList<>(raw);
        int nulls = 0;
        for (int i = cslist.size() - 1; i >= 0; i--) {
            if (cslist.get(i) == null) {
                nulls++;
                log.warn("[JSON] dao returned NULL element at index {}", i);
                cslist.remove(i);
            }
        }

        int totalCnt = cs.CounselListCnt(cri);
        // 3) 복호화/마스킹
        postProcessDecryptAndMask(cslist, inst, cri.getSearchType(), cri.getKeyword());

        // 4) 화면 컬럼 메타
        Set<String> hiddenListColumns = getHiddenListColumns();
        List<Map<String, Object>> orderItems = normalizeDynamicOrderItemComments(
                inst,
                filterListSettingItems(cs.getOrderItems(inst), hiddenListColumns));
        List<Map<String, Object>> innerContentItems = buildListSettingInnerContentItems(inst, orderItems,
                hiddenListColumns);

        // 5) 직렬화 안전한 맵으로 변환 (byte[] 등 제거)
        List<Map<String, Object>> rows = toRowMaps(cslist, orderItems, inst);

        boolean hasMore = (page * perPageNum) < totalCnt;
        log.warn("[JSON] dao size={} first={}", (raw == null ? -1 : raw.size()),
                (raw != null && !raw.isEmpty() ? raw.get(0) : null));

        Map<String, Object> body = new HashMap<>();
        body.put("success", true);
        body.put("hasMore", hasMore);
        body.put("cslist", rows); // ★ 이제 null 없이, 평평한 맵 구조로 내려감
        body.put("orderItems", orderItems != null ? orderItems : List.of());
        body.put("innerContentItems", innerContentItems != null ? innerContentItems : List.of());

        log.warn("[JSON] /counsel/list EXIT rows={} (from={} nulls={}) hasMore={} orderItems.size={}",
                rows.size(), before, nulls, hasMore, (orderItems != null ? orderItems.size() : -1));
        log.warn("[JSON] sample row0 = {}", rows.isEmpty() ? null : rows.get(0));
        return body;
    }

    @GetMapping({ "counsel/reservation", "/counsel/reservation" })
    public String counselReservationPage(
            @RequestParam(value = "status", defaultValue = "RESERVED") String status,
            @RequestParam(value = "reservationId", required = false) Long reservationId,
            @RequestParam(value = "message", required = false) String message,
            @RequestParam(value = "error", required = false) String error,
            Model model,
            HttpSession session) {
        String inst = ensureInst(session);
        if (inst == null) {
            return "redirect:/login";
        }

        Userdata info = ensureUserInfo(session, inst);
        String selectedStatus = normalizeReservationStatusParam(status, true);
        List<CounselReservation> reservations = cs.listCounselReservations(inst, selectedStatus, 500);
        List<CounselReservation> allReservations = cs.listCounselReservations(inst, "ALL", 2000);

        CounselReservation reservationForm = (reservationId != null && reservationId > 0)
                ? cs.getCounselReservationById(inst, reservationId)
                : null;
        if (reservationForm == null) {
            reservationForm = new CounselReservation();
            reservationForm.setStatus("RESERVED");
            reservationForm.setPriority(3);
            reservationForm.setReserved_at(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        }

        model.addAttribute("info", info);
        model.addAttribute("inst", inst);
        model.addAttribute("endVar", "on");
        model.addAttribute("st", "");
        model.addAttribute("kw", "");
        model.addAttribute("selectedStatus", selectedStatus);
        model.addAttribute("reservations", reservations);
        model.addAttribute("reservationForm", reservationForm);
        model.addAttribute("reservationFormReservedAtInput", toDateTimeLocalValue(reservationForm.getReserved_at()));
        model.addAttribute("statusCounts", countReservationStatus(allReservations));
        model.addAttribute("message", message);
        model.addAttribute("error", error);
        return "csm/counsel/reservation";
    }

    @PostMapping({ "counsel/reservation/save", "/counsel/reservation/save" })
    public String saveCounselReservation(
            @RequestParam(value = "id", required = false) Long id,
            @RequestParam("patientName") String patientName,
            @RequestParam(value = "patientPhone", required = false) String patientPhone,
            @RequestParam(value = "guardianName", required = false) String guardianName,
            @RequestParam(value = "callSummary", required = false) String callSummary,
            @RequestParam(value = "priority", required = false, defaultValue = "3") Integer priority,
            @RequestParam(value = "reservedAt", required = false) String reservedAt,
            @RequestParam(value = "status", required = false, defaultValue = "RESERVED") String status,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        String inst = ensureInst(session);
        if (inst == null) {
            return "redirect:/login";
        }

        try {
            CounselReservation reservation = new CounselReservation();
            reservation.setId(id);
            reservation.setPatient_name(patientName);
            reservation.setPatient_phone(patientPhone);
            reservation.setGuardian_name(guardianName);
            reservation.setCall_summary(callSummary);
            reservation.setPriority(priority);
            String normalizedReservedAt = safeString(reservedAt).trim();
            if (normalizedReservedAt.isEmpty() && (id == null || id <= 0)) {
                normalizedReservedAt = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            }
            reservation.setReserved_at(normalizedReservedAt);
            reservation.setStatus(normalizeReservationStatusParam(status, false));
            reservation.setCreated_by(resolveReservationActor(inst, session));

            long savedId = cs.saveCounselReservation(inst, reservation);
            redirectAttributes.addAttribute("status", normalizeReservationStatusParam(status, true));
            redirectAttributes.addAttribute("reservationId", savedId);
            redirectAttributes.addAttribute("message", "상담 접수가 저장되었습니다.");
        } catch (IllegalArgumentException e) {
            redirectAttributes.addAttribute("status", normalizeReservationStatusParam(status, true));
            redirectAttributes.addAttribute("reservationId", id);
            redirectAttributes.addAttribute("error", e.getMessage());
        } catch (Exception e) {
            log.warn("[reservation] save fail inst={}, err={}", inst, e.toString());
            redirectAttributes.addAttribute("status", normalizeReservationStatusParam(status, true));
            redirectAttributes.addAttribute("reservationId", id);
            redirectAttributes.addAttribute("error", "상담 접수 저장 중 오류가 발생했습니다.");
        }
        return "redirect:/counsel/reservation";
    }

    @PostMapping({ "counsel/reservation/status", "/counsel/reservation/status" })
    public String updateCounselReservationStatus(
            @RequestParam("reservationId") Long reservationId,
            @RequestParam("status") String status,
            @RequestParam(value = "viewStatus", defaultValue = "RESERVED") String viewStatus,
            HttpSession session,
            RedirectAttributes redirectAttributes) {
        String inst = ensureInst(session);
        if (inst == null) {
            return "redirect:/login";
        }

        try {
            cs.updateCounselReservationStatus(inst, reservationId, status, resolveReservationActor(inst, session));
            redirectAttributes.addAttribute("message", "접수 상태가 변경되었습니다.");
        } catch (Exception e) {
            log.warn("[reservation] status update fail inst={}, reservationId={}, err={}", inst, reservationId, e.toString());
            redirectAttributes.addAttribute("error", "접수 상태 변경 중 오류가 발생했습니다.");
        }
        redirectAttributes.addAttribute("status", normalizeReservationStatusParam(viewStatus, true));
        return "redirect:/counsel/reservation";
    }

    @GetMapping({ "notice", "/notice" })
    public String institutionNoticePage(Model model, HttpSession session) {
        String inst = ensureInst(session);
        if (inst == null) {
            return "redirect:/login";
        }
        if (isCoreInst(inst)) {
            return "redirect:/core/notice";
        }

        Userdata info = ensureUserInfo(session, inst);
        String userId = resolveNoticeUserId(session);
        List<Map<String, Object>> notices = cs.listInstitutionNotices(inst, userId, 500);
        int unreadCount = cs.countInstitutionUnreadNotices(inst, userId);

        model.addAttribute("info", info);
        model.addAttribute("inst", inst);
        model.addAttribute("endVar", "on");
        model.addAttribute("st", "");
        model.addAttribute("kw", "");
        model.addAttribute("notices", notices);
        model.addAttribute("unreadCount", unreadCount);
        model.addAttribute("noticeCount", notices.size());
        return "csm/notice/list";
    }

    @PostMapping({ "notice/read/{noticeId}", "/notice/read/{noticeId}" })
    @ResponseBody
    public ResponseEntity<?> markInstitutionNoticeRead(
            @PathVariable("noticeId") long noticeId,
            HttpSession session) {
        String inst = ensureInst(session);
        if (inst == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "세션이 만료되었습니다."));
        }
        if (isCoreInst(inst)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("message", "권한 없음"));
        }
        String userId = resolveNoticeUserId(session);
        if (userId.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "사용자 정보를 확인할 수 없습니다."));
        }
        Integer userIdx = resolveNoticeUserIdx(session);
        int updated = cs.markInstitutionNoticeRead(inst, userId, userIdx, noticeId);
        int unreadCount = cs.countInstitutionUnreadNotices(inst, userId);
        return ResponseEntity.ok(Map.of(
                "result", "1",
                "updated", updated,
                "unreadCount", unreadCount));
    }

    @GetMapping({ "notice/popup/next", "/notice/popup/next" })
    @ResponseBody
    public ResponseEntity<?> institutionNoticePopupNext(HttpSession session) {
        String inst = ensureInst(session);
        if (inst == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("result", "0", "message", "세션이 만료되었습니다."));
        }
        if (isCoreInst(inst)) {
            Map<String, Object> coreResponse = new HashMap<>();
            coreResponse.put("result", "0");
            coreResponse.put("notice", null);
            coreResponse.put("unreadCount", 0);
            return ResponseEntity.ok(coreResponse);
        }
        String userId = resolveNoticeUserId(session);
        if (userId.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("result", "0", "message", "사용자 정보를 확인할 수 없습니다."));
        }

        List<Map<String, Object>> notices = Optional
                .ofNullable(cs.listInstitutionNotices(inst, userId, 300))
                .orElse(Collections.emptyList());
        Map<String, Object> popupNotice = null;
        for (Map<String, Object> notice : notices) {
            if (notice == null) {
                continue;
            }
            String popupYn = notice.get("popup_yn") == null ? "" : String.valueOf(notice.get("popup_yn")).trim();
            String readYn = notice.get("read_yn") == null ? "" : String.valueOf(notice.get("read_yn")).trim();
            if ("Y".equalsIgnoreCase(popupYn) && !"Y".equalsIgnoreCase(readYn)) {
                popupNotice = notice;
                break;
            }
        }
        int unreadCount = cs.countInstitutionUnreadNotices(inst, userId);
        Map<String, Object> response = new HashMap<>();
        response.put("result", popupNotice == null ? "0" : "1");
        response.put("notice", popupNotice);
        response.put("unreadCount", unreadCount);
        return ResponseEntity.ok(response);
    }

    @GetMapping({ "setting", "/setting", "setting/", "/setting/" })
    public String settingPage(
            Model model,
            HttpSession session,
            @RequestParam(value = "end", defaultValue = "on") String end,
            @RequestParam(value = "searchType", defaultValue = "") String searchType,
            @RequestParam(value = "keyword", defaultValue = "") String keyword) {
        String inst = ensureInst(session);
        if (inst == null) {
            return "redirect:/login";
        }

        Userdata userinfo = ensureUserInfo(session, inst);
        model.addAttribute("info", userinfo);
        model.addAttribute("endVar", end);
        model.addAttribute("st", searchType);
        model.addAttribute("kw", keyword);
        Map<String, Object> categoryDataMap = cs.getCategoryData(inst);
        model.addAttribute("categoryData", categoryDataMap.get("categoryData"));
        model.addAttribute("fieldTypeMapping", categoryDataMap.get("fieldTypeMapping"));
        model.addAttribute("fieldOptionsMapping", categoryDataMap.get("fieldOptionsMapping"));
        return "csm/setting/setting";
    }

    @GetMapping({ "setting/templates", "/setting/templates" })
    @ResponseBody
    public ResponseEntity<?> settingTemplates(HttpSession session) {
        String inst = ensureInst(session);
        if (inst == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "세션이 만료되었습니다."));
        }

        List<Map<String, Object>> rows = Optional.ofNullable(cs.coreTemplateSelect())
                .orElse(Collections.emptyList())
                .stream()
                .filter(Objects::nonNull)
                .filter(row -> !"Y".equalsIgnoreCase(Objects.toString(row.get("del_yn"), "N")))
                .map(row -> {
                    Map<String, Object> out = new LinkedHashMap<>();
                    out.put("idx", parseIntSafely(row.get("idx"), 0));
                    out.put("name", readAsString(row, "name"));
                    out.put("description", readAsString(row, "description"));
                    return out;
                })
                .filter(row -> parseIntSafely(row.get("idx"), 0) > 0)
                .collect(Collectors.toList());

        return ResponseEntity.ok(rows);
    }

    @GetMapping({ "setting/getTemplate/{templateIdx}", "/setting/getTemplate/{templateIdx}" })
    @ResponseBody
    public ResponseEntity<?> settingGetTemplate(HttpSession session, @PathVariable int templateIdx) {
        String inst = ensureInst(session);
        if (inst == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "세션이 만료되었습니다."));
        }
        if (templateIdx <= 0) {
            return ResponseEntity.badRequest().body(Map.of("message", "templateIdx가 올바르지 않습니다."));
        }

        List<Map<String, Object>> mainCategories = Optional.ofNullable(cs.coreTemplateMainSelect(templateIdx))
                .orElse(Collections.emptyList());

        Map<String, List<Map<String, Object>>> subCategoryMap = new LinkedHashMap<>();
        Map<String, List<Map<String, Object>>> optionMap = new LinkedHashMap<>();
        Map<String, String> fieldTypeMapping = new LinkedHashMap<>();
        Map<String, List<Map<String, Object>>> fieldOptionsMapping = new LinkedHashMap<>();

        for (Map<String, Object> main : mainCategories) {
            if (main == null) {
                continue;
            }
            int mainIdx = parseIntSafely(main.get("idx"), 0);
            if (mainIdx <= 0) {
                continue;
            }

            List<Map<String, Object>> subCategories = Optional.ofNullable(cs.coreTemplateSubSelect(mainIdx))
                    .orElse(Collections.emptyList());
            subCategoryMap.put(String.valueOf(mainIdx), subCategories);

            for (Map<String, Object> sub : subCategories) {
                if (sub == null) {
                    continue;
                }
                int subIdx = parseIntSafely(sub.get("idx"), 0);
                if (subIdx <= 0) {
                    continue;
                }

                int chk = parseIntSafely(sub.get("sub_col_02"), 0);
                int rad = parseIntSafely(sub.get("sub_col_03"), 0);
                int txt = parseIntSafely(sub.get("sub_col_04"), 0);
                int sel = parseIntSafely(sub.get("sub_col_05"), 0);
                String fieldKey = "field_" + mainIdx + "_" + subIdx;

                fieldTypeMapping.put(fieldKey, resolveTemplateFieldType(chk, rad, txt, sel));

                List<Map<String, Object>> options = Optional.ofNullable(cs.coreTemplateOptionSelect(subIdx))
                        .orElse(Collections.emptyList());
                optionMap.put(String.valueOf(subIdx), options);
                fieldOptionsMapping.put(fieldKey, options);
            }
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("mainCategories", mainCategories);
        response.put("subCategoryMap", subCategoryMap);
        response.put("optionMap", optionMap);
        response.put("fieldTypeMapping", fieldTypeMapping);
        response.put("fieldOptionsMapping", fieldOptionsMapping);
        return ResponseEntity.ok(response);
    }

    @PostMapping({ "setting/postTemplate/{templateIdx}", "/setting/postTemplate/{templateIdx}" })
    @ResponseBody
    public ResponseEntity<?> settingPostTemplate(HttpSession session, @PathVariable int templateIdx) {
        String inst = ensureInst(session);
        if (inst == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "세션이 만료되었습니다."));
        }
        if (templateIdx <= 0) {
            return ResponseEntity.badRequest().body(Map.of("message", "templateIdx가 올바르지 않습니다."));
        }

        List<Map<String, Object>> mainCategories = Optional.ofNullable(cs.coreTemplateMainSelect(templateIdx))
                .orElse(Collections.emptyList());
        if (mainCategories.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("message", "적용할 템플릿 데이터가 없습니다."));
        }

        try {
            List<Category1> existingMain = Optional.ofNullable(cs.selectCategory1(inst)).orElse(Collections.emptyList());
            for (Category1 c1 : existingMain) {
                if (c1 == null || c1.getCc_col_01() <= 0) {
                    continue;
                }
                cs.deleteCategory(inst, "parent", c1.getCc_col_01());
            }

            for (Map<String, Object> main : mainCategories) {
                if (main == null) {
                    continue;
                }
                int templateMainIdx = parseIntSafely(main.get("idx"), 0);
                String mainName = readAsString(main, "main_col_01").trim();
                if (templateMainIdx <= 0 || mainName.isBlank()) {
                    continue;
                }

                Category1 insertedMain = cs.insertCategory1(inst, mainName);
                int insertedMainIdx = insertedMain.getCc_col_01();
                if (insertedMainIdx <= 0) {
                    continue;
                }

                List<Map<String, Object>> subCategories = Optional.ofNullable(cs.coreTemplateSubSelect(templateMainIdx))
                        .orElse(Collections.emptyList());

                for (Map<String, Object> sub : subCategories) {
                    if (sub == null) {
                        continue;
                    }
                    int templateSubIdx = parseIntSafely(sub.get("idx"), 0);
                    String subName = readAsString(sub, "sub_col_01").trim();
                    int chk = parseIntSafely(sub.get("sub_col_02"), 0);
                    int rad = parseIntSafely(sub.get("sub_col_03"), 0);
                    int txt = parseIntSafely(sub.get("sub_col_04"), 0);
                    int sel = parseIntSafely(sub.get("sub_col_05"), 0);

                    if (templateSubIdx <= 0 || subName.isBlank()) {
                        continue;
                    }

                    Category2 insertedSub = cs.insertCategory2(inst, insertedMainIdx, subName, chk, rad, txt, sel);
                    if (insertedSub == null || insertedSub.getCc_col_01() <= 0 || sel != 1) {
                        continue;
                    }

                    List<String> options = Optional.ofNullable(cs.coreTemplateOptionSelect(templateSubIdx))
                            .orElse(Collections.emptyList())
                            .stream()
                            .map(opt -> Objects.toString(opt.get("option_col_01"), "").trim())
                            .filter(v -> !v.isBlank())
                            .collect(Collectors.toList());
                    if (!options.isEmpty()) {
                        cs.insertCategory3(inst, insertedSub.getCc_col_01(), subName, options);
                    }
                }
            }
            return ResponseEntity.ok(Map.of("result", "1"));
        } catch (Exception e) {
            log.error("[setting/postTemplate] fail inst={}, templateIdx={}", inst, templateIdx, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("result", "0", "message", "템플릿 적용 중 오류가 발생했습니다."));
        }
    }

    @GetMapping({ "setting/TemplatePreview", "/setting/TemplatePreview", "setting/templatePreview",
            "/setting/templatePreview" })
    @ResponseBody
    public ResponseEntity<?> settingTemplatePreview(HttpSession session) {
        String inst = ensureInst(session);
        if (inst == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("message", "세션이 만료되었습니다."));
        }
        Map<String, Object> categoryDataMap = cs.getCategoryData(inst);
        return ResponseEntity.ok(Map.of(
                "categoryData", categoryDataMap.get("categoryData"),
                "fieldTypeMapping", categoryDataMap.get("fieldTypeMapping"),
                "fieldOptionsMapping", categoryDataMap.get("fieldOptionsMapping")));
    }

    private String resolveTemplateFieldType(int chk, int rad, int txt, int sel) {
        boolean hasChk = chk == 1;
        boolean hasRad = rad == 1;
        boolean hasTxt = txt == 1;
        boolean hasSel = sel == 1;

        if (hasChk && !hasRad && !hasTxt && !hasSel)
            return "checkbox_only";
        if (hasRad && !hasChk && !hasTxt && !hasSel)
            return "radio_only";
        if (hasSel && !hasChk && !hasRad && !hasTxt)
            return "select_only";
        if (hasTxt && !hasChk && !hasRad && !hasSel)
            return "text_only";
        if (hasChk && hasTxt && !hasRad && !hasSel)
            return "checkbox_text";
        if (hasChk && hasSel && !hasRad && !hasTxt)
            return "checkbox_select";
        if (hasRad && hasTxt && !hasChk && !hasSel)
            return "radio_text";
        if (hasRad && hasSel && !hasChk && !hasTxt)
            return "radio_select";
        if (hasChk && hasTxt && hasSel && !hasRad)
            return "checkbox_select_text";
        if (hasRad && hasTxt && hasSel && !hasChk)
            return "radio_select_text";
        return "unknown";
    }

    @GetMapping({ "statistics", "/statistics", "statistics/", "/statistics/" })
    public String statisticsPage(Model model, HttpSession session) {
        String inst = ensureInst(session);
        if (inst == null) {
            return "redirect:/login";
        }
        Userdata userinfo = ensureUserInfo(session, inst);
        model.addAttribute("info", userinfo);
        model.addAttribute("endVar", "on");
        model.addAttribute("st", "");
        model.addAttribute("kw", "");
        String instname = (String) session.getAttribute("instname");
        if ((instname == null || instname.isBlank()) && userinfo != null) {
            instname = userinfo.getUs_col_05();
        }
        LocalDate now = LocalDate.now();
        boolean hasData = false;
        int firstYear = now.getYear();
        int firstMonth = now.getMonthValue();
        int lastYear = now.getYear();
        int lastMonth = now.getMonthValue();
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("inst", inst);
            params.put("instname", instname);
            Map<String, Object> dateRange = cs.getCounselDateRange(params);
            if (dateRange != null) {
                java.sql.Date firstDateRaw = (java.sql.Date) dateRange.get("first_date");
                java.sql.Date lastDateRaw = (java.sql.Date) dateRange.get("last_date");
                if (firstDateRaw != null && lastDateRaw != null) {
                    LocalDate firstDate = firstDateRaw.toLocalDate();
                    LocalDate lastDate = lastDateRaw.toLocalDate();
                    firstYear = firstDate.getYear();
                    firstMonth = firstDate.getMonthValue();
                    lastYear = lastDate.getYear();
                    lastMonth = lastDate.getMonthValue();
                    hasData = true;
                }
            }
        } catch (Exception e) {
            log.warn("statisticsPage dateRange load failed", e);
        }
        model.addAttribute("hasData", hasData);
        model.addAttribute("firstYear", firstYear);
        model.addAttribute("firstMonth", firstMonth);
        model.addAttribute("lastYear", lastYear);
        model.addAttribute("lastMonth", lastMonth);
        return "csm/counsel/chart";
    }

    @ResponseBody
    @RequestMapping("statistics1")
    public ResponseEntity<?> getMonthlyStatistics(
            @RequestParam("year") int year,
            @RequestParam("month") int month,
            @RequestParam(value = "counselor", defaultValue = "") String counselor,
            HttpSession session) {
        String inst = ensureInst(session);
        if (inst == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("세션이 만료되었습니다.");
        }
        String instname = (String) session.getAttribute("instname");
        if ((instname == null || instname.isBlank()) && session.getAttribute("userInfo") instanceof Userdata info) {
            instname = info.getUs_col_05();
        }
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("year", year);
            params.put("month", month);
            params.put("counselor", counselor);
            params.put("inst", inst);
            params.put("instname", instname);
            return ResponseEntity.ok(cs.getMonthlyCounselStatistics(params));
        } catch (Exception e) {
            log.error("statistics1 error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("상담 통계 조회 실패");
        }
    }

    @ResponseBody
    @GetMapping("statisticsType")
    public ResponseEntity<?> getTypeStatistics(
            @RequestParam int year,
            @RequestParam int month,
            @RequestParam(value = "counselor", defaultValue = "") String counselor,
            HttpSession session) {
        String inst = ensureInst(session);
        if (inst == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("세션이 만료되었습니다.");
        }
        String instname = (String) session.getAttribute("instname");
        if ((instname == null || instname.isBlank()) && session.getAttribute("userInfo") instanceof Userdata info) {
            instname = info.getUs_col_05();
        }
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("year", year);
            params.put("month", month);
            params.put("counselor", counselor);
            params.put("inst", inst);
            params.put("instname", instname);
            return ResponseEntity.ok(cs.getTypeStatistics(params));
        } catch (Exception e) {
            log.error("statisticsType error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("상담형태 통계 조회 실패");
        }
    }

    @ResponseBody
    @GetMapping("statisticsAdmissionSuccess")
    public ResponseEntity<?> getAdmissionSuccessStats(
            @RequestParam int year,
            @RequestParam int month,
            @RequestParam(value = "counselor", defaultValue = "") String counselor,
            HttpSession session) {
        String inst = ensureInst(session);
        if (inst == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("세션이 만료되었습니다.");
        }
        String instname = (String) session.getAttribute("instname");
        if ((instname == null || instname.isBlank()) && session.getAttribute("userInfo") instanceof Userdata info) {
            instname = info.getUs_col_05();
        }
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("year", year);
            params.put("month", month);
            params.put("counselor", counselor);
            params.put("inst", inst);
            params.put("instname", instname);
            return ResponseEntity.ok(cs.selectAdmissionSuccessStats(params));
        } catch (Exception e) {
            log.error("statisticsAdmissionSuccess error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("입원연계 성공 통계 조회 실패");
        }
    }

    @ResponseBody
    @GetMapping("statisticsByAdmissionType")
    public ResponseEntity<?> statisticsByAdmissionType(
            @RequestParam int year,
            @RequestParam int month,
            @RequestParam(value = "counselor", defaultValue = "") String counselor,
            HttpSession session) {
        String inst = ensureInst(session);
        if (inst == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("세션이 만료되었습니다.");
        }
        String instname = (String) session.getAttribute("instname");
        if ((instname == null || instname.isBlank()) && session.getAttribute("userInfo") instanceof Userdata info) {
            instname = info.getUs_col_05();
        }
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("year", year);
            params.put("month", month);
            params.put("counselor", counselor);
            params.put("inst", inst);
            params.put("instname", instname);
            return ResponseEntity.ok(cs.selectAdmissionTypeStats(params));
        } catch (Exception e) {
            log.error("statisticsByAdmissionType error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("상담연계유형 통계 조회 실패");
        }
    }

    @ResponseBody
    @GetMapping("statisticsByAdmissionTypeSuccess")
    public ResponseEntity<?> statisticsByAdmissionTypeSuccess(
            @RequestParam int year,
            @RequestParam int month,
            @RequestParam(value = "counselor", defaultValue = "") String counselor,
            HttpSession session) {
        String inst = ensureInst(session);
        if (inst == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("세션이 만료되었습니다.");
        }
        String instname = (String) session.getAttribute("instname");
        if ((instname == null || instname.isBlank()) && session.getAttribute("userInfo") instanceof Userdata info) {
            instname = info.getUs_col_05();
        }
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("year", year);
            params.put("month", month);
            params.put("counselor", counselor);
            params.put("inst", inst);
            params.put("instname", instname);
            return ResponseEntity.ok(cs.selectAdmissionTypeSuccessStats(params));
        } catch (Exception e) {
            log.error("statisticsByAdmissionTypeSuccess error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("입원연계 성공유형 통계 조회 실패");
        }
    }

    @ResponseBody
    @GetMapping("statisticsByCurrentLocation")
    public ResponseEntity<?> statisticsByCurrentLocation(
            @RequestParam int year,
            @RequestParam int month,
            @RequestParam(value = "counselor", defaultValue = "") String counselor,
            HttpSession session) {
        String inst = ensureInst(session);
        if (inst == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("세션이 만료되었습니다.");
        }
        String instname = (String) session.getAttribute("instname");
        if ((instname == null || instname.isBlank()) && session.getAttribute("userInfo") instanceof Userdata info) {
            instname = info.getUs_col_05();
        }
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("year", year);
            params.put("month", month);
            params.put("counselor", counselor);
            params.put("inst", inst);
            params.put("instname", instname);
            return ResponseEntity.ok(cs.getCurrentLocationStats(params));
        } catch (Exception e) {
            log.error("statisticsByCurrentLocation error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("현재 계신곳 통계 조회 실패");
        }
    }

    @ResponseBody
    @GetMapping("statisticsByCurrentLocationSuccess")
    public ResponseEntity<?> statisticsByCurrentLocationSuccess(
            @RequestParam int year,
            @RequestParam int month,
            @RequestParam(value = "counselor", defaultValue = "") String counselor,
            HttpSession session) {
        String inst = ensureInst(session);
        if (inst == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("세션이 만료되었습니다.");
        }
        String instname = (String) session.getAttribute("instname");
        if ((instname == null || instname.isBlank()) && session.getAttribute("userInfo") instanceof Userdata info) {
            instname = info.getUs_col_05();
        }
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("year", year);
            params.put("month", month);
            params.put("counselor", counselor);
            params.put("inst", inst);
            params.put("instname", instname);
            return ResponseEntity.ok(cs.getCurrentLocationSuccessStats(params));
        } catch (Exception e) {
            log.error("statisticsByCurrentLocationSuccess error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("현재 계신곳 입원연계 성공 통계 조회 실패");
        }
    }

    @ResponseBody
    @GetMapping("statisticsNonAdmissionReason")
    public ResponseEntity<?> statisticsNonAdmissionReason(
            @RequestParam int year,
            @RequestParam int month,
            @RequestParam(value = "counselor", defaultValue = "") String counselor,
            HttpSession session) {
        String inst = ensureInst(session);
        if (inst == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("세션이 만료되었습니다.");
        }
        String instname = (String) session.getAttribute("instname");
        if ((instname == null || instname.isBlank()) && session.getAttribute("userInfo") instanceof Userdata info) {
            instname = info.getUs_col_05();
        }
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("year", year);
            params.put("month", month);
            params.put("counselor", counselor);
            params.put("inst", inst);
            params.put("instname", instname);
            return ResponseEntity.ok(cs.getNonAdmissionReasonStats(params));
        } catch (Exception e) {
            log.error("statisticsNonAdmissionReason error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("미입원사유 통계 조회 실패");
        }
    }

    @GetMapping(value = { "statistics/export", "/statistics/export" })
    public ResponseEntity<?> exportStatisticsExcel(
            @RequestParam int year,
            @RequestParam int month,
            @RequestParam(value = "counselor", defaultValue = "") String counselor,
            HttpSession session) {
        String inst = ensureInst(session);
        if (inst == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("세션이 만료되었습니다.");
        }

        String instname = (String) session.getAttribute("instname");
        if ((instname == null || instname.isBlank()) && session.getAttribute("userInfo") instanceof Userdata info) {
            instname = info.getUs_col_05();
        }

        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Map<String, Object> params = new HashMap<>();
            params.put("year", year);
            params.put("month", month);
            params.put("counselor", counselor);
            params.put("inst", inst);
            params.put("instname", instname);

            writeStatisticsMetaSheet(workbook, year, month, counselor, instname);
            writeMapSheet(
                    workbook,
                    "1_상담자별월별",
                    cs.getMonthlyCounselStatistics(params),
                    List.of("month", "counselor", "count"),
                    Map.of("month", "월", "counselor", "상담자", "count", "건수"));
            writeMapSheet(
                    workbook,
                    "1-1_상담형태별월별",
                    cs.getTypeStatistics(params),
                    List.of("month", "method", "count"),
                    Map.of("month", "월", "method", "상담형태", "count", "건수"));
            writeMapSheet(
                    workbook,
                    "2_입원연계성공월별",
                    cs.selectAdmissionSuccessStats(params),
                    List.of("month", "counselor", "count"),
                    Map.of("month", "월", "counselor", "상담자", "count", "건수"));
            writeMapSheet(
                    workbook,
                    "3_상담유입경로월별",
                    cs.selectAdmissionTypeStats(params),
                    List.of("month", "type", "counselor", "count"),
                    Map.of("month", "월", "type", "상담유입경로", "counselor", "상담자", "count", "건수"));
            writeMapSheet(
                    workbook,
                    "3-1_상담유입경로성공월별",
                    cs.selectAdmissionTypeSuccessStats(params),
                    List.of("month", "type", "counselor", "count"),
                    Map.of("month", "월", "type", "상담유입경로", "counselor", "상담자", "count", "건수"));
            writeMapSheet(
                    workbook,
                    "4_현재계신곳월별",
                    cs.getCurrentLocationStats(params),
                    List.of("month", "type", "counselor", "count"),
                    Map.of("month", "월", "type", "현재계신곳", "counselor", "상담자", "count", "건수"));
            writeMapSheet(
                    workbook,
                    "4-1_현재계신곳성공월별",
                    cs.getCurrentLocationSuccessStats(params),
                    List.of("month", "type", "counselor", "count"),
                    Map.of("month", "월", "type", "현재계신곳", "counselor", "상담자", "count", "건수"));
            writeMapSheet(
                    workbook,
                    "5_미입원사유월별",
                    cs.getNonAdmissionReasonStats(params),
                    List.of("month", "type", "count"),
                    Map.of("month", "월", "type", "미입원사유", "count", "건수"));

            workbook.write(out);
            String counselorPart = (counselor == null || counselor.isBlank()) ? "전체" : counselor;
            String fileName = String.format("statistics_%d-%02d_%s.xlsx", year, month, counselorPart);
            String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encoded)
                    .contentType(
                            MediaType.parseMediaType(
                                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(out.toByteArray());
        } catch (Exception e) {
            log.error("statistics/export error", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("통계 엑셀 다운로드 실패");
        }
    }

    private void writeStatisticsMetaSheet(
            Workbook workbook,
            int year,
            int month,
            String counselor,
            String instname) {
        Sheet sheet = workbook.createSheet("조회조건");
        Row head = sheet.createRow(0);
        head.createCell(0).setCellValue("항목");
        head.createCell(1).setCellValue("값");

        Row r1 = sheet.createRow(1);
        r1.createCell(0).setCellValue("기관");
        r1.createCell(1).setCellValue(instname == null ? "" : instname);

        Row r2 = sheet.createRow(2);
        r2.createCell(0).setCellValue("기준년월");
        r2.createCell(1).setCellValue(String.format("%d-%02d", year, month));

        Row r3 = sheet.createRow(3);
        r3.createCell(0).setCellValue("상담자");
        r3.createCell(1).setCellValue((counselor == null || counselor.isBlank()) ? "전체" : counselor);

        Row r4 = sheet.createRow(4);
        r4.createCell(0).setCellValue("다운로드시각");
        r4.createCell(1).setCellValue(LocalDateTime.now().toString());

        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
    }

    private void writeMapSheet(
            Workbook workbook,
            String sheetName,
            List<Map<String, Object>> rows,
            List<String> columnOrder,
            Map<String, String> headers) throws IOException {
        String safeSheetName = sheetName.length() > 31 ? sheetName.substring(0, 31) : sheetName;
        Sheet sheet = workbook.createSheet(safeSheetName);

        CellStyle headerStyle = workbook.createCellStyle();
        org.apache.poi.ss.usermodel.Font headerFont = workbook.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);

        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < columnOrder.size(); i++) {
            String key = columnOrder.get(i);
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers.getOrDefault(key, key));
            cell.setCellStyle(headerStyle);
        }

        List<Map<String, Object>> safeRows = rows == null ? Collections.emptyList() : rows;
        for (int r = 0; r < safeRows.size(); r++) {
            Map<String, Object> rowData = safeRows.get(r);
            Row row = sheet.createRow(r + 1);
            for (int c = 0; c < columnOrder.size(); c++) {
                Object value = rowData.get(columnOrder.get(c));
                Cell cell = row.createCell(c);
                if (value == null) {
                    cell.setCellValue("");
                } else if (value instanceof Number n) {
                    cell.setCellValue(n.doubleValue());
                } else {
                    cell.setCellValue(String.valueOf(value));
                }
            }
        }

        for (int i = 0; i < columnOrder.size(); i++) {
            sheet.autoSizeColumn(i);
        }
    }

    @GetMapping(value = { "smsSetting", "/smsSetting", "smsSetting/", "/smsSetting/" }, params = "!requestType")
    public String smsSettingPage(
            Model model,
            HttpSession session,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "perPageNum", defaultValue = "10") int perPageNum,
            @RequestParam(value = "comment", defaultValue = "") String comment,
            @RequestParam(value = "end", defaultValue = "on") String end,
            @RequestParam(value = "searchType", defaultValue = "") String searchType,
            @RequestParam(value = "keyword", defaultValue = "") String keyword) {
        String inst = ensureInst(session);
        if (inst == null) {
            return "redirect:/login";
        }

        Userdata userinfo = ensureUserInfo(session, inst);
        model.addAttribute("info", userinfo);
        model.addAttribute("page", page);
        model.addAttribute("perPageNum", perPageNum);
        model.addAttribute("comment", comment);
        model.addAttribute("endVar", end);
        model.addAttribute("st", searchType);
        model.addAttribute("kw", keyword);
        Criteria cri = new Criteria();
        cri.setInst(inst);
        cri.setPage(page);
        cri.setPerPageNum(perPageNum);
        cri.setKeyword(comment);
        model.addAttribute("smsTemplates", ss.SelectTemplate(cri));
        return "csm/sms/smssetting";
    }

    @GetMapping(value = { "smsSetting",
            "/smsSetting" }, params = "requestType=json", produces = "application/json;charset=UTF-8")
    @ResponseBody
    public Map<String, Object> smsSettingJson(
            HttpServletRequest request,
            HttpSession session,
            @RequestParam(value = "comment", defaultValue = "") String comment,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "perPageNum", defaultValue = "10") int perPageNum) {
        String inst = ensureInst(session);
        if (inst == null) {
            return Map.of("success", false, "redirect", loginRedirectPath(request));
        }

        Criteria cri = new Criteria();
        cri.setInst(inst);
        cri.setKeyword(comment);
        cri.setPage(page);
        cri.setPerPageNum(perPageNum);

        int total = ss.SelectTemplateCnt(cri);
        List<SmsTemplate> smsTemplates = ss.SelectTemplate(cri);
        boolean hasMore = (page * perPageNum) < total;

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("smsTemplates", smsTemplates != null ? smsTemplates : Collections.emptyList());
        response.put("hasMore", hasMore);
        return response;
    }

    @PostMapping("smsInsert")
    @ResponseBody
    public Map<String, Object> smsInsert(HttpSession session, @RequestBody SmsTemplate payload) {
        String inst = ensureInst(session);
        if (inst == null) {
            return Map.of("result", "0", "message", "세션 만료");
        }
        payload.setInst(inst);
        int result = ss.smsInsert(payload);
        return Map.of("result", result > 0 ? "1" : "0");
    }

    @PostMapping("smsUpdate")
    @ResponseBody
    public Map<String, Object> smsUpdate(HttpSession session, @RequestBody SmsTemplate payload) {
        String inst = ensureInst(session);
        if (inst == null) {
            return Map.of("result", "0", "message", "세션 만료");
        }
        payload.setInst(inst);
        int result = ss.smsUpdate(payload);
        return Map.of("result", result > 0 ? "1" : "0");
    }

    @PostMapping("smsDelete")
    @ResponseBody
    public Map<String, Object> smsDelete(HttpSession session, @RequestParam("id") int id) {
        String inst = ensureInst(session);
        if (inst == null) {
            return Map.of("result", "0", "message", "세션 만료");
        }
        int result = ss.smsDelete(inst, id);
        return Map.of("result", result > 0 ? "1" : "0");
    }

    @GetMapping(value = { "cardsetting", "/cardsetting" }, params = "!requestType", produces = "text/html")
    public String cardSettingPage(
            Model model,
            HttpSession session,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "perPageNum", defaultValue = "10") int perPageNum,
            @RequestParam(value = "comment", defaultValue = "") String comment) {
        String inst = ensureInst(session);
        if (inst == null) {
            return "redirect:/login";
        }
        Userdata userinfo = ensureUserInfo(session, inst);
        model.addAttribute("info", userinfo);
        model.addAttribute("page", page);
        model.addAttribute("perPageNum", perPageNum);
        model.addAttribute("comment", comment);
        model.addAttribute("endVar", "on");
        model.addAttribute("st", "");
        model.addAttribute("kw", "");

        Criteria cri = new Criteria();
        cri.setInst(inst);
        cri.setPage(page);
        cri.setPerPageNum(perPageNum);
        cri.setKeyword(comment);
        model.addAttribute("card", cs.SelectCardSearch(cri));
        return "csm/sms/cardsetting";
    }

    @GetMapping(value = { "cardsetting",
            "/cardsetting" }, params = "requestType=json", produces = "application/json;charset=UTF-8")
    @ResponseBody
    public Map<String, Object> cardSettingJson(
            HttpServletRequest request,
            HttpSession session,
            @RequestParam(value = "comment", defaultValue = "") String comment,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "perPageNum", defaultValue = "10") int perPageNum) {
        String inst = ensureInst(session);
        if (inst == null) {
            return Map.of("success", false, "redirect", loginRedirectPath(request));
        }

        Criteria cri = new Criteria();
        cri.setInst(inst);
        cri.setKeyword(comment);
        cri.setPage(page);
        cri.setPerPageNum(perPageNum);

        int total = cs.cardCnt(cri);
        List<Card> cards = cs.SelectCardSearch(cri);
        boolean hasMore = (page * perPageNum) < total;

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("card", cards != null ? cards : Collections.emptyList());
        response.put("hasMore", hasMore);
        return response;
    }

    @PostMapping("InsertCard")
    @ResponseBody
    public Map<String, Object> insertCard(HttpSession session, @RequestBody Card card) {
        String inst = ensureInst(session);
        if (inst == null) {
            return Map.of("result", "0", "message", "세션 만료");
        }
        card.setInst(inst);
        int result = cs.InsertCard(card);
        return Map.of("result", result > 0 ? "1" : "0");
    }

    @PostMapping("UpdateCard")
    @ResponseBody
    public Map<String, Object> updateCard(HttpSession session, @RequestBody Card card) {
        String inst = ensureInst(session);
        if (inst == null) {
            return Map.of("result", "0", "message", "세션 만료");
        }
        card.setInst(inst);
        int result = cs.UpdateCard(card);
        return Map.of("result", result > 0 ? "1" : "0");
    }

    @PostMapping("DeleteCard")
    @ResponseBody
    public Map<String, Object> deleteCard(HttpSession session, @RequestBody Card card) {
        String inst = ensureInst(session);
        if (inst == null) {
            return Map.of("result", "0", "message", "세션 만료");
        }
        card.setInst(inst);
        int result = cs.DeleteCard(card);
        return Map.of("result", result > 0 ? "1" : "0");
    }

    @GetMapping(value = { "smslog", "/smslog", "smslog/", "/smslog/" }, params = "!requestType", produces = "text/html")
    public String smsLogPage(
            Model model,
            HttpSession session,
            @RequestParam(value = "keyword", defaultValue = "") String keyword,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "perPageNum", defaultValue = "10") int perPageNum,
            @RequestParam(value = "fail", defaultValue = "") String fail) {
        String inst = ensureInst(session);
        if (inst == null) {
            return "redirect:/login";
        }

        Criteria cri = new Criteria();
        cri.setInst(inst);
        cri.setKeyword(keyword);
        cri.setPage(page);
        cri.setPerPageNum(perPageNum);
        cri.setFail(fail);

        int smsCnt = ss.smsCnt(cri);
        List<Map<String, Object>> smsHistory = ss.selectTransmissionHistory(cri);
        Paging pageMaker = new Paging();
        pageMaker.setCri(cri);
        pageMaker.setTotalCount(smsCnt);

        Userdata userinfo = ensureUserInfo(session, inst);
        model.addAttribute("info", userinfo);
        model.addAttribute("endVar", "on");
        model.addAttribute("st", "");
        model.addAttribute("kw", "");
        model.addAttribute("cri", cri);
        model.addAttribute("cnt", smsCnt);
        model.addAttribute("pageMaker", pageMaker);
        model.addAttribute("keyword", keyword);
        model.addAttribute("page", page);
        model.addAttribute("perPageNum", perPageNum);
        model.addAttribute("smsHistory", smsHistory);
        return "csm/sms/smslog";
    }

    @GetMapping(value = { "smslog",
            "/smslog" }, params = "requestType=json", produces = "application/json;charset=UTF-8")
    @ResponseBody
    public Map<String, Object> smsLogJson(
            HttpServletRequest request,
            HttpSession session,
            @RequestParam(value = "keyword", defaultValue = "") String keyword,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "perPageNum", defaultValue = "10") int perPageNum,
            @RequestParam(value = "fail", defaultValue = "") String fail) {
        String inst = ensureInst(session);
        if (inst == null) {
            return Map.of("success", false, "redirect", loginRedirectPath(request));
        }

        Criteria cri = new Criteria();
        cri.setInst(inst);
        cri.setKeyword(keyword);
        cri.setPage(page);
        cri.setPerPageNum(perPageNum);
        cri.setFail(fail);
        int smsCnt = ss.smsCnt(cri);
        List<Map<String, Object>> smsHistory = ss.selectTransmissionHistory(cri);
        boolean hasMore = (page * perPageNum) < smsCnt;

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("smsHistory", smsHistory);
        response.put("hasMore", hasMore);
        return response;
    }

    /**
     * 상담 화면 문자 전송 이력 조회(API)
     * 프론트(new.js)에서 POST /sms/log 호출을 사용
     */
    @PostMapping(value = {
            "sms/log", "/sms/log",
            "counsel/sms/log", "/counsel/sms/log"
    }, produces = "application/json;charset=UTF-8")
    @ResponseBody
    public List<Map<String, Object>> smsLogForCounsel(
            HttpSession session,
            @RequestBody(required = false) Map<String, Object> payload) {
        String inst = ensureInst(session);
        if (inst == null) {
            return Collections.emptyList();
        }
        @SuppressWarnings("unchecked")
        List<String> toPhones = payload != null && payload.get("to_phone") instanceof List
                ? ((List<Object>) payload.get("to_phone")).stream()
                        .filter(Objects::nonNull)
                        .map(String::valueOf)
                        .filter(s -> !s.isBlank())
                        .toList()
                : Collections.emptyList();

        log.info("[sms/log] inst={}, toPhones.size={}", inst, toPhones.size());
        if (toPhones.isEmpty()) {
            return Collections.emptyList();
        }
        return Optional.ofNullable(ss.getSmsLogs(inst, toPhones)).orElseGet(Collections::emptyList);
    }

    /**
     * 상담 화면 문자 전송 API
     * 현재 프로젝트에는 외부 문자 게이트웨이 연동 구현이 없어 명시적 실패 응답 반환
     */
    @PostMapping(value = {
            "api/external/sendSMS", "/api/external/sendSMS",
            "counsel/api/external/sendSMS", "/counsel/api/external/sendSMS"
    }, produces = "application/json;charset=UTF-8")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> relaySendSms(
            HttpSession session,
            @RequestBody(required = false) Map<String, Object> payload) {
        String inst = ensureInst(session);
        if (inst == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of(
                            "description", "fail",
                            "message", "세션이 만료되었습니다."));
        }
        if (payload == null || payload.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "description", "fail",
                    "message", "요청 payload가 비어 있습니다."));
        }

        String type = Optional.ofNullable(payload.get("type")).map(String::valueOf).orElse("sms")
                .toLowerCase(Locale.ROOT);
        String from = Optional.ofNullable(payload.get("from")).map(String::valueOf).orElse("");
        String to = Optional.ofNullable(payload.get("to")).map(String::valueOf).orElse("");
        String requestRefkey = buildSmsRefkey(inst,
                Optional.ofNullable(payload.get("refkey")).map(String::valueOf).orElse(""));
        payload.put("refkey", requestRefkey);

        String message = "";
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> content = payload.get("content") instanceof Map
                    ? (Map<String, Object>) payload.get("content")
                    : Map.of();
            @SuppressWarnings("unchecked")
            Map<String, Object> typed = content.get(type) instanceof Map ? (Map<String, Object>) content.get(type)
                    : Map.of();
            message = Optional.ofNullable(typed.get("message")).map(String::valueOf).orElse("");
        } catch (Exception ignore) {
            message = "";
        }

        try {
            Map<String, Object> resp = externalSmsGatewayService.send(payload);
            String desc = Optional.ofNullable(resp.get("description")).map(String::valueOf).orElse("");
            boolean success = "success".equalsIgnoreCase(desc);

            ss.insertTransmissionHistory(
                    inst,
                    message,
                    from,
                    to,
                    success ? "SUCCESS" : "FAILURE",
                    String.valueOf(resp.getOrDefault("_raw", resp.toString())),
                    requestRefkey,
                    type);

            HttpStatus status = success ? HttpStatus.OK : HttpStatus.BAD_GATEWAY;
            return ResponseEntity.status(status).body(resp);
        } catch (Exception e) {
            log.error("[api/external/sendSMS] send fail inst={}, to={}", inst, to, e);
            try {
                ss.insertTransmissionHistory(
                        inst,
                        message,
                        from,
                        to,
                        "FAILURE",
                        e.getMessage(),
                        requestRefkey,
                        type);
            } catch (Exception historyEx) {
                log.error("[api/external/sendSMS] history insert fail inst={}", inst, historyEx);
            }
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                    "description", "fail",
                    "message", "문자 전송 중 오류 발생: " + e.getMessage()));
        }
    }

    @PostMapping(value = { "sms/template/save", "/sms/template/save" }, produces = "text/plain;charset=UTF-8")
    @ResponseBody
    public ResponseEntity<String> saveTemplate(HttpSession session, @RequestBody SmsTemplate request) {
        String inst = ensureInst(session);
        if (inst == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("세션이 만료되었습니다.");
        }
        if (request.getTitle() == null || request.getTitle().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("템플릿 제목을 입력해주세요.");
        }
        if (request.getTemplate() == null || request.getTemplate().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("템플릿 내용을 입력해주세요.");
        }
        ss.saveTemplate(inst, request.getTitle(), request.getTemplate());
        return ResponseEntity.ok("상용구가 저장되었습니다.");
    }

    @PostMapping(value = { "sms/sendSMS", "/sms/sendSMS" }, produces = "text/plain;charset=UTF-8")
    @ResponseBody
    public ResponseEntity<?> sendSmsByLegacyContract(@RequestBody Map<String, Object> payload) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> content = (Map<String, Object>) payload.get("content");
            String type = Optional.ofNullable(payload.get("type")).map(String::valueOf).orElse("")
                    .toUpperCase(Locale.ROOT);
            @SuppressWarnings("unchecked")
            Map<String, Object> inner = content == null ? null
                    : (Map<String, Object>) content.get(type.toLowerCase(Locale.ROOT));
            if (inner == null) {
                return ResponseEntity.badRequest().body("content." + type.toLowerCase(Locale.ROOT) + " 파라미터가 없습니다.");
            }

            Map<String, Object> response = externalSmsGatewayService.send(payload);
            String raw = String.valueOf(response.getOrDefault("_raw", response.toString()));
            String desc = Optional.ofNullable(response.get("description")).map(String::valueOf).orElse("");
            if ("success".equalsIgnoreCase(desc)) {
                return ResponseEntity.ok(raw);
            }
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(raw);
        } catch (Exception e) {
            log.error("[sms/sendSMS] send fail", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("문자 전송 중 오류 발생: " + e.getMessage());
        }
    }

    @PostMapping(value = { "api/external/SMSRequest",
            "/api/external/SMSRequest" }, produces = "application/json;charset=UTF-8")
    @ResponseBody
    public ResponseEntity<?> smsRequestCallback(
            @RequestBody(required = false) Map<String, Object> requestBody,
            @RequestParam(required = false) Map<String, String> requestParams) {
        Map<String, Object> payload = new HashMap<>();
        if (requestBody != null) {
            payload.putAll(requestBody);
        }
        if (requestParams != null) {
            requestParams.forEach((k, v) -> {
                if (!payload.containsKey(k)) {
                    payload.put(k, v);
                }
            });
        }
        Map<String, Object> normalized = normalizeCallbackPayload(payload);

        String refkey = readAsString(normalized, "refkey", "ref_key").trim();
        if (refkey.isBlank()) {
            log.warn("[api/external/SMSRequest] callback ignored: empty refkey, payloadKeys={}", payload.keySet());
            return ResponseEntity.ok().build();
        }
        String inst = refkey.length() >= 4 ? refkey.substring(0, 4) : "";
        String resultCode = readAsString(normalized, "result", "resultcode", "result_code", "code", "status").trim();
        String status;
        if ("4100".equals(resultCode) || "6600".equals(resultCode)) {
            status = "전송완료";
        } else if (resultCode.isBlank()) {
            // 콜백 포맷 차이로 코드가 비어도 즉시 실패로 단정하지 않는다.
            status = "전송중";
        } else {
            status = "전송실패";
        }
        log.info("[api/external/SMSRequest] callback received inst={}, refkey={}, resultCode={}, status={}", inst,
                refkey, resultCode, status);

        try {
            int updated = ss.updateMessageHistoryStatus(inst, refkey, status);
            if (updated == 0) {
                log.warn("[api/external/SMSRequest] no history row updated. inst={}, refkey={}", inst, refkey);
            }
        } catch (Exception e) {
            log.error("[api/external/SMSRequest] history update fail inst={}, refkey={}", inst, refkey, e);
        }

        try {
            String device = readAsString(normalized, "device");
            String cmsgId = readAsString(normalized, "cmsgid", "cmsg_id");
            String msgId = readAsString(normalized, "msgid", "msg_id");
            String phone = readAsString(normalized, "phone");
            String media = readAsString(normalized, "media");
            String toName = readAsString(normalized, "to_name", "toname");
            String unixTime = readAsString(normalized, "time", "unix_time", "unixtime");

            boolean hasAnyDetail = !(device.isBlank() && cmsgId.isBlank() && msgId.isBlank()
                    && phone.isBlank() && media.isBlank() && resultCode.isBlank());
            if (!hasAnyDetail) {
                log.warn("[api/external/SMSRequest] callback detail empty. skip insert. refkey={}", refkey);
            } else {
                ss.insertSendResult(inst, device, cmsgId, msgId, phone, media, toName, unixTime, resultCode, refkey);
            }
        } catch (Exception e) {
            log.error("[api/external/SMSRequest] save callback fail inst={}, refkey={}", inst, refkey, e);
        }
        return ResponseEntity.ok().build();
    }

    @GetMapping(value = { "rate", "/rate", "rate/", "/rate/" }, produces = "text/html")
    public String ratePage(Model model, HttpSession session) {
        String inst = ensureInst(session);
        if (inst == null) {
            return "redirect:/login";
        }
        Userdata userinfo = ensureUserInfo(session, inst);
        model.addAttribute("info", userinfo);
        model.addAttribute("endVar", "on");
        model.addAttribute("st", "");
        model.addAttribute("kw", "");

        Map<String, Integer> usage = ss.getSendTypeUsage(inst);
        List<Instdata> iddata = ss.price(inst);
        List<Map<String, Object>> monthlyUsage = ss.getMonthlyUsage(inst);
        Instdata price = iddata.isEmpty() ? new Instdata() : iddata.get(0);

        double smsPrice = parseDouble(price.getSms_price());
        double lmsPrice = parseDouble(price.getLms_price());
        double mmsPrice = parseDouble(price.getMms_price());
        int smsCount = usage.getOrDefault("sms", 0);
        int lmsCount = usage.getOrDefault("lms", 0);
        int mmsCount = usage.getOrDefault("mms", 0);

        double smsTotal = smsCount * smsPrice;
        double lmsTotal = lmsCount * lmsPrice;
        double mmsTotal = mmsCount * mmsPrice;
        double total = smsTotal + lmsTotal + mmsTotal;

        Map<String, Integer> thisMonthUsage = ss.getSendTypeUsageByMonth(inst, LocalDate.now());
        Map<String, Integer> lastMonthUsage = ss.getSendTypeUsageByMonth(inst, LocalDate.now().minusMonths(1));
        int totalThisMonth = thisMonthUsage.values().stream().mapToInt(Integer::intValue).sum();
        int totalLastMonth = lastMonthUsage.values().stream().mapToInt(Integer::intValue).sum();

        int smsThisMonth = thisMonthUsage.getOrDefault("sms", 0);
        int lmsThisMonth = thisMonthUsage.getOrDefault("lms", 0);
        int mmsThisMonth = thisMonthUsage.getOrDefault("mms", 0);
        double smsTotalThisMonth = smsThisMonth * smsPrice;
        double lmsTotalThisMonth = lmsThisMonth * lmsPrice;
        double mmsTotalThisMonth = mmsThisMonth * mmsPrice;
        double thisMonthTotal = smsTotalThisMonth + lmsTotalThisMonth + mmsTotalThisMonth;

        model.addAttribute("thisMonthUsage", thisMonthUsage);
        model.addAttribute("lastMonthUsage", lastMonthUsage);
        model.addAttribute("totalThisMonth", totalThisMonth);
        model.addAttribute("totalLastMonth", totalLastMonth);
        model.addAttribute("smsThisMonth", smsThisMonth);
        model.addAttribute("lmsThisMonth", lmsThisMonth);
        model.addAttribute("mmsThisMonth", mmsThisMonth);
        model.addAttribute("smsTotalThisMonth", smsTotalThisMonth);
        model.addAttribute("lmsTotalThisMonth", lmsTotalThisMonth);
        model.addAttribute("mmsTotalThisMonth", mmsTotalThisMonth);
        model.addAttribute("thisMonthTotal", thisMonthTotal);
        model.addAttribute("monthlyUsage", monthlyUsage);
        model.addAttribute("smsPrice", smsPrice);
        model.addAttribute("lmsPrice", lmsPrice);
        model.addAttribute("mmsPrice", mmsPrice);
        model.addAttribute("smsCount", smsCount);
        model.addAttribute("lmsCount", lmsCount);
        model.addAttribute("mmsCount", mmsCount);
        model.addAttribute("smsTotal", smsTotal);
        model.addAttribute("lmsTotal", lmsTotal);
        model.addAttribute("mmsTotal", mmsTotal);
        model.addAttribute("total", total);
        model.addAttribute("list", usage);
        model.addAttribute("data", iddata);

        return "csm/sms/rate";
    }

    @GetMapping(value = { "monthlyUsage", "/monthlyUsage" })
    @ResponseBody
    public Object monthlyUsage(
            @RequestParam(value = "month", defaultValue = "all") String month,
            HttpSession session) {
        String inst = ensureInst(session);
        if (inst == null) {
            return Collections.emptyList();
        }
        if ("all".equals(month)) {
            List<Map<String, Object>> resultList = new ArrayList<>();
            List<Map<String, Object>> monthlyList = ss.getMonthlyUsage(inst);
            List<Instdata> prices = ss.price(inst);
            Instdata price = prices.isEmpty() ? new Instdata() : prices.get(0);
            double smsPrice = parseDouble(price.getSms_price());
            double lmsPrice = parseDouble(price.getLms_price());
            double mmsPrice = parseDouble(price.getMms_price());

            for (Map<String, Object> m : monthlyList) {
                String ym = String.valueOf(m.get("month"));
                int sms = ((Number) m.getOrDefault("sms", 0)).intValue();
                int lms = ((Number) m.getOrDefault("lms", 0)).intValue();
                int mms = ((Number) m.getOrDefault("mms", 0)).intValue();
                double total = sms * smsPrice + lms * lmsPrice + mms * mmsPrice;

                Map<String, Object> monthData = new HashMap<>();
                monthData.put("month", ym);
                monthData.put("sms", sms);
                monthData.put("lms", lms);
                monthData.put("mms", mms);
                monthData.put("smsPrice", smsPrice);
                monthData.put("lmsPrice", lmsPrice);
                monthData.put("mmsPrice", mmsPrice);
                monthData.put("total", total);
                resultList.add(monthData);
            }
            return resultList;
        }

        List<Instdata> prices = ss.price(inst);
        Instdata price = prices.isEmpty() ? new Instdata() : prices.get(0);
        double smsPrice = parseDouble(price.getSms_price());
        double lmsPrice = parseDouble(price.getLms_price());
        double mmsPrice = parseDouble(price.getMms_price());
        Map<String, Integer> usage = ss.getSendTypeUsageByMonth(inst, LocalDate.parse(month + "-01"));
        int sms = usage.getOrDefault("sms", 0);
        int lms = usage.getOrDefault("lms", 0);
        int mms = usage.getOrDefault("mms", 0);
        double total = sms * smsPrice + lms * lmsPrice + mms * mmsPrice;

        Map<String, Object> result = new HashMap<>();
        result.put("month", month);
        result.put("sms", sms);
        result.put("lms", lms);
        result.put("mms", mms);
        result.put("smsPrice", smsPrice);
        result.put("lmsPrice", lmsPrice);
        result.put("mmsPrice", mmsPrice);
        result.put("total", total);
        return result;
    }

    @PostMapping({ "counsel", "/counsel" })
    public String postCounsel(HttpServletRequest request, HttpSession session) {
        String inst = ensureInst(session);
        if (inst == null) {
            return "redirect:/login";
        }

        try {
            CounselData counselData = buildCounselDataFromRequest(request, inst);
            Integer csIdx = transactionTemplate.execute(status -> {
                int idx = cs.insertCounselData(counselData);
                if (idx <= 0) {
                    status.setRollbackOnly();
                    return 0;
                }
                saveAdmissionPledgeFromRequest(inst, idx, request);

                for (CounselDataEntry entry : parseDynamicEntries(request, inst)) {
                    entry.setCs_idx((long) idx);
                    cs.insertCounselDataEntry(inst, entry);
                }
                for (Guardian guardian : Optional.ofNullable(counselData.getGuardians()).orElse(Collections.emptyList())) {
                    guardian.setCs_idx((long) idx);
                    if (isBlank(guardian.getName()) && isBlank(guardian.getRelationship())
                            && isBlank(guardian.getContact_number())) {
                        continue;
                    }
                    cs.insertGuardian(inst, guardian);
                }
                saveCounselLogSnapshot(inst, idx, request);
                if (isModuleEnabled(inst, ModuleFeatureService.FEATURE_COUNSEL_AUDIO)) {
                    bindPendingCounselAudio(inst, idx, request);
                }
                if (isModuleEnabled(inst, ModuleFeatureService.FEATURE_COUNSEL_FILE)) {
                    bindPendingCounselFiles(inst, idx, request);
                }
                long reservationId = parseLongSafely(request.getParameter("reservation_id"), 0L);
                if (reservationId > 0) {
                    cs.linkReservationToCounsel(inst, reservationId, idx, resolveReservationActor(inst, session));
                }
                return idx;
            });
            if (csIdx == null || csIdx <= 0) {
                return "";
            }
            return "redirect:/counsel/list";
        } catch (IllegalArgumentException e) {
            log.warn("[counsel] validation fail inst={}, err={}", inst, e.getMessage());
            return "redirect:/counsel/new";
        } catch (Exception e) {
            log.error("[counsel] save fail inst={}", inst, e);
            return "";
        }
    }

    @PostMapping({ "counselupdate/{cs_idx}", "/counselupdate/{cs_idx}" })
    public String updateCounsel(
            @PathVariable("cs_idx") int csIdx,
            HttpServletRequest request,
            HttpSession session) {
        String inst = ensureInst(session);
        if (inst == null) {
            return "redirect:/login";
        }

        CounselData existing = cs.getCounselById(inst, csIdx);
        if (existing == null) {
            return "redirect:/counsel/list";
        }

        try {
            CounselData counselData = buildCounselDataFromRequest(request, inst);
            counselData.setCs_idx(csIdx);
            transactionTemplate.executeWithoutResult(status -> {
                cs.updateCounselData(counselData);
                saveAdmissionPledgeFromRequest(inst, csIdx, request);

                boolean hasDynamicFieldInput = request.getParameterMap().keySet().stream()
                        .anyMatch(k -> k != null && k.startsWith("field_"));
                if (hasDynamicFieldInput) {
                    cs.deleteCounselDataEntriesByCsIdx(inst, csIdx);
                    for (CounselDataEntry entry : parseDynamicEntries(request, inst)) {
                        entry.setCs_idx((long) csIdx);
                        cs.insertCounselDataEntry(inst, entry);
                    }
                }

                cs.deleteGuardiansByCsIdx(inst, csIdx);
                for (Guardian guardian : Optional.ofNullable(counselData.getGuardians()).orElse(Collections.emptyList())) {
                    guardian.setCs_idx((long) csIdx);
                    if (isBlank(guardian.getName()) && isBlank(guardian.getRelationship())
                            && isBlank(guardian.getContact_number())) {
                        continue;
                    }
                    cs.insertGuardian(inst, guardian);
                }
                saveCounselLogSnapshot(inst, csIdx, request);
                if (isModuleEnabled(inst, ModuleFeatureService.FEATURE_COUNSEL_AUDIO)) {
                    bindPendingCounselAudio(inst, csIdx, request);
                }
                if (isModuleEnabled(inst, ModuleFeatureService.FEATURE_COUNSEL_FILE)) {
                    bindPendingCounselFiles(inst, csIdx, request);
                }
                long reservationId = parseLongSafely(request.getParameter("reservation_id"), 0L);
                if (reservationId > 0) {
                    cs.linkReservationToCounsel(inst, reservationId, csIdx, resolveReservationActor(inst, session));
                }
            });
            return "redirect:/counsel/list";
        } catch (IllegalArgumentException e) {
            log.warn("[counselupdate] validation fail inst={}, cs_idx={}, err={}", inst, csIdx, e.getMessage());
            return "redirect:/counsel/new/" + csIdx;
        } catch (Exception e) {
            log.error("[counselupdate] update fail inst={}, cs_idx={}", inst, csIdx, e);
            return "";
        }
    }

    @PostMapping({ "counsel/delete/{cs_idx}", "/counsel/delete/{cs_idx}" })
    @ResponseBody
    public Map<String, Object> counselDelete(@PathVariable("cs_idx") int csIdx, HttpSession session) {
        String inst = ensureInst(session);
        if (inst == null) {
            return Map.of("result", "0", "message", "세션 만료");
        }
        int response = cs.counselDelete(inst, csIdx);
        if (response == 1) {
            return Map.of("result", "1");
        }
        return Map.of("result", "0", "message", "Delete failed");
    }

    @PostMapping(value = { "counsel/audio/upload",
            "/counsel/audio/upload" }, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseBody
    public ResponseEntity<?> uploadCounselAudio(
            @RequestParam("audio") MultipartFile audio,
            @RequestParam(value = "csIdx", required = false) Integer csIdx,
            @RequestParam(value = "tempKey", required = false) String tempKey,
            @RequestParam(value = "durationSeconds", required = false) Double durationSeconds,
            @RequestParam(value = "transcript", required = false) String transcript,
            HttpSession session,
            HttpServletRequest request) {
        String inst = ensureInst(session);
        if (inst == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("success", false, "message", "세션 만료"));
        }
        if (!isModuleEnabled(inst, ModuleFeatureService.FEATURE_COUNSEL_AUDIO)) {
            return featureDisabledResponse("녹음 기능이 비활성화되었습니다.");
        }
        if (audio == null || audio.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "업로드할 음성 파일이 없습니다."));
        }

        try {
            Path audioDir = resolveCounselAudioDir(inst);
            Files.createDirectories(audioDir);

            String originalFilename = Optional.ofNullable(audio.getOriginalFilename()).orElse("recording.webm");
            String normalizedMimeType = normalizeMimeType(audio.getContentType());
            String extension = resolveAudioFileExtension(originalFilename, normalizedMimeType);
            String storedFilename = buildStoredAudioFilename(extension);
            Path target = audioDir.resolve(storedFilename).normalize();

            if (!target.startsWith(audioDir)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(Map.of("success", false, "message", "파일 경로가 유효하지 않습니다."));
            }

            try (InputStream in = audio.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }

            Integer normalizedCsIdx = (csIdx != null && csIdx > 0) ? csIdx : null;
            String normalizedTempKey = normalizedCsIdx == null ? sanitizeTempKey(tempKey) : "";
            if (normalizedCsIdx == null && normalizedTempKey.isBlank()) {
                normalizedTempKey = buildFallbackTempKey();
            }

            String frontendTranscript = safeString(transcript).trim();
            String clovaTranscript = transcribeWithClova(target, normalizedMimeType);
            String finalTranscript = clovaTranscript.isBlank() ? frontendTranscript : clovaTranscript;
            String transcriptSource = !clovaTranscript.isBlank() ? "clova"
                    : (!frontendTranscript.isBlank() ? "browser" : "");
            if (normalizedCsIdx != null && normalizedCsIdx > 0 && !finalTranscript.isBlank()) {
                cs.appendCounselContentIfMissing(inst, normalizedCsIdx, finalTranscript);
            }

            Userdata info = (Userdata) session.getAttribute("userInfo");
            String createdBy = info == null ? "" : safeString(info.getUs_col_02()).trim();
            long audioId = cs.insertCounselAudio(
                    inst,
                    normalizedCsIdx,
                    normalizedTempKey,
                    originalFilename,
                    storedFilename,
                    normalizedMimeType,
                    audio.getSize(),
                    durationSeconds,
                    finalTranscript,
                    createdBy);

            if (audioId <= 0) {
                return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "음성 저장 실패"));
            }

            Map<String, Object> row = cs.getCounselAudioById(inst, audioId);
            if (row == null) {
                return ResponseEntity.ok(Map.of("success", true, "audioId", audioId));
            }

            Map<String, Object> payload = toCounselAudioPayload(row, request);
            payload.put("transcriptSource", transcriptSource);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "item", payload));
        } catch (Exception e) {
            log.error("[counsel/audio/upload] fail inst={}, csIdx={}", inst, csIdx, e);
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "녹음 파일 저장 중 오류"));
        }
    }

    @GetMapping({ "counsel/audio/list", "/counsel/audio/list" })
    @ResponseBody
    public ResponseEntity<?> listCounselAudio(
            @RequestParam(value = "csIdx", required = false) Integer csIdx,
            @RequestParam(value = "tempKey", required = false) String tempKey,
            HttpSession session,
            HttpServletRequest request) {
        String inst = ensureInst(session);
        if (inst == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("success", false, "message", "세션 만료"));
        }
        if (!isModuleEnabled(inst, ModuleFeatureService.FEATURE_COUNSEL_AUDIO)) {
            return featureDisabledResponse("녹음 기능이 비활성화되었습니다.");
        }

        try {
            String normalizedTempKey = sanitizeTempKey(tempKey);
            List<Map<String, Object>> rows = cs.getCounselAudioList(inst, csIdx, normalizedTempKey);
            enrichAudioTranscriptsIfNeeded(inst, rows);
            List<Map<String, Object>> payload = rows.stream()
                    .map(row -> toCounselAudioPayload(row, request))
                    .collect(Collectors.toList());
            return ResponseEntity.ok(Map.of("success", true, "list", payload));
        } catch (Exception e) {
            log.error("[counsel/audio/list] fail inst={}, csIdx={}", inst, csIdx, e);
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "목록 조회 실패"));
        }
    }

    @GetMapping({ "counsel/audio/stream/{audioId}", "/counsel/audio/stream/{audioId}" })
    public ResponseEntity<?> streamCounselAudio(
            @PathVariable("audioId") long audioId,
            HttpSession session) {
        String inst = ensureInst(session);
        if (inst == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("세션 만료");
        }
        if (!isModuleEnabled(inst, ModuleFeatureService.FEATURE_COUNSEL_AUDIO)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("녹음 기능이 비활성화되었습니다.");
        }
        if (audioId <= 0) {
            return ResponseEntity.badRequest().body("invalid audio id");
        }

        try {
            Map<String, Object> row = cs.getCounselAudioById(inst, audioId);
            if (row == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("audio not found");
            }

            String storedFilename = objectString(row.get("stored_filename")).trim();
            if (storedFilename.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("audio file not found");
            }

            Path baseDir = resolveCounselAudioDir(inst);
            Path file = baseDir.resolve(storedFilename).normalize();
            if (!file.startsWith(baseDir) || !Files.exists(file) || !Files.isRegularFile(file)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("audio file not found");
            }

            String mime = objectString(row.get("mime_type"));
            if (mime.isBlank()) {
                mime = Optional.ofNullable(Files.probeContentType(file)).orElse("application/octet-stream");
            }
            MediaType mediaType;
            try {
                mediaType = MediaType.parseMediaType(mime);
            } catch (Exception ignored) {
                mediaType = MediaType.APPLICATION_OCTET_STREAM;
            }

            InputStreamResource resource = new InputStreamResource(Files.newInputStream(file));
            String originalFilename = objectString(row.get("original_filename")).trim();
            if (originalFilename.isEmpty()) {
                originalFilename = storedFilename;
            }

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "inline; filename=\"" + sanitizeHeaderFilename(originalFilename) + "\"")
                    .contentType(mediaType)
                    .contentLength(Files.size(file))
                    .body(resource);
        } catch (Exception e) {
            log.error("[counsel/audio/stream] fail inst={}, audioId={}", inst, audioId, e);
            return ResponseEntity.internalServerError().body("audio read fail");
        }
    }

    @PostMapping({ "counsel/audio/delete/{audioId}", "/counsel/audio/delete/{audioId}" })
    @ResponseBody
    public ResponseEntity<?> deleteCounselAudio(
            @PathVariable("audioId") long audioId,
            HttpSession session) {
        String inst = ensureInst(session);
        if (inst == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("success", false, "message", "세션 만료"));
        }
        if (!isModuleEnabled(inst, ModuleFeatureService.FEATURE_COUNSEL_AUDIO)) {
            return featureDisabledResponse("녹음 기능이 비활성화되었습니다.");
        }
        if (audioId <= 0) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "invalid audio id"));
        }

        try {
            Map<String, Object> row = cs.getCounselAudioById(inst, audioId);
            if (row == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("success", false, "message", "not found"));
            }

            String storedFilename = objectString(row.get("stored_filename")).trim();
            Path baseDir = resolveCounselAudioDir(inst);
            Path file = storedFilename.isEmpty() ? null : baseDir.resolve(storedFilename).normalize();

            int dbDeleted = cs.deleteCounselAudioById(inst, audioId);
            boolean fileDeleted = false;
            if (file != null && file.startsWith(baseDir) && Files.exists(file)) {
                fileDeleted = Files.deleteIfExists(file);
            }

            return ResponseEntity.ok(Map.of("success", dbDeleted > 0, "fileDeleted", fileDeleted));
        } catch (Exception e) {
            log.error("[counsel/audio/delete] fail inst={}, audioId={}", inst, audioId, e);
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "삭제 중 오류"));
        }
    }

    @PostMapping({ "counsel/audio/retranscribe/{audioId}", "/counsel/audio/retranscribe/{audioId}" })
    @ResponseBody
    public ResponseEntity<?> retranscribeCounselAudio(
            @PathVariable("audioId") long audioId,
            HttpSession session,
            HttpServletRequest request) {
        String inst = ensureInst(session);
        if (inst == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("success", false, "message", "세션 만료"));
        }
        if (!isModuleEnabled(inst, ModuleFeatureService.FEATURE_COUNSEL_AUDIO)) {
            return featureDisabledResponse("녹음 기능이 비활성화되었습니다.");
        }
        if (audioId <= 0) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "invalid audio id"));
        }

        try {
            Map<String, Object> row = cs.getCounselAudioById(inst, audioId);
            if (row == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("success", false, "message", "not found"));
            }

            String storedFilename = objectString(row.get("stored_filename")).trim();
            if (storedFilename.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("success", false, "message", "audio file not found"));
            }

            Path baseDir = resolveCounselAudioDir(inst);
            Path file = baseDir.resolve(storedFilename).normalize();
            if (!file.startsWith(baseDir) || !Files.exists(file) || !Files.isRegularFile(file)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("success", false, "message", "audio file not found"));
            }

            String mimeType = objectString(row.get("mime_type"));
            String normalizedMimeType = normalizeMimeType(mimeType);
            String transcript = transcribeWithClova(file, normalizedMimeType);
            if (transcript.isBlank()) {
                if (normalizedMimeType.contains("webm")) {
                    return ResponseEntity.badRequest().body(Map.of(
                            "success", false,
                            "code", "unsupported_format",
                            "message", "webm 포맷은 텍스트 재변환을 지원하지 않습니다. mp4/ogg/wav로 다시 녹음해 주세요."));
                }
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                        "success", false,
                        "code", "stt_failed",
                        "message", "텍스트 변환에 실패했습니다. 잠시 후 다시 시도해 주세요."));
            }

            cs.updateCounselAudioTranscript(inst, audioId, transcript);
            int rowCsIdx = (int) parseLongObject(row.get("cs_idx"), 0L);
            if (rowCsIdx > 0) {
                cs.appendCounselContentIfMissing(inst, rowCsIdx, transcript);
            }

            Map<String, Object> updated = cs.getCounselAudioById(inst, audioId);
            if (updated == null) {
                return ResponseEntity.ok(Map.of("success", true, "audioId", audioId));
            }

            Map<String, Object> payload = toCounselAudioPayload(updated, request);
            payload.put("transcriptSource", "clova");
            return ResponseEntity.ok(Map.of("success", true, "item", payload));
        } catch (Exception e) {
            log.error("[counsel/audio/retranscribe] fail inst={}, audioId={}", inst, audioId, e);
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "재변환 중 오류"));
        }
    }

    @PostMapping(value = { "counsel/file/upload",
            "/counsel/file/upload" }, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseBody
    public ResponseEntity<?> uploadCounselFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "csIdx", required = false) Integer csIdx,
            @RequestParam(value = "tempKey", required = false) String tempKey,
            HttpSession session,
            HttpServletRequest request) {
        String inst = ensureInst(session);
        if (inst == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("success", false, "message", "세션 만료"));
        }
        if (!isModuleEnabled(inst, ModuleFeatureService.FEATURE_COUNSEL_FILE)) {
            return featureDisabledResponse("파일 업로드 기능이 비활성화되었습니다.");
        }
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "업로드할 파일이 없습니다."));
        }

        try {
            Path fileDir = resolveCounselFileDir(inst);
            Files.createDirectories(fileDir);

            String originalFilename = Optional.ofNullable(file.getOriginalFilename()).orElse("counsel-file");
            String extension = resolveGenericFileExtension(originalFilename);
            String storedFilename = buildStoredCounselFileFilename(extension);
            Path target = fileDir.resolve(storedFilename).normalize();
            if (!target.startsWith(fileDir)) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", "파일 경로가 유효하지 않습니다."));
            }

            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }

            Integer normalizedCsIdx = (csIdx != null && csIdx > 0) ? csIdx : null;
            String normalizedTempKey = normalizedCsIdx == null ? sanitizeTempKey(tempKey) : "";
            if (normalizedCsIdx == null && normalizedTempKey.isBlank()) {
                normalizedTempKey = buildFallbackTempKey();
            }

            Userdata info = (Userdata) session.getAttribute("userInfo");
            String createdBy = info == null ? "" : safeString(info.getUs_col_02()).trim();
            String mimeType = normalizeMimeType(file.getContentType());
            if (mimeType.isBlank()) {
                mimeType = Optional.ofNullable(Files.probeContentType(target)).orElse("application/octet-stream");
            }
            long fileId = cs.insertCounselFile(
                    inst,
                    normalizedCsIdx,
                    normalizedTempKey,
                    originalFilename,
                    storedFilename,
                    mimeType,
                    file.getSize(),
                    createdBy);
            if (fileId <= 0) {
                return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "파일 저장 실패"));
            }

            Map<String, Object> row = cs.getCounselFileById(inst, fileId);
            if (row == null) {
                return ResponseEntity.ok(Map.of("success", true, "fileId", fileId));
            }
            Map<String, Object> payload = toCounselFilePayload(row, request);
            return ResponseEntity.ok(Map.of("success", true, "item", payload));
        } catch (Exception e) {
            log.error("[counsel/file/upload] fail inst={}, csIdx={}", inst, csIdx, e);
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "파일 저장 중 오류"));
        }
    }

    @GetMapping({ "counsel/file/list", "/counsel/file/list" })
    @ResponseBody
    public ResponseEntity<?> listCounselFile(
            @RequestParam(value = "csIdx", required = false) Integer csIdx,
            @RequestParam(value = "tempKey", required = false) String tempKey,
            HttpSession session,
            HttpServletRequest request) {
        String inst = ensureInst(session);
        if (inst == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("success", false, "message", "세션 만료"));
        }
        if (!isModuleEnabled(inst, ModuleFeatureService.FEATURE_COUNSEL_FILE)) {
            return featureDisabledResponse("파일 업로드 기능이 비활성화되었습니다.");
        }
        try {
            String normalizedTempKey = sanitizeTempKey(tempKey);
            List<Map<String, Object>> rows = cs.getCounselFileList(inst, csIdx, normalizedTempKey);
            List<Map<String, Object>> payload = rows.stream()
                    .map(row -> toCounselFilePayload(row, request))
                    .collect(Collectors.toList());
            return ResponseEntity.ok(Map.of("success", true, "list", payload));
        } catch (Exception e) {
            log.error("[counsel/file/list] fail inst={}, csIdx={}", inst, csIdx, e);
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "목록 조회 실패"));
        }
    }

    @GetMapping({ "counsel/file/download/{fileId}", "/counsel/file/download/{fileId}" })
    public ResponseEntity<?> downloadCounselFile(
            @PathVariable("fileId") long fileId,
            HttpSession session) {
        String inst = ensureInst(session);
        if (inst == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("세션 만료");
        }
        if (!isModuleEnabled(inst, ModuleFeatureService.FEATURE_COUNSEL_FILE)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("파일 업로드 기능이 비활성화되었습니다.");
        }
        if (fileId <= 0) {
            return ResponseEntity.badRequest().body("invalid file id");
        }

        try {
            Map<String, Object> row = cs.getCounselFileById(inst, fileId);
            if (row == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("file not found");
            }

            String storedFilename = objectString(row.get("stored_filename")).trim();
            if (storedFilename.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("file not found");
            }

            Path baseDir = resolveCounselFileDir(inst);
            Path file = baseDir.resolve(storedFilename).normalize();
            if (!file.startsWith(baseDir) || !Files.exists(file) || !Files.isRegularFile(file)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("file not found");
            }

            String mime = objectString(row.get("mime_type")).trim();
            if (mime.isBlank()) {
                mime = Optional.ofNullable(Files.probeContentType(file)).orElse("application/octet-stream");
            }
            MediaType mediaType;
            try {
                mediaType = MediaType.parseMediaType(mime);
            } catch (Exception ignored) {
                mediaType = MediaType.APPLICATION_OCTET_STREAM;
            }

            String originalFilename = objectString(row.get("original_filename")).trim();
            if (originalFilename.isEmpty()) {
                originalFilename = storedFilename;
            }

            InputStreamResource resource = new InputStreamResource(Files.newInputStream(file));
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"" + sanitizeHeaderFilename(originalFilename) + "\"")
                    .contentType(mediaType)
                    .contentLength(Files.size(file))
                    .body(resource);
        } catch (Exception e) {
            log.error("[counsel/file/download] fail inst={}, fileId={}", inst, fileId, e);
            return ResponseEntity.internalServerError().body("file download fail");
        }
    }

    @PostMapping({ "counsel/file/delete/{fileId}", "/counsel/file/delete/{fileId}" })
    @ResponseBody
    public ResponseEntity<?> deleteCounselFile(
            @PathVariable("fileId") long fileId,
            HttpSession session) {
        String inst = ensureInst(session);
        if (inst == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("success", false, "message", "세션 만료"));
        }
        if (!isModuleEnabled(inst, ModuleFeatureService.FEATURE_COUNSEL_FILE)) {
            return featureDisabledResponse("파일 업로드 기능이 비활성화되었습니다.");
        }
        if (fileId <= 0) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "invalid file id"));
        }

        try {
            Map<String, Object> row = cs.getCounselFileById(inst, fileId);
            if (row == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("success", false, "message", "not found"));
            }

            String storedFilename = objectString(row.get("stored_filename")).trim();
            Path baseDir = resolveCounselFileDir(inst);
            Path file = storedFilename.isEmpty() ? null : baseDir.resolve(storedFilename).normalize();

            int dbDeleted = cs.deleteCounselFileById(inst, fileId);
            boolean fileDeleted = false;
            if (file != null && file.startsWith(baseDir) && Files.exists(file)) {
                fileDeleted = Files.deleteIfExists(file);
            }

            return ResponseEntity.ok(Map.of("success", dbDeleted > 0, "fileDeleted", fileDeleted));
        } catch (Exception e) {
            log.error("[counsel/file/delete] fail inst={}, fileId={}", inst, fileId, e);
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "삭제 중 오류"));
        }
    }

    @PostMapping({ "counsel/file/extract/{fileId}", "/counsel/file/extract/{fileId}" })
    @ResponseBody
    public ResponseEntity<?> extractCounselFileText(
            @PathVariable("fileId") long fileId,
            HttpSession session) {
        String inst = ensureInst(session);
        if (inst == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("success", false, "message", "세션 만료"));
        }
        if (!isModuleEnabled(inst, ModuleFeatureService.FEATURE_COUNSEL_FILE)) {
            return featureDisabledResponse("파일 업로드 기능이 비활성화되었습니다.");
        }
        if (fileId <= 0) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "invalid file id"));
        }

        try {
            Map<String, Object> row = cs.getCounselFileById(inst, fileId);
            if (row == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("success", false, "message", "not found"));
            }

            String storedFilename = objectString(row.get("stored_filename")).trim();
            if (storedFilename.isEmpty()) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("success", false, "message", "file not found"));
            }

            Path baseDir = resolveCounselFileDir(inst);
            Path file = baseDir.resolve(storedFilename).normalize();
            if (!file.startsWith(baseDir) || !Files.exists(file) || !Files.isRegularFile(file)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("success", false, "message", "file not found"));
            }

            String originalFilename = objectString(row.get("original_filename"));
            String mimeType = objectString(row.get("mime_type"));
            String extractedText = extractTextFromCounselFile(file, originalFilename, mimeType);
            if (extractedText.isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(Map.of(
                        "success", false,
                        "message", "문서에서 텍스트를 추출하지 못했습니다."));
            }
            cs.updateCounselFileExtractedText(inst, fileId, extractedText);

            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "text", extractedText));
        } catch (Exception e) {
            log.error("[counsel/file/extract] fail inst={}, fileId={}", inst, fileId, e);
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "텍스트 추출 중 오류"));
        }
    }

    @PostMapping({ "counsel/summary/generate", "/counsel/summary/generate" })
    @ResponseBody
    public ResponseEntity<?> generateCounselSummary(
            @RequestBody Map<String, Object> requestBody,
            HttpSession session) {
        String inst = ensureInst(session);
        if (inst == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("success", false, "message", "세션 만료"));
        }

        String rawContent = objectString(requestBody == null ? null : requestBody.get("content")).trim();
        if (rawContent.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "요약할 상담 내용이 없습니다."));
        }
        if (openAiApiKey == null || openAiApiKey.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                    "success", false,
                    "message", "OpenAI API 키가 설정되지 않았습니다."));
        }

        OpenAiSummaryResult summaryResult = summarizeCounselWithOpenAi(rawContent);
        if (!summaryResult.success()) {
            return ResponseEntity.status(summaryResult.status()).body(Map.of(
                    "success", false,
                    "message", summaryResult.message()));
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "summary", summaryResult.summary(),
                "provider", "openai"));
    }

    @PostMapping({ "counsel/audio/summary/{audioId}", "/counsel/audio/summary/{audioId}" })
    @ResponseBody
    public ResponseEntity<?> generateCounselAudioSummary(
            @PathVariable("audioId") long audioId,
            HttpSession session,
            HttpServletRequest request) {
        String inst = ensureInst(session);
        if (inst == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("success", false, "message", "세션 만료"));
        }
        if (!isModuleEnabled(inst, ModuleFeatureService.FEATURE_COUNSEL_AUDIO)) {
            return featureDisabledResponse("녹음 기능이 비활성화되었습니다.");
        }
        if (audioId <= 0) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "invalid audio id"));
        }

        try {
            Map<String, Object> row = cs.getCounselAudioById(inst, audioId);
            if (row == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("success", false, "message", "not found"));
            }

            String currentSummary = objectString(row.get("summary_text")).trim();
            if (!currentSummary.isBlank()) {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "summary", currentSummary,
                        "item", toCounselAudioPayload(row, request),
                        "cached", true));
            }

            String transcript = objectString(row.get("transcript")).trim();
            if (transcript.isBlank()) {
                String storedFilename = objectString(row.get("stored_filename")).trim();
                String normalizedMimeType = normalizeMimeType(objectString(row.get("mime_type")));
                if (!storedFilename.isBlank()) {
                    Path baseDir = resolveCounselAudioDir(inst);
                    Path file = baseDir.resolve(storedFilename).normalize();
                    if (file.startsWith(baseDir) && Files.exists(file) && Files.isRegularFile(file)) {
                        String regenerated = transcribeWithClova(file, normalizedMimeType);
                        if (!regenerated.isBlank()) {
                            transcript = regenerated;
                            cs.updateCounselAudioTranscript(inst, audioId, regenerated);
                            int rowCsIdx = (int) parseLongObject(row.get("cs_idx"), 0L);
                            if (rowCsIdx > 0) {
                                cs.appendCounselContentIfMissing(inst, rowCsIdx, regenerated);
                            }
                            Map<String, Object> refreshed = cs.getCounselAudioById(inst, audioId);
                            if (refreshed != null) {
                                row = refreshed;
                            }
                        } else if (normalizedMimeType.contains("webm")) {
                            return ResponseEntity.badRequest().body(Map.of(
                                    "success", false,
                                    "code", "unsupported_format",
                                    "message", "webm 포맷은 요약 전 텍스트 변환을 지원하지 않습니다. mp4/ogg/wav로 다시 업로드해 주세요."));
                        }
                    }
                }
            }
            if (transcript.isBlank()) {
                String msg = isClovaSpeechConfigured()
                        ? "요약할 녹취 원문이 없습니다. 텍스트 재시도를 먼저 실행해 주세요."
                        : "음성 전사 설정이 없어 요약할 녹취 원문을 만들 수 없습니다.";
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", msg));
            }
            if (openAiApiKey == null || openAiApiKey.isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                        "success", false,
                        "message", "OpenAI API 키가 설정되지 않았습니다."));
            }

            String filename = objectString(row.get("original_filename")).trim();
            OpenAiSummaryResult summaryResult = summarizeAttachmentWithOpenAi("녹취", transcript, filename);
            if (!summaryResult.success()) {
                return ResponseEntity.status(summaryResult.status()).body(Map.of(
                        "success", false,
                        "message", summaryResult.message()));
            }

            cs.updateCounselAudioSummary(inst, audioId, summaryResult.summary());
            Map<String, Object> updated = cs.getCounselAudioById(inst, audioId);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "summary", summaryResult.summary(),
                    "item", toCounselAudioPayload(updated, request),
                    "cached", false));
        } catch (Exception e) {
            log.error("[counsel/audio/summary] fail inst={}, audioId={}", inst, audioId, e);
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "요약 생성 중 오류"));
        }
    }

    @PostMapping({ "counsel/file/summary/{fileId}", "/counsel/file/summary/{fileId}" })
    @ResponseBody
    public ResponseEntity<?> generateCounselFileSummary(
            @PathVariable("fileId") long fileId,
            HttpSession session,
            HttpServletRequest request) {
        String inst = ensureInst(session);
        if (inst == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("success", false, "message", "세션 만료"));
        }
        if (!isModuleEnabled(inst, ModuleFeatureService.FEATURE_COUNSEL_FILE)) {
            return featureDisabledResponse("파일 업로드 기능이 비활성화되었습니다.");
        }
        if (fileId <= 0) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "invalid file id"));
        }

        try {
            Map<String, Object> row = cs.getCounselFileById(inst, fileId);
            if (row == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("success", false, "message", "not found"));
            }

            String currentSummary = objectString(row.get("summary_text")).trim();
            if (!currentSummary.isBlank()) {
                return ResponseEntity.ok(Map.of(
                        "success", true,
                        "summary", currentSummary,
                        "item", toCounselFilePayload(row, request),
                        "cached", true));
            }

            String extractedText = objectString(row.get("extracted_text")).trim();
            if (extractedText.isBlank()) {
                String storedFilename = objectString(row.get("stored_filename")).trim();
                Path baseDir = resolveCounselFileDir(inst);
                Path file = storedFilename.isEmpty() ? null : baseDir.resolve(storedFilename).normalize();
                if (file == null || !file.startsWith(baseDir) || !Files.exists(file) || !Files.isRegularFile(file)) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(Map.of("success", false, "message", "file not found"));
                }
                extractedText = extractTextFromCounselFile(
                        file,
                        objectString(row.get("original_filename")),
                        objectString(row.get("mime_type")));
                if (extractedText.isBlank()) {
                    return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                            .body(Map.of("success", false, "message", "문서에서 텍스트를 추출하지 못했습니다."));
                }
                cs.updateCounselFileExtractedText(inst, fileId, extractedText);
                row = cs.getCounselFileById(inst, fileId);
            }

            if (openAiApiKey == null || openAiApiKey.isBlank()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                        "success", false,
                        "message", "OpenAI API 키가 설정되지 않았습니다."));
            }

            String filename = objectString(row.get("original_filename")).trim();
            OpenAiSummaryResult summaryResult = summarizeAttachmentWithOpenAi("첨부문서", extractedText, filename);
            if (!summaryResult.success()) {
                return ResponseEntity.status(summaryResult.status()).body(Map.of(
                        "success", false,
                        "message", summaryResult.message()));
            }

            cs.updateCounselFileSummary(inst, fileId, summaryResult.summary());
            Map<String, Object> updated = cs.getCounselFileById(inst, fileId);
            return ResponseEntity.ok(Map.of(
                    "success", true,
                    "summary", summaryResult.summary(),
                    "item", toCounselFilePayload(updated, request),
                    "cached", false));
        } catch (Exception e) {
            log.error("[counsel/file/summary] fail inst={}, fileId={}", inst, fileId, e);
            return ResponseEntity.internalServerError().body(Map.of("success", false, "message", "요약 생성 중 오류"));
        }
    }

    @PostMapping("counsel/ListSetting")
    @ResponseBody
    public ResponseEntity<?> saveOrder(HttpSession session, @RequestBody List<OrderedItem> orderedItems) {
        String inst = (String) session.getAttribute("inst");
        if (inst == null || inst.isBlank()) {
            return ResponseEntity.status(401).body("세션 만료");
        }

        try {
            Set<String> hiddenListColumns = getHiddenListColumns();
            List<OrderedItem> filteredItems = Optional.ofNullable(orderedItems)
                    .orElse(Collections.emptyList())
                    .stream()
                    .filter(Objects::nonNull)
                    .filter(item -> !isHiddenListColumn(item.getColumn(), hiddenListColumns))
                    .toList();

            // 프론트에서 이미 turn/viewYn 세팅해옴: 그대로 저장
            counselListService.replaceAll(inst, filteredItems); // 배치로 한번에

            return ResponseEntity.ok("Order saved successfully");
        } catch (Exception e) {
            log.error("ListSetting save error", e);
            return ResponseEntity.status(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to save order");
        }
    }

    private void bindPendingCounselAudio(String inst, int csIdx, HttpServletRequest request) {
        if (csIdx <= 0 || request == null) {
            return;
        }
        String tempKey = sanitizeTempKey(request.getParameter("audio_temp_key"));
        if (tempKey.isBlank()) {
            return;
        }
        try {
            cs.bindCounselAudioByTempKey(inst, tempKey, csIdx);
            syncCounselContentFromAudioTranscripts(inst, csIdx);
        } catch (Exception e) {
            log.warn("[counsel/audio/bind] fail inst={}, csIdx={}, err={}", inst, csIdx, e.toString());
        }
    }

    private void bindPendingCounselFiles(String inst, int csIdx, HttpServletRequest request) {
        if (csIdx <= 0 || request == null) {
            return;
        }
        String tempKey = sanitizeTempKey(request.getParameter("file_temp_key"));
        if (tempKey.isBlank()) {
            return;
        }
        try {
            cs.bindCounselFileByTempKey(inst, tempKey, csIdx);
        } catch (Exception e) {
            log.warn("[counsel/file/bind] fail inst={}, csIdx={}, err={}", inst, csIdx, e.toString());
        }
    }

    private void syncCounselContentFromAudioTranscripts(String inst, int csIdx) {
        if (csIdx <= 0) {
            return;
        }
        try {
            List<Map<String, Object>> rows = cs.getCounselAudioList(inst, csIdx, null);
            enrichAudioTranscriptsIfNeeded(inst, rows);
            if (rows == null || rows.isEmpty()) {
                return;
            }
            for (Map<String, Object> row : rows) {
                String transcript = objectString(row.get("transcript")).trim();
                if (transcript.isEmpty()) {
                    continue;
                }
                cs.appendCounselContentIfMissing(inst, csIdx, transcript);
            }
        } catch (Exception e) {
            log.warn("[counsel/audio/sync] fail inst={}, csIdx={}, err={}", inst, csIdx, e.toString());
        }
    }

    private void enrichAudioTranscriptsIfNeeded(String inst, List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }

        Path baseDir = resolveCounselAudioDir(inst);
        for (Map<String, Object> row : rows) {
            if (row == null) {
                continue;
            }
            String transcript = objectString(row.get("transcript")).trim();
            if (!transcript.isEmpty()) {
                continue;
            }

            long audioId = parseLongObject(row.get("id"), 0L);
            if (audioId <= 0) {
                continue;
            }

            String storedFilename = objectString(row.get("stored_filename")).trim();
            if (storedFilename.isEmpty()) {
                continue;
            }

            try {
                Path file = baseDir.resolve(storedFilename).normalize();
                if (!file.startsWith(baseDir) || !Files.exists(file) || !Files.isRegularFile(file)) {
                    continue;
                }

                String mimeType = objectString(row.get("mime_type"));
                String generated = transcribeWithClova(file, mimeType);
                if (generated.isBlank()) {
                    continue;
                }

                row.put("transcript", generated);
                cs.updateCounselAudioTranscript(inst, audioId, generated);

                int rowCsIdx = (int) parseLongObject(row.get("cs_idx"), 0L);
                if (rowCsIdx > 0) {
                    cs.appendCounselContentIfMissing(inst, rowCsIdx, generated);
                }
            } catch (Exception e) {
                log.warn("[counsel/audio/transcript-backfill] fail inst={}, audioId={}, err={}",
                        inst, audioId, e.toString());
            }
        }
    }

    private Path resolveCounselAudioDir(String inst) {
        return Paths.get(counselAudioBaseDir, sanitizeInstDirName(inst)).toAbsolutePath().normalize();
    }

    private Path resolveCounselFileDir(String inst) {
        return Paths.get(counselFileBaseDir, sanitizeInstDirName(inst)).toAbsolutePath().normalize();
    }

    private String sanitizeInstDirName(String inst) {
        if (inst == null) {
            return "unknown";
        }
        String normalized = inst.trim();
        return normalized.replaceAll("[^A-Za-z0-9_\\-]", "_");
    }

    private String normalizeMimeType(String mimeType) {
        String normalized = safeString(mimeType).trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return "";
        }
        int sep = normalized.indexOf(';');
        if (sep >= 0) {
            normalized = normalized.substring(0, sep).trim();
        }
        return normalized;
    }

    private String resolveAudioFileExtension(String filename, String contentType) {
        String extension = "";
        if (filename != null) {
            int idx = filename.lastIndexOf('.');
            if (idx > -1 && idx < filename.length() - 1) {
                extension = filename.substring(idx + 1).toLowerCase(Locale.ROOT);
            }
        }
        if (!extension.isBlank()) {
            return extension;
        }
        String mime = normalizeMimeType(contentType);
        if (mime.contains("webm")) {
            return "webm";
        }
        if (mime.contains("mpeg") || mime.contains("mp3")) {
            return "mp3";
        }
        if (mime.contains("m4a") || mime.contains("mp4")) {
            return "m4a";
        }
        if (mime.contains("aac")) {
            return "aac";
        }
        if (mime.contains("ac3")) {
            return "ac3";
        }
        if (mime.contains("flac")) {
            return "flac";
        }
        if (mime.contains("ogg")) {
            return "ogg";
        }
        if (mime.contains("wav")) {
            return "wav";
        }
        return "webm";
    }

    private String resolveGenericFileExtension(String filename) {
        if (filename == null) {
            return "bin";
        }
        String name = filename.trim();
        int idx = name.lastIndexOf('.');
        if (idx < 0 || idx >= name.length() - 1) {
            return "bin";
        }
        String ext = name.substring(idx + 1).replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
        return ext.isBlank() ? "bin" : ext;
    }

    private String buildStoredAudioFilename(String extension) {
        String ext = (extension == null || extension.isBlank()) ? "webm"
                : extension.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
        if (ext.isBlank()) {
            ext = "webm";
        }
        String stamp = new SimpleDateFormat("yyyyMMddHHmmssSSS").format(new Date());
        String rand = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return stamp + "_" + rand + "." + ext;
    }

    private String buildStoredCounselFileFilename(String extension) {
        String ext = (extension == null || extension.isBlank()) ? "bin"
                : extension.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
        if (ext.isBlank()) {
            ext = "bin";
        }
        String stamp = new SimpleDateFormat("yyyyMMddHHmmssSSS").format(new Date());
        String rand = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return "file_" + stamp + "_" + rand + "." + ext;
    }

    private String buildFallbackTempKey() {
        String stamp = new SimpleDateFormat("yyyyMMddHHmmssSSS").format(new Date());
        String rand = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        return "tmp_" + stamp + "_" + rand;
    }

    private String sanitizeTempKey(String tempKey) {
        if (tempKey == null) {
            return "";
        }
        return tempKey.trim().replaceAll("[^A-Za-z0-9_\\-]", "");
    }

    private String sanitizeHeaderFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "recording";
        }
        return filename.replace("\"", "").replace("\r", "").replace("\n", "");
    }

    private String buildCounselAudioStreamUrl(HttpServletRequest request, long audioId) {
        String ctx = request == null ? "" : safeString(request.getContextPath());
        if (ctx.endsWith("/")) {
            ctx = ctx.substring(0, ctx.length() - 1);
        }
        return ctx + "/counsel/audio/stream/" + audioId;
    }

    private String buildCounselFileDownloadUrl(HttpServletRequest request, long fileId) {
        String ctx = request == null ? "" : safeString(request.getContextPath());
        if (ctx.endsWith("/")) {
            ctx = ctx.substring(0, ctx.length() - 1);
        }
        return ctx + "/counsel/file/download/" + fileId;
    }

    private Map<String, Object> toCounselAudioPayload(Map<String, Object> row, HttpServletRequest request) {
        if (row == null) {
            return Collections.emptyMap();
        }

        long id = parseLongObject(row.get("id"), 0L);
        Map<String, Object> out = new HashMap<>();
        out.put("id", id);
        out.put("csIdx", parseLongObject(row.get("cs_idx"), 0L));
        out.put("tempKey", objectString(row.get("temp_key")));
        out.put("originalFilename", objectString(row.get("original_filename")));
        out.put("storedFilename", objectString(row.get("stored_filename")));
        out.put("mimeType", objectString(row.get("mime_type")));
        out.put("fileSize", parseLongObject(row.get("file_size"), 0L));
        out.put("durationSeconds", row.get("duration_seconds"));
        out.put("transcript", objectString(row.get("transcript")));
        out.put("summaryText", objectString(row.get("summary_text")));
        out.put("createdBy", objectString(row.get("created_by")));
        out.put("createdAt", row.get("created_at"));
        out.put("updatedAt", row.get("updated_at"));
        out.put("streamUrl", buildCounselAudioStreamUrl(request, id));
        return out;
    }

    private Map<String, Object> toCounselFilePayload(Map<String, Object> row, HttpServletRequest request) {
        if (row == null) {
            return Collections.emptyMap();
        }

        long id = parseLongObject(row.get("id"), 0L);
        Map<String, Object> out = new HashMap<>();
        out.put("id", id);
        out.put("csIdx", parseLongObject(row.get("cs_idx"), 0L));
        out.put("tempKey", objectString(row.get("temp_key")));
        out.put("originalFilename", objectString(row.get("original_filename")));
        out.put("storedFilename", objectString(row.get("stored_filename")));
        out.put("mimeType", objectString(row.get("mime_type")));
        out.put("fileSize", parseLongObject(row.get("file_size"), 0L));
        out.put("extractedText", objectString(row.get("extracted_text")));
        out.put("summaryText", objectString(row.get("summary_text")));
        out.put("createdBy", objectString(row.get("created_by")));
        out.put("createdAt", row.get("created_at"));
        out.put("updatedAt", row.get("updated_at"));
        out.put("downloadUrl", buildCounselFileDownloadUrl(request, id));
        return out;
    }

    private String extractTextFromCounselFile(Path filePath, String originalFilename, String mimeType) {
        if (filePath == null || !Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            return "";
        }
        try {
            String ext = resolveGenericFileExtension(originalFilename);
            if (ext.isBlank() || "bin".equals(ext)) {
                ext = resolveGenericFileExtension(filePath.getFileName().toString());
            }

            String extracted;
            if ("pdf".equals(ext)) {
                extracted = extractTextFromPdfFile(filePath);
            } else if ("docx".equals(ext)) {
                extracted = extractTextFromDocxFile(filePath);
            } else if (isImageExtension(ext)) {
                extracted = extractTextFromImageWithOpenAi(filePath, mimeType);
            } else if (isTextLikeExtension(ext)) {
                extracted = extractTextFromUtf8TextFile(filePath);
            } else {
                extracted = "";
            }
            return normalizeExtractedText(extracted, 12000);
        } catch (Exception e) {
            log.warn("[counsel/file/extract] parse fail file={}, err={}", filePath, e.toString());
            return "";
        }
    }

    private boolean isImageExtension(String ext) {
        return Set.of("jpg", "jpeg", "png", "gif", "bmp", "webp", "tif", "tiff")
                .contains(safeString(ext).toLowerCase(Locale.ROOT));
    }

    private boolean isTextLikeExtension(String ext) {
        return Set.of("txt", "csv", "md", "json", "xml", "html", "htm", "log", "rtf")
                .contains(safeString(ext).toLowerCase(Locale.ROOT));
    }

    private String extractTextFromUtf8TextFile(Path filePath) {
        try {
            return Files.readString(filePath, StandardCharsets.UTF_8);
        } catch (Exception e) {
            try {
                byte[] bytes = Files.readAllBytes(filePath);
                return new String(bytes, StandardCharsets.UTF_8);
            } catch (Exception ignored) {
                return "";
            }
        }
    }

    private String extractTextFromDocxFile(Path filePath) {
        try (InputStream in = Files.newInputStream(filePath);
                XWPFDocument document = new XWPFDocument(in);
                XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        } catch (Exception e) {
            log.warn("[counsel/file/extract-docx] fail file={}, err={}", filePath, e.toString());
            return "";
        }
    }

    private String extractTextFromPdfFile(Path filePath) {
        try (PDDocument document = loadPdfDocumentCompat(filePath)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        } catch (Exception e) {
            log.warn("[counsel/file/extract-pdf] fail file={}, err={}", filePath, e.toString());
            return "";
        }
    }

    private PDDocument loadPdfDocumentCompat(Path filePath) throws Exception {
        if (filePath == null) {
            throw new IllegalArgumentException("filePath is null");
        }

        // PDFBox 3.x: org.apache.pdfbox.Loader.loadPDF(File)
        try {
            Class<?> loaderClass = Class.forName("org.apache.pdfbox.Loader");
            Method loadPdfMethod = loaderClass.getMethod("loadPDF", java.io.File.class);
            Object loaded = loadPdfMethod.invoke(null, filePath.toFile());
            if (loaded instanceof PDDocument document) {
                return document;
            }
        } catch (ClassNotFoundException | NoSuchMethodException ignored) {
            // PDFBox 2.x 환경으로 fallback
        }

        // PDFBox 2.x: PDDocument.load(File)
        Method legacyLoad = PDDocument.class.getMethod("load", java.io.File.class);
        Object loaded = legacyLoad.invoke(null, filePath.toFile());
        if (loaded instanceof PDDocument document) {
            return document;
        }
        throw new IllegalStateException("Unsupported PDFBox runtime");
    }

    private String extractTextFromImageWithOpenAi(Path imageFile, String mimeType) {
        String endpoint = resolveOpenAiResponsesEndpoint();
        String apiKey = safeString(openAiApiKey).trim();
        if (endpoint.isBlank() || apiKey.isBlank() || imageFile == null) {
            return "";
        }

        try {
            byte[] imageBytes = Files.readAllBytes(imageFile);
            if (imageBytes.length == 0) {
                return "";
            }

            String mediaType = normalizeMimeType(mimeType);
            if (mediaType.isBlank()) {
                mediaType = normalizeMimeType(Files.probeContentType(imageFile));
            }
            if (mediaType.isBlank()) {
                mediaType = "image/png";
            }
            String imageDataUrl = "data:" + mediaType + ";base64," + Base64.getEncoder().encodeToString(imageBytes);

            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", safeString(openAiModel).isBlank() ? "gpt-4.1-mini" : safeString(openAiModel).trim());
            body.put("max_output_tokens", 1200);

            ArrayNode input = body.putArray("input");
            ObjectNode message = input.addObject();
            message.put("role", "user");
            ArrayNode content = message.putArray("content");

            ObjectNode promptNode = content.addObject();
            promptNode.put("type", "input_text");
            promptNode.put("text", """
                    이미지 문서에서 보이는 텍스트를 한국어 기준으로 최대한 정확히 추출해라.
                    임의 요약/추측 금지, 원문에 있는 정보만 출력.
                    표/줄바꿈은 가능한 유지하고 일반 텍스트로만 출력해라.
                    """);

            ObjectNode imageNode = content.addObject();
            imageNode.put("type", "input_image");
            imageNode.put("image_url", imageDataUrl);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(45))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("[openai] image extract fail status={} body={}", response.statusCode(),
                        truncateLogBody(response.body()));
                return "";
            }
            return extractSummaryFromOpenAiResponse(response.body());
        } catch (Exception e) {
            log.warn("[openai] image extract error {}", e.toString());
            return "";
        }
    }

    private String normalizeExtractedText(String text, int maxLength) {
        String normalized = safeString(text)
                .replace("\r", "\n")
                .replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
        if (normalized.isBlank()) {
            return "";
        }
        if (normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, maxLength);
    }

    private static String objectString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static long parseLongObject(Object value, long defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number num) {
            return num.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private String transcribeWithClova(Path audioFile, String mimeType) {
        String endpoint = resolveClovaInvokeEndpoint();
        if (endpoint.isBlank() || clovaSecretKey == null || clovaSecretKey.isBlank() || audioFile == null) {
            return "";
        }
        if (!Files.exists(audioFile) || !Files.isRegularFile(audioFile)) {
            return "";
        }

        try {
            byte[] mediaBytes = Files.readAllBytes(audioFile);
            String boundary = "----CsmClovaBoundary" + java.util.UUID.randomUUID().toString().replace("-", "");
            String fileName = audioFile.getFileName().toString();
            String mediaType = normalizeMimeType(mimeType);
            if (mediaType.isBlank()) {
                mediaType = normalizeMimeType(Files.probeContentType(audioFile));
            }
            if (mediaType.isBlank()) {
                mediaType = "application/octet-stream";
            }
            if (mediaType.contains("webm")) {
                log.warn("[clova] skip webm audio. file={} mime={}", fileName, mediaType);
                return "";
            }

            String paramsJson = "{\"language\":\"ko-KR\",\"completion\":\"sync\",\"fullText\":true}";

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            writeMultipartTextPart(bos, boundary, "params", paramsJson, "application/json; charset=UTF-8");
            writeMultipartFilePart(bos, boundary, "media", fileName, mediaType, mediaBytes);
            bos.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Accept", "application/json")
                    .header("X-CLOVASPEECH-API-KEY", clovaSecretKey.trim())
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(bos.toByteArray()))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("[clova] stt fail status={} body={}", response.statusCode(), truncateLogBody(response.body()));
                return "";
            }
            return extractTranscriptFromClovaResponse(response.body());
        } catch (Exception e) {
            log.warn("[clova] stt call error {}", e.toString());
            return "";
        }
    }

    private String resolveClovaInvokeEndpoint() {
        String base = clovaInvokeUrl == null ? "" : clovaInvokeUrl.trim();
        if (base.isBlank()) {
            return "";
        }
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (base.contains("/recognizer/")) {
            return base;
        }
        return base + "/recognizer/upload";
    }

    private void writeMultipartTextPart(
            ByteArrayOutputStream bos,
            String boundary,
            String name,
            String value,
            String contentType) throws IOException {
        bos.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        bos.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n").getBytes(StandardCharsets.UTF_8));
        if (contentType != null && !contentType.isBlank()) {
            bos.write(("Content-Type: " + contentType + "\r\n").getBytes(StandardCharsets.UTF_8));
        }
        bos.write("\r\n".getBytes(StandardCharsets.UTF_8));
        bos.write((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
        bos.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private void writeMultipartFilePart(
            ByteArrayOutputStream bos,
            String boundary,
            String name,
            String filename,
            String contentType,
            byte[] data) throws IOException {
        bos.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        bos.write(
                ("Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + sanitizeHeaderFilename(filename)
                        + "\"\r\n").getBytes(StandardCharsets.UTF_8));
        bos.write(("Content-Type: "
                + (contentType == null || contentType.isBlank() ? "application/octet-stream" : contentType)
                + "\r\n").getBytes(StandardCharsets.UTF_8));
        bos.write("\r\n".getBytes(StandardCharsets.UTF_8));
        bos.write(data == null ? new byte[0] : data);
        bos.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private String extractTranscriptFromClovaResponse(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "";
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            List<String> candidates = new ArrayList<>();

            collectTranscriptCandidate(candidates, root.path("text").asText(""));
            collectTranscriptCandidate(candidates, root.path("fullText").asText(""));

            JsonNode segments = root.path("segments");
            if (segments.isArray()) {
                StringBuilder sb = new StringBuilder();
                for (JsonNode segment : segments) {
                    String segmentText = segment.path("text").asText("").trim();
                    if (!segmentText.isEmpty()) {
                        if (sb.length() > 0) {
                            sb.append(' ');
                        }
                        sb.append(segmentText);
                    }
                }
                collectTranscriptCandidate(candidates, sb.toString());
            }

            JsonNode result = root.path("result");
            if (!result.isMissingNode()) {
                collectTranscriptCandidate(candidates, result.path("text").asText(""));
                collectTranscriptCandidate(candidates, result.path("fullText").asText(""));
            }

            if (candidates.isEmpty()) {
                return "";
            }
            return candidates.stream()
                    .max((a, b) -> Integer.compare(a.length(), b.length()))
                    .orElse("");
        } catch (Exception e) {
            log.warn("[clova] parse fail {}", e.toString());
            return "";
        }
    }

    private void collectTranscriptCandidate(List<String> out, String text) {
        String normalized = normalizeTranscriptText(text);
        if (!normalized.isBlank()) {
            out.add(normalized);
        }
    }

    private String normalizeTranscriptText(String text) {
        if (text == null) {
            return "";
        }
        return text.replaceAll("\\s+", " ").trim();
    }

    private String truncateLogBody(String body) {
        String v = body == null ? "" : body.replaceAll("\\s+", " ").trim();
        int max = 500;
        if (v.length() <= max) {
            return v;
        }
        return v.substring(0, max) + "...";
    }

    private record OpenAiSummaryResult(boolean success, String summary, HttpStatus status, String message) {
        static OpenAiSummaryResult success(String summary) {
            return new OpenAiSummaryResult(true, summary, HttpStatus.OK, "");
        }

        static OpenAiSummaryResult failure(HttpStatus status, String message) {
            return new OpenAiSummaryResult(false, "", status, message);
        }
    }

    private OpenAiSummaryResult summarizeCounselWithOpenAi(String content) {
        String clipped = clipSummarySourceText(content, 12000);
        if (clipped.isBlank()) {
            return OpenAiSummaryResult.failure(HttpStatus.BAD_REQUEST, "요약할 상담 내용이 없습니다.");
        }
        return summarizeWithOpenAi(buildCounselSummaryPrompt(clipped), 500);
    }

    private OpenAiSummaryResult summarizeAttachmentWithOpenAi(String sourceType, String content, String filename) {
        String clipped = clipSummarySourceText(content, 12000);
        if (clipped.isBlank()) {
            return OpenAiSummaryResult.failure(HttpStatus.BAD_REQUEST, "요약할 원문이 없습니다.");
        }
        return summarizeWithOpenAi(buildAttachmentSummaryPrompt(sourceType, clipped, filename), 500);
    }

    private OpenAiSummaryResult summarizeWithOpenAi(String prompt, int maxOutputTokens) {
        String endpoint = resolveOpenAiResponsesEndpoint();
        String apiKey = safeString(openAiApiKey).trim();
        if (endpoint.isBlank() || apiKey.isBlank()) {
            return OpenAiSummaryResult.failure(HttpStatus.BAD_REQUEST, "OpenAI API 키가 설정되지 않았습니다.");
        }
        String normalizedPrompt = safeString(prompt).trim();
        if (normalizedPrompt.isBlank()) {
            return OpenAiSummaryResult.failure(HttpStatus.BAD_REQUEST, "요약할 내용이 없습니다.");
        }

        try {
            ObjectNode body = objectMapper.createObjectNode();
            body.put("model", safeString(openAiModel).isBlank() ? "gpt-4.1-mini" : safeString(openAiModel).trim());
            body.put("max_output_tokens", maxOutputTokens);

            ArrayNode input = body.putArray("input");
            ObjectNode message = input.addObject();
            message.put("role", "user");
            ArrayNode messageContents = message.putArray("content");
            ObjectNode text = messageContents.addObject();
            text.put("type", "input_text");
            text.put("text", normalizedPrompt);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(40))
                    .header("Accept", "application/json")
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("[openai] summary fail status={} body={}", response.statusCode(),
                        truncateLogBody(response.body()));
                return resolveOpenAiFailureResult(response.statusCode(), response.body());
            }
            String summary = extractSummaryFromOpenAiResponse(response.body());
            if (summary.isBlank()) {
                return OpenAiSummaryResult.failure(HttpStatus.BAD_GATEWAY, "요약 결과를 생성하지 못했습니다.");
            }
            return OpenAiSummaryResult.success(summary);
        } catch (Exception e) {
            log.warn("[openai] summary call error {}", e.toString());
            return OpenAiSummaryResult.failure(HttpStatus.BAD_GATEWAY, "OpenAI 호출 중 오류가 발생했습니다. 잠시 후 다시 시도해 주세요.");
        }
    }

    private OpenAiSummaryResult resolveOpenAiFailureResult(int statusCode, String responseBody) {
        HttpStatus status = HttpStatus.resolve(statusCode);
        if (status == null) {
            status = HttpStatus.BAD_GATEWAY;
        }

        String code = "";
        String type = "";
        String upstreamMessage = "";
        try {
            JsonNode root = objectMapper.readTree(safeString(responseBody));
            JsonNode error = root.path("error");
            code = safeString(error.path("code").asText("")).trim();
            type = safeString(error.path("type").asText("")).trim();
            upstreamMessage = safeString(error.path("message").asText("")).trim();
        } catch (Exception ignored) {
            // 응답 본문 파싱 실패 시 기본 메시지 사용
        }

        if (statusCode == 429 && "insufficient_quota".equalsIgnoreCase(code)) {
            return OpenAiSummaryResult.failure(HttpStatus.TOO_MANY_REQUESTS,
                    "OpenAI 사용 한도(쿼터)를 초과했습니다. OpenAI 결제/사용량을 확인해 주세요.");
        }
        if (statusCode == 429) {
            return OpenAiSummaryResult.failure(HttpStatus.TOO_MANY_REQUESTS,
                    "요청이 많아 OpenAI 호출이 제한되었습니다. 잠시 후 다시 시도해 주세요.");
        }
        if (statusCode == 401 || "invalid_api_key".equalsIgnoreCase(code)) {
            return OpenAiSummaryResult.failure(HttpStatus.UNAUTHORIZED,
                    "OpenAI API 키가 유효하지 않습니다. 설정값을 확인해 주세요.");
        }
        if (statusCode >= 500 && statusCode < 600) {
            return OpenAiSummaryResult.failure(HttpStatus.BAD_GATEWAY,
                    "OpenAI 서버 응답이 불안정합니다. 잠시 후 다시 시도해 주세요.");
        }
        if (!upstreamMessage.isBlank()) {
            return OpenAiSummaryResult.failure(status, "OpenAI 오류: " + upstreamMessage);
        }
        if (!type.isBlank()) {
            return OpenAiSummaryResult.failure(status, "OpenAI 오류(" + type + ")가 발생했습니다.");
        }
        return OpenAiSummaryResult.failure(status, "요약 생성에 실패했습니다. 잠시 후 다시 시도해 주세요.");
    }

    private String resolveOpenAiResponsesEndpoint() {
        String base = safeString(openAiBaseUrl).trim();
        if (base.isBlank()) {
            return "";
        }
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (base.endsWith("/responses")) {
            return base;
        }
        if (base.endsWith("/v1")) {
            return base + "/responses";
        }
        return base + "/v1/responses";
    }

    private String clipSummarySourceText(String content, int maxChars) {
        String normalized = safeString(content).replace("\r", "\n").trim();
        if (normalized.isBlank()) {
            return "";
        }
        if (normalized.length() <= maxChars) {
            return normalized;
        }
        return normalized.substring(0, maxChars);
    }

    private String buildCounselSummaryPrompt(String content) {
        return """
                너는 입원상담 기록 정리 도우미다.
                아래 상담 내용을 한국어로 간결하게 요약해라.

                출력 형식(각 항목 한 줄):
                핵심상태: ...
                추정병명: ...
                입원의사: ...
                특이사항: ...
                다음조치: ...

                규칙:
                - 근거 없는 추측은 금지.
                - 원문에 없는 정보는 쓰지 말 것.
                - 정보가 없으면 "미확인"으로 작성.
                - 5줄 이외의 설명은 출력하지 말 것.

                상담 내용:
                """ + content;
    }

    private String buildAttachmentSummaryPrompt(String sourceType, String content, String filename) {
        String kind = safeString(sourceType).isBlank() ? "첨부자료" : safeString(sourceType).trim();
        String safeFilename = safeString(filename).trim();
        return """
                너는 입원상담 보조자료 요약 도우미다.
                아래 %s 원문을 한국어로 간결하게 요약해라.

                출력 형식(각 항목 한 줄):
                핵심내용: ...
                환자상태: ...
                위험요소: ...
                입원판단포인트: ...
                추가확인사항: ...

                규칙:
                - 근거 없는 추측은 금지.
                - 원문에 없는 정보는 쓰지 말 것.
                - 정보가 없으면 "미확인"으로 작성.
                - 5줄 이외의 설명은 출력하지 말 것.
                - 파일명이 있으면 문맥 파악에만 참고하고, 파일명만으로 추정하지 말 것.

                파일명:
                %s

                원문:
                %s
                """.formatted(kind, safeFilename.isBlank() ? "미확인" : safeFilename, content);
    }

    private String extractSummaryFromOpenAiResponse(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return "";
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            List<String> candidates = new ArrayList<>();

            JsonNode outputText = root.path("output_text");
            if (outputText.isTextual()) {
                collectSummaryCandidate(candidates, outputText.asText(""));
            } else if (outputText.isArray()) {
                for (JsonNode textNode : outputText) {
                    collectSummaryCandidate(candidates, textNode.asText(""));
                }
            }

            JsonNode output = root.path("output");
            if (output.isArray()) {
                for (JsonNode item : output) {
                    JsonNode contents = item.path("content");
                    if (!contents.isArray()) {
                        continue;
                    }
                    for (JsonNode c : contents) {
                        collectSummaryCandidate(candidates, c.path("text").asText(""));
                        collectSummaryCandidate(candidates, c.path("output_text").asText(""));
                    }
                }
            }

            if (candidates.isEmpty()) {
                return "";
            }
            return candidates.stream()
                    .max((a, b) -> Integer.compare(a.length(), b.length()))
                    .orElse("");
        } catch (Exception e) {
            log.warn("[openai] summary parse fail {}", e.toString());
            return "";
        }
    }

    private void collectSummaryCandidate(List<String> out, String text) {
        String normalized = normalizeSummaryText(text);
        if (!normalized.isBlank()) {
            out.add(normalized);
        }
    }

    private String normalizeSummaryText(String text) {
        String value = safeString(text).replace("\r", "\n").trim();
        if (value.isBlank()) {
            return "";
        }
        if (value.startsWith("```")) {
            value = value.replaceAll("(?s)^```[a-zA-Z]*\\n", "").replaceAll("(?s)\\n```$", "").trim();
        }
        value = value.replaceAll("\\n{3,}", "\n\n");
        return value;
    }

    private CounselData buildCounselDataFromRequest(HttpServletRequest request, String inst) throws Exception {
        CounselData counselData = new CounselData();
        counselData.setInst(inst);

        String csCol01Plain = safeString(request.getParameter("cs_col_01")).trim();
        if (csCol01Plain.isEmpty()) {
            throw new IllegalArgumentException("환자명은 필수입니다.");
        }
        counselData.setCs_col_01(aes.encryptHexECB(csCol01Plain));
        counselData.setCs_col_01_hash(hashSHA256(csCol01Plain));

        counselData.setCs_col_02(request.getParameter("cs_col_02"));
        counselData.setCs_col_03(request.getParameter("cs_col_03"));
        counselData.setCs_col_04(request.getParameter("cs_col_04"));
        counselData.setCs_col_05(request.getParameter("cs_col_05"));
        counselData.setCs_col_06(request.getParameter("cs_col_06"));
        counselData.setCs_col_07(
                joinWithOptionalText(request.getParameter("cs_col_07"), request.getParameter("cs_col_07_text")));
        counselData.setCs_col_08(
                joinWithOptionalText(request.getParameter("cs_col_08"), request.getParameter("cs_col_08_text")));
        counselData.setCs_col_09(request.getParameter("cs_col_09"));
        counselData.setCs_col_10(request.getParameter("cs_col_10"));
        counselData.setCs_col_11(request.getParameter("cs_col_11"));
        counselData.setCs_col_12(request.getParameter("cs_col_12"));

        counselData.setCs_col_16(request.getParameter("cs_col_16"));
        counselData.setCs_col_17(request.getParameter("cs_col_17"));
        counselData.setCs_col_18(request.getParameter("cs_col_18"));
        counselData.setCs_col_19(request.getParameter("cs_col_19"));
        counselData.setCs_col_20(request.getParameter("cs_col_20"));
        counselData.setCs_col_21(request.getParameter("cs_col_21"));
        counselData.setCs_col_22(request.getParameter("cs_col_22"));
        counselData.setCs_col_23(request.getParameter("cs_col_23"));
        counselData.setCs_col_24(request.getParameter("cs_col_24"));
        counselData.setCs_col_25(request.getParameter("cs_col_25"));
        counselData.setCs_col_26(request.getParameter("cs_col_26"));
        counselData.setCs_col_27(request.getParameter("cs_col_27"));
        counselData.setCs_col_28(request.getParameter("cs_col_28"));
        counselData.setCs_col_29(request.getParameter("cs_col_29"));
        counselData.setCs_col_30(request.getParameter("cs_col_30"));
        counselData.setCs_col_31(request.getParameter("cs_col_31"));
        counselData.setCs_col_32(request.getParameter("cs_col_32"));
        counselData.setCs_col_33(request.getParameter("cs_col_33"));
        counselData.setCs_col_34(request.getParameter("cs_col_34"));
        counselData.setCs_col_35(request.getParameter("cs_col_35"));
        counselData.setCs_col_36(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        counselData.setCs_col_37(request.getParameter("cs_col_37"));
        counselData.setCs_col_38(request.getParameter("cs_col_38"));
        counselData.setCs_col_39(request.getParameter("cs_col_39"));
        counselData.setCs_col_40(request.getParameter("cs_col_40"));
        counselData.setCs_col_41(request.getParameter("cs_col_41"));
        counselData.setCs_col_42(request.getParameter("cs_col_42"));

        List<Guardian> guardians = new ArrayList<>();
        String[] guardianNames = Optional.ofNullable(request.getParameterValues("cs_col_13[]"))
                .orElse(request.getParameterValues("cs_col_13"));
        String[] relationships = Optional.ofNullable(request.getParameterValues("cs_col_14[]"))
                .orElse(request.getParameterValues("cs_col_14"));
        String[] contactNumbers = Optional.ofNullable(request.getParameterValues("cs_col_15[]"))
                .orElse(request.getParameterValues("cs_col_15"));

        int maxLength = Math.max(
                guardianNames == null ? 0 : guardianNames.length,
                Math.max(relationships == null ? 0 : relationships.length,
                        contactNumbers == null ? 0 : contactNumbers.length));
        for (int i = 0; i < maxLength; i++) {
            String name = guardianNames != null && i < guardianNames.length ? guardianNames[i] : "";
            String relation = relationships != null && i < relationships.length ? relationships[i] : "";
            String phone = contactNumbers != null && i < contactNumbers.length ? contactNumbers[i] : "";
            if (isBlank(name) && isBlank(relation) && isBlank(phone)) {
                continue;
            }

            Guardian guardian = new Guardian();
            if (!isBlank(name)) {
                guardian.setName(aes.encryptHexECB(name));
                guardian.setName_hash(hashSHA256(name));
            }
            guardian.setRelationship(relation);
            if (!isBlank(phone)) {
                guardian.setContact_number(aes.encryptHexECB(phone));
                guardian.setContact_number_hash(hashSHA256(phone));
            }
            guardians.add(guardian);
        }
        counselData.setGuardians(guardians);
        return counselData;
    }

    private void saveAdmissionPledgeFromRequest(String inst, int csIdx, HttpServletRequest request) {
        if (csIdx <= 0) {
            return;
        }
        if (!isModuleEnabled(inst, ModuleFeatureService.FEATURE_ADMISSION_PLEDGE)) {
            return;
        }

        boolean required = "Y".equalsIgnoreCase(safeString(request.getParameter("admission_pledge_required")));
        if (!required) {
            cs.deleteAdmissionPledge(inst, csIdx);
            return;
        }

        String agreedYn = "Y".equalsIgnoreCase(safeString(request.getParameter("admission_agreed_yn"))) ? "Y" : "N";
        String signerName = safeString(request.getParameter("admission_signer_name")).trim();
        if (signerName.isEmpty()) {
            signerName = safeString(request.getParameter("cs_col_01")).trim();
        }
        String signerRelation = safeString(request.getParameter("admission_signer_relation")).trim();
        String guardianName = safeString(request.getParameter("admission_guardian_name")).trim();
        String guardianRelation = safeString(request.getParameter("admission_guardian_relation")).trim();
        String guardianAddr = safeString(request.getParameter("admission_guardian_addr")).trim();
        String guardianPhone = safeString(request.getParameter("admission_guardian_phone")).trim();
        String guardianCostYn = "Y".equalsIgnoreCase(safeString(request.getParameter("admission_guardian_cost_yn"))) ? "Y"
                : "N";
        String subGuardianName = safeString(request.getParameter("admission_sub_guardian_name")).trim();
        String subGuardianRelation = safeString(request.getParameter("admission_sub_guardian_relation")).trim();
        String subGuardianAddr = safeString(request.getParameter("admission_sub_guardian_addr")).trim();
        String subGuardianPhone = safeString(request.getParameter("admission_sub_guardian_phone")).trim();
        String subGuardianCostYn = "Y".equalsIgnoreCase(safeString(request.getParameter("admission_sub_guardian_cost_yn"))) ? "Y"
                : "N";
        String signedAt = safeString(request.getParameter("admission_signed_at")).trim();
        if (signedAt.isEmpty()) {
            signedAt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        }
        String pledgeText = safeString(request.getParameter("admission_pledge_text")).trim();
        if (pledgeText.isEmpty()) {
            pledgeText = DEFAULT_ADMISSION_PLEDGE_TEXT;
        }
        String signatureData = normalizeSignatureData(request.getParameter("admission_signature_data"));
        String pageInkData = normalizePageInkData(request.getParameter("admission_page_ink_data"));

        if (!"Y".equals(agreedYn)) {
            log.warn("[admission-pledge] invalid input. clear row. inst={}, cs_idx={}", inst, csIdx);
            cs.deleteAdmissionPledge(inst, csIdx);
            return;
        }

        Map<String, Object> pledge = new HashMap<>();
        pledge.put("agreed_yn", agreedYn);
        pledge.put("signer_name", signerName);
        pledge.put("signer_relation", signerRelation);
        pledge.put("guardian_name", guardianName);
        pledge.put("guardian_relation", guardianRelation);
        pledge.put("guardian_addr", guardianAddr);
        pledge.put("guardian_phone", guardianPhone);
        pledge.put("guardian_cost_yn", guardianCostYn);
        pledge.put("sub_guardian_name", subGuardianName);
        pledge.put("sub_guardian_relation", subGuardianRelation);
        pledge.put("sub_guardian_addr", subGuardianAddr);
        pledge.put("sub_guardian_phone", subGuardianPhone);
        pledge.put("sub_guardian_cost_yn", subGuardianCostYn);
        pledge.put("signed_at", signedAt);
        pledge.put("pledge_text", pledgeText);
        pledge.put("signature_data", signatureData);
        pledge.put("page_ink_data", pageInkData);
        cs.upsertAdmissionPledge(inst, csIdx, pledge);
    }

    private String normalizeSignatureData(String rawSignature) {
        String signature = safeString(rawSignature).trim();
        if (signature.isBlank()) {
            return "";
        }
        if (isValidPngDataUrl(signature, 1_500_000)) {
            return signature;
        }
        if (signature.length() > 4_000_000) {
            return "";
        }
        try {
            JsonNode node = objectMapper.readTree(signature);
            if (node == null || !node.isObject()) {
                return "";
            }
            String primary = firstValidSignatureImage(node, "primary", "main", "signer", "final_signature", "signature");
            if (primary.isBlank()) {
                return "";
            }
            String guardian = firstValidSignatureImage(node, "guardian", "guardian_signature", "primary_guardian");
            String subGuardian = firstValidSignatureImage(node, "sub_guardian", "subGuardian", "sub_guardian_signature",
                    "secondary_guardian");

            ObjectNode out = objectMapper.createObjectNode();
            out.put("version", 2);
            out.put("primary", primary);
            if (!guardian.isBlank()) {
                out.put("guardian", guardian);
            }
            if (!subGuardian.isBlank()) {
                out.put("sub_guardian", subGuardian);
            }

            String normalized = objectMapper.writeValueAsString(out);
            return normalized.length() <= 4_000_000 ? normalized : "";
        } catch (Exception e) {
            return "";
        }
    }

    private String normalizePageInkData(String rawPageInk) {
        String pageInk = safeString(rawPageInk).trim();
        if (pageInk.isBlank()) {
            return "";
        }
        if (!isValidPngDataUrl(pageInk, 3_000_000)) {
            return "";
        }
        return pageInk;
    }

    private String firstValidSignatureImage(JsonNode node, String... keys) {
        if (node == null || keys == null) {
            return "";
        }
        for (String key : keys) {
            if (isBlank(key) || !node.has(key)) {
                continue;
            }
            String value = safeString(node.path(key).asText()).trim();
            if (isValidPngDataUrl(value, 1_500_000)) {
                return value;
            }
        }
        return "";
    }

    private boolean isValidPngDataUrl(String value, int maxLen) {
        String text = safeString(value).trim();
        if (text.isBlank()) {
            return false;
        }
        if (!text.startsWith("data:image/png;base64,")) {
            return false;
        }
        if (text.length() > maxLen) {
            return false;
        }
        return true;
    }

    private void saveCounselLogSnapshot(String inst, int csIdx, HttpServletRequest request) {
        try {
            String now = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            String counselAt = safeString(request.getParameter("cs_col_16")).trim();
            if (counselAt.isEmpty()) {
                counselAt = now.substring(0, 10);
            }

            CounselLog logRow = new CounselLog();
            logRow.setCs_idx(csIdx);
            String patientName = safeString(request.getParameter("cs_col_01")).trim();
            logRow.setName(encryptCounselLogName(patientName));
            logRow.setCounsel_content(safeString(request.getParameter("cs_col_32")));
            logRow.setCounsel_method(safeString(request.getParameter("cs_col_18")));
            logRow.setCounsel_result(safeString(request.getParameter("cs_col_19")));
            logRow.setCounsel_name(safeString(request.getParameter("cs_col_17")));
            logRow.setCounsel_at(counselAt);

            int logIdx = cs.insertCounselLog(inst, logRow);
            if (logIdx <= 0) {
                return;
            }

            for (CounselLogGuardian guardian : parseCounselLogGuardiansFromRequest(request, counselAt)) {
                guardian.setLog_idx(logIdx);
                cs.insertCounselLogGuardian(inst, guardian);
            }
        } catch (Exception e) {
            log.warn("[counsel] log snapshot save fail inst={}, cs_idx={}, err={}", inst, csIdx, e.toString());
        }
    }

    private String encryptCounselLogName(String plainName) {
        String normalized = safeString(plainName).trim();
        if (normalized.isEmpty()) {
            return "";
        }
        try {
            return aes.encryptHexECB(normalized);
        } catch (Exception e) {
            log.warn("[counsel-log] patient name encrypt fail; save raw. err={}", e.toString());
            return normalized;
        }
    }

    private List<CounselLog> normalizeCounselLogs(List<CounselLog> logs) {
        if (logs == null || logs.isEmpty()) {
            return Collections.emptyList();
        }
        for (CounselLog row : logs) {
            normalizeCounselLog(row);
        }
        return logs;
    }

    private void normalizeCounselLog(CounselLog row) {
        if (row == null) {
            return;
        }
        String rawName = safeString(row.getName()).trim();
        if (!isLikelyHex(rawName)) {
            return;
        }
        try {
            String decrypted = mysqlAesDecryptHexToUtf8(rawName, aesKey);
            if (decrypted != null) {
                row.setName(decrypted);
            }
        } catch (Exception e) {
            log.debug("[counsel-log] patient name decrypt skip idx={}, err={}", row.getIdx(), e.toString());
        }
    }

    private List<CounselLogGuardian> parseCounselLogGuardiansFromRequest(HttpServletRequest request, String counselAt) {
        List<CounselLogGuardian> guardians = new ArrayList<>();
        String[] guardianNames = Optional.ofNullable(request.getParameterValues("cs_col_13[]"))
                .orElse(request.getParameterValues("cs_col_13"));
        String[] relationships = Optional.ofNullable(request.getParameterValues("cs_col_14[]"))
                .orElse(request.getParameterValues("cs_col_14"));
        String[] contactNumbers = Optional.ofNullable(request.getParameterValues("cs_col_15[]"))
                .orElse(request.getParameterValues("cs_col_15"));

        int maxLength = Math.max(
                guardianNames == null ? 0 : guardianNames.length,
                Math.max(relationships == null ? 0 : relationships.length,
                        contactNumbers == null ? 0 : contactNumbers.length));
        for (int i = 0; i < maxLength; i++) {
            String name = guardianNames != null && i < guardianNames.length ? safeString(guardianNames[i]).trim() : "";
            String relation = relationships != null && i < relationships.length ? safeString(relationships[i]).trim()
                    : "";
            String phone = contactNumbers != null && i < contactNumbers.length ? safeString(contactNumbers[i]).trim()
                    : "";
            if (name.isEmpty() && relation.isEmpty() && phone.isEmpty()) {
                continue;
            }

            CounselLogGuardian guardian = new CounselLogGuardian();
            guardian.setCounsel_guardian(name);
            guardian.setCounsel_relationship(relation);
            guardian.setCounsel_number(phone);
            guardian.setCounsel_at(counselAt);
            guardians.add(guardian);
        }
        return guardians;
    }

    private List<CounselDataEntry> parseDynamicEntries(HttpServletRequest request, String inst) {
        Map<String, Object> categoryDataMap = cs.getCategoryData(inst);
        @SuppressWarnings("unchecked")
        Map<String, String> fieldTypeMapping = (Map<String, String>) categoryDataMap.getOrDefault(
                "fieldTypeMapping", Collections.emptyMap());

        List<CounselDataEntry> entries = new ArrayList<>();
        Map<String, String[]> parameterMap = request.getParameterMap();
        for (Map.Entry<String, String[]> parameter : parameterMap.entrySet()) {
            String parameterName = parameter.getKey();
            if (!parameterName.startsWith("field_")) {
                continue;
            }
            String baseParam = normalizeFieldBase(parameterName);
            String[] parts = baseParam.split("_");
            if (parts.length < 3) {
                continue;
            }
            Long categoryId;
            Long subcategoryId;
            try {
                categoryId = Long.parseLong(parts[1]);
                subcategoryId = Long.parseLong(parts[2]);
            } catch (NumberFormatException e) {
                continue;
            }

            String[] values = parameter.getValue();
            if (values == null || values.length == 0) {
                continue;
            }

            List<String> processedValues = new ArrayList<>();
            for (String value : values) {
                if (value == null || value.isBlank()) {
                    continue;
                }
                String selected = value.trim();
                String[] valueParts = selected.split("_", 2);
                processedValues.add(valueParts[0]);
                if (valueParts.length > 1) {
                    try {
                        subcategoryId = Long.parseLong(valueParts[1]);
                    } catch (NumberFormatException ignore) {
                    }
                }
            }
            if (processedValues.isEmpty()) {
                continue;
            }

            CounselDataEntry entry = new CounselDataEntry();
            entry.setCategory_id(categoryId);
            entry.setSubcategory_id(subcategoryId);
            entry.setValue(String.join("|", processedValues));
            entry.setFieldType(fieldTypeMapping.get(baseParam));
            entries.add(entry);
        }
        return entries;
    }

    private String normalizeFieldBase(String parameterName) {
        String base = parameterName;
        if (base.endsWith("_details")) {
            base = base.substring(0, base.length() - "_details".length());
        }
        if (base.endsWith("_select")) {
            base = base.substring(0, base.length() - "_select".length());
        }
        if (base.endsWith("_checkbox")) {
            base = base.substring(0, base.length() - "_checkbox".length());
        }
        if (base.endsWith("_radio")) {
            base = base.substring(0, base.length() - "_radio".length());
        }
        if (base.endsWith("_text")) {
            base = base.substring(0, base.length() - "_text".length());
        }
        return base;
    }

    private String joinWithOptionalText(String value, String text) {
        if (isBlank(text)) {
            return value;
        }
        if (isBlank(value)) {
            return text.trim();
        }
        return value + "," + text.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /** 공통 모델 세팅(유저/템플릿/카드/발신번호/카테고리 등) */
    private void populateCommon(Model model, String inst, Userdata userinfo) {
        // 1) 유저 리스트
        Userdata ud = new Userdata();
        ud.setUs_col_04(inst);
        List<Userdata> userList = cs.userSelect(ud);
        model.addAttribute("user", userList);
        model.addAttribute("info", userinfo);

        // 2) 상용구/명함/발신번호
        SmsTemplate st = new SmsTemplate();
        st.setInst(inst);
        List<SmsTemplate> smstemplate = ss.SelectTemplateView(st);
        List<Card> card = cs.SelectCard(inst);
        model.addAttribute("smstemplate", smstemplate);
        model.addAttribute("card", card);

        Counsel_phone cp = new Counsel_phone();
        cp.setInst(inst);
        List<Counsel_phone> phoneList = cs.selectPhone(cp);
        log.info("[populateCommon] inst={}, phoneList.size={}", inst, phoneList == null ? null : phoneList.size());
        if (phoneList != null) {
            for (Counsel_phone p : phoneList) {
                if (p == null)
                    continue;
                log.info("[populateCommon] phone row id={}, name='{}', num='{}'",
                        p.getId(), p.getPhone_name(), p.getPhone_num());
            }
        }
        model.addAttribute("ph", phoneList);
        List<Map<String, String>> phOptions = phoneList == null ? Collections.emptyList()
                : phoneList.stream()
                        .filter(Objects::nonNull)
                        .map(p -> {
                            String num = Optional.ofNullable(p.getPhone_num()).map(String::trim).orElse("");
                            String name = Optional.ofNullable(p.getPhone_name()).map(String::trim).orElse("");
                            if (num.isEmpty() || "null".equalsIgnoreCase(num))
                                return null;
                            if (name.isEmpty() || "null".equalsIgnoreCase(name))
                                name = "미지정";
                            Map<String, String> m = new HashMap<>();
                            m.put("value", num);
                            m.put("text", "(" + name + ") " + num);
                            return m;
                        })
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
        model.addAttribute("phOptions", phOptions);

        // 3) 카테고리/필드매핑
        Map<String, Object> categoryDataMap = cs.getCategoryData(inst);
        model.addAttribute("categoryData", categoryDataMap.get("categoryData"));
        model.addAttribute("fieldTypeMapping", categoryDataMap.get("fieldTypeMapping"));
        model.addAttribute("fieldOptionsMapping", categoryDataMap.get("fieldOptionsMapping"));
    }

    /** UA로 간단한 모바일 판별 */
    private boolean isMobile(HttpServletRequest req) {
        String ua = req.getHeader("User-Agent");
        if (ua == null)
            return false;
        return ua.matches(".*(Mobile|Android|iPhone|iPad|iPod|IEMobile|BlackBerry|Opera Mini).*");
    }

    /** 신규 페이지 (빈 폼) */
    @GetMapping({ "counsel/admission-pledge", "/counsel/admission-pledge" })
    public String counselAdmissionPledge(
            @RequestParam(value = "csIdx", required = false) Integer csIdxParam,
            @RequestParam(value = "draftKey", required = false) String draftKey,
            @RequestParam(value = "returnUrl", required = false) String returnUrl,
            @RequestParam(value = "patientName", required = false) String patientName,
            @RequestParam(value = "gender", required = false) String gender,
            @RequestParam(value = "birth", required = false) String birth,
            @RequestParam(value = "chartNo", required = false) String chartNo,
            @RequestParam(value = "room", required = false) String room,
            @RequestParam(value = "phone", required = false) String phone,
            Model model,
            HttpSession session) {
        String inst = ensureInst(session);
        if (inst == null) {
            return "redirect:/login";
        }
        if (!isModuleEnabled(inst, ModuleFeatureService.FEATURE_ADMISSION_PLEDGE)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "입원서약서 기능이 비활성화되었습니다.");
        }

        int csIdx = csIdxParam == null ? 0 : Math.max(0, csIdxParam);
        Map<String, Object> admissionPledge = csIdx > 0
                ? Optional.ofNullable(cs.getAdmissionPledge(inst, csIdx)).orElse(Collections.emptyMap())
                : Collections.emptyMap();

        model.addAttribute("inst", inst);
        model.addAttribute("csIdx", csIdx);
        model.addAttribute("draftKey", safeString(draftKey).trim());
        model.addAttribute("returnUrl", normalizeInternalReturnUrl(returnUrl));
        model.addAttribute("patientName", safeString(patientName).trim());
        model.addAttribute("gender", safeString(gender).trim());
        model.addAttribute("birth", safeString(birth).trim());
        model.addAttribute("chartNo", safeString(chartNo).trim());
        model.addAttribute("room", safeString(room).trim());
        model.addAttribute("phone", safeString(phone).trim());
        model.addAttribute("defaultAdmissionPledgeText", DEFAULT_ADMISSION_PLEDGE_TEXT);
        model.addAttribute("admissionPledge", admissionPledge);
        return "csm/counsel/admissionPledge";
    }

    /** 신규 페이지 (빈 폼) */
    @GetMapping({ "counsel/new", "/counsel/new" })
    public String counselNew(
            @RequestParam(value = "reservationId", required = false) Long reservationId,
            Model model,
            HttpSession session,
            HttpServletRequest req) {
        String inst = ensureInst(session);
        if (inst == null)
            return "redirect:/login";

        Userdata userinfo = ensureUserInfo(session, inst);
        populateCommon(model, inst, userinfo);
        populateModuleFeatureModel(model, inst);

        CounselData prefill = new CounselData();
        List<Guardian> prefillGuardians = new ArrayList<>();
        Map<String, Object> reservationLink = Collections.emptyMap();
        if (reservationId != null && reservationId > 0) {
            CounselReservation reservation = cs.getCounselReservationById(inst, reservationId);
            if (reservation != null) {
                prefill.setCs_col_01(safeString(reservation.getPatient_name()));
                prefill.setCs_col_06(safeString(reservation.getPatient_phone()));
                prefill.setCs_col_18("전화");
                prefill.setCs_col_19("입원예약");
                prefill.setCs_col_21(safeString(reservation.getReserved_at()));
                prefill.setCs_col_32(safeString(reservation.getCall_summary()));
                if (!isBlank(reservation.getGuardian_name()) || !isBlank(reservation.getPatient_phone())) {
                    Guardian guardian = new Guardian();
                    guardian.setName(safeString(reservation.getGuardian_name()));
                    guardian.setRelationship("");
                    guardian.setContact_number(safeString(reservation.getPatient_phone()));
                    prefillGuardians.add(guardian);
                }
                reservationLink = toReservationLink(reservation);
            }
        }

        // 신규 화면 렌더에 필요한 기본 모델값
        model.addAttribute("inst", inst);
        model.addAttribute("cs_idx", null);
        model.addAttribute("csData", prefill);
        model.addAttribute("csEntries", Collections.emptyList());
        model.addAttribute("guardians", prefillGuardians);
        model.addAttribute("valueMap", Collections.emptyMap());
        model.addAttribute("cslog", Collections.emptyList());
        model.addAttribute("admissionPledge", Collections.emptyMap());
        model.addAttribute("reservationId", reservationLink.get("id"));
        model.addAttribute("reservationLink", reservationLink);
        model.addAttribute("isEdit", false);

        // nav fragment 파라미터 기본값
        model.addAttribute("endVar", "on");
        model.addAttribute("st", "");
        model.addAttribute("kw", "");

        return isMobile(req) ? "csm/counsel/newMobile" : "csm/counsel/new";
    }

    /** 기존 데이터 로딩(수정/상세) */
    @GetMapping({ "counsel/new/{cs_idx}", "/counsel/new/{cs_idx}" })
    public String counselLog(@PathVariable("cs_idx") int csIdx,
            Authentication auth,
            Model model,
            HttpServletRequest req,
            HttpSession session) {

        String inst = ensureInst(session);
        if (inst == null)
            return "redirect:/login";

        Userdata userinfo = (Userdata) session.getAttribute("userInfo");
        populateCommon(model, inst, userinfo);
        populateModuleFeatureModel(model, inst);

        model.addAttribute("cs_idx", csIdx);
        var username = auth.getName();
        var info = cs.loadUserInfo(inst, username);
        model.addAttribute("info", info);

        // 1) 기본 데이터 로딩 및 널-정규화
        CounselData data = cs.getCounselById(inst, csIdx);
        log.debug("[getCounselById] inst={}, csIdx={}, data={}", inst, csIdx, data);
        // if (data == null) {
        // // 잘못된 cs_idx 이거나 Mapper 문제가 있는 상황 – 빈 객체로 덮어쓰면 화면이 항상 빈값이 됨
        // throw new ResponseStatusException(HttpStatus.NOT_FOUND, "cs_idx not found: "
        // + csIdx);
        // }
        boolean isEdit = (data != null);
        if (!isEdit)
            data = new CounselData();
        // boolean isEdit = true; // 이 엔드포인트는 수정/상세 전용
        log.debug("isEdit={}", isEdit);
        log.debug("[Controller] cs bean class = {}", cs.getClass().getName());
        // 2) 엔트리 로딩은 항상 (신규면 빈 리스트일 수도)
        List<CounselDataEntry> entries = cs.getEntriesByInstAndCsIdx(inst, csIdx);
        data.setEntries(entries);
        log.debug("[SMOKE] entries size={}", entries == null ? null : entries.size());
        List<CounselLog> counselLogs = normalizeCounselLogs(
                Optional.ofNullable(cs.getCounselLog(inst, csIdx)).orElse(Collections.emptyList()));
        model.addAttribute("cslog", counselLogs);
        Map<String, Object> admissionPledge = Optional.ofNullable(cs.getAdmissionPledge(inst, csIdx))
                .orElse(Collections.emptyMap());
        model.addAttribute("admissionPledge", admissionPledge);
        CounselReservation linkedReservation = cs.getCounselReservationByLinkedCsIdx(inst, csIdx);
        model.addAttribute("reservationId", linkedReservation != null ? linkedReservation.getId() : null);
        model.addAttribute("reservationLink",
                linkedReservation != null ? toReservationLink(linkedReservation) : Collections.emptyMap());

        // 3) isEdit일 때만 추가 정보(복호화/보호자)
        if (isEdit) {
            // 1) 환자명: 이미 평문이면 그대로 두고, HEX처럼 보일 때만 복호화
            String p = data.getCs_col_01();
            log.debug("[BEFORE DEC] cs_col_01 = {}", p);
            boolean looksHex = (p != null && p.matches("(?i)^[0-9a-f]{32,}$")); // 단순 HEX 판별

            if (isEdit && looksHex) {
                try {
                    String dec = mysqlAesDecryptHexToUtf8(p, aesKey);
                    if (dec == null || dec.isBlank()) {
                        log.warn("patient name decrypt blank; set empty. cs_idx={}", csIdx);
                        data.setCs_col_01("");
                    } else {
                        data.setCs_col_01(dec);
                    }
                } catch (Exception e) {
                    log.warn("patient name decrypt fail; set empty. cs_idx={}, err={}", csIdx, e.toString());
                    data.setCs_col_01("");
                }
            }

            // 디버그
            log.debug("[CHECK] cs_idx={}, cs_col_01(raw)={}, isEdit={}, looksHex={}, cs_col_01(final)={}",
                    csIdx, p, isEdit, looksHex, data.getCs_col_01());
            log.debug("[AFTER  DEC] cs_col_01 = {}", data.getCs_col_01());

            // 2) 보호자: 각 값이 HEX처럼 보일 때만 복호화
            List<Guardian> guardians = Optional.ofNullable(cs.getGuardiansById(inst, csIdx))
                    .orElseGet(Collections::emptyList)
                    .stream()
                    .filter(Objects::nonNull) // ★ null 요소 제거
                    .toList();

            for (Guardian g : guardians) {
                String n = g.getName();
                if (n != null && isLikelyHex(n)) {
                    try {
                        g.setName(mysqlAesDecryptHexToUtf8(n, aesKey));
                    } catch (Exception ignored) {
                    }
                }
                String c = g.getContact_number();
                if (c != null && isLikelyHex(c)) {
                    try {
                        g.setContact_number(mysqlAesDecryptHexToUtf8(c, aesKey));
                    } catch (Exception ignored) {
                    }
                }
            }
            model.addAttribute("guardians", guardians);
        } else

        {
            model.addAttribute("guardians", Collections.emptyList());
        }

        // 4) 공통 모델
        Map<String, Object> m = model.asMap();
        @SuppressWarnings("unchecked")
        List<Category1WithSubcategoriesAndOptions> categoryData = (List<Category1WithSubcategoriesAndOptions>) m
                .get("categoryData");
        @SuppressWarnings("unchecked")
        Map<String, String> fieldTypeMapping = (Map<String, String>) m.get("fieldTypeMapping");
        @SuppressWarnings("unchecked")
        Map<String, List<Category3>> fieldOptionsMapping = (Map<String, List<Category3>>) m.get("fieldOptionsMapping");

        // 5) valueMap 빌드(이 타이밍에 entries가 셋팅돼 있어야 함)
        log.debug("entries for valueMap = {}", data.getEntries() == null ? null : data.getEntries().size());
        Map<String, String> valueMap = buildValueMap(data, categoryData, fieldTypeMapping, fieldOptionsMapping);
        log.debug("DBG csData.cs_col_01 before view = {}", data.getCs_col_01());
        // 6) 모델 바인딩
        model.addAttribute("csData", data);
        model.addAttribute("csEntries", Optional.ofNullable(data.getEntries()).orElseGet(Collections::emptyList));
        model.addAttribute("isEdit", isEdit);
        model.addAttribute("valueMap", valueMap);
        log.debug("valueMap keys sample = {}", valueMap.keySet().stream().limit(10).toList());

        return

        isMobile(req) ? "csm/counsel/newMobile" : "csm/counsel/new";
    }

    @GetMapping({ "getGuardianData", "/getGuardianData", "csm/getGuardianData", "/csm/getGuardianData" })
    @ResponseBody
    public List<CounselLogGuardian> getGuardianData(@RequestParam("logIdx") int logIdx, HttpSession session) {
        String inst = ensureInst(session);
        if (inst == null || logIdx <= 0) {
            return Collections.emptyList();
        }
        try {
            return Optional.ofNullable(cs.getCounselLogGuardianByLogIdx(inst, logIdx))
                    .orElse(Collections.emptyList());
        } catch (Exception e) {
            log.warn("[getGuardianData] load fail inst={}, logIdx={}, err={}", inst, logIdx, e.toString());
            return Collections.emptyList();
        }
    }

    @GetMapping({ "counsel/log/detail", "/counsel/log/detail", "csm/counsel/log/detail", "/csm/counsel/log/detail" })
    @ResponseBody
    public Map<String, Object> getCounselLogDetail(@RequestParam("logIdx") int logIdx, HttpSession session) {
        String inst = ensureInst(session);
        if (inst == null || logIdx <= 0) {
            return Map.of("result", "0", "message", "invalid request");
        }
        try {
            CounselLog logRow = cs.getCounselLogByIdx(inst, logIdx);
            if (logRow == null) {
                return Map.of("result", "0", "message", "log not found");
            }

            normalizeCounselLog(logRow);

            Map<String, Object> payload = new HashMap<>();
            payload.put("idx", logRow.getIdx());
            payload.put("cs_idx", logRow.getCs_idx());
            payload.put("name", safeString(logRow.getName()));
            payload.put("counsel_content", safeString(logRow.getCounsel_content()));
            payload.put("counsel_method", safeString(logRow.getCounsel_method()));
            payload.put("counsel_result", safeString(logRow.getCounsel_result()));
            payload.put("counsel_name", safeString(logRow.getCounsel_name()));
            payload.put("counsel_at", safeString(logRow.getCounsel_at()));
            payload.put("created_at",
                    logRow.getCreated_at() == null ? "" : new SimpleDateFormat("yyyy-MM-dd").format(logRow.getCreated_at()));

            Map<String, Object> response = new HashMap<>();
            response.put("result", "1");
            response.put("log", payload);
            return response;
        } catch (Exception e) {
            log.warn("[getCounselLogDetail] load fail inst={}, logIdx={}, err={}", inst, logIdx, e.toString());
            return Map.of("result", "0", "message", "load fail");
        }
    }

    // ================== 헬퍼 ==================
    static final class VState {
        boolean checkbox;
        boolean radio;
        String select; // 선택값
        String text; // 최종 텍스트 후보
    }

    private Map<String, String> buildValueMap(
            CounselData data,
            List<Category1WithSubcategoriesAndOptions> categoryData,
            Map<String, String> fieldTypeMapping,
            Map<String, List<Category3>> fieldOptionsMapping) {

        Map<String, String> out = new HashMap<>();
        if (data == null)
            return out;

        List<CounselDataEntry> entries = data.getEntries();
        if (entries == null || entries.isEmpty())
            return out;

        // 널가드
        if (categoryData == null)
            categoryData = List.of();
        if (fieldTypeMapping == null)
            fieldTypeMapping = Map.of();

        // fieldKey -> 옵션라벨 집합, fieldKey -> 서브카테고리 라벨
        Map<String, Set<String>> optionLabels = new HashMap<>();
        Map<String, String> subLabels = new HashMap<>();

        for (var c1w : categoryData) {
            if (c1w == null || c1w.getCategory1() == null)
                continue;
            var c1 = c1w.getCategory1();
            var subs = c1w.getSubcategories();
            if (subs == null)
                continue;

            for (var c2w : subs) {
                if (c2w == null || c2w.getCategory2() == null)
                    continue;
                var c2 = c2w.getCategory2();

                String fieldKey = "field_" + c1.getCc_col_01() + "_" + c2.getCc_col_01();
                subLabels.put(fieldKey, c2.getCc_col_02()); // 서브카테고리 라벨(예: "말기암(암명)")

                var opts = c2w.getOptions();
                Set<String> labels = (opts == null) ? java.util.Collections.emptySet()
                        : opts.stream()
                                .map(opt -> {
                                    if (opt == null)
                                        return null;
                                    String label = opt.getCc_col_03AsString();
                                    return (label == null || label.isBlank()) ? opt.getCc_col_02() : label;
                                })
                                .filter(s -> s != null && !s.isBlank())
                                .collect(java.util.stream.Collectors.toSet());
                optionLabels.put(fieldKey, labels);
            }
        }

        for (var e : entries) {
            if (e == null)
                continue;

            String base = "field_" + e.getCategory_id() + "_" + e.getSubcategory_id();
            String type = fieldTypeMapping.getOrDefault(base, "");
            String val = e.getValue();

            if (type.contains("checkbox"))
                out.put(base + "_checkbox", "on");
            if (type.contains("radio"))
                out.put(base + "_radio", "on");
            if (type.contains("select"))
                out.put(base + "_select", (val == null ? "" : val));

            if (type.contains("text") && val != null && !val.isBlank()) {
                var labels = optionLabels.getOrDefault(base, java.util.Collections.emptySet());
                var c2Lbl = subLabels.get(base);

                String extracted = extractUserText(val, labels, c2Lbl);
                if (extracted != null && !extracted.isBlank()) {
                    String key = base + "_text";
                    String prev = out.get(key);
                    // 여러 행이 있을 수 있으니, 기존보다 더 “의미 있는” 값이면 갱신(간단히 빈 값이면 교체)
                    if (prev == null || prev.isBlank() || extracted.length() > prev.length()) {
                        out.put(key, extracted);
                    }
                }
            }
        }

        log.debug("fieldTypeMapping size={}", fieldTypeMapping.size());
        log.debug("optionLabels size={}", optionLabels.size());
        log.debug("valueMap size={}", out.size());
        return out;
    }

    /** "라벨|사용자입력" / "사용자입력|라벨" / "라벨" 등에서 사용자 입력만 추출 */
    private static String extractUserText(String raw, java.util.Set<String> optLabels, String c2Label) {
        if (raw == null)
            return null;
        String s = raw.trim();
        if (s.isEmpty())
            return null;

        // 라벨 집합(옵션 라벨 + 서브카테고리 라벨)
        java.util.Set<String> labels = new java.util.HashSet<>(optLabels != null ? optLabels : java.util.Set.of());
        if (c2Label != null && !c2Label.isBlank())
            labels.add(c2Label.trim());

        String[] parts = s.split("\\|");
        for (String part : parts) {
            String p = part == null ? "" : part.trim();
            if (!p.isEmpty() && !labels.contains(p)) {
                return p; // ← 라벨이 아닌 토큰 = 사용자 입력
            }
        }
        // ★ 변경 포인트: 모든 토큰이 라벨이면 null 반환(= text 표시 안 함)
        return null;
    }

    private Userdata ensureUserInfo(HttpSession session, String inst) {
        Userdata userinfo = (Userdata) session.getAttribute("userInfo");
        if (userinfo != null)
            return userinfo;

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            String username = auth.getName(); // 로그인 아이디
            // 💡 inst는 ensureInst(session)로 확보했으니 그대로 사용
            Userdata reloaded = cs.loadUserInfo(inst, username);
            if (reloaded != null) {
                session.setAttribute("userInfo", reloaded);
            }
            return reloaded;
        }
        return null;
    }

    private void populateModuleFeatureModel(Model model, String inst) {
        Map<String, Boolean> featureStates = moduleFeatureService.getFeatureStateMap(inst);
        model.addAttribute("moduleRoomBoardEnabled",
                Boolean.TRUE.equals(featureStates.get(ModuleFeatureService.FEATURE_ROOM_BOARD)));
        model.addAttribute("moduleCounselAudioEnabled",
                Boolean.TRUE.equals(featureStates.get(ModuleFeatureService.FEATURE_COUNSEL_AUDIO)));
        model.addAttribute("moduleAdmissionPledgeEnabled",
                Boolean.TRUE.equals(featureStates.get(ModuleFeatureService.FEATURE_ADMISSION_PLEDGE)));
        model.addAttribute("moduleCounselFileEnabled",
                Boolean.TRUE.equals(featureStates.get(ModuleFeatureService.FEATURE_COUNSEL_FILE)));
        model.addAttribute("clovaSpeechConfigured", isClovaSpeechConfigured());
        model.addAttribute("openAiConfigured", isOpenAiConfigured());
    }

    private boolean isClovaSpeechConfigured() {
        return !resolveClovaInvokeEndpoint().isBlank() && !safeString(clovaSecretKey).trim().isBlank();
    }

    private boolean isOpenAiConfigured() {
        return !safeString(openAiApiKey).trim().isBlank();
    }

    /** 세션에 inst가 없으면 인증정보에서 복구 시도 */
    private String ensureInst(HttpSession session) {
        Object v = session.getAttribute("inst");
        if (v instanceof String s && !s.isBlank())
            return s;

        var ctx = SecurityContextHolder.getContext();
        if (ctx == null)
            return null;

        var auth = ctx.getAuthentication();
        if (auth == null)
            return null;

        String inst = null;
        Object details = auth.getDetails();
        if (details instanceof InstDetails id) {
            inst = id.normalized(); // ← 여기!
        }

        if (inst != null && !inst.isBlank()) {
            session.setAttribute("inst", inst);
            return inst;
        }
        return null;
    }

    private boolean isCoreInst(String inst) {
        return inst != null && "core".equalsIgnoreCase(inst.trim());
    }

    private boolean isModuleEnabled(String inst, String featureCode) {
        return moduleFeatureService.isEnabled(inst, featureCode);
    }

    private ResponseEntity<Map<String, Object>> featureDisabledResponse(String message) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of(
                "success", false,
                "message", message));
    }

    private String loginRedirectPath(HttpServletRequest request) {
        String contextPath = request != null ? request.getContextPath() : "";
        if (contextPath == null || contextPath.isBlank() || "/".equals(contextPath)) {
            return "/login";
        }
        return contextPath + "/login";
    }

    private int parseIntSafely(Object value, int defaultValue) {
        if (value == null)
            return defaultValue;
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private long parseLongSafely(Object value, long defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            return Long.parseLong(String.valueOf(value).trim());
        } catch (Exception e) {
            return defaultValue;
        }
    }

    private String normalizeCoreNoticeStatusParam(String status, boolean allowAll) {
        String raw = safeString(status).trim();
        if (raw.isEmpty()) {
            return allowAll ? "ALL" : "DRAFT";
        }
        String upper = raw.toUpperCase(Locale.ROOT);
        if (allowAll && ("ALL".equals(upper) || "전체".equals(raw))) {
            return "ALL";
        }
        if ("게시".equals(raw) || "PUBLISHED".equals(upper)) {
            return "PUBLISHED";
        }
        if ("보관".equals(raw) || "ARCHIVED".equals(upper)) {
            return "ARCHIVED";
        }
        return "DRAFT";
    }

    private String normalizeReservationStatusParam(String status, boolean allowAll) {
        String raw = safeString(status).trim();
        if (raw.isEmpty()) {
            return allowAll ? "ALL" : "RESERVED";
        }
        String upper = raw.toUpperCase(Locale.ROOT);
        if (allowAll && ("ALL".equals(upper) || "전체".equals(raw))) {
            return "ALL";
        }
        if ("접수".equals(raw) || "예약".equals(raw) || "RESERVED".equals(upper)) {
            return "RESERVED";
        }
        if ("입원상담연계".equals(raw) || "입원상담 연계".equals(raw)
                || "입원상담이관".equals(raw) || "입원상담 이관".equals(raw)
                || "완료".equals(raw) || "COMPLETED".equals(upper)) {
            return "COMPLETED";
        }
        if ("취소".equals(raw) || "CANCELLED".equals(upper) || "CANCELED".equals(upper)) {
            return "CANCELLED";
        }
        return "RESERVED";
    }

    private Map<String, Integer> countReservationStatus(List<CounselReservation> reservations) {
        LinkedHashMap<String, Integer> out = new LinkedHashMap<>();
        out.put("all", 0);
        out.put("reserved", 0);
        out.put("completed", 0);
        out.put("cancelled", 0);
        if (reservations == null || reservations.isEmpty()) {
            return out;
        }
        for (CounselReservation reservation : reservations) {
            if (reservation == null) {
                continue;
            }
            String status = normalizeReservationStatusParam(reservation.getStatus(), false);
            if ("RESERVED".equals(status)) {
                out.put("reserved", out.get("reserved") + 1);
            } else if ("COMPLETED".equals(status)) {
                out.put("completed", out.get("completed") + 1);
            } else if ("CANCELLED".equals(status)) {
                out.put("cancelled", out.get("cancelled") + 1);
            }
        }
        out.put("all", out.get("reserved") + out.get("completed") + out.get("cancelled"));
        return out;
    }

    private String toDateTimeLocalValue(String raw) {
        String value = safeString(raw).trim();
        if (value.isEmpty()) {
            return "";
        }
        List<DateTimeFormatter> formatters = List.of(
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"));
        for (DateTimeFormatter formatter : formatters) {
            try {
                LocalDateTime dt = LocalDateTime.parse(value, formatter);
                return dt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"));
            } catch (DateTimeParseException ignore) {
            }
        }
        return "";
    }

    private String resolveReservationActor(String inst, HttpSession session) {
        Object userInfo = session != null ? session.getAttribute("userInfo") : null;
        if (userInfo instanceof Userdata user) {
            String userId = safeString(user.getUs_col_02()).trim();
            if (!userId.isEmpty()) {
                return userId;
            }
        }
        Authentication auth = SecurityContextHolder.getContext() == null ? null
                : SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            String name = safeString(auth.getName()).trim();
            if (!name.isEmpty() && !"anonymousUser".equalsIgnoreCase(name)) {
                return name;
            }
        }
        String safeInst = safeString(inst).trim();
        return safeInst.isEmpty() ? "system" : safeInst + "_system";
    }

    private String resolveNoticeUserId(HttpSession session) {
        Object userInfo = session != null ? session.getAttribute("userInfo") : null;
        if (userInfo instanceof Userdata user) {
            String userId = safeString(user.getUs_col_02()).trim();
            if (!userId.isEmpty()) {
                return userId;
            }
        }
        Authentication auth = SecurityContextHolder.getContext() == null ? null
                : SecurityContextHolder.getContext().getAuthentication();
        if (auth != null) {
            String name = safeString(auth.getName()).trim();
            if (!name.isEmpty() && !"anonymousUser".equalsIgnoreCase(name)) {
                return name;
            }
        }
        return "";
    }

    private Integer resolveNoticeUserIdx(HttpSession session) {
        Object userInfo = session != null ? session.getAttribute("userInfo") : null;
        if (userInfo instanceof Userdata user) {
            Integer userIdx = user.getUs_col_01();
            if (userIdx != null && userIdx > 0) {
                return userIdx;
            }
        }
        return null;
    }

    private String resolveNoticeActor(String inst, HttpSession session) {
        String userId = resolveNoticeUserId(session);
        if (!userId.isBlank()) {
            return userId;
        }
        String safeInst = safeString(inst).trim();
        return safeInst.isBlank() ? "core_system" : safeInst + "_system";
    }

    private Map<String, Object> toReservationLink(CounselReservation reservation) {
        if (reservation == null || reservation.getId() == null || reservation.getId() <= 0) {
            return Collections.emptyMap();
        }
        String status = normalizeReservationStatusParam(reservation.getStatus(), false);
        LinkedHashMap<String, Object> out = new LinkedHashMap<>();
        out.put("id", reservation.getId());
        out.put("patientName", safeString(reservation.getPatient_name()));
        out.put("patientPhone", safeString(reservation.getPatient_phone()));
        out.put("guardianName", safeString(reservation.getGuardian_name()));
        out.put("reservedAt", safeString(reservation.getReserved_at()));
        out.put("priority", reservation.getPriority() == null ? 3 : reservation.getPriority());
        out.put("status", status);
        out.put("statusLabel", switch (status) {
            case "COMPLETED" -> "입원상담 연계";
            case "CANCELLED" -> "취소";
            default -> "접수";
        });
        out.put("url", "/counsel/reservation?status=ALL&reservationId=" + reservation.getId());
        return out;
    }

    private int parseBool01(Object value) {
        if (value == null)
            return 0;
        String s = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        if ("1".equals(s) || "true".equals(s) || "y".equals(s) || "on".equals(s))
            return 1;
        return 0;
    }

    private Integer parseNullableBool01(Object value) {
        if (value == null) {
            return null;
        }
        String s = String.valueOf(value).trim().toLowerCase(Locale.ROOT);
        if ("1".equals(s) || "true".equals(s) || "y".equals(s) || "on".equals(s)) {
            return 1;
        }
        if ("0".equals(s) || "false".equals(s) || "n".equals(s) || "off".equals(s)) {
            return 0;
        }
        return null;
    }

    private List<String> toStringList(Object value) {
        if (value == null) {
            return List.of();
        }
        if (value instanceof List<?> list) {
            List<String> result = new ArrayList<>();
            for (Object item : list) {
                if (item == null) {
                    continue;
                }
                String s = String.valueOf(item).trim();
                if (!s.isBlank()) {
                    result.add(s);
                }
            }
            return result;
        }
        String raw = String.valueOf(value).trim();
        if (raw.isBlank()) {
            return List.of();
        }
        String[] parts = raw.split(",");
        List<String> result = new ArrayList<>();
        for (String part : parts) {
            if (part == null) {
                continue;
            }
            String s = part.trim();
            if (!s.isBlank()) {
                result.add(s);
            }
        }
        return result;
    }

    private String readAsString(Map<String, Object> map, String... keys) {
        for (String key : keys) {
            if (map.containsKey(key) && map.get(key) != null) {
                return String.valueOf(map.get(key));
            }
        }
        return "";
    }

    private Map<String, Object> normalizeCallbackPayload(Map<String, Object> payload) {
        Map<String, Object> normalized = new HashMap<>();
        if (payload == null || payload.isEmpty()) {
            return normalized;
        }
        for (Map.Entry<String, Object> e : payload.entrySet()) {
            String key = e.getKey();
            Object value = e.getValue();
            if (key == null) {
                continue;
            }
            normalized.putIfAbsent(key, value);
            normalized.putIfAbsent(key.toLowerCase(Locale.ROOT), value);
            if (value instanceof Map<?, ?> nested) {
                for (Map.Entry<?, ?> ne : nested.entrySet()) {
                    if (ne.getKey() == null) {
                        continue;
                    }
                    String nk = String.valueOf(ne.getKey());
                    normalized.putIfAbsent(nk, ne.getValue());
                    normalized.putIfAbsent(nk.toLowerCase(Locale.ROOT), ne.getValue());
                }
            }
        }
        return normalized;
    }

    private String buildSmsRefkey(String inst, String incomingRefkey) {
        String normalizedInst = inst == null ? "" : inst.replaceAll("[^A-Za-z0-9_]", "");
        if (incomingRefkey != null) {
            String trimmed = incomingRefkey.trim();
            if (!trimmed.isBlank() && trimmed.startsWith(normalizedInst)) {
                return trimmed;
            }
        }
        String ts = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
        int rnd = 1000 + (int) (Math.random() * 9000);
        return normalizedInst + ts + rnd;
    }

    private double parseDouble(String value) {
        if (value == null || value.isBlank()) {
            return 0.0;
        }
        try {
            return Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    /** Criteria 바인딩 & 검색 키워드 처리 */
    private void bindCriteria(
            Criteria cri, String inst, int page, int perPageNum,
            String dateRange, String startDate, String endDate, String status, String pathType,
            String searchType, String keyword, String end) {

        cri.setInst(inst);
        cri.setAesKey(aesKey);
        cri.setPage(page);
        cri.setPerPageNum(perPageNum);
        cri.setDateRange(nullToEmpty(dateRange));
        cri.setStartDate(normalizeDateParam(startDate));
        cri.setEndDate(normalizeDateParam(endDate));
        cri.setStatus(normalizeListFilterParam(status));
        cri.setPathType(normalizeListFilterParam(pathType));
        cri.setSearchType(nullToEmpty(searchType));
        cri.setKey(nullToEmpty(keyword)); // 기존 코드 호환: setKey 사용하더라도 아래 setKeyword가 최종 적용

        // 검색어 처리
        if (keyword == null || keyword.trim().isEmpty()) {
            cri.setKeyword(null);
            cri.setKeywordBytes(null);
            log.debug("검색어 없음 → 키워드 조건 제외");
        } else if ("phone".equals(searchType)) {
            if (keyword.length() == 4) {
                // 뒷자리/중간 4자리 LIKE
                cri.setKeyword(keyword);
                cri.setKeywordBytes(null);
            } else {
                // 전체번호 SHA-256
                cri.setKeywordBytes(hashSHA256(keyword));
                cri.setKeyword(keyword);
            }
        } else if ("patient".equals(searchType) || "guardian".equals(searchType)) {
            // 환자/보호자 이름 SHA-256
            cri.setKeywordBytes(hashSHA256(keyword));
            cri.setKeyword(keyword);
        } else if ("counselor".equals(searchType) || "content".equals(searchType)) {
            // 그대로 전달
            cri.setKeyword(keyword);
            cri.setKeywordBytes(null);
        } else {
            // 일반 LIKE
            cri.setKeyword(keyword);
            cri.setKeywordBytes(null);
        }

        cri.setEnd(end);
    }

    private String normalizeListFilterParam(String value) {
        String normalized = nullToEmpty(value).trim();
        if (normalized.isBlank()) {
            return "all";
        }
        return normalized;
    }

    private String normalizeDateParam(String value) {
        String normalized = nullToEmpty(value).trim();
        if (normalized.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return normalized;
        }
        return "";
    }

    /** 목록 후처리: 복호화 + 기관별 마스킹 */
    /**
     * 목록 후처리: 복호화 + 기관별 마스킹
     * - cs_col_01: VARBINARY → (VO에 byte[] 존재 시) 바이트 직복호화 우선, 아니면 HEX 문자열 복호화
     * - guardians.name/contact_number: HEX로 들어오면 복호화 시도
     */
    private void postProcessDecryptAndMask(List<CounselData> list, String inst, String searchType, String keyword) {
        if (list == null || list.isEmpty())
            return;

        int decFail = 0;

        for (CounselData cd : list) {
            if (cd == null)
                continue;

            // --- 1) 환자명 ---
            try {
                boolean replaced = false;

                // 1-1) raw 바이트가 매핑돼 있으면 우선 사용 (VO에 byte[] cs_col_01_raw 추가되어 있어야 함)
                // getCs_col_01_raw()가 없다면 이 블록은 건너뛴다.
                try {
                    Method rawGetter = cd.getClass().getMethod("getCs_col_01_raw");
                    Object rawObj = rawGetter.invoke(cd);
                    if (rawObj instanceof byte[] raw && raw.length > 0) {
                        String plain = decryptBytesAES(raw, aesKey);
                        if (plain != null) {
                            cd.setCs_col_01(plain);
                            replaced = true;
                        }
                    }
                } catch (NoSuchMethodException ignore) {
                    // VO에 raw가 없으면 무시
                } catch (Exception e) {
                    decFail++;
                    log.debug("[{}] decrypt raw bytes fail: {}", inst, e.toString());
                }

                // 1-2) raw가 없거나 실패한 경우: HEX 텍스트로 판단되면 복호화
                if (!replaced) {
                    String p = cd.getCs_col_01();
                    if (isLikelyHex(p)) {
                        String dec = mysqlAesDecryptHexToUtf8(p, aesKey);
                        if (dec != null) {
                            cd.setCs_col_01(dec);
                            replaced = true;
                        }
                    }
                }
                // replaced=false면 원본 유지
            } catch (Exception e) {
                decFail++;
            }

            // --- 2) 보호자 목록 ---
            if (cd.getGuardians() != null) {
                for (Guardian g : cd.getGuardians()) {
                    if (g == null)
                        continue;

                    // name
                    String gn = g.getName();
                    if (isLikelyHex(gn)) {
                        try {
                            String dec = mysqlAesDecryptHexToUtf8(gn, aesKey);
                            if (dec != null)
                                g.setName(dec);
                        } catch (Exception e) {
                            decFail++;
                        }
                    }

                    // contact_number
                    String gc = g.getContact_number();
                    if (isLikelyHex(gc)) {
                        try {
                            String dec = mysqlAesDecryptHexToUtf8(gc, aesKey);
                            if (dec != null)
                                g.setContact_number(dec);
                        } catch (Exception e) {
                            decFail++;
                        }
                    }
                }
            }

            // 3) 평문 컬럼들 (cs_col_07/08/11 등)은 건드리지 않음
        }

        log.debug("[POST] after: size={} (decFail={} hashMismatch=0)", list.size(), decFail);
    }

    // HEX 판정 (짝수 길이 & [0-9a-fA-F]만)
    private boolean isLikelyHex(String s) {
        if (s == null)
            return false;
        int n = s.length();
        if (n < 2 || (n % 2) != 0)
            return false;
        for (int i = 0; i < n; i++) {
            char c = s.charAt(i);
            boolean hex = (c >= '0' && c <= '9') ||
                    (c >= 'a' && c <= 'f') ||
                    (c >= 'A' && c <= 'F');
            if (!hex)
                return false;
        }
        return true;
    }

    private String mysqlAesDecryptHexToUtf8(String hex, String key) throws Exception {
        byte[] cipherBytes = hexStringToBytes(hex);
        javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/ECB/PKCS5Padding");
        javax.crypto.spec.SecretKeySpec sk = new javax.crypto.spec.SecretKeySpec(
                key.getBytes(java.nio.charset.StandardCharsets.UTF_8), "AES");
        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, sk);
        byte[] plain = cipher.doFinal(cipherBytes);
        return new String(plain, java.nio.charset.StandardCharsets.UTF_8);
    }

    private byte[] hexStringToBytes(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i + 1), 16));
        }
        return data;
    }

    /** VARBINARY 암호문(raw bytes)을 바로 복호화 */
    private String decryptBytesAES(byte[] rawCipher, String key) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        SecretKeySpec sk = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES");
        cipher.init(Cipher.DECRYPT_MODE, sk);
        byte[] plain = cipher.doFinal(rawCipher);
        return new String(plain, StandardCharsets.UTF_8);
    }

    private static boolean hashMatchesFlexible(String plain, Object stored) {
        if (plain == null || stored == null)
            return false;

        String storedAsBase64;
        if (stored instanceof byte[] bytes) {
            // DB가 RAW bytes로 저장된 경우: Base64로 맞춰서 비교(아래 계산도 Base64)
            storedAsBase64 = Base64.getEncoder().encodeToString(bytes);
        } else {
            storedAsBase64 = String.valueOf(stored).trim();
            // 만약 DB가 "hex"라면 여기서 감지해서 변환 필요.
            // 간단 감지: hex만으로 구성되고 길이가 64이면 hex로 보자.
            if (storedAsBase64.matches("^[0-9a-fA-F]{64}$")) {
                // hex -> bytes -> base64
                byte[] bytes = hexToBytes(storedAsBase64);
                storedAsBase64 = Base64.getEncoder().encodeToString(bytes);
            }
        }

        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(plain.getBytes(StandardCharsets.UTF_8));
            String plainBase64 = Base64.getEncoder().encodeToString(digest);
            return plainBase64.equals(storedAsBase64);
        } catch (NoSuchAlgorithmException e) {
            return false;
        }
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] out = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            out[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return out;
    }

    /** 복호화 시도: 성공하면 평문, 실패하면 null */
    private String tryDecryptSafe(String ciphertext, String inst) {
        if (ciphertext == null || ciphertext.isBlank())
            return null;
        try {
            return aes.decryptECB(ciphertext);
        } catch (Exception e) {
            log.debug("[{}] decrypt fail: {}", inst, e.toString());
            return null;
        }
    }

    private static boolean hashMatches(String plain, byte[] stored) {
        if (plain == null || stored == null)
            return false;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(plain.getBytes(StandardCharsets.UTF_8));
            // 원시 바이트 동일 비교
            if (java.util.Arrays.equals(digest, stored))
                return true;

            // 혹시 DB에 base64 문자열이 BLOB로 들어간 특이 케이스도 커버
            String base64 = Base64.getEncoder().encodeToString(digest);
            String storedStr = new String(stored, StandardCharsets.UTF_8);
            return base64.equals(storedStr);
        } catch (NoSuchAlgorithmException e) {
            return false;
        }
    }

    private static String safeString(String s) {
        return s == null ? "" : s;
    }

    private String normalizeInternalReturnUrl(String raw) {
        String value = safeString(raw).trim();
        if (value.isEmpty()) {
            return "";
        }
        if (!value.startsWith("/") || value.startsWith("//")) {
            return "";
        }
        return value;
    }

    private static String mask(String s) {
        return (s == null || s.isEmpty()) ? "" : "****";
    }

    private static boolean matchesKeyword(CounselData row, String searchType, String keyword) {
        if (row == null)
            return false;
        if (keyword == null || keyword.isBlank())
            return true;

        final String kw = normalize(keyword);
        final String kwDigits = digits(keyword);

        try {
            switch (String.valueOf(searchType)) {
                case "patient":
                    // (가급적 실제 환자명 필드로 교체하세요)
                    return anyStringField(row, v -> contains(v, kw), f -> isNameLikeField(f))
                            || anyGuardian(row, g -> contains(g.getName(), kw));

                case "guardian":
                    return anyGuardian(row, g -> contains(g.getName(), kw));

                case "phone":
                    return anyStringField(row, v -> digits(v).contains(kwDigits), f -> isPhoneField(f))
                            || anyGuardian(row, g -> digits(g.getContact_number()).contains(kwDigits));

                case "counselor":
                    return anyStringField(row, v -> contains(v, kw), f -> isCounselorField(f));

                case "content":
                    return anyStringField(row, v -> contains(v, kw), f -> isContentField(f));

                default:
                    // 전체 문자열 필드 + 보호자 이름/전화 어느 하나라도 매칭되면 OK
                    return anyStringField(row, v -> contains(v, kw), f -> true)
                            || anyGuardian(row, g -> contains(g.getName(), kw) ||
                                    digits(g.getContact_number()).contains(kwDigits));
            }
        } catch (Exception e) {
            // 예비 필터 실패 시 실패-오픈 (리스트 날려먹지 않기)
            return true;
        }
    }
    // ===== 유틸 =====

    private static byte[] hashSHA256(String text) {
        try {
            var md = java.security.MessageDigest.getInstance("SHA-256");
            return md.digest(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 생성 실패", e);
        }
    }

    private static String safeDecrypt(AES128 aes, String ciphertext) {
        if (ciphertext == null || ciphertext.isBlank())
            return ciphertext;
        try {
            return aes.decryptECB(ciphertext);
        } catch (Exception e) {
            // 복호화 실패 시 원본 유지(로그만)
            log.warn("복호화 실패: {}", e.getMessage());
            return ciphertext;
        }
    }

    /** 이름 마스킹: 홍길동 → 홍*동 */
    private static String maskName(String name) {
        if (name == null || name.length() < 2)
            return name;
        if (name.length() == 2)
            return name.charAt(0) + "*";
        return name.charAt(0) + "*".repeat(name.length() - 2) + name.charAt(name.length() - 1);
    }

    /** 전화번호 마스킹: 010-1234-5678 → 010-****-5678 */
    private static String maskPhoneNumber(String num) {
        if (num == null)
            return null;
        // 매우 단순 마스킹(패턴 다양하면 정규식 보강)
        return num.replaceAll("(\\d{2,3})-(\\d{3,4})-(\\d{4})", "$1-****-$3");
    }

    // ---------- 헬퍼들 ----------

    private static boolean anyStringField(Object bean, Predicate<String> valueTest, Predicate<Field> fieldFilter) {
        for (Field f : bean.getClass().getDeclaredFields()) {
            if (f.getType() != String.class)
                continue;
            if (!fieldFilter.test(f))
                continue;
            f.setAccessible(true);
            try {
                String v = (String) f.get(bean);
                if (v != null && valueTest.test(v))
                    return true;
            } catch (IllegalAccessException ignored) {
            }
        }
        return false;
    }

    private static boolean anyGuardian(CounselData row, Predicate<Guardian> test) {
        List<Guardian> gl = row.getGuardians();
        if (gl == null)
            return false;
        for (Guardian g : gl) {
            if (g != null && test.test(g))
                return true;
        }
        return false;
    }

    private static String normalize(String s) {
        return s == null ? "" : s.toLowerCase().trim();
    }

    private static boolean contains(String src, String kw) {
        return src != null && normalize(src).contains(kw);
    }

    private static String digits(String s) {
        return s == null ? "" : s.replaceAll("\\D+", "");
    }

    // 필드명 휴리스틱 (필요에 맞게 보완하세요)
    private static boolean isPhoneField(Field f) {
        String n = f.getName().toLowerCase();
        return n.contains("phone") || n.contains("tel") || n.contains("contact") || n.contains("mobile")
                || n.contains("hp");
    }

    private static boolean isNameLikeField(Field f) {
        String n = f.getName().toLowerCase();
        return n.contains("name") || n.contains("patient");
    }

    private static boolean isCounselorField(Field f) {
        String n = f.getName().toLowerCase();
        return n.contains("counselor") || n.contains("manager") || n.contains("agent");
    }

    private static boolean isContentField(Field f) {
        String n = f.getName().toLowerCase();
        return n.contains("content") || n.contains("memo") || n.contains("note") || n.contains("desc");
    }

    private List<Map<String, Object>> toRowMaps(
            List<CounselData> list,
            List<Map<String, Object>> orderItems,
            String inst) {

        if (list == null || list.isEmpty())
            return List.of();

        // 안전: orderItems가 null이어도 최소한 cs_idx, 일부 기본 필드는 넣을 수 있게
        List<Map<String, Object>> cols = orderItems != null ? orderItems : List.of();
        Set<String> requestedDynamicColumns = extractRequestedDynamicColumns(cols);

        List<Map<String, Object>> out = new ArrayList<>(list.size());
        for (CounselData cd : list) {
            if (cd == null)
                continue;

            Map<String, Object> row = new LinkedHashMap<>();
            // 항상 필요한 키
            row.put("cs_idx", safeGet(cd, "cs_idx"));

            Map<String, String> dynamicValueMap = Collections.emptyMap();
            if (!requestedDynamicColumns.isEmpty()) {
                List<CounselDataEntry> entries = cd.getEntries();
                if (entries == null || entries.isEmpty()) {
                    try {
                        entries = cs.getEntriesByInstAndCsIdx(inst, cd.getCs_idx());
                    } catch (Exception ignore) {
                        entries = Collections.emptyList();
                    }
                }
                dynamicValueMap = buildDynamicValueMap(entries);
            }

            // 화면에서 선택된 컬럼만 넣기(직렬화 안전)
            for (Map<String, Object> col : cols) {
                if (col == null)
                    continue;
                String name = normalizeOrderColumnName(col);
                if (name == null || name.isBlank())
                    continue;

                String normalizedName = normalizeListColumnKey(name);
                if (requestedDynamicColumns.contains(normalizedName)) {
                    row.put(name, dynamicValueMap.getOrDefault(normalizedName, ""));
                    continue;
                }

                Object val = safeGet(cd, name); // 스네이크/카멜 대응
                // byte[], Blob 같은 건 날린다(직렬화 이슈 예방)
                if (val instanceof byte[])
                    continue;
                row.put(name, val == null ? "" : val);
            }

            // 보호자 배열(이미 postProcess에서 복호화/마스킹됨)
            List<Guardian> gs = cd.getGuardians();
            if (gs == null || gs.isEmpty()) {
                try {
                    gs = cs.getGuardiansById(inst, cd.getCs_idx());
                } catch (Exception ignore) {
                }
            }
            if (gs != null && !gs.isEmpty()) {
                List<Map<String, Object>> gmaps = new ArrayList<>(gs.size());
                for (Guardian g : gs) {
                    if (g == null)
                        continue;
                    String guardianName = nullToEmpty(g.getName());
                    String guardianPhone = nullToEmpty(g.getContact_number());
                    if (isLikelyHex(guardianName)) {
                        try {
                            String dec = mysqlAesDecryptHexToUtf8(guardianName, aesKey);
                            if (dec != null)
                                guardianName = dec;
                        } catch (Exception ignore) {
                        }
                    }
                    if (isLikelyHex(guardianPhone)) {
                        try {
                            String dec = mysqlAesDecryptHexToUtf8(guardianPhone, aesKey);
                            if (dec != null)
                                guardianPhone = dec;
                        } catch (Exception ignore) {
                        }
                    }
                    Map<String, Object> gm = new LinkedHashMap<>();
                    gm.put("name", guardianName);
                    gm.put("relationship", nullToEmpty(g.getRelationship()));
                    gm.put("contact_number", guardianPhone);
                    gmaps.add(gm);
                }
                row.put("guardians", gmaps);
            } else {
                row.put("guardians", List.of());
            }

            out.add(row);
        }
        return out;
    }

    private List<Map<String, Object>> buildListSettingInnerContentItems(
            String inst,
            List<Map<String, Object>> orderItems,
            Set<String> hiddenColumns) {
        List<Map<String, Object>> baseItems = filterListSettingItems(cs.getInnerContentItems(inst), hiddenColumns);
        Set<String> selectedColumns = extractSelectedColumns(orderItems);

        List<Map<String, Object>> dynamicCandidates = buildDynamicListSettingCandidates(inst);
        List<Map<String, Object>> merged = new ArrayList<>(baseItems);
        for (Map<String, Object> candidate : dynamicCandidates) {
            String columnName = normalizeOrderColumnName(candidate);
            String normalized = normalizeListColumnKey(columnName);
            if (normalized.isBlank()) {
                continue;
            }
            if (isHiddenListColumn(columnName, hiddenColumns) || selectedColumns.contains(normalized)) {
                continue;
            }
            merged.add(candidate);
        }
        return merged;
    }

    private List<Map<String, Object>> normalizeDynamicOrderItemComments(
            String inst,
            List<Map<String, Object>> orderItems) {
        if (orderItems == null || orderItems.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Integer, String> categoryNameMap = buildCategoryNameMap(inst);
        if (categoryNameMap.isEmpty()) {
            return orderItems;
        }

        List<Map<String, Object>> normalized = new ArrayList<>(orderItems.size());
        for (Map<String, Object> item : orderItems) {
            if (item == null) {
                continue;
            }
            Map<String, Object> copy = new LinkedHashMap<>(item);
            String columnName = normalizeListColumnKey(normalizeOrderColumnName(item));
            Integer categoryId = extractDynamicCategoryId(columnName);
            if (categoryId != null) {
                String comment = safeString(categoryNameMap.get(categoryId)).trim();
                if (!comment.isBlank()) {
                    copy.put("column_comment", comment);
                    copy.put("comment", comment);
                }
            }
            normalized.add(copy);
        }
        return normalized;
    }

    private List<Map<String, Object>> buildDynamicListSettingCandidates(String inst) {
        try {
            Map<String, Object> categoryDataMap = cs.getCategoryData(inst);
            Object raw = categoryDataMap == null ? null : categoryDataMap.get("categoryData");
            if (!(raw instanceof List<?> wrappers) || wrappers.isEmpty()) {
                return Collections.emptyList();
            }

            List<Map<String, Object>> items = new ArrayList<>();
            for (Object wrapperObj : wrappers) {
                if (!(wrapperObj instanceof Category1WithSubcategoriesAndOptions wrapper)) {
                    continue;
                }
                Category1 c1 = wrapper.getCategory1();
                if (c1 == null || c1.getCc_col_01() <= 0) {
                    continue;
                }
                String c1Name = safeString(c1.getCc_col_02()).trim();
                String columnName = "category_" + c1.getCc_col_01();
                String comment = c1Name.isBlank() ? columnName : c1Name;
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("column_name", columnName);
                item.put("column_comment", comment);
                items.add(item);
            }
            return items;
        } catch (Exception e) {
            log.warn("[list-setting] dynamic field candidates load failed inst={}, err={}", inst, e.toString());
            return Collections.emptyList();
        }
    }

    private Map<Integer, String> buildCategoryNameMap(String inst) {
        try {
            Map<String, Object> categoryDataMap = cs.getCategoryData(inst);
            Object raw = categoryDataMap == null ? null : categoryDataMap.get("categoryData");
            if (!(raw instanceof List<?> wrappers) || wrappers.isEmpty()) {
                return Collections.emptyMap();
            }
            Map<Integer, String> out = new LinkedHashMap<>();
            for (Object wrapperObj : wrappers) {
                if (!(wrapperObj instanceof Category1WithSubcategoriesAndOptions wrapper)) {
                    continue;
                }
                Category1 c1 = wrapper.getCategory1();
                if (c1 == null || c1.getCc_col_01() <= 0) {
                    continue;
                }
                String c1Name = safeString(c1.getCc_col_02()).trim();
                if (!c1Name.isBlank()) {
                    out.put(c1.getCc_col_01(), c1Name);
                }
            }
            return out;
        } catch (Exception e) {
            log.warn("[list-setting] category name map load failed inst={}, err={}", inst, e.toString());
            return Collections.emptyMap();
        }
    }

    private Integer extractDynamicCategoryId(String normalizedColumn) {
        if (normalizedColumn == null || normalizedColumn.isBlank()) {
            return null;
        }
        try {
            if (normalizedColumn.matches("^category_\\d+$")) {
                return Integer.valueOf(normalizedColumn.substring("category_".length()));
            }
            if (normalizedColumn.matches("^field_\\d+_\\d+$")) {
                String[] parts = normalizedColumn.split("_");
                if (parts.length >= 3) {
                    return Integer.valueOf(parts[1]);
                }
            }
        } catch (NumberFormatException ignore) {
            return null;
        }
        return null;
    }

    private Set<String> extractSelectedColumns(List<Map<String, Object>> items) {
        if (items == null || items.isEmpty()) {
            return Collections.emptySet();
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (Map<String, Object> item : items) {
            String normalized = normalizeListColumnKey(normalizeOrderColumnName(item));
            if (!normalized.isBlank()) {
                out.add(normalized);
            }
        }
        return out;
    }

    private Set<String> extractRequestedDynamicColumns(List<Map<String, Object>> items) {
        if (items == null || items.isEmpty()) {
            return Collections.emptySet();
        }
        LinkedHashSet<String> out = new LinkedHashSet<>();
        for (Map<String, Object> item : items) {
            String normalized = normalizeListColumnKey(normalizeOrderColumnName(item));
            if (isDynamicListColumn(normalized)) {
                out.add(normalized);
            }
        }
        return out;
    }

    private boolean isDynamicListColumn(String key) {
        if (key == null) {
            return false;
        }
        return key.matches("^field_\\d+_\\d+$") || key.matches("^category_\\d+$");
    }

    private Map<String, String> buildDynamicValueMap(List<CounselDataEntry> entries) {
        if (entries == null || entries.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, LinkedHashSet<String>> fieldValues = new LinkedHashMap<>();
        Map<String, LinkedHashSet<String>> categoryValues = new LinkedHashMap<>();
        for (CounselDataEntry entry : entries) {
            if (entry == null || entry.getCategory_id() <= 0) {
                continue;
            }
            String value = safeString(entry.getValue()).trim();
            if (value.isBlank()) {
                continue;
            }

            String categoryKey = "category_" + entry.getCategory_id();
            categoryValues.computeIfAbsent(categoryKey, k -> new LinkedHashSet<>()).add(value);

            if (entry.getSubcategory_id() > 0) {
                String fieldKey = "field_" + entry.getCategory_id() + "_" + entry.getSubcategory_id();
                fieldValues.computeIfAbsent(fieldKey, k -> new LinkedHashSet<>()).add(value);
            }
        }

        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, LinkedHashSet<String>> e : fieldValues.entrySet()) {
            out.put(e.getKey(), String.join(" / ", e.getValue()));
        }
        for (Map.Entry<String, LinkedHashSet<String>> e : categoryValues.entrySet()) {
            out.put(e.getKey(), String.join(" / ", e.getValue()));
        }
        return out;
    }

    private String normalizeOrderColumnName(Map<String, Object> col) {
        if (col == null)
            return null;
        Object raw = col.get("column_name");
        if (raw == null)
            raw = col.get("COLUMN_NAME");
        if (raw == null)
            raw = col.get("coulmn");
        if (raw == null)
            raw = col.get("COULMN");
        if (raw == null)
            return null;

        String name = String.valueOf(raw).trim();
        if (name.isEmpty() || "null".equalsIgnoreCase(name))
            return null;
        if (name.endsWith("[]"))
            name = name.substring(0, name.length() - 2);
        return name;
    }

    private Set<String> getHiddenListColumns() {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        out.add("cs_idx");
        out.add("cs_col_01_hash");
        out.add("created_at");
        out.add("updated_at");
        out.add("cs_col_35");

        String raw = safeString(counselListHiddenColumnsRaw);
        if (!raw.isBlank()) {
            for (String token : raw.split(",")) {
                String normalized = normalizeListColumnKey(token);
                if (!normalized.isBlank()) {
                    out.add(normalized);
                }
            }
        }
        return out;
    }

    private List<Map<String, Object>> filterListSettingItems(List<Map<String, Object>> items,
            Set<String> hiddenColumns) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> item : items) {
            if (item == null) {
                continue;
            }
            String columnName = normalizeOrderColumnName(item);
            if (isHiddenListColumn(columnName, hiddenColumns)) {
                continue;
            }
            filtered.add(item);
        }
        return filtered;
    }

    private boolean isHiddenListColumn(String columnName, Set<String> hiddenColumns) {
        String normalized = normalizeListColumnKey(columnName);
        if (normalized.isBlank()) {
            return true;
        }
        return hiddenColumns != null && hiddenColumns.contains(normalized);
    }

    private String normalizeListColumnKey(String columnName) {
        if (columnName == null) {
            return "";
        }
        String normalized = columnName.trim().toLowerCase(Locale.ROOT);
        if (normalized.endsWith("[]")) {
            normalized = normalized.substring(0, normalized.length() - 2);
        }
        return normalized;
    }

    private static Object safeGet(Object bean, String snakeName) {
        if (bean == null || snakeName == null || snakeName.isBlank())
            return null;

        String camel = snakeToCamel(snakeName);

        String capCamel = Character.toUpperCase(camel.charAt(0)) + camel.substring(1);
        String capSnake = Character.toUpperCase(snakeName.charAt(0)) + snakeName.substring(1);

        // 1) getXxx (camel) 시도: getCsCol01
        try {
            Method m = bean.getClass().getMethod("get" + capCamel);
            return m.invoke(bean);
        } catch (NoSuchMethodException ignored) {
        } catch (Exception e) {
            return null;
        }

        // 2) isXxx (camel) 시도: isCsCol01 (boolean일 때)
        try {
            Method m = bean.getClass().getMethod("is" + capCamel);
            return m.invoke(bean);
        } catch (NoSuchMethodException ignored) {
        } catch (Exception e) {
            return null;
        }

        // 3) getXxx (snake) 시도: ★ getCs_col_01 ← 지금 VO가 이 형태입니다!
        try {
            Method m = bean.getClass().getMethod("get" + capSnake);
            return m.invoke(bean);
        } catch (NoSuchMethodException ignored) {
        } catch (Exception e) {
            return null;
        }

        // 4) isXxx (snake) 시도
        try {
            Method m = bean.getClass().getMethod("is" + capSnake);
            return m.invoke(bean);
        } catch (NoSuchMethodException ignored) {
        } catch (Exception e) {
            return null;
        }

        // 5) BeanWrapper (둘 다)
        try {
            BeanWrapperImpl bw = new BeanWrapperImpl(bean);
            if (bw.isReadableProperty(camel))
                return bw.getPropertyValue(camel);
            if (bw.isReadableProperty(snakeName))
                return bw.getPropertyValue(snakeName);
        } catch (Exception ignored) {
        }

        // 6) 필드 직접 (private 포함)
        try {
            Field f = bean.getClass().getDeclaredField(snakeName);
            f.setAccessible(true);
            return f.get(bean);
        } catch (Exception ignored) {
        }
        try {
            Field f = bean.getClass().getDeclaredField(camel);
            f.setAccessible(true);
            return f.get(bean);
        } catch (Exception ignored) {
        }

        // 7) 혹시 Map이면 키로 직접
        if (bean instanceof Map<?, ?> m) {
            Object v = m.get(snakeName);
            return v != null ? v : m.get(camel);
        }
        return null;
    }

    private static String snakeToCamel(String s) {
        if (s == null || s.isEmpty())
            return "";
        String lower = s.toLowerCase(Locale.ROOT);
        Matcher matcher = Pattern.compile("_([a-z0-9])").matcher(lower);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            // 그룹 1을 대문자로 바꿔 치환
            matcher.appendReplacement(sb, matcher.group(1).toUpperCase(Locale.ROOT));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private String decodeEntryValue(Object v) {
        if (v == null)
            return null;
        if (v instanceof String s)
            return s; // TEXT는 평문 그대로
        if (v instanceof byte[] b) {
            return new String(b, java.nio.charset.StandardCharsets.UTF_8);
        }
        if (v instanceof java.sql.Blob blob) {
            try {
                byte[] b = blob.getBytes(1L, (int) blob.length());
                return new String(b, java.nio.charset.StandardCharsets.UTF_8);
            } catch (Exception ignore) {
                return null;
            } finally {
                try {
                    blob.free();
                } catch (Exception ignore) {
                }
            }
        }
        return String.valueOf(v);
    }
}
