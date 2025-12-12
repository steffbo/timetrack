package cc.remer.timetrack.usecase.user;

import cc.remer.timetrack.adapter.persistence.UserRepository;
import cc.remer.timetrack.adapter.persistence.WorkingHoursRepository;
import cc.remer.timetrack.api.model.CreateUserRequest;
import cc.remer.timetrack.api.model.UserResponse;
import cc.remer.timetrack.domain.user.GermanState;
import cc.remer.timetrack.domain.user.Role;
import cc.remer.timetrack.domain.user.User;
import cc.remer.timetrack.domain.workinghours.WorkingHours;
import cc.remer.timetrack.exception.DuplicateEmailException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * Use case for creating a new user.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CreateUser {

    private final UserRepository userRepository;
    private final WorkingHoursRepository workingHoursRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;

    /**
     * Execute the create user use case.
     *
     * @param request the create user request
     * @return the created user response
     * @throws DuplicateEmailException if email already exists
     */
    @Transactional
    public UserResponse execute(CreateUserRequest request) {
        log.info("Creating new user with email: {}", request.getEmail());

        // Check if email already exists
        if (userRepository.existsByEmail(request.getEmail())) {
            log.warn("Email already exists: {}", request.getEmail());
            throw new DuplicateEmailException("Email bereits vergeben: " + request.getEmail());
        }

        // Create user entity
        GermanState state = GermanState.BERLIN; // Default state
        if (request.getState() != null) {
            state = GermanState.valueOf(request.getState().getValue());
        }

        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .role(Role.valueOf(request.getRole().getValue()))
                .active(request.getActive() != null ? request.getActive() : true)
                .state(state)
                .build();

        // Save user
        User savedUser = userRepository.save(user);
        log.info("User created successfully with ID: {}", savedUser.getId());

        // Create default working hours (Mon-Fri: 8h, Sat-Sun: 0h)
        createDefaultWorkingHours(savedUser);
        log.debug("Default working hours created for user: {}", savedUser.getId());

        return userMapper.toResponse(savedUser);
    }

    /**
     * Create default working hours for a new user.
     * Monday to Friday: 8 hours each
     * Saturday and Sunday: 0 hours
     *
     * @param user the user to create working hours for
     */
    private void createDefaultWorkingHours(User user) {
        for (short weekday = 1; weekday <= 7; weekday++) {
            boolean isWeekend = weekday == 6 || weekday == 7;
            BigDecimal hours = isWeekend ? BigDecimal.ZERO : new BigDecimal("8.00");

            WorkingHours workingHours = WorkingHours.builder()
                    .user(user)
                    .weekday(weekday)
                    .hours(hours)
                    .isWorkingDay(!isWeekend)
                    .build();

            workingHoursRepository.save(workingHours);
        }
    }
}
