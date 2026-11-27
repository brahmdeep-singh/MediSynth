package com.personal.medisynth.DTO;

import java.time.LocalDate;

import com.personal.medisynth.model.Gender;
import com.personal.medisynth.model.PatientProfile;
import com.personal.medisynth.model.Users;

public record PatientProfileResponse(Long userId, String name, String email, String phone, LocalDate dateOfBirth,
        Gender gender, String address, String emergencyContactName, String emergencyContactPhone) {

    public static PatientProfileResponse from(Users u, PatientProfile p) {
        return new PatientProfileResponse(u.getId(), u.getName(), u.getEmail(), u.getPhone(), p.getDateOfBirth(),
                p.getGender(), p.getAddress(), p.getEmergencyContactName(), p.getEmergencyContactPhone());
    }
}
