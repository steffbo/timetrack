package cc.remer.timetrack.usecase.workinghours;

import cc.remer.timetrack.api.model.UpdateWorkingDayConfig;
import cc.remer.timetrack.api.model.UpdateWorkingHoursRequest;
import cc.remer.timetrack.domain.user.User;
import cc.remer.timetrack.domain.workinghours.WorkingHours;
import cc.remer.timetrack.util.ValidationUtils;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
class WorkingDayUpdateMapper {

    List<WorkingDayUpdate> toFullWeekUpdates(UpdateWorkingHoursRequest request) {
        if (request.getWorkingDays() == null || request.getWorkingDays().isEmpty()) {
            throw new IllegalArgumentException("Arbeitsstunden-Konfiguration darf nicht leer sein");
        }

        if (request.getWorkingDays().size() != 7) {
            throw new IllegalArgumentException("Es müssen genau 7 Wochentage konfiguriert werden");
        }

        Set<Integer> weekdaysSeen = new HashSet<>();
        List<WorkingDayUpdate> updates = new ArrayList<>(7);

        for (UpdateWorkingDayConfig dayConfig : request.getWorkingDays()) {
            Integer weekday = dayConfig.getWeekday();
            ValidationUtils.validateWeekday(weekday);

            if (weekdaysSeen.contains(weekday)) {
                throw new IllegalArgumentException("Doppelter Wochentag: " + weekday);
            }
            weekdaysSeen.add(weekday);

            updates.add(toUpdate(dayConfig, weekday.shortValue(), " für Wochentag " + weekday));
        }

        for (int weekday = 1; weekday <= 7; weekday++) {
            if (!weekdaysSeen.contains(weekday)) {
                throw new IllegalArgumentException("Fehlender Wochentag: " + weekday);
            }
        }

        return updates;
    }

    WorkingDayUpdate toSingleDayUpdate(Integer weekday, UpdateWorkingDayConfig dayConfig) {
        ValidationUtils.validateWeekday(weekday);
        return toUpdate(dayConfig, weekday.shortValue(), "");
    }

    WorkingHours applyTo(WorkingHours workingHours, User user, WorkingDayUpdate update) {
        if (workingHours == null) {
            return WorkingHours.builder()
                    .user(user)
                    .weekday(update.weekday())
                    .hours(update.hours())
                    .isWorkingDay(update.isWorkingDay())
                    .startTime(update.startTime())
                    .endTime(update.endTime())
                    .breakMinutes(update.breakMinutes())
                    .build();
        }

        workingHours.setHours(update.hours());
        workingHours.setIsWorkingDay(update.isWorkingDay());
        workingHours.setStartTime(update.startTime());
        workingHours.setEndTime(update.endTime());
        workingHours.setBreakMinutes(update.breakMinutes());
        return workingHours;
    }

    private WorkingDayUpdate toUpdate(UpdateWorkingDayConfig dayConfig, Short weekday, String errorContext) {
        validateDayConfig(dayConfig, errorContext);

        LocalTime startTime = dayConfig.getStartTime() != null ? LocalTime.parse(dayConfig.getStartTime()) : null;
        LocalTime endTime = dayConfig.getEndTime() != null ? LocalTime.parse(dayConfig.getEndTime()) : null;
        Integer breakMinutes = dayConfig.getBreakMinutes() != null ? dayConfig.getBreakMinutes() : 0;
        BigDecimal hours = startTime != null && endTime != null
                ? calculateHoursFromTimes(startTime, endTime, breakMinutes)
                : BigDecimal.valueOf(dayConfig.getHours());

        return new WorkingDayUpdate(
                weekday,
                hours,
                dayConfig.getIsWorkingDay(),
                startTime,
                endTime,
                breakMinutes
        );
    }

    private void validateDayConfig(UpdateWorkingDayConfig dayConfig, String errorContext) {
        boolean hasStartTime = dayConfig.getStartTime() != null && !dayConfig.getStartTime().isEmpty();
        boolean hasEndTime = dayConfig.getEndTime() != null && !dayConfig.getEndTime().isEmpty();

        if (hasStartTime != hasEndTime) {
            throw new IllegalArgumentException("Start- und Endzeit müssen beide angegeben werden oder beide leer sein" + errorContext);
        }

        if (hasStartTime && hasEndTime) {
            try {
                LocalTime startTime = LocalTime.parse(dayConfig.getStartTime());
                LocalTime endTime = LocalTime.parse(dayConfig.getEndTime());

                ValidationUtils.validateTimeRange(startTime, endTime);
            } catch (Exception e) {
                throw new IllegalArgumentException("Ungültiges Zeitformat" + errorContext + ": " + e.getMessage());
            }
        }

        ValidationUtils.validateHours(dayConfig.getHours());

        if (dayConfig.getBreakMinutes() != null && dayConfig.getBreakMinutes() < 0) {
            throw new IllegalArgumentException("Pausenzeit darf nicht negativ sein" + errorContext);
        }

        if (dayConfig.getIsWorkingDay() == null) {
            throw new IllegalArgumentException("isWorkingDay darf nicht null sein" + errorContext);
        }
    }

    private BigDecimal calculateHoursFromTimes(LocalTime startTime, LocalTime endTime, Integer breakMinutes) {
        long minutes = ChronoUnit.MINUTES.between(startTime, endTime);
        long netMinutes = minutes - (breakMinutes != null ? breakMinutes : 0);
        netMinutes = Math.max(0, netMinutes);
        return BigDecimal.valueOf(netMinutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }
}
