package com.campusrate.dto;

import com.campusrate.model.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request and response shapes for sign up, login and profiles.
 * DTOs (data transfer objects) keep the API separate from the database entities,
 * so we never accidentally send something like a password hash to the browser.
 */
public final class AuthDtos {

    private AuthDtos() {
    }

    /**
     * @param website a "honeypot": the sign-up form has a hidden field with this name.
     *                People never see it, so they leave it empty; simple spam bots fill in
     *                every field, which gives them away.
     */
    public record RegisterRequest(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, max = 100, message = "must be at least 8 characters") String password,
            @NotBlank @Size(max = 60) String displayName,
            String website
    ) {
    }

    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password
    ) {
    }

    public record ProfileRequest(
            @NotBlank @Size(max = 60) String displayName,
            @NotBlank @Pattern(regexp = "CURRENT|ALUMNI|TRANSFER|PROSPECTIVE") String studentStatus,
            boolean international,
            @Size(max = 80) String homeCountry,
            boolean showCountry
    ) {
    }

    public record UserResponse(
            Long id, String email, String displayName, String role, String studentStatus,
            boolean international, String homeCountry, boolean showCountry, int reputation
    ) {
        public static UserResponse from(User u) {
            return new UserResponse(u.getId(), u.getEmail(), u.getDisplayName(), u.getRole(),
                    u.getStudentStatus(), u.isInternational(), u.getHomeCountry(), u.isShowCountry(),
                    u.getReputation());
        }
    }

    public record AuthResponse(String token, UserResponse user) {
    }
}
