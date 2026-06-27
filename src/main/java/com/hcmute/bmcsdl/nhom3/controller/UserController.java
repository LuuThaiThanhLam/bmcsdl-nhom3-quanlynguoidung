package com.hcmute.bmcsdl.nhom3.controller;

import com.hcmute.bmcsdl.nhom3.dto.UserProfileDTO;
import com.hcmute.bmcsdl.nhom3.exception.OracleException;
import com.hcmute.bmcsdl.nhom3.service.UserProfileService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/user")
public class UserController {

    private final UserProfileService userProfileService;

    public UserController(UserProfileService userProfileService) {
        this.userProfileService = userProfileService;
    }

    @GetMapping("/profile")
    public String profile(HttpSession session, Model model) {
        if (!isLoggedIn(session)) {
            return "redirect:/";
        }
        java.util.List<UserProfileDTO> profiles = userProfileService.getMyProfiles(session);
        String olsReadLabel = userProfileService.getMySessionReadLabel(session);
        boolean olsActive = olsReadLabel != null
                || profiles.stream().anyMatch(p -> p.getOlsLabel() != null);

        model.addAttribute("profiles", profiles);
        model.addAttribute("olsReadLabel", olsReadLabel);
        model.addAttribute("olsActive", olsActive);
        return "user/profile";
    }

    @GetMapping("/profile/{userId}/edit")
    public String editForm(@PathVariable long userId, HttpSession session, Model model) {
        if (!isLoggedIn(session)) {
            return "redirect:/";
        }
        model.addAttribute("profile", userProfileService.getMyProfile(session, userId));
        return "user/profile_edit";
    }

    @PostMapping("/profile/update")
    public String update(@ModelAttribute UserProfileDTO profile,
                         HttpSession session,
                         RedirectAttributes redirectAttributes) {
        if (!isLoggedIn(session)) {
            return "redirect:/";
        }
        try {
            userProfileService.updateMyProfile(session, profile);
            redirectAttributes.addFlashAttribute("success", "Cap nhat ho so thanh cong!");
        } catch (OracleException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            redirectAttributes.addFlashAttribute("errorCode", e.getErrorCode());
        }
        return "redirect:/user/profile";
    }

    @GetMapping("/directory")
    public String directory(HttpSession session, Model model) {
        if (!isLoggedIn(session)) {
            return "redirect:/";
        }
        model.addAttribute("entries", userProfileService.getDirectory(session));
        return "user/directory";
    }

    @ExceptionHandler(OracleException.class)
    public String handleOracleException(OracleException exception, Model model, HttpSession session) {
        if (!isLoggedIn(session)) {
            return "redirect:/";
        }
        model.addAttribute("error", exception.getMessage());
        model.addAttribute("errorCode", exception.getErrorCode());
        model.addAttribute("profiles", java.util.List.of());
        return "user/profile";
    }

    private boolean isLoggedIn(HttpSession session) {
        return session != null && session.getAttribute("username") != null;
    }
}
