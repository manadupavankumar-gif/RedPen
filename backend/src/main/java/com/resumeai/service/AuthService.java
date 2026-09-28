package com.resumeai.service;

import com.resumeai.model.User;
import com.resumeai.repository.UserRepository;
import com.resumeai.security.JwtService;
import com.resumeai.security.PasswordUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Two ways in: an anonymous "guest" identity (no sign-in, kept per browser), or a real
 * email/password account (optional). Both end up as the same kind of User row and the same
 * kind of JWT, so every other endpoint works the same either way.
 */
@Service
public class AuthService {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final UserRepository users;
    private final JwtService jwt;
    private final RateLimiter limiter;
    private final PasswordUtil passwords;
    private final SecureRandom random = new SecureRandom();

    @Value("${app.limits.new-sessions-per-hour-per-ip:10}")
    private int newSessionsPerHour;

    public AuthService(UserRepository users, JwtService jwt, RateLimiter limiter, PasswordUtil passwords) {
        this.users = users;
        this.jwt = jwt;
        this.limiter = limiter;
        this.passwords = passwords;
    }

    public Map<String, Object> guest(String ip) {
        // stops one network from creating thousands of identities to dodge the daily limit
        if (!limiter.tryAcquire("guest:" + ip, newSessionsPerHour, Duration.ofHours(1))) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many new sessions from your network. Please try again in a while.");
        }
        byte[] raw = new byte[16];
        random.nextBytes(raw);

        User u = new User();
        u.setName("Guest");
        u.setEmail("guest-" + HexFormat.of().formatHex(raw) + "@guest.local");
        u.setPasswordHash("!"); // nobody can sign in as a guest; the token is the only key
        users.save(u);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("token", jwt.generate(u));
        return m;
    }

    /** Creates a real account. The person can keep using the app without ever calling this. */
    public Map<String, Object> signup(String ip, String name, String email, String password) {
        if (!limiter.tryAcquire("signup:" + ip, newSessionsPerHour, Duration.ofHours(1))) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many attempts from your network. Please try again in a while.");
        }
        String cleanName = name == null ? "" : name.strip();
        String cleanEmail = email == null ? "" : email.strip().toLowerCase();
        if (cleanName.isBlank() || cleanName.length() > 100) {
            throw bad("Enter your name.");
        }
        if (!EMAIL.matcher(cleanEmail).matches() || cleanEmail.length() > 150) {
            throw bad("Enter a valid email address.");
        }
        if (password == null || password.length() < 6 || password.length() > 200) {
            throw bad("Password must be at least 6 characters.");
        }
        if (users.existsByEmail(cleanEmail)) {
            throw bad("An account with that email already exists. Try signing in instead.");
        }

        User u = new User();
        u.setName(cleanName);
        u.setEmail(cleanEmail);
        u.setPasswordHash(passwords.hash(password));
        users.save(u);

        return session(u);
    }

    /** Signs into an existing account. */
    public Map<String, Object> login(String ip, String email, String password) {
        if (!limiter.tryAcquire("login:" + ip, Math.max(newSessionsPerHour, 20), Duration.ofHours(1))) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many attempts from your network. Please try again in a while.");
        }
        String cleanEmail = email == null ? "" : email.strip().toLowerCase();
        User u = users.findByEmail(cleanEmail).orElse(null);
        // constant-shape check so a missing account and a wrong password behave the same
        boolean ok = u != null && !"!".equals(u.getPasswordHash()) && passwords.matches(password == null ? "" : password, u.getPasswordHash());
        if (!ok) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Wrong email or password.");
        }
        return session(u);
    }

    /** Basic info for whoever holds this token, so the browser can show "Signed in as ..." after a refresh. */
    public Map<String, Object> me(Long userId) {
        User u = users.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session expired. Please sign in again."));
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", u.getName());
        m.put("guest", "!".equals(u.getPasswordHash()));
        m.put("email", "!".equals(u.getPasswordHash()) ? null : u.getEmail());
        return m;
    }

    private Map<String, Object> session(User u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("token", jwt.generate(u));
        m.put("name", u.getName());
        m.put("email", u.getEmail());
        return m;
    }

    private static ResponseStatusException bad(String msg) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
    }
}
