package cc.remer.timetrack.usecase.user;

import cc.remer.timetrack.api.model.UserResponse;
import cc.remer.timetrack.domain.user.User;
import cc.remer.timetrack.util.MapperUtils;
import org.springframework.stereotype.Component;

/**
 * Mapper for User entity and API models.
 */
@Component
public class UserMapper {

    /**
     * Convert User entity to UserResponse DTO.
     *
     * @param user the user entity
     * @return the user response DTO
     */
    public UserResponse toResponse(User user) {
        if (user == null) {
            return null;
        }

        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setEmail(user.getEmail());
        response.setFirstName(user.getFirstName());
        response.setLastName(user.getLastName());
        response.setRole(UserResponse.RoleEnum.fromValue(user.getRole().name()));
        response.setActive(user.getActive());
        response.setState(UserResponse.StateEnum.fromValue(user.getState().name()));
        response.setHalfDayHolidaysEnabled(user.getHalfDayHolidaysEnabled());
        response.setCreatedAt(MapperUtils.toOffsetDateTime(user.getCreatedAt()));
        response.setUpdatedAt(MapperUtils.toOffsetDateTime(user.getUpdatedAt()));

        return response;
    }
}
