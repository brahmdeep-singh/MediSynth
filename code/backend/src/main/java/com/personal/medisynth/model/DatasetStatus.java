package com.personal.medisynth.model;

/** EVALUATING (generated, awaiting evaluation) -> READY or NEEDS_REGENERATION -> (regenerate) EVALUATING; any -> RETIRED. */
public enum DatasetStatus {
    EVALUATING,
    READY,
    NEEDS_REGENERATION,
    RETIRED
}
