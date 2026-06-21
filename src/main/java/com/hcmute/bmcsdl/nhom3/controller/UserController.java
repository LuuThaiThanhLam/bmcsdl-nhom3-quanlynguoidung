package com.hcmute.bmcsdl.nhom3.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/user")
public class UserController {
    @GetMapping("/profile")
    public String profile(HttpSession session) {
        if (session.getAttribute("username") == null) return "redirect:/";
        return "user/profile";
    }
}