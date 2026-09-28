package com.resumeai.controller;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.resumeai.service.ResumeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only shared reports. No login needed, but only people with the secret link can open one. */
@RestController
@RequestMapping("/api/public")
public class PublicController {

    private final ResumeService service;

    public PublicController(ResumeService service) {
        this.service = service;
    }

    @GetMapping("/report/{token}")
    public ObjectNode report(@PathVariable String token) {
        return service.publicReport(token);
    }
}
