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
import org.springframework.context.annotation.Lazy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@Slf4j
public class CreateUser {

    private final UserRepository userRepository;
    private final WorkingHoursRepository workingHoursRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final GenerateInviteToken generateInviteToken;

    public CreateUser(UserRepository userRepository,
                      WorkingHoursRepository workingHoursRepository,
                      PasswordEncoder passwordEncoder,
                      UserMapper userMapper,
                      @Lazy GenerateInviteToken generateInviteToken) {
        this.userRepository = userRepository;
        this.workingHoursRepository = workingHoursRepository;
        this.passwordEncoder = passwordEncoder;
        this.userMapper = userMapper;
        this.generateInviteToken = generateInviteToken;
    }

    @Transactional
    public UserResponse execute(CreateUserRequest request) {
        log.info("Creating new user with email: {}", request.getEmail());

        if (userRepository.existsByEmail(request.getEmail())) {
            log.warn("Email already exists: {}", request.getEmail());
            throw new DuplicateEmailException("Email bereits vergeben: " + request.getEmail());
        }

        GermanState state = GermanState.BERLIN;
        if (request.getState() != null) {
            state = GermanState.valueOf(request.getState().getValue());
        }

        boolean hasPasword = request.getPassword() != null && !request.getPassword().isBlank();

        User user = User.builder()
                .email(request.getEmail())
                .passwordHash(hasPasword ? passwordEncoder.encode(request.getPassword()) : null)
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .role(Role.valueOf(request.getRole().getValue()))
                .active(request.getActive() != null ? request.getActive() : true)
                .state(state)
                .halfDayHolidaysEnabled(request.getHalfDayHolidaysEnabled() != null ? request.getHalfDayHolidaysEnabled() : false)
                .build();

        User savedUser = userRepository.save(user);
        log.info("User created with ID: {}", savedUser.getId());

        createDefaultWorkingHours(savedUser);

        UserResponse response = userMapper.toResponse(savedUser);

        if (!hasPasword) {
            // Generate invite token and embed URL in response
            String inviteUrl = generateInviteToken.generateUrl(savedUser.getId());
            response.setInviteUrl(inviteUrl);
            log.info("Invite token generated for pending user: {}", savedUser.getId());
        }

        return response;
    }

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
