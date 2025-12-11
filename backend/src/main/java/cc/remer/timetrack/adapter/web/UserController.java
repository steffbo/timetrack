package cc.remer.timetrack.adapter.web;

import cc.remer.timetrack.api.UsersApi;
import cc.remer.timetrack.api.model.CreateUserRequest;
import cc.remer.timetrack.api.model.UpdateUserRequest;
import cc.remer.timetrack.api.model.UserResponse;
import cc.remer.timetrack.usecase.user.CreateUser;
import cc.remer.timetrack.usecase.user.DeleteUser;
import cc.remer.timetrack.usecase.user.GetUser;
import cc.remer.timetrack.usecase.user.UpdateUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for user management operations.
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class UserController implements UsersApi {

    private final CreateUser createUser;
    private final GetUser getUser;
    private final UpdateUser updateUser;
    private final DeleteUser deleteUser;

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        log.info("GET /users - Getting all users");
        List<UserResponse> response = getUser.getAllUsers();
        return ResponseEntity.ok(response);
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponse> createUser(CreateUserRequest createUserRequest) {
        log.info("POST /users - Creating new user");
        UserResponse response = createUser.execute(createUserRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @Override
    public ResponseEntity<UserResponse> getCurrentUser() {
        log.info("GET /users/me - Getting current user");
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserResponse response = getUser.getCurrentUser(authentication);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<UserResponse> getUserById(Long id) {
        log.info("GET /users/{} - Getting user by ID", id);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserResponse response = getUser.execute(id, authentication);
        return ResponseEntity.ok(response);
    }

    @Override
    public ResponseEntity<UserResponse> updateUser(Long id, UpdateUserRequest updateUserRequest) {
        log.info("PUT /users/{} - Updating user", id);
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserResponse response = updateUser.execute(id, updateUserRequest, authentication);
        return ResponseEntity.ok(response);
    }

    @Override
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteUser(Long id) {
        log.info("DELETE /users/{} - Deleting user", id);
        deleteUser.execute(id);
        return ResponseEntity.noContent().build();
    }
}
