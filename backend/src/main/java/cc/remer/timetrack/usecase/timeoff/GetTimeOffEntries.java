package cc.remer.timetrack.usecase.timeoff;

import cc.remer.timetrack.adapter.persistence.TimeOffRepository;
import cc.remer.timetrack.api.model.TimeOffResponse;
import cc.remer.timetrack.domain.timeoff.TimeOff;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Use case to get time-off entries for a user.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GetTimeOffEntries {

    private final TimeOffRepository timeOffRepository;
    private final TimeOffMapper mapper;

    /**
     * Execute the use case to get time-off entries.
     *
     * @param userId the user ID
     * @param startDate optional filter by start date
     * @param endDate optional filter by end date
     * @return list of time-off responses
     */
    public List<TimeOffResponse> execute(Long userId, LocalDate startDate, LocalDate endDate) {
        log.info("Getting time-off entries for user ID: {}", userId);

        List<TimeOff> entries;
        if (startDate != null && endDate != null) {
            entries = timeOffRepository.findByUserIdAndDateRange(userId, startDate, endDate);
        } else {
            entries = timeOffRepository.findByUserIdOrderByStartDateDesc(userId);
        }

        return entries.stream()
                .map(mapper::toResponse)
                .collect(Collectors.toList());
    }
}
