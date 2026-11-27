package com.personal.medisynth.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.personal.medisynth.DTO.PatientProfileResponse;
import com.personal.medisynth.DTO.UpdatePatientProfileRequest;
import com.personal.medisynth.exception.ApiException;
import com.personal.medisynth.model.PatientProfile;
import com.personal.medisynth.model.Users;
import com.personal.medisynth.repo.PatientProfileRepo;
import com.personal.medisynth.repo.UserRepo;

@Service
public class PatientService {

    private final UserRepo userRepo;
    private final PatientProfileRepo profileRepo;

    public PatientService(UserRepo userRepo, PatientProfileRepo profileRepo) {
        this.userRepo = userRepo;
        this.profileRepo = profileRepo;
    }

    @Transactional
    public PatientProfileResponse getMe(Long userId) {
        Users user = requireUser(userId);
        return PatientProfileResponse.from(user, profileFor(user));
    }

    /** Updates only whitelisted fields of the caller's own profile; id comes from the token, never the body. */
    @Transactional
    public PatientProfileResponse updateMe(Long userId, UpdatePatientProfileRequest req) {
        Users user = requireUser(userId);
        user.setName(InputNormalizer.text(req.name()));
        user.setPhone(InputNormalizer.text(req.phone()));
        PatientProfile profile = profileFor(user);
        profile.setDateOfBirth(req.dateOfBirth());
        profile.setGender(req.gender());
        profile.setAddress(InputNormalizer.blankToNull(req.address()));
        profile.setEmergencyContactName(InputNormalizer.blankToNull(req.emergencyContactName()));
        profile.setEmergencyContactPhone(InputNormalizer.blankToNull(req.emergencyContactPhone()));
        return PatientProfileResponse.from(user, profile);
    }

    private Users requireUser(Long userId) {
        return userRepo.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("UNAUTHENTICATED", "Authentication is required"));
    }

    private PatientProfile profileFor(Users user) {
        return profileRepo.findByUserId(user.getId()).orElseGet(() -> {
            PatientProfile created = new PatientProfile();
            created.setUser(user);
            return profileRepo.save(created);
        });
    }
}
