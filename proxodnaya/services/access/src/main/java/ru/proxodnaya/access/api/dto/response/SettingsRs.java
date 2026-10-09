package ru.proxodnaya.access.api.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SettingsRs {

    private double faceThreshold;
    private int qrToleranceSec;
    private int shiftGraceMin;
    private boolean requireShift;
    private int tempLockFailures;
    private int tempLockWindowSec;
    private int framesRequired;
    private int challengeTtlSec;
    private int repeatScanCooldownSec;
}