package com.resumeai.controller;

import com.resumeai.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public record SignupRequest(String name, String email, String password) {}
    public record LoginRequest(String email, String password) {}

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    /** Called once per browser: returns a private token that identifies that browser's saved scores.
     *  No account needed \u2014 this is what lets people use the whole app without ever signing in. */
    @PostMapping("/guest")
    public Map<String, Object> guest(HttpServletRequest request) {
        return auth.guest(request.getRemoteAddr());
    }

    /** Optional real account. Signing up swaps the browser's guest token for a real one; nothing else changes. */
    @PostMapping("/signup")
    public Map<String, Object> signup(HttpServletRequest request, @RequestBody SignupRequest r) {
        return auth.signup(request.getRemoteAddr(), r.name(), r.email(), r.password());
    }

    /** Signs into an existing account from any browser. */
    @PostMapping("/login")
    public Map<String, Object> login(HttpServletRequest request, @RequestBody LoginRequest r) {
        return auth.login(request.getRemoteAddr(), r.email(), r.password());
    }

    /** Who the current token belongs to \u2014 used to restore "Signed in as ..." after a page refresh. */
    @GetMapping("/me")
    public Map<String, Object> me(@RequestAttribute("userId") Long userId) {
        return auth.me(userId);
    }
}
