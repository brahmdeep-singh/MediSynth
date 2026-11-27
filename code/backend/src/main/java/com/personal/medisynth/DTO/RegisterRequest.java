package com.personal.medisynth.DTO;

import java.math.BigDecimal;

import com.personal.medisynth.validation.StrongPassword;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Public registration payload. There is deliberately no status/grantedRole/id field:
 * unknown JSON properties are ignored, so they can never be mass-assigned.
 * {@code role} is only a *request* (PATIENT, DOCTOR, LAB_TECHNICIAN or RESEARCHER); ADMIN is rejected by the server.
 */
public record RegisterRequest(
        @NotBlank(message = "Name is required") @Size(max = 100) String name,
        @NotBlank(message = "Email is required")
        @Size(max = 254)
        @Pattern(regexp = "^[A-Za-z0-9._%+'-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$", message = "Email is not valid") String email,
        @NotBlank(message = "Password is required") @StrongPassword String password,
        @NotBlank(message = "Phone is required")
        @Pattern(regexp = "^\\+?[0-9]{7,15}$", message = "Phone must be 7-15 digits, optionally starting with +") String phone,
        @NotBlank(message = "Role is required") String role,
        // Doctor application details (required when role = DOCTOR, ignored for PATIENT)
        @Size(max = 100) String specialization,
        @Size(max = 500) String qualifications,
        @Min(0) @Max(70) Integer experienceYears,
        @Size(max = 50) String licenseNumber,
        @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal consultationFee,
        @Size(max = 500) String availability,
        // Lab technician application details (required when role = LAB_TECHNICIAN)
        @Size(max = 100) String department,
        @Size(max = 50) String employeeId,
        // Researcher application details (required when role = RESEARCHER)
        @Size(max = 150) String institution,
        @Size(max = 200) String researchArea) {

    @Override
    public String toString() {
        return "RegisterRequest[redacted]";
    }
}
