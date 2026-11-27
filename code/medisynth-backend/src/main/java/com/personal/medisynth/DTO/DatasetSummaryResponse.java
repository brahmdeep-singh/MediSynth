package com.personal.medisynth.DTO;

public record DatasetSummaryResponse(long total, long evaluating, long ready, long needsRegeneration, long retired) {
}
