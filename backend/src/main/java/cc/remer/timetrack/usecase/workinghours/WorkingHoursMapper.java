package cc.remer.timetrack.usecase.workinghours;

import cc.remer.timetrack.api.model.WorkingDayConfig;
import cc.remer.timetrack.api.model.WorkingHoursResponse;
import cc.remer.timetrack.domain.workinghours.WorkingHours;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Mapper for WorkingHours entity and API models.
 */
@Component
class WorkingHoursMapper {

    /**
     * Convert a list of WorkingHours entities to WorkingHoursResponse DTO.
     *
     * @param userId the user ID
     * @param workingHoursList list of working hours entities
     * @return the working hours response DTO
     */
    WorkingHoursResponse toResponse(Long userId, List<WorkingHours> workingHoursList) {
        if (workingHoursList == null || workingHoursList.isEmpty()) {
            return null;
        }

        WorkingHoursResponse response = new WorkingHoursResponse();
        response.setUserId(userId);

        List<WorkingDayConfig> workingDays = workingHoursList.stream()
                .sorted(Comparator.comparing(WorkingHours::getWeekday))
                .map(this::toWorkingDayConfig)
                .collect(Collectors.toList());

        response.setWorkingDays(workingDays);

        return response;
    }

    /**
     * Convert WorkingHours entity to WorkingDayConfig DTO.
     *
     * @param workingHours the working hours entity
     * @return the working day config DTO
     */
    WorkingDayConfig toWorkingDayConfig(WorkingHours workingHours) {
        if (workingHours == null) {
            return null;
        }

        WorkingDayConfig config = new WorkingDayConfig();
        config.setId(workingHours.getId());
        config.setWeekday(workingHours.getWeekday().intValue());
        config.setHours(workingHours.getHours().doubleValue());
        config.setIsWorkingDay(workingHours.getIsWorkingDay());

        // Set optional time fields
        if (workingHours.getStartTime() != null) {
            config.setStartTime(workingHours.getStartTime().toString());
        }
        if (workingHours.getEndTime() != null) {
            config.setEndTime(workingHours.getEndTime().toString());
        }

        // Set break minutes
        config.setBreakMinutes(workingHours.getBreakMinutes());

        // Set day name from weekday
        DayOfWeek dayOfWeek = workingHours.getDayOfWeek();
        config.setDayName(WorkingDayConfig.DayNameEnum.fromValue(dayOfWeek.name()));

        return config;
    }

}
