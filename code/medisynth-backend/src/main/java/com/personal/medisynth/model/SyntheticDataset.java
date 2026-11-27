package com.personal.medisynth.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** A Synthetix dataset batch. The rows live in synthetic_patient_records and are written with JDBC in batches. */
@Entity
@Table(name = "synthetic_datasets")
@Getter
@Setter
@NoArgsConstructor
public class SyntheticDataset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private DatasetStatus status = DatasetStatus.EVALUATING;

    @Column(name = "record_count", nullable = false)
    private int recordCount;

    @Column(name = "seed", nullable = false)
    private long seed;

    @Column(name = "version", nullable = false)
    private int version = 1;

    @Column(name = "profile_source", nullable = false, length = 30)
    private String profileSource;

    @Column(name = "profile_sample_bucket", nullable = false, length = 20)
    private String profileSampleBucket;

    @Column(name = "profile_age_weights", nullable = false, length = 300)
    private String profileAgeWeights;

    @Column(name = "profile_gender_weights", nullable = false, length = 100)
    private String profileGenderWeights;

    @Column(name = "overall_score")
    private Double overallScore;

    @Column(name = "marginal_score")
    private Double marginalScore;

    @Column(name = "relationship_score")
    private Double relationshipScore;

    @Column(name = "validity_score")
    private Double validityScore;

    @Column(name = "privacy_score")
    private Double privacyScore;

    @Column(name = "k_anonymity_share")
    private Double kAnonymityShare;

    @Column(name = "min_class_size")
    private Integer minClassSize;

    @Column(name = "unique_share")
    private Double uniqueShare;

    @Column(name = "column_scores", columnDefinition = "text")
    private String columnScores;

    @Column(name = "relationship_checks", columnDefinition = "text")
    private String relationshipChecks;

    @Column(name = "failure_reasons", columnDefinition = "text")
    private String failureReasons;

    @Column(name = "evaluated_at")
    private Instant evaluatedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by", nullable = false, updatable = false)
    private Users createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    /** Clears a previous evaluation (used when the rows are regenerated). */
    public void clearEvaluation() {
        overallScore = null;
        marginalScore = null;
        relationshipScore = null;
        validityScore = null;
        privacyScore = null;
        kAnonymityShare = null;
        minClassSize = null;
        uniqueShare = null;
        columnScores = null;
        relationshipChecks = null;
        failureReasons = null;
        evaluatedAt = null;
    }
}
