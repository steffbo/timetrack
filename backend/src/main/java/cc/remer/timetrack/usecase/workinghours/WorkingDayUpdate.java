package cc.remer.timetrack.usecase.workinghours;

import java.math.BigDecimal;
import java.time.LocalTime;

record WorkingDayUpdate(
        Short weekday,
        BigDecimal hours,
        Boolean isWorkingDay,
        LocalTime startTime,
        LocalTime endTime,
        Integer breakMinutes
) {
}
