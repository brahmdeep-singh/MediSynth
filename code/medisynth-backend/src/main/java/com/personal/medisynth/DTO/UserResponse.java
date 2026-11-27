package com.personal.medisynth.DTO;

import java.time.Instant;

import com.personal.medisynth.model.AccountStatus;
import com.personal.medisynth.model.Role;
import com.personal.medisynth.model.Users;

/** Admin view of an account. Never contains the password hash. */
public record UserResponse(Long id, String name, String email, String phone, Role role, AccountStatus accountStatus,
        Instant createdAt, Instant updatedAt) {

    public static UserResponse from(Users u) {
        return new UserResponse(u.getId(), u.getName(), u.getEmail(), u.getPhone(), u.getGrantedRole(),
                u.getAccountStatus(), u.getCreatedAt(), u.getUpdatedAt());
    }
}
