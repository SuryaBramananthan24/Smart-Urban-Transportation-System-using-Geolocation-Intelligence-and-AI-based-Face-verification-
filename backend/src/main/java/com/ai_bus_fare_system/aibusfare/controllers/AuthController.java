package com.ai_bus_fare_system.aibusfare.controllers;

import com.ai_bus_fare_system.aibusfare.service.UserService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private UserService userService;

    @PostMapping("/signup")
    public Map<String, Object> signup(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String email    = body.get("email");
        String password = body.get("password");

        if (username == null || email == null || password == null || password.length() < 6) {
            return Map.of("success", false, "message", "Invalid input. Password must be ≥ 6 chars.");
        }

        String result = userService.register(username, email, password);
        return switch (result) {
            case "OK"           -> Map.of("success", true,  "message", "Account created!");
            case "USERNAME_TAKEN" -> Map.of("success", false, "message", "Username already taken.");
            case "EMAIL_TAKEN"  -> Map.of("success", false, "message", "Email already registered.");
            default             -> Map.of("success", false, "message", "Registration failed.");
        };
    }

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> body, HttpSession session) {
        String username = body.get("username");
        String password = body.get("password");

        boolean ok = userService.login(username, password);
        if (ok) {
            session.setAttribute("loggedInUser", username);
            return Map.of("success", true, "message", "Login successful!");
        }
        return Map.of("success", false, "message", "Invalid username or password.");
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(HttpSession session) {
        session.invalidate();
        return Map.of("success", true, "message", "Logged out.");
    }
}
