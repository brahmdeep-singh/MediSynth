package com.personal.medisynth.service;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.personal.medisynth.model.Role;
import com.personal.medisynth.repo.LabTechnicianProfileRepo;
import com.personal.medisynth.repo.ResearcherProfileRepo;

/** Professional details an applicant submitted for the lab technician / researcher roles (shown to admins). */
@Service
public class ApplicantDetailsService {

    private final LabTechnicianProfileRepo labRepo;
    private final ResearcherProfileRepo researcherRepo;

    public ApplicantDetailsService(LabTechnicianProfileRepo labRepo, ResearcherProfileRepo researcherRepo) {
        this.labRepo = labRepo;
        this.researcherRepo = researcherRepo;
    }

    @Transactional(readOnly = true)
    public Map<String, String> resolve(Long userId, Role requestedRole) {
        Map<String, String> details = new LinkedHashMap<>();
        if (requestedRole == Role.LAB_TECHNICIAN) {
            labRepo.findByUserId(userId).ifPresent(p -> {
                details.put("department", p.getDepartment());
                details.put("employeeId", p.getEmployeeId());
            });
        } else if (requestedRole == Role.RESEARCHER) {
            researcherRepo.findByUserId(userId).ifPresent(p -> {
                details.put("institution", p.getInstitution());
                details.put("researchArea", p.getResearchArea());
            });
        }
        return details.isEmpty() ? null : details;
    }
}
