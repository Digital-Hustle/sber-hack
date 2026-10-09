package ru.proxodnaya.access.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SettingsRs(
        double faceThreshold,
        int qrToleranceSec,
        int shiftGraceMin,
        boolean requireShift,
        int tempLockFailures,
        int tempLockWindowSec,
        int framesRequired,
        int challengeTtlSec,
        int repeatScanCooldownSec
) {}