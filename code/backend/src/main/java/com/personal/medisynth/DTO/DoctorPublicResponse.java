package com.personal.medisynth.DTO;

import java.math.BigDecimal;

import com.personal.medisynth.model.DoctorProfile;

/** What any signed-in user may see about a verified doctor: no email, phone or license number. */
public record DoctorPublicResponse(Long id, String name, String specialization, String qualifications,
        Integer experienceYears, BigDecimal consultationFee, String availability) {

    public static DoctorPublicResponse from(DoctorProfile p) {
        return new DoctorPublicResponse(p.getId(), p.getUser().getName(), p.getSpecialization(),
                p.getQualifications(), p.getExperienceYears(), p.getConsultationFee(), p.getAvailability());
    }
}
