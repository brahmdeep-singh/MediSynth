package com.personal.medisynth.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Single row (id = 1) recording whether the one-time first-admin bootstrap has been used. */
@Entity
@Table(name = "bootstrap_state")
@Getter
@Setter
@NoArgsConstructor
public class BootstrapState {

    @Id
    @Column(name = "id")
    private Integer id;

    @Column(name = "consumed", nullable = false)
    private boolean consumed;

    @Column(name = "consumed_at")
    private Instant consumedAt;

    @Column(name = "consumed_by_user_id")
    private Long consumedByUserId;
}
