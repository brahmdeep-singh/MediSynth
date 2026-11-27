package com.personal.medisynth.DTO;

import com.personal.medisynth.validation.StrongPassword;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Details of the admin account to create (bootstrap, or created by an existing admin). */
public record AdminProvisionRequest(
        @NotBlank(message = "Name is required") @Size(max = 100) String name,
        @NotBlank(message = "Email is required")
        @Size(max = 254)
        @Pattern(regexp = "^[A-Za-z0-9._%+'-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$", message = "Email is not valid") String email,
        @NotBlank(message = "Password is required") @StrongPassword String password,
        @NotBlank(message = "Phone is required")
        @Pattern(regexp = "^\\+?[0-9]{7,15}$", message = "Phone must be 7-15 digits, optionally starting with +") String phone) {

    @Override
    public String toString() {
        return "AdminProvisionRequest[redacted]";
    }
}
