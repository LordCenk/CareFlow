package com.careflow.user.controller;

import com.careflow.common.response.ApiResponse;
import com.careflow.security.CustomUserPrincipal;
import com.careflow.user.dto.UserProfileResponse;
import com.careflow.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users")
@Tag(name = "Users", description = "Current user profile and operations")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    @Operation(summary = "Get current authenticated user profile")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getCurrentUser(
            @AuthenticationPrincipal CustomUserPrincipal principal
    ) {
        UserProfileResponse response = userService.getCurrentUser(principal);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
