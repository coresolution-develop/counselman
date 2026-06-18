package com.coresolution.cancertreatment.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.coresolution.cancertreatment.model.SessionUser;
import com.coresolution.cancertreatment.model.PatientRequest;
import com.coresolution.cancertreatment.model.PatientRoomRequest;
import com.coresolution.cancertreatment.model.TreatmentPackageRequest;
import com.coresolution.cancertreatment.model.TreatmentScheduleRequest;
import com.coresolution.cancertreatment.model.ScheduleRecurrenceRequest;
import com.coresolution.cancertreatment.model.SettingItemRequest;
import com.coresolution.cancertreatment.model.TreatmentRoomRequest;
import com.coresolution.cancertreatment.model.TreatmentSeatRequest;
import com.coresolution.cancertreatment.model.TherapistRequest;
import com.coresolution.cancertreatment.service.PatientService;
import com.coresolution.cancertreatment.service.TherapistService;
import com.coresolution.cancertreatment.service.TreatmentDurationService;
import com.coresolution.cancertreatment.service.PatientRoomService;
import com.coresolution.cancertreatment.service.SettingService;
import com.coresolution.cancertreatment.service.SsoService;
import com.coresolution.cancertreatment.service.TreatmentPackageService;
import com.coresolution.cancertreatment.service.TreatmentRoomService;
import com.coresolution.cancertreatment.service.TreatmentSeatService;
import com.coresolution.cancertreatment.service.ScheduleRecurrenceService;
import com.coresolution.cancertreatment.service.TreatmentScheduleService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

@Controller
public class TreatmentScheduleController {

    private static final String SESSION_USER = "cancerTreatmentUser";

    private final SsoService ssoService;
    private final TreatmentScheduleService scheduleService;
    private final PatientService patientService;
    private final SettingService settingService;
    private final TreatmentRoomService treatmentRoomService;
    private final TreatmentPackageService treatmentPackageService;
    private final PatientRoomService patientRoomService;
    private final TreatmentSeatService treatmentSeatService;
    private final ScheduleRecurrenceService scheduleRecurrenceService;
    private final TherapistService therapistService;
    private final TreatmentDurationService treatmentDurationService;
    private final String mediplatPortalUrl;

    public TreatmentScheduleController(
            SsoService ssoService,
            TreatmentScheduleService scheduleService,
            PatientService patientService,
            SettingService settingService,
            TreatmentRoomService treatmentRoomService,
            TreatmentPackageService treatmentPackageService,
            PatientRoomService patientRoomService,
            TreatmentSeatService treatmentSeatService,
            ScheduleRecurrenceService scheduleRecurrenceService,
            TherapistService therapistService,
            TreatmentDurationService treatmentDurationService,
            @Value("${cancer-treatment.mediplat-portal-url:http://localhost:8082/portal}") String mediplatPortalUrl) {
        this.ssoService = ssoService;
        this.scheduleService = scheduleService;
        this.patientService = patientService;
        this.settingService = settingService;
        this.treatmentRoomService = treatmentRoomService;
        this.treatmentPackageService = treatmentPackageService;
        this.patientRoomService = patientRoomService;
        this.treatmentSeatService = treatmentSeatService;
        this.scheduleRecurrenceService = scheduleRecurrenceService;
        this.therapistService = therapistService;
        this.treatmentDurationService = treatmentDurationService;
        this.mediplatPortalUrl = mediplatPortalUrl;
    }

    @GetMapping({ "", "/" })
    public String root(HttpSession session) {
        return sessionUser(session) == null ? "redirect:/login-required" : "redirect:/cancer-treatment-schedule";
    }

    @GetMapping("/login-required")
    public String loginRequired() {
        return "redirect:" + mediplatPortalUrl;
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:" + mediplatPortalUrl;
    }

    @GetMapping({ "/mediplat/sso/entry", "mediplat/sso/entry" })
    public String ssoEntry(
            @RequestParam("inst") String inst,
            @RequestParam("userId") String userId,
            @RequestParam("expires") long expires,
            @RequestParam("signature") String signature,
            @RequestParam(name = "target", required = false) String target,
            @RequestParam(name = "role", required = false) String role,
            HttpServletRequest request) {
        String redirectTarget;
        try {
            redirectTarget = ssoService.validateAndResolveTarget(inst, userId, expires, target, role, signature);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage(), e);
        }

