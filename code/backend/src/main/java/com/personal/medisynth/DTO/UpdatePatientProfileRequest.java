package com.personal.medisynth.DTO;

import java.time.LocalDate;

import com.personal.medisynth.model.Gender;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Full replacement of the editable patient fields. Email, role and status cannot be changed here. */
public record UpdatePatientProfileRequest(
        @NotBlank(message = "Name is required") @Size(max = 100) String name,
        @NotBlank(message = "Phone is required")
        @Pattern(regexp = "^\\+?[0-9]{7,15}$", message = "Phone must be 7-15 digits, optionally starting with +") String phone,
        @Past(message = "Date of birth must be in the past") LocalDate dateOfBirth,
        Gender gender,
        @Size(max = 300) String address,
        @Size(max = 100) String emergencyContactName,
        @Pattern(regexp = "^\\+?[0-9]{7,15}$", message = "Phone must be 7-15 digits, optionally starting with +") String emergencyContactPhone) {
}
