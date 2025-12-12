package cc.remer.timetrack.usecase.recurringoffday;

import cc.remer.timetrack.adapter.persistence.RecurringOffDayRepository;
import cc.remer.timetrack.api.model.RecurringOffDayResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Use case to get all recurring off-days for a user.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class GetRecurringOffDays {

    private final RecurringOffDayRepository recurringOffDayRepository;
    private final RecurringOffDayMapper mapper;

    /**
     * Execute the use case to get all recurring off-days for a user.
     *
     * @param userId the user ID
     * @return list of recurring off-day responses
     */
    public List<RecurringOffDayResponse> execute(Long userId) {
        log.info("Getting recurring off-days for user ID: {}", userId);

        return recurringOffDayRepository.findByUserIdOrderByStartDateDesc(userId).stream()
                .map(mapper::toResponse)
                .collect(Collectors.toList());
    }
}
