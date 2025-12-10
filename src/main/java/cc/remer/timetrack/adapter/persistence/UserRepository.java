package cc.remer.timetrack.adapter.persistence;

import cc.remer.timetrack.domain.user.Role;
import cc.remer.timetrack.domain.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for User entity operations.
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Find a user by email address.
     *
     * @param email the email address
     * @return Optional containing the user if found
     */
    Optional<User> findByEmail(String email);

    /**
     * Check if a user exists with the given email.
     *
     * @param email the email address
     * @return true if user exists
     */
    boolean existsByEmail(String email);

    /**
     * Find all users with a specific role.
     *
     * @param role the user role
     * @return list of users with the specified role
     */
    List<User> findByRole(Role role);

    /**
     * Find all active users.
     *
     * @param active the active status
     * @return list of users with the specified active status
     */
    List<User> findByActive(Boolean active);

    /**
     * Find all active users with a specific role.
     *
     * @param role the user role
     * @param active the active status
     * @return list of users matching the criteria
     */
    List<User> findByRoleAndActive(Role role, Boolean active);
}
