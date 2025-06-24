package com.henuka.imitations.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class VideoController {

    @GetMapping("/video/record")
    public String showVideoRecordingPage(Model model) {
        model.addAttribute("title", "Record Video");
        return "video/record";
    }
}
