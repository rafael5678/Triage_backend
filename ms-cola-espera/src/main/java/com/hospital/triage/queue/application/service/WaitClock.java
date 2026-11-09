package com.hospital.triage.queue.application.service;

import com.hospital.triage.common.dto.RankedPatientDTO;

import java.time.Duration;
import java.time.OffsetDateTime;

/** How long a patient has waited since arrival and how much of the max wait remains. */
final class WaitClock {

    static final int DEFAULT_MAX_WAIT = 30;

    private WaitClock() {
    }

    static int waited(RankedPatientDTO p, OffsetDateTime now) {
        OffsetDateTime start = p.getArrivedAt() != null ? p.getArrivedAt() : p.getEvaluatedAt();
        if (start == null) {
            return 0;
        }
        return (int) Math.max(0, Duration.between(start, now).toMinutes());
    }

    static int maxWait(RankedPatientDTO p) {
        return p.getMaxWaitMinutes() == null ? DEFAULT_MAX_WAIT : p.getMaxWaitMinutes();
    }

    /** Fills waitedMinutes, minutesLeft and overdue. Negative means the max wait already passed. */
    static RankedPatientDTO stamp(RankedPatientDTO p, OffsetDateTime now) {
        int waited = waited(p, now);
        int left = maxWait(p) - waited;
        p.setWaitedMinutes(waited);
        p.setMinutesLeft(left);
        p.setOverdue(left < 0);
        return p;
    }
}
