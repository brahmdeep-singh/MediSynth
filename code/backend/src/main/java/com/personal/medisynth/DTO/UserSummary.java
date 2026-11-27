package com.personal.medisynth.DTO;

import com.personal.medisynth.model.Role;
import com.personal.medisynth.model.Users;

/** The only user data returned by auth endpoints: id, name, email, granted role. */
public record UserSummary(Long id, String name, String email, Role role) {

    public static UserSummary from(Users u) {
        return new UserSummary(u.getId(), u.getName(), u.getEmail(), u.getGrantedRole());
    }
}
