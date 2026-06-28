package com.hcmute.bmcsdl.nhom3.controller;

import com.hcmute.bmcsdl.nhom3.dto.ManagerProfileDTO;
import com.hcmute.bmcsdl.nhom3.dto.ManagerSelfInfoDTO;
import com.hcmute.bmcsdl.nhom3.exception.OracleException;
import com.hcmute.bmcsdl.nhom3.service.ManagerProfileService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.function.Supplier;

/**
 * Controller vai tro MANAGER (APP_MANAGER_PROFILE / APP_MANAGER_SALES - Profile Manager).
 *
 * Cac trang (sidebar nhieu muc, giong admin):
 *   /manager/dashboard   - tong quan
 *   /manager/profiles    - quan ly ho so CUNG PHONG BAN (VPD), + nang/ha nhan OLS, tim kiem
 *   /manager/my-account  - thong tin tai khoan cua chinh minh
 *   /manager/my-roles    - role + quyen cua chinh minh
 *   /manager/my-profile  - profile Oracle cua chinh minh
 *   /manager/my-ols      - nhan OLS cua chinh minh
 *
 * Moi thao tac chay duoi quyen cua chinh Manager -> VPD/OLS/RBAC phat huy.
 */
@Controller
@RequestMapping("/manager")
public class ManagerController {

    private final ManagerProfileService service;

    public ManagerController(ManagerProfileService service) {
        this.service = service;
    }

    // ---------------- Dashboard ----------------
    @GetMapping("/dashboard")
    public String dashboard(HttpSession session, Model model) {
        if (!isLoggedIn(session)) return "redirect:/";
        try {
            List<ManagerProfileDTO> profiles = service.getVisibleProfiles(session);
            String dept = safe(() -> service.getMyDepartment(session), null);
            model.addAttribute("profiles", profiles);
            model.addAttribute("myDepartment", dept);
            model.addAttribute("totalProfiles", profiles.size());
            long priv = profiles.stream()
                    .filter(p -> p.getOlsLabelText() != null && p.getOlsLabelText().startsWith("PRI"))
                    .count();
            long mgr = profiles.stream()
                    .filter(p -> p.getRoleLevel() != null && p.getRoleLevel() >= 2)
                    .count();
            model.addAttribute("privateCount", priv);
            model.addAttribute("publicCount", profiles.size() - priv);
            model.addAttribute("mgrCount", mgr);
            model.addAttribute("empCount", profiles.size() - mgr);
        } catch (OracleException e) {
            fillEmptyDashboard(model, e);
        }
        return "manager/dashboard";
    }

    // ---------------- Quan ly ho so (cung phong ban) ----------------
    @GetMapping("/profiles")
    public String profiles(HttpSession session, Model model) {
        if (!isLoggedIn(session)) return "redirect:/";
        try {
            model.addAttribute("profiles", service.getVisibleProfiles(session));
            model.addAttribute("myDepartment", safe(() -> service.getMyDepartment(session), null));
        } catch (OracleException e) {
            model.addAttribute("profiles", List.of());
            model.addAttribute("error", e.getMessage());
            model.addAttribute("errorCode", e.getErrorCode());
        }
        return "manager/profiles";
    }

    @PostMapping("/profiles/update")
    public String updateProfile(@ModelAttribute ManagerProfileDTO dto, HttpSession session, RedirectAttributes ra) {
        if (!isLoggedIn(session)) return "redirect:/";
        return run(ra, () -> {
            int n = service.updateBasicInfo(session, dto);
            if (n == 0) throw new OracleException(
                    "Khong cap nhat duoc (0 dong). Ho so khong thuoc phong ban cua ban (VPD chan).", "VPD_BLOCKED");
            return "Cap nhat ho so #" + dto.getUserId() + " thanh cong";
        }, "/manager/profiles");
    }

    @PostMapping("/profiles/{userId}/delete")
    public String deleteProfile(@PathVariable long userId, HttpSession session, RedirectAttributes ra) {
        if (!isLoggedIn(session)) return "redirect:/";
        return run(ra, () -> {
            int n = service.deleteProfile(session, userId);
            if (n == 0) throw new OracleException(
                    "Khong xoa duoc (0 dong). Ho so khong thuoc phong ban cua ban (VPD chan).", "VPD_BLOCKED");
            return "Xoa ho so #" + userId + " thanh cong";
        }, "/manager/profiles");
    }