        HttpSession session = request.getSession(true);
        request.changeSessionId();
        session = request.getSession(false);
        session.setAttribute(SESSION_USER,
                new SessionUser(inst.trim(), userId.trim(), ssoService.canonicalRole(role)));
        return "redirect:" + redirectTarget;
    }

    @GetMapping("/cancer-treatment-schedule")
    public String page(Model model, HttpSession session) {
        SessionUser user = sessionUser(session);
        if (user == null) {
            return "redirect:/login-required";
        }
        populateShell(model, user);
        model.addAttribute("activeMenu", "schedule");
        return "cancer-treatment-schedule";
    }

    @GetMapping("/treatment-calendar")
    public String calendarPage(Model model, HttpSession session) {
        SessionUser user = sessionUser(session);
        if (user == null) {
            return "redirect:/login-required";
        }
        populateShell(model, user);
        model.addAttribute("activeMenu", "calendar");
        return "treatment-calendar";
    }

    @GetMapping("/patients")
    public String patientsPage(Model model, HttpSession session) {
        SessionUser user = sessionUser(session);
        if (user == null) {
            return "redirect:/login-required";
        }
        populateShell(model, user);
        model.addAttribute("activeMenu", "patients");
        return "patients";
    }

    @GetMapping("/treatment-status-by-room")
    public String treatmentStatusByRoomPage(Model model, HttpSession session) {
        SessionUser user = sessionUser(session);
        if (user == null) {
            return "redirect:/login-required";
        }
        populateShell(model, user);
        model.addAttribute("activeMenu", "status-room");
        return "treatment-status-by-room";
    }

    @GetMapping("/dashboard")
    public String dashboardPage(Model model, HttpSession session) {
        SessionUser user = sessionUser(session);
        if (user == null) {
            return "redirect:/login-required";
        }
        populateShell(model, user);
        model.addAttribute("activeMenu", "dashboard");
        return "dashboard";
    }

    @GetMapping("/treatment-rooms")
    public String treatmentRoomsPage(Model model, HttpSession session) {
        SessionUser user = sessionUser(session);
        if (user == null) {
            return "redirect:/login-required";
        }
        populateShell(model, user);
        model.addAttribute("activeMenu", "rooms");
        return "treatment-rooms";
    }

    @GetMapping("/settings")
    public String settingsPage(Model model, HttpSession session) {
        SessionUser user = sessionUser(session);
        if (user == null) {
            return "redirect:/login-required";
        }
        populateShell(model, user);
        model.addAttribute("activeMenu", "settings");
        return "settings";
    }

    @GetMapping("/treatment-packages")
    public String treatmentPackagesPage(Model model, HttpSession session) {
        SessionUser user = sessionUser(session);
        if (user == null) {
            return "redirect:/login-required";
        }
        populateShell(model, user);
        model.addAttribute("activeMenu", "packages");
        return "treatment-packages";
    }

    @GetMapping("/patient-rooms")
    public String patientRoomsPage(Model model, HttpSession session) {
        SessionUser user = sessionUser(session);
        if (user == null) {
            return "redirect:/login-required";
        }
        populateShell(model, user);
        model.addAttribute("activeMenu", "patient-rooms");
        return "patient-rooms";
    }

    @GetMapping("/schedule-by-patient")
    public String scheduleByPatientPage(Model model, HttpSession session) {
        return perspectivePage(model, session, "patient", "환자별 치료스케줄", "sched-patient");
    }

    @GetMapping("/schedule-by-ward")
    public String scheduleByWardPage(Model model, HttpSession session) {
        return perspectivePage(model, session, "ward", "병동별 치료스케줄", "sched-ward");
    }

    @GetMapping("/schedule-by-doctor")
    public String scheduleByDoctorPage(Model model, HttpSession session) {
        return perspectivePage(model, session, "doctor", "의사별 치료스케줄", "sched-doctor");
    }

    private String perspectivePage(Model model, HttpSession session, String perspective, String title, String activeMenu) {
        SessionUser user = sessionUser(session);
        if (user == null) {
            return "redirect:/login-required";
        }
        populateShell(model, user);
        model.addAttribute("activeMenu", activeMenu);
        model.addAttribute("perspective", perspective);
        model.addAttribute("perspectiveTitle", title);
        return "schedule-perspective";
    }

    @GetMapping("/schedule-register")
    public String scheduleRegisterPage(Model model, HttpSession session) {
        SessionUser user = sessionUser(session);
        if (user == null) {
            return "redirect:/login-required";
        }
        populateShell(model, user);
        model.addAttribute("activeMenu", "schedule-calendar");
        return "schedule-register";
    }

    @GetMapping("/treatment-seats")
    public String treatmentSeatsPage(Model model, HttpSession session) {
        SessionUser user = sessionUser(session);
        if (user == null) {
            return "redirect:/login-required";
        }
        populateShell(model, user);
        model.addAttribute("activeMenu", "rooms");
        return "treatment-seats";
    }

    @GetMapping("/therapists")
    public String therapistsPage(Model model, HttpSession session) {
        SessionUser user = sessionUser(session);
        if (user == null) {
            return "redirect:/login-required";
        }
        populateShell(model, user);
        model.addAttribute("activeMenu", "rooms");
        return "therapists";
    }

    @GetMapping("/treatment-durations")
    public String treatmentDurationsPage(Model model, HttpSession session) {
        SessionUser user = sessionUser(session);
        if (user == null) {
            return "redirect:/login-required";
        }
        populateShell(model, user);
        model.addAttribute("activeMenu", "rooms");
        return "treatment-durations";
    }

    @GetMapping("/treatment-options")
    public String treatmentOptionsPage(Model model, HttpSession session) {
        SessionUser user = sessionUser(session);
        if (user == null) {
            return "redirect:/login-required";
        }
        populateShell(model, user);
        model.addAttribute("activeMenu", "options");
        return "treatment-options";
    }

    @GetMapping("/api/treatment-schedules")
    @ResponseBody
    public ResponseEntity<?> listSchedules(
            @RequestParam(name = "date", required = false) String date,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "treatmentName", required = false) String treatmentName,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "roomId", required = false) Long roomId,
            @RequestParam(name = "from", required = false) String from,
            @RequestParam(name = "to", required = false) String to,
            HttpSession session) {
        SessionUser user = requireUser(session);
        if (org.springframework.util.StringUtils.hasText(from) && org.springframework.util.StringUtils.hasText(to)) {
            return ResponseEntity.ok(Map.of(
                    "items", scheduleService.listSchedulesByRange(user.getInstCode(), from, to)));
        }
        return ResponseEntity.ok(Map.of(
                "summary", scheduleService.getSummary(user.getInstCode(), date),
                "items", scheduleService.listSchedules(user.getInstCode(), date, keyword, treatmentName, status, roomId)));
    }

    @GetMapping("/api/dashboard")
    @ResponseBody
    public ResponseEntity<?> dashboardData(
            @RequestParam(name = "date", required = false) String date,
            HttpSession session) {
        SessionUser user = requireUser(session);
        return ResponseEntity.ok(scheduleService.getDashboard(user.getInstCode(), date));
    }

    @GetMapping("/api/patients")
    @ResponseBody
    public ResponseEntity<?> listPatients(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "ward", required = false) String ward,
            HttpSession session) {
        SessionUser user = requireUser(session);
        return ResponseEntity.ok(Map.of("items", patientService.listPatients(user.getInstCode(), keyword, ward)));
    }

    @GetMapping("/api/patients/doctors")
    @ResponseBody
    public ResponseEntity<?> listPatientDoctors(HttpSession session) {
        SessionUser user = requireUser(session);
        return ResponseEntity.ok(Map.of("items", patientService.listDoctors(user.getInstCode())));
    }

    @PostMapping("/api/patients")
    @ResponseBody
    public ResponseEntity<?> createPatient(
            @RequestBody PatientRequest request,
            HttpSession session) {
        SessionUser user = requireMember(session);
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(patientService.createPatient(user.getInstCode(), request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/api/patients/{id}")
    @ResponseBody
    public ResponseEntity<?> updatePatient(
            @PathVariable("id") Long id,
            @RequestBody PatientRequest request,
            HttpSession session) {
        SessionUser user = requireMember(session);
        try {
            return ResponseEntity.ok(patientService.updatePatient(user.getInstCode(), id, request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PatchMapping("/api/patients/{id}/field")
    @ResponseBody
    public ResponseEntity<?> updatePatientField(
            @PathVariable("id") Long id,
            @RequestBody Map<String, String> body,
            HttpSession session) {
        SessionUser user = requireMember(session);
        try {
            return ResponseEntity.ok(patientService.updateTextField(
                    user.getInstCode(),
                    id,
                    body.get("field"),
                    body.get("value")));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/api/settings")
    @ResponseBody
    public ResponseEntity<?> listSettings(HttpSession session) {
        SessionUser user = requireUser(session);
        return ResponseEntity.ok(settingService.listAll(user.getInstCode()));
    }

    @GetMapping("/api/settings/{category}")
    @ResponseBody
    public ResponseEntity<?> listSettingItems(
            @PathVariable("category") String category,
            HttpSession session) {
        SessionUser user = requireUser(session);
        try {
            return ResponseEntity.ok(Map.of("items", settingService.listItems(user.getInstCode(), category)));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/api/settings/{category}")
    @ResponseBody
    public ResponseEntity<?> createSettingItem(
            @PathVariable("category") String category,
            @RequestBody SettingItemRequest request,
            HttpSession session) {
        SessionUser user = requireMember(session);
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(settingService.createItem(user.getInstCode(), category, request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/api/settings/{category}/{id}")
    @ResponseBody
    public ResponseEntity<?> updateSettingItem(
            @PathVariable("category") String category,
            @PathVariable("id") Long id,
            @RequestBody SettingItemRequest request,
            HttpSession session) {
        SessionUser user = requireMember(session);
        try {
            return ResponseEntity.ok(settingService.updateItem(user.getInstCode(), category, id, request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/api/settings/{category}/{id}")
    @ResponseBody
    public ResponseEntity<?> deleteSettingItem(
            @PathVariable("category") String category,
            @PathVariable("id") Long id,
            HttpSession session) {
        SessionUser user = requireMember(session);
        try {
            settingService.deleteItem(user.getInstCode(), category, id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/api/treatment-rooms")
    @ResponseBody
    public ResponseEntity<?> listTreatmentRooms(HttpSession session) {
        SessionUser user = requireUser(session);
        return ResponseEntity.ok(Map.of("items", treatmentRoomService.listRooms(user.getInstCode())));
    }

    @PostMapping("/api/treatment-rooms")
    @ResponseBody
    public ResponseEntity<?> createTreatmentRoom(
            @RequestBody TreatmentRoomRequest request,
            HttpSession session) {
        SessionUser user = requireMember(session);
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(treatmentRoomService.createRoom(user.getInstCode(), request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/api/treatment-rooms/{id}")
    @ResponseBody
    public ResponseEntity<?> updateTreatmentRoom(
            @PathVariable("id") Long id,
            @RequestBody TreatmentRoomRequest request,
            HttpSession session) {
        SessionUser user = requireMember(session);
        try {
            return ResponseEntity.ok(treatmentRoomService.updateRoom(user.getInstCode(), id, request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/api/treatment-rooms/{id}")
    @ResponseBody
    public ResponseEntity<?> deleteTreatmentRoom(
            @PathVariable("id") Long id,
            HttpSession session) {
        SessionUser user = requireMember(session);
        try {
            treatmentRoomService.deleteRoom(user.getInstCode(), id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/api/patient-rooms")
    @ResponseBody
    public ResponseEntity<?> listPatientRooms(HttpSession session) {
        SessionUser user = requireUser(session);
        return ResponseEntity.ok(Map.of("items", patientRoomService.listRooms(user.getInstCode())));
    }

    @PostMapping("/api/patient-rooms")
    @ResponseBody
    public ResponseEntity<?> createPatientRoom(
            @RequestBody PatientRoomRequest request,
            HttpSession session) {
        SessionUser user = requireMember(session);
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(patientRoomService.createRoom(user.getInstCode(), request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/api/patient-rooms/{id}")
    @ResponseBody
    public ResponseEntity<?> updatePatientRoom(
            @PathVariable("id") Long id,
            @RequestBody PatientRoomRequest request,
            HttpSession session) {
        SessionUser user = requireMember(session);
        try {
            return ResponseEntity.ok(patientRoomService.updateRoom(user.getInstCode(), id, request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/api/patient-rooms/{id}")
    @ResponseBody
    public ResponseEntity<?> deletePatientRoom(
            @PathVariable("id") Long id,
            HttpSession session) {
        SessionUser user = requireMember(session);
        try {
            patientRoomService.deleteRoom(user.getInstCode(), id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/api/therapists")
    @ResponseBody
    public ResponseEntity<?> listTherapists(HttpSession session) {
        SessionUser user = requireUser(session);
        return ResponseEntity.ok(Map.of("items", therapistService.listTherapists(user.getInstCode())));
    }

    @PostMapping("/api/therapists")
    @ResponseBody
    public ResponseEntity<?> createTherapist(@RequestBody TherapistRequest request, HttpSession session) {
        SessionUser user = requireMember(session);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(therapistService.createTherapist(user.getInstCode(), request));
    }

    @PutMapping("/api/therapists/{id}")
    @ResponseBody
    public ResponseEntity<?> updateTherapist(
            @PathVariable("id") Long id, @RequestBody TherapistRequest request, HttpSession session) {
        SessionUser user = requireMember(session);
        return ResponseEntity.ok(therapistService.updateTherapist(user.getInstCode(), id, request));
    }

    @DeleteMapping("/api/therapists/{id}")
    @ResponseBody
    public ResponseEntity<?> deleteTherapist(@PathVariable("id") Long id, HttpSession session) {
        SessionUser user = requireMember(session);
        therapistService.deleteTherapist(user.getInstCode(), id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/treatment-durations")
    @ResponseBody
    public ResponseEntity<?> listTreatmentDurations(HttpSession session) {
        SessionUser user = requireUser(session);
        return ResponseEntity.ok(Map.of("items", treatmentDurationService.listDurations(user.getInstCode())));
    }

    @PatchMapping("/api/treatment-durations/{id}")
    @ResponseBody
    public ResponseEntity<?> updateTreatmentDuration(
            @PathVariable("id") Long id, @RequestBody Map<String, Object> body, HttpSession session) {
        SessionUser user = requireMember(session);
        Object raw = body.get("durationMinutes");
        Integer minutes = raw == null ? null : ((Number) raw).intValue();
        treatmentDurationService.updateDuration(user.getInstCode(), id, minutes);
        return ResponseEntity.ok(Map.of("ok", true));
    }

    @GetMapping("/api/treatment-seats")
    @ResponseBody
    public ResponseEntity<?> listTreatmentSeats(
            @RequestParam(name = "roomId", required = false) Long roomId,
            HttpSession session) {
        SessionUser user = requireUser(session);
        return ResponseEntity.ok(Map.of("items", treatmentSeatService.listSeats(user.getInstCode(), roomId)));
    }

    @PostMapping("/api/treatment-seats")
    @ResponseBody
    public ResponseEntity<?> createTreatmentSeat(
            @RequestBody TreatmentSeatRequest request,
            HttpSession session) {
        SessionUser user = requireMember(session);
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(treatmentSeatService.createSeat(user.getInstCode(), request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/api/treatment-seats/{id}")
    @ResponseBody
    public ResponseEntity<?> updateTreatmentSeat(
            @PathVariable("id") Long id,
            @RequestBody TreatmentSeatRequest request,
            HttpSession session) {
        SessionUser user = requireMember(session);
        try {
            return ResponseEntity.ok(treatmentSeatService.updateSeat(user.getInstCode(), id, request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/api/treatment-seats/{id}")
    @ResponseBody
    public ResponseEntity<?> deleteTreatmentSeat(
            @PathVariable("id") Long id,
            HttpSession session) {
        SessionUser user = requireMember(session);
        try {
            treatmentSeatService.deleteSeat(user.getInstCode(), id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/api/treatment-packages")
    @ResponseBody
    public ResponseEntity<?> listTreatmentPackages(
            @RequestParam(name = "roomId", required = false) Long roomId,
            @RequestParam(name = "categoryId", required = false) Long categoryId,
            HttpSession session) {
        SessionUser user = requireUser(session);
        return ResponseEntity.ok(Map.of("items", treatmentPackageService.listPackages(user.getInstCode(), roomId, categoryId)));
    }

    @PostMapping("/api/treatment-packages")
    @ResponseBody
    public ResponseEntity<?> createTreatmentPackage(
            @RequestBody TreatmentPackageRequest request,
            HttpSession session) {
        SessionUser user = requireMember(session);
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(treatmentPackageService.createPackage(user.getInstCode(), request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/api/treatment-packages/{id}")
    @ResponseBody
    public ResponseEntity<?> updateTreatmentPackage(
            @PathVariable("id") Long id,
            @RequestBody TreatmentPackageRequest request,
            HttpSession session) {
        SessionUser user = requireMember(session);
        try {
            return ResponseEntity.ok(treatmentPackageService.updatePackage(user.getInstCode(), id, request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/api/treatment-packages/{id}")
    @ResponseBody
    public ResponseEntity<?> deleteTreatmentPackage(
            @PathVariable("id") Long id,
            HttpSession session) {
        SessionUser user = requireMember(session);
        try {
            treatmentPackageService.deletePackage(user.getInstCode(), id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/api/treatment-schedules")
    @ResponseBody
    public ResponseEntity<?> createSchedule(
            @RequestBody TreatmentScheduleRequest request,
            HttpSession session) {
        SessionUser user = requireMember(session);
        try {
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(scheduleService.createSchedule(user.getInstCode(), request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/api/treatment-schedules/recurring")
    @ResponseBody
    public ResponseEntity<?> createRecurringSchedules(
            @RequestBody ScheduleRecurrenceRequest request,
            HttpSession session) {
        SessionUser user = requireMember(session);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(scheduleRecurrenceService.createSchedules(user.getInstCode(), user.getUsername(), request));
    }

    @PutMapping("/api/treatment-schedules/{id}")
    @ResponseBody
    public ResponseEntity<?> updateSchedule(
            @PathVariable("id") Long id,
            @RequestBody TreatmentScheduleRequest request,
            HttpSession session) {
        SessionUser user = requireMember(session);
        try {
            return ResponseEntity.ok(scheduleService.updateSchedule(user.getInstCode(), id, request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @DeleteMapping("/api/treatment-schedules/{id}")
    @ResponseBody
    public ResponseEntity<?> deleteSchedule(
            @PathVariable("id") Long id,
            HttpSession session) {
        SessionUser user = requireMember(session);
        try {
            scheduleService.deleteSchedule(user.getInstCode(), id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PatchMapping("/api/treatment-schedules/{id}/status")
    @ResponseBody
    public ResponseEntity<?> updateStatus(
            @PathVariable("id") Long id,
            @RequestBody Map<String, String> body,
            HttpSession session) {
        SessionUser user = requireMember(session);
        try {
            return ResponseEntity.ok(scheduleService.updateStatus(user.getInstCode(), id, body.get("status")));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PatchMapping("/api/treatment-schedules/{id}/time")
    @ResponseBody
    public ResponseEntity<?> updateScheduleTime(
            @PathVariable("id") Long id,
            @RequestBody Map<String, String> body,
            HttpSession session) {
        SessionUser user = requireMember(session);
        try {
            return ResponseEntity.ok(scheduleService.updateStartTime(user.getInstCode(), id, body.get("startTime")));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PatchMapping("/api/treatment-schedules/{id}/field")
    @ResponseBody
    public ResponseEntity<?> updateScheduleField(
            @PathVariable("id") Long id,
            @RequestBody Map<String, String> body,
            HttpSession session) {
        SessionUser user = requireMember(session);
        try {
            return ResponseEntity.ok(scheduleService.updateTextField(user.getInstCode(), id, body.get("field"), body.get("value")));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping(value = "/api/treatment-schedules/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @ResponseBody
    public SseEmitter events(HttpSession session) {
        requireUser(session);
        return scheduleService.subscribe();
    }

    private SessionUser requireUser(HttpSession session) {
        SessionUser user = sessionUser(session);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }
        return user;
    }

    /**
     * Demands an authenticated MEMBER (write-capable) session.
     * VIEWER (default) callers get 403; unauthenticated callers get 401.
     */
    private SessionUser requireMember(HttpSession session) {
        SessionUser user = requireUser(session);
        if (!user.isMember()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "쓰기 권한이 없습니다.");
        }
        return user;
    }

    private SessionUser sessionUser(HttpSession session) {
        Object value = session.getAttribute(SESSION_USER);
        return value instanceof SessionUser user ? user : null;
    }

    private void populateShell(Model model, SessionUser user) {
        model.addAttribute("user", user);
        model.addAttribute("mediplatPortalUrl", mediplatPortalUrl);
        model.addAttribute("summary", scheduleService.getSummary(user.getInstCode(), null));
    }
}
