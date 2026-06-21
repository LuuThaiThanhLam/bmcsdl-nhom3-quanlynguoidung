package com.hcmute.bmcsdl.nhom3.controller;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/manager")
public class ManagerController {
    @GetMapping("/dashboard")
    public String dashboard(HttpSession session) {
        if (session.getAttribute("username") == null) return "redirect:/";
        return "manager/dashboard";
    }
}