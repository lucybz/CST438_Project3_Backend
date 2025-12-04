/*
package com.example.backend.controller;

import com.example.backend.entity.User;
import com.example.backend.repository.UserRepository;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import org.springframework.web.bind.annotation.CrossOrigin;

@RestController
@RequestMapping("/api/auth")
public class OAuthController {

    private final UserRepository userRepository;

    public OAuthController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

   @PostMapping("/google")
public ResponseEntity<?> googleLogin(@RequestBody Map<String, String> body) {
    System.out.println("->  /api/auth/google called");

    String accessToken = body.get("access_token");
    System.out.println("Access token received: " + (accessToken != null));

    if (accessToken == null) {
        return ResponseEntity.badRequest().body("Missing access_token");
    }

    try {
        URL url = new URL("https://www.googleapis.com/userinfo/v2/me");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);

        BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream()));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        reader.close();

        System.out.println("Google response: " + sb);

        JsonObject profile = JsonParser.parseString(sb.toString()).getAsJsonObject();
        String email = profile.get("email").getAsString();
        String name = profile.get("name").getAsString();

        System.out.println("Email from Google: " + email);

        User user = userRepository.findByEmail(email)
                .orElseGet(() -> {
                    System.out.println("Creating new user for " + email);
                    User newUser = new User();
                    newUser.setUsername(name);
                    newUser.setEmail(email);
                    newUser.setProvider("google");
                    return userRepository.save(newUser);
                });

        System.out.println("User saved: " + user.getEmail());
        return ResponseEntity.ok(user);

    } catch (Exception e) {
        e.printStackTrace();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("OAuth failed: " + e.getMessage());
        }
    }
}
    */