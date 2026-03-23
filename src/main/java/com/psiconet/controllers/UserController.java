package com.psiconet.controllers;

import com.psiconet.model.entities.access.User;
import com.psiconet.services.interfaces.profile.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @GetMapping("/me")
    public ResponseEntity<Object> getMe(Authentication authentication) {

        System.out.println("authentication: " + authentication);

        User user = (User) authentication.getPrincipal();

        System.out.println("user: " + user);

        Object profileDto = userService.getLoggedUserProfile(user);

        System.out.println("profileDto: " + profileDto);

        return ResponseEntity.ok(profileDto);
    }
}
