package com.personal.medisynth.DTO;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Doctor-editable fields only. License number and verification status are admin-controlled. */
public record UpdateDoctorProfileRequest(
        @NotBlank(message = "Name is required") @Size(max = 100) String name,
        @NotBlank(message = "Phone is required")
        @Pattern(regexp = "^\\+?[0-9]{7,15}$", message = "Phone must be 7-15 digits, optionally starting with +") String phone,
        @NotBlank(message = "Specialization is required") @Size(max = 100) String specialization,
        @NotBlank(message = "Qualifications are required") @Size(max = 500) String qualifications,
        @NotNull(message = "Experience is required") @Min(0) @Max(70) Integer experienceYears,
        @NotNull(message = "Consultation fee is required") @DecimalMin("0.00") @Digits(integer = 8, fraction = 2) BigDecimal consultationFee,
        @Size(max = 500) String availability) {
}