    @PostMapping("/profiles/{userId}/upgrade-label")
    public String upgradeLabel(@PathVariable long userId, HttpSession session, RedirectAttributes ra) {
        if (!isLoggedIn(session)) return "redirect:/";
        return run(ra, () -> {
            service.upgradeLabel(session, userId);
            return "Da nang nhan ho so #" + userId + " len PRIVATE";
        }, "/manager/profiles");
    }

    @PostMapping("/profiles/{userId}/downgrade-label")
    public String downgradeLabel(@PathVariable long userId, HttpSession session, RedirectAttributes ra) {
        if (!isLoggedIn(session)) return "redirect:/";
        return run(ra, () -> {
            service.downgradeLabel(session, userId);
            return "Da ha nhan ho so #" + userId + " ve PUBLIC";
        }, "/manager/profiles");
    }

    // ---------------- Thong tin cua chinh minh ----------------
    @GetMapping("/my-account")
    public String myAccount(HttpSession session, Model model) {
        if (!isLoggedIn(session)) return "redirect:/";
        try {
            ManagerSelfInfoDTO info = service.getMyAccountInfo(session);
            model.addAttribute("account", info.getAccount());
            model.addAttribute("department", info.getDepartment());
        } catch (OracleException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("errorCode", e.getErrorCode());
        }
        return "manager/my_account";
    }

    @GetMapping("/my-roles")
    public String myRoles(HttpSession session, Model model) {
        if (!isLoggedIn(session)) return "redirect:/";
        try {
            ManagerSelfInfoDTO info = service.getMyRolesInfo(session);
            model.addAttribute("sessionRoles", info.getSessionRoles());
            model.addAttribute("rolePrivs", info.getRolePrivs());
            model.addAttribute("sysPrivs", info.getSysPrivs());
            model.addAttribute("tabPrivs", info.getTabPrivs());
            model.addAttribute("colPrivs", info.getColPrivs());
        } catch (OracleException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("errorCode", e.getErrorCode());
        }
        return "manager/my_roles";
    }

    @GetMapping("/my-profile")
    public String myProfile(HttpSession session, Model model) {
        if (!isLoggedIn(session)) return "redirect:/";
        try {
            ManagerSelfInfoDTO info = service.getMyProfileInfo(session);
            model.addAttribute("profileLimits", info.getProfileLimits());
        } catch (OracleException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("errorCode", e.getErrorCode());
        }
        return "manager/my_profile";
    }

    @GetMapping("/my-ols")
    public String myOls(HttpSession session, Model model) {
        if (!isLoggedIn(session)) return "redirect:/";
        try {
            ManagerSelfInfoDTO info = service.getMyOlsInfo(session);
            model.addAttribute("olsLabel", info.getOlsLabel());
        } catch (OracleException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("errorCode", e.getErrorCode());
        }
        return "manager/my_ols";
    }

    // ---------------- helpers ----------------
    private String run(RedirectAttributes ra, Supplier<String> action, String redirectUrl) {
        try {
            ra.addFlashAttribute("success", action.get());
        } catch (OracleException e) {
            ra.addFlashAttribute("error", e.getMessage());
            ra.addFlashAttribute("errorCode", e.getErrorCode());
        }
        return "redirect:" + redirectUrl;
    }

    private <T> T safe(Supplier<T> s, T fallback) {
        try { return s.get(); } catch (Exception e) { return fallback; }
    }

    private void fillEmptyDashboard(Model model, OracleException e) {
        model.addAttribute("profiles", List.of());
        model.addAttribute("totalProfiles", 0);
        model.addAttribute("privateCount", 0);
        model.addAttribute("publicCount", 0);
        model.addAttribute("mgrCount", 0);
        model.addAttribute("empCount", 0);
        model.addAttribute("error", e.getMessage());
        model.addAttribute("errorCode", e.getErrorCode());
    }

    private boolean isLoggedIn(HttpSession session) {
        return session != null && session.getAttribute("username") != null;
    }
}
