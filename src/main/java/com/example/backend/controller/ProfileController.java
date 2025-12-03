package com.example.backend.controller;

import com.example.backend.entity.User;
import com.example.backend.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class ProfileController {

    private final UserService userService;

    public ProfileController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/signup")
    public ResponseEntity<?> signup(@RequestBody SignupRequest request) {
        System.out.println("-> /api/auth/signup called with email=" + request.getEmail());
        try {
            User user = userService.registerUser(request.getEmail(), request.getPassword());
            System.out.println("Signup success for " + user.getEmail());
            return ResponseEntity.ok(user);
        } catch (Exception e) {
            System.err.println("Signup failed:");
            e.printStackTrace(); // 👈 this prints the real cause to the console
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        System.out.println("-> /api/auth/login called with email=" + request.getEmail());
        try {
            User user = userService.loginUser(request.getEmail(), request.getPassword());
            System.out.println("Login success for " + user.getEmail());
            return ResponseEntity.ok(user);
        } catch (Exception e) {
            System.err.println("Login failed:");
            e.printStackTrace();
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    static class SignupRequest {
        private String email;
        private String password;
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    static class LoginRequest {
        private String email;
        private String password;
        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }
}
