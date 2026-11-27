package com.personal.medisynth.repo;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.personal.medisynth.model.AccountStatus;
import com.personal.medisynth.model.Role;
import com.personal.medisynth.model.Users;

@Repository
public interface UserRepo extends JpaRepository<Users, Long> {

    Optional<Users> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByGrantedRole(Role role);

    Page<Users> findAllBy(Pageable pageable);

    Page<Users> findByGrantedRole(Role role, Pageable pageable);

    Page<Users> findByAccountStatus(AccountStatus status, Pageable pageable);

    Page<Users> findByGrantedRoleAndAccountStatus(Role role, AccountStatus status, Pageable pageable);
}
