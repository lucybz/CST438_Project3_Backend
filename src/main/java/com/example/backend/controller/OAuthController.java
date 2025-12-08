package com.example.backend.controller;

import com.example.backend.entity.User;
import com.example.backend.repository.UserRepository;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "http://localhost:8081")   // adjust if needed
public class OAuthController {

    private final UserRepository userRepository;

    // ---------- GitHub config from application.properties ----------
    @Value("${github.client.id}")
    private String githubClientId;

    @Value("${github.client.secret}")
    private String githubClientSecret;

    @Value("${github.redirect.uri}")
    private String githubRedirectUri;

    // ---------- GitHub MOBILE config ----------
    @Value("${github.mobile.client.id}")
    private String githubMobileClientId;

    @Value("${github.mobile.client.secret}")
    private String githubMobileClientSecret;

    @Value("${github.mobile.redirect.uri}")
    private String githubMobileRedirectUri;

    public OAuthController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // Shared helper to read HTTP response bodies
    private String readBody(InputStream in) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
            return sb.toString();
        }
    }

    // ----------------- GOOGLE LOGIN -----------------
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

            String sb = readBody(conn.getInputStream());
            System.out.println("Google response: " + sb);

            JsonObject profile = JsonParser.parseString(sb).getAsJsonObject();
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

// ----------------- GITHUB LOGIN -----------------
    @PostMapping("/github")
    public ResponseEntity<?> githubLogin(@RequestBody Map<String, String> body) {
        System.out.println("->  /api/auth/github called");

        String code = body.get("code");
        String source = body.getOrDefault("source", "web"); // "web" or "mobile"
        System.out.println("GitHub code from frontend: " + code);
        System.out.println("Source: " + source);

        if (code == null || code.isEmpty()) {
            return ResponseEntity.badRequest().body("Missing code");
        }

        // Pick the correct app credentials based on source
        String clientId;
        String clientSecret;
        String redirectUri;

        if ("mobile".equalsIgnoreCase(source)) {
            clientId = githubMobileClientId;
            clientSecret = githubMobileClientSecret;
            redirectUri = githubMobileRedirectUri;
        } else {
            clientId = githubClientId;
            clientSecret = githubClientSecret;
            redirectUri = githubRedirectUri;
}


        System.out.println("Using client_id: " + clientId);
        System.out.println("Using redirect_uri: " + redirectUri);

        try {
            // Exchange code for access token
            String tokenUrl = "https://github.com/login/oauth/access_token";
            URL url = new URL(tokenUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setDoOutput(true);

            String params =
                    "client_id=" + clientId +
                    "&client_secret=" + clientSecret +
                    "&code=" + code +
                    "&redirect_uri=" + redirectUri;

            try (OutputStream os = conn.getOutputStream()) {
                os.write(params.getBytes(StandardCharsets.UTF_8));
            }

            int status = conn.getResponseCode();
            InputStream is = status >= 400 ? conn.getErrorStream() : conn.getInputStream();
            String tokenBody = readBody(is);
            System.out.println("GitHub token HTTP " + status + " body: " + tokenBody);

            if (status != 200) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("GitHub token error: " + tokenBody);
            }

            JsonObject tokenJson = JsonParser.parseString(tokenBody).getAsJsonObject();
            if (!tokenJson.has("access_token")) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("Missing access_token in GitHub response: " + tokenBody);
            }

            String accessToken = tokenJson.get("access_token").getAsString();

            // Fetch basic GitHub profile
            URL meUrl = new URL("https://api.github.com/user");
            HttpURLConnection meConn = (HttpURLConnection) meUrl.openConnection();
            meConn.setRequestProperty("Authorization", "Bearer " + accessToken);
            meConn.setRequestProperty("Accept", "application/json");

            String userBody = readBody(meConn.getInputStream());
            System.out.println("GitHub user response: " + userBody);

            JsonObject userJson = JsonParser.parseString(userBody).getAsJsonObject();
            String githubLogin = userJson.get("login").getAsString();

            String avatarUrl = userJson.has("avatar_url") && !userJson.get("avatar_url").isJsonNull()
                    ? userJson.get("avatar_url").getAsString()
                    : null;

            String email = null;
            if (userJson.has("email") && !userJson.get("email").isJsonNull()) {
                email = userJson.get("email").getAsString();
            }

            // If email is still null, query /user/emails
            if (email == null) {
                URL emailUrl = new URL("https://api.github.com/user/emails");
                HttpURLConnection emailConn = (HttpURLConnection) emailUrl.openConnection();
                emailConn.setRequestProperty("Authorization", "Bearer " + accessToken);
                emailConn.setRequestProperty("Accept", "application/json");

                String emailsBody = readBody(emailConn.getInputStream());
                System.out.println("GitHub emails response: " + emailsBody);

                JsonArray emailsJson = JsonParser.parseString(emailsBody).getAsJsonArray();
                for (JsonElement el : emailsJson) {
                    JsonObject obj = el.getAsJsonObject();
                    boolean primary = obj.get("primary").getAsBoolean();
                    boolean verified = obj.get("verified").getAsBoolean();
                    if (primary && verified) {
                        email = obj.get("email").getAsString();
                        break;
                    }
                }
            }

            if (email == null) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body("Could not get email from GitHub");
            }

            // Create or find user in DB
            String finalEmail = email;
            User user = userRepository.findByEmail(finalEmail)
                    .orElseGet(() -> {
                        System.out.println("Creating new GitHub user for " + finalEmail);
                        User newUser = new User();
                        newUser.setEmail(finalEmail);
                        newUser.setUsername(githubLogin);
                        newUser.setProvider("github");
                        // newUser.setAvatarUrl(avatarUrl); for later
                        return userRepository.save(newUser);
                    });

            System.out.println("GitHub user saved: " + user.getEmail());
            return ResponseEntity.ok(user);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("GitHub OAuth failed: " + e.getMessage());
        }
    }
}