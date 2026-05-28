package com.personal_tracker.app.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

    @GetMapping("/myacc")
    public String myAccount() {
        return "forward:/myacc.html";
    }

    @GetMapping("/operations")
    public String operations() {
        return "forward:/operations.html";
    }

    @GetMapping("/rules")
    public String rules() {
        return "forward:/rules.html";
    }

    @GetMapping("/login")
    public String login() {
        return "forward:/login.html";
    }
}
